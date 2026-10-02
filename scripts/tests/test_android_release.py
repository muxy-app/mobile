import importlib.util
import os
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import MagicMock, patch
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]


def load_uploader():
    modules = {name: MagicMock() for name in (
        "google", "google.oauth2", "google.oauth2.service_account",
        "googleapiclient", "googleapiclient.discovery", "googleapiclient.errors", "googleapiclient.http",
    )}
    modules["googleapiclient.errors"].HttpError = type("HttpError", (Exception,), {})
    spec = importlib.util.spec_from_file_location("play_upload", ROOT / "scripts/lib/play_upload.py")
    module = importlib.util.module_from_spec(spec)
    with patch.dict(sys.modules, modules):
        spec.loader.exec_module(module)
    return module


class ReleaseInputsTest(unittest.TestCase):
    def validate(self, version="3.0.0", code="1788620581", track="auto"):
        return subprocess.run(
            ["/bin/bash", "-c", 'set -euo pipefail; source "$1"; validate_release_inputs; printf "%s:%s" "$VERSION_CODE" "$TRACK"',
             "release-inputs", str(ROOT / "android/scripts/release-inputs.sh")],
            env={**os.environ, "VERSION_NAME": version, "VERSION_CODE": code, "TRACK": track},
            capture_output=True, text=True,
        )

    def test_automatic_track_rule(self):
        self.assertEqual("1788620581:production", self.validate().stdout)
        self.assertEqual("1788620581:alpha", self.validate(version="0.9.0").stdout)
        self.assertEqual("1788620581:alpha", self.validate(version="00.9.0").stdout)

    def test_explicit_tracks(self):
        for track in ("internal", "alpha", "production"):
            with self.subTest(track=track):
                self.assertEqual(f"1788620581:{track}", self.validate(track=track).stdout)

    def test_version_code_bounds(self):
        self.assertEqual(0, self.validate(code="2100000000").returncode)
        for code in ("4", "1788620580", "2100000001", "0000000001", "999999999999999999", "1+2", "$(false)"):
            with self.subTest(code=code):
                self.assertNotEqual(0, self.validate(code=code).returncode)

    def test_missing_code_uses_timestamp(self):
        result = self.validate(code="")
        self.assertEqual(0, result.returncode)
        self.assertRegex(result.stdout, r"^[1-9][0-9]{9}:production$")

    def test_invalid_versions_and_tracks_fail(self):
        for version in ("", "v3.0.0", "3.0", "3.0.0-beta", "3.0.0;false"):
            with self.subTest(version=version):
                self.assertNotEqual(0, self.validate(version=version).returncode)
        self.assertNotEqual(0, self.validate(track="beta").returncode)


