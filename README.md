# WPM+

[![Android](https://github.com/sampple-korea/wifi-vault-restore/actions/workflows/android.yml/badge.svg)](https://github.com/sampple-korea/wifi-vault-restore/actions/workflows/android.yml)

[한국어 README](README.ko.md)

WPM+ is an Android app for backing up, extracting, restoring, and auditing Wi-Fi credentials under the limits of modern Android security. WPM stands for Wi-Fi Password Manager.

The project is built as a commercial-grade MVP:

- Encrypted local Wi-Fi vault backed by Android Keystore AES-GCM
- Import from Samsung Quick Share `WiFi_*.json.gz`, WPM+ portable exports, JSON, CSV, and Wi-Fi QR payloads
- Password-based encrypted WPM+ export files for moving vaults between devices
- Shizuku/Sui extraction engine that tries the privileged Wi-Fi Manager API first, then falls back to shell-readable files and diagnostics
- Android system restore flow using `Settings.ACTION_WIFI_ADD_NETWORKS` in batches of five
- Search, password reveal, sensitive clipboard copy, and notes in the vault UI
- One-time crash report dialog on the next launch after an app crash
- Restore/import/extract reports with redacted password handling
- Material 3 Jetpack Compose UI
- English, Korean, Japanese, and Spanish resources
- GitHub Actions CI for unit tests and debug APK builds

## Android Reality Check

Normal Android apps cannot silently read saved Wi-Fi passwords. This app therefore uses layered extraction:

| Mode | Expected access | Password extraction |
| --- | --- | --- |
| Normal app | User-imported files and QR payloads | Yes, only for user-provided data |
| Shizuku ADB shell | Privileged Wi-Fi Manager API, shell-accessible Wi-Fi diagnostics, and commands | Often SSID-only on production builds |
| Shizuku root / Sui | Privileged Wi-Fi Manager API and root-readable Wi-Fi config store files | Best-effort PSK extraction from system APIs and known files |

The restore flow uses the official Android user-confirmed API. Android accepts up to five networks per confirmation request, so the app queues batches and records the result of every batch.

Relevant Android docs:

- Save Wi-Fi networks: https://developer.android.com/develop/connectivity/wifi/wifi-save-network-passpoint-config
- `Settings.ACTION_WIFI_ADD_NETWORKS`: https://developer.android.com/reference/android/provider/Settings#ACTION_WIFI_ADD_NETWORKS
- Per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages
- Android Keystore: https://developer.android.com/privacy-and-security/keystore

Shizuku docs:

- Shizuku API: https://github.com/RikkaApps/Shizuku-API

Reference reviewed:

- WiFi Password Manager by Khh-vu: https://github.com/Khh-vu/wifi-password-manager

The reference app informed the extraction order, export/import ergonomics, cache-first UI ideas, and sensitive clipboard handling. WPM+ implements those ideas in its own code structure.

## Current Status

This repository is in active MVP development. Implemented so far:

- Project scaffold with Material 3 Compose
- Multilingual resources and generated locale config
- Encrypted vault repository
- Import parsers and unit tests
- Shizuku privileged Wi-Fi Manager reader and UserService command runner
- System Wi-Fi extraction parser for Wi-Fi config store XML and `wpa_supplicant.conf`
- WPM+ gzip and password-encrypted export/import codecs
- Vault search, notes, reveal/copy controls, and crash-report copy dialog
- Batch restore session model and UI wiring
- GitHub Actions build workflow

Remaining hardening work:

- Full UI copy localization for all dynamic text
- More Android vendor config path coverage
- Device testing across Samsung, Pixel, Xiaomi, and Android Enterprise profiles
- Formal vault migration policy

## Development

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The repo intentionally avoids storing real Wi-Fi passwords in fixtures, logs, or reports.

## Security Principles

- No password values in logs, reports, crash messages, or UI summaries
- Encrypted vault file excluded from Android Auto Backup and device transfer
- Android Keystore key is device-bound
- Biometric or device credential authentication is requested when the device is secure
- Shizuku/root extraction reports the exact privilege mode used

## License

License is not selected yet.
