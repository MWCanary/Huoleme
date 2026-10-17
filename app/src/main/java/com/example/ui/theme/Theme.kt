package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class AppThemeMode {
    CLASSIC_BLUE,
    SURVIVAL_RED,
    FOREST_GREEN,
    SUNSET_ORANGE,
    DYNAMIC,
    IOS_GLASS,
    GOOGLE_STYLE,
    CUSTOM
}

// 1. Classic Blue Color Schemes
private val BlueLightScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = Color(0xFFF0F4FA),
    onPrimaryContainer = BlueOnPrimaryContainer,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F3F5),
    onSecondaryContainer = Color(0xFF202124),
    background = Color(0xFFF4F9FF),
    onBackground = BlueOnBackground,
    surface = Color(0xFFF4F9FF),
    onSurface = BlueOnSurface,
    surfaceVariant = Color(0xFFE0EFFF),
    onSurfaceVariant = Color(0xFF43474E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val BlueDarkScheme = darkColorScheme(
    primary = BlueDarkPrimary,
    onPrimary = BlueDarkOnPrimary,
    primaryContainer = BlueDarkPrimaryContainer,
    onPrimaryContainer = BlueDarkOnPrimaryContainer,
    secondary = BlueDarkSecondary,
    onSecondary = Color(0xFF202124),
    secondaryContainer = BlueDarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFE8EAED),
    background = BlueDarkBackground,
    onBackground = BlueDarkOnBackground,
    surface = BlueDarkSurface,
    onSurface = BlueDarkOnSurface,
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 2. Survival Crimson Red Schemes
private val RedLightScheme = lightColorScheme(
    primary = RedPrimary,
    onPrimary = RedOnPrimary,
    primaryContainer = Color(0xFFFDF2F2),
    onPrimaryContainer = RedOnPrimaryContainer,
    secondary = RedSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5F5F5),
    onSecondaryContainer = Color(0xFF212121),
    background = Color(0xFFFFF5F5),
    onBackground = RedOnBackground,
    surface = Color(0xFFFFF5F5),
    onSurface = RedOnSurface,
    surfaceVariant = Color(0xFFFFE0E0),
    onSurfaceVariant = Color(0xFF534341),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val RedDarkScheme = darkColorScheme(
    primary = RedDarkPrimary,
    onPrimary = RedDarkOnPrimary,
    primaryContainer = RedDarkPrimaryContainer,
    onPrimaryContainer = RedDarkOnPrimaryContainer,
    secondary = RedDarkSecondary,
    onSecondary = Color(0xFF212121),
    secondaryContainer = RedDarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFECEFF1),
    background = RedDarkBackground,
    onBackground = RedDarkOnBackground,
    surface = RedDarkSurface,
    onSurface = RedDarkOnSurface,
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 3. Forest Green Schemes
private val GreenLightScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = Color(0xFFF2F8F2),
    onPrimaryContainer = GreenOnPrimaryContainer,
    secondary = GreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F6F4),
    onSecondaryContainer = Color(0xFF2E302E),
    background = Color(0xFFF2FCF3),
    onBackground = GreenOnBackground,
    surface = Color(0xFFF2FCF3),
    onSurface = GreenOnSurface,
    surfaceVariant = Color(0xFFDDF6E0),
    onSurfaceVariant = Color(0xFF43483E),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val GreenDarkScheme = darkColorScheme(
    primary = GreenDarkPrimary,
    onPrimary = GreenDarkOnPrimary,
    primaryContainer = GreenDarkPrimaryContainer,
    onPrimaryContainer = GreenDarkOnPrimaryContainer,
    secondary = GreenDarkSecondary,
    onSecondary = Color(0xFF1B5E20),
    secondaryContainer = GreenDarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFE8EFE9),
    background = GreenDarkBackground,
    onBackground = GreenDarkOnBackground,
    surface = GreenDarkSurface,
    onSurface = GreenDarkOnSurface,
    surfaceVariant = Color(0xFF43483E),
    onSurfaceVariant = Color(0xFFC3C8BB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 4. Sunset Orange Schemes
private val OrangeLightScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = OrangeOnPrimary,
    primaryContainer = Color(0xFFFFF7F2),
    onPrimaryContainer = OrangeOnPrimaryContainer,
    secondary = OrangeSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6F6F6),
    onSecondaryContainer = Color(0xFF3E2723),
    background = Color(0xFFFFF7F0),
    onBackground = OrangeOnBackground,
    surface = Color(0xFFFFF7F0),
    onSurface = OrangeOnSurface,
    surfaceVariant = Color(0xFFFFE5D0),
    onSurfaceVariant = Color(0xFF52443C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val OrangeDarkScheme = darkColorScheme(
    primary = OrangeDarkPrimary,
    onPrimary = OrangeDarkOnPrimary,
    primaryContainer = OrangeDarkPrimaryContainer,
    onPrimaryContainer = OrangeDarkOnPrimaryContainer,
    secondary = OrangeDarkSecondary,
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = OrangeDarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFEFEBE9),
    background = OrangeDarkBackground,
    onBackground = OrangeDarkOnBackground,
    surface = OrangeDarkSurface,
    onSurface = OrangeDarkOnSurface,
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 5. Cyberpunk Purple
private val CyberLightScheme = lightColorScheme(
    primary = Color(0xFF9C27B0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8F2FC),
    onPrimaryContainer = Color(0xFF7B1FA2),
    secondary = Color(0xFF3F51B5), // Indigo blue instead of pink-red
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE7F6), // Lavender grey instead of soft pink
    onSecondaryContainer = Color(0xFF311B92),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF7B1FA2),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2), // Clean warm red
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val CyberDarkScheme = darkColorScheme(
    primary = Color(0xFFE040FB),
    onPrimary = Color(0xFF2A0033),
    primaryContainer = Color(0xFF4A0072),
    onPrimaryContainer = Color(0xFFF3E5F5),
    secondary = Color(0xFF00E5FF), // cyan/blue instead of pink neon
    onSecondary = Color(0xFF4A0072),
    secondaryContainer = Color(0xFF311B92), // dark blue-violet instead of rose-crimson
    onSecondaryContainer = Color(0xFFFCE4EC),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1220),
    onBackground = Color(0xFFF3E5F5),
    onSurface = Color(0xFFF3E5F5),
    surfaceVariant = Color(0xFF4A0072),
    onSurfaceVariant = Color(0xFFFCE4EC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 6. Elegant Gold
private val GoldLightScheme = lightColorScheme(
    primary = Color(0xFFB59410),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFAF9F2),
    onPrimaryContainer = Color(0xFF221B00),
    secondary = Color(0xFF8D6E63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5F5F5),
    onSecondaryContainer = Color(0xFF3E2723),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF221B00),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF221B00),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF221B00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val GoldDarkScheme = darkColorScheme(
    primary = Color(0xFFFFD700),
    onPrimary = Color(0xFF2C2512),
    primaryContainer = Color(0xFF5A4900),
    onPrimaryContainer = Color(0xFFFFF176),
    secondary = Color(0xFFD7CCC8),
    onSecondary = Color(0xFF2C2512),
    secondaryContainer = Color(0xFF4E342E),
    onSecondaryContainer = Color(0xFFEFEBE9),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1A14),
    onBackground = Color(0xFFFFFDF8),
    onSurface = Color(0xFFFFFDF8),
    surfaceVariant = Color(0xFF645300),
    onSurfaceVariant = Color(0xFFFFF176),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 7. iOS Liquid Glass Themes
