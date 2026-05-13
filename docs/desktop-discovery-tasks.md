# Desktop Tasks: Bonjour Advertising + Pairing QR Code

Companion work required on the Muxy desktop (`Projects/muxy`) to fully unlock the
mobile-side features added for issue #24. The mobile app already ships the
consumer side; nothing here changes the WebSocket protocol.

## 1. Advertise the server via Bonjour (`_muxy._tcp`)

The mobile app browses the local network for `_muxy._tcp` service records and
shows them in the "Nearby Muxy desktops" list on the Add-device screen. It also
re-resolves the stored service name on every reconnect, so the connection
survives LAN-IP changes (the original complaint in issue #24).

### Where

`MuxyServer/MuxyRemoteServer.swift` — `startListener()`, where `NWListener` is
created (around line 171).

### What to do

Attach a service descriptor to the listener so macOS registers it with Bonjour
once the listener is `.ready`:

```swift
listener = try NWListener(using: params, on: endpointPort)
listener?.service = NWListener.Service(name: nil, type: "_muxy._tcp")
```

- `name: nil` lets `Network.framework` use the device's local hostname
  (`scutil --get LocalHostName`). The user already sees this name in macOS
  sharing UI, so it's a sensible default.
- If we ever want a user-customised advertised name, plumb the value from the
  Mobile settings panel through to `MuxyRemoteServer.init` and pass it here.
- TXT records are not required by the mobile client today. If we later need to
  advertise capabilities (protocol version, auth requirements), add them via
  the second `NWListener.Service` initialiser.

### Verification

1. Build + launch Muxy with Mobile server enabled.
2. From any other Mac on the LAN:
   ```sh
   dns-sd -B _muxy._tcp local
   ```
   The browser should list the running Mac.
3. Open the mobile app → Add device. The "Nearby Muxy desktops" card should
   populate within a couple of seconds.
4. Pair, then change the Mac's IP (toggle WiFi or rejoin). Re-opening the app
   should reconnect without user action — the mobile client re-resolves the
   service name to the new IP via mDNS.

### Notes / gotchas

- iOS requires `NSBonjourServices` to include `_muxy._tcp` and
  `NSLocalNetworkUsageDescription` to be set. Both are already configured in
  `muxy-mobile/app.json`.
- On Android the mobile app needs `CHANGE_WIFI_MULTICAST_STATE`; this is
  declared by `react-native-zeroconf` and picked up by Expo prebuild.
- AP/client isolation on the user's router blocks mDNS between peers. Not
  something the desktop can fix, but worth mentioning in the Mobile settings
  help text.

## 2. Show a pairing QR code in Mobile Settings

The mobile app's Add-device header has a QR-scan button that decodes a Muxy
pairing URI and prefills the pairing form. The desktop should render the
matching QR somewhere obvious in **Settings → Mobile**, next to the existing
"Approved devices" list.

### URI format

```
muxy://pair?host=<host>&port=<port>&service=<service>&label=<label>
```

| Param     | Required | Source                                                |
| --------- | -------- | ----------------------------------------------------- |
| `host`    | yes      | Prefer `<LocalHostName>.local`. Fall back to the LAN IPv4 if mDNS is disabled at the OS level. |
| `port`    | yes      | The configured Mobile server port (default `4865`).   |
| `service` | optional | Bonjour service name advertised under `_muxy._tcp` (see §1). When present, the mobile client uses it for IP-change-resilient reconnects. |
| `label`   | optional | User-friendly device name (e.g. `Saeed's MacBook`). URL-encode spaces and special chars. |

Example (URL-encoded):

```
muxy://pair?host=Saeeds-MacBook-Pro.local&port=4865&service=Saeeds-MacBook-Pro&label=Saeed%27s%20Mac
```

Reference implementation of the parser used by the client:
`muxy-mobile/src/state/pairUri.ts`.

### Where

`Muxy/` — wherever the Mobile settings panel is rendered. The QR should sit
above or beside the "Approved devices" list with a short caption like _"Scan
this with the Muxy mobile app to add this Mac."_

### What to do

1. Build the URI string. Sketch:
   ```swift
   import Foundation

   func pairingURIString(port: UInt16) -> String {
       var components = URLComponents()
       components.scheme = "muxy"
       components.host = "pair"

       let localHost = (Host.current().localizedName ?? "localhost") + ".local"
       let serviceName = Host.current().localizedName ?? "Muxy"
       let label = Host.current().localizedName ?? "My Mac"

       components.queryItems = [
           URLQueryItem(name: "host", value: localHost),
           URLQueryItem(name: "port", value: String(port)),
           URLQueryItem(name: "service", value: serviceName),
           URLQueryItem(name: "label", value: label),
       ]
       return components.url!.absoluteString
   }
   ```
2. Render it as a QR using `CIFilter.qrCodeGenerator()` (built into macOS, no
   third-party dep needed). Use `inputCorrectionLevel = "M"` and upscale via
   `CIFilter.lanczosScaleTransform` for sharpness on Retina displays.
3. Refresh the QR whenever:
   - The Mobile server port changes.
   - The advertised service name changes.
   - The Mobile server is toggled on (no point showing it while disabled).
4. Optional polish: tooltip / "Copy link" button that copies the raw URI so
   users on the same machine can deep-link instead of scanning.

### Verification

1. Open Settings → Mobile on the desktop; the QR should render.
2. On the phone: Add device → tap the QR icon (top-right) → scan the QR.
3. The mobile Add-device screen should reopen with Host, Port, Service, and
   Label prefilled. Tap "Pair" → existing approval flow runs as usual.
4. Smoke-test a malformed URI (e.g. encode `muxy://pair?host=&port=99999`).
   The mobile client should refuse and show _"That QR code isn't a Muxy
   pairing code."_.

### Notes / gotchas

- The QR is **not** an auth bypass. It carries no token. First-pair still goes
  through the existing `pairDevice` approval sheet on the desktop.
- Keep the URI short. Long labels balloon the QR size and reduce scan
  reliability. Stick to the device name; let users rename on the mobile side.
- If we later want a single-tap "approve from QR" flow we'd need to mint a
  short-lived one-time token in the URI. Out of scope here.

## Out of scope

- Changing the WebSocket protocol or auth flow.
- Mobile-side discovery code — already shipped (see
  `muxy-mobile/src/transport/discovery.ts` and `app/scan-pair.tsx`).
- Windows/Linux ports of the desktop. If/when they exist they need their own
  mDNS advertising (Avahi on Linux, the built-in Windows 10+ mDNS responder
  on Windows).
