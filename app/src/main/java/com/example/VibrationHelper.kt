package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import kotlinx.coroutines.*

object VibrationHelper {
    private val toneLock = Any()
    private var toneGenerator: ToneGenerator? = null
    private var sosJob: Job? = null
    private var alertJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun acquireToneGenerator(): ToneGenerator? {
        synchronized(toneLock) {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            }
            return toneGenerator
        }
    }

    private fun releaseToneGenerator() {
        synchronized(toneLock) {
            try {
                toneGenerator?.release()
            } catch (_: Exception) {
            } finally {
                toneGenerator = null
            }
        }
    }

    private fun startToneSafe(toneType: Int, durationMs: Int) {
        synchronized(toneLock) {
            try {
                toneGenerator?.startTone(toneType, durationMs)
            } catch (_: Exception) {}
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        val appContext = context.applicationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun triggerVibration(context: Context, isAlert: Boolean = false) {
        val appContext = context.applicationContext
        try {
            val vibrator = getVibrator(appContext)
            if (vibrator != null && vibrator.hasVibrator()) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(if (isAlert) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isAlert) {
                        val timings = LongArray(41) { index ->
                            if (index == 0) 0L
                            else if (index % 2 == 1) 400L
                            else 200L
                        }
                        val amplitudes = IntArray(41) { index ->
                            if (index == 0) 0
                            else if (index % 2 == 1) 255
                            else 0
                        }
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), audioAttributes)
                        playAlertSound(appContext)
                    } else {
                        val timings = longArrayOf(0, 80, 80, 80)
                        val amplitudes = intArrayOf(0, 180, 0, 240)
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), audioAttributes)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    if (isAlert) {
                        val timings = LongArray(41) { index ->
                            if (index == 0) 0L
                            else if (index % 2 == 1) 400L
                            else 200L
                        }
                        vibrator.vibrate(timings, -1)
                        playAlertSound(appContext)
                    } else {
                        vibrator.vibrate(longArrayOf(0, 80, 80, 80), -1)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playAlertSound(context: Context) {
        val appContext = context.applicationContext
        alertJob?.cancel()
        alertJob = scope.launch {
            try {
                try {
                    val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                        0
                    )
                } catch (_: SecurityException) {
                } catch (_: Exception) {}

                acquireToneGenerator()
                for (i in 0 until 20) {
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    delay(600)
                }
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                releaseToneGenerator()
            }
        }
    }

    fun triggerMorseSOSVibration(context: Context, repeat: Boolean = false) {
        val appContext = context.applicationContext
        try {
            val vibrator = getVibrator(appContext)
            if (vibrator != null && vibrator.hasVibrator()) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                val timings = longArrayOf(
                    0,
                    150, 150, 150, 150, 150, 300,
                    450, 150, 450, 150, 450, 300,
                    150, 150, 150, 150, 150, 1000
                )
                val amplitudes = intArrayOf(
                    0,
                    255, 0, 255, 0, 255, 0,
                    255, 0, 255, 0, 255, 0,
                    255, 0, 255, 0, 255, 0
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, if (repeat) 0 else -1)
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(effect, audioAttributes)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(timings, if (repeat) 0 else -1)
                }

                playMorseSOSSound(appContext, repeat)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playMorseSOSSound(context: Context, repeat: Boolean) {
        val appContext = context.applicationContext
        sosJob?.cancel()
        sosJob = scope.launch {
            try {
                try {
                    val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    audioManager?.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                        0
                    )
                } catch (_: SecurityException) {
                } catch (_: Exception) {}

                acquireToneGenerator()

                do {
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(450)

                    if (!isActive) break

                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450); delay(600)
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450); delay(600)
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450); delay(750)

                    if (!isActive) break

                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    if (!isActive) break
                    startToneSafe(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(1150)
                } while (repeat && isActive)
            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                releaseToneGenerator()
            }
        }
    }

    fun stopVibration(context: Context) {
        try {
            sosJob?.cancel()
            sosJob = null
            alertJob?.cancel()
            alertJob = null
            releaseToneGenerator()
            getVibrator(context)?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
