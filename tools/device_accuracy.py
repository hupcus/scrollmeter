#!/usr/bin/env python3
"""Drive the debug build's test list over adb and measure accuracy (spec §36 Tests A, C, D, E).

Prerequisites: debug build installed, ScrollMeter accessibility service enabled, phone unlocked
with the screen on, portrait. The script opens the app, taps "Testovací seznam", and for each
test taps "Vynulovat", performs the gesture, waits for the list to settle and reads the last
`TESTLIST` logcat line (ground truth from the list's NestedScrollConnection vs what the
accessibility pipeline measured for our own package).

Usage:
    python3 tools/device_accuracy.py [--serial SERIAL] [--only A500,C] [--markdown]
"""

from __future__ import annotations

import argparse
import os
import re
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from analyze_debug_csv import testlist_accuracy  # noqa: E402

PACKAGE = "com.scrollmeter.app.debug"
ACTIVITY = f"{PACKAGE}/com.scrollmeter.app.MainActivity"
ADB = os.environ.get("ADB", str(Path.home() / "Library/Android/sdk/platform-tools/adb"))
SETTLE_S = 3.0  # TESTLIST is debounced by 1.5 s after the last change
EV = re.compile(r"^(?P<t>\d+\.\d+)\s.*ScrollMeter: ev .*pkg=(?P<pkg>\S+) .*used=(?P<ux>-?\d+),(?P<uy>-?\d+) .*src=(?P<src>\w+)")
MARK = re.compile(r"^(?P<t>\d+\.\d+)\s.*ScrollMeter: MARK_UP")


@dataclass
class Result:
    test: str
    gt_px: float
    measured_px: float
    gt_mm: float
    measured_mm: float
    events: int
    after_lift_events: int | None = None
    after_lift_px: float | None = None

    @property
    def error_pct(self) -> float:
        return (self.measured_mm - self.gt_mm) / self.gt_mm * 100 if self.gt_mm else float("nan")


class Device:
    def __init__(self, serial: str | None):
        self.base = [ADB] + (["-s", serial] if serial else [])

    def shell(self, command: str, check: bool = True) -> str:
        out = subprocess.run(self.base + ["shell", command], capture_output=True, text=True, check=check)
        return out.stdout

    def logcat(self) -> str:
        return subprocess.run(self.base + ["logcat", "-d", "-v", "epoch", "-s", "ScrollMeter:D"],
                              capture_output=True, text=True).stdout

    def clear_logcat(self) -> None:
        subprocess.run(self.base + ["logcat", "-c"], check=True)

    def nodes(self) -> list[ET.Element]:
        self.shell("uiautomator dump /sdcard/scrollmeter-ui.xml >/dev/null 2>&1", check=False)
        xml = self.shell("cat /sdcard/scrollmeter-ui.xml")
        return list(ET.fromstring(xml).iter("node"))

    def tap_text(self, text: str) -> None:
        for node in self.nodes():
            if node.get("text") == text:
                x, y = center(node.get("bounds"))
                self.shell(f"input tap {x} {y}")
                return
        raise RuntimeError(f"'{text}' not on screen")

    def bounds_of_text(self, text: str) -> tuple[int, int, int, int]:
        for node in self.nodes():
            if node.get("text") == text:
                return parse_bounds(node.get("bounds"))
        raise RuntimeError(f"'{text}' not on screen")


def parse_bounds(bounds: str) -> tuple[int, int, int, int]:
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", bounds))
    return x1, y1, x2, y2


def center(bounds: str) -> tuple[int, int]:
    x1, y1, x2, y2 = parse_bounds(bounds)
    return (x1 + x2) // 2, (y1 + y2) // 2


def slow_drag(x1: int, y1: int, x2: int, y2: int, steps: int = 12) -> str:
    """DOWN, straight MOVEs, a 600 ms hold, UP: the velocity at lift is ~0, so no fling."""
    cmds = [f"input motionevent DOWN {x1} {y1}"]
    for i in range(1, steps + 1):
        cmds.append(f"input motionevent MOVE {x1 + (x2 - x1) * i // steps} {y1 + (y2 - y1) * i // steps}")
    cmds += ["sleep 0.6", f"input motionevent UP {x2} {y2}", "log -t ScrollMeter MARK_UP"]
    return "; ".join(cmds)


def fling(x1: int, y1: int, x2: int, y2: int, duration_ms: int = 120) -> str:
    return f"input swipe {x1} {y1} {x2} {y2} {duration_ms}; log -t ScrollMeter MARK_UP"


