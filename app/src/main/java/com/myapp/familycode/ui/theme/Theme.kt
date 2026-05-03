package com.myapp.familycode.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFF90CAFF),
    onSecondary = Color(0xFF003350),
    secondaryContainer = Color(0xFF004A72),
    onSecondaryContainer = Color(0xFFCDE5FF),
    tertiary = Color(0xFFFFB4AB),
    onTertiary = Color(0xFF690005),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE3E2E6),
    surfaceContainer = Color(0xFF1A1C21),
    surfaceContainerLow = Color(0xFF131519),
    surfaceContainerHigh = Color(0xFF22252B),
    background = Color(0xFF090C11),
    onBackground = Color(0xFFE3E2E6),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0062A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF0062A1),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE5FF),
    onSecondaryContainer = Color(0xFF001D35),
    tertiary = Color(0xFF9C4141),
    onTertiary = Color.White,
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF191C20),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerLow = Color(0xFFF2F4FA),
    surfaceContainerHigh = Color(0xFFE1E3EA),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF191C20),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

@Composable
fun FamilyCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
