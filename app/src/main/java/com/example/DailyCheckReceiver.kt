package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "com.example.ACTION_QUICK_CHECK_IN") {
            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.checkInDao()
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    val quickLocation = LocationHelper.getLocationString(context)

                    val record = com.example.data.CheckInRecord(
                        date = today,
                        timestamp = java.lang.System.currentTimeMillis(),
                        location = quickLocation
                    )
                    dao.insertRecord(record)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(1001)
                    
                    VibrationHelper.stopVibration(context)

                    launch(Dispatchers.Main) {
                        val toastMsg = "快速打卡成功！生存记录已更新。"
                        android.widget.Toast.makeText(context, toastMsg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (action == "com.example.ACTION_APP_KILLED") {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "killed_notification_channel_vibrate"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "被划掉通知",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "当应用在后台被划掉时发出的通知"
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    vibrationPattern = longArrayOf(0, 200, 100, 200)
                }
                manager.createNotificationChannel(channel)
            }
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingLaunchIntent = PendingIntent.getActivity(
                context, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("不要划掉我😭！")
                .setContentText("应用已从后台被划掉")
                .setContentIntent(pendingLaunchIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(longArrayOf(0, 200, 100, 200))
                .setAutoCancel(true)
                .build()
            manager.notify(2002, notification)
            return
        }
        
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.checkInDao()
                
                val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
                val targetHour = prefs.getInt("notification_hour", 20)
                val targetMinute = prefs.getInt("notification_minute", 0)
                
                val now = java.util.Calendar.getInstance()
                val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = now.get(java.util.Calendar.MINUTE)
                
                // If it triggered way too early (e.g. before target time), it might be a delayed alarm from yesterday
                // We will check yesterday's record instead, or if it's past target time, we check today's record.
                val dateToCheck = if (hour < targetHour || (hour == targetHour && minute < targetMinute)) {
                    // Triggered early today, meaning it's a delayed alarm from yesterday
                    val yesterday = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DATE, -1) }
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterday.time)
                } else {
                    // Triggered on or after target time today
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                }
                
                val checkInRecord = dao.getRecordByDate(dateToCheck)
                
                if (checkInRecord == null) {
                    // Send notification if they missed checking in
                    sendSurvivalNotification(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendSurvivalNotification(context: Context) {
        val channelId = "survival_check_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intermittent vibration: 20 times (20 active periods, 20 silent pauses = 40 entries, plus initial delay = 41 slots)
        val pattern = LongArray(41) { index ->
            if (index == 0) {
                0L
            } else if (index % 2 == 1) {
                400L // 400ms vibration
            } else {
                300L // 300ms pause
            }
        }

        // Create notification channel for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = pattern
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Set action intent to open MainActivity
        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val checkInIntent = Intent(context, DailyCheckReceiver::class.java).apply {
            action = "com.example.ACTION_QUICK_CHECK_IN"
        }
        val checkInPendingIntent = PendingIntent.getBroadcast(
            context,
            12345,
            checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
        val btnLabel = "快速打卡"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setVibrate(pattern)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_add, btnLabel, checkInPendingIntent)

        notificationManager.notify(1001, builder.build())
        VibrationHelper.triggerVibration(context, isAlert = true)
    }
}
