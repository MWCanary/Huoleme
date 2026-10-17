package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent { 
      MyApplicationTheme(themeMode = AppThemeMode.CLASSIC_BLUE, darkTheme = false) { 
        DashboardPage(
          isCheckedInToday = false,
          currentStreak = 5,
          lastCheckInTime = "2026-05-23 21:44:12",
          hasPermission = true,
          infoBarNotification = null,
          onInfoBarClose = {},
          onCheckIn = {},
          onRequestPermission = {},
          survivalTestament = "This is a survival testament.",
          activeLanguage = "zh",
          birthDate = "1990-01-01"
        )
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
