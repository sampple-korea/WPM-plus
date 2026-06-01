# WPM+

[![Android](https://github.com/sampple-korea/WPM-plus/actions/workflows/android.yml/badge.svg)](https://github.com/sampple-korea/WPM-plus/actions/workflows/android.yml)

[English README](README.md)

WPM+는 최신 Android 보안 정책 안에서 Wi‑Fi 자격 증명을 백업, 추출, 복원, 감사하기 위한 Android 앱입니다. WPM은 Wi‑Fi Password Manager의 약자입니다.

상용 MVP 기준으로 다음 기능을 목표로 합니다.

- Android Keystore AES-GCM 기반 로컬 암호화 Wi‑Fi 금고
- Samsung Quick Share `WiFi_*.json.gz`, WPM+ 휴대용 내보내기, JSON, CSV, Wi‑Fi QR 가져오기
- 기기 간 이동을 위한 비밀번호 기반 암호화 WPM+ 내보내기 파일
- Shizuku/Sui 기반 추출 엔진: non-SDK Wi‑Fi Manager reflection 없이 로컬 shell 권한 Wi‑Fi 설정 파일과 진단을 사용
- `Settings.ACTION_WIFI_ADD_NETWORKS`를 이용한 5개 단위 시스템 복원
- 금고 UI의 검색/필터, 수정/삭제, 비밀번호 보기, 민감 클립보드 복사, QR/공유, 메모
- 앱 크래시 후 다음 실행에서 한 번만 표시되는 redaction 적용 크래시 리포트 복사 흐름
- 복원/가져오기/추출 결과 리포트
- 비밀번호 값을 로그와 리포트에 남기지 않는 redaction 정책
- Material 3 Jetpack Compose UI
- 영어, 한국어, 일본어, 스페인어 리소스
- GitHub Actions 기반 단위 테스트, lint, debug APK, release app bundle 빌드

## Android 제약

일반 Android 앱은 저장된 Wi‑Fi 비밀번호를 조용히 읽을 수 없습니다. 그래서 이 앱은 권한 단계별로 가능한 방법을 나눕니다.

| 모드 | 접근 범위 | 비밀번호 추출 |
| --- | --- | --- |
| 일반 앱 | 사용자가 가져온 파일과 QR | 사용자가 제공한 데이터에 한해 가능 |
| Shizuku ADB shell | shell 권한 Wi‑Fi 진단, 설정 파일, 명령 | production build에서는 보통 SSID만 가능 |
| Shizuku root / Sui | root로 읽을 수 있는 Wi‑Fi config store 파일과 Wi‑Fi 진단 | 알려진 로컬 파일에서 PSK를 best-effort로 추출 |

복원은 Android 공식 사용자 확인 API를 사용합니다. Android는 한 번에 최대 5개 네트워크를 확인 요청으로 받기 때문에 앱이 5개씩 큐를 돌리고 각 배치 결과를 리포트에 남깁니다.

관련 공식 문서:

- Wi‑Fi 네트워크 저장: https://developer.android.com/develop/connectivity/wifi/wifi-save-network-passpoint-config
- `Settings.ACTION_WIFI_ADD_NETWORKS`: https://developer.android.com/reference/android/provider/Settings#ACTION_WIFI_ADD_NETWORKS
- 앱별 언어 설정: https://developer.android.com/guide/topics/resources/app-languages
- Android Keystore: https://developer.android.com/privacy-and-security/keystore

Shizuku 문서:

- Shizuku API: https://github.com/RikkaApps/Shizuku-API

참고한 프로젝트와 앱:

- Khh-vu의 WiFi Password Manager: https://github.com/Khh-vu/wifi-password-manager
- VREM WiFi Analyzer: https://github.com/VREMSoftwareDevelopment/WiFiAnalyzer
- Ubiquiti WiFiman: https://play.google.com/store/apps/details?id=com.ubnt.usurvey
- NetSpot WiFi Analyzer: https://www.netspotapp.com/
- Fing: https://www.fing.com/products/fing-app
- Instabridge: https://instabridge.com/

이 앱들은 제품 경계를 정하는 데 참고했습니다. WPM+는 개인 Wi‑Fi 자격 증명 관리에 맞는 금고, 가져오기/내보내기, QR/공유, 진단, 명확한 상태 표시만 채택합니다. 공개 핫스팟 지도, LAN/포트 스캐너, 히트맵, 속도 테스트, 광범위한 네트워크 보안 도구는 권한과 UI 복잡도를 키우고 신뢰 모델도 달라지므로 의도적으로 제외합니다.

## 현재 상태

이 저장소는 MVP 개발 중입니다. 현재 구현된 항목은 다음과 같습니다.

- Material 3 Compose 프로젝트 골격
- 다국어 리소스 및 locale config 생성 설정
- 암호화 금고 저장소
- 가져오기 파서와 단위 테스트
- Shizuku UserService 명령 실행기
- Wi‑Fi config store XML 및 `wpa_supplicant.conf` 파서
- WPM+ gzip 및 비밀번호 암호화 내보내기/불러오기 코덱
- 금고 검색/필터, 수정/삭제 생명주기, 메모, 보기/복사/공유 overflow 컨트롤, redaction 적용 크래시 리포트 복사 다이얼로그
- 네트워크별 복원 가능 여부와 건너뜀 사유를 보여주는 복원 선택 검토
- 5개 단위 복원 세션 모델과 UI 연결
- debug APK와 release AAB lane을 포함한 GitHub Actions 빌드 워크플로

남은 하드닝 작업:

- 제조사별 Wi‑Fi config 경로 확장
- Samsung, Pixel, Xiaomi, Android Enterprise 프로필 테스트
- 정식 금고 마이그레이션 정책

## 개발

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew bundleRelease
```

이 저장소는 실제 Wi‑Fi 비밀번호를 fixture, 로그, 리포트에 저장하지 않는 것을 원칙으로 합니다.

GitHub Actions debug APK는 저장소 secret에 보관된 CI debug 키로 서명합니다. 그래서 main 브랜치 빌드마다 APK 서명 인증서가 유지됩니다. main 브랜치 release bundle은 `WPM_PLUS_RELEASE_*` upload key secret을 요구하며 Play App Signing용 `bundleRelease` 경로로 빌드합니다. 로컬 빌드는 서명 환경 변수를 직접 제공하지 않는 한 Android 기본 debug keystore를 계속 사용합니다.

## 스토어 및 개인정보 자료

- 개인정보처리방침 초안: [docs/privacy-policy.md](docs/privacy-policy.md)
- Google Play Data safety 초안: [docs/play-data-safety.md](docs/play-data-safety.md)
- Android App Bundle 업로드 안내: https://developer.android.com/studio/publish/upload-bundle
- Play App Signing 안내: https://support.google.com/googleplay/android-developer/answer/9842756

## 보안 원칙

- 비밀번호 값은 로그, 리포트, 크래시 메시지, 요약 UI에 노출하지 않음
- 암호화 금고 파일은 Android Auto Backup 및 기기 이전에서 제외
- Android Keystore 키는 기기 종속
- MVP 빌드에서는 누락된 인증 프롬프트 때문에 추출/복원이 막히지 않도록 로컬 금고에 생체 인증 잠금을 요구하지 않음
- Shizuku/root 추출 시 사용된 권한 모드를 명확히 리포트

## 라이선스

WPM+는 Apache License, Version 2.0으로 배포됩니다.

재배포 시 `NOTICE`의 attribution 고지를 유지해야 합니다.
