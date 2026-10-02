from contextlib import redirect_stderr, redirect_stdout
import importlib.util
from io import StringIO
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]


def load_selector():
    spec = importlib.util.spec_from_file_location("sdk_release", ROOT / "scripts/lib/sdk_release.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def release(tag, draft=False, prerelease=None, assets=None):
    version = tag[1:]
    if assets is None:
        assets = ["SHA256SUMS", f"muxy-mobile-{version}-ios.zip", f"muxy-mobile-{version}-android.zip"]
    return {
        "tag_name": tag,
        "draft": draft,
        "prerelease": "-" in tag if prerelease is None else prerelease,
        "assets": [{"name": name} for name in assets],
    }


class SdkReleaseTest(unittest.TestCase):
    def setUp(self):
        self.selector = load_selector()

    def test_stable_is_preferred_over_a_higher_beta(self):
        releases = [release("v2.5.0-beta-1098"), release("v2.0.0")]
        self.assertEqual("2.0.0", self.selector.select_release(releases))

    def test_highest_stable_version_is_selected_numerically(self):
        releases = [release(tag) for tag in ("v2.9.99", "v2.10.1", "v2.10.0", "v2.9.100")]
        self.assertEqual("2.10.1", self.selector.select_release(releases))

    def test_highest_beta_number_is_selected_without_a_stable(self):
        for separator in ("-", "."):
            with self.subTest(separator=separator):
                tags = [f"v2.0.0-beta{separator}{number}" for number in (99, 1098, 9, 100)]
                releases = [release(tag) for tag in tags]
                self.assertEqual(f"2.0.0-beta{separator}1098", self.selector.select_release(releases))

    def test_beta_base_version_takes_precedence_over_beta_number(self):
        releases = [release("v2.0.0-beta-1098"), release("v2.1.0-beta.1")]
        self.assertEqual("2.1.0-beta.1", self.selector.select_release(releases))

    def test_unnumbered_beta_is_eligible(self):
        self.assertEqual("2.0.0-beta", self.selector.select_release([release("v2.0.0-beta")]))

    def test_drafts_other_majors_and_non_beta_prereleases_are_ignored(self):
        releases = [
            release("v2.99.0", draft=True),
            release("v2.99.0-beta-1", draft=True),
            release("v1.99.0"),
            release("v3.0.0"),
            release("v2.50.0-alpha.1"),
            release("v2.50.0-rc.1"),
            release("v2.100.0", prerelease=True),
            release("v2.0.0-beta-1098"),
        ]
        self.assertEqual("2.0.0-beta-1098", self.selector.select_release(releases))

    def test_beta_tag_is_not_stable_even_when_marked_as_stable(self):
        releases = [release("v2.5.0-beta-1098", prerelease=False), release("v2.0.0")]
        self.assertEqual("2.0.0", self.selector.select_release(releases))

    def test_no_eligible_release_fails(self):
        for releases in ([], [release("v1.0.0"), release("v3.0.0"), release("v2.0.0-rc.1")]):
            with self.subTest(releases=releases):
                with self.assertRaisesRegex(ValueError, "No stable 2.x or beta 2.x"):
                    self.selector.select_release(releases)

    def test_missing_selected_assets_fail_instead_of_falling_back(self):
        assets = ["SHA256SUMS", "muxy-mobile-2.1.0-ios.zip", "muxy-mobile-2.1.0-android.zip"]
        for missing in assets:
            with self.subTest(missing=missing):
                releases = [
                    release("v2.1.0", assets=[asset for asset in assets if asset != missing]),
                    release("v2.0.0"),
                    release("v2.2.0-beta-1"),
                ]
                with self.assertRaises(ValueError) as failure:
                    self.selector.select_release(releases)
                self.assertIn("Muxy 2.1.0", str(failure.exception))
                self.assertIn(missing, str(failure.exception))

    def test_missing_beta_assets_do_not_select_an_older_beta(self):
        releases = [release("v2.0.0-beta-10", assets=[]), release("v2.0.0-beta-9")]
        with self.assertRaisesRegex(ValueError, "Muxy 2.0.0-beta-10 is missing"):
            self.selector.select_release(releases)

    def test_stable_releases_are_found_on_later_api_pages(self):
        pages = [[release("v2.5.0-beta-1098")], [release("v2.0.0")]]
        result = subprocess.CompletedProcess([], 0, stdout=json.dumps(pages))
        with patch.object(self.selector.subprocess, "run", return_value=result) as run:
            self.assertEqual("2.0.0", self.selector.resolve_release())
        run.assert_called_once_with(
            ["gh", "api", "--paginate", "--slurp", "repos/muxy-app/muxy/releases?per_page=100"],
            check=True, capture_output=True, text=True,
        )

    def test_command_prints_only_the_resolved_version(self):
        output, errors = StringIO(), StringIO()
        with patch.object(self.selector, "resolve_release", return_value="2.1.0"):
            with redirect_stdout(output), redirect_stderr(errors):
                self.assertEqual(0, self.selector.main())
        self.assertEqual("2.1.0\n", output.getvalue())
        self.assertEqual("", errors.getvalue())

    def test_lookup_failures_do_not_emit_a_version(self):
        failures = [
            subprocess.CalledProcessError(1, "gh", stderr="API rate limit exceeded"),
            FileNotFoundError("gh is not installed"),
            json.JSONDecodeError("Invalid release response", "", 0),
            ValueError("No eligible release"),
        ]
        for failure in failures:
            with self.subTest(failure=failure):
                output, errors = StringIO(), StringIO()
                with patch.object(self.selector, "resolve_release", side_effect=failure):
                    with redirect_stdout(output), redirect_stderr(errors):
                        self.assertEqual(1, self.selector.main())
                self.assertEqual("", output.getvalue())
                self.assertIn("Cannot resolve Muxy SDK release:", errors.getvalue())


if __name__ == "__main__":
    unittest.main()
