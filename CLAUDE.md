# Muxy Mobile

React Native + Expo companion app for **Muxy**, the desktop terminal multiplexer. Connects to Muxy's WebSocket server on the local network to control sessions remotely.

WebSocket protocol (methods, events, data shapes) lives in `docs/`. The desktop server source lives at `~/Projects/muxy` — read it directly when the docs aren't enough.

## Stack

- Expo SDK 54, React Native 0.81, React 19, expo-router 6
- TypeScript strict (`noUncheckedIndexedAccess`)
- Zustand + AsyncStorage for persisted state, expo-secure-store for tokens
- react-native-reanimated, react-native-safe-area-context, expo-image
- New Architecture enabled, React Compiler enabled
- Path alias `@/*` → `src/*`

## Commands

- `npm start` / `npm run ios` / `npm run android`
- `npm run typecheck` — `tsc --noEmit`
- `npm run lint` — `expo lint`

Always run typecheck and lint before declaring a change done.

## Conventions

**No comments in code.** Identifiers, types, and structure should explain themselves. Don't add `// section banners`, `// TODO`s, JSDoc blocks, or trailing rationale comments. Only exception: a non-obvious *why* a future reader can't recover from the code (an upstream quirk, a known bug workaround). If you can't name the concrete reader-saving reason, leave it out.

**No throwaway dev panels.** Validate transport/state/infra work through real product screens. Don't build `__DEV__`-gated debug UIs as test gates.

**Theme tokens, never raw colors.** Every screen consumes `useTokens()` semantic tokens (`surface.*`, `text.*`, `border.*`, `accent.*`, `status.*`). No hex literals outside `src/theme/`. The accent picker uses `palette[4]` (ANSI blue slot) and falls back to `fg` — never to a hardcoded value.

**Status bar follows the theme.** `<StatusBar style={mode === 'dark' ? 'light' : 'dark'} />` for icons; `expo-system-ui.setBackgroundColorAsync(tokens.surface.primary)` for the native root view.

**Connection is explicit.** No auto-connect on launch — `activeDeviceId` is not persisted. The user must tap a device row to connect. Background → foreground reconnects within the same session via `AppStateBinder`.

**Zustand selectors return stable references.** Don't construct fresh objects/arrays inside a selector — derive in `useMemo` downstream. Otherwise `useSyncExternalStore` re-renders on every tick → infinite loop.

**`terminalOutput` does not pass through React state.** High-frequency events go straight from `WSClient.bus.emit()` to subscribers. The terminal emulator subscribes directly via `client.on('terminalOutput', …)`.

**Protocol changes go through `src/transport/protocol.ts`.** Add the typed `MethodMap` / `EventDataMap` entry, then re-export new types from `src/transport/index.ts`. If a response shape isn't in `docs/`, read the Swift source in `~/Projects/muxy` (look for `*DTO.swift`, `MuxyProtocol.swift`, `ProtocolParams.swift`).
