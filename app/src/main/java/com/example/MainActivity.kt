package com.example

import android.Manifest
import android.content.Intent
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.browser.customtabs.CustomTabsIntent
import android.net.Uri
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.data.AppDatabase
import com.example.data.CheckInRecord
import com.example.data.CheckInRepository
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.provider.Settings
import android.os.PowerManager

class MainActivity : ComponentActivity() {

    private var sensorManager: android.hardware.SensorManager? = null
    private var accelerometer: android.hardware.Sensor? = null
    private var shakeListener: android.hardware.SensorEventListener? = null
    private var canTriggerAd = true
    var onRemoteCheckInCallback: (() -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                onRemoteCheckInCallback?.let { callback ->
                    callback()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    fun openWebUrl(url: String) {
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(this, "无法打开网页", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
        accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        
        shakeListener = object : android.hardware.SensorEventListener {
            private var lastX = 0f
            private var lastY = 0f
            private var lastZ = 0f
            private var isFirst = true

            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (!canTriggerAd) return
                event?.let {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]
                    
                    if (isFirst) {
                        lastX = x
                        lastY = y
                        lastZ = z
                        isFirst = false
                        return
                    }

                    val deltaX = Math.abs(x - lastX)
                    val deltaY = Math.abs(y - lastY)
                    val deltaZ = Math.abs(z - lastZ)
                    
                    lastX = x
                    lastY = y
                    lastZ = z
                    
                    // Trigger on slight movement (1.8 m/s^2 difference in any axis)
                    if (deltaX > 1.8f || deltaY > 1.8f || deltaZ > 1.8f) { 
                        canTriggerAd = false
                        sensorManager?.unregisterListener(this)
                        try {
                            val urls = listOf(
                                "https://qr.alipay.com/fkx12551xqkxb4jowrbhq40",
                                "https://pan.huang1111.cn/s/A6NvBiB",
                                "https://pan.sysri.cn/home?path=cloudreve%3A%2F%2FXA73HB%40share"
                            )
                            openWebUrl(urls.random())
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
 
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        
        if (!getIsAdFree(this)) {
            sensorManager?.registerListener(shakeListener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
            
            lifecycleScope.launch {
                delay(10000)
                canTriggerAd = false
                shakeListener?.let { sensorManager?.unregisterListener(it) }
            }
        }

        // Ensure all legacy Dev account credentials and flags are purged
        clearDevAccount(this)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            var currentLanguage by remember { mutableStateOf(getSavedLanguage(context)) }
            
            // Manage and persist selected themes live
            var currentThemeMode by remember { mutableStateOf(getSavedThemeMode(context)) }
            var currentCustomColor by remember { mutableStateOf(getSavedCustomColor(context)) }
            var currentDarkModePref by remember { mutableStateOf(getSavedDarkMode(context)) }
            
            var currentFontSizeLevel by remember { mutableStateOf(getFontSizeLevel(context)) }
            
            val isDark = when (currentDarkModePref) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            
            val fontMultiplier = when (currentFontSizeLevel) {
                0 -> 0.85f
                1 -> 0.92f
                2 -> 1.0f
                3 -> 1.15f
                4 -> 1.30f
                else -> 1.0f
            }
            
            val currentDensity = androidx.compose.ui.platform.LocalDensity.current
            val newDensity = androidx.compose.ui.unit.Density(density = currentDensity.density, fontScale = fontMultiplier)
            
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides newDensity) {
                MyApplicationTheme(
                    themeMode = currentThemeMode,
                    darkTheme = isDark,
                    customColor = currentCustomColor
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainScreen(
                            currentThemeMode = currentThemeMode,
                            currentCustomColor = currentCustomColor,
                            currentDarkModePref = currentDarkModePref,
                            activeLanguage = currentLanguage,
                            currentFontSizeLevel = currentFontSizeLevel,
                            onThemeChange = { newTheme ->
                                currentThemeMode = newTheme
                                saveThemeMode(context, newTheme)
                            },
                            onCustomColorChange = { newColor ->
                                currentCustomColor = newColor
                                saveCustomColor(context, newColor)
                            },
                            onDarkModeChange = { newMode ->
                                currentDarkModePref = newMode
                                saveDarkMode(context, newMode)
                            },
                            onLanguageChange = { newLang ->
                                currentLanguage = newLang
                                saveSavedLanguage(context, newLang)
                            },
                            onFontSizeLevelChange = { newLevel ->
                                currentFontSizeLevel = newLevel
                                saveFontSizeLevel(context, newLevel)
                            }
                        )
                    }
                }
            }
        }
    }
}

// Persist preferences
fun getSavedAppVersion(context: Context): String {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getString("app_version_saved", "") ?: ""
}
fun saveAppVersion(context: Context, version: String) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("app_version_saved", version).apply()
}
fun getSavedThemeMode(context: Context): AppThemeMode {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    val name = prefs.getString("theme_mode", AppThemeMode.DYNAMIC.name) ?: AppThemeMode.DYNAMIC.name
    return try {
        AppThemeMode.valueOf(name)
    } catch (e: Exception) {
        AppThemeMode.DYNAMIC
    }
}

fun saveThemeMode(context: Context, mode: AppThemeMode) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("theme_mode", mode.name).apply()
}

fun getSavedCustomColor(context: Context): Color {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    val argb = prefs.getInt("custom_theme_color", 0xFF6200EE.toInt())
    return Color(argb)
}

fun saveCustomColor(context: Context, color: Color) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putInt("custom_theme_color", color.toArgb()).apply()
}

fun getFirstLaunchDone(context: Context): Boolean {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("first_launch_done_v1_1", false)
}

fun saveFirstLaunchDone(context: Context) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("first_launch_done_v1_1", true).apply()
}

fun clearFirstLaunchDone(context: Context) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("first_launch_done_v1_1", false).apply()
}

fun getSavedDarkMode(context: Context): String {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getString("dark_mode", "SYSTEM") ?: "SYSTEM"
}

fun saveDarkMode(context: Context, mode: String) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("dark_mode", mode).apply()
}

fun getSavedLanguage(context: Context): String {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getString("app_language", "zh_CN") ?: "zh_CN"
}

fun saveSavedLanguage(context: Context, lang: String) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("app_language", lang).apply()
}

fun getFontSizeLevel(context: Context): Int {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getInt("font_size_level", 2) // 0 to 4, default 2 (normal)
}

fun saveFontSizeLevel(context: Context, level: Int) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putInt("font_size_level", level).apply()
}

fun getBirthDate(context: Context): String {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getString("user_birth_date", "") ?: ""
}

fun saveBirthDate(context: Context, dateStr: String) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("user_birth_date", dateStr).apply()
}

fun getNotificationHour(context: Context): Int {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getInt("notification_hour", 22)
}

fun getNotificationMinute(context: Context): Int {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getInt("notification_minute", 0)
}

fun saveNotificationTime(context: Context, hour: Int, minute: Int) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit()
        .putInt("notification_hour", hour)
        .putInt("notification_minute", minute)
        .apply()
}

fun getForegroundServiceEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("foreground_service_enabled", false)
}

fun saveForegroundServiceEnabled(context: Context, enabled: Boolean) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("foreground_service_enabled", enabled).apply()
}

fun getIsAdFree(context: Context): Boolean {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_ad_free", false)
}

fun setIsAdFree(context: Context, isAdFree: Boolean) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("is_ad_free", isAdFree).apply()
}

fun isDevChannel(): Boolean {
    return BuildConfig.VERSION_NAME.contains("Dev", ignoreCase = true)
}

fun getDevAccount(context: Context): String = ""

fun isDevLoggedIn(context: Context): Boolean = false

fun clearDevAccount(context: Context) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit()
        .remove("dev_account_user")
        .remove("dev_account_code")
        .remove("dev_account_code_hash")
        .remove("dev_account_logged_in")
        .apply()
}

fun hashSha256(input: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}


fun getIgnoreBatteryWarning(context: Context): Boolean {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("ignore_battery_warning", false)
}

fun setIgnoreBatteryWarning(context: Context, ignore: Boolean) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("ignore_battery_warning", ignore).apply()
}

fun getIsAnimationEnabled(context: Context): Boolean {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    return prefs.getBoolean("is_animation_enabled", true)
}

fun setIsAnimationEnabled(context: Context, isEnabled: Boolean) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("is_animation_enabled", isEnabled).apply()
}



