package com.fcmtts.tts

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * FCM 메시지를 받아 바로 TTS로 읽는다. 앱이 백그라운드이거나 종료된 상태에서도 동작하려면
 * 서버에서 notification 없이 data 메시지로 보내야 한다 (README 참고).
 */
class TtsMessagingService : FirebaseMessagingService() {

  override fun onMessageReceived(message: RemoteMessage) {
    val text = extractText(message)
    if (text.isNullOrBlank()) {
      Log.w(TAG, "읽을 텍스트가 없는 메시지: ${message.data}")
      return
    }

    val entry =
        JSONObject()
            .put("id", message.messageId ?: System.currentTimeMillis().toString())
            .put("text", text)
            .put("title", message.data["title"] ?: message.notification?.title ?: "")
            .put("receivedAt", System.currentTimeMillis())
    TtsSettings.addHistory(this, entry)
    FcmTtsModule.emitMessage(entry)

    if (!TtsSettings.get(this).enabled) return

    // onMessageReceived는 백그라운드 스레드에서 실행되며 반환되면 프로세스가 정리될 수 있으므로,
    // 읽기가 끝날 때까지 (FCM이 허용하는 시간 안에서) 기다린다.
    val latch = CountDownLatch(1)
    TtsSpeaker.speak(this, text) { latch.countDown() }
    latch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS)
  }

  override fun onNewToken(token: String) {
    TtsSettings.setToken(this, token)
    FcmTtsModule.emitToken(token)
  }

  private fun extractText(message: RemoteMessage): String? {
    val data = message.data
    return data["text"] ?: data["tts"] ?: data["message"] ?: data["body"]
        ?: message.notification?.body
  }

  companion object {
    private const val TAG = "TtsMessagingService"
    private const val MAX_WAIT_SECONDS = 18L
  }
}
