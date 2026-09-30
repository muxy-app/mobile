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

## Other commands

| Command | Action |
| --- | --- |
| `./scripts/run.sh build` | Build the debug APK without a device |
| `./scripts/run.sh test` | Run the JVM unit tests |
| `./scripts/run.sh test-device` | Run the instrumented tests in the emulator |
| `./scripts/run.sh stop` | Stop Muxy in the emulator |
| `./scripts/run.sh restart` | Stop, rebuild and relaunch Muxy in the emulator |
| `./scripts/run.sh logs` | Follow Muxy's logs and crashes |
| `./scripts/run.sh devices` | List emulators and connected devices |
| `./scripts/run.sh help` | Show commands and environment variables |
| `./scripts/checks.sh` | Run ktlint, including the rule that rejects comments, and Android Lint |

Build outputs are stored in each module's `build/` folder. The first build downloads Gradle and the dependencies and can take several minutes.

## Release builds

`./gradlew :app:assembleRelease` builds with R8. `-PversionName` and `-PversionCode` set the version, and the build signs with the upload key when `ANDROID_SIGNING_KEY_PATH`, `ANDROID_KEY_STORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` are all set. A relative key path is resolved from the repository root, as in `.env.example`. With none of them set, the APK is unsigned.

## Connect to Muxy 1

In the macOS Muxy app, open **Settings > Mobile** and enable **Allow mobile device connection**. In the app, choose **Add Connection > Muxy 1**.

- Emulator: connect to `10.0.2.2:4865`, the emulator's address for your Mac.
- Phone: use your Mac's LAN IP and port `4865`, with both devices on the same network.

Use the configured port if you changed it, then approve the connection on your Mac.

## Pair with Muxy 2

The app connects to Muxy 2 builds that share a protocol version with its SDK. See [Muxy SDK](#muxy-sdk).

1. On the computer, open Muxy **Settings > Mobile**, turn on **Allow mobile devices**, and choose **Show Pairing Code**. Without the desktop app, run `muxy mobile enable` and then `muxy mobile pair`.
2. In the app, choose **Add Connection > Muxy 2** and scan the code, or open the `muxy://pair` link. In the emulator, use **Copy Link** on the computer and paste it in the app.
3. Confirm the address and the device name, then tap **Add**.

The phone and the computer must be on the same network or connected through a VPN such as Tailscale. The emulator reaches the addresses in the pairing link through your Mac's network.
