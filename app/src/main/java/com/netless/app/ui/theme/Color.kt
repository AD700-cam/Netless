// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────
// Netless design tokens — dark theme.
//
// Every Compose screen draws from these; inline Color(0x…) literals outside
// this package are a CI failure (see the palette-gate step in build.yml).
// The ramp consolidates the near-duplicate greys that had accreted across
// screens (0x1E1E1E vs 0x1A1A1A, 0x8A8A8A vs 0x888888, …) into one value
// per visual role, so "card grey" or "secondary text" can be changed in
// exactly one place.
// ─────────────────────────────────────────────────────────────────────────

/** Controls whether the app renders in pure-black dark mode (true) or
 *  a softer dim mode (false). Consumed by NetlessTheme and individual screens.
 *  Defaults to true (full dark).
 */
val LocalNetlessDarkMode = compositionLocalOf { true }

// Surfaces (darkest → lightest)
val NetlessBlack = Color(0xFF000000)

/** Screen background behind cards/lists. */
val NetlessSurfaceDim = Color(0xFF0A0A0A)

/** Card / dialog surface. */
val NetlessDarkGray = Color(0xFF1A1A1A)

/** Elevated surface: input fields, chips, avatars. */
val NetlessMediumGray = Color(0xFF2A2A2A)

/** Borders, dividers, inactive track. */
val NetlessLightGray = Color(0xFF333333)

/** Stronger outline / disabled container. */
val NetlessOutlineGray = Color(0xFF4A4A4A)

/** Disabled content / faint hint. */
val NetlessDisabledGray = Color(0xFF555555)

// Text (dimmest → brightest)
/** Placeholder / hint text. */
val NetlessTextGray = Color(0xFF666666)

/** Secondary text: captions, labels, timestamps. */
val NetlessTextLightGray = Color(0xFF888888)

/** Long-form body text on dark dialogs. */
val NetlessTextPale = Color(0xFFCCCCCC)

val NetlessTextWhite = Color(0xFFFFFFFF)

// Card Colors (light card variant)
val NetlessCardBackground = Color(0xFFE8E8E8)
val NetlessCardText = Color(0xFF000000)
val NetlessCardSubtext = Color(0xFF4A4A4A)

// ─────────────────────────────────────────────────────────────────────────
// Zenvest Modern UI Tokens (Light & Dark Balanced Theme)
// ─────────────────────────────────────────────────────────────────────────
val ZenvestSkyTop = Color(0xFFD8ECF8)
val ZenvestSkyMid = Color(0xFFEBF5FA)
val ZenvestBgLight = Color(0xFFF8FAFC)
val ZenvestBgDark = Color(0xFF090D16)
val ZenvestSurfaceDark = Color(0xFF111827)
val ZenvestCardDark = Color(0xFF1E293B)

val ZenvestCardGlassWhite = Color(0xEBFFFFFF)
val ZenvestCardGlassDark = Color(0xE0172033)
val ZenvestCardBorder = Color(0x66FFFFFF)
val ZenvestCardBorderDark = Color(0x2E38BDF8)

val ZenvestAuroraRose = Color(0xFFFECDD3)
val ZenvestAuroraPeach = Color(0xFFFED7AA)
val ZenvestAuroraLavender = Color(0xFFDDD6FE)
val ZenvestAuroraCyan = Color(0xFFBAE6FD)
val ZenvestAuroraMint = Color(0xFFA7F3D0)

val ZenvestTextPrimary = Color(0xFF0F172A)
val ZenvestTextSecondary = Color(0xFF64748B)
val ZenvestTextMuted = Color(0xFF94A3B8)

val ZenvestPillBgLight = Color(0xEEFFFFFF)
val ZenvestPillBorderLight = Color(0x4DE2E8F0)
val ZenvestPillBgDark = Color(0x661E293B)
val ZenvestPillBorderDark = Color(0x4D334155)
val ZenvestActionCircleDark = Color(0xFF0F172A)

val ZenvestSheetBgLight = Color(0xFFFFFFFF)
val ZenvestSheetBgDark = Color(0xFF111827)
val ZenvestDividerLight = Color(0xFFF1F5F9)
val ZenvestDividerDark = Color(0xFF1E293B)

// Elevated / Chip surfaces
val NetlessSurfaceElevated = Color(0xFF222224)
val NetlessCardBorder = Color(0xFF2E2E32)
val NetlessChipBackground = Color(0xFF26262A)

// Accents
val NetlessAccentBlue = Color(0xFF4A90E2)
val NetlessAccentGreen = Color(0xFF4CAF50)
val NetlessBrandElectricGreen = Color(0xFF00E676)
val NetlessBrandCyan = Color(0xFF00E5FF)
val NetlessBrandEmerald = Color(0xFF10B981)

/** Bright green used as the light end of success gradients. */
val NetlessAccentGreenBright = Color(0xFF43E97B)

// ─────────────────────────────────────────────────────────────────────────
// Transaction status palette. One color per outcome, used identically in
// the history list, detail dialog and result screen so a status never
// changes meaning between screens.
// ─────────────────────────────────────────────────────────────────────────

/** SUCCESS — bank confirmed. */
val NetlessStatusSuccess = NetlessBrandEmerald

/** FAILED / declined, and destructive actions (delete, clear). */
val NetlessStatusError = Color(0xFFEF4444)

/** NEEDS_REVIEW / PENDING — user attention required. */
val NetlessStatusWarning = Color(0xFFF59E0B)

/** UNVERIFIED / CANCELLED — outcome unknown or nothing happened. Neutral:
 *  deliberately neither success-green nor failure-red. */
val NetlessStatusNeutral = Color(0xFF9E9E9E)

// Status Background Pills (15% opacity)
val NetlessStatusSuccessBg = Color(0x2610B981)
val NetlessStatusErrorBg = Color(0x26EF4444)
val NetlessStatusWarningBg = Color(0x26F59E0B)
val NetlessStatusNeutralBg = Color(0x269E9E9E)

/**
 * The single mapping from a [com.netless.app.data.TransactionStatus] string
 * to its display color. Replaces the byte-identical getStatusColor()
 * functions that had been copy-pasted into multiple screens.
 */
fun statusColor(status: String): Color = when (status.uppercase()) {
    "SUCCESS", "SUCCESSFUL", "COMPLETED" -> NetlessStatusSuccess
    "FAILED", "DECLINED" -> NetlessStatusError
    "PENDING", "NEEDS_REVIEW" -> NetlessStatusWarning
    else -> NetlessStatusNeutral // UNVERIFIED, CANCELLED, unknown
}

fun statusBackgroundColor(status: String): Color = when (status.uppercase()) {
    "SUCCESS", "SUCCESSFUL", "COMPLETED" -> NetlessStatusSuccessBg
    "FAILED", "DECLINED" -> NetlessStatusErrorBg
    "PENDING", "NEEDS_REVIEW" -> NetlessStatusWarningBg
    else -> NetlessStatusNeutralBg
}
