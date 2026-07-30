# Plan: SSH in the RN app (Option A) + ios-native move + platform-selectable releases

Self-contained execution plan. All facts below were verified against the repo on 2026-07-30.
Approved by Saeed. Execute phases in order; Phase 6 is independent of Phases 3–5 and may run in parallel.

## Goal

1. Add the SSH feature (already shipped in the native Swift app) to the React Native app via a
   **local Expo native module — iOS only**. Android hides the feature entirely. Expo Go on iOS
   shows it **visible but disabled**.
2. Move `ios/` → `ios-native/` (it is a separate, standalone SwiftUI project) and repoint the
   native iOS release pipeline at it.
3. Rotate the main release pipeline (`release.yml`) to build **both platforms from RN**, with
   checkbox inputs to choose which platforms to release.
4. End state: dispatch a release with **iOS ✓ / Android ✗** that ships the RN app (with SSH) to
   App Store Connect.

## Verified current state

- **Repo**: Expo SDK 54, RN 0.81.5, React 19.1, expo-router, `newArchEnabled: true`,
  `reactCompiler: true`. Jest via `jest-expo`. Zustand for state. `npm run typecheck`, `npm run lint`.
- **iOS releases today = native Swift app.** `.github/workflows/ios-release.yml` and
  `scripts/release-ios.sh` run `xcodebuild` against `ios/Muxy.xcodeproj` (scheme `Muxy`).
  The native app uses **Citadel/NIOSSH** for SSH and SwiftTerm for the terminal.
- **Android releases today = RN app.** `.github/workflows/android-release.yml` runs
  `npx expo prebuild --platform android --no-install --clean` + Gradle on `ubuntu-latest`,
  sets version/versionCode into `app.json` before prebuild, uploads AAB to Play
  (track: `alpha` if major version 0, `production` otherwise). `scripts/release-android.sh` mirrors it locally.
- **`release.yml` (main)**: workflow_dispatch with a single `version` input → `validate` job →
  calls `ios-release.yml` (native) + `android-release.yml` (RN) via `workflow_call` with
  `skip_release_tag: true` → final job creates GitHub release `vX.Y.Z`. No platform selection.
- **`checks.yml`**: SwiftLint via `ios/scripts/checks.sh`, path-filtered on `ios/**`.
- **git tracking**: `ios/` has 153 tracked files. `android/` has **zero** tracked files (the local
  folder is generated/experimental output; CI regenerates it with prebuild). `ios/Pods/` and
  `ios/build/` are **untracked leftovers** from an old Expo prebuild (contain hermes-engine etc.) —
  delete them during the move.
- **Bundle ID `com.muxy.app` is shared** by the native app and `app.json`. Same App Store record —
  an RN iOS release updates the same store app. This is intended.
- **app.json**: version `0.3.1`, `ios.buildNumber "1"`, plugin `./plugins/withMuxyMenuCommands`,
  Bonjour/local-network Info.plist entries, `expo-secure-store` present.
- **Empty placeholder dirs already exist**: `modules/muxy-ssh/`, `src/ssh/`, `src/components/ssh/`.
- **Native SSH code to port** lives in `ios/Muxy/Networking/SSH/`:
  `SSHSession.swift`, `SSHHostKeyValidator.swift`, `SSHAuthenticationFactory.swift`,
  `SSHConnectionState.swift`, `SSHError.swift`, `SSHConnectionTester.swift`.
  Related UI/validation to mirror: `ios/Muxy/Features/AddConnection/`,
  `ios/Muxy/Core/Validation/ConnectionInputValidator.swift`,
  `ios/Muxy/Features/SSHTerminal/`.
- **RN terminal stack**: `src/components/terminal/TerminalView.tsx` is coupled to muxy WS panes
  (`paneId` prop). `src/components/terminal/TerminalWebView.tsx` is the reusable xterm.js layer
  with an imperative handle (`write(base64)`); xterm is embedded via `scripts/embed-xterm.mjs`
  into `src/components/terminal/xtermBundle.ts`. WS transport: `src/transport/WSClient.ts`.
