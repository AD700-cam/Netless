// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

@file:Suppress("LongMethod", "MaxLineLength", "NoWildcardImports")

package com.netless.app.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netless.app.MainActivity
import com.netless.app.R
import com.netless.app.data.SettingsRepository
import com.netless.app.ui.theme.NetlessSurfaceDim
import com.netless.app.ui.theme.NetlessTheme
import com.netless.app.ui.theme.ZenvestBgLight
import com.netless.app.ui.theme.ZenvestTextMuted
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary

/**
 * Custom 404 / Fallback Screen.
 *
 * Rendered when a deep link, route, or transaction record cannot be found.
 * Provides a graceful, reassuring recovery back to the main dashboard.
 */
class FallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Netless)

        val isDarkMode = try {
            SettingsRepository(applicationContext).getSettings().darkMode
        } catch (_: Exception) {
            false
        }

        setContent {
            NetlessTheme(darkTheme = isDarkMode) {
                FallbackScreen(
                    isDark = isDarkMode,
                    onReturnHome = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun FallbackScreen(
    isDark: Boolean,
    onReturnHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) NetlessSurfaceDim else ZenvestBgLight)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0x1A38BDF8) else Color(0xFFE0F2FE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Item or Page Not Found",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "The transaction record or destination you were looking for is unavailable or has been archived.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onReturnHome,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Return to Dashboard",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
