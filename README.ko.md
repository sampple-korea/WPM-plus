# WPM+

[![Android](https://github.com/sampple-korea/wifi-vault-restore/actions/workflows/android.yml/badge.svg)](https://github.com/sampple-korea/wifi-vault-restore/actions/workflows/android.yml)

[English README](README.md)

WPM+는 최신 Android 보안 정책 안에서 Wi‑Fi 자격 증명을 백업, 추출, 복원, 감사하기 위한 Android 앱입니다. WPM은 Wi‑Fi Password Manager의 약자입니다.

상용 MVP 기준으로 다음 기능을 목표로 합니다.

- Android Keystore AES-GCM 기반 로컬 암호화 Wi‑Fi 금고
- Samsung Quick Share `WiFi_*.json.gz`, WPM+ 휴대용 내보내기, JSON, CSV, Wi‑Fi QR 가져오기
- 기기 간 이동을 위한 비밀번호 기반 암호화 WPM+ 내보내기 파일
- Shizuku/Sui 기반 추출 엔진: privileged Wi‑Fi Manager API를 먼저 시도하고, 실패하면 shell 파일/진단으로 폴백
- `Settings.ACTION_WIFI_ADD_NETWORKS`를 이용한 5개 단위 시스템 복원
- 금고 UI의 검색, 비밀번호 보기, 민감 클립보드 복사, 메모
- 앱 크래시 후 다음 실행에서 한 번만 표시되는 크래시 리포트 복사 팝업
- 복원/가져오기/추출 결과 리포트
- 비밀번호 값을 로그와 리포트에 남기지 않는 redaction 정책
- Material 3 Jetpack Compose UI
- 영어, 한국어, 일본어, 스페인어 리소스
- GitHub Actions 기반 단위 테스트 및 debug APK 빌드

## Android 제약

일반 Android 앱은 저장된 Wi‑Fi 비밀번호를 조용히 읽을 수 없습니다. 그래서 이 앱은 권한 단계별로 가능한 방법을 나눕니다.

| 모드 | 접근 범위 | 비밀번호 추출 |
| --- | --- | --- |
| 일반 앱 | 사용자가 가져온 파일과 QR | 사용자가 제공한 데이터에 한해 가능 |
| Shizuku ADB shell | privileged Wi‑Fi Manager API, shell 권한 Wi‑Fi 진단/명령 | production build에서는 보통 SSID만 가능 |
| Shizuku root / Sui | privileged Wi‑Fi Manager API와 root로 읽을 수 있는 Wi‑Fi config store 파일 | 시스템 API와 알려진 파일에서 PSK를 best-effort로 추출 |

복원은 Android 공식 사용자 확인 API를 사용합니다. Android는 한 번에 최대 5개 네트워크를 확인 요청으로 받기 때문에 앱이 5개씩 큐를 돌리고 각 배치 결과를 리포트에 남깁니다.

관련 공식 문서:

- Wi‑Fi 네트워크 저장: https://developer.android.com/develop/connectivity/wifi/wifi-save-network-passpoint-config
- `Settings.ACTION_WIFI_ADD_NETWORKS`: https://developer.android.com/reference/android/provider/Settings#ACTION_WIFI_ADD_NETWORKS
- 앱별 언어 설정: https://developer.android.com/guide/topics/resources/app-languages
- Android Keystore: https://developer.android.com/privacy-and-security/keystore

Shizuku 문서:

- Shizuku API: https://github.com/RikkaApps/Shizuku-API

참고한 프로젝트:

- Khh-vu의 WiFi Password Manager: https://github.com/Khh-vu/wifi-password-manager

참고 앱에서는 추출 순서, 내보내기/불러오기 UX, 캐시 중심 금고 UI, 민감 클립보드 처리 아이디어를 확인했습니다. WPM+는 해당 아이디어를 자체 구조로 다시 구현했습니다.

## 현재 상태

이 저장소는 MVP 개발 중입니다. 현재 구현된 항목은 다음과 같습니다.

- Material 3 Compose 프로젝트 골격
- 다국어 리소스 및 locale config 생성 설정
- 암호화 금고 저장소
- 가져오기 파서와 단위 테스트
- Shizuku privileged Wi‑Fi Manager reader와 UserService 명령 실행기
- Wi‑Fi config store XML 및 `wpa_supplicant.conf` 파서
- WPM+ gzip 및 비밀번호 암호화 내보내기/불러오기 코덱
- 금고 검색, 메모, 보기/복사 컨트롤, 크래시 리포트 복사 다이얼로그
- 5개 단위 복원 세션 모델과 UI 연결
- GitHub Actions 빌드 워크플로

남은 하드닝 작업:

- 동적 UI 문구 전체 다국어화
- 제조사별 Wi‑Fi config 경로 확장
- Samsung, Pixel, Xiaomi, Android Enterprise 프로필 테스트
- 정식 금고 마이그레이션 정책

## 개발

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

이 저장소는 실제 Wi‑Fi 비밀번호를 fixture, 로그, 리포트에 저장하지 않는 것을 원칙으로 합니다.

## 보안 원칙

- 비밀번호 값은 로그, 리포트, 크래시 메시지, 요약 UI에 노출하지 않음
- 암호화 금고 파일은 Android Auto Backup 및 기기 이전에서 제외
- Android Keystore 키는 기기 종속
- 기기가 보안 잠금 상태이면 생체 인증 또는 기기 자격 증명을 요구
- Shizuku/root 추출 시 사용된 권한 모드를 명확히 리포트

## 라이선스

아직 라이선스를 선택하지 않았습니다.
