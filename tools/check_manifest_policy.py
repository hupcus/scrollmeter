#!/usr/bin/env python3
"""Check merged AndroidManifest.xml files against ScrollMeter's hard rules (CLAUDE.md, spec §5, §29).

PolicyGuardTest reads the app's own sources; this reads what the build actually ships, including
permissions and components contributed by libraries. CI runs it after the Gradle build:

    python3 tools/check_manifest_policy.py app/build/intermediates/merged_manifests/*/process*Manifest/AndroidManifest.xml

A manifest whose path contains `/release/` must also carry no debug tooling (FileProvider).
Standard library only; exit code 1 on any violation.
"""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

A = "{http://schemas.android.com/apk/res/android}"
SERVICE = "com.scrollmeter.app.accessibility.ScrollAccessibilityService"
FORBIDDEN_PERMISSIONS = (
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.FOREGROUND_SERVICE",  # prefix: covers FOREGROUND_SERVICE_* types too
)


def violations(manifest: str, release: bool) -> list[str]:
    root = ET.fromstring(manifest)
    found = []
    for perm in root.iter("uses-permission"):
        name = perm.get(A + "name", "")
        if any(name.startswith(f) for f in FORBIDDEN_PERMISSIONS):
            found.append(f"forbidden permission {name}")
    app = root.find("application")
    if app is None:
        return found + ["no <application>"]
    if app.get(A + "allowBackup") != "false":
        found.append("allowBackup is not false")
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
        for p in app.iter("provider"):
            if "FileProvider" in p.get(A + "name", ""):
                found.append(f"release ships a FileProvider ({p.get(A + 'authorities')})")
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
