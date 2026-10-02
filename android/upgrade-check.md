# React Native to Kotlin upgrade check

Use a disposable emulator, not your normal development emulator. This checks an actual package update, including preservation of the Android Keystore keys; a physical phone is not required. Neither build needs Metro or a development server.

## Build the APKs

Run from the repository root. Use JDK 21 and the existing Android SDK:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export GRADLE_USER_HOME="$HOME/.gradle"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
```

Create a separate worktree for the last RN release. Skip creation/prebuild if this worktree has already been prepared. Never run prebuild in the native checkout.

```sh
git worktree add --detach ../muxy-rn-phase9 v2.5.2
(cd ../muxy-rn-phase9 && npm ci && CI=1 npx expo prebuild -p android --no-install --clean)
(cd ../muxy-rn-phase9/android && ./gradlew :app:assembleRelease -PreactNativeArchitectures=arm64-v8a)
mkdir -p android/.build/phase9
cp ../muxy-rn-phase9/android/app/build/outputs/apk/release/app-release.apk android/.build/phase9/rn-release.apk
```

The RN release APK has application ID `com.muxy.app` and version code 3. Expo's template signs it with its bundled debug keystore. Sign the Kotlin release with the same key and use version code 4:

```sh
(cd android && ./gradlew :app:assembleRelease \
  -PversionName=3.0.0 -PversionCode=4 \
  -Pandroid.injected.signing.store.file="$PWD/../../muxy-rn-phase9/android/app/debug.keystore" \
  -Pandroid.injected.signing.store.password=android \
  -Pandroid.injected.signing.key.alias=androiddebugkey \
  -Pandroid.injected.signing.key.password=android)
cp android/app/build/outputs/apk/release/app-release.apk android/.build/phase9/native-upgrade.apk
```

Version code 4 and the debug key are only for this local check. Do not upload these APKs to Play. The production release entry points require a version code above `1788620580` and the existing Play upload key.

## Prepare the emulator

Create a dedicated AVD with the installed API 36 Google Play image, or select the already-prepared `Muxy_Phase9_Upgrade` in Android Studio's Device Manager:

```sh
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  -n Muxy_Phase9_Upgrade -k 'system-images;android-36;google_apis_playstore;arm64-v8a' -d pixel_7
```

Start it yourself in Android Studio and finish setup. Run `adb devices -l` and identify its serial. If several emulators are running, `adb -s <serial> emu avd name` identifies each one. Replace `<serial>` below with the upgrade emulator's serial:

```sh
export DEVICE_SERIAL='<serial>'
adb -s "$DEVICE_SERIAL" install android/.build/phase9/rn-release.apk
```

Open Muxy yourself. Pair a Muxy 1 Mac at `10.0.2.2:4865` and approve on the Mac. Pick a workspace filter, turn Nerd Font off and Auto-Focus Terminal on, then note the trial state. Leave demo mode off for the first check. The RN trial begins on pairing; use a fresh trial so the release paywall will not block connection verification.

## Upgrade without deleting data

Do **not** uninstall Muxy, clear its storage, use the normal debug runner, or run instrumented tests between these steps. They can destroy the old data or change the signing key/version.

```sh
adb -s "$DEVICE_SERIAL" install -r android/.build/phase9/native-upgrade.apk
```

Open Muxy yourself and verify:

- Onboarding stays complete.
- The saved Mac connects without a new approval prompt.
- The same workspace is selected.
- Nerd Font remains off and Auto-Focus Terminal remains on.
- The trial still has its original start, rather than resetting on upgrade.
- After closing and reopening Muxy, the same state remains and importing does not run again.

Check migration logs without dumping tokens or encrypted stores:

```sh
adb -s "$DEVICE_SERIAL" logcat -d -s Muxy/persistence:I
```

`Legacy import finished` appears once for this installation; failures log only the record category and exception type. A second launch must not append another import-finished message. Report any failure before deleting the RN sources.

If Android reports an incompatible signature, stop and check that both APKs were signed with the bundled RN keystore. Do not uninstall to work around it: that would invalidate the upgrade check.

## Remaining release checks

- Launch the Kotlin release on `Muxy_Phase9_API29` and `Muxy_Phase9_API37` after starting those AVDs yourself. Check startup, settings, connections and a terminal. These are separate installations, not the migration check.
- A Play internal-track build must use the Play upload key and a timestamp version code. Sign in to a license tester account, install from Play, and check purchase restoration. A locally debug-signed APK cannot verify Play delivery or restoration of a real purchase.

## Cleanup after confirmation

Keep both upgrade APKs until the import decision and any follow-up tests are finished. Then remove the disposable AVDs, API 29/37 images if no longer needed, the RN worktree and its dependencies/build outputs, and temporary artifact-inspection files. Keep the existing `Muxy_API_36` AVD, API 36 image, JDK 21, pinned Muxy SDK and NDK 30 for native development. Remove only tooling/dependencies installed for these checks that are no longer used by another installed tool.
