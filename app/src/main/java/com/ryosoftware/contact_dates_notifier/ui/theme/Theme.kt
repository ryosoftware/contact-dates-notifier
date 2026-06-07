package com.ryosoftware.contact_dates_notifier.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import com.ryosoftware.contact_dates_notifier.R
import com.ryosoftware.contact_dates_notifier.data.ApplicationPreferences

@Composable
fun BirthdaysNotifierTheme(
    activity: Activity,
    content: @Composable () -> Unit
) {
    val themeStyle by ApplicationPreferences.observeString(activity, ApplicationPreferences.THEME_STYLE_KEY, ApplicationPreferences.THEME_STYLE_DEFAULT)
        .collectAsState(initial = ApplicationPreferences.getString(activity, ApplicationPreferences.THEME_STYLE_KEY, ApplicationPreferences.THEME_STYLE_DEFAULT))
    val blackBackground by ApplicationPreferences.observeBoolean(activity, ApplicationPreferences.PURE_BLACK_BACKGROUND_KEY, ApplicationPreferences.PURE_BLACK_BACKGROUND_DEFAULT)
        .collectAsState(initial = ApplicationPreferences.getBoolean(activity, ApplicationPreferences.PURE_BLACK_BACKGROUND_KEY, ApplicationPreferences.PURE_BLACK_BACKGROUND_DEFAULT))
    val useSystemAccent by ApplicationPreferences.observeBoolean(activity, ApplicationPreferences.USE_SYSTEM_ACCENT_KEY, ApplicationPreferences.USE_SYSTEM_ACCENT_DEFAULT)
        .collectAsState(initial = ApplicationPreferences.getBoolean(activity, ApplicationPreferences.USE_SYSTEM_ACCENT_KEY, ApplicationPreferences.USE_SYSTEM_ACCENT_DEFAULT))

    val isDarkTheme =
        themeStyle == ApplicationPreferences.THEME_DARK ||
                (themeStyle == ApplicationPreferences.THEME_SYSTEM && isSystemInDarkTheme())

    var colorScheme: ColorScheme = when {
        isDarkTheme -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(activity) else darkColorScheme()
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(activity) else lightColorScheme()
    }

    if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.S) || (!useSystemAccent)) {
        colorScheme = colorScheme.copy(
            primary = colorResource(R.color.primary),
            onPrimary = colorResource(R.color.on_primary),

            secondary = colorResource(R.color.secondary),
            onSecondary = colorResource(R.color.on_secondary),

            primaryContainer = colorResource(R.color.primary),
            onPrimaryContainer = colorResource(R.color.on_primary),

            secondaryContainer = colorResource(R.color.secondary),
            onSecondaryContainer = colorResource(R.color.on_secondary)
        )
    }

    if (blackBackground && isDarkTheme) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceTint = Color.Black,
            surfaceContainer = Color.Black,
            surfaceContainerHigh = Color.Black,
            surfaceContainerHighest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceBright = Color.Black,
            surfaceDim = Color.Black,
        )
    }

    val primaryColor = colorScheme.primary.toArgb()

    SideEffect {
        val window = activity.window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = android.graphics.Color.rgb(
            maxOf(0, android.graphics.Color.red(primaryColor)),
            maxOf(0, android.graphics.Color.green(primaryColor) - 0x15),
            maxOf(0, android.graphics.Color.blue(primaryColor) - 0x15)
        )
    }

    MaterialTheme(colorScheme = colorScheme) {
        content()
    }
}
