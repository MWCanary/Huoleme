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
    private var toneGenerator: ToneGenerator? = null
    private var sosJob: Job? = null
    private var alertJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun triggerVibration(context: Context, isAlert: Boolean = false) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(if (isAlert) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (isAlert) {
                        // 20 times intermittent strong vibration
                        val timings = LongArray(41) { index ->
                            if (index == 0) 0L
                            else if (index % 2 == 1) 400L // 400ms vibration
                            else 200L // 200ms pause
                        }
                        val amplitudes = IntArray(41) { index ->
                            if (index == 0) 0
                            else if (index % 2 == 1) 255 // max amplitude
                            else 0
                        }
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), audioAttributes)
                        
                        playAlertSound(context)
                    } else {
                        // Clean, crisp tactile click
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
                        
                        playAlertSound(context)
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
        alertJob?.cancel()
        alertJob = scope.launch {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                }
                for (i in 0 until 20) {
                    if (!isActive) break
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
                    delay(600) // 400ms tone + 200ms pause
                }
                toneGenerator?.release()
                toneGenerator = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun triggerMorseSOSVibration(context: Context, repeat: Boolean = false) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()

                // Morse SOS timings: S (...), O (---), S (...)
                val timings = longArrayOf(
                    0, 
                    150, 150, 150, 150, 150, 300,  // S (...): 150ms on, 150ms off, 150ms on... 300ms gap
                    450, 150, 450, 150, 450, 300,  // O (---): 450ms on, 150ms off, 450ms on... 300ms gap
                    150, 150, 150, 150, 150, 1000  // S (...): 150ms on, 150ms off... 1000ms loop gap
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
                
                playMorseSOSSound(context, repeat)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun playMorseSOSSound(context: Context, repeat: Boolean) {
        sosJob?.cancel()
        sosJob = scope.launch {
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                
                if (toneGenerator == null) {
                    toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                }
                
                do {
                    // S (...)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(450) // 150 + 300 gap
                    
                    if (!isActive) break
                    
                    // O (---)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450); delay(600)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450); delay(600)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450); delay(750) // 450 + 300 gap
                    
                    if (!isActive) break
                    
                    // S (...)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(300)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 150); delay(1150) // 150 + 1000 loop gap
                    
                } while (repeat && isActive)
                
                if (!repeat) {
                    toneGenerator?.release()
                    toneGenerator = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopVibration(context: Context) {
        try {
            sosJob?.cancel()
            alertJob?.cancel()
            toneGenerator?.release()
            toneGenerator = null
            
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
