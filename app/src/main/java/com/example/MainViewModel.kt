package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CheckInRecord
import com.example.data.CheckInRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val application: Application,
    private val repository: CheckInRepository
) : AndroidViewModel(application) {

    // All database records
    val allRecords: StateFlow<List<CheckInRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Whether the user has checked in today
    val isCheckedInToday: StateFlow<Boolean> = allRecords.map { records ->
        val todayStr = getTodayString()
        records.any { it.date == todayStr }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // Current continuous streak in days
    val currentStreak: StateFlow<Int> = allRecords.map { records ->
        calculateStreak(records)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // Last check-in date/time formatted string
    val lastCheckInTimeStr: StateFlow<String> = allRecords.map { records ->
        val latest = records.firstOrNull()
        if (latest != null) {
            val date = Date(latest.timestamp)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(date)
        } else {
            application.getString(R.string.no_check_in_history)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    // Perform check-in for today with optional location
    fun checkInToday(location: String = "无位置") {
        viewModelScope.launch {
            val todayStr = getTodayString()
            val record = CheckInRecord(
                date = todayStr,
                timestamp = System.currentTimeMillis(),
                location = location
            )
            repository.insertRecord(record)
        }
    }

    // Cancel check-in for today (allows continuous testing of check-in flow)
    fun undoTodayCheckIn() {
        viewModelScope.launch {
            val todayStr = getTodayString()
            repository.deleteRecordByDate(todayStr)
        }
    }

    // Inject check-ins for the last N days to test/simulate streak accumulation
    fun injectPastCheckIns(daysCount: Int) {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            
            // Insert records starting from yesterday and going backwards
            for (i in 1..daysCount) {
                calendar.time = Date()
                calendar.add(Calendar.DATE, -i)
                val dateStr = sdf.format(calendar.time)
                
                val record = CheckInRecord(
                    date = dateStr,
                    timestamp = calendar.timeInMillis,
                    location = "无位置"
                )
                repository.insertRecord(record)
            }
        }
    }

    // Clear all check-ins to reset application state
    fun clearAllRecords() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    // Instantly trigger a missed check-in "你还活得好好的吗吗/你还活这么？" notification for testing
    fun triggerTestNotification(onResult: (String) -> Unit = {}) {
        val channelId = "survival_check_channel"
        val context = application.applicationContext
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
        val btnLabel = "我都活的好好的 (快速打卡)"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.notification_title) + " (测试主演练)")
            .setContentText(context.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setVibrate(pattern)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_add, btnLabel, checkInPendingIntent)

        notificationManager.notify(1001, builder.build())
        VibrationHelper.triggerVibration(context, isAlert = true)
        onResult("SUCCESS")
    }

    private fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private val AUTHORITATIVE_STATE_KEYS = setOf(
        "dev_account_logged_in",
        "dev_account_user",
        "dev_account_code",
        "dev_account_code_hash",
        "is_ad_free",
        "ad_free_key"
    )

    private fun isAuthoritativeStateKey(key: String): Boolean {
        val lower = key.lowercase()
        return AUTHORITATIVE_STATE_KEYS.contains(lower) ||
                lower.contains("dev_account") ||
                lower.contains("ad_free") ||
                lower.contains("privilege") ||
                lower.contains("auth_token") ||
                lower.contains("license")
    }

    // Export all data to JSON (excluding any authoritative or privilege state)
    fun exportAllDataToJson(prefs: android.content.SharedPreferences): String {
        val rootObject = org.json.JSONObject()
        
        // Export records
        val records = allRecords.value
        val jsonArray = org.json.JSONArray()
        records.forEach { record ->
            val jsonObject = org.json.JSONObject()
            jsonObject.put("date", record.date)
            jsonObject.put("timestamp", record.timestamp)
            jsonObject.put("note", record.note)
            jsonObject.put("location", record.location)
            jsonArray.put(jsonObject)
        }
        rootObject.put("records", jsonArray)
        
        // Export SharedPreferences: Strictly export only safe general preferences
        val prefsObject = org.json.JSONObject()
        prefs.all.forEach { (key, value) ->
            if (value != null && !isAuthoritativeStateKey(key)) {
                prefsObject.put(key, value)
            }
        }
        rootObject.put("preferences", prefsObject)
        
        return rootObject.toString()
    }

    // Import records from JSON (silently discarding any authoritative state fields)
    fun importRecordsFromJson(jsonString: String, prefs: android.content.SharedPreferences, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonStrTrimmed = jsonString.trim()
                var recordsArray: org.json.JSONArray? = null
                var newRecordsCount = 0
                var prefsImported = false
                
                if (jsonStrTrimmed.startsWith("{")) {
                    val rootObj = org.json.JSONObject(jsonStrTrimmed)
                    if (rootObj.has("records")) {
                        recordsArray = rootObj.optJSONArray("records")
                    }
                    if (rootObj.has("preferences")) {
                        val prefsObj = rootObj.getJSONObject("preferences")
                        val editor = prefs.edit()
                        val keys = prefsObj.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            // Silently discard all authoritative / privilege / login / ad-free fields
                            if (isAuthoritativeStateKey(key)) {
                                continue
                            }
                            val value = prefsObj.get(key)
                            when (value) {
                                is String -> editor.putString(key, value)
                                is Int -> editor.putInt(key, value)
                                is Boolean -> editor.putBoolean(key, value)
                                is Float -> editor.putFloat(key, value)
                                is Long -> editor.putLong(key, value)
                            }
                        }
                        editor.apply()
                        prefsImported = true
                    }
                } else if (jsonStrTrimmed.startsWith("[")) {
                    recordsArray = org.json.JSONArray(jsonStrTrimmed)
                } else {
                    onResult(false, "无效的文件格式 (Invalid File Format)")
                    return@launch
                }
                
                if (recordsArray != null) {
                    for (i in 0 until recordsArray.length()) {
                        val jsonObject = recordsArray.getJSONObject(i)
                        val date = jsonObject.optString("date", "")
                        val timestamp = jsonObject.optLong("timestamp", 0L)
                        val note = jsonObject.optString("note", "")
                        val location = jsonObject.optString("location", "无位置")
                        if (date.isNotEmpty() && timestamp > 0L) {
                            val existing = repository.getRecordByDate(date)
                            if (existing == null) {
                                val record = com.example.data.CheckInRecord(date = date, timestamp = timestamp, note = note, location = location)
                                repository.insertRecord(record)
                                newRecordsCount++
                            }
                        }
                    }
                }
                val msg = "导入成功 (Import Success): 导入了 $newRecordsCount 条新记录。" + if (prefsImported) " 并恢复了设置。" else ""
                onResult(true, msg)
            } catch (e: Exception) {
                onResult(false, "导入失败 (Import Failed): ${e.message}")
            }
        }
    }
    // Calculate current running streak
    private fun calculateStreak(records: List<CheckInRecord>): Int {
        if (records.isEmpty()) return 0
        
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val sortedDates = records.map { it.date }.distinct().sortedDescending()
        if (sortedDates.isEmpty()) return 0

        val todayCalendar = Calendar.getInstance()
        val todayStr = sdf.format(todayCalendar.time)
        
        todayCalendar.add(Calendar.DATE, -1)
        val yesterdayStr = sdf.format(todayCalendar.time)

        // Streak is active only if latest check-in is today or yesterday
        val latestDateStr = sortedDates.first()
        if (latestDateStr != todayStr && latestDateStr != yesterdayStr) {
            return 0
        }

        var currentStreak = 0
        val checkCalendar = Calendar.getInstance()
        
        val latestDate = try {
            sdf.parse(latestDateStr)
        } catch (e: Exception) {
            null
        } ?: return 0
        checkCalendar.time = latestDate
        
        var expectedDateStr = latestDateStr
        
        for (dateStr in sortedDates) {
            if (dateStr == expectedDateStr) {
                currentStreak++
                checkCalendar.add(Calendar.DATE, -1)
                expectedDateStr = sdf.format(checkCalendar.time)
            } else {
                break
            }
        }
        
        return currentStreak
    }

    // Factory Class
    class Factory(
        private val application: Application,
        private val repository: CheckInRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
