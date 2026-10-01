# Termux terminal emulator

This module is the `terminal-emulator` library of [termux/termux-app](https://github.com/termux/termux-app) at tag `v0.118.3`, commit `5b657c6adf4304e5198951ce815fe0205dcac29c`.

termux-app is released under GPLv3, except for this library and `terminal-view`. They derive from Jack Palevich's [Terminal Emulator for Android](https://github.com/jackpal/Android-Terminal-Emulator) and are released under the Apache License 2.0, as termux-app's `LICENSE.md` states. The license text is in [LICENSE](LICENSE).

## Changes from upstream

- Removed the code that runs local processes: `JNI.java`, `TerminalSession.java` and `src/main/jni`.
- `TerminalSessionClient.java`: removed the callbacks that take a `TerminalSession`, because that class is gone, and added a note saying so.
- Replaced the Gradle build file, and dropped the manifest, which only declared the package. `build.gradle.kts` sets the namespace instead.

The other sources and the upstream unit tests are unchanged.
