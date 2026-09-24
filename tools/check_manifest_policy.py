#!/usr/bin/env python3
"""Check merged AndroidManifest.xml files against ScrollMeter's hard rules (CLAUDE.md, spec §5, §29).

PolicyGuardTest reads the app's own sources; this reads what the build actually ships, including
permissions and components contributed by libraries. CI runs it after the Gradle build:

    python3 tools/check_manifest_policy.py app/build/intermediates/merged_manifests/*/process*Manifest/AndroidManifest.xml

Permissions are an allowlist (PLAN Phase 3): anything the build requests beyond it — from our
manifest or from a library — fails, so a new permission needs an ADR and a change here. The
FORBIDDEN list stays to name the hard-rule ones explicitly in the failure message.

The application must keep `allowBackup="false"` and point `dataExtractionRules` at its rules (ADR-027),
and every exported component other than the launcher activity must be guarded by a permission.

A release manifest must also carry no debug tooling: the debug build's FileProvider (authority
`….devtools.files`) fails, and so does any provider whose authority is not in the release allowlist
(the CSV share sheet's own provider, ADR-030, and androidx.startup's).
Standard library only; exit code 1 on any violation.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

A = "{http://schemas.android.com/apk/res/android}"
SERVICE = "com.scrollmeter.app.accessibility.ScrollAccessibilityService"
# ADR-021: time in app (Usage access, granted by the user in Settings). ADR-030: the optional
# goal / record / summary notifications (PLAN Phase 6).
ALLOWED_PERMISSIONS = ("android.permission.PACKAGE_USAGE_STATS", "android.permission.POST_NOTIFICATIONS")
DEBUG_PROVIDER_AUTHORITY_SUFFIX = ".devtools.files"
# Every provider a release may ship, exactly: the CSV share sheet's (ADR-030) and androidx.startup's
# InitializationProvider (not exported; pulled in by lifecycle / profileinstaller / emoji2). A new
# one — ours or a library's — has to be named here.
RELEASE_PROVIDER_AUTHORITIES = ("{package}.exports", "{package}.androidx-startup")
# androidx.core declares and requests this signature permission for its own receivers, named after
# the application id (com.scrollmeter.app or com.scrollmeter.app.debug) — matched exactly.
OWN_SIGNATURE_PERMISSION = "{package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
PERMISSION_TAGS = ("uses-permission", "uses-permission-sdk-23")
LAUNCHER_ACTIVITY = "com.scrollmeter.app.MainActivity"
# compose-ui-tooling (debugImplementation) ships an exported PreviewActivity for Android Studio's
# "run preview on device"; tolerated in debug manifests only.
DEBUG_ONLY_EXPORTED = ("androidx.compose.ui.tooling.PreviewActivity",)
FORBIDDEN_PERMISSIONS = (
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.FOREGROUND_SERVICE",  # prefix: covers FOREGROUND_SERVICE_* types too
)


def allowed(name: str, package: str) -> bool:
    return name in ALLOWED_PERMISSIONS or name == OWN_SIGNATURE_PERMISSION.format(package=package)


def violations(manifest: str, release: bool) -> list[str]:
    root = ET.fromstring(manifest)
    found = []
    package = root.get("package", "")
    for tag in PERMISSION_TAGS:
        for perm in root.iter(tag):
            name = perm.get(A + "name", "")
            if any(name.startswith(f) for f in FORBIDDEN_PERMISSIONS):
                found.append(f"forbidden permission {name}")
            elif not allowed(name, package):
                found.append(f"permission not in the allowlist {name}")
    app = root.find("application")
    if app is None:
        return found + ["no <application>"]
    if app.get(A + "allowBackup") != "false":
        found.append("allowBackup is not false")
    if not app.get(A + "dataExtractionRules"):
        found.append("no dataExtractionRules (device-to-device transfer on API 31+)")
    for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
        for c in app.iter(kind):
            name = c.get(A + "name", "")
            tolerated = name == LAUNCHER_ACTIVITY or (not release and name in DEBUG_ONLY_EXPORTED)
            if c.get(A + "exported") == "true" and not c.get(A + "permission") and not tolerated:
                found.append(f"exported {kind} without a permission: {name}")
    services = [s for s in app.iter("service") if s.get(A + "name") == SERVICE]
    if len(services) != 1:
        found.append(f"expected one {SERVICE}, found {len(services)}")
    for s in services:
        if s.get(A + "exported") != "false":
            found.append("accessibility service is not exported=false")
        if s.get(A + "permission") != "android.permission.BIND_ACCESSIBILITY_SERVICE":
            found.append("accessibility service is not guarded by BIND_ACCESSIBILITY_SERVICE")
        if s.get(A + "foregroundServiceType"):
            found.append("accessibility service declares a foregroundServiceType")
    if release:
        allowed_authorities = {a.format(package=package) for a in RELEASE_PROVIDER_AUTHORITIES}
        for p in app.iter("provider"):
            authorities = p.get(A + "authorities", "")
            if authorities.endswith(DEBUG_PROVIDER_AUTHORITY_SUFFIX):
                found.append(f"release ships the debug FileProvider ({authorities})")
            elif any(a not in allowed_authorities for a in authorities.split(";")):
                found.append(f"provider not in the release allowlist ({authorities})")
    return found


def main(argv: list[str]) -> int:
    if not argv:
        print("usage: check_manifest_policy.py <merged AndroidManifest.xml> [...]", file=sys.stderr)
        return 2
    failed = False
    for path in map(Path, argv):
        release = "/release/" in path.as_posix()
        found = violations(path.read_text(encoding="utf-8"), release)
        label = "release" if release else "debug"
        if found:
            failed = True
            for v in found:
                print(f"FAIL {path} ({label}): {v}")
        else:
            print(f"ok   {path} ({label})")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