- **Native-module availability pattern already in use**: `src/transport/discovery.ts:17` checks
  `NativeModules.RNZeroconf != null` and degrades gracefully in Expo Go. The app already depends
  on native modules absent from Expo Go (react-native-iap, react-native-zeroconf,
  react-native-keyboard-controller).

## Locked decisions

- RN iOS **replaces** the native app on the App Store (same bundle ID). Native release path is kept
  working, pointed at `ios-native/`, "for now".
- Tag prefixes: main release keeps `vX.Y.Z`; standalone RN iOS runs keep `ios-v`; the native
  workflow's standalone tag prefix changes to `ios-native-v`.
- Add `expo-dev-client` as a dependency (dev loop for SSH).
- CI signing for the RN iOS archive: **automatic provisioning via the App Store Connect API key**
  (`-allowProvisioningUpdates` + `-authenticationKeyPath/ID/IssuerID`). The existing manual
  cert/profile import steps are the fallback if automatic signing misbehaves.
- RN iOS CI build number = Unix timestamp (`date +%s`), guaranteeing it exceeds every previous
  build uploaded for `com.muxy.app` (native used run numbers/timestamps).
- SSH module is Apple-only: `expo-module.config.json` → `"platforms": ["apple"]`. No Android
  sources at all. Android UI entry points hidden via the availability gate.
- iOS minimum deployment target is raised to **17.0** (Citadel declares `.iOS(.v17)`), set in
  `app.json` and the module podspec. Consequence: App Store users on iOS 15/16 stop receiving
  updates once the RN build replaces the native app. Accepted 2026-07-30.
- Citadel is pinned with `kind: 'exactVersion'` in `modules/muxy-ssh/ios/MuxySsh.podspec`, and the
  full transitive graph is locked by the committed `modules/muxy-ssh/Package.resolved`, which the
  RN iOS release workflow restores into `ios/Muxy.xcworkspace/xcshareddata/swiftpm/` and enforces
  with `xcodebuild -disableAutomaticPackageResolution`. Version bumps must regenerate that file
  from a local prebuild.

## Project rules that apply (from CLAUDE.md — do not skip)

- No code comments anywhere (overrides everything).
- Early returns, root-cause fixes, security first, no hacky solutions.
- After each task the app must build. **User visually tests before tests are written**; write
  tests only after user confirmation.
- Never run the app in dev mode; the user runs it.
- PR descriptions ≤ 3 lines. Upload screenshots/recordings for PRs.

---

## Phase 1 — Move `ios/` → `ios-native/`, repoint native release

Mechanical move, no behavior change.

1. `git mv ios ios-native`. Then delete untracked leftovers that came along on disk:
   `ios-native/Pods/`, `ios-native/build/` (verify untracked with `git status` first).
2. Update every `ios/` reference (complete verified list):
   - `.github/workflows/checks.yml` — lines 7, 11 (`ios/**` → `ios-native/**`), line 33
     (`ios-native/scripts/checks.sh`). Also update `concurrency.group` name if desired.
   - `.github/workflows/ios-release.yml` → **rename file to `ios-native-release.yml`**, workflow
     `name: Release iOS (Native)`. Path updates at: line 53 `APP_PROJECT`, 54 `APP_ARCHIVE_PATH`,
     55 `APP_EXPORT_PATH`, 92 SwiftPM cache `hashFiles` path, 120–122 `private_keys` paths,
     250 `authenticationKeyPath`, 275–276 artifact paths. Tag prefix in the standalone release
     step (lines ~283–308): `ios-v` → `ios-native-v` (list query, `gh release create` tag and title).
   - `.github/workflows/release.yml` — line 30 `uses: ./.github/workflows/ios-release.yml` will be
     repointed in Phase 6 (the main release keeps calling the native workflow until Phase 6 lands;
     update the path to `ios-native-release.yml` now so nothing breaks in between).
   - `scripts/release-ios.sh` → **rename to `scripts/release-ios-native.sh`**. Update lines 4, 13
     (comments), 44 `APP_EXPORT_PATH`, 102 `APP_PROJECT`, 103 `APP_ARCHIVE_PATH`, plus usage strings.
   - `.gitignore` line 57: `ios/.build` → `ios-native/.build`. Add `/ios` (it becomes generated
     prebuild output from Phase 2 on).
   - `CLAUDE.md` line 19 (`ios/` → `ios-native/`) and line 24 (`ios-native/scripts/run.sh test`).
