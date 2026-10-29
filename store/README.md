# Store screenshots

App Store and Google Play images, generated from the apps running in demo mode.

| Folder | Store slot | Size |
|---|---|---|
| `app-store/iphone-6.9` | iPhone 6.9" | 1320 × 2868 |
| `app-store/ipad-13` | iPad 13" | 2064 × 2752 |
| `google-play/phone` | Phone | 1440 × 2560 |
| `google-play/tablet` | 7" and 10" tablet | 1440 × 2560 |
| `google-play/feature-graphic.png` | Feature graphic | 1024 × 500 |

## Regenerate

Requirements: Xcode, the Android SDK with `ANDROID_HOME` set, and [AXe](https://github.com/cameroncooke/AXe) (`brew install cameroncooke/axe/axe`).

1. Build both apps: `ios/scripts/run.sh build` and `android/scripts/run.sh build`.
2. Capture (raw captures go to the git-ignored `raw/` folder):
   - `store/scripts/capture-ios.sh "iPhone 18 Pro Max" store/raw/iphone`
   - `store/scripts/capture-ios.sh "iPad Pro 13-inch (M5)" store/raw/ipad` in portrait
   - `store/scripts/capture-android.sh <phone serial> store/raw/android-phone` with a Pixel 10 emulator
   - `PORTRAIT=1 store/scripts/capture-android.sh <tablet serial> store/raw/android-tablet` with a Pixel Tablet emulator
3. Render: `store/scripts/render.sh` (or pass targets such as `iphone android-phone feature-graphic`).

Slide copy and layouts live in `composer/Sources/StoreComposer/Slides.swift`. The demo content shown in the screenshots comes from each app's demo mode (`ios/Muxy/Features/Demo`, `android/app/src/main/kotlin/com/muxy/app/features/demo`).
