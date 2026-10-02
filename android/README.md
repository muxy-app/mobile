# Muxy for Android

## Requirements

- JDK 21, such as Temurin 21, with `JAVA_HOME` pointing to it. Gradle runs on it.
- The Android SDK in `ANDROID_HOME`, with Platform 37 (`platforms;android-37.0`), Build Tools, Platform Tools and the Emulator. The build downloads a missing platform when its license has been accepted.
- An emulator (AVD) with a Google Play system image, or a phone with USB or wireless debugging.
- Internet access to download the Muxy SDK and, on the first build, Gradle and the dependencies. Android Studio is optional.

Run the following commands from `android/`, or prefix them with `android/` from the repository root.

## Muxy SDK

The app embeds the Muxy mobile SDK, a Rust library with generated Kotlin bindings that load through JNA. The SDK isn't committed, because its libraries are too large for git. Instead, `MuxyMobileSDK/SHA256SUMS` pins the Muxy release it comes from and the checksum of its Android download. Install it before the first app build, and again whenever the pin changes:

```sh
scripts/sdk.sh install
```

The script checks the download against the pin and writes the SDK to `MuxyMobileSDK/Installed/`, with a `REVISION` file naming its version. The build stops when the SDK is missing, and the runner tells you when it isn't the pinned release.

The app connects to any Muxy build that shares a protocol version with its SDK. `muxy --build-info` prints a computer's `protocol` versions, and each Muxy release lists its SDK's in `muxy-mobile-<version>.json`. Pin a newer release by its version, such as `2.0.0-beta-1090`, when the protocol version changes or to use newer SDK features, then build and test the app. Keep the iOS and Android pins on the same Muxy release.

```sh
scripts/sdk.sh pin <version>
```

To try SDK changes that aren't released yet, build the SDK from a Muxy 2 checkout. This needs `rustup`, `cargo-ndk`, an Android NDK in `ANDROID_NDK_HOME`, and Xcode, because Muxy's build script also builds the iOS SDK. It takes a few minutes.

```sh
scripts/sdk.sh build ~/Projects/muxy
```

`scripts/sdk.sh install` switches back to the pinned release. If the app crashes with a UniFFI checksum mismatch after switching, run `./gradlew clean` and build again.

## Emulator

```sh
./scripts/run.sh
```

The runner uses the running emulator. If none is running, it boots the first AVD in `emulator -list-avds` and waits for it to finish booting. It builds the debug app, installs it and launches Muxy.

If no AVD exists, create one with a Google Play system image in Android Studio's Device Manager, or with `avdmanager`.

To choose an emulator:

```sh
AVD_NAME="Muxy_API_36" ./scripts/run.sh
DEVICE_SERIAL="emulator-5556" ./scripts/run.sh
```

`AVD_NAME` boots that AVD when it isn't running. `DEVICE_SERIAL` selects a running emulator or a phone and takes precedence over `AVD_NAME`.

## Phone

1. Enable **Developer options** on the phone, then turn on **USB debugging**, or **Wireless debugging**.
2. For wireless debugging, pair once with `adb pair <ip:port>`, then connect with `adb connect <ip:port>`.
3. Accept the debugging prompt on the phone and run:

```sh
./scripts/run.sh device
```

The runner selects the only connected phone. To choose among several:

```sh
./scripts/run.sh devices
./scripts/run.sh device "<serial>"
```

Debug builds install as `com.muxy.app` and are signed with your debug key. Android won't install one over the Play Store version, because their signatures differ; uninstall that first, which deletes its data.

## Terminal emulator

Muxy 1 and SSH terminals run on Termux's `terminal-emulator` library, vendored as the `:terminal-emulator` module under the Apache License 2.0. [terminal-emulator/NOTICE.md](terminal-emulator/NOTICE.md) names the upstream tag and lists the changes. The module keeps its upstream sources and tests and is excluded from ktlint and the rule that rejects comments.

## Other commands

| Command | Action |
| --- | --- |
| `./scripts/run.sh build` | Build the debug APK without a device |
| `./scripts/run.sh test` | Run the JVM unit tests, including the vendored terminal emulator's |
| `./scripts/run.sh test-device` | Run the instrumented tests in the emulator |
| `./scripts/run.sh stop` | Stop Muxy in the emulator |
| `./scripts/run.sh restart` | Stop, rebuild and relaunch Muxy in the emulator |
| `./scripts/run.sh logs` | Follow Muxy's logs and crashes |
| `./scripts/run.sh devices` | List emulators and connected devices |
| `./scripts/run.sh help` | Show commands and environment variables |
| `./scripts/checks.sh` | Run ktlint, including the rule that rejects comments, and Android Lint |

