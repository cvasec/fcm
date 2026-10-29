package com.fcmtts.tts

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * FCM 메시지를 받아 TTS로 읽는다.
 * - data 메시지: 앱 상태와 관계없이 onMessageReceived 로 들어온다.
 * - notification 메시지: 백그라운드에서는 SDK가 알림만 띄우고 onMessageReceived 를 부르지 않으므로
 *   handleIntent 에서 직접 가로채 읽는다.
 */
class TtsMessagingService : FirebaseMessagingService() {

  override fun handleIntent(intent: Intent) {
    super.handleIntent(intent)
    if (intent.action != ACTION_RECEIVE) return
    val extras = intent.extras ?: return
    val message = RemoteMessage(extras)
    // notification 메시지는 앱 상태와 관계없이 여기서 처리하고 onMessageReceived 에서는 건너뛴다.
    if (message.notification != null) handle(message)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    if (message.notification == null) handle(message)
  }

  override fun onNewToken(token: String) {
    TtsSettings.setToken(this, token)
    FcmTtsModule.emitToken(token)
  }

  private fun handle(message: RemoteMessage) {
    if (!markHandled(message.messageId)) return
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

    if (!SpeechService.start(this, text)) {
      // 포그라운드 서비스를 띄울 수 없으면 FCM 이 허용하는 시간 안에서 여기서 직접 읽는다.
      val latch = CountDownLatch(1)
      TtsSpeaker.speak(this, text) { latch.countDown() }
      latch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS)
    }
  }

  private fun extractText(message: RemoteMessage): String? {
    val data = message.data
    return data["text"] ?: data["tts"] ?: data["message"] ?: data["body"]
        ?: message.notification?.body
  }

  companion object {
    private const val TAG = "TtsMessagingService"
    private const val ACTION_RECEIVE = "com.google.android.c2dm.intent.RECEIVE"
    private const val MAX_WAIT_SECONDS = 18L
    private const val MAX_RECENT_IDS = 20

    private val recentIds = ArrayDeque<String>()

    /** 같은 메시지가 중복 전달되어 두 번 읽는 것을 막는다. 처음 보는 메시지면 true. */
    @Synchronized
    private fun markHandled(id: String?): Boolean {
      if (id == null) return true
      if (recentIds.contains(id)) return false
      recentIds.addLast(id)
      if (recentIds.size > MAX_RECENT_IDS) recentIds.removeFirst()
      return true
    }
  }
}
