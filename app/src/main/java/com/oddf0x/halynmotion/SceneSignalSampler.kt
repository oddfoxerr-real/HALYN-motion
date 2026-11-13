package com.oddf0x.halynmotion

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.BatteryManager
import android.os.SystemClock
import java.util.Calendar

/**
 * Privacy-first device signals for HALYN's procedural scenes.
 *
 * No microphone, screen capture, playback capture, accessibility service, or
 * network calls. Playback is a boolean activity signal, not audio amplitudes.
 * Notifications are opt-in via Android's notification-listener access and
 * only the last event timestamp is retained.
 */
data class SceneSignals(
    val music: Float = 0f,
    val charging: Float = 0f,
    val battery: Float = 0.5f,
    val notification: Float = 0f,
    val dayPhase: Float = 0.5f,
    val silent: Float = 0f
)

class SceneSignalSampler(context: Context, private val settings: SharedPreferences) {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var lastSampleMs = -1L
    private var music = 0f
    private var charging = 0f
    private var battery = 0.5f
    private var silent = 0f
    private var dayPhase = 0.5f

    fun sample(): SceneSignals {
        val now = SystemClock.elapsedRealtime()
        // Slow-changing signals are sampled once per second, not every frame.
        if (lastSampleMs < 0 || now < lastSampleMs || now - lastSampleMs >= 1000L) {
            lastSampleMs = now
            music = try {
                if (audio.isMusicActive) 1f else 0f
            } catch (_: RuntimeException) {
                0f
            }
            silent = try {
                if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) 0f else 1f
            } catch (_: RuntimeException) {
                0f
            }
            val status = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (status != null) {
                val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    battery = (level.toFloat() / scale).coerceIn(0f, 1f)
                }
                charging = when (status.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                    BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> 1f
                    else -> 0f
                }
            }
            val calendar = Calendar.getInstance()
            val seconds = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                calendar.get(Calendar.MINUTE) * 60 + calendar.get(Calendar.SECOND)
            dayPhase = seconds / 86400f
        }
        val eventMs = settings.getLong("last_notification_signal_ms", -1L)
        val age = now - eventMs
        val notification = if (eventMs > 0L && age in 0L..2200L) {
            (1f - age / 2200f).coerceIn(0f, 1f)
        } else {
            0f
        }
        return SceneSignals(music, charging, battery, notification, dayPhase, silent)
    }
}
