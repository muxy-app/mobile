from contextlib import redirect_stderr, redirect_stdout
import importlib.util
from io import StringIO
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SHA = "a" * 64


def load_pins():
    spec = importlib.util.spec_from_file_location("sdk_pin", ROOT / "scripts/lib/sdk_pin.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class SdkPinTest(unittest.TestCase):
    def setUp(self):
        self.module = load_pins()
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def pins(self, ios, android):
        paths = {}
        for platform, line in (("ios", ios), ("android", android)):
            path = self.directory / f"{platform}.SHA256SUMS"
            path.write_text(line + "\n")
            paths[platform] = path
        return paths

    def test_the_shared_pinned_version_is_read(self):
        pins = self.pins(
            f"{SHA}  muxy-mobile-2.5.0-beta.1321-ios.zip",
            f"{SHA}  muxy-mobile-2.5.0-beta.1321-android.zip",
        )
        self.assertEqual("2.5.0-beta.1321", self.module.pinned_version(pins))

    def test_platforms_pinning_different_releases_fail(self):
        pins = self.pins(f"{SHA}  muxy-mobile-2.5.0-ios.zip", f"{SHA}  muxy-mobile-2.4.0-android.zip")
        with self.assertRaisesRegex(ValueError, "ios pins 2.5.0, android pins 2.4.0"):
            self.module.pinned_version(pins)

    def test_malformed_or_swapped_pins_fail(self):
        for ios in (f"{SHA}  muxy-mobile-2.5.0-android.zip", "not a pin", f"{SHA} muxy-mobile-2.5.0-ios.zip"):
            with self.subTest(ios=ios):
                pins = self.pins(ios, f"{SHA}  muxy-mobile-2.5.0-android.zip")
                with self.assertRaisesRegex(ValueError, "must hold one line"):
                    self.module.pinned_version(pins)

    def test_the_repository_pins_one_release(self):
        self.assertRegex(self.module.pinned_version(), r"^2\.")

    def test_command_prints_only_the_version(self):
        output, errors = StringIO(), StringIO()
        with patch.object(self.module, "pinned_version", return_value="2.5.0"):
            with redirect_stdout(output), redirect_stderr(errors):
                self.assertEqual(0, self.module.main())
        self.assertEqual("2.5.0\n", output.getvalue())
        self.assertEqual("", errors.getvalue())

    def test_failures_do_not_emit_a_version(self):
        for failure in (FileNotFoundError("missing"), ValueError("different releases")):
            with self.subTest(failure=failure):
                output, errors = StringIO(), StringIO()
                with patch.object(self.module, "pinned_version", side_effect=failure):
                    with redirect_stdout(output), redirect_stderr(errors):
                        self.assertEqual(1, self.module.main())
                self.assertEqual("", output.getvalue())
                self.assertIn("Cannot read the pinned Muxy SDK:", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