Build outputs are stored in each module's `build/` folder. The first build downloads Gradle and the dependencies and can take several minutes.

## Release builds

The **Release Android** workflow builds the Kotlin app from `android/`. Its optional `track` input selects `internal`, `alpha`, or `production`; `auto` preserves the default of production for 1.x or later and alpha for 0.x. Uploads are drafts. Use `internal` for the first native release, then promote the verified build in Play Console. The combined **Release** workflow keeps the automatic track rule.

`./gradlew :app:assembleRelease` builds an APK with R8; `:app:bundleRelease` builds the Play AAB. `-PversionName` and `-PversionCode` set the version, and the build signs with the upload key when `ANDROID_SIGNING_KEY_PATH`, `ANDROID_KEY_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` are all set. A relative key path is resolved from the repository root, as in `.env.example`. With none of them set, the APK is unsigned. Releases must use the existing Play upload key.

NDK `30.0.16248370` strips the packaged native libraries and extracts full debug symbols. Install it with `android --no-metrics sdk install 'ndk;30.0.16248370'`; AGP can also install it when its license is accepted. CI retains the AAB, `app/build/outputs/mapping/release/mapping.txt`, and `app/build/outputs/native-debug-symbols/release/native-debug-symbols.zip`, and uploads all three to Play. Release notes include the installed Muxy SDK version.

From the repository root, the local script reads the same signing values and Play service account path from `.env`:

```sh
scripts/release-android.sh --track internal 3.0.0
scripts/release-android.sh --upload --track internal 3.0.0
scripts/release-android.sh --upload-only --track internal path/to/app-release.aab
```

The first command asks before uploading when Play credentials are configured; `--upload` and `--upload-only` request an upload without that prompt. Version codes default to Unix timestamps and must be greater than the final RN upload, `1788620580`, and no greater than Play's `2100000000` limit. Upload-only requires `bundletool` (`brew install bundletool`), reads the version and package from the AAB, and takes mapping and symbols from that AAB rather than potentially stale build outputs.

