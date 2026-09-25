#!/usr/bin/env python3
"""Checks a release APK for what must never ship (spec §34, §66; ADR-010; PLAN Phase 8).

The merged manifest is checked by check_manifest_policy.py; this looks at the built APK itself:

- not debuggable (`aapt2 dump badging`);
- no code calls `android.util.Log` (`dexdump -d`) — ours logs only behind `BuildConfig.DEBUG`, and
  `proguard-rules.pro` strips the libraries' calls (ADR-034). Checking every class, not just ours,
  is deliberate: R8 merges library classes into ours and the other way round, so "whose code is
  this" cannot be read reliably from a renamed class. mapping.txt only names the class in the report;
- none of the debug recording's literals in the dex (the raw event file lives in `src/debug/`).

    python3 tools/check_release_apk.py --apk app/build/outputs/apk/release/app-release.apk \\
        --mapping app/build/outputs/mapping/release/mapping.txt

Exit 0 = clean, 1 = violations (listed on stderr), 2 = a tool could not run.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path

LOG_CLASS = "Landroid/util/Log;"
# Literals only the debug event recording uses (src/debug/.../DebugRecordingFile.kt, DebugExport.kt).
FORBIDDEN_DEX_LITERALS = (b"recording.csv", b"files/debug")

_MAPPING_CLASS = re.compile(r"^(\S+) -> (\S+):$")
# dexdump -d marks the start of each method's code with "|[offset] a.b.method:(args)ret".
_DEX_METHOD = re.compile(r"\|\[[0-9a-f]+\] (\S+)\.([^.:\s]+):\(")


def deobfuscation_map(mapping_text: str) -> dict[str, str]:
    """Renamed class → original, from R8's mapping.txt (class lines only)."""
    result: dict[str, str] = {}
    for line in mapping_text.splitlines():
        match = _MAPPING_CLASS.match(line)
        if match:
            result[match.group(2)] = match.group(1)
    return result


def methods_calling_log(dexdump_text: str) -> set[tuple[str, str]]:
    """(class, method) as named in the dex, for every method whose code invokes android.util.Log."""
    found: set[tuple[str, str]] = set()
    current: tuple[str, str] | None = None
    for line in dexdump_text.splitlines():
        match = _DEX_METHOD.search(line)
        if match:
            current = (match.group(1), match.group(2))
        elif current and "invoke-" in line and LOG_CLASS in line:
            found.add(current)
    return found


def log_calls(dexdump_text: str, mapping_text: str) -> list[str]:
    """Every method that still calls Log. The original class name is a hint only: R8 merges small
    classes (library ones into ours, too), so a renamed class can hold several classes' code."""
    names = deobfuscation_map(mapping_text)
    return [f"{cls}.{method} (in {names.get(cls, cls)})" for cls, method in sorted(methods_calling_log(dexdump_text))]


def is_debuggable(badging_text: str) -> bool:
    return any(line.strip() == "application-debuggable" for line in badging_text.splitlines())


def forbidden_literals(apk: Path) -> list[str]:
    found = []
    with zipfile.ZipFile(apk) as archive:
        for entry in archive.namelist():
            if re.fullmatch(r"classes\d*\.dex", entry):
                data = archive.read(entry)
                found += [f"{entry} contains {literal.decode()!r}" for literal in FORBIDDEN_DEX_LITERALS if literal in data]
    return found


def violations(apk: Path, mapping_text: str, dexdump_text: str, badging_text: str) -> list[str]:
    found = [f"{name} calls android.util.Log" for name in log_calls(dexdump_text, mapping_text)]
    if is_debuggable(badging_text):
        found.append("the APK is debuggable")
    return found + forbidden_literals(apk)


def _run(command: list[str]) -> str:
    # dexdump prints string constants raw (MUTF-8), which is not always valid UTF-8.
    return subprocess.run(command, check=True, capture_output=True, text=True, errors="replace").stdout


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True, help="R8 mapping.txt of the same build")
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "Library/Android/sdk"))
    parser.add_argument("--build-tools", type=Path, default=sdk / "build-tools/36.0.0")
    args = parser.parse_args(argv)

    try:
        dexdump = _run([str(args.build_tools / "dexdump"), "-d", str(args.apk)])
        badging = _run([str(args.build_tools / "aapt2"), "dump", "badging", str(args.apk)])
        mapping = args.mapping.read_text(encoding="utf-8")
    except (OSError, subprocess.CalledProcessError) as error:
        print(f"check_release_apk: cannot inspect {args.apk}: {error}", file=sys.stderr)
        return 2

    found = violations(args.apk, mapping, dexdump, badging)
    for item in found:
        print(f"VIOLATION: {item}", file=sys.stderr)
    if not found:
        print(f"release APK clean: {args.apk}")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
