// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.netless.app.NetlessApplication
import com.netless.app.data.SettingsRepository
import com.netless.app.utils.findComponentActivity

private val DarkColorScheme = darkColorScheme(
    primary = NetlessTextWhite,
    secondary = NetlessAccentBlue,
    tertiary = NetlessAccentGreen,
    background = ZenvestBgDark,
    surface = ZenvestSurfaceDark,
    onBackground = NetlessTextWhite,
    onSurface = NetlessTextWhite
)

private val LightColorScheme = lightColorScheme(
    primary = ZenvestTextPrimary,
    secondary = NetlessAccentBlue,
    tertiary = NetlessBrandEmerald,
    background = ZenvestBgLight,
    surface = Color.White,
    onBackground = ZenvestTextPrimary,
    onSurface = ZenvestTextPrimary
)

/**
 * NetlessTheme provides both light (Zenvest-inspired modern aesthetic with
 * ethereal sky gradients and frosted glass cards) and balanced dark mode,
 * dynamically controlled via Settings or the optional darkTheme override.
 */
@Composable
fun NetlessTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember(context) {
        val app = context.applicationContext as? NetlessApplication
        app?.settingsRepository ?: SettingsRepository(context.applicationContext)
    }
    val settings by settingsRepository.settingsFlow.collectAsState()
    val isDark = darkTheme ?: settings.darkMode

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findComponentActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            if (isDark) {
                window.statusBarColor = android.graphics.Color.parseColor("#090D16")
                window.navigationBarColor = android.graphics.Color.parseColor("#090D16")
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            } else {
                window.statusBarColor = android.graphics.Color.parseColor("#D8ECF8")
                window.navigationBarColor = android.graphics.Color.WHITE
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    CompositionLocalProvider(LocalNetlessDarkMode provides isDark) {
        MaterialTheme(
            colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