def run_test(dev: Device, name: str, gestures: list[str], axis: str, settle_s: float = SETTLE_S) -> Result:
    dev.tap_text("Vynulovat")
    time.sleep(2.0)
    dev.clear_logcat()
    for g in gestures:
        dev.shell(g)
        time.sleep(0.4)
    time.sleep(settle_s)
    log = dev.logcat()
    acc = testlist_accuracy(log)
    if acc is None:
        raise RuntimeError(f"{name}: no TESTLIST line — is the service enabled?")
    gt_px = acc["gt_y_px"] if axis == "y" else acc["gt_x_px"]
    measured_px = acc["eng_y_px"] if axis == "y" else acc["eng_x_px"]
    result = Result(name, gt_px, measured_px, acc["gt_mm"], acc["eng_mm"], int(acc["events"]))
    marks = [float(m["t"]) for m in map(MARK.match, log.splitlines()) if m]
    if marks:
        last_up = marks[-1]
        own = [m for m in map(EV.match, log.splitlines()) if m and m["pkg"] == PACKAGE and m["src"] in ("DIRECT_DELTA", "FALLBACK_POSITION")]
        after = [m for m in own if float(m["t"]) > last_up]
        result.after_lift_events = len(after)
        result.after_lift_px = sum(abs(int(m["ux"])) + abs(int(m["uy"])) for m in after)
    return result


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL"))
    parser.add_argument("--only", help="comma-separated test names")
    parser.add_argument("--markdown", action="store_true")
    args = parser.parse_args(argv)
    dev = Device(args.serial)

    dev.shell("input keyevent KEYCODE_WAKEUP")
    dev.shell(f"am start -W -n {ACTIVITY}")
    time.sleep(1.5)
    if not any(n.get("text") == "Vynulovat" for n in dev.nodes()):
        dev.tap_text("Testovací seznam")
        time.sleep(1.5)

    row_top, row_bottom = dev.bounds_of_text("Karta 1")[1], dev.bounds_of_text("Karta 1")[3]
    row_y = (row_top + row_bottom) // 2
    list_top = dev.bounds_of_text("Položka 1")[1]
    x = 540
    y_low = 2150  # above the gesture-navigation area on a 2400 px tall panel
    assert y_low - 1000 > list_top, f"list starts too low ({list_top})"

    tests = {
        "A500": ([slow_drag(x, y_low, x, y_low - 500)], "y", SETTLE_S),
        "A1000": ([slow_drag(x, y_low, x, y_low - 1000)], "y", SETTLE_S),
        "A5000": ([slow_drag(x, y_low, x, y_low - 1000)] * 5, "y", SETTLE_S),
        "B20": ([f"input swipe {x} {y_low} {x} {y_low - 700} 300"] * 20, "y", 5.0),
        "C_fling": ([fling(x, y_low, x, y_low - 600)], "y", 5.0),
        "C_fling3": ([fling(x, y_low, x, y_low - 600)] * 3, "y", 6.0),
        "D_slow": ([slow_drag(900, row_y, 150, row_y)], "x", SETTLE_S),
        "D_fling": ([fling(900, row_y, 200, row_y)], "x", 5.0),
        "E_reversal": ([slow_drag(x, y_low, x, y_low - 1000), slow_drag(x, y_low - 1000, x, y_low)], "y", SETTLE_S),
    }
    wanted = args.only.split(",") if args.only else list(tests)
    results = []
    for name in wanted:
        gestures, axis, settle = tests[name]
        r = run_test(dev, name, gestures, axis, settle)
        results.append(r)
        print(f"{name}: GT {r.gt_px:.0f} px / measured {r.measured_px:.0f} px · GT {r.gt_mm:.2f} mm / "
              f"measured {r.measured_mm:.2f} mm · error {r.error_pct:+.2f} % · events {r.events}"
              + (f" · after lift {r.after_lift_events} ev / {r.after_lift_px:.0f} px" if r.after_lift_events is not None else ""),
              flush=True)

    valid = [r for r in results if r.gt_mm > 0]
    if valid:
        mae = statistics.mean(abs(r.measured_mm - r.gt_mm) for r in valid)
        mape = statistics.mean(abs(r.error_pct) for r in valid)
        print(f"MAE {mae:.2f} mm · MAPE {mape:.2f} % over {len(valid)} runs")
    if args.markdown:
        print("\n| Test | GT px | Measured px | GT mm | Measured mm | Error % | Events | After lift (events / px) |")
        print("|---|---|---|---|---|---|---|---|")
        for r in results:
            lift = f"{r.after_lift_events} / {r.after_lift_px:.0f}" if r.after_lift_events is not None else "—"
            print(f"| {r.test} | {r.gt_px:.0f} | {r.measured_px:.0f} | {r.gt_mm:.2f} | {r.measured_mm:.2f} | "
                  f"{r.error_pct:+.2f} | {r.events} | {lift} |")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
