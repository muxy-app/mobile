# Muxy Mobile

Native iOS and Android clients for the [Muxy](https://github.com/muxy-app/muxy) macOS terminal.

```
.
├── ios/        SwiftUI app (iOS 17+)
└── android/    Kotlin / Jetpack Compose app (minSdk 29)
```

The two apps share no code. Each talks to a Muxy mac server over WebSocket (default port `4865`).

## Install

### iOS (TestFlight)

1. Join via [TestFlight](https://testflight.apple.com/join/7t1AaYHW).
2. On your Mac, open Muxy → Settings (`Cmd + ,`) → Mobile, enable **Allow mobile device connection**.
3. Open the iOS app, enter the IP and port, approve the connection on your Mac.

### Android (Closed Testing)

1. Join the [testers group](https://groups.google.com/g/muxy-testers).
2. Opt in at the [testing link](https://play.google.com/apps/testing/com.muxy.app).
3. Install from [Google Play](https://play.google.com/store/apps/details?id=com.muxy.app).
4. On your Mac, open Muxy → Settings (`Cmd + ,`) → Mobile, enable **Allow mobile device connection**.
5. Open the Android app, enter the IP and port, approve the connection on your Mac.

## Development

### iOS

```sh
cd ios
open MuxyMobile.xcodeproj
# or run on a simulator:
scripts/run-mobile.sh
```

### Android

```sh
cd android
./gradlew assembleDebug
```

Open `android/` in Android Studio for development.

## License

Source-available under the Functional Source License 1.1 with an Apache 2.0 future grant (`FSL-1.1-ALv2`). See `LICENSE` and `LICENSE-NOTES.md`.
