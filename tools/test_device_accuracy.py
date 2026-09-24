import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import accuracy  # noqa: E402
import device_accuracy as tool  # noqa: E402
from analyze_debug_csv import testlist_accuracy  # noqa: E402

LINE = ("09-24 10:00:00.000 D ScrollMeter: TESTLIST surface=VIEW gt_x_px=0.0 gt_y_px=1666.0 eng_x_px=0 eng_y_px=1666 "
        "gt_mm=104.859 eng_mm=104.859 eng_events=8 scale_method=MANUAL_CARD calibration_version=2 "
        "mm_per_px_x=0.062941 mm_per_px_y=0.062941")


class DeviceAccuracyTest(unittest.TestCase):
    def test_testlist_line_with_calibration_still_parses(self):
        acc = testlist_accuracy(LINE)
        self.assertEqual(acc["gt_y_px"], 1666.0)
        self.assertEqual(acc["eng_mm"], 104.859)
        m = tool.SCALE.search(LINE)
        self.assertEqual((m["method"], m["version"], m["y"]), ("MANUAL_CARD", "2", "0.062941"))

    def test_csv_pair_round_trips_through_accuracy(self):
        results = [
            tool.Result("view", "A500", 784, 784, 49.65, 49.65, 6),
            tool.Result("column", "A500", 476, 458, 30.15, 29.01, 4),
        ]
        with tempfile.TemporaryDirectory() as d:
            tool.write_csvs(Path(d), results)
            truth = accuracy.load(Path(d) / "ground_truth.csv", "test", "ground_truth_mm")
            measured = accuracy.load(Path(d) / "measured.csv", "test", "measured_mm")
        rows, no_measurement, no_truth = accuracy.compare(truth, measured)
        self.assertEqual([r.key for r in rows], ["view/A500", "column/A500"])
        self.assertEqual((no_measurement, no_truth), ([], []))
        self.assertAlmostEqual(accuracy.mae(rows), (0.0 + 1.14) / 2)


if __name__ == "__main__":
    unittest.main()