3. Sanity-grep after: `grep -rn "ios/" .github scripts CLAUDE.md .gitignore` — remaining hits must
   be intentional (`ios-native/...` or RN-generated `ios/` from Phase 6 workflow).

**Verify:** `ios-native/scripts/run.sh test` passes locally. `checks.yml` triggers on a PR touching
`ios-native/**`.

## Phase 2 — Prove the RN iOS build path (no SSH yet)

1. `npm install expo-dev-client` (adds the dev-client; keeps versions aligned via
   `npx expo install expo-dev-client`).
2. `npx expo prebuild --platform ios` → generates fresh `ios/` (now gitignored). Confirms the
   config plugins (`withMuxyMenuCommands`, splash, camera, secure-store, iap, build-properties)
   apply cleanly on iOS.
3. Build for simulator to confirm compilation (`npx expo run:ios` builds; the user runs/tests the
   app per project rules).

**Verify:** RN app builds from a clean prebuild; user confirms it boots and WS terminal still works.

## Phase 3 — Build `modules/muxy-ssh` (local Expo module, Apple-only)

### 3a. Spike (time-boxed, do first): Citadel linkage

Citadel is SwiftPM-only; Expo autolinking uses CocoaPods. Two approaches, in order:

1. **`spm_dependency` in the podspec** (CocoaPods ≥ 1.15 era; RN 0.75+ supports it; Citadel
   itself requires iOS ≥ 17.0, so the app's deployment target is raised accordingly — see
   Locked decisions):
   ```ruby
   s.spm_dependency(
     url: 'https://github.com/orlandos-nl/Citadel.git',
     requirement: { kind: 'exactVersion', version: '<match ios-native Package.resolved>' },
     products: ['Citadel']
   )
   ```
   Pin the same Citadel version as `ios-native/Muxy.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved`.
2. **Fallback: config plugin** that injects an `XCRemoteSwiftPackageReference` into the prebuilt
   Xcode project and links the product to the Pods target (documented pattern:
   reactnativecrossroads.com "Expo plugin: Add SPM Dependency"). Put it in `plugins/`.

Success criterion for the spike: `npx expo prebuild -p ios && xcodebuild` compiles a module that
`import Citadel` succeeds in. If both approaches fail hard, stop and report — do not fall back to
libssh2/NMSSH without discussion.

### 3b. Module scaffold

```
modules/muxy-ssh/
  expo-module.config.json      { "platforms": ["apple"], "apple": { "modules": ["MuxySshModule"] } }
  index.ts                     public TS API
  src/                         TS implementation (events, types)
  ios/MuxySsh.podspec
  ios/MuxySshModule.swift      Expo Module definition (functions + events)
  ios/...                      ported Swift files
```

Scaffold reference: `npx create-expo-module@latest --local muxy-ssh` (adjust to match the existing
empty `modules/muxy-ssh/` location).

### 3c. Port Swift from `ios-native/Muxy/Networking/SSH/`

Port with minimal changes (the code is proven): `SSHSession`, `SSHHostKeyValidator`,
`SSHAuthenticationFactory`, `SSHConnectionState`, `SSHError`, `SSHConnectionTester`.
Multi-session support: module holds `[String: SSHSession]` keyed by generated session IDs.