Before uploading, inspect the AAB with `bundletool dump manifest --bundle=app/build/outputs/bundle/release/app-release.aab`, verify 16 KB alignment, and run on API 29 and API 37. Target API 36 meets [Play's August 2026 update requirement](https://support.google.com/googleplay/android-developer/answer/11926878). Keep target 36 until the local-network permission changes for target 37 are implemented. Update Play's Data safety declarations and release notes; Android 7–9 users retain the old app but cannot receive this update.

## React Native upgrade check

The one-time importer reads the old SQLite and SecureStore data before screens, deep links, billing refresh, or demo syncing can consume it. It preserves Muxy 1 connection IDs, approval credentials, workspace selections, the trial timestamp, and the four RN settings. Demo connections are regenerated from the setting. Invalid records are skipped; a missing or invalid token requires pairing again. Purchases restore through Play, not local migration. Old stores and obsolete Keystore aliases are removed after import; a failed cleanup can retry without importing again.

Use a separate disposable emulator so the test does not overwrite your normal Muxy data. A phone is not required. See [upgrade-check.md](upgrade-check.md) for the release-APK build and install sequence. The retired RN source is available at `v2.5.2`; build it only in a separate worktree. Do not run `expo prebuild` in this checkout: it would replace the Kotlin project.

JVM tests use saved JSON fixtures. Instrumented tests create SQLite and real Android Keystore fixtures, exercise cleanup, and verify the durable once-only marker. Run them on a disposable emulator: `run.sh test-device` uninstalls the tested app by default. Release helpers have offline tests, runnable from the repository root with `PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests -v`.

## Trial and unlock

The 3-day trial starts on the first app launch after installation, before adding any connections. Its timestamp is kept in encrypted, non-backed-up storage and isn't reset by restarting, updating, or pairing. Once expired, opening any connection, including SSH and demo, shows the paywall. The one-time Google Play product `muxy_unlock` restores access; pending purchases do not unlock.

Billing is enforced in release builds. Debug builds bypass it unless you enable the visual-test overrides:

```sh
./gradlew :app:assembleDebug -PmuxyBillingEnforced=true -PmuxyTrialMinutes=2
```

When using the runner from the repository root, pass the same properties through the environment so its rebuild keeps them:

```sh
ORG_GRADLE_PROJECT_muxyBillingEnforced=true \
ORG_GRADLE_PROJECT_muxyTrialMinutes=2 \
android/scripts/run.sh
```

Install that APK and launch it yourself. A fresh installation shows “Trial: 1 day left” immediately, without pairing; after two minutes, the next minute tick or connection tap detects expiry. The footer opens trial details, Unlock, and Restore purchase. Reinstalling or clearing app data deletes the saved trial and connections. Release builds ignore both overrides and always use three days.

Real purchase, cancellation, pending payment, and restore testing requires the active `muxy_unlock` product, a Play test track, and a signed-in license tester. Debug-only enforcement does not simulate Play purchases. On startup and foreground return, the app queries Play purchases; a successful refresh never re-locks an unlocked running app. Unacknowledged purchases are retried every minute and on later purchase queries.

## Connect to Muxy 1

In the macOS Muxy app, open **Settings > Mobile** and enable **Allow mobile device connection**. In the app, choose **Add Connection > Muxy 1**, then pick your Mac under **Nearby**, scan its pairing QR code, or enter its address:

- Emulator: connect to `10.0.2.2:4865`, the emulator's address for your Mac. The emulator can't see your Mac under Nearby.
- Phone: use your Mac's LAN IP and port `4865`, with both devices on the same network.

Use the configured port if you changed it, then approve the connection on your Mac within two minutes.

A `muxy://pair` link opens **Add Connection** with the address filled in. To try one in the emulator:

```sh
adb shell 'am start -a android.intent.action.VIEW -d "muxy://pair?host=10.0.2.2&port=4865"'
```

## Pair with Muxy 2

The app connects to Muxy 2 builds that share a protocol version with its SDK. See [Muxy SDK](#muxy-sdk).

1. On the computer, open Muxy **Settings > Mobile**, turn on **Allow mobile devices**, and choose **Show Pairing Code**. Without the desktop app, run `muxy mobile enable` and then `muxy mobile pair`.
2. In the app, choose **Add Connection > Muxy 2** and scan the code, or open the `muxy://pair` link. In the emulator, use **Copy Link** on the computer and paste it in the app.
3. Confirm the address and the device name, then tap **Add**.

The phone and the computer must be on the same network or connected through a VPN such as Tailscale. The emulator reaches the addresses in the pairing link through your Mac's network.

## Connect over SSH

Choose **Add Connection > SSH**, enter the name, host, port (22 by default), and username, then choose **Password** or paste a **Private Key** with an optional passphrase. OpenSSH Ed25519 and RSA keys are supported. The app tests an 80×24 shell before saving the connection.

From the emulator, use `10.0.2.2` to reach your Mac's SSH server (enable **System Settings > General > Sharing > Remote Login** yourself). Other servers must be reachable from the Mac's network.

Each tab owns its own SSH session. Switching tabs keeps sessions open; closing a tab or going back closes them. `exit` removes the tab, and **Retry** creates a fresh session after a drop. There is no keepalive or background foreground service.

The first host key is trusted silently and its SHA-256 fingerprint is pinned in encrypted, non-backed-up storage, alongside the credentials. A different host key is refused. Verify the change independently before deleting and adding the connection again; deleting a connection removes its credentials and host-key pin.

SSH uses sshj and the full BouncyCastle provider in place of Android's trimmed provider. `app/proguard-rules.pro` retains BouncyCastle's reflective algorithm registration for R8; removing those rules can break authentication only in release builds. The SLF4J binding is no-op so library logs cannot expose SSH details; application SSH logs contain only lifecycle events and exception types.

The pinned `bcpkix-jdk18on-1.84.jar` also contains an unused EST TLS trust manager (`JcaJceUtils$1`). `app/lint.xml` excludes only that dependency jar from `TrustAllX509TrustManager`; the rule remains active for application code and other dependencies. R8's `-checkdiscard` asserts that the entire unused `org.bouncycastle.est` package is absent from release builds.
