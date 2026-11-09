# Privacy Policy

_Effective date: the date this document was first published at its public URL._

_Last updated: November 8, 2026._

Muxy ("the app") lets your phone or tablet connect to computers running Muxy 1 or Muxy 2, or to SSH servers you choose. This policy describes the data handled by the iOS and Android apps.

## Summary

- No Muxy account, sign-up, or email is required.
- No analytics, advertising, or third-party tracking SDKs are included.
- Terminal, file, and version-control data travels directly between your device and the computers you connect to, not through a Muxy-operated server.
- If you turn on notifications, your paired Muxy 2 computers send them through the muxy.app relay to Apple or Google. Their content is end-to-end encrypted, so the relay can't read it.
- Android purchases use Google Play. Android QR scanning uses Google's code scanner.

## What the app stores on your device

- **Connection credentials.** Pairing identities and tokens, Muxy 2 credentials, SSH passwords or private keys and passphrases, and trusted SSH host-key fingerprints are stored locally. iOS uses Keychain. Android encrypts secrets with AES-256-GCM using an Android Keystore key and stores the encrypted values in the app's private, non-backed-up storage. Credentials authenticate only the connections you configure.
- **Notification keys and preferences.** When notifications are on, each paired Muxy 2 computer gives the app a key that decrypts its notifications. iOS stores these keys in a Keychain group shared only with the app's notification extension, and stores which computers have notifications on, their names, and their notification status in an app group shared with that extension. Android stores the keys with the other encrypted secrets, and the preferences and push token in private DataStore files.
- **Saved connections and preferences.** Connection names, addresses, ports, SSH usernames, workspace selections, theme, terminal options, onboarding state, and demo mode are stored in local preferences. Android uses private DataStore files and disables app backups; credentials are stored separately from these preferences.
- **Trial and purchases (Android only).** The three-day trial starts on the first app launch after installation. Its timestamp is stored with the encrypted secrets. The app queries Google Play for the unlock product and handles purchase tokens to acknowledge completed purchases. Purchase state is held in memory and restored from Play; the app does not receive your payment details, name, or email.
- **Diagnostics.** The apps write diagnostic events to the platform's logging system for troubleshooting. The Android app logs connection and billing lifecycle events and error types without recording credential contents. There is no automatic upload of diagnostics or crash reports to Muxy.

On the first Android launch after upgrading from the previous app, a one-time local import attempts to preserve saved Muxy 1 connections, credentials, preferences, workspace selections, and the trial timestamp. Invalid or unreadable records are skipped. The old stores and obsolete encryption keys are then removed. Purchases are restored from Google Play, not imported from local storage.

Deleting a connection removes its saved credentials. Clearing Android app storage or uninstalling the Android app removes its local data; a Play purchase remains associated with your Google account and can be restored. iOS Keychain items may persist after uninstalling the app.

## What the app sends over the network

The app connects directly to the address and port you configure. Muxy 1 uses WebSocket connections, Muxy 2 uses the Muxy mobile SDK, and SSH uses encrypted SSH sessions. Use a trusted local network or private VPN, especially for unencrypted Muxy 1 connections. Nearby discovery looks for Muxy services on your local network.

These connections carry authentication data, terminal output and input, and the file or version-control operations you request, including file reads and edits, commits, branches, worktrees, pushes, pulls, and pull requests. Those operations may cause your computer to contact services you have configured there.

The app does not route this content through a Muxy-operated server. It has no background networking service; connection shutdown may finish after the app leaves the foreground.

## Push notifications

Notifications are optional and can be turned off for each computer in Settings → Notifications, or for the whole app in the system settings.

- **Push token.** When notifications are allowed and on for at least one paired Muxy 2 computer, the app gets a push token from Apple Push Notification service (iOS) or Firebase Cloud Messaging (Android) and gives it only to your paired Muxy 2 computers, over the connection you already have. The app itself never contacts muxy.app. On Android, Firebase Cloud Messaging stays off until then; on Android 10 to 12, where notifications are allowed by default, that is as soon as you pair a Muxy 2 computer. Firebase Analytics is not included.
- **The relay.** Your computer registers the token with the relay at muxy.app, which holds the Apple and Google credentials, and sends each notification through it. The relay stores the token encrypted, plus registration timestamps and daily delivery counts. It never receives your terminal content, project or session names, or who you are.
- **End-to-end encryption.** Each notification's text is encrypted on your computer with that computer's key and decrypted on your device. Apple, Google, and muxy.app only see a generic "An agent needs your attention" or "An agent finished working" fallback and an opaque encrypted payload. If a notification can't be decrypted with a key the app holds, it shows only that fallback text.
- **Turning notifications off** for a computer asks it to delete its relay registration and deletes that computer's key from your device; if the computer can't be reached, the app asks the next time it connects. Removing a computer from the app does the same if the computer can be reached at that moment, and always deletes its key from your device; otherwise revoke this device on the computer to stop its notifications. Revoking the device on the computer always deletes the registration.

Apple and Google handle notification delivery under their own privacy policies ([Apple](https://www.apple.com/legal/privacy/), [Google](https://policies.google.com/privacy)).

On Android, the Google Play Billing Library communicates with Google Play to load product information, check and restore purchases, complete a purchase, and acknowledge it. Purchase checks also happen at startup and when returning to the foreground, not only when you tap Unlock or Restore. Google handles this under its [privacy policy](https://policies.google.com/privacy).

## QR scanning and permissions

- **Network access.** iOS requests Local Network access. Android declares `INTERNET` and `ACCESS_NETWORK_STATE` to reach your chosen computers and Google Play.
- **Notifications.** The app asks for notification permission after you pair a Muxy 2 computer or when you turn notifications on, not at first launch. Android 13 and newer request `POST_NOTIFICATIONS` at that moment.
- **QR scanning.** On Android, Google Play services provides the code-scanner interface without the app requesting camera permission. The app receives the scanned pairing code, not camera images; Google processes scanning on-device. iOS requests camera access when you choose to scan a pairing code. The app does not save or upload camera images.
- **Billing (Android only).** `com.android.vending.BILLING` enables the in-app purchase. It is not a runtime permission.
- Android does not request camera, microphone, location, contacts, or shared-storage permissions. It also declares an app-specific, signature-protected permission used by AndroidX for internal broadcast receivers.

## What the app does not collect

Muxy does not collect your terminal content, files, credentials, contacts, location, payment details, usage analytics, advertising identifiers, or crash analytics on its servers. The app does not sell your data. The Google Play purchase and scanner interactions and the optional, end-to-end encrypted push notifications described above are the exceptions to direct communication with your chosen computers.

## Children

The app is a developer tool and is not directed to children under 13.

## Changes to this policy

If this policy changes, the updated version will be posted at this URL with a new "Last updated" date.

## Contact

Questions about this policy: sa.vaziry@gmail.com