### 3d. Native API surface (Expo Modules DSL)

- `connect(config) -> Promise<sessionId>` — config: host, port, username, auth (password | key),
  initial cols/rows, term type.
- `write(sessionId, dataBase64)`
- `resize(sessionId, cols, rows)`
- `disconnect(sessionId)`
- `testConnection(config) -> Promise<void>`
- Events: `onData { sessionId, dataBase64 }`, `onStateChange { sessionId, state }`,
  `onClosed { sessionId, reason? }`, `onHostKeyPrompt { sessionId, fingerprint, keyType }` with a
  `respondToHostKey(sessionId, accept)` function completing the pending validation
  (mirror `SSHHostKeyValidator` semantics; persist accepted keys — known-hosts storage decided in 4a).

### 3e. TS layer

- `modules/muxy-ssh/index.ts`: typed wrapper; `requireOptionalNativeModule('MuxySsh')` from
  `expo-modules-core` so the import is safe in Expo Go and on Android (returns `null`).
- Export `isSSHAvailable(): boolean`.

**Verify:** in a dev build (user runs it): connect to a real host, stream PTY output, echo input,
resize, disconnect cleanly. Then `npm run typecheck && npm run lint`.

## Phase 4 — Integrate in the RN app

### 4a. `src/ssh/` (domain)

- `availability.ts` — `getSSHSupport(): 'available' | 'disabled-expo-go' | 'hidden'`:
  `Platform.OS === 'android'` → `hidden`; module `null` on iOS → `disabled-expo-go`; else `available`.
- `store.ts` — zustand store: saved connections (id, name, host, port, username, authType),
  live session state. Non-secret metadata in AsyncStorage (follow existing store persistence
  patterns in `src/state/`); secrets (passwords/private keys) in `expo-secure-store` keyed by
  connection id; known-hosts entries in AsyncStorage (fingerprints are not secrets).
- `types.ts`, validation helpers mirroring `ConnectionInputValidator.swift`.

### 4b. `src/components/ssh/` (UI)

- Connection list + add/edit form (mirror native `AddConnectionView` fields + validation).
- Host-key trust prompt (sheet/dialog) wired to `onHostKeyPrompt`/`respondToHostKey`.
- `SSHTerminal.tsx` — wraps `TerminalWebView` **directly** (do not force `TerminalView`, it is
  WS/pane-coupled): module `onData` → webview `write(base64)`; keystrokes → `MuxySsh.write`;
  fit/resize callback → `MuxySsh.resize`. Reuse `buildTerminalTheme`, `KeyBar`, focus helpers
  where they fit.

### 4c. Routes (`app/`, expo-router)

- SSH entry point on the home screen (`app/index.tsx`) gated by `getSSHSupport()`.
- `app/ssh/index.tsx` (connections), `app/ssh/add.tsx` or modal, `app/ssh/[id].tsx` (terminal).

### 4d. Gating behavior

- Android: no SSH UI whatsoever.
- iOS Expo Go: SSH entry visible but disabled, with hint text ("Requires a development build").
- iOS dev/release build: fully enabled.

**Verify:** app builds; user visually tests the full flow in a dev build, the disabled state in
Expo Go (iOS), and confirms nothing appears on Android.

## Phase 5 — Tests (only after user confirms Phase 4 visually)

- Jest (jest-expo), colocated `*.test.ts` like the rest of `src/`:
  - `availability.test.ts` — all three gate outcomes (mock `Platform.OS` and module presence).
  - store tests — add/edit/remove connections, session state transitions.
  - input validation tests (host/port/username rules).
  - host-key flow with a mocked native module (accept/reject paths).
- `npm run typecheck && npm run lint && npm test` all green.

## Phase 6 — Release pipeline rotation (independent of Phases 3–5)

### 6a. New `.github/workflows/ios-release.yml` (RN iOS)

Mirror `android-release.yml`'s structure, on `macos-latest`:

