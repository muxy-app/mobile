import os
from pathlib import Path
import shutil
import subprocess
from tempfile import TemporaryDirectory
import unittest


ROOT = Path(__file__).resolve().parents[2]


class IOSToolchainTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.tools = self.directory / "bin"
        self.tools.mkdir()
        self.calls = self.directory / "calls"
        self.env_loaded = self.directory / "env-loaded"
        for name in (
            "ios/scripts/check-xcode.sh", "ios/scripts/run.sh",
            "scripts/release-ios.sh", "scripts/lib/common.sh",
        ):
            destination = self.directory / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / name, destination)
        (self.directory / ".env").write_text('touch "$ENV_LOADED"\nexit 90\n')
        self.tool("xcrun", 'printf "%s\\n" "$*" > "$CALLS"\nprintf "%s\\n" "$SDK_VERSION"\nexit "${SDK_EXIT:-0}"\n')
        self.tool("xcodebuild", 'printf "%s\\n" "$*" >> "$BUILD_CALLS"\n')

    def tool(self, name, body):
        path = self.tools / name
        path.write_text("#!/bin/bash\n" + body)
        path.chmod(0o755)

    def run_script(self, script="ios/scripts/check-xcode.sh", *arguments, version="27.1", **environment):
        return subprocess.run(
            ["/bin/bash", str(self.directory / script), *arguments],
            cwd=self.directory,
            env={
                **os.environ, "PATH": f"{self.tools}:{os.environ['PATH']}",
                "SDK_VERSION": version, "SDK_EXIT": "0", "CALLS": str(self.calls),
                "BUILD_CALLS": str(self.directory / "build-calls"), "ENV_LOADED": str(self.env_loaded),
                **environment,
            },
            capture_output=True, text=True,
        )

    def test_accepts_duo_sdks_and_newer_versions(self):
        for version in ("27.1", "27.1.0", "27.2", "27.10", "28.0"):
            with self.subTest(version=version):
                result = self.run_script(version=version)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn(f"Using iOS SDK {version}", result.stdout)
                self.assertEqual("--sdk iphoneos --show-sdk-version\n", self.calls.read_text())

    def test_rejects_compatibility_mode_sdks(self):
        for version in ("26.2", "26.3", "26.6", "27.0", "27.0.1"):
            with self.subTest(version=version):
                result = self.run_script(version=version)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("iOS SDK 27.1 or newer is required", result.stderr)
                self.assertIn("DEVELOPER_DIR", result.stderr)

    def test_rejects_unknown_sdk_versions(self):
        for version in ("", "unknown", "27", "27.1beta", "27.1\n26.3", "27.1;false"):
            with self.subTest(version=version):
                result = self.run_script(version=version)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Cannot determine the iOS SDK version", result.stderr)

    def test_sdk_lookup_failure_does_not_pass(self):
        result = self.run_script(SDK_EXIT="7")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Select Xcode 27.1 or newer", result.stderr)
        self.assertNotIn("Using iOS SDK", result.stdout)

    def test_runner_rejects_old_sdk_before_building(self):
        result = self.run_script("ios/scripts/run.sh", "build", version="26.3")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("iOS SDK 27.1 or newer is required", result.stderr)
        self.assertEqual("-version\n", (self.directory / "build-calls").read_text())

    def test_local_release_rejects_old_sdk_before_loading_secrets(self):
        result = self.run_script("scripts/release-ios.sh", "3.0.0", "42", version="26.3")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("iOS SDK 27.1 or newer is required", result.stderr)
        self.assertFalse(self.env_loaded.exists())
        self.assertFalse((self.directory / "build-calls").exists())

    def test_release_workflow_selects_and_validates_duo_toolchain(self):
        workflow = (ROOT / ".github/workflows/ios-release.yml").read_text()
        self.assertIn("    runs-on: xcode-27\n", workflow)
        self.assertIn("sudo xcode-select -s /Applications/Xcode_27.1.app\n", workflow)
        self.assertLess(workflow.index("xcode-select"), workflow.index("ios/scripts/check-xcode.sh"))
        self.assertLess(workflow.index("ios/scripts/check-xcode.sh"), workflow.index("- name: Install Muxy SDK"))


if __name__ == "__main__":
    unittest.main()
