import io
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import accuracy as tool  # noqa: E402


class AccuracyTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.root = Path(self.dir.name)

    def tearDown(self):
        self.dir.cleanup()

    def write(self, name: str, text: str) -> Path:
        path = self.root / name
        path.write_text(text, encoding="utf-8")
        return path

    def test_mae_and_mape_follow_spec_38(self):
        rows = [tool.Comparison("a", 100.0, 98.0), tool.Comparison("b", 200.0, 210.0)]
        self.assertAlmostEqual(tool.mae(rows), 6.0)
        self.assertAlmostEqual(tool.mape(rows), 3.5)

    def test_zero_ground_truth_counts_for_mae_only(self):
        rows = [tool.Comparison("a", 0.0, 5.0), tool.Comparison("b", 100.0, 99.0)]
        self.assertAlmostEqual(tool.mae(rows), 3.0)
        self.assertAlmostEqual(tool.mape(rows), 1.0)
        self.assertIsNone(tool.mape([tool.Comparison("a", 0.0, 1.0)]))
        self.assertIsNone(tool.mae([]))

    def test_rows_are_paired_by_key_and_unpaired_keys_are_reported(self):
        rows, no_measurement, no_truth = tool.compare({"a": 1.0, "b": 2.0}, {"b": 2.5, "c": 3.0})
        self.assertEqual([r.key for r in rows], ["b"])
        self.assertEqual(no_measurement, ["a"])
        self.assertEqual(no_truth, ["c"])

    def test_cli_prints_the_summary_and_exits_zero_when_everything_pairs(self):
        truth = self.write("gt.csv", "test,ground_truth_mm\nview/A500,49.65\nview/A1000,105.51\n")
        measured = self.write("m.csv", "test,measured_mm\nview/A1000,105.00\nview/A500,49.65\n")
        out = io.StringIO()
        with redirect_stdout(out):
            code = tool.main([str(truth), str(measured)])
        self.assertEqual(code, 0)
        self.assertIn("2 runs · MAE 0.26 mm · MAPE 0.24 %", out.getvalue())

    def test_cli_fails_on_an_unpaired_key(self):
        truth = self.write("gt.csv", "test,ground_truth_mm\na,10\nb,20\n")
        measured = self.write("m.csv", "test,measured_mm\na,10\n")
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()) as err:
            self.assertEqual(tool.main([str(truth), str(measured)]), 1)
        self.assertIn("no measurement for: b", err.getvalue())

    def test_missing_column_or_repeated_key_is_an_error(self):
        bad = self.write("bad.csv", "test,value\na,1\n")
        twice = self.write("twice.csv", "test,measured_mm\na,1\na,2\n")
        with self.assertRaises(ValueError):
            tool.load(bad, "test", "measured_mm")
        with self.assertRaises(ValueError):
            tool.load(twice, "test", "measured_mm")
        with redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            self.assertEqual(tool.main([str(bad), str(twice)]), 2)


if __name__ == "__main__":
    unittest.main()
