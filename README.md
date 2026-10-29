# FCM TTS

FCM 서버에서 받은 메시지를 **폰 내장 TTS(TextToSpeech)** 로 읽어 주는 Android용 React Native 앱입니다.

- 앱이 포그라운드·백그라운드·종료·화면 꺼짐 상태 모두에서 메시지를 받아 바로 읽습니다.
- 읽기 처리는 네이티브(Kotlin) `FirebaseMessagingService` 에서 하므로 JS 런타임이 떠 있지 않아도 동작합니다.
- `data` 메시지와 `notification` 메시지를 모두 읽습니다. (`notification` 메시지는 백그라운드에서 SDK 가 알림만 띄우는데,
  `handleIntent` 에서 가로채 함께 읽습니다.)
- 읽는 동안에는 포그라운드 서비스("메시지를 읽는 중" 알림)로 프로세스를 유지하고, wake lock 과 오디오 포커스(다른 소리 볼륨 낮춤)를 잡습니다.
  다 읽으면 서비스는 스스로 종료됩니다.
- 화면에서 FCM 토큰 확인/공유, 읽기 켜기/끄기, 속도·높낮이·언어 설정, 테스트 읽기, 수신 기록(최근 50개)을 볼 수 있습니다.

## 구조

```
App.tsx                         RN 화면 (토큰, 설정, 테스트, 수신 기록)
src/FcmTts.ts                   네이티브 모듈 JS 래퍼 + 이벤트
android/app/src/main/java/com/fcmtts/tts/
  TtsMessagingService.kt        FCM 수신 (data + notification) → 기록 저장 → 읽기 시작
  SpeechService.kt              읽는 동안 유지되는 포그라운드 서비스 (wake lock, 오디오 포커스)
  TtsSpeaker.kt                 TextToSpeech 래퍼 (초기화 전 대기열 처리)
  TtsSettings.kt                SharedPreferences 설정/기록 저장
  FcmTtsModule.kt               JS 용 네이티브 모듈 (getToken, speak, setSettings ...)
  FcmTtsPackage.kt
server/send.js                  firebase-admin 으로 테스트 메시지 보내는 예제
```

## 준비

1. [Firebase 콘솔](https://console.firebase.google.com)에서 프로젝트를 만들고 Android 앱을 추가합니다.
   패키지 이름은 `com.fcmtts` 입니다.
2. 내려받은 `google-services.json` 을 `android/app/google-services.json` 에 둡니다.
   (파일이 없으면 빌드는 되지만 FCM 이 동작하지 않고 화면에 오류가 표시됩니다.)
3. 폰에 TTS 엔진(예: Google 음성 인식 및 합성)과 한국어 음성 데이터가 설치되어 있어야 합니다.

## 실행

```sh
npm install
npm run android          # 기기/에뮬레이터 연결 후
```

릴리스 APK:

```sh
cd android && ./gradlew assembleRelease
# android/app/build/outputs/apk/release/app-release.apk
```

> 릴리스 빌드는 기본적으로 debug 키스토어로 서명되어 있습니다. 배포하려면 `android/app/build.gradle` 의 `signingConfigs` 를 바꾸세요.

앱을 실행하면 첫 화면에 FCM 토큰이 표시됩니다. "토큰 공유 / 복사" 버튼으로 서버에 전달하세요.

## 메시지 보내기

`data` 메시지와 `notification` 메시지 모두 읽지만, 알림 표시 없이 음성만 원하면 `data` 만 보내는 것을 권장합니다.
**반드시 `android.priority: "high"`** 로 보내세요. 일반 우선순위 메시지는 도즈 상태에서 늦게 전달되고,
백그라운드에서 포그라운드 서비스를 시작할 수 없습니다. (이 경우에도 약 18초 안에서는 읽지만 긴 문장은 잘릴 수 있습니다.)

읽을 텍스트는 `data` 의 다음 키 중 처음 있는 값을 사용합니다: `text` → `tts` → `message` → `body`.
`title` 은 기록에만 표시됩니다.

### HTTP v1 API

```http
POST https://fcm.googleapis.com/v1/projects/<PROJECT_ID>/messages:send
Authorization: Bearer <OAuth2 액세스 토큰>
Content-Type: application/json

{
  "message": {
    "token": "<기기 토큰>",
    "data": { "title": "주문 알림", "text": "새 주문이 들어왔습니다." },
    "android": { "priority": "high" }
  }
}
```

### 예제 스크립트 (firebase-admin)

Firebase 콘솔 → 프로젝트 설정 → 서비스 계정에서 비공개 키를 받아 `server/service-account.json` 으로 저장한 뒤:

```sh
cd server
npm install
GOOGLE_APPLICATION_CREDENTIALS=./service-account.json node send.js "<기기 토큰>" "새 주문이 들어왔습니다." "주문 알림"
```

## 참고

- 앱 첫 화면에 "배터리 최적화 해제 필요" 카드가 보이면 눌러서 해제하세요. 화면 꺼짐/도즈 상태에서도 바로 읽게 됩니다.
- 삼성: 설정 → 배터리 → 백그라운드 사용 한도 → "절전 예외 앱"에 추가. 샤오미: 앱 정보 → 배터리 절약 → "제한 없음", 자동 시작 허용.
- 최근 앱 목록에서 **강제 종료(설정 → 강제 중지)** 한 앱은 Android 정책상 다시 실행할 때까지 FCM 을 받지 못합니다. (최근 앱에서 밀어서 닫는 것은 괜찮습니다.)
- 무음/진동 모드에서도 미디어 볼륨으로 읽히는 기기가 대부분이며, 음량은 미디어(어시스턴트) 볼륨을 따릅니다.
- 언어는 BCP-47 태그(`ko-KR`, `en-US` 등)로 입력합니다. 지원하지 않는 언어면 엔진 기본 언어로 읽습니다.
- 소리는 `USAGE_ASSISTANT` 오디오 스트림으로 재생됩니다.
