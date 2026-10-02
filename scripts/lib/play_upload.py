#!/usr/bin/env python3
"""Upload an AAB to Google Play via the Android Publisher API.

Used by scripts/release-android.sh in place of `fastlane supply`.
"""

import argparse
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import BadZipFile, ZIP_DEFLATED, ZipFile

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload


def bundle_symbols(aab_path: str, directory: Path) -> tuple[Path, Path]:
    mapping = directory / "mapping.txt"
    symbols = directory / "native-debug-symbols.zip"
    prefix = "BUNDLE-METADATA/com.android.tools.build.debugsymbols/"
    with ZipFile(aab_path) as bundle:
        mapping.write_bytes(bundle.read("BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map"))
        entries = [entry for entry in bundle.infolist() if entry.filename.startswith(prefix) and not entry.is_dir()]
        if not entries or mapping.stat().st_size == 0:
            raise ValueError("AAB must contain R8 mapping and native debug symbols")
        with ZipFile(symbols, "w", compression=ZIP_DEFLATED) as archive:
            for entry in entries:
                name = entry.filename.removeprefix(prefix)
                parts = name.split("/")
                if len(parts) != 2 or any(part in ("", ".", "..") for part in parts):
                    raise ValueError("Invalid native symbol path in AAB")
                archive.writestr(name, bundle.read(entry))
    return mapping, symbols


def upload(aab_path: str, package_name: str, track: str, json_key: str, mapping: Path, symbols: Path) -> int:
    credentials = service_account.Credentials.from_service_account_file(
        json_key, scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    service = build("androidpublisher", "v3", credentials=credentials)
    edits = service.edits()

    print(f"Creating edit for {package_name}...")
    edit = edits.insert(packageName=package_name, body={}).execute()
    edit_id = edit["id"]

    try:
        print(f"Uploading {aab_path}...")
        media = MediaFileUpload(
            aab_path,
            mimetype="application/octet-stream",
            resumable=True,
        )
        bundle = edits.bundles().upload(
            packageName=package_name,
            editId=edit_id,
            media_body=media,
        ).execute()
        version_code = bundle["versionCode"]
        print(f"Uploaded versionCode {version_code}.")

        for kind, path in (("proguard", mapping), ("nativeCode", symbols)):
            print(f"Uploading {kind} symbols...")
            edits.deobfuscationfiles().upload(
                packageName=package_name,
                editId=edit_id,
                apkVersionCode=version_code,
                deobfuscationFileType=kind,
                media_body=MediaFileUpload(str(path), mimetype="application/octet-stream"),
            ).execute()

        print(f"Assigning to track '{track}' as draft...")
        edits.tracks().update(
            packageName=package_name,
            editId=edit_id,
            track=track,
            body={
                "track": track,
                "releases": [
                    {
                        "status": "draft",
                        "versionCodes": [str(version_code)],
                    }
                ],
            },
        ).execute()

        print("Committing edit...")
        edits.commit(
            packageName=package_name,
            editId=edit_id,
            changesNotSentForReview=(track == "production"),
        ).execute()

        print(f"Uploaded {aab_path} to {package_name} on track '{track}' as draft.")
        return 0
    except HttpError as exc:
        print(f"Play API error: {exc}", file=sys.stderr)
        try:
            edits.delete(packageName=package_name, editId=edit_id).execute()
        except HttpError:
            pass
        return 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aab", required=True)
    parser.add_argument("--package-name", required=True)
    parser.add_argument("--track", required=True, choices=("internal", "alpha", "production"))
    parser.add_argument("--json-key", required=True)
    args = parser.parse_args()
    with TemporaryDirectory(prefix="muxy-play-") as directory:
        try:
            mapping, symbols = bundle_symbols(args.aab, Path(directory))
        except (OSError, BadZipFile, KeyError, ValueError) as exc:
            print(f"Cannot read release symbols: {exc}", file=sys.stderr)
            return 1
        return upload(args.aab, args.package_name, args.track, args.json_key, mapping, symbols)


if __name__ == "__main__":
    sys.exit(main())
