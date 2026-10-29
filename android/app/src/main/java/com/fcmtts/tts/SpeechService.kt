package com.fcmtts.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * 백그라운드/종료 상태에서 받은 메시지를 끝까지 읽기 위한 포그라운드 서비스.
 * 읽는 동안 프로세스와 CPU를 유지하고, 다른 오디오는 잠시 볼륨을 낮춘다. 대기열이 비면 스스로 종료한다.
 */
class SpeechService : Service() {

  private val handler = Handler(Looper.getMainLooper())
  private var remaining = 0
  private var wakeLock: PowerManager.WakeLock? = null
  private var focusRequest: AudioFocusRequest? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startInForeground()
    val text = intent?.getStringExtra(EXTRA_TEXT)
    if (text.isNullOrBlank()) {
      if (remaining == 0) finish()
      return START_NOT_STICKY
    }

    if (remaining++ == 0) {
      acquireWakeLock()
      requestAudioFocus()
    }
    TtsSpeaker.speak(this, text) { handler.post { onUtteranceFinished() } }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    releaseResources()
    super.onDestroy()
  }

  private fun onUtteranceFinished() {
    if (--remaining <= 0) finish()
  }

  private fun finish() {
    remaining = 0
    releaseResources()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun startInForeground() {
    val notification = buildNotification()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
          NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } else {
      startForeground(NOTIFICATION_ID, notification)
    }
  }

  private fun buildNotification(): Notification {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      manager.createNotificationChannel(
          NotificationChannel(CHANNEL_ID, "음성 읽기", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
          })
    }
    val launch = packageManager.getLaunchIntentForPackage(packageName)
    val contentIntent =
        launch?.let {
          PendingIntent.getActivity(
              this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    val builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          Notification.Builder(this, CHANNEL_ID)
        } else {
          @Suppress("DEPRECATION") Notification.Builder(this)
        }
    return builder
        .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
        .setContentTitle("메시지를 읽는 중")
        .setContentIntent(contentIntent)
        .setOngoing(true)
        .build()
  }

  private fun acquireWakeLock() {
    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
    wakeLock =
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FcmTts:speech").apply {
          setReferenceCounted(false)
          acquire(WAKE_LOCK_TIMEOUT_MS)
        }
  }

  private fun requestAudioFocus() {
    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val request =
          AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
              .setAudioAttributes(TtsSpeaker.audioAttributes)
              .build()
      focusRequest = request
      am.requestAudioFocus(request)
    } else {
      @Suppress("DEPRECATION")
      am.requestAudioFocus(
          null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }
  }

  private fun releaseResources() {
    wakeLock?.let { if (it.isHeld) it.release() }
    wakeLock = null
    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      focusRequest?.let { am.abandonAudioFocusRequest(it) }
      focusRequest = null
    } else {
      @Suppress("DEPRECATION") am.abandonAudioFocus(null)
    }
  }

  companion object {
    private const val TAG = "SpeechService"
    private const val CHANNEL_ID = "speech"
    private const val NOTIFICATION_ID = 1001
    private const val EXTRA_TEXT = "text"
    private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

    /**
     * 포그라운드 서비스로 읽기를 시작한다. 높은 우선순위 FCM 메시지를 처리하는 중에는 백그라운드에서도
     * 시작이 허용되지만, 일반 우선순위로 강등된 경우 등에는 실패할 수 있으므로 false를 반환한다.
     */
    fun start(context: Context, text: String): Boolean {
      val intent = Intent(context, SpeechService::class.java).putExtra(EXTRA_TEXT, text)
      return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
        true
      } catch (e: Exception) {
        // Android 12+: ForegroundServiceStartNotAllowedException (IllegalStateException 하위)
        Log.w(TAG, "포그라운드 서비스를 시작하지 못했습니다: ${e.message}")
        false
      }
    }
  }
}
