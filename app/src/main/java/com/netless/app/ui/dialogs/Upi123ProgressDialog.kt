// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.dialogs

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.netless.app.R
import com.netless.app.ui.theme.NetlessAccentGreen
import com.netless.app.ui.theme.NetlessDarkGray
import com.netless.app.ui.theme.NetlessLightGray
import com.netless.app.ui.theme.NetlessTextGray
import com.netless.app.ui.theme.NetlessTextLightGray
import com.netless.app.ui.theme.ZenvestSurfaceDark
import com.netless.app.ui.theme.ZenvestCardBorderDark
import com.netless.app.ui.theme.ZenvestTextMuted
import com.netless.app.ui.theme.ZenvestCardDark
import com.netless.app.ui.theme.LocalNetlessDarkMode
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary
import kotlinx.coroutines.delay

@Composable
fun Upi123ProgressDialog(
    isVisible: Boolean,
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    if (isVisible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Upi123ProgressDialogContent(
                showConfigurationOptions = showConfigurationOptions,
                onConfigured = onConfigured,
                onNotConfigured = onNotConfigured,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun Upi123ProgressDialogContent(
    showConfigurationOptions: Boolean = false,
    onConfigured: () -> Unit = {},
    onNotConfigured: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val isDark = LocalNetlessDarkMode.current

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) ZenvestSurfaceDark else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isDark) ZenvestCardBorderDark else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            color = if (isDark) {
                                Color(0xFF0284C7).copy(alpha = 0.22f)
                            } else {
                                Color(0xFFE0F2FE)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = UpiIcon,
                        contentDescription = stringResource(R.string.upi123_dlg_setup_icon_desc),
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (showConfigurationOptions) {
                        stringResource(R.string.upi123_dlg_setup_complete_title)
                    } else {
                        stringResource(R.string.upi123_dlg_setting_up_title)
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else ZenvestTextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (showConfigurationOptions) {
                        stringResource(R.string.upi123_dlg_setup_complete_message)
                    } else {
                        stringResource(R.string.upi123_dlg_ivr_triggered_message)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (showConfigurationOptions) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onNotConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) ZenvestCardDark else Color(0xFFF1F5F9),
                                contentColor = if (isDark) Color.White else ZenvestTextPrimary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.upi123_dlg_not_yet),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = onConfigured,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.upi123_dlg_yes),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp),
                        color = Color(0xFF0284C7),
                        trackColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.upi123_dlg_configuring),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = (if (isDark) ZenvestTextMuted else ZenvestTextSecondary).copy(alpha = alpha),
                        textAlign = TextAlign.Center
                    )

                    var showAlreadySetUp by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(2500)
                        showAlreadySetUp = true
                    }
                    if (showAlreadySetUp) {
                        Spacer(modifier = Modifier.height(18.dp))
                        OutlinedButton(
                            onClick = onConfigured,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.upi123_dlg_already_set_up),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.upi123_dlg_close),
                    tint = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Custom UPI Icon
val UpiIcon: ImageVector
    get() {
        return ImageVector.Builder(
            name = "upi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = androidx.compose.ui.graphics.SolidColor(NetlessAccentGreen),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
                strokeLineMiter = 1f,
                pathFillType = androidx.compose.ui.graphics.PathFillType.NonZero
            ) {
                moveTo(6f, 8f)
                verticalLineTo(16f)
                curveTo(6f, 18.2f, 7.8f, 20f, 10f, 20f)
                horizontalLineTo(14f)
                curveTo(16.2f, 20f, 18f, 18.2f, 18f, 16f)
                verticalLineTo(8f)
                moveTo(8f, 8f)
                verticalLineTo(16f)
                curveTo(8f, 17.1f, 8.9f, 18f, 10f, 18f)
                horizontalLineTo(14f)
                curveTo(15.1f, 18f, 16f, 17.1f, 16f, 16f)
                verticalLineTo(8f)
                moveTo(10f, 12f)
                horizontalLineTo(14f)
                moveTo(10f, 14f)
                horizontalLineTo(14f)
            }
        }.build()
    }