class NdkInstallTest(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.tools = self.directory / "cmdline-tools/latest/bin"
        self.tools.mkdir(parents=True)
        self.calls = self.directory / "calls"
        self.ndk = next(line.split('"')[1] for line in (ROOT / "android/app/build.gradle.kts").read_text().splitlines() if "ndkVersion =" in line)

    def tool(self, name):
        path = self.tools / name
        path.write_text('#!/bin/bash\nprintf "%s\\n" "${0##*/}" "$@" > "$CALLS"\nexit "${TOOL_EXIT:-0}"\n')
        path.chmod(0o755)

    def install(self, **environment):
        return subprocess.run(
            ["/bin/bash", str(ROOT / "android/scripts/install-ndk.sh")],
            env={**os.environ, "ANDROID_HOME": str(self.directory), "CALLS": str(self.calls), **environment},
            capture_output=True, text=True,
        )

    def test_classic_runner_uses_sdkmanager(self):
        self.tool("sdkmanager")
        self.assertEqual(0, self.install().returncode)
        self.assertEqual(["sdkmanager", f"ndk;{self.ndk}"], self.calls.read_text().splitlines())

    def test_new_tools_disable_metrics_without_using_wrapper(self):
        self.tool("android")
        self.tool("sdkmanager")
        self.assertEqual(0, self.install().returncode)
        self.assertEqual(["android", "--no-metrics", "sdk", "install", f"ndk;{self.ndk}"], self.calls.read_text().splitlines())

    def test_installation_failure_propagates_without_falling_back(self):
        self.tool("android")
        self.tool("sdkmanager")
        self.assertEqual(7, self.install(TOOL_EXIT="7").returncode)
        self.assertEqual("android", self.calls.read_text().splitlines()[0])

    def test_missing_sdk_or_tools_fails(self):
        self.assertNotEqual(0, self.install(ANDROID_HOME="").returncode)
        self.assertNotEqual(0, self.install().returncode)
        self.assertFalse(self.calls.exists())


class PlayUploadTest(unittest.TestCase):
    def setUp(self):
        self.uploader = load_uploader()
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.bundle = self.directory / "app.aab"

    def write_bundle(self, mapping=b"mapping", symbols=None):
        if symbols is None:
            symbols = {"arm64-v8a/libmuxy_mobile.so.dbg": b"debug"}
        with ZipFile(self.bundle, "w") as bundle:
            if mapping is not None:
                bundle.writestr("BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map", mapping)
            for name, value in symbols.items():
                bundle.writestr("BUNDLE-METADATA/com.android.tools.build.debugsymbols/" + name, value)
            bundle.writestr("base/lib/arm64-v8a/libmuxy_mobile.so", b"runtime")

    def test_extracts_matching_mapping_and_symbols_from_bundle(self):
        self.write_bundle()
        mapping, symbols = self.uploader.bundle_symbols(str(self.bundle), self.directory)
        self.assertEqual(b"mapping", mapping.read_bytes())
        with ZipFile(symbols) as archive:
            self.assertEqual(["arm64-v8a/libmuxy_mobile.so.dbg"], archive.namelist())
            self.assertEqual(b"debug", archive.read(archive.namelist()[0]))

    def test_rejects_missing_or_empty_symbols(self):
        for mapping, symbols in ((None, {}), (b"", None), (b"mapping", {})):
            with self.subTest(mapping=mapping, symbols=symbols):
                self.write_bundle(mapping, symbols)
                with self.assertRaises((KeyError, ValueError)):
                    self.uploader.bundle_symbols(str(self.bundle), self.directory)

    def test_rejects_unsafe_symbol_paths(self):
        for name in ("../escape", "/escape", "./escape", "arm64-v8a//file", "arm64-v8a/subdir/file", "file"):
            with self.subTest(name=name):
                self.write_bundle(symbols={name: b"debug"})
                with self.assertRaises(ValueError):
                    self.uploader.bundle_symbols(str(self.bundle), self.directory)

    def test_uploads_both_symbol_types_before_draft_commit(self):
        edits = self.uploader.build.return_value.edits.return_value
        edits.insert.return_value.execute.return_value = {"id": "edit"}
        edits.bundles.return_value.upload.return_value.execute.return_value = {"versionCode": 1788620581}
        for track in ("internal", "production"):
            edits.reset_mock()
            result = self.uploader.upload("app.aab", "com.muxy.app", track, "key.json", Path("mapping.txt"), Path("symbols.zip"))
            self.assertEqual(0, result)
            calls = edits.deobfuscationfiles.return_value.upload.call_args_list
            self.assertEqual(["proguard", "nativeCode"], [call.kwargs["deobfuscationFileType"] for call in calls])
            self.assertTrue(all(call.kwargs["apkVersionCode"] == 1788620581 for call in calls))
            release = edits.tracks.return_value.update.call_args.kwargs["body"]["releases"][0]
            self.assertEqual({"status": "draft", "versionCodes": ["1788620581"]}, release)
            self.assertEqual(track == "production", edits.commit.call_args.kwargs["changesNotSentForReview"])
            names = [call[0] for call in edits.mock_calls]
            self.assertLess(names.index("deobfuscationfiles().upload"), names.index("commit"))

    def test_symbol_upload_failure_deletes_edit_without_commit(self):
        edits = self.uploader.build.return_value.edits.return_value
        edits.insert.return_value.execute.return_value = {"id": "edit"}
        edits.bundles.return_value.upload.return_value.execute.return_value = {"versionCode": 1788620581}
        edits.deobfuscationfiles.return_value.upload.side_effect = self.uploader.HttpError("failed")
        self.assertEqual(1, self.uploader.upload("app.aab", "com.muxy.app", "internal", "key.json", Path("mapping"), Path("symbols")))
        edits.commit.assert_not_called()
        edits.delete.assert_called_once_with(packageName="com.muxy.app", editId="edit")


if __name__ == "__main__":
    unittest.main()
