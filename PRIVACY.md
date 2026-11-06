# Privacy Policy

_Effective date: the date this document was first published at its public URL._

_Last updated: November 4, 2026._

Muxy ("the app") lets your phone or tablet connect to computers running Muxy 1 or Muxy 2, or to SSH servers you choose. This policy describes the data handled by the iOS and Android apps.

## Summary

- No Muxy account, sign-up, or email is required.
- No analytics, advertising, or third-party tracking SDKs are included.
- Terminal, file, and version-control data travels directly between your device and the computers you connect to, not through a Muxy-operated server.
- Android QR scanning uses Google's code scanner.

## What the app stores on your device

- **Connection credentials.** Pairing identities and tokens, Muxy 2 credentials, SSH passwords or private keys and passphrases, and trusted SSH host-key fingerprints are stored locally. iOS uses Keychain. Android encrypts secrets with AES-256-GCM using an Android Keystore key and stores the encrypted values in the app's private, non-backed-up storage. Credentials authenticate only the connections you configure.
- **Saved connections and preferences.** Connection names, addresses, ports, SSH usernames, workspace selections, theme, terminal options, onboarding state, and demo mode are stored in local preferences. Android uses private DataStore files and disables app backups; credentials are stored separately from these preferences.
- **Diagnostics.** The apps write diagnostic events to the platform's logging system for troubleshooting. The Android app logs connection lifecycle events and error types without recording credential contents. There is no automatic upload of diagnostics or crash reports to Muxy.

On the first Android launch after upgrading from the previous app, a one-time local import attempts to preserve saved Muxy 1 connections, credentials, preferences, and workspace selections. Invalid or unreadable records are skipped. The old stores and obsolete encryption keys are then removed.

Deleting a connection removes its saved credentials. Clearing Android app storage or uninstalling the Android app removes its local data. iOS Keychain items may persist after uninstalling the app.

## What the app sends over the network

The app connects directly to the address and port you configure. Muxy 1 uses WebSocket connections, Muxy 2 uses the Muxy mobile SDK, and SSH uses encrypted SSH sessions. Use a trusted local network or private VPN, especially for unencrypted Muxy 1 connections. Nearby discovery looks for Muxy services on your local network.

These connections carry authentication data, terminal output and input, and the file or version-control operations you request, including file reads and edits, commits, branches, worktrees, pushes, pulls, and pull requests. Those operations may cause your computer to contact services you have configured there.

The app does not route this content through a Muxy-operated server. It has no background networking service; connection shutdown may finish after the app leaves the foreground.

## QR scanning and permissions

- **Network access.** iOS requests Local Network access. Android declares `INTERNET` and `ACCESS_NETWORK_STATE` to reach your chosen computers.
- **QR scanning.** On Android, Google Play services provides the code-scanner interface without the app requesting camera permission. The app receives the scanned pairing code, not camera images; Google processes scanning on-device. iOS requests camera access when you choose to scan a pairing code. The app does not save or upload camera images.
- Android does not request camera, microphone, location, contacts, or shared-storage permissions. It also declares an app-specific, signature-protected permission used by AndroidX for internal broadcast receivers.

## What the app does not collect

Muxy does not collect your terminal content, files, credentials, contacts, location, payment details, usage analytics, advertising identifiers, or crash analytics on its servers. The app does not sell your data. The Google code-scanner interaction described above is the platform-service exception to direct communication with your chosen computers.

## Children

The app is a developer tool and is not directed to children under 13.

## Changes to this policy

If this policy changes, the updated version will be posted at this URL with a new "Last updated" date.

## Contact

Questions about this policy: sa.vaziry@gmail.com
