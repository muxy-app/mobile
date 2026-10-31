# Store screenshots

App Store and Google Play images, generated from the apps running in demo mode.

| Folder | Store slot | Size |
|---|---|---|
| `app-store/iphone-6.9` | iPhone 6.9" | 1320 × 2868 |
| `app-store/iphone-duo` | iPhone Duo (landscape) | 2853 × 2007 |
| `app-store/ipad-13` | iPad 13" | 2064 × 2752 |
| `app-store/header.png` | Product page header | 3840 × 1646 |
| `app-store/search-results.png` | Search results | 3840 × 2560 |
| `google-play/phone` | Phone | 1440 × 2560 |
| `google-play/tablet` | 7" and 10" tablet | 1440 × 2560 |
| `google-play/feature-graphic.png` | Feature graphic | 1024 × 500 |

## Regenerate

Requirements: Xcode and the Android SDK with `ANDROID_HOME` set. iOS captures run the `MuxyStoreScreenshots` UI tests (`ios/MuxyStoreScreenshots`).

1. Build both apps: `ios/scripts/run.sh build` and `android/scripts/run.sh build`.
2. Capture (raw captures go to the git-ignored `raw/` folder):
   - `store/scripts/capture-ios.sh "iPhone 18 Pro Max" store/raw/iphone`
   - `store/scripts/capture-ios.sh <iPhone Duo UDID> store/raw/iphone-duo` on the Duo runtime (`iOS-27-2-iPhone19-4`), unfolded in DeviceHub first
   - `store/scripts/capture-ios.sh "iPad Pro 13-inch (M5)" store/raw/ipad` in portrait
   - `store/scripts/capture-android.sh <phone serial> store/raw/android-phone` with a Pixel 10 emulator
   - `PORTRAIT=1 store/scripts/capture-android.sh <tablet serial> store/raw/android-tablet` with a Pixel Tablet emulator
3. Render: `store/scripts/render.sh` (or pass targets such as `iphone android-phone feature-graphic app-store-header`).

Slide copy and layouts live in `composer/Sources/StoreComposer/Slides.swift`. The demo content shown in the screenshots comes from each app's demo mode (`ios/Muxy/Features/Demo`, `android/app/src/main/kotlin/com/muxy/app/features/demo`).
