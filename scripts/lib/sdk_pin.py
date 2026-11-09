from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
PINS = {
    "ios": ROOT / "ios/MuxyMobileSDK/SHA256SUMS",
    "android": ROOT / "android/MuxyMobileSDK/SHA256SUMS",
}
PIN_PATTERN = re.compile(r"[0-9a-f]{64}  muxy-mobile-([0-9A-Za-z.-]+)-(ios|android)\.zip")


def pinned_version(pins: dict[str, Path] = PINS) -> str:
    versions = {}
    for platform, path in pins.items():
        match = PIN_PATTERN.fullmatch(path.read_text().strip())
        if match is None or match[2] != platform:
            raise ValueError(f"{path} must hold one line: <sha256>  muxy-mobile-<version>-{platform}.zip")
        versions[platform] = match[1]
    if len(set(versions.values())) != 1:
        pinned = ", ".join(f"{platform} pins {version}" for platform, version in versions.items())
        raise ValueError(f"The apps pin different Muxy SDK releases ({pinned}). Pin the same release on both.")
    return next(iter(versions.values()))


def main() -> int:
    try:
        print(pinned_version())
        return 0
    except (OSError, ValueError) as exc:
        print(f"Cannot read the pinned Muxy SDK: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
