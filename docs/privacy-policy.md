# WPM+ Privacy Policy

Last updated: 2026-06-02

WPM+ is a local Wi-Fi credential vault and restore utility by sampple-korea. The app is designed to work without a server, account, analytics SDK, advertising SDK, or the Android `INTERNET` permission.

## Data WPM+ Handles

WPM+ can store Wi-Fi network names, security types, passwords, hidden-network flags, auto-join flags, notes, import/export records, restore reports, extraction diagnostics, and one-time local crash reports. This data is stored on the device in the app sandbox. The vault file is encrypted with Android Keystore-backed AES-GCM.

## Collection And Sharing

WPM+ does not collect, upload, sell, or share user data with the developer or third parties.

The app can disclose data only when the user explicitly chooses an action such as exporting a vault file, sharing a Wi-Fi QR/text payload, copying a password to the clipboard, or copying a local crash report. Those actions are user-directed and handled through Android system surfaces or the destination chosen by the user.

## Privileged Access

Normal Android apps cannot silently read saved Wi-Fi passwords. WPM+ can optionally use Shizuku/Sui after the user grants permission. That privileged path runs local shell/file diagnostics to inspect Wi-Fi configuration files and commands available on the device. Results remain local unless the user exports, shares, or copies them.

## Crash Reports

If the app crashes, WPM+ writes a one-time report in local app storage and shows a copy action on the next launch. Password-like fields and Wi-Fi QR passwords are redacted before the report is saved. WPM+ does not upload crash reports automatically.

## Backup And Deletion

Android Auto Backup and device-transfer extraction are disabled for WPM+. Deleting a network from the vault removes it from the local encrypted vault. Clearing app data or uninstalling WPM+ removes local app data from the device according to Android system behavior.

## Third-Party Code

WPM+ uses AndroidX, Jetpack Compose, and Shizuku libraries. The app does not include analytics, ads, or crash-upload SDKs.

## Play Disclosure References

This policy is written to align with Google Play user data and Data safety disclosure expectations:

- User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Data safety form guidance: https://support.google.com/googleplay/android-developer/answer/10787469
