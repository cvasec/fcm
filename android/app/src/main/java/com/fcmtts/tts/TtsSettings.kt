package com.fcmtts.tts

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences 기반 설정 + 수신 기록 저장소. 앱이 꺼져 있어도 서비스에서 접근 가능. */
object TtsSettings {
  private const val PREFS = "fcm_tts"
  private const val KEY_ENABLED = "enabled"
  private const val KEY_RATE = "rate"
  private const val KEY_PITCH = "pitch"
  private const val KEY_LANGUAGE = "language"
  private const val KEY_TOKEN = "token"
  private const val KEY_HISTORY = "history"
  private const val MAX_HISTORY = 50

  data class Values(val enabled: Boolean, val rate: Float, val pitch: Float, val language: String)

  private fun prefs(context: Context) =
      context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun get(context: Context): Values {
    val p = prefs(context)
    return Values(
        enabled = p.getBoolean(KEY_ENABLED, true),
        rate = p.getFloat(KEY_RATE, 1.0f),
        pitch = p.getFloat(KEY_PITCH, 1.0f),
        language = p.getString(KEY_LANGUAGE, "ko-KR") ?: "ko-KR",
    )
  }

  fun update(
      context: Context,
      enabled: Boolean? = null,
      rate: Float? = null,
      pitch: Float? = null,
      language: String? = null,
  ) {
    prefs(context).edit().apply {
      enabled?.let { putBoolean(KEY_ENABLED, it) }
      rate?.let { putFloat(KEY_RATE, it.coerceIn(0.1f, 4.0f)) }
      pitch?.let { putFloat(KEY_PITCH, it.coerceIn(0.1f, 4.0f)) }
      language?.let { putString(KEY_LANGUAGE, it) }
      apply()
    }
  }

  fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

  fun setToken(context: Context, token: String) {
    prefs(context).edit().putString(KEY_TOKEN, token).apply()
  }

  @Synchronized
  fun addHistory(context: Context, entry: JSONObject) {
    val list = getHistory(context)
    val next = JSONArray().put(entry)
    for (i in 0 until minOf(list.length(), MAX_HISTORY - 1)) next.put(list.get(i))
    prefs(context).edit().putString(KEY_HISTORY, next.toString()).apply()
  }

  fun getHistory(context: Context): JSONArray =
      try {
        JSONArray(prefs(context).getString(KEY_HISTORY, "[]"))
      } catch (e: Exception) {
        JSONArray()
      }

  fun clearHistory(context: Context) {
    prefs(context).edit().remove(KEY_HISTORY).apply()
  }
}
