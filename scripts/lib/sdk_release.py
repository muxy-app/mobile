from collections.abc import Iterable
import json
import re
import subprocess
import sys


RELEASE_PATTERN = re.compile(r"v(2\.[0-9]+\.[0-9]+)(-beta(?:[.-]([0-9]+))?)?")


def select_release(releases: Iterable[dict]) -> str:
    candidates = []
    for release in releases:
        match = RELEASE_PATTERN.fullmatch(release["tag_name"])
        if release["draft"] or match is None:
            continue
        stable = match[2] is None
        if stable and release["prerelease"]:
            continue
        version = tuple(int(part) for part in match[1].split("."))
        candidates.append(((stable, *version, int(match[3] or 0)), release))

    if not candidates:
        raise ValueError("No stable 2.x or beta 2.x Muxy release is available")

    release = max(candidates, key=lambda candidate: candidate[0])[1]
    version = release["tag_name"][1:]
    required_assets = {"SHA256SUMS", f"muxy-mobile-{version}-ios.zip", f"muxy-mobile-{version}-android.zip"}
    missing_assets = required_assets - {asset["name"] for asset in release["assets"]}
    if missing_assets:
        raise ValueError(f"Muxy {version} is missing release assets: {', '.join(sorted(missing_assets))}")
    return version


def resolve_release() -> str:
    result = subprocess.run(
        ["gh", "api", "--paginate", "--slurp", "repos/muxy-app/muxy/releases?per_page=100"],
        check=True, capture_output=True, text=True,
    )
    pages = json.loads(result.stdout)
    return select_release(release for page in pages for release in page)


def main() -> int:
    try:
        print(resolve_release())
        return 0
    except subprocess.CalledProcessError as exc:
        print(f"Cannot resolve Muxy SDK release: {exc.stderr.strip()}", file=sys.stderr)
        return 1
    except (OSError, ValueError) as exc:
        print(f"Cannot resolve Muxy SDK release: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
