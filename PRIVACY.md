# Privacy Policy

_Effective date: the date this document was first published at its public URL._

Muxy ("the app") is a developer tool that lets your phone or tablet (iOS or Android) connect to a Mac running the Muxy desktop application over your local network or a private VPN. This policy describes what data the app handles and what it does not.

## Summary

- No account, no sign-up, no email required.
- No analytics, advertising, or third-party tracking SDKs.
- The app communicates only with the Mac you choose to pair it with.
- All data stays on your devices.

## What the app stores on your device

The app stores the following locally on your device. None of it is transmitted to Muxy or any third party.

- **Pairing credentials.** A random device ID and token are generated on first launch and stored in the platform's secure storage (iOS Keychain on Apple devices; AES-256 EncryptedSharedPreferences via Jetpack Security on Android), device-locked and limited to this device only. They are used to authenticate the app to a Mac you have paired with.
- **Saved devices.** The names, hostnames, and ports of Macs you have added are stored in the app's local preferences (UserDefaults on iOS, private SharedPreferences on Android). Credentials are not stored here.
- **Preferences.** Terminal font size and Nerd Font toggle.
- **Trial and purchase state (Android only).** The timestamp of your first successful pairing is stored in AES-256 EncryptedSharedPreferences to track the 3-day free trial. Whether you have purchased the in-app unlock is determined by querying Google Play on your device; the app caches this entitlement state locally. No purchase or billing details are stored by the app.
- **Diagnostic log (in memory only).** While the app is running, it keeps a short rolling log of connection events (timestamps, the hostname and port you are connecting to, and request identifiers) to help you troubleshoot connection problems. This log is held in memory, is cleared when the app exits, and is never sent anywhere. If a connection error occurs, the app shows the log inside an error sheet so you can copy or share it yourself if you choose to.

You can remove a saved device at any time from the device list. Uninstalling the app removes all locally stored data.

## What the app sends over the network

When you connect to a Mac, the app opens a direct WebSocket connection to the address and port you entered. It sends only the messages required to authenticate, view terminal output, control panes, and perform the version-control actions you initiate (such as staging, committing, pushing, pulling, switching branches, managing worktrees, or opening pull requests).

The app does not contact any Muxy-operated server. It does not perform background networking.

On Android, the app uses the Google Play Billing Library to offer a one-time in-app purchase that unlocks connecting after the free trial ends. When you tap to unlock or restore a purchase, the app communicates with Google Play services on your device, which in turn communicates with Google's servers under Google's own privacy policy. The app receives only whether the unlock product has been purchased on your Google account; it does not receive or store your name, email, or payment details.

## What the app does not collect

- No personal information.
- No contacts, photos, location, microphone, or camera data.
- No usage analytics or crash analytics.
- No advertising identifiers.
- No data sold or shared with third parties.

## Permissions

- **Network access.** Local Network on iOS; `INTERNET` and `ACCESS_NETWORK_STATE` on Android. Required so the app can reach the Mac you pair with on your LAN or VPN. No other runtime permissions are requested.
- **Billing (Android only).** `com.android.vending.BILLING` is added by the Google Play Billing Library to enable the in-app purchase. It is not a runtime permission and grants no access to personal data.

## Children

The app is a developer tool and is not directed to children under 13.

## Changes to this policy

If this policy changes, the updated version will be posted at this URL with a new "Last updated" date.

## Contact

Questions about this policy: sa.vaziry@gmail.com
