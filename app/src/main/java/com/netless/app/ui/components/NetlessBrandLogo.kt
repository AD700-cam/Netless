// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netless.app.R
import com.netless.app.ui.theme.InterFontFamily
import com.netless.app.ui.theme.NetlessBrandFontFamily
import com.netless.app.ui.theme.ZenvestTextPrimary

/**
 * Netless signature brand logo: identical to the application launcher icon
 */
@Composable
fun NetlessLogo(
    size: Dp = 38.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.ic_netless_logo),
        contentDescription = "Netless Logo",
        modifier = modifier
            .size(size)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(10.dp), spotColor = Color(0x3300E5FF))
            .clip(RoundedCornerShape(10.dp))
    )
}

/**
 * Netless brand wordmark matching the official brand design:
 * "NET" in white / dark primary + "LESS" in emerald green (#10B981)
 * with the "— Offline UPI Payments —" tagline.
 */
@Composable
fun NetlessBrandWordmark(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    showSubtitle: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showLogo) {
            NetlessLogo(size = 38.dp)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NET",
                    fontFamily = NetlessBrandFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = if (isDark) Color.White else ZenvestTextPrimary
                )
                Text(
                    text = "LESS",
                    fontFamily = NetlessBrandFontFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = Color(0xFF10B981)
                )
            }
            if (showSubtitle) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "—",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Offline UPI Payments",
                        fontFamily = InterFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.4.sp,
                        color = if (isDark) Color(0xCCFFFFFF) else Color(0x99000000)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "—",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}
