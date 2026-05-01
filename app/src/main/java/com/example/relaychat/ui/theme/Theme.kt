package com.example.relaychat.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.relaychat.core.model.AppThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF2757E6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE4FF),
    onPrimaryContainer = Color(0xFF0E1C4D),
    secondary = Color(0xFF147A72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEF4EE),
    onSecondaryContainer = Color(0xFF052A27),
    tertiary = Color(0xFF4D73CB),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE7FF),
    onTertiaryContainer = Color(0xFF112855),
    background = Color(0xFFF1F5FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFBFDFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6ECF5),
    onSurfaceVariant = Color(0xFF4A586C),
    outline = Color(0xFF78879E),
    outlineVariant = Color(0xFFD3DBE7),
    surfaceTint = Color(0xFF2757E6),
    scrim = Color(0xCC09101D),
    error = Color(0xFFC7464C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFAD9DB),
    onErrorContainer = Color(0xFF420A0F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C0FF),
    onPrimary = Color(0xFF0B1742),
    primaryContainer = Color(0xFF1A2F73),
    onPrimaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFF7ADFD2),
    onSecondary = Color(0xFF042C28),
    secondaryContainer = Color(0xFF14453F),
    onSecondaryContainer = Color(0xFFC8F5EF),
    tertiary = Color(0xFFB8CAFF),
    onTertiary = Color(0xFF0E2758),
    tertiaryContainer = Color(0xFF213D79),
    onTertiaryContainer = Color(0xFFDCE6FF),
    background = Color(0xFF09121D),
    onBackground = Color(0xFFEAF1FF),
    surface = Color(0xFF111A27),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF243143),
    onSurfaceVariant = Color(0xFFC1CEE4),
    outline = Color(0xFF8290A8),
    outlineVariant = Color(0xFF2F3B4E),
    surfaceTint = Color(0xFFA9C0FF),
    scrim = Color(0xE6080C14),
    error = Color(0xFFFFB3B7),
    onError = Color(0xFF680014),
    errorContainer = Color(0xFF8C1D27),
    onErrorContainer = Color(0xFFFFDADD),
)

private val RelayTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.45.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.45.sp,
    ),
)

@Composable
fun RelayChatTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemIsDark = isSystemInDarkTheme()
    val darkTheme = remember(themeMode, systemIsDark) {
        themeMode.resolve(systemIsDark = systemIsDark)
    }
    val colorScheme = remember(darkTheme) {
        if (darkTheme) DarkColors else LightColors
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RelayTypography,
        content = content,
    )
}

fun AppThemeMode.applyToAppCompat() {
    val targetNightMode = when (this) {
        AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    }

    if (AppCompatDelegate.getDefaultNightMode() != targetNightMode) {
        AppCompatDelegate.setDefaultNightMode(targetNightMode)
    }
}
