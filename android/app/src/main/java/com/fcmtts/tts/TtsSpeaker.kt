package com.fcmtts.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 폰 내장 TextToSpeech 엔진 래퍼. 초기화가 끝나기 전에 들어온 문장은 대기열에 쌓았다가 읽는다.
 * 앱 프로세스 전체에서 하나의 인스턴스를 공유한다.
 */
object TtsSpeaker {
  private const val TAG = "TtsSpeaker"

  private var tts: TextToSpeech? = null
  private var ready = false
  private val pending = mutableListOf<Pair<String, String>>()
  private val callbacks = ConcurrentHashMap<String, () -> Unit>()

  /** 문장을 읽는다. [onDone]은 읽기가 끝나거나 실패하면 호출된다. */
  @Synchronized
  fun speak(context: Context, text: String, onDone: (() -> Unit)? = null) {
    val id = UUID.randomUUID().toString()
    if (onDone != null) callbacks[id] = onDone
    if (ready) {
      speakNow(context, text, id)
    } else {
      pending.add(text to id)
      ensureInit(context)
    }
  }

  @Synchronized
  fun stop() {
    tts?.stop()
    callbacks.values.forEach { it() }
    callbacks.clear()
  }

  @Synchronized
  private fun ensureInit(context: Context) {
    if (tts != null) return
    val appContext = context.applicationContext
    tts =
        TextToSpeech(appContext) { status ->
          synchronized(this) {
            if (status == TextToSpeech.SUCCESS) {
              ready = true
              pending.forEach { (text, id) -> speakNow(appContext, text, id) }
            } else {
              Log.e(TAG, "TTS 초기화 실패: $status")
              pending.forEach { (_, id) -> finish(id) }
              tts?.shutdown()
              tts = null
            }
            pending.clear()
          }
        }
    tts?.setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build())
    tts?.setOnUtteranceProgressListener(
        object : UtteranceProgressListener() {
          override fun onStart(utteranceId: String) {}

          override fun onDone(utteranceId: String) = finish(utteranceId)

          @Deprecated("Deprecated in Java")
          override fun onError(utteranceId: String) = finish(utteranceId)

          override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId)

          override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId)
        })
  }

  private fun speakNow(context: Context, text: String, id: String) {
    val engine = tts ?: return finish(id)
    val settings = TtsSettings.get(context)
    val locale = Locale.forLanguageTag(settings.language)
    if (engine.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) {
      engine.setLanguage(locale)
    } else {
      Log.w(TAG, "지원하지 않는 언어: ${settings.language}, 기본 언어로 읽습니다")
    }
    engine.setSpeechRate(settings.rate)
    engine.setPitch(settings.pitch)
    val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id) }
    val result = engine.speak(text, TextToSpeech.QUEUE_ADD, params, id)
    if (result != TextToSpeech.SUCCESS) finish(id)
  }

  private fun finish(id: String) {
    callbacks.remove(id)?.invoke()
  }
}
