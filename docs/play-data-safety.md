# Google Play Data Safety Draft

Last updated: 2026-06-02

This is the Play Console Data safety draft for the current WPM+ app behavior. Re-check it before submission if permissions, SDKs, networking, analytics, account features, or crash upload behavior change.

## Collection

- Does the app collect or share any required user data types with the developer or third parties? No.
- Does the app transmit user data off the device automatically? No.
- Does the app include analytics, ads, or crash-report upload SDKs? No.
- Does the app use encryption in transit for collected data? Not applicable, because WPM+ does not transmit collected data.

## Local Data Handled

WPM+ handles the following data locally in the app sandbox:

- Wi-Fi network names, security types, passwords, hidden-network flags, and auto-join flags
- User notes attached to Wi-Fi credentials
- Import, extraction, restore, and crash diagnostics
- User-selected export files, share payloads, and clipboard copies

The vault is encrypted locally with Android Keystore-backed AES-GCM. Android Auto Backup and device-transfer extraction are disabled.

## User-Initiated Disclosure

The user can intentionally move data out of the app through these explicit actions:

- Exporting a WPM+ vault file
- Sharing Wi-Fi QR/text details through Android's share sheet
- Copying a password or crash report to the clipboard
- Selecting an external document provider as the export target

These are user-directed actions, not automatic app collection or developer sharing. If the user chooses a cloud drive, messenger, or other destination, that destination's own terms and privacy policy apply.

## Security Practices To Declare

- Data encrypted at rest in the local vault: Yes
- Users can request data deletion: Yes, by deleting individual vault entries or clearing/uninstalling app data
- Data is transferred to the developer: No

## Policy References

- Google Play User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Data safety form guidance: https://support.google.com/googleplay/android-developer/answer/10787469