private val IosLightScheme = lightColorScheme(
    primary = IosPrimary,
    onPrimary = IosOnPrimary,
    primaryContainer = Color(0xFFF0F6FF),
    onPrimaryContainer = IosOnPrimaryContainer,
    secondary = IosSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2F2F7),
    onSecondaryContainer = Color(0xFF1C1C1E),
    background = Color(0xFFFFFFFF),
    onBackground = IosOnBackground,
    surface = Color(0xFFFFFFFF),
    onSurface = IosOnSurface,
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF3C3C43),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF8E0000),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val IosDarkScheme = darkColorScheme(
    primary = IosDarkPrimary,
    onPrimary = IosDarkOnPrimary,
    primaryContainer = IosDarkPrimaryContainer,
    onPrimaryContainer = IosDarkOnPrimaryContainer,
    secondary = IosDarkSecondary,
    onSecondary = Color(0xFF121212),
    secondaryContainer = IosDarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFF2F2F7),
    background = IosDarkBackground,
    onBackground = IosDarkOnBackground,
    surface = IosDarkSurface,
    onSurface = IosDarkOnSurface,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFFEBF5FE),
    error = Color(0xFFFF453A),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// 8. Google Style Themes
private val GoogleLightScheme = lightColorScheme(
    primary = GooglePrimary,
    onPrimary = GoogleOnPrimary,
    primaryContainer = Color(0xFFF0F4FA),
    onPrimaryContainer = GoogleOnPrimaryContainer,
    secondary = GoogleSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F3F4),
    onSecondaryContainer = Color(0xFF202124),
    background = Color(0xFFFFFFFF),
    onBackground = GoogleOnBackground,
    surface = Color(0xFFFFFFFF),
    onSurface = GoogleOnSurface,
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF5F6368),
    error = Color(0xFFEA4335),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFC5221F),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFFF3F4F6),
    surfaceContainerLow = Color(0xFFF9FAFB),
    surfaceContainerHigh = Color(0xFFE5E7EB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFD1D5DB),
    surfaceDim = Color(0xFFE5E7EB),
    surfaceBright = Color(0xFFFFFFFF)
)

