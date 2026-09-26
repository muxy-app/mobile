# Muxy for iOS

## Requirements

- Xcode with an iOS SDK and simulator runtime supporting iOS 26.2 or newer.
- Python 3, used by the runner to discover devices.
- Internet access to download the Muxy SDK and, on the first build, to resolve Swift packages. CocoaPods and an Expo server are not required.

Check the selected Xcode installation with `xcodebuild -version`. If it points to Command Line Tools instead of Xcode, select Xcode:

```sh
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

Run the following commands from `ios-native/`, or prefix them with `ios-native/` from the repository root.

## Muxy SDK

The app embeds the Muxy mobile SDK, a Rust library with generated Swift bindings. The SDK isn't committed, because its XCFramework is too large for git. Instead, `MuxyMobileSDK/SHA256SUMS` pins the Muxy release it comes from and the checksum of its iOS download. Install it before the first app build, and again whenever the pin changes:

```sh
scripts/sdk.sh install
```

The script checks the download against the pin and writes the SDK to `MuxyMobileSDK/Build/`, with a `REVISION` file naming its version. The runner stops when the SDK is missing and tells you when it isn't the pinned release.

The app connects to any Muxy build that shares a protocol version with its SDK. `muxy --build-info` prints a computer's `protocol` versions, and each Muxy release lists its SDK's in `muxy-mobile-<version>.json`. Pin a newer release by its version, such as `2.0.0-beta-1078`, when the protocol version changes or to use newer SDK features, then build and test the app:

```sh
scripts/sdk.sh pin <version>
```

To try SDK changes that aren't released yet, build the SDK from a Muxy 2 checkout. This needs `rustup` and takes a few minutes. When `cargo-ndk` is installed, Muxy's build script also builds Android and needs a working Android NDK.

```sh
scripts/sdk.sh build ~/Projects/muxy
```

`scripts/sdk.sh install` switches back to the pinned release. If the app crashes with a UniFFI checksum mismatch after switching, clean the build folder and build again.

## Simulator

```sh
./scripts/run.sh
```

The runner selects an available iPhone simulator, preferring newer runtimes and a booted device within each runtime. If none exists, it creates one using an installed runtime. It builds the app, waits for the simulator to finish booting, then installs and launches Muxy.

If no runtime is installed, install iOS 26.2 or newer in **Xcode > Settings > Components**.

To choose a simulator:

```sh
SIM_NAME="iPhone 16e" ./scripts/run.sh
SIM_ID="<simulator UDID>" ./scripts/run.sh
```

`SIM_NAME` can create a simulator matching an installed iPhone device type. `SIM_ID` must identify an existing simulator and takes precedence over `SIM_NAME`.

## iPhone or iPad

1. Connect the device to your Mac, unlock it, and accept **Trust This Computer**. Wireless deployment also works after pairing in Xcode.
2. Enable **Settings > Privacy & Security > Developer Mode** on the device and complete the restart if prompted.
3. Add your Apple account in **Xcode > Settings > Accounts**.
4. Open `Muxy.xcodeproj`. Under **Muxy > Signing & Capabilities**, select a development team for the Debug configuration and enable automatic signing. If your team cannot use `com.muxy.app`, use a unique bundle identifier in Xcode and run from Xcode instead of this script.
5. Keep the device unlocked and run:

```sh
./scripts/run.sh device
```

The runner automatically selects the only paired iOS device. To choose among multiple devices:

```sh
./scripts/run.sh devices
./scripts/run.sh device "<device name or ID>"
```

You can also set `DEVICE_ID` or override signing with `DEVELOPMENT_TEAM`:

```sh
DEVELOPMENT_TEAM="<team ID>" ./scripts/run.sh device
```

The runner uses Debug signing and allows Xcode to update development provisioning profiles and register the selected device. It installs using the `com.muxy.app` bundle identifier, which may replace an existing Muxy installation. If prompted, trust your developer certificate under **Settings > General > VPN & Device Management**.

For an unavailable device or developer disk image error, keep the phone unlocked and check **Xcode > Window > Devices and Simulators**. A device running an iOS beta may require a compatible Xcode beta. You can select it for one invocation without changing the system default:

```sh
DEVELOPER_DIR="/Applications/Xcode-beta.app/Contents/Developer" ./scripts/run.sh device
```

## Other commands

| Command | Action |
| --- | --- |
| `./scripts/run.sh build` | Build for Simulator without booting, installing, or launching |
| `./scripts/run.sh build-device` | Build for a paired device without installing or launching |
| `./scripts/run.sh test` | Run unit tests in Simulator |
| `./scripts/run.sh stop` | Stop Muxy in the selected simulator |
| `./scripts/run.sh restart` | Rebuild and relaunch Muxy in the selected simulator |
| `./scripts/run.sh devices` | List available simulators and paired devices |
| `./scripts/run.sh help` | Show commands and environment variables |

Build products are stored in `.build/xcode/`. The first build can take several minutes while Swift packages compile.

## Release

In GitHub Actions, the **Release** workflow ships this app when **Release iOS** is selected, alongside Android. The **Release iOS** workflow releases iOS alone and tags `ios-v<version>`. Both install the pinned Muxy SDK, archive with the App Store profile, and upload the build to App Store Connect for TestFlight. To release from a Mac with the secrets in the repository's `.env`, run from the repository root:

```sh
scripts/release-ios-native.sh <version>
```

Use a version higher than the one on the App Store. The app requires iOS 26.2 or newer.

## Connect to Muxy 1

In the macOS Muxy app, open **Settings > Mobile** and enable **Allow mobile device connection**. In the app, choose **Add Connection > Muxy 1**.

- Simulator: connect to `127.0.0.1:4865`.
- Physical device: use your Mac's LAN IP and port `4865`, with both devices on the same network. Allow local network access when prompted.

Use the configured port if you changed it, then approve the connection on your Mac.

## Pair with Muxy 2

The app connects to Muxy 2 builds that share a protocol version with its SDK. See [Muxy SDK](#muxy-sdk).

1. On the computer, open Muxy **Settings > Mobile**, turn on **Allow mobile devices**, and choose **Show Pairing Code**. Without the desktop app, run `muxy mobile enable` and then `muxy mobile pair`.
2. In the app, choose **Add Connection > Muxy 2** and scan the code, or open the `muxy://pair` link from the Camera app. In Simulator, use **Copy Link** on the computer and paste it in the app.
3. Confirm the address and the device name, then tap **Add**.

The phone and the computer must be on the same network or connected through a VPN such as Tailscale. Allow local network access when iOS asks. To try the app without a computer, turn on **Settings > Demo Mode**.