1. Inputs identical in spirit: `version` (X.Y.Z), optional `build_number` (default `date +%s`),
   `workflow_call` variant with `skip_release_tag`.
2. Steps: checkout → setup Node 20 + npm cache → `npm ci` → set `expo.version` and
   `expo.ios.buildNumber` in `app.json` via `node -e` (same pattern as Android's versionCode step)
   → `npx expo prebuild --platform ios --no-install` → `npx pod-install ios`
   → archive with automatic signing:
   ```
   xcodebuild -workspace ios/Muxy.xcworkspace -scheme Muxy -configuration Release \
     -destination "generic/platform=iOS" -archivePath build/Muxy.xcarchive \
     -allowProvisioningUpdates \
     -authenticationKeyPath <AuthKey.p8> -authenticationKeyID ... -authenticationKeyIssuerID ... \
     DEVELOPMENT_TEAM=$APPLE_TEAM_ID clean archive
   ```
   → export IPA (`method: app-store-connect`, automatic signing style, same auth key flags)
   → `xcrun altool --upload-app` (same as native workflow) → upload artifacts
   → standalone tag step with `ios-v` prefix (grep commit subjects; RN-side changes are not
   `ios:`-prefixed, so use the plain `git log` notes style like `release.yml` does, filtered range
   from previous `ios-v` tag).
3. Secrets: reuse existing `APP_STORE_CONNECT_API_KEY`, `APP_STORE_CONNECT_KEY_ID`,
   `APP_STORE_CONNECT_ISSUER_ID`, `APPLE_TEAM_ID`. Certificate/profile secrets become unnecessary
   with automatic signing — keep them declared optional in case of fallback to manual signing
   (manual fallback = copy the cert/profile import steps from `ios-native-release.yml`).
4. Pin an Xcode version compatible with RN 0.81/Expo 54 (`sudo xcode-select`), not necessarily the
   one the native workflow uses.

### 6b. `release.yml` platform selection

- Add `workflow_dispatch` inputs: `release_ios` (type: boolean, default true, description "Release
  iOS (React Native)"), `release_android` (type: boolean, default true).
- `ios` job: `if: inputs.release_ios` → `uses: ./.github/workflows/ios-release.yml` (the new RN one).
- `android` job: `if: inputs.release_android` → unchanged call.
- Final `release` job: `needs: [validate, ios, android]` with
  `if: ${{ !cancelled() && needs.validate.result == 'success' && needs.ios.result != 'failure' && needs.android.result != 'failure' && (needs.ios.result == 'success' || needs.android.result == 'success') }}`
  so it runs when at least one platform released and none failed.

**Verify:** `gh workflow run` on a branch or actlint/`actionlint` pass; then the real run (6c).

### 6c. The goal release (runbook)

1. Ensure `main` has all phases merged and green checks.
2. GitHub → Actions → "Release" → Run workflow: version = next (suggest `0.4.0`),
   iOS ✓, Android ✗.
3. Confirms: RN IPA lands in App Store Connect (TestFlight), GitHub release `v0.4.0` created.
4. User promotes the build in ASC when satisfied.

---

## Risks

- **Citadel via CocoaPods** — the only piece that could change the approach; that is why Phase 3
  starts with the spike. Escalate before substituting libraries.
- **Manual signing + Pods resource bundles** — avoided by automatic signing via ASC API key.
- **Store transition** — the iOS binary switches SwiftUI → RN in one update; same bundle ID and
  feature set; first upload only reaches TestFlight, promotion is manual.
- **`expo prebuild -p android` locally** would clobber the untracked Kotlin experiments in
  `android/` — do not run it locally; moving `android/` → `android-native/` is a recommended
  follow-up, out of scope here.

## Out of scope

- Android SSH implementation (feature hidden on Android).
- Moving `android/` → `android-native/`.
- WS-proxy SSH variant (Option C).
- Deleting the native iOS app or its release path.
