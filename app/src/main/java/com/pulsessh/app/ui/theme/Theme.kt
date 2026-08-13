package com.pulsessh.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Fallback dark scheme, built from the hand-tuned terminal palette in `Color.kt`. */
private val pulseSshDarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = darkPrimary,
        onPrimary = darkOnPrimary,
        primaryContainer = darkPrimaryContainer,
        onPrimaryContainer = darkOnPrimaryContainer,
        secondary = darkSecondary,
        onSecondary = darkOnSecondary,
        tertiary = darkTertiary,
        onTertiary = darkOnTertiary,
        background = darkBackground,
        onBackground = darkOnBackground,
        surface = darkSurface,
        onSurface = darkOnSurface,
        surfaceVariant = darkSurfaceVariant,
        onSurfaceVariant = darkOnSurfaceVariant,
        outline = darkOutline,
        error = darkError,
        onError = darkOnError,
    )

/** Fallback light scheme, built from the hand-tuned terminal palette in `Color.kt`. */
private val pulseSshLightColorScheme: ColorScheme =
    lightColorScheme(
        primary = lightPrimary,
        onPrimary = lightOnPrimary,
        primaryContainer = lightPrimaryContainer,
        onPrimaryContainer = lightOnPrimaryContainer,
        secondary = lightSecondary,
        onSecondary = lightOnSecondary,
        tertiary = lightTertiary,
        onTertiary = lightOnTertiary,
        background = lightBackground,
        onBackground = lightOnBackground,
        surface = lightSurface,
        onSurface = lightOnSurface,
        surfaceVariant = lightSurfaceVariant,
        onSurfaceVariant = lightOnSurfaceVariant,
        outline = lightOutline,
        error = lightError,
        onError = lightOnError,
    )

/**
 * Applies the PulseSSH Material 3 theme to [content].
 *
 * Colour resolution, in order:
 * 1. On Android 12 (API 31) and above, when [dynamicColor] is true, the wallpaper-derived scheme
 *    is used so the app matches the rest of the system.
 * 2. Otherwise the hand-written terminal scheme is used. Callers that must be visually stable -
 *    screenshot tests, previews, anything compared pixel by pixel - should pass
 *    `dynamicColor = false`, because the dynamic scheme differs from device to device.
 *
 * Nothing here touches the system bar colours: the activity draws edge to edge and the bars are
 * left transparent, with insets handled by the Scaffolds inside.
 *
 * @param darkTheme whether to use the dark scheme; follows the system setting by default.
 * @param dynamicColor whether wallpaper-derived colour is allowed on supported devices.
 * @param content the UI to draw inside the theme.
 */
@Composable
fun PulseSshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) {
            pulseSshDarkColorScheme
        } else {
            pulseSshLightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = pulseSshTypography,
        content = content,
    )
}
