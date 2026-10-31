// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class NetlessAccentTheme(
    val primary: Color,
    val primaryDark: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
    val accent: Color,
    val accentLight: Color
)

val BlueAccentTheme = NetlessAccentTheme(
    primary = Color(0xFF3B82F6),
    primaryDark = Color(0xFF1D4ED8),
    headerGradientStart = Color(0xFF4F46E5),
    headerGradientEnd = Color(0xFF2563EB),
    accent = Color(0xFF38BDF8),
    accentLight = Color(0xFF60A5FA)
)

val EmeraldAccentTheme = NetlessAccentTheme(
    primary = Color(0xFF10B981),
    primaryDark = Color(0xFF047857),
    headerGradientStart = Color(0xFF10B981),
    headerGradientEnd = Color(0xFF059669),
    accent = Color(0xFF00E676),
    accentLight = Color(0xFF34D399)
)

val LocalNetlessAccentTheme = compositionLocalOf { BlueAccentTheme }
