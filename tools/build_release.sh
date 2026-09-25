#!/usr/bin/env bash
# Signed release build: APK (sideload / GitHub Release) and AAB (Google Play), both with the upload key.
#
# The keystore lives outside the repository and its password in the macOS Keychain (handoff.md,
# Phase 8). Nothing secret is printed, written to a file or passed on a command line: the password
# goes from the Keychain into this process's environment, where app/build.gradle.kts reads it.
#
#   tools/build_release.sh            # build, verify the signature, check the APK
#
# Overrides: SIGNING_KEYSTORE_PATH (default ~/.android-keystores/scrollmeter-upload.jks),
# SCROLLMETER_KEYCHAIN_SERVICE (default scrollmeter-upload-keystore), ANDROID_HOME.
set -euo pipefail

cd "$(dirname "$0")/.."

export SIGNING_KEYSTORE_PATH="${SIGNING_KEYSTORE_PATH:-$HOME/.android-keystores/scrollmeter-upload.jks}"
export SIGNING_KEY_ALIAS="${SIGNING_KEY_ALIAS:-scrollmeter-upload}"
service="${SCROLLMETER_KEYCHAIN_SERVICE:-scrollmeter-upload-keystore}"
sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
build_tools="$sdk/build-tools/36.0.0"

[[ -f "$SIGNING_KEYSTORE_PATH" ]] || { echo "keystore not found: $SIGNING_KEYSTORE_PATH" >&2; exit 1; }
if ! SIGNING_STORE_PASSWORD="$(security find-generic-password -s "$service" -a scrollmeter -w 2>/dev/null)"; then
    echo "no Keychain item '$service' (account scrollmeter)" >&2
    exit 1
fi
# PKCS12 keystores have one password for the store and the key.
export SIGNING_STORE_PASSWORD
export SIGNING_KEY_PASSWORD="$SIGNING_STORE_PASSWORD"

# No configuration cache: it would serialise the signing config, passwords included, into .gradle/.
./gradlew --no-configuration-cache clean assembleRelease bundleRelease

apk=app/build/outputs/apk/release/app-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
unset SIGNING_STORE_PASSWORD SIGNING_KEY_PASSWORD

"$build_tools/apksigner" verify --verbose --print-certs "$apk" \
    | grep -E "^Verified using|^Signer #1 certificate (DN|SHA-256)"
python3 tools/check_release_apk.py --apk "$apk" --mapping app/build/outputs/mapping/release/mapping.txt \
    --build-tools "$build_tools"
shasum -a 256 "$apk" "$aab"
