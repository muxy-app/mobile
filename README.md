# Muxy Mobile

Native iOS and Android clients for the [Muxy](https://github.com/muxy-app/muxy) macOS terminal.

## Install

### iOS

1. Download from [App Store](https://apps.apple.com/de/app/muxy/id6762464046?l=en-GB)
2. On your Mac, open Muxy → Settings (`Cmd + ,`) → Mobile, enable **Allow mobile device connection**.
3. Open the iOS app, enter the IP and port, approve the connection on your Mac.

### Android

1. Download from [Play Store](https://play.google.com/store/apps/details?id=com.muxy.app)
2. On your Mac, open Muxy → Settings (`Cmd + ,`) → Mobile, enable **Allow mobile device connection**.
3. Open the Android app, enter the IP and port, approve the connection on your Mac.

For Muxy 2, choose **Add Connection → Muxy 2** and connect with a pairing code or SSH. SSH opens the remote computer's Muxy projects, terminals, files, and Git tools; Muxy must already be installed there. The separate **SSH** connection type opens a regular shell.

## Development

- [iOS development](ios/README.md) — Swift, SwiftUI and SwiftTerm.
- [Android development](android/README.md) — Kotlin and Jetpack Compose.

## License

Source-available under the Functional Source License 1.1 with an Apache 2.0 future grant (`FSL-1.1-ALv2`). See `LICENSE` and `LICENSE-NOTES.md`.