val VALID_AD_FREE_KEYS = setOf(
    "X9A4B8K2", "M3N7V1Q5", "P8W2J6T4", "R5C9H3Y1", "D2G8F4L6",
    "Z1S7E9N3", "B6A2M5X8", "Y4V1T7K9", "Q3P8J2W5", "L7R4C6H9",
    "K2Z9S5E1", "F8D3G1M7", "N6B4A9X2", "T1Y8V3P7", "J5Q2W9L4",
    "H7R1C8Z3", "E4K9S2F6", "M1D5G7N8", "X3B6A2Y4", "V9T4P1J7",
    "W2Q8L5H3", "Z7R3C1E9", "S4K6F2M8", "D9G1N4X7", "B3A5Y8V2",
    "T6P9J4W1", "L8H2Z5R7", "C3E6S9K1", "F4M2D8G5", "N7X1B3A9",
    "Y2V6T8P4", "J9W1Q5L7", "H3Z8R2C6", "E1S4K9F3", "M7D2G5N1",
    "X4B8A3Y6", "V1T5P9J2", "W7Q4L8H1", "Z2R6C3E5", "S9K1F7M4",
    "D3G8N2X5", "B7A1Y4V9", "T2P6J8W3", "L5H9Z1R4", "C8E2S6K9",
    "F1M7D4G2", "N9X3B5A8", "Y6V2T1P7", "J4W8Q3L5", "H9Z4R7C1",
    "E2S8K3F9", "M4D1G6N7", "X8B5A9Y2", "V3T7P4J1", "W6Q2L9H5",
    "Z5R9C4E8", "S1K7F3M2", "D6G4N8X1", "B9A2Y5V7", "T4P1J6W8",
    "L2H5Z9R3", "C7E1S8K4", "F5M9D2G6", "N1X4B7A3", "Y8V5T2P9",
    "J3W7Q1L4", "H6Z2R8C5", "E9S3K1F7", "M2D8G4N9", "X7B1A6Y3",
    "V5T9P2J8", "W4Q6L1H7", "Z8R4C9E2", "S3K5F1M6", "D7G2N5X9",
    "B2A8Y3V4", "T9P4J7W1", "L6H1Z3R8", "C4E7S2K5", "F9M3D6G1",
    "N5X8B2A7", "Y1V4T9P3", "J7W2Q8L6", "H4Z9R1C3", "E5S6K8F2",
    "M8D5G3N1", "X2B9A4Y7", "V6T1P5J9", "W3Q7L2H4", "Z1R5C8E6",
    "S7K2F9M4", "D4G9N1X8", "B5A3Y6V2", "T8P2J4W9", "L1H6Z7R5",
    "C9E4S1K3", "F2M8D5G7", "N4X6B9A1", "Y7V3T8P2", "J1W5Q4L9"
)

fun calculateLivedAge(birthDateStr: String): Triple<Int, Int, Int>? {
    if (birthDateStr.isEmpty()) return null
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
        val birthDate = sdf.parse(birthDateStr) ?: return null
        
        val startCal = java.util.Calendar.getInstance().apply { time = birthDate }
        val endCal = java.util.Calendar.getInstance()
        
        if (endCal.before(startCal)) return Triple(0, 0, 0)
        
        var years = endCal.get(java.util.Calendar.YEAR) - startCal.get(java.util.Calendar.YEAR)
        var months = endCal.get(java.util.Calendar.MONTH) - startCal.get(java.util.Calendar.MONTH)
        var days = endCal.get(java.util.Calendar.DAY_OF_MONTH) - startCal.get(java.util.Calendar.DAY_OF_MONTH)
        
        if (months < 0 || (months == 0 && days < 0)) {
            years--
            months += 12
        }
        
        if (days < 0) {
            months--
            if (months < 0) {
                years--
                months += 12
            }
            
            val tempCal = java.util.Calendar.getInstance().apply { time = startCal.time }
            tempCal.add(java.util.Calendar.YEAR, years)
            tempCal.add(java.util.Calendar.MONTH, months)
            
            var remainingDays = 0
            while (tempCal.get(java.util.Calendar.YEAR) != endCal.get(java.util.Calendar.YEAR) ||
                   tempCal.get(java.util.Calendar.MONTH) != endCal.get(java.util.Calendar.MONTH) ||
                   tempCal.get(java.util.Calendar.DAY_OF_MONTH) != endCal.get(java.util.Calendar.DAY_OF_MONTH)) {
                tempCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                remainingDays++
                if (remainingDays > 40) {
                    remainingDays = 0 // safeguard fallback
                    break
                }
            }
            days = remainingDays
        }
        
        return Triple(years, months, days)
    } catch (e: Exception) {
        return null
    }
}

fun wrapContextWithLocale(context: Context, languageTag: String): Context {
    val locale = Locale.SIMPLIFIED_CHINESE
    Locale.setDefault(locale)
    val resources = context.resources
    val configuration = resources.configuration
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    } else {
        @Suppress("DEPRECATION")
        configuration.locale = locale
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return context
    }
}

fun getLocalizedText(lang: String, key: String): String {
    return when (key) {
        "lang_title" -> "应用语言"
        "birth_title" -> "出生日期"
        "lived_prefix" -> "您已在世界上生存了："
        "years" -> "年"
        "months" -> "月"
        "days" -> "天"
        "birth_hint" -> "请设置您的出生日期"
        "enter_birth" -> "首次开启：请设置您的出生日期"
        "birth_placeholder" -> "年份 (如1995)"
        "birth_month_placeholder" -> "月份 (1-12)"
        "birth_day_placeholder" -> "日份 (1-31)"
        "birth_date_invalid" -> "请输入正确的日期格式！"
        "current_language" -> "简体中文"
        "choose_lang" -> "语言设置"
        "agree_terms" -> "点击同意即表示你已经同意了我们的条款。为了支持应用的持续开发，此版本包含了部分广告内容。"
        "start_btn" -> "开始"
        "seconds" -> "秒"
        "minutes" -> "分"
        "hours" -> "时"
        "enter_birth_desc" -> "系统将为您实时计量在这个世界生存的每分每秒，感知生命呼吸痕迹，从而活在当下、珍惜生命。"
        "edit_birth_btn" -> "修改生日 "
        "privacy_protect" -> "提示：所有内容皆为本地安全沙箱加密，不提供云端同步，保障绝对的隐私安宁。"
        "app_title" -> "活了么"
        "app_subtitle" -> "三十六计"
        "enable_animation" -> "打卡按钮动效"
        "enable_animation_setting_title" -> "动画与视觉特效"
        "enable_animation_setting_desc" -> "一键开启或关闭应用内的所有过渡动画与物理呼吸动效"
        "font_size_setting_title" -> "字体大小"
        "font_size_setting_desc" -> "调整应用内的字体显示大小"
        "font_size_0" -> "极小"
        "font_size_1" -> "小"
        "font_size_2" -> "标准"
        "font_size_3" -> "大"
        "font_size_4" -> "特大"
        "lifeline_shield_safe" -> "生命线守护：安全倒计时"
        "lifeline_shield_danger" -> "❗ 生命警戒：生存时限倒数"
        "countdown_safe" -> "距下一周期生存签署截止还剩："
        "countdown_danger" -> "距强震报警及发信求助拉响还剩："
        "testament_title" -> "生存遗言："
        "testament_default" -> "请协助联系紧急联络人，我已失联！此信息由「活了么」生命体征守护终端自动托管。"
        "testament_hint" -> "💡 提示：若上述倒计时归零生存信号仍未发布，您的遗言自救信号将被向该设备推送通知。您可以通过“系统设置”随时编写专属求救信。"
        "consecutive_days_title" -> "连续确认存活天数"
        "consecutive_day_unit" -> "天"
        "last_verify_title" -> "最后生命核实时间"
        "no_records" -> "尚无记录"
        "notification_warning" -> "提醒：当前系统未授予通知权限。如果未来遗忘打卡，“你还活着么？”紧急提醒通知将无法弹出警示。请点击授权。"
        "theme_system" -> "系统壁纸"
        "theme_blue" -> "蓝"
        "theme_red" -> "红"
        "theme_green" -> "绿"
        "theme_orange" -> "橙"
        "theme_purple" -> "紫"
        "theme_gold" -> "金"
        "theme_section_title" -> "视觉色彩方案"
        "theme_preference_title" -> "深色偏好机制："
        "theme_pref_system" -> "跟随系统"
        "theme_pref_light" -> "浅色模式"
        "theme_pref_dark" -> "深色模式(推荐)"
        "daily_deadline_title" -> "日常打卡期限"
        "start_journey" -> "开始使用"
        "daily_deadline_desc" -> "后台生命线自愈守护自动关联 OS AlarmManager 闹钟：在设定的时间 (%02d:%02d) 执行日常安全判定。若超出 24 小时未触发主屏幕“我都活的好好的”签到动作，将唤醒高优先级强震及物理声效关心推送。"
        "notif_route_label" -> "当前系统通知确认线路："
        "active_connected" -> "已激活 (Connected)"
        "inactive" -> "未授权 (Inactive)"
        "grant_permission_btn" -> "授予并激活“你还活着吗”警告权限"
        "testament_archive_title" -> "生存遗言与临终求救信档案"
        "testament_archive_desc" -> "在此预置当安全倒计时结束且您依然失联时的托管信息。当判定您状态“失去连接”（未打卡超过24小时且失联）时，此托管信息将长期保存在生命线自愈面板上："
        "testament_placeholder" -> "请输入紧急救助联系方式及存活申明，例如：『如果我失联超过24小时，请立即帮我联系120并核实我的安全！』"
        "about_app_title" -> "关于活了么"
        "version_label" -> "版本"
        "check_updates_btn" -> "下载更新"
        "checking_text" -> "检查中..."
        "has_new_version_text" -> "选择链接下载新版本"
        "already_latest_text" -> "已是最新版"
        "latest_toast_msg" -> "当前已是最新版\n暂无更新内容"
        "select_download_link" -> "请选择下载链接更新："
        "cancel_btn" -> "取消"
        "dev_verify_title" -> "验证身份"
        "dev_verify_desc" -> "请输入密码以进入开发者选项"
        "dev_password_placeholder" -> "请输入密码"
        "dev_password_error" -> "密码错误，请重试"
        "unlock_btn" -> "解锁"
        "developer_options_title" -> "开发者选项"
        "dev_back" -> "返回参数中心"
        "dev_card_title" -> "安全线路沙盒演习调试"
        "dev_card_desc" -> "系统自愈安全线路可靠性检测机制："
        "dev_notify_btn" -> "立马演练警告推送"
        "dev_inject_btn" -> "历史回填7天"
        "dev_undo_title" -> "撤销今日生存打卡"
        "dev_undo_desc" -> "一键撤销今天已打卡的信号，重置防线生存守护状态。"
        "dev_undo_btn" -> "撤销打卡"
        "dev_clear_title" -> "生命轨迹数据清零"
        "dev_clear_desc" -> "清空后打卡历史将直接销毁且连续天数重设。"
        "dev_clear_btn" -> "重设档案库"
        "dev_lock_title" -> "锁定模式"
        "dev_lock_desc" -> "随时通过锁定功能阻止任何非授权诊断操作。"
        "dev_lock_btn" -> "锁定并返回"
        "debug_click_hint" -> "只需再点击 %d 次即可开启调试接口"
        "nav_home" -> "主页"
        "nav_history" -> "打卡记录"
        "nav_settings" -> "设置"
        "status_safe" -> "恭喜，您又多活了一天"
        "status_safe_desc" -> "恭喜，您又多活了一天。"
        "status_pending" -> "生命体征待确认"
        "status_pending_desc" -> "请打卡以确认您还活着"
        "btn_alive" -> "我还活着！"
        "btn_alive_done" -> "已打卡"
        "alive" -> "正常活着! (Alive)"
        "sign" -> "请打卡以确认您还活着"
        "expect_info" -> "期待今日信号状态更新，点击应答："
        "undo_text" -> "⚠ 调试模式：一键撤销今天打卡并重置状态"
        "app_version" -> "版本 V" + BuildConfig.VERSION_NAME
        "author_desc" -> "本系统致力于将孤独与安宁中的现实关怀融于移动应用。不依靠任何云服务器，百分百本地极速执行生存应答及防漏打卡报警机制。愿生命如常，健康永远伴您前行。"
        else -> ""
    }
}

