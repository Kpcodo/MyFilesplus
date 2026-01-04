package com.mfp.filemanager.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DeepBlue,
    secondary = SoftTeal,
    tertiary = Pink80,
    background = DarkBackground,
    surface = SurfaceDark,
    onPrimary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite
)

private val AmoledColorScheme = darkColorScheme(
    primary = DeepBlue,
    secondary = SoftTeal,
    tertiary = Pink80,
    background = androidx.compose.ui.graphics.Color.Black,
    surface = androidx.compose.ui.graphics.Color.Black, // Or very dark grey if preferred for cards
    onPrimary = TextWhite,
    onBackground = TextWhite,
    onSurface = TextWhite,
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF121212) // Slightly lighter for cards/input fields
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
    
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun FileManagerTheme(
    themeMode: Int = 0, // 0=System, 1=Light, 2=Dark, 3=AMOLED
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    accentColor: Int = 0xFF6650a4.toInt(), // Default Purple40
    content: @Composable () -> Unit
) {
    val effectiveDarkTheme = when (themeMode) {
        0 -> darkTheme // System
        1 -> false // Light
        2, 3 -> true // Dark, AMOLED
        else -> darkTheme
    }

    val colorScheme = when {
        themeMode == 3 -> {
             // AMOLED Mode: Force Black background
             val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                 dynamicDarkColorScheme(LocalContext.current)
             } else {
                 DarkColorScheme
             }
             baseScheme.copy(
                 background = androidx.compose.ui.graphics.Color.Black,
                 surface = androidx.compose.ui.graphics.Color.Black,
                 surfaceVariant = androidx.compose.ui.graphics.Color(0xFF121212),
                 primary = if (accentColor != 0xFF6650a4.toInt()) androidx.compose.ui.graphics.Color(accentColor) else baseScheme.primary,
                 secondary = if (accentColor != 0xFF6650a4.toInt()) androidx.compose.ui.graphics.Color(accentColor) else baseScheme.secondary
             )
        }
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            
            if (accentColor != 0xFF6650a4.toInt()) {
                val userAccent = androidx.compose.ui.graphics.Color(accentColor)
                baseScheme.copy(primary = userAccent, secondary = userAccent, tertiary = Pink80) // Keeping tertiary simplified for now
            } else {
                baseScheme
            }
        }
        effectiveDarkTheme -> DarkColorScheme.copy(primary = androidx.compose.ui.graphics.Color(accentColor), secondary = androidx.compose.ui.graphics.Color(accentColor))
        else -> LightColorScheme.copy(primary = androidx.compose.ui.graphics.Color(accentColor), secondary = androidx.compose.ui.graphics.Color(accentColor))
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDarkTheme && themeMode != 3
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
