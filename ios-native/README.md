# Muxy for iOS

## Requirements

- Xcode with an iOS SDK and simulator runtime supporting iOS 26.2 or newer.
- Python 3, used by the runner to discover devices.
- Internet access for the first build to resolve Swift packages. CocoaPods and an Expo server are not required.

Check the selected Xcode installation with `xcodebuild -version`. If it points to Command Line Tools instead of Xcode, select Xcode:

```sh
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

Run the following commands from `ios-native/`, or prefix them with `ios-native/` from the repository root.

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

## Connect to your Mac

In the macOS Muxy app, open **Settings > Mobile** and enable **Allow mobile device connection**.

- Simulator: connect to `127.0.0.1:4865`.
- Physical device: use your Mac's LAN IP and port `4865`, with both devices on the same network. Allow local network access when prompted.

Use the configured port if you changed it, then approve the connection on your Mac.