private val GoogleDarkScheme = darkColorScheme(
    primary = GoogleDarkPrimary,
    onPrimary = GoogleDarkOnPrimary,
    primaryContainer = GoogleDarkPrimaryContainer,
    onPrimaryContainer = GoogleDarkOnPrimaryContainer,
    secondary = GoogleDarkSecondary,
    onSecondary = Color(0xFF202124),
    secondaryContainer = GoogleDarkSecondaryContainer,
    onSecondaryContainer = Color(0xFFE8EAED),
    background = GoogleDarkBackground,
    onBackground = GoogleDarkOnBackground,
    surface = GoogleDarkSurface,
    onSurface = GoogleDarkOnSurface,
    surfaceVariant = Color(0xFF3C4043),
    onSurfaceVariant = Color(0xFFBDC1C6),
    error = Color(0xFFF28B82),
    onError = Color(0xFF202124),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

fun getCustomLightScheme(customColor: Color): ColorScheme {
    return lightColorScheme(
        primary = customColor,
        onPrimary = Color.White,
        primaryContainer = customColor.copy(alpha = 0.15f).compositeOver(Color.White),
        onPrimaryContainer = Color(0xFF202124),
        secondary = customColor.copy(alpha = 0.7f).compositeOver(Color.White),
        onSecondary = Color.White,
        secondaryContainer = customColor.copy(alpha = 0.1f).compositeOver(Color.White),
        onSecondaryContainer = Color(0xFF202124),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF202124),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF202124),
        surfaceVariant = customColor.copy(alpha = 0.05f).compositeOver(Color.White),
        onSurfaceVariant = Color(0xFF43474E),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF410002),
        surfaceTint = Color.Transparent,
        surfaceContainer = customColor.copy(alpha = 0.03f).compositeOver(Color.White),
        surfaceContainerLow = customColor.copy(alpha = 0.02f).compositeOver(Color.White),
        surfaceContainerHigh = customColor.copy(alpha = 0.06f).compositeOver(Color.White),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerHighest = customColor.copy(alpha = 0.08f).compositeOver(Color.White),
        surfaceDim = customColor.copy(alpha = 0.06f).compositeOver(Color.White),
        surfaceBright = Color(0xFFFFFFFF)
    )
}

fun getCustomDarkScheme(customColor: Color): ColorScheme {
    val darkBg = Color(0xFF121212)
    val surfaceBg = Color(0xFF1D1B20)
    return darkColorScheme(
        primary = customColor,
        onPrimary = Color(0xFF121212),
        primaryContainer = customColor.copy(alpha = 0.25f).compositeOver(surfaceBg),
        onPrimaryContainer = Color(0xFFE8EAED),
        secondary = customColor.copy(alpha = 0.6f).compositeOver(surfaceBg),
        onSecondary = Color(0xFF121212),
        secondaryContainer = customColor.copy(alpha = 0.15f).compositeOver(surfaceBg),
        onSecondaryContainer = Color(0xFFE8EAED),
        background = darkBg,
        onBackground = Color(0xFFE8EAED),
        surface = surfaceBg,
        onSurface = Color(0xFFE8EAED),
        surfaceVariant = customColor.copy(alpha = 0.1f).compositeOver(surfaceBg),
        onSurfaceVariant = Color(0xFFC3C7D0),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.CLASSIC_BLUE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to enjoy our custom styled brand palettes
    customColor: Color = Color(0xFF6200EE),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        (dynamicColor || themeMode == AppThemeMode.DYNAMIC) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            if (darkTheme) {
                when (themeMode) {
                    AppThemeMode.CLASSIC_BLUE -> BlueDarkScheme
                    AppThemeMode.SURVIVAL_RED -> RedDarkScheme
                    AppThemeMode.FOREST_GREEN -> GreenDarkScheme
                    AppThemeMode.SUNSET_ORANGE -> OrangeDarkScheme
                    AppThemeMode.DYNAMIC -> BlueDarkScheme
                    AppThemeMode.IOS_GLASS -> IosDarkScheme
                    AppThemeMode.GOOGLE_STYLE -> GoogleDarkScheme
                    AppThemeMode.CUSTOM -> getCustomDarkScheme(customColor)
                }
            } else {
                when (themeMode) {
                    AppThemeMode.CLASSIC_BLUE -> BlueLightScheme
                    AppThemeMode.SURVIVAL_RED -> RedLightScheme
                    AppThemeMode.FOREST_GREEN -> GreenLightScheme
                    AppThemeMode.SUNSET_ORANGE -> OrangeLightScheme
                    AppThemeMode.DYNAMIC -> BlueLightScheme
                    AppThemeMode.IOS_GLASS -> IosLightScheme
                    AppThemeMode.GOOGLE_STYLE -> GoogleLightScheme
                    AppThemeMode.CUSTOM -> getCustomLightScheme(customColor)
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
