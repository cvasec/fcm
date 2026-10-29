package com.fcmtts.tts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.firebase.messaging.FirebaseMessaging
import java.lang.ref.WeakReference
import org.json.JSONObject

/** JS에서 FCM 토큰, TTS 설정, 수신 기록에 접근하기 위한 네이티브 모듈. */
class FcmTtsModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

  init {
    current = WeakReference(reactContext)
  }

  override fun getName() = NAME

  @ReactMethod
  fun getToken(promise: Promise) {
    try {
      FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (task.isSuccessful) {
          val token = task.result
          TtsSettings.setToken(reactContext, token)
          promise.resolve(token)
        } else {
          promise.reject("E_TOKEN", task.exception?.message ?: "토큰을 가져오지 못했습니다", task.exception)
        }
      }
    } catch (e: IllegalStateException) {
      // google-services.json 없이 빌드하면 FirebaseApp이 초기화되지 않는다.
      promise.reject("E_FIREBASE", "Firebase가 초기화되지 않았습니다. google-services.json을 확인하세요.", e)
    }
  }

  @ReactMethod
  fun speak(text: String, promise: Promise) {
    TtsSpeaker.speak(reactContext, text) { promise.resolve(null) }
  }

  @ReactMethod
  fun stop() {
    TtsSpeaker.stop()
  }

  @ReactMethod
  fun getSettings(promise: Promise) {
    promise.resolve(settingsMap())
  }

  @ReactMethod
  fun setSettings(map: ReadableMap, promise: Promise) {
    TtsSettings.update(
        reactContext,
        enabled = if (map.hasKey("enabled")) map.getBoolean("enabled") else null,
        rate = if (map.hasKey("rate")) map.getDouble("rate").toFloat() else null,
        pitch = if (map.hasKey("pitch")) map.getDouble("pitch").toFloat() else null,
        language = if (map.hasKey("language")) map.getString("language") else null,
    )
    promise.resolve(settingsMap())
  }

  @ReactMethod
  fun getHistory(promise: Promise) {
    val history = TtsSettings.getHistory(reactContext)
    val array = Arguments.createArray()
    for (i in 0 until history.length()) array.pushMap(toMap(history.getJSONObject(i)))
    promise.resolve(array)
  }

  @ReactMethod
  fun clearHistory(promise: Promise) {
    TtsSettings.clearHistory(reactContext)
    promise.resolve(null)
  }

  @ReactMethod
  fun isIgnoringBatteryOptimizations(promise: Promise) {
    val pm = reactContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    promise.resolve(pm.isIgnoringBatteryOptimizations(reactContext.packageName))
  }

  /** 배터리 최적화 예외를 요청하는 시스템 대화상자를 연다. 도즈/앱 대기 상태에서도 바로 읽기 위함. */
  @ReactMethod
  fun requestIgnoreBatteryOptimizations() {
    val intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${reactContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      reactContext.startActivity(intent)
    } catch (e: Exception) {
      reactContext.startActivity(
          Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }

  // NativeEventEmitter 요구 사항
  @ReactMethod fun addListener(eventName: String) {}

  @ReactMethod fun removeListeners(count: Double) {}

  private fun settingsMap(): WritableMap {
    val s = TtsSettings.get(reactContext)
    return Arguments.createMap().apply {
      putBoolean("enabled", s.enabled)
      putDouble("rate", s.rate.toDouble())
      putDouble("pitch", s.pitch.toDouble())
      putString("language", s.language)
    }
  }

  companion object {
    const val NAME = "FcmTts"
    const val EVENT_MESSAGE = "FcmTtsMessage"
    const val EVENT_TOKEN = "FcmTtsToken"

    private var current: WeakReference<ReactApplicationContext>? = null

    private fun toMap(json: JSONObject): WritableMap =
        Arguments.createMap().apply {
          putString("id", json.optString("id"))
          putString("text", json.optString("text"))
          putString("title", json.optString("title"))
          putDouble("receivedAt", json.optLong("receivedAt").toDouble())
        }

    private fun emit(event: String, payload: Any) {
      val ctx = current?.get() ?: return
      if (!ctx.hasActiveReactInstance()) return
      ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
          .emit(event, payload)
    }

    fun emitMessage(entry: JSONObject) = emit(EVENT_MESSAGE, toMap(entry))

    fun emitToken(token: String) = emit(EVENT_TOKEN, token)
  }
}
