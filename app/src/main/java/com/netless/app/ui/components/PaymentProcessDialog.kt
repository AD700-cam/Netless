// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.netless.app.ui.theme.NetlessBrandElectricGreen
import com.netless.app.ui.theme.NetlessBrandEmerald
import com.netless.app.ui.theme.LocalNetlessDarkMode
import com.netless.app.ui.theme.ZenvestAuroraCyan
import com.netless.app.ui.theme.ZenvestBgDark
import com.netless.app.ui.theme.ZenvestTextMuted
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary

/**
 * Pre-call process pop-out dialog showing the user that:
 * "Most transactions are completed in 40 seconds on call dialer"
 * along with the clear step-by-step breakdown of how the offline payment executes.
 */
@Composable
fun PaymentProcessDialog(
    phoneNumber: String,
    amount: String,
    contactName: String? = null,
    bankName: String = "Punjab National Bank",
    onStartCall: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalNetlessDarkMode.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = if (isDark) Color(0x6600E5FF) else Color(0x330284C7)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = if (isDark) {
                            Brush.verticalGradient(
                                listOf(Color(0xFF172338), Color(0xFF0F172A), ZenvestBgDark)
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC), Color(0xFFF1F5F9))
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color(0x4038BDF8) else Color(0x40CBD5E1),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ─── BADGE PILL: 40s Offline Rail ───
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isDark) Color(0x2610B981) else Color(0x1F059669))
                        .border(
                            1.dp,
                            if (isDark) NetlessBrandElectricGreen.copy(alpha = 0.5f) else Color(0x40059669),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NetlessBrandElectricGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ 40s OFFLINE TRANSFER RAIL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = if (isDark) NetlessBrandEmerald else Color(0xFF059669)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ─── PROMINENT HEADLINE ───
                Text(
                    text = "Most of the transactions are completed in 40 seconds on call dialer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else ZenvestTextPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 26.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Your device dials the secure NPCI 123Pay rail. Phone number & amount route automatically.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // ─── TRANSACTION CARD ───
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isDark) Color(0x4D1E293B) else Color(0xF2FFFFFF))
                        .border(
                            1.dp,
                            if (isDark) Color(0x33475569) else Color(0x40CBD5E1),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "₹$amount",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else ZenvestTextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val recipientDisplay = if (!contactName.isNullOrBlank()) {
                            "$contactName ($phoneNumber)"
                        } else {
                            phoneNumber
                        }

                        Text(
                            text = "Transfer to: $recipientDisplay",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) ZenvestAuroraCyan else Color(0xFF0284C7)
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = "Debited from: $bankName",
                            fontSize = 11.5.sp,
                            color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ─── STEP BY STEP 40-SECOND BREAKDOWN ───
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProcessStepItem(
                        stepNumber = "1",
                        timeRange = "0 – 4s",
                        title = "Auto-Dialing 08045163666",
                        desc = "Connects to official NPCI IVR with zero data.",
                        isDark = isDark
                    )

                    ProcessStepItem(
                        stepNumber = "2",
                        timeRange = "4 – 15s",
                        title = "Automated DTMF Routing",
                        desc = "Selects transfer and inputs payee number & amount.",
                        isDark = isDark
                    )

                    ProcessStepItem(
                        stepNumber = "3",
                        timeRange = "15 – 25s",
                        title = "Paced Details Confirmation",
                        desc = "Bank verifies recipient; tone 1 confirms details.",
                        isDark = isDark
                    )

                    ProcessStepItem(
                        stepNumber = "4",
                        timeRange = "25 – 40s",
                        title = "Enter UPI PIN on Call Keypad",
                        desc = "Listen to call prompt and enter your secret UPI PIN.",
                        isDark = isDark,
                        isLast = true
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ─── SECURITY NOTICE ───
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0x330F172A) else Color(0x20F1F5F9))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Secure",
                        tint = if (isDark) NetlessBrandEmerald else Color(0xFF059669),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "100% Offline. Your UPI PIN is entered directly on the live call, never seen by the app.",
                        fontSize = 11.sp,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // ─── ACTION BUTTONS ───
                Button(
                    onClick = {
                        onDismiss()
                        onStartCall()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0x6600E5FF)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color(0xFF0284C7) else Color(0xFF0284C7)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Call & Transfer (40s)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessStepItem(
    stepNumber: String,
    timeRange: String,
    title: String,
    desc: String,
    isDark: Boolean,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0))
                    .border(
                        1.dp,
                        if (isDark) ZenvestAuroraCyan.copy(alpha = 0.6f) else Color(0xFF0284C7),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) ZenvestAuroraCyan else Color(0xFF0284C7)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(14.dp)
                        .background(if (isDark) Color(0x33475569) else Color(0x40CBD5E1))
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else ZenvestTextPrimary
                )
                Text(
                    text = timeRange,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) NetlessBrandEmerald else Color(0xFF059669)
                )
            }
            Text(
                text = desc,
                fontSize = 11.sp,
                color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}
