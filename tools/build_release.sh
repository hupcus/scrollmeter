#!/usr/bin/env bash
# Signed release build: APK (sideload / GitHub Release) and AAB (Google Play), both with the upload key.
#
# The keystore lives outside the repository and its password in the macOS Keychain (ADR-034). Nothing
# secret is printed, written to a file or passed on a command line: the password goes from the Keychain
# into this process's environment, where app/build.gradle.kts reads it.
#
#   tools/build_release.sh            # build, prove both files carry the upload key, check the APK
#
# Refuses a working tree with changes, so every artifact is exactly the printed commit.
# Overrides: SIGNING_KEYSTORE_PATH (default ~/.android-keystores/scrollmeter-upload.jks),
# SCROLLMETER_KEYCHAIN_SERVICE (default scrollmeter-upload-keystore), ANDROID_HOME.
set -euo pipefail

cd "$(dirname "$0")/.."

# SHA-256 of the upload certificate (ADR-034). A new upload key (Play support reset) changes it here, with an ADR.
upload_cert_sha256=046f8cd0b07323f70712e11253cad7fb8203f268c8d81053783d9c1a669c0e88

export SIGNING_KEYSTORE_PATH="${SIGNING_KEYSTORE_PATH:-$HOME/.android-keystores/scrollmeter-upload.jks}"
export SIGNING_KEY_ALIAS="${SIGNING_KEY_ALIAS:-scrollmeter-upload}"
service="${SCROLLMETER_KEYCHAIN_SERVICE:-scrollmeter-upload-keystore}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
build_tools="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)"

if [[ -n "$(git status --porcelain)" ]]; then
    echo "the working tree has changes — a release is built from a commit only (git status)" >&2
    exit 1
fi
[[ -f "$SIGNING_KEYSTORE_PATH" ]] || { echo "keystore not found: $SIGNING_KEYSTORE_PATH" >&2; exit 1; }
if ! SIGNING_STORE_PASSWORD="$(security find-generic-password -s "$service" -a scrollmeter -w 2>/dev/null)"; then
    echo "no Keychain item '$service' (account scrollmeter)" >&2
    exit 1
fi
# PKCS12 keystores have one password for the store and the key.
export SIGNING_STORE_PASSWORD
export SIGNING_KEY_PASSWORD="$SIGNING_STORE_PASSWORD"

# No configuration cache: it would store the passwords under .gradle/ (app/build.gradle.kts refuses it too).
# A Gradle or Kotlin daemon this starts keeps the password in its environment until it exits; readable
# only by this user, who can read the Keychain item anyway (`security` is on its access list) — accepted.
./gradlew --no-configuration-cache clean assembleRelease bundleRelease

apk=app/build/outputs/apk/release/app-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
unset SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD

# Signed is not enough: signed with a different key, Play refuses the AAB and phones refuse the APK as an update.
apk_cert="$("$build_tools/apksigner" verify --print-certs "$apk" \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
aab_cert="$(keytool -printcert -jarfile "$aab" | sed -n 's/^[[:space:]]*SHA256: //p' | tr -d ':' | tr 'A-F' 'a-f')"
for pair in "APK:$apk_cert" "AAB:$aab_cert"; do
    if [[ "${pair#*:}" != "$upload_cert_sha256" ]]; then
        echo "${pair%%:*} is not signed with the upload key (certificate SHA-256 '${pair#*:}')" >&2
        exit 1
    fi
done
echo "APK and AAB signed with the upload key ($upload_cert_sha256)"

python3 tools/check_release_apk.py --apk "$apk" --mapping app/build/outputs/mapping/release/mapping.txt \
    --build-tools "$build_tools"
echo "commit $(git rev-parse HEAD)"
shasum -a 256 "$apk" "$aab"