fun getSavedTestament(context: Context, lang: String = "zh_CN"): String {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    val defaultText = getLocalizedText(lang, "testament_default")
    return prefs.getString("survival_testament", defaultText) ?: defaultText
}

fun saveSavedTestament(context: Context, testament: String) {
    val prefs = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
    prefs.edit().putString("survival_testament", testament.trim()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    currentThemeMode: AppThemeMode,
    currentCustomColor: Color,
    currentDarkModePref: String,
    activeLanguage: String,
    currentFontSizeLevel: Int,
    onThemeChange: (AppThemeMode) -> Unit,
    onCustomColorChange: (Color) -> Unit,
    onDarkModeChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onFontSizeLevelChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application

    // Database and repository setup
    val db = remember { AppDatabase.getDatabase(app) }
    val repository = remember { CheckInRepository(db.checkInDao()) }
    
    // Viewmodel instantiation
    val model: MainViewModel = viewModel(
        factory = MainViewModel.Factory(app, repository)
    )

    // Flow observations
    val allRecords by model.allRecords.collectAsStateWithLifecycle()
    val isCheckedInToday by model.isCheckedInToday.collectAsStateWithLifecycle()
    val currentStreak by model.currentStreak.collectAsStateWithLifecycle()
    val lastCheckInTime by model.lastCheckInTimeStr.collectAsStateWithLifecycle()

    // Page navigation state (Dashboard, Logs, Settings)
    var currentPage by remember { mutableStateOf("dashboard") }
    
    // Survival message/testament state
    var survivalTestament by remember { mutableStateOf(getSavedTestament(context)) }

    // Birth Date state
    var userBirthDate by remember { mutableStateOf(getBirthDate(context)) }

    // System info notifications
    var infoBarNotification by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Helper functions for Location
    fun checkLocationPermission(ctx: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun getCurrentLocationString(ctx: Context): String {
        if (!checkLocationPermission(ctx)) {
            return "无位置"
        }
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return "无位置"
            
            var bestLoc: android.location.Location? = null
            val providers = listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER
            )
            for (p in providers) {
                try {
                    if (lm.isProviderEnabled(p)) {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null) {
                            if (bestLoc == null || loc.time > bestLoc.time || (loc.accuracy > 0 && loc.accuracy < bestLoc.accuracy)) {
                                bestLoc = loc
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            if (bestLoc == null) {
                for (p in lm.allProviders) {
                    try {
                        val loc = lm.getLastKnownLocation(p)
                        if (loc != null && (bestLoc == null || loc.time > bestLoc.time)) {
                            bestLoc = loc
                        }
                    } catch (e: Exception) {}
                }
            }
            if (bestLoc != null) {
                val lat = String.format(Locale.US, "%.4f", bestLoc.latitude)
                val lon = String.format(Locale.US, "%.4f", bestLoc.longitude)
                "经度: $lon, 纬度: $lat"
            } else {
                "无位置"
            }
        } catch (e: Exception) {
            "无位置"
        }
    }

    DisposableEffect(Unit) {
        val activity = context as? MainActivity
        activity?.onRemoteCheckInCallback = {
            if (currentPage == "dashboard") {
                VibrationHelper.triggerVibration(context, isAlert = false)
                val loc = LocationHelper.getLocationString(context)
                model.checkInToday(loc)
                infoBarNotification = "生存判定更新" to "打卡成功！您目前状态为 [正常存活中]，连续守护时间已递增。"
            }
        }
        onDispose {
            activity?.onRemoteCheckInCallback = null
        }
    }

    // Global animations enabled state
    var isAnimationEnabledState by remember { mutableStateOf(getIsAnimationEnabled(context)) }

    // First launch state tracking
    var firstLaunchDone by remember { mutableStateOf(getFirstLaunchDone(context)) }
    
    var showUpgradeDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val savedVer = getSavedAppVersion(context)
        val currentVer = "1.3.6 Dev Gen 2"
        if (firstLaunchDone && savedVer != currentVer) {
            showUpgradeDialog = true
        }
        if (savedVer != currentVer) {
            saveAppVersion(context, currentVer)
            clearDevAccount(context)
        }
    }


    // Notification permission and battery optimization state checks
    var hasNotificationPermissionState by remember {
        mutableStateOf(true)
    }
    var isIgnoringBatteryOptimizationsState by remember {
        mutableStateOf(true)
    }
    var isIgnoreBatteryWarning by remember {
        mutableStateOf(getIgnoreBatteryWarning(context))
    }

    // Export / Import JSON Launchers
    val scope = rememberCoroutineScope()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val prefsToExport = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
                    val jsonStr = model.exportAllDataToJson(prefsToExport)
 
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(jsonStr.toByteArray())
                    }
                    Toast.makeText(context, "导出成功 (Export Success)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "导出失败 (Export Failed)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonStr = context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }
                    if (jsonStr != null) {
                        val prefsToImport = context.getSharedPreferences("huolileme_settings", Context.MODE_PRIVATE)
                        model.importRecordsFromJson(jsonStr, prefsToImport) { success, msg ->
 
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "解析失败 (Parse Failed)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Check helper functions
    fun checkNotificationPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun checkBatteryOptimizationExemption(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } else {
            true
        }
    }

    // Lifecycle observer to recheck permissions on Resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotificationPermissionState = checkNotificationPermission(context)
                isIgnoringBatteryOptimizationsState = checkBatteryOptimizationExemption(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var hasNotificationPermission by remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        } else {
            mutableStateOf(true)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        hasNotificationPermissionState = isGranted
        if (isGranted) {
            infoBarNotification = "守护已激活" to "每日生存确认提醒已经就绪，正在后台保护..."
        } else {
            infoBarNotification = "权限不可用" to "由于未授予通知权限，系统将无法在您漏打卡时发出警告！请点击此栏授予权限。"
        }
    }

    val activityRecognitionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle physical activity permission result
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            LocationHelper.refreshLocation(context)
        }
    }

    // Auto check/schedule daily receiver and permissions on launch
    LaunchedEffect(Unit) {
        if (!checkNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Request Physical Activity (ACTIVITY_RECOGNITION) permission on Android 10+ (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val activityGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (!activityGranted) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        // Request Location permissions (ACCESS_FINE_LOCATION & ACCESS_COARSE_LOCATION)
        if (!LocationHelper.checkLocationPermission(context)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            LocationHelper.refreshLocation(context)
        }
        val customHour = getNotificationHour(context)
        val customMinute = getNotificationMinute(context)
        AlarmScheduler.scheduleDailyCheck(context, customHour, customMinute)
        
        if (getForegroundServiceEnabled(context)) {
            LifelineForegroundService.start(context)
        }
    }

    // Developer Options State
    var debugClickCount by remember { mutableStateOf(0) }
    var isEnteringDeveloperPassword by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf(false) }
    var isDeveloperPageUnlocked by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(56.dp)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                debugClickCount++
                                if (debugClickCount >= 7) {
                                    debugClickCount = 0
                                    isEnteringDeveloperPassword = true
                                }
                            }
                    )
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = getLocalizedText(activeLanguage, "app_title"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = getLocalizedText(activeLanguage, "app_subtitle"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                ),
                actions = {
                    Box(modifier = Modifier.size(56.dp))
                }
            )
        },
 
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentPage == "dashboard",
                    onClick = { currentPage = "dashboard" },
                    icon = { Icon(Icons.Filled.Home, contentDescription = getLocalizedText(activeLanguage, "nav_home")) },
                    label = { Text(getLocalizedText(activeLanguage, "nav_home")) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = currentPage == "logs",
                    onClick = { currentPage = "logs" },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = getLocalizedText(activeLanguage, "nav_history")) },
                    label = { Text(getLocalizedText(activeLanguage, "nav_history")) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    alwaysShowLabel = false
                )
                NavigationBarItem(
                    selected = currentPage == "settings",
                    onClick = { currentPage = "settings" },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = getLocalizedText(activeLanguage, "nav_settings")) },
                    label = { Text(getLocalizedText(activeLanguage, "nav_settings")) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    alwaysShowLabel = false
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Back navigation handling for developer sections
                if (isEnteringDeveloperPassword) {
                    androidx.activity.compose.BackHandler {
                        isEnteringDeveloperPassword = false
                        enteredPassword = ""
                        passwordError = false
                    }
                }
                if (isDeveloperPageUnlocked && currentPage == "settings") {
                    androidx.activity.compose.BackHandler {
                        isDeveloperPageUnlocked = false
                    }
                }

                // Content of the active page
                if (isEnteringDeveloperPassword) {
                    DeveloperPasswordSubPage(
                        activeLanguage = activeLanguage,
                        enteredPassword = enteredPassword,
                        onPasswordChange = {
                            enteredPassword = it
                            passwordError = false
                        },
                        passwordError = passwordError,
                        onUnlock = {
                            // Secure password SHA-256 comparison of "50909800"
                            val isCorrect = try {
                                val digest = java.security.MessageDigest.getInstance("SHA-256")
                                val hashBytes = digest.digest(enteredPassword.toByteArray(Charsets.UTF_8))
                                val hex = hashBytes.joinToString("") { String.format("%02x", it) }
                                hex == "5b69fc44c983e8806747ad338c5dc235145873250b49b82faffc68595b310576"
                            } catch (e: Exception) {
                                false
                            }

                            if (isCorrect) {
                                isDeveloperPageUnlocked = true
                                currentPage = "settings"
                                isEnteringDeveloperPassword = false
                                enteredPassword = ""
                                passwordError = false
                            } else {
                                passwordError = true
                            }
                        },
                        onCancel = {
                            isEnteringDeveloperPassword = false
                            enteredPassword = ""
                            passwordError = false
                        }
                    )
                } else {
                    val isAnimationEnabled = isAnimationEnabledState
                    androidx.compose.animation.Crossfade(
                        targetState = currentPage, 
                        label = "page_transition",
                        animationSpec = if (isAnimationEnabled) androidx.compose.animation.core.tween() else androidx.compose.animation.core.snap()
                    ) { page ->
                        when (page) {
                        "dashboard" -> {
                        DashboardPage(
                            isCheckedInToday = isCheckedInToday,
                            currentStreak = currentStreak,
                            lastCheckInTime = lastCheckInTime,
                            hasPermission = hasNotificationPermission,
                            infoBarNotification = infoBarNotification,
                            onInfoBarClose = { infoBarNotification = null },
                            onCheckIn = {
                                VibrationHelper.triggerVibration(context, isAlert = false)
                                val loc = LocationHelper.getLocationString(context)
                                model.checkInToday(loc)
                                infoBarNotification = "生存判定更新" to "打卡成功！您目前状态为 [正常存活中]，连续守护时间已递增。"
                            },
                            onRequestPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            survivalTestament = survivalTestament,
                            activeLanguage = activeLanguage,
                            birthDate = userBirthDate,
                            isAnimationEnabled = isAnimationEnabledState
                        )
                    }
                    "logs" -> {
                        HistoryLogsPage(
                            allRecords = allRecords
                        )
                    }
                    "settings" -> {
                        SettingsPage(
                            currentTheme = currentThemeMode,
                            currentCustomColor = currentCustomColor,
                            currentDarkModePref = currentDarkModePref,
                            hasPermission = hasNotificationPermission,
                            isAnimationEnabled = isAnimationEnabledState,
                            currentFontSizeLevel = currentFontSizeLevel,
                            onAnimationEnabledChanged = { enabled ->
                                isAnimationEnabledState = enabled
                                setIsAnimationEnabled(context, enabled)
                            },
                            onFontSizeLevelSelected = onFontSizeLevelChange,
                            onThemeSelected = onThemeChange,
                            onCustomColorSelected = onCustomColorChange,
                            onDarkModeSelected = onDarkModeChange,
                            onTriggerNotification = {
                                model.triggerTestNotification {
                                    infoBarNotification = "守护测试成功" to "已经成功在本地渠道发出『活得好好的吗？』的高强度物理生存强震与推送，请下拉通知栏查看！"
                                }
                                Toast.makeText(context, "已成功启动本地强通知物理演练...", Toast.LENGTH_SHORT).show()
                            },
                            onInjectData = {
                                model.injectPastCheckIns(7)
                                infoBarNotification = "数据注入成功" to "成功回填了近 7 天的生存确认记录。"
                            },
                            onClearRecords = {
                                model.clearAllRecords()
                                infoBarNotification = "清除历史" to "已成功重置了所有的生存档案历史。"
                            },
                            onRevokeTermsSilently = {
                                model.clearAllRecords()
                                userBirthDate = ""
                                saveBirthDate(context, "")
                                clearFirstLaunchDone(context)
                            },
                            onUndoCheckIn = {
                                VibrationHelper.triggerVibration(context, isAlert = false)
                                model.undoTodayCheckIn()
                                infoBarNotification = "撤销打卡" to "已成功重置为您今日尚未生存打卡的状态。"
                            },
                            onRequestPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            survivalTestament = survivalTestament,
                            onTestamentChanged = {
                                survivalTestament = it
                                saveSavedTestament(context, it)
                            },
                            activeLanguage = activeLanguage,
                            onLanguageChange = onLanguageChange,
                            birthDate = userBirthDate,
                            onBirthDateChanged = {
                                userBirthDate = it
                                saveBirthDate(context, it)
                            },
                            isShowingDeveloperSubPage = isDeveloperPageUnlocked,
                            onBackFromDeveloper = { isDeveloperPageUnlocked = false },
                            onExportJson = { exportLauncher.launch("application/json") },
                            onImportJson = { importLauncher.launch(arrayOf("application/json")) }
                        )
                    }
                }
                }
                }

                if (showUpgradeDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showUpgradeDialog = false },
                        title = {
                            Text("版本 1.3.6 Dev Gen 2")
                        },
                        text = {
                            Text("已升级至 1.3.6 Dev Gen 2 版本。\n严禁降级提示：本版本已完成底层生存档案升级，禁止降级至旧版，否则会导致档案损坏。")
                        },
                        confirmButton = {
                            TextButton(onClick = { showUpgradeDialog = false }) {
                                Text(getLocalizedText(activeLanguage, "cancel_btn"))
                            }
                        }
                    )
                }

                // First-Launch Welcome Tutorial Overlay
                if (!firstLaunchDone) {
                    OnboardingWizardOverlay(
                        currentLanguage = activeLanguage,
                        onLanguageChange = onLanguageChange,
                        onBirthDateSaved = {
                            userBirthDate = it
                            saveBirthDate(context, it)
                        },
                        onImportJson = { importLauncher.launch(arrayOf("application/json")) },
                        onDismiss = {
                            saveFirstLaunchDone(context)
                            firstLaunchDone = true
                        }
                    )
                }

                // Full screen permission and battery checks overlay
                if (firstLaunchDone && (!hasNotificationPermissionState || (!isIgnoringBatteryOptimizationsState && !isIgnoreBatteryWarning))) {
                    SystemAccessCheckOverlay(
                        activeLanguage = activeLanguage,
                        hasNotification = hasNotificationPermissionState,
                        hasBatteryExemption = isIgnoringBatteryOptimizationsState,
                        onJumpNotification = { requestNotificationPermissionSetting(context) },
                        onJumpBattery = { requestIgnoreBatteryOptimizations(context) },
                        onRefresh = {
                            hasNotificationPermissionState = checkNotificationPermission(context)
                            isIgnoringBatteryOptimizationsState = checkBatteryOptimizationExemption(context)
                        },
                        onIgnoreBattery = {
                            setIgnoreBatteryWarning(context, true)
                            isIgnoreBatteryWarning = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperPasswordSubPage(
    activeLanguage: String,
    enteredPassword: String,
    onPasswordChange: (String) -> Unit,
    passwordError: Boolean,
    onUnlock: () -> Unit,
    onCancel: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top row with Back icon button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCancel,
                modifier = Modifier.testTag("developer_password_back_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = getLocalizedText(activeLanguage, "cancel_btn"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = getLocalizedText(activeLanguage, "dev_verify_title"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = getLocalizedText(activeLanguage, "dev_verify_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = getLocalizedText(activeLanguage, "dev_verify_desc"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = enteredPassword,
                    onValueChange = onPasswordChange,
                    label = { Text(getLocalizedText(activeLanguage, "dev_password_placeholder")) },
                    singleLine = true,
                    isError = passwordError,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { onUnlock() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("developer_password_input")
                )

                if (passwordError) {
                    Text(
                        text = getLocalizedText(activeLanguage, "dev_password_error"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(getLocalizedText(activeLanguage, "cancel_btn"))
                    }

                    Button(
                        onClick = onUnlock,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("developer_password_confirm"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(getLocalizedText(activeLanguage, "unlock_btn"))
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingWizardOverlay(
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    onBirthDateSaved: (String) -> Unit,
    onImportJson: () -> Unit = {},
    onDismiss: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) { }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val maxH = maxHeight
        val context = LocalContext.current
        var onboardingYear by remember { mutableStateOf("2000") }
        var onboardingMonth by remember { mutableStateOf("01") }
        var onboardingDay by remember { mutableStateOf("01") }
        var isAgreedToTerms by remember { mutableStateOf(false) }
        
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .heightIn(max = maxH - 48.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "生命守护服务首次引导",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = getLocalizedText(currentLanguage, "app_version") + " — " + getLocalizedText(currentLanguage, "app_subtitle"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                // Prohibition of Downgrade Warning
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "⚠️ 严禁降级提示",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "本版本 (1.3.6 Dev Gen 2) 已升级底层生存档案与数据库结构，禁止降级至旧版，降级将导致生存档案损坏或应用无法运行。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Birthday Choice
                Text(
                    text = getLocalizedText(currentLanguage, "birth_title"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = getLocalizedText(currentLanguage, "enter_birth_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = onboardingYear,
                        onValueChange = { onboardingYear = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("2000") },
                        label = { Text(getLocalizedText(currentLanguage, "years")) },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = onboardingMonth,
                        onValueChange = { onboardingMonth = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("01") },
                        label = { Text(getLocalizedText(currentLanguage, "months")) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = onboardingDay,
                        onValueChange = { onboardingDay = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("01") },
                        label = { Text(getLocalizedText(currentLanguage, "days")) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                // Check and show live age calculation or error
                val validatedAge = remember(onboardingYear, onboardingMonth, onboardingDay) {
                    try {
                        val y = onboardingYear.toIntOrNull() ?: -1
                        val m = onboardingMonth.toIntOrNull() ?: -1
                        val d = onboardingDay.toIntOrNull() ?: -1
                        if (y in 1900..2050 && m in 1..12 && d in 1..31) {
                            val formattedDate = "${y}-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false }
                            val parsedDate = sdf.parse(formattedDate)
                            if (parsedDate != null && parsedDate.time > System.currentTimeMillis()) {
                                null
                            } else {
                                calculateLivedAge(formattedDate)
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (validatedAge != null) {
                    val (y, m, d) = validatedAge
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "✨ " + getLocalizedText(currentLanguage, "lived_prefix") + " $y " + getLocalizedText(currentLanguage, "years") + " $m " + getLocalizedText(currentLanguage, "months") + " $d " + getLocalizedText(currentLanguage, "days"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Text(
                        text = getLocalizedText(currentLanguage, "birth_date_invalid"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val serviceAgreementText = "【用户服务协议与免责条款】\n" +
                                     "1. 本地安全：本程序定位为单机绿色生命安全提醒工具。所有打卡日志、判定轨迹皆储存在手机物理沙盒中，无任何云端服务器，保障隐私100%安全。\n" +
                                     "2. 应急提醒机制：守护功能依靠系统的 AlarmManager 定时对签到状态进行离线校验。若由于失联缺失确认，应用会触发本地高强度物理振动与自定义音画关心警讯。\n" +
                                     "3. 特殊免责事项：由于系统后台策略差异，本程序无法保证 100% 唤醒。请尽量将本应用加入电池优化白名单，或开启后台保活。\n" +
                                     "4. 禁止降级：本版本为 1.3.6 Dev Gen 2，底层生存档案已完成升级迁移，严禁回滚降级至旧版本，降级将导致生存档案损坏或应用无法运行。"
                        Text(
                            text = serviceAgreementText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                OutlinedButton(
                    onClick = onImportJson,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "首次开启：导入打卡数据 (.json)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isAgreedToTerms = !isAgreedToTerms }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = isAgreedToTerms,
                        onCheckedChange = { isAgreedToTerms = it }
                    )
                    Text(
                        text = "点击即表示你已经同意了所有内容(包括禁止降级及可能有未提及的内容)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Button(
                    onClick = {
                        val formattedDate = "${onboardingYear}-${onboardingMonth.padStart(2, '0')}-${onboardingDay.padStart(2, '0')}"
                        onBirthDateSaved(formattedDate)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    enabled = validatedAge != null && isAgreedToTerms,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                ) {
                    Text(
                        text = getLocalizedText(currentLanguage, "start_journey"), 
                        style = MaterialTheme.typography.titleMedium,
                        color = if (validatedAge != null && isAgreedToTerms) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardPage(
    isCheckedInToday: Boolean,
    currentStreak: Int,
    lastCheckInTime: String?,
    hasPermission: Boolean,
    infoBarNotification: Pair<String, String>?,
    onInfoBarClose: () -> Unit,
    onCheckIn: () -> Unit,
    onRequestPermission: () -> Unit,
    survivalTestament: String,
    activeLanguage: String,
    birthDate: String,
    isAnimationEnabled: Boolean
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var notificationHour by remember { mutableStateOf(getNotificationHour(context)) }
    var notificationMinute by remember { mutableStateOf(getNotificationMinute(context)) }
    
    // Tactile button click animation states
    var isButtonPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (isButtonPressed && isAnimationEnabled) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ButtonScale"
    )
    
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationHour = getNotificationHour(context)
                notificationMinute = getNotificationMinute(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    
    // Realtime Survival Countdown timer
    var timeRemainingStr by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(isCheckedInToday, notificationHour, notificationMinute) {
        while (true) {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, notificationHour)
                set(java.util.Calendar.MINUTE, notificationMinute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            if (isCheckedInToday || now.after(target)) {
                target.add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
            val diffMs = target.timeInMillis - now.timeInMillis
            if (diffMs <= 0) {
                timeRemainingStr = "00:00:00"
            } else {
                val hours = diffMs / 3600000
                val minutes = (diffMs % 3600000) / 60000
                val seconds = (diffMs % 60000) / 1000
                val hStr = getLocalizedText(activeLanguage, "hours")
                val mStr = getLocalizedText(activeLanguage, "minutes")
                val sStr = getLocalizedText(activeLanguage, "seconds")
                timeRemainingStr = String.format("%02d %s %02d %s %02d %s", hours, hStr, minutes, mStr, seconds, sStr)
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCheckedInToday || !isAnimationEnabled) 1f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info Bars
        if (infoBarNotification != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer, // Light purple background as in screenshot
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(infoBarNotification.first, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(infoBarNotification.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha=0.8f))
                    }
                    IconButton(
                        onClick = onInfoBarClose,
                        modifier = Modifier.size(24.dp).padding(4.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }

        if (!isCheckedInToday) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer, // Light red background as in screenshot
                    contentColor = MaterialTheme.colorScheme.error
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = getLocalizedText(activeLanguage, "status_pending"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "提示：今天尚未发送守护信号。请及时点击下方主打卡进行状态签署，以解除预警。", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha=0.8f)
                        )
                    }
                    IconButton(
                        onClick = { /* Could have a local dismiss if we want, but screenshot doesn't require functional close if not provided */ },
                        modifier = Modifier.size(24.dp).padding(4.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Check-in Card (Restored to old version style from first image)
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Circular Icon Box
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(120.dp)
                        .border(
                            width = 4.dp,
                            color = if (isCheckedInToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCheckedInToday) Icons.Filled.Favorite else Icons.Filled.Favorite,
                        contentDescription = "Status Icon",
                        tint = if (isCheckedInToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = if (isCheckedInToday) getLocalizedText(activeLanguage, "status_safe") 
                           else "请打卡以确认您还活着",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = {
                        if (!isCheckedInToday) {
                            if (isAnimationEnabled) {
                                coroutineScope.launch {
                                    isButtonPressed = true
                                    delay(90)
                                    isButtonPressed = false
                                    delay(60)
                                    onCheckIn()
                                }
                            } else {
                                onCheckIn()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCheckedInToday) MaterialTheme.colorScheme.primaryContainer 
                                          else MaterialTheme.colorScheme.primary, // Indigo blue from screenshot
                        contentColor = if (isCheckedInToday) MaterialTheme.colorScheme.onPrimaryContainer 
                                        else MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = !isCheckedInToday,
                    shape = CircleShape,
                    modifier = Modifier
                        .scale(buttonScale)
                        .fillMaxWidth(0.85f)
                        .height(56.dp)
                        .testTag("check_in_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCheckedInToday) getLocalizedText(activeLanguage, "btn_alive_done") else "我还活着!",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // NEW FEATURE: Emergency SOS Morse Distress Beacon (离线求救震动信标)
        var isMorseVibrating by remember { mutableStateOf(false) }
        var bellClickCount by remember { mutableStateOf(0) }
        var showEmergencyCalls by remember { mutableStateOf(false) }
        val dContext = LocalContext.current
        var numberToCall by remember { mutableStateOf("") }
        
        val callPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted && numberToCall.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$numberToCall"))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { dContext.startActivity(intent) } catch (e: Exception) {}
            }
        }
        
        DisposableEffect(Unit) {
            onDispose {
                VibrationHelper.stopVibration(dContext)
            }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().testTag("emergency_sos_beacon_card"),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = if (isMorseVibrating) MaterialTheme.colorScheme.secondaryContainer
                                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isMorseVibrating) Icons.Filled.Warning else Icons.Filled.Notifications,
                        contentDescription = "SOS Beacon",
                        tint = if (isMorseVibrating) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                bellClickCount++
                                if (bellClickCount >= 3) {
                                    showEmergencyCalls = true
                                    bellClickCount = 0
                                }
                            }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "自愈防线：应急离线求救信标",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isMorseVibrating) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (showEmergencyCalls) {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "一键拨打紧急呼叫：",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("110", "119", "120", "122").forEach { number ->
                                Button(
                                    onClick = { 
                                        numberToCall = number
                                        if (androidx.core.content.ContextCompat.checkSelfPermission(dContext, Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            val intent = Intent(Intent.ACTION_CALL, android.net.Uri.parse("tel:$number"))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            try { dContext.startActivity(intent) } catch (e: Exception) {}
                                        } else {
                                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(number, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "当您身处无法大声呼救、信号微弱等极端困境时，开启此信标可以让手机以最大功率循环震动并发出声响，发送国际常用「S.O.S」摩尔斯电码（... --- ...），发出规律的声音信号协助搜救人员精确感知您的方位。",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMorseVibrating) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMorseVibrating) "🔴 SOS信标广播中..." else "⚪ 状态：就绪",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        color = if (isMorseVibrating) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isMorseVibrating = !isMorseVibrating
                            if (isMorseVibrating) {
                                VibrationHelper.triggerMorseSOSVibration(dContext, repeat = true)
                            } else {
                                VibrationHelper.stopVibration(dContext)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMorseVibrating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            contentColor = if (isMorseVibrating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("sos_beacon_toggle_button")
                    ) {
                        Text(
                            text = if (isMorseVibrating) "停止播发" else "发送SOS信标",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
 
        // New interactive "死了吗?" Survival switch status and Mortal Testament card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isCheckedInToday) Icons.Filled.Refresh else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (isCheckedInToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCheckedInToday) getLocalizedText(activeLanguage, "lifeline_shield_safe") else getLocalizedText(activeLanguage, "lifeline_shield_danger"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCheckedInToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCheckedInToday) getLocalizedText(activeLanguage, "countdown_safe") else getLocalizedText(activeLanguage, "countdown_danger"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeRemainingStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isCheckedInToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = getLocalizedText(activeLanguage, "testament_title"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.small
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = survivalTestament,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = getLocalizedText(activeLanguage, "testament_hint"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 13.sp
                    )
                }
            }
        }

        // Lived Duration Card based on birthDate
        val parsedAge = remember(birthDate) { calculateLivedAge(birthDate) }
        if (parsedAge != null) {
            val (yearsLived, monthsLived, daysLived) = parsedAge
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = "Time Lived",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = getLocalizedText(activeLanguage, "lived_prefix"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = yearsLived.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = getLocalizedText(activeLanguage, "years"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = monthsLived.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = getLocalizedText(activeLanguage, "months"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(
                                text = daysLived.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = getLocalizedText(activeLanguage, "days"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        } else {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = "No Birthday Setup",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = getLocalizedText(activeLanguage, "birth_hint") + " 💡",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .height(96.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = getLocalizedText(activeLanguage, "consecutive_days_title"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$currentStreak ${getLocalizedText(activeLanguage, "consecutive_day_unit")}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .weight(1.2f)
                    .height(96.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = getLocalizedText(activeLanguage, "last_verify_title"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (lastCheckInTime ?: "").ifEmpty { getLocalizedText(activeLanguage, "no_records") },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestPermission() },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Notification warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = getLocalizedText(activeLanguage, "notification_warning"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryLogsPage(
    allRecords: List<CheckInRecord>
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
 
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.List,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "本地生命状态安全档案",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "共已存档 ${allRecords.size} 次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        if (allRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "暂无生存打卡记录\n请在主页打卡激活守护档案数据库",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = allRecords,
                    key = { record -> record.date }
                ) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Checked",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = record.date + " [安全校验通过]",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📍 " + if (record.location.isNotBlank()) record.location else "无位置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正常守护中",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPage(
    currentTheme: AppThemeMode,
    currentCustomColor: Color,
    currentDarkModePref: String,
    hasPermission: Boolean,
    isAnimationEnabled: Boolean,
    currentFontSizeLevel: Int,
    onAnimationEnabledChanged: (Boolean) -> Unit,
    onFontSizeLevelSelected: (Int) -> Unit,
    onThemeSelected: (AppThemeMode) -> Unit,
    onCustomColorSelected: (Color) -> Unit,
    onDarkModeSelected: (String) -> Unit,
    onTriggerNotification: () -> Unit,
    onInjectData: () -> Unit,
    onClearRecords: () -> Unit,
    onRevokeTermsSilently: () -> Unit,
    onUndoCheckIn: () -> Unit,
    onRequestPermission: () -> Unit,
    survivalTestament: String,
    onTestamentChanged: (String) -> Unit,
    activeLanguage: String,
    onLanguageChange: (String) -> Unit,
    birthDate: String,
    onBirthDateChanged: (String) -> Unit,
    isShowingDeveloperSubPage: Boolean,
    onBackFromDeveloper: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var notificationHour by remember { mutableStateOf(getNotificationHour(context)) }
    var notificationMinute by remember { mutableStateOf(getNotificationMinute(context)) }
    var notificationHourText by remember(notificationHour) { mutableStateOf(notificationHour.toString()) }
    var notificationMinuteText by remember(notificationMinute) { mutableStateOf(notificationMinute.toString()) }
    var isForegroundEnabled by remember { mutableStateOf(getForegroundServiceEnabled(context)) }

    var showUndoWarningDialog by remember { mutableStateOf(false) }
    var showResetWarningDialog by remember { mutableStateOf(false) }
    var showRevokeWarningDialog by remember { mutableStateOf(false) }
    var versionClickCount by remember { mutableStateOf(0) }

    if (isShowingDeveloperSubPage) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Secondary page top bar with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackFromDeveloper,
                    modifier = Modifier.testTag("developer_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = getLocalizedText(activeLanguage, "dev_back"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = getLocalizedText(activeLanguage, "developer_options_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Developer debug tools card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().testTag("developer_options_unlocked_card"),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Build, 
                            null, 
                            tint = MaterialTheme.colorScheme.primary, 
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = getLocalizedText(activeLanguage, "dev_card_title"), 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = getLocalizedText(activeLanguage, "dev_card_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onTriggerNotification,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1.2f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = getLocalizedText(activeLanguage, "dev_notify_btn"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        ElevatedButton(
                            onClick = onInjectData,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(getLocalizedText(activeLanguage, "dev_inject_btn"), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getLocalizedText(activeLanguage, "dev_undo_title"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getLocalizedText(activeLanguage, "dev_undo_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showUndoWarningDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.testTag("developer_undo_check_in_button")
                        ) {
                            Text(getLocalizedText(activeLanguage, "dev_undo_btn"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(getLocalizedText(activeLanguage, "dev_clear_title"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            Text(getLocalizedText(activeLanguage, "dev_clear_desc"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { showResetWarningDialog = true },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(getLocalizedText(activeLanguage, "dev_clear_btn"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (showUndoWarningDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showUndoWarningDialog = false },
                            title = {
                                Text("⚠️ 撤销打卡警告")
                            },
                            text = {
                                Text("确定要撤销今日的生存打卡记录吗？此操作将重置您今日的生存守护状态。")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showUndoWarningDialog = false
                                        onUndoCheckIn()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("确认撤销", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUndoWarningDialog = false }) {
                                    Text(getLocalizedText(activeLanguage, "cancel_btn"))
                                }
                            }
                        )
                    }

                    if (showResetWarningDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showResetWarningDialog = false },
                            title = {
                                Text("⚠️ 重设档案库高危警告")
                            },
                            text = {
                                Text("确定要重设档案库并清空所有记录吗？此操作不可逆，您的所有生存档案历史和连续打卡天数将被永久销毁！")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showResetWarningDialog = false
                                        onClearRecords()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("确认重设", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showResetWarningDialog = false }) {
                                    Text(getLocalizedText(activeLanguage, "cancel_btn"))
                                }
                            }
                        )
                    }
                }
            }


        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Text(
            text = getLocalizedText(activeLanguage, "app_title"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // SECTION 1: Personalization (M3 Style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getLocalizedText(activeLanguage, "theme_section_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val sweepBrush = androidx.compose.ui.graphics.Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFF1E88E5),
                            Color(0xFF43A047),
                            Color(0xFFFB8C00),
                            Color(0xFFE53935),
                            Color(0xFF9C27B0),
                            Color(0xFFB59410),
                            Color(0xFF1E88E5)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ThemeDotM3(
                                color = Color.Transparent,
                                label = getLocalizedText(activeLanguage, "theme_system"),
                                isSelected = currentTheme == AppThemeMode.DYNAMIC,
                                brush = sweepBrush,
                                modifier = Modifier.weight(1f),
                                onClick = { onThemeSelected(AppThemeMode.DYNAMIC) }
                            )
                        }
                        ThemeDotM3(
                            color = Color(0xFF1E88E5),
                            label = getLocalizedText(activeLanguage, "theme_blue"),
                            isSelected = currentTheme == AppThemeMode.CLASSIC_BLUE,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AppThemeMode.CLASSIC_BLUE) }
                        )
 
                        ThemeDotM3(
                            color = Color(0xFFE53935),
                            label = getLocalizedText(activeLanguage, "theme_red"),
                            isSelected = currentTheme == AppThemeMode.SURVIVAL_RED,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AppThemeMode.SURVIVAL_RED) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeDotM3(
                            color = Color(0xFF43A047),
                            label = getLocalizedText(activeLanguage, "theme_green"),
                            isSelected = currentTheme == AppThemeMode.FOREST_GREEN,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AppThemeMode.FOREST_GREEN) }
                        )
                        ThemeDotM3(
                            color = Color(0xFFFB8C00),
                            label = getLocalizedText(activeLanguage, "theme_orange"),
                            isSelected = currentTheme == AppThemeMode.SUNSET_ORANGE,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AppThemeMode.SUNSET_ORANGE) }
                        )
                        ThemeDotM3(
                            color = currentCustomColor,
                            label = "自定义",
                            isSelected = currentTheme == AppThemeMode.CUSTOM,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AppThemeMode.CUSTOM) }
                        )
                    }

                    if (currentTheme == AppThemeMode.CUSTOM) {
                        var showHexDialog by remember { mutableStateOf(false) }
                        var hexInput by remember { mutableStateOf("") }
                        var isRgbSlidersExpanded by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // RGB 滑块折叠/展开开关（默认折叠）
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isRgbSlidersExpanded = !isRgbSlidersExpanded }
                                    .testTag("toggle_rgb_sliders_card"),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier.size(24.dp),
                                            shape = CircleShape,
                                            color = currentCustomColor,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                        ) {}
                                        Column {
                                            Text(
                                                text = "RGB 色值调节滑块",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isRgbSlidersExpanded) "点击折叠 RGB 滑块" else "默认已折叠，点击展开调节红、绿、蓝滑块",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (isRgbSlidersExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = if (isRgbSlidersExpanded) "折叠" else "展开",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // 展开后显示 RGB 滑块调节功能
                            AnimatedVisibility(
                                visible = isRgbSlidersExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    var rValue by remember(currentCustomColor) { mutableStateOf((currentCustomColor.toArgb() shr 16 and 0xFF).toFloat()) }
                                    var gValue by remember(currentCustomColor) { mutableStateOf((currentCustomColor.toArgb() shr 8 and 0xFF).toFloat()) }
                                    var bValue by remember(currentCustomColor) { mutableStateOf((currentCustomColor.toArgb() and 0xFF).toFloat()) }
                                    val previewColor = Color(rValue.toInt(), gValue.toInt(), bValue.toInt())

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            modifier = Modifier.size(44.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = previewColor
                                        ) {}
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text(
                                            text = "实时混色预览 (松手后应用主题)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    val onColorUpdated = {
                                        val newColor = Color(rValue.toInt(), gValue.toInt(), bValue.toInt())
                                        onCustomColorSelected(newColor)
                                        onThemeSelected(AppThemeMode.CUSTOM)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "红 (R): ${rValue.toInt()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(80.dp)
                                        )
                                        Slider(
                                            value = rValue,
                                            onValueChange = { rValue = it },
                                            onValueChangeFinished = onColorUpdated,
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = previewColor,
                                                thumbColor = previewColor
                                            )
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "绿 (G): ${gValue.toInt()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(80.dp)
                                        )
                                        Slider(
                                            value = gValue,
                                            onValueChange = { gValue = it },
                                            onValueChangeFinished = onColorUpdated,
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = previewColor,
                                                thumbColor = previewColor
                                            )
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "蓝 (B): ${bValue.toInt()}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(80.dp)
                                        )
                                        Slider(
                                            value = bValue,
                                            onValueChange = { bValue = it },
                                            onValueChangeFinished = onColorUpdated,
                                            valueRange = 0f..255f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = previewColor,
                                                thumbColor = previewColor
                                            )
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showHexDialog = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp).align(Alignment.End),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("HEX精确输入", style = MaterialTheme.typography.labelSmall)
                            }

                            if (showHexDialog) {
                                AlertDialog(
                                    onDismissRequest = { showHexDialog = false },
                                    title = { Text("输入自定义HEX颜色") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("请输入6位或8位16进制颜色代码（例如 #FF4081 或 #009688）：")
                                            OutlinedTextField(
                                                value = hexInput,
                                                onValueChange = { hexInput = it },
                                                placeholder = { Text("#FF4081") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            try {
                                                var cleanHex = hexInput.trim().removePrefix("#")
                                                if (cleanHex.length == 6) {
                                                    cleanHex = "FF" + cleanHex
                                                }
                                                if (cleanHex.length == 8) {
                                                    val parsedArgb = cleanHex.toLong(16).toInt()
                                                    onCustomColorSelected(Color(parsedArgb))
                                                    onThemeSelected(AppThemeMode.CUSTOM)
                                                    showHexDialog = false
                                                } else {
                                                    Toast.makeText(context, "格式无效，请输入6位HEX代码", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "无效的颜色代码", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Text("确定")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showHexDialog = false }) {
                                            Text("取消")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = getLocalizedText(activeLanguage, "theme_preference_title"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDarkModeSelected("SYSTEM") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentDarkModePref == "SYSTEM", onClick = { onDarkModeSelected("SYSTEM") })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getLocalizedText(activeLanguage, "theme_pref_system"), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDarkModeSelected("LIGHT") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentDarkModePref == "LIGHT", onClick = { onDarkModeSelected("LIGHT") })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getLocalizedText(activeLanguage, "theme_pref_light"), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDarkModeSelected("DARK") }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentDarkModePref == "DARK", onClick = { onDarkModeSelected("DARK") })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getLocalizedText(activeLanguage, "theme_pref_dark"), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAnimationEnabledChanged(!isAnimationEnabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isAnimationEnabled) Icons.Filled.PlayArrow else Icons.Filled.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = getLocalizedText(activeLanguage, "enable_animation_setting_title"), 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getLocalizedText(activeLanguage, "enable_animation_setting_desc"), 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isAnimationEnabled,
                        onCheckedChange = { onAnimationEnabledChanged(it) },
                        modifier = Modifier.testTag("animation_toggle_switch")
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Font Size Settings
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = getLocalizedText(activeLanguage, "font_size_setting_title"), 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getLocalizedText(activeLanguage, "font_size_setting_desc"), 
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Slider(
                        value = currentFontSizeLevel.toFloat(),
                        onValueChange = { onFontSizeLevelSelected(it.toInt()) },
                        valueRange = 0f..4f,
                        steps = 3,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(getLocalizedText(activeLanguage, "font_size_0"), style = MaterialTheme.typography.bodySmall)
                        Text(getLocalizedText(activeLanguage, "font_size_2"), style = MaterialTheme.typography.bodySmall)
                        Text(getLocalizedText(activeLanguage, "font_size_4"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        
        // SECTION 1.5: Birth Date Selection (NEW)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Birthday Choice
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DateRange, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getLocalizedText(activeLanguage, "birth_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = getLocalizedText(activeLanguage, "enter_birth_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                // Input fields inside Settings for year, month, day
                var editYear by remember(birthDate) {
                    val parts = birthDate.split("-")
                    mutableStateOf(parts.getOrNull(0)?.ifEmpty { null } ?: "1995")
                }
                var editMonth by remember(birthDate) {
                    val parts = birthDate.split("-")
                    mutableStateOf(parts.getOrNull(1)?.ifEmpty { null } ?: "10")
                }
                var editDay by remember(birthDate) {
                    val parts = birthDate.split("-")
                    mutableStateOf(parts.getOrNull(2)?.ifEmpty { null } ?: "24")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("2000") },
                        label = { Text(getLocalizedText(activeLanguage, "years")) },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = editMonth,
                        onValueChange = { editMonth = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("1") },
                        label = { Text(getLocalizedText(activeLanguage, "months")) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = editDay,
                        onValueChange = { editDay = it.filter { c -> c.isDigit() } },
                        placeholder = { Text("1") },
                        label = { Text(getLocalizedText(activeLanguage, "days")) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                }

                // Check and show live age calculation in setting or allow saving
                val settingsAge = remember(editYear, editMonth, editDay) {
                    try {
                        val y = editYear.toIntOrNull() ?: -1
                        val m = editMonth.toIntOrNull() ?: -1
                        val d = editDay.toIntOrNull() ?: -1
                        if (y in 1900..2050 && m in 1..12 && d in 1..31) {
                            val formattedDate = "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
                            val parsedDate = sdf.parse(formattedDate)
                            if (parsedDate != null && parsedDate.time > System.currentTimeMillis()) {
                                null
                            } else {
                                calculateLivedAge(formattedDate)
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (settingsAge != null) {
                    val (y, m, d) = settingsAge
                    Text(
                        text = "✨ " + getLocalizedText(activeLanguage, "lived_prefix") + " $y " + getLocalizedText(activeLanguage, "years") + " $m " + getLocalizedText(activeLanguage, "months") + " $d " + getLocalizedText(activeLanguage, "days"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Button(
                        onClick = {
                            val y = editYear.toInt()
                            val m = editMonth.toInt()
                            val d = editDay.toInt()
                            val formattedDate = "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
                            onBirthDateChanged(formattedDate)
                            val toastMsg = "更新成功！"
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(getLocalizedText(activeLanguage, "edit_birth_btn"), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = getLocalizedText(activeLanguage, "birth_date_invalid"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }


 

        // SECTION 1.6: Ad-Free Section
        var isAdFree by remember { mutableStateOf(getIsAdFree(context)) }
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().testTag("ad_free_settings_card"),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("免广告设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (isAdFree) {
                    Text(
                        text = "您已开启免广告，感谢您的支持！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            setIsAdFree(context, false)
                            isAdFree = false
                            Toast.makeText(context, "已关闭免广告", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("disable_ad_free_button"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("关闭免广告", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                } else {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Text(
                        text = "摇一摇触发广告。赞助￥0.5获取免广告密钥，或者您也可以尝试反编译APK在代码中寻找密钥：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
                            }
                            .testTag("contact_author_get_key_btn"),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "联系作者获取密钥",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "滑动至最底部",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    
                    var adFreeKeyInput by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = adFreeKeyInput,
                        onValueChange = { adFreeKeyInput = it },
                        placeholder = { Text("免广告密钥") },
                        modifier = Modifier.fillMaxWidth().testTag("ad_free_key_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    
                    Button(
                        onClick = {
                            if (VALID_AD_FREE_KEYS.contains(adFreeKeyInput.uppercase())) {
                                setIsAdFree(context, true)
                                isAdFree = true
                                Toast.makeText(context, "激活成功，感谢赞助！", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "密钥无效", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("activate_ad_free_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("激活免广告", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

        // SECTION 2: Notification Reminders
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().testTag("custom_notification_settings_card"),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getLocalizedText(activeLanguage, "daily_deadline_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = String.format(getLocalizedText(activeLanguage, "daily_deadline_desc"), notificationHour, notificationMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(getLocalizedText(activeLanguage, "notif_route_label"), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (hasPermission) getLocalizedText(activeLanguage, "active_connected") else getLocalizedText(activeLanguage, "inactive"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                if (!hasPermission) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(getLocalizedText(activeLanguage, "grant_permission_btn"), fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = "自定义通知时间（24小时制）：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour field
                    OutlinedTextField(
                        value = notificationHourText,
                        onValueChange = { newValue ->
                            val cleanValue = newValue.filter { it.isDigit() }
                            if (cleanValue.length <= 2) {
                                notificationHourText = cleanValue
                                saveJob?.cancel()
                                saveJob = scope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    val parsed = cleanValue.toIntOrNull()
                                    if (parsed != null && parsed in 0..23) {
                                        notificationHour = parsed
                                        saveNotificationTime(context, parsed, notificationMinute)
                                        AlarmScheduler.scheduleDailyCheck(context, parsed, notificationMinute)
                                    } else {
                                        notificationHourText = notificationHour.toString() // revert if invalid
                                    }
                                }
                            }
                        },
                        label = { Text("时 (0-23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )

                    Text(":", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // Minute field
                    OutlinedTextField(
                        value = notificationMinuteText,
                        onValueChange = { newValue ->
                            val cleanValue = newValue.filter { it.isDigit() }
                            if (cleanValue.length <= 2) {
                                notificationMinuteText = cleanValue
                                saveJob?.cancel()
                                saveJob = scope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    val parsed = cleanValue.toIntOrNull()
                                    if (parsed != null && parsed in 0..59) {
                                        notificationMinute = parsed
                                        saveNotificationTime(context, notificationHour, parsed)
                                        AlarmScheduler.scheduleDailyCheck(context, notificationHour, parsed)
                                    } else {
                                        notificationMinuteText = notificationMinute.toString()
                                    }
                                }
                            }
                        },
                        label = { Text("分 (0-59)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }

                Text(
                    text = "设定时间：${notificationHour.toString().padStart(2, '0')}:${notificationMinute.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "常驻通知（前台服务防杀）",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "强制活了么应用保活",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isForegroundEnabled,
                        onCheckedChange = { checked ->
                            isForegroundEnabled = checked
                            saveForegroundServiceEnabled(context, checked)
                            if (checked) {
                                LifelineForegroundService.start(context)
                            } else {
                                LifelineForegroundService.stop(context)
                            }
                        }
                    )
                }
            }
        }

        // SECTION 2.5: Survival Testament / 临终寄语 Editor
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Testament",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(getLocalizedText(activeLanguage, "testament_archive_title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = getLocalizedText(activeLanguage, "testament_archive_desc"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                var textValue by remember { mutableStateOf(survivalTestament) }
                OutlinedTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        onTestamentChanged(it)
                    },
                    modifier = Modifier.fillMaxWidth().height(110.dp).testTag("testament_input"),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = false,
                    placeholder = { Text(getLocalizedText(activeLanguage, "testament_placeholder"), style = MaterialTheme.typography.bodySmall) },
                    textStyle = MaterialTheme.typography.bodySmall
                )
                
                Text(
                    text = getLocalizedText(activeLanguage, "privacy_protect"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // SECTION 3: Backup and Restore
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Backup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("数据备份与还原", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "将打卡记录导出为 JSON 文件，或从之前的备份文件中还原打卡记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportJson,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出打卡记录", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onImportJson,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("还原打卡记录", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // SECTION 4: Android-style "About Phone" (关于本系统防卫终端)
        Text(
            text = getLocalizedText(activeLanguage, "about_app_title"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Top Header with Android Device graphic or Info Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Device info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = getLocalizedText(activeLanguage, "app_title"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = getLocalizedText(activeLanguage, "app_title") + " · " + getLocalizedText(activeLanguage, "app_version"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Version number info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("version_item")
                        .clickable {
                            versionClickCount++
                            if (versionClickCount >= 10) {
                                showRevokeWarningDialog = true
                            }
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "Version icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = getLocalizedText(activeLanguage, "version_label"),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (showRevokeWarningDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { 
                            showRevokeWarningDialog = false 
                            versionClickCount = 0
                        },
                        title = {
                            Text("撤销条款与清除数据")
                        },
                        text = {
                            Text("警告：您正在撤销协议条款。点击确认后，所有本地数据将被永久清除，且应用将立即退出。是否继续？")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showRevokeWarningDialog = false
                                    onRevokeTermsSilently()
                                    if (context is android.app.Activity) {
                                        context.finishAffinity()
                                    } else {
                                        kotlin.system.exitProcess(0)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("确定清除并退出", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                showRevokeWarningDialog = false 
                                versionClickCount = 0
                            }) {
                                Text(getLocalizedText(activeLanguage, "cancel_btn"))
                            }
                        }
                    )
                }


                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // Download updates
                var showUpdateDialog by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showUpdateDialog = true }
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Download update",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = getLocalizedText(activeLanguage, "check_updates_btn"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (showUpdateDialog) {
                    val cloudreve1Label = "Cloudreve 1 (有广告)"
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = {
                            Text(getLocalizedText(activeLanguage, "has_new_version_text"))
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = getLocalizedText(activeLanguage, "select_download_link"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            showUpdateDialog = false
                                            (context as MainActivity).openWebUrl("https://pan.huang1111.cn/s/A6NvBiB")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(cloudreve1Label)
                                    }
                                    Button(
                                        onClick = {
                                            showUpdateDialog = false
                                            (context as MainActivity).openWebUrl("https://pan.sysri.cn/s/XA73HB")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cloudreve 2")
                                    }
                                    Button(
                                        onClick = {
                                            showUpdateDialog = false
                                            (context as MainActivity).openWebUrl("https://cncncloud.com/s/0P9Up")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cloudreve 3")
                                    }
                                    Button(
                                        onClick = {
                                            showUpdateDialog = false
                                            (context as MainActivity).openWebUrl("https://111pan.cn/s/a6NsJ")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cloudreve 4")
                                    }
                                    Button(
                                        onClick = {
                                            showUpdateDialog = false
                                            (context as MainActivity).openWebUrl("https://pan.moe/s/0ZPvuG")
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cloudreve 5")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text(getLocalizedText(activeLanguage, "cancel_btn"))
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                // Contact Author Row (Moved to bottom)
                var showContactAuthorDialog by remember { mutableStateOf(false) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .testTag("about_contact_author_row")
                        .clickable { showContactAuthorDialog = true }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Email,
                            contentDescription = "Contact author icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "联系作者",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Contact author detail",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showContactAuthorDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showContactAuthorDialog = false },
                        title = {
                            Text("联系作者")
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "请选择联系作者的方式（与免广告支持通道一致）：",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = {
                                        showContactAuthorDialog = false
                                        (context as MainActivity).openWebUrl("https://t.me/mwcanary")
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("about_telegram_contact_btn"),
                                    shape = CircleShape
                                ) {
                                    Text("Telegram 联系作者", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showContactAuthorDialog = false
                                        (context as MainActivity).openWebUrl("https://wa.me/qr/D5YEI5LG6RG3F1")
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("about_whatsapp_contact_btn"),
                                    shape = CircleShape
                                ) {
                                    Text("Whatsapp 联系作者", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                OutlinedButton(
                                    onClick = {
                                        showContactAuthorDialog = false
                                        (context as MainActivity).openWebUrl("https://teams.live.com/l/invite/FEAKgipM-NHSstOjg?v=g1")
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("about_teams_contact_btn"),
                                    shape = CircleShape
                                ) {
                                    Text("Teams联系作者", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showContactAuthorDialog = false }) {
                                Text(getLocalizedText(activeLanguage, "cancel_btn"))
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Author Bio text & Sponsor Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MW Canary & Google Gemini",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = getLocalizedText(activeLanguage, "author_desc"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    val sponsorLabel = "赞助作者"
                    OutlinedButton(
                        onClick = { (context as MainActivity).openWebUrl("https://qr.alipay.com/fkx12551xqkxb4jowrbhq40") },
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).testTag("sponsor_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Sponsor link",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sponsorLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun ThemeDotM3(
    color: Color,
 
    label: String,
    isSelected: Boolean,
    brush: androidx.compose.ui.graphics.Brush? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (brush != null) Modifier.background(brush)
                    else Modifier.background(color)
                )
                .clickable { onClick() }
                .border(
                    width = if (isSelected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun AboutItemRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

fun requestNotificationPermissionSetting(context: Context) {
    try {
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                putExtra("app_package", context.packageName)
                putExtra("app_uid", context.applicationInfo.uid)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

@Composable
fun SystemAccessCheckOverlay(
    activeLanguage: String,
    hasNotification: Boolean,
    hasBatteryExemption: Boolean,
    onJumpNotification: () -> Unit,
    onJumpBattery: () -> Unit,
    onRefresh: () -> Unit,
    onIgnoreBattery: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) { }
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = "请授权这些必要的权限",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "本程序是单机离线生命守护工具。当您失联时，依靠定时闹钟和高强度震动发出警报。若以下权限未启用，守护将完全失效。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // Condition 1: Notification Permission
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (hasNotification) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(
                        1.dp,
                        if (hasNotification) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasNotification) Icons.Filled.CheckCircle else Icons.Filled.Notifications,
                            contentDescription = null,
                            tint = if (hasNotification) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "通知权限",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasNotification) "已授权" else "未授权。您将无法收到失联警报！",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!hasNotification) {
                            TextButton(onClick = onJumpNotification) {
                                Text("去开启")
                            }
                        }
                    }
                }

                // Condition 2: Battery Optimization
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (hasBatteryExemption) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                    border = BorderStroke(
                        1.dp,
                        if (hasBatteryExemption) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasBatteryExemption) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (hasBatteryExemption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "电池白名单后台运行",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (hasBatteryExemption) "已排除 (完美常驻)" else "被限制。可能被系统杀后台，导致守护中断！",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!hasBatteryExemption) {
                            TextButton(onClick = onJumpBattery) {
                                Text("去设置")
                            }
                        }
                    }
                }

                // Spacer
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(
                        text = "重新检测并进入",
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!hasBatteryExemption) {
                    TextButton(
                        onClick = onIgnoreBattery,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("不设置白名单，承担被杀后台风险进入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
