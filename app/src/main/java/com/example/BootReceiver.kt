package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device booted. Rescheduling lifeline jobs...")

            // 1. Reschedule Daily Alarm
            val hour = getNotificationHour(context)
            val minute = getNotificationMinute(context)
            AlarmScheduler.scheduleDailyCheck(context, hour, minute)

            // 2. Start Foreground Service if enabled
            if (getForegroundServiceEnabled(context)) {
                try {
                    LifelineForegroundService.start(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
