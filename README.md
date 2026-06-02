# WPM+

[![Android](https://github.com/sampple-korea/WPM-plus/actions/workflows/android.yml/badge.svg)](https://github.com/sampple-korea/WPM-plus/actions/workflows/android.yml)

[한국어 README](README.ko.md)

WPM+ is an Android app for backing up, extracting, restoring, and auditing Wi-Fi credentials under the limits of modern Android security. WPM stands for Wi-Fi Password Manager.

The project is built as a commercial-grade MVP:

- Encrypted local Wi-Fi vault backed by Android Keystore AES-GCM
- Import from Samsung Quick Share `WiFi_*.json.gz`, WPM+ portable exports, JSON, CSV, and Wi-Fi QR payloads
- Password-based encrypted WPM+ export files for moving vaults between devices
- Shizuku/Sui extraction engine that uses local shell-readable Wi-Fi config files and diagnostics without non-SDK Wi-Fi Manager reflection
- Android system restore flow using `Settings.ACTION_WIFI_ADD_NETWORKS` in batches of five
- Search, filter, edit, delete, password reveal, sensitive clipboard copy, QR/share, and notes in the vault UI
- One-time redacted crash report copy flow on the next launch after an app crash
- Restore/import/extract reports with redacted password handling
- Material 3 Jetpack Compose UI
- English, Korean, Japanese, and Spanish resources
- GitHub Actions CI for unit tests, lint, debug APKs, and signed release APKs

## Android Reality Check

Normal Android apps cannot silently read saved Wi-Fi passwords. This app therefore uses layered extraction:

| Mode | Expected access | Password extraction |
| --- | --- | --- |
| Normal app | User-imported files and QR payloads | Yes, only for user-provided data |
| Shizuku ADB shell | Shell-accessible Wi-Fi diagnostics, config files, and commands | Often SSID-only on production builds |
| Shizuku root / Sui | Root-readable Wi-Fi config store files and Wi-Fi diagnostics | Best-effort PSK extraction from known local files |

The restore flow uses the official Android user-confirmed API. Android accepts up to five networks per confirmation request, so the app queues batches and records the result of every batch.

Relevant Android docs:

- Save Wi-Fi networks: https://developer.android.com/develop/connectivity/wifi/wifi-save-network-passpoint-config
- `Settings.ACTION_WIFI_ADD_NETWORKS`: https://developer.android.com/reference/android/provider/Settings#ACTION_WIFI_ADD_NETWORKS
- Per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages
- Android Keystore: https://developer.android.com/privacy-and-security/keystore

Shizuku docs:

- Shizuku API: https://github.com/RikkaApps/Shizuku-API

References reviewed:

- WiFi Password Manager by Khh-vu: https://github.com/Khh-vu/wifi-password-manager
- WiFi Analyzer by VREM: https://github.com/VREMSoftwareDevelopment/WiFiAnalyzer
- Ubiquiti WiFiman: https://play.google.com/store/apps/details?id=com.ubnt.usurvey
- NetSpot WiFi Analyzer: https://www.netspotapp.com/
- Fing: https://www.fing.com/products/fing-app
- Instabridge: https://instabridge.com/

These apps informed the product boundaries. WPM+ adopts the vault, import/export, QR/share, diagnostics, and clear status ideas that fit a private Wi-Fi credential manager. It deliberately avoids public hotspot maps, LAN/port scanners, heatmaps, speed tests, and broad network-security tools because those would add permissions, clutter, and a different trust model.

## Current Status

This repository is in active MVP development. Implemented so far:

- Project scaffold with Material 3 Compose
- Multilingual resources and generated locale config
- Encrypted vault repository
- Import parsers and unit tests
- Shizuku UserService command runner
- System Wi-Fi extraction parser for Wi-Fi config store XML and `wpa_supplicant.conf`
- WPM+ gzip and password-encrypted export/import codecs
- Vault search/filter, edit/delete lifecycle, notes, reveal/copy/share overflow controls, and redacted crash-report copy dialog
- Restore selection review with per-network eligibility and skip reasons
- Batch restore session model and UI wiring
- GitHub Actions build workflow with debug APK and signed release APK lanes

Remaining hardening work:

- More Android vendor config path coverage
- Device testing across Samsung, Pixel, Xiaomi, and Android Enterprise profiles
- Formal vault migration policy

## Development

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

The repo intentionally avoids storing real Wi-Fi passwords in fixtures, logs, or reports.

GitHub Actions debug APKs are signed with a repository secret-backed CI debug key so the APK signing certificate stays stable across main-branch builds. Release APKs are built through `assembleRelease`; when `WPM_PLUS_RELEASE_*` signing secrets are present, CI verifies and uploads the signed release APK. Local builds keep using the normal Android debug keystore unless signing environment variables are provided.

## Store And Privacy Materials

- Privacy policy draft: [docs/privacy-policy.md](docs/privacy-policy.md)
- Google Play Data safety draft: [docs/play-data-safety.md](docs/play-data-safety.md)

## Security Principles

- No password values in logs, reports, crash messages, or UI summaries
- Encrypted vault file excluded from Android Auto Backup and device transfer
- Android Keystore key is device-bound
- The app does not require biometric unlock for the local vault in the MVP build, so extraction and restore jobs can run without a missing authentication prompt
- Shizuku/root extraction reports the exact privilege mode used

## License

WPM+ is licensed under the Apache License, Version 2.0.

Redistributions must preserve the attribution notices in `NOTICE`.
