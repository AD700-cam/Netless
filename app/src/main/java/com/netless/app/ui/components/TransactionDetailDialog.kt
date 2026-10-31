// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.netless.app.R
import com.netless.app.data.Transaction
import com.netless.app.ui.theme.NetlessDarkGray
import com.netless.app.ui.theme.NetlessLightGray
import com.netless.app.ui.theme.NetlessMediumGray
import com.netless.app.ui.theme.NetlessStatusError
import com.netless.app.ui.theme.NetlessSurfaceDim
import com.netless.app.ui.theme.NetlessTextLightGray
import com.netless.app.ui.theme.NetlessTextPale
import com.netless.app.ui.theme.LocalNetlessAccentTheme
import com.netless.app.ui.theme.LocalNetlessDarkMode
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary
import com.netless.app.ui.theme.statusColor
import com.netless.app.utils.CurrencyFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val clipboardManager = LocalClipboardManager.current
    val accent = LocalNetlessAccentTheme.current
    val statusColor = statusColor(transaction.status)
    val isDark = LocalNetlessDarkMode.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isDark) NetlessSurfaceDim else Color.White,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isDark) NetlessLightGray else Color(0xFFCBD5E1))
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.detail_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else ZenvestTextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) NetlessDarkGray else Color(0xFFF1F5F9))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.detail_close),
                            tint = if (isDark) NetlessTextLightGray else ZenvestTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Large amount
                Text(
                    text = stringResource(R.string.amount_rupees, CurrencyFormat.inr(transaction.amount)),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent.headerGradientStart,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = transaction.status.lowercase()
                            .replaceFirstChar { it.uppercase() },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }

                // Non-success outcomes get a plain-language explanation
                statusExplainerText(transaction.status)?.let { explainer ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = explainer,
                        fontSize = 12.sp,
                        color = NetlessTextPale,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Detail card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isDark) NetlessDarkGray else Color(0xFFF8FAFC),
                    border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Bank reference — the number from the bank's own SMS,
                        // the one a user would actually quote back to their
                        // bank in a dispute. Shown first and only when the
                        // bank supplied one (a PENDING row has none yet).
                        if (!transaction.bankRef.isNullOrEmpty()) {
                            DetailRow(
                                label = stringResource(R.string.label_bank_reference),
                                value = transaction.bankRef,
                                onCopy = { clipboardManager.setText(AnnotatedString(transaction.bankRef)) }
                            )
                            DetailDivider()
                        }

                        // Bank
                        DetailRow(
                            label = stringResource(R.string.label_bank),
                            value = transaction.bankName,
                            onCopy = { clipboardManager.setText(AnnotatedString(transaction.bankName)) }
                        )

                        // Recipient
                        if (!transaction.recipientName.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.detail_label_recipient),
                                value = transaction.recipientName,
                                onCopy = { clipboardManager.setText(AnnotatedString(transaction.recipientName)) }
                            )
                        }

                        // Phone
                        if (!transaction.phoneNumber.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.detail_label_phone_number),
                                value = transaction.phoneNumber,
                                onCopy = { clipboardManager.setText(AnnotatedString(transaction.phoneNumber)) }
                            )
                        }

                        // UPI ID
                        if (!transaction.upiId.isNullOrEmpty()) {
                            DetailDivider()
                            DetailRow(
                                label = stringResource(R.string.label_upi_id),
                                value = transaction.upiId,
                                onCopy = { clipboardManager.setText(AnnotatedString(transaction.upiId)) }
                            )
                        }

                        DetailDivider()

                        // Date & Time
                        DetailRow(
                            label = stringResource(R.string.label_date_time),
                            value = formatFullDate(transaction.timestamp),
                            onCopy = {
                                clipboardManager.setText(
                                    AnnotatedString(formatFullDate(transaction.timestamp))
                                )
                            }
                        )
                    }
                }

                // Privacy-safe bank summary (raw SMS bodies are not stored)
                if (transaction.smsExcerpt.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = NetlessDarkGray,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.detail_bank_confirmation),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = NetlessTextLightGray
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = transaction.smsExcerpt,
                                fontSize = 12.sp,
                                color = NetlessTextLightGray,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Delete button
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NetlessStatusError.copy(alpha = 0.1f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { showDeleteConfirm = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.detail_delete_transaction),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = NetlessStatusError
                        )
                    }
                }
            }
        }
    }

    // Deletion is permanent, so confirm before removing the record.
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = NetlessDarkGray,
            titleContentColor = Color.White,
            textContentColor = NetlessTextPale,
            title = {
                Text(
                    stringResource(R.string.detail_delete_confirm_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    stringResource(R.string.detail_delete_confirm_body),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = NetlessStatusError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = NetlessTextLightGray)
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    val isDark = LocalNetlessDarkMode.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = NetlessTextLightGray
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else ZenvestTextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(NetlessMediumGray)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onCopy() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.action_copy),
                modifier = Modifier.size(14.dp),
                tint = NetlessTextLightGray
            )
        }
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 0.5.dp,
        color = NetlessMediumGray
    )
}

/** Localised plain-language meaning of a non-success lifecycle status. */
@androidx.compose.runtime.Composable
private fun statusExplainerText(status: String): String? = when (status.uppercase()) {
    "PENDING" -> androidx.compose.ui.res.stringResource(com.netless.app.R.string.status_explainer_pending)
    "UNVERIFIED" -> androidx.compose.ui.res.stringResource(com.netless.app.R.string.status_explainer_unverified)
    "NEEDS_REVIEW" -> androidx.compose.ui.res.stringResource(com.netless.app.R.string.status_explainer_needs_review)
    "CANCELLED" -> androidx.compose.ui.res.stringResource(com.netless.app.R.string.status_explainer_cancelled)
    "FAILED" -> androidx.compose.ui.res.stringResource(com.netless.app.R.string.status_explainer_failed)
    else -> null
}

private fun formatFullDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}
