// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netless.app.R
import com.netless.app.data.Transaction
import com.netless.app.ui.components.TransactionDetailDialog
import com.netless.app.ui.theme.BlueAccentTheme
import com.netless.app.ui.theme.NetlessCardBorder
import com.netless.app.ui.theme.NetlessDarkGray
import com.netless.app.ui.theme.NetlessMediumGray
import com.netless.app.ui.theme.NetlessStatusError
import com.netless.app.ui.theme.NetlessTextLightGray
import com.netless.app.ui.theme.NetlessTheme
import com.netless.app.ui.theme.LocalNetlessAccentTheme
import com.netless.app.ui.theme.LocalNetlessDarkMode
import com.netless.app.ui.theme.ZenvestBgDark
import com.netless.app.ui.theme.ZenvestBgLight
import com.netless.app.ui.theme.ZenvestCardDark
import com.netless.app.ui.theme.ZenvestDividerDark
import com.netless.app.ui.theme.ZenvestDividerLight
import com.netless.app.ui.theme.ZenvestPillBorderLight
import com.netless.app.ui.theme.ZenvestSkyMid
import com.netless.app.ui.theme.ZenvestSkyTop
import com.netless.app.ui.theme.ZenvestSurfaceDark
import com.netless.app.ui.theme.ZenvestTextMuted
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary
import com.netless.app.ui.theme.statusColor
import com.netless.app.utils.CurrencyFormat
import com.netless.app.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Utility functions
fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}

fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("hh:mm a", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}

@androidx.annotation.StringRes
fun statusLabelRes(status: String): Int = when (status.uppercase()) {
    "SUCCESS", "SUCCESSFUL", "COMPLETED" -> R.string.status_label_success
    "UNVERIFIED" -> R.string.status_label_unverified
    "NEEDS_REVIEW" -> R.string.status_label_needs_review
    "CANCELLED" -> R.string.status_label_cancelled
    "FAILED", "DECLINED" -> R.string.status_label_failed
    else -> R.string.status_label_pending
}

private fun isSameDay(c1: Calendar, c2: Calendar): Boolean =
    c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)

class TransactionHistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Netless)
        setContent {
            CompositionLocalProvider(LocalNetlessAccentTheme provides BlueAccentTheme) {
                NetlessTheme {
                    TransactionHistoryScreen(
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBackClick: () -> Unit
) {
    val transactionViewModel: TransactionViewModel = viewModel()

    // State
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    // Collect data
    val allTransactions by transactionViewModel.loadAllTransactions().collectAsState(initial = emptyList())
    val isLoading by transactionViewModel.isLoading.collectAsState()
    val error by transactionViewModel.error.collectAsState()

    // Filter by search and status
    val filteredTransactions = remember(allTransactions, searchQuery, selectedFilter) {
        val matchesSearch = if (searchQuery.isEmpty()) {
            allTransactions
        } else {
            allTransactions.filter { transaction ->
                transaction.recipientName?.contains(searchQuery, ignoreCase = true) == true ||
                    transaction.phoneNumber?.contains(searchQuery, ignoreCase = true) == true ||
                    transaction.bankName.contains(searchQuery, ignoreCase = true) ||
                    transaction.amount.contains(searchQuery, ignoreCase = true)
            }
        }
        val now = System.currentTimeMillis()
        when (selectedFilter) {
            "TODAY" -> {
                val oneDayAgo = now - 24 * 60 * 60 * 1000L
                matchesSearch.filter { it.timestamp >= oneDayAgo }
            }
            "WEEK" -> {
                val oneWeekAgo = now - 7 * 24 * 60 * 60 * 1000L
                matchesSearch.filter { it.timestamp >= oneWeekAgo }
            }
            "SUCCESS" -> matchesSearch.filter {
                val s = it.status.uppercase()
                s == "SUCCESS" || s == "SUCCESSFUL" || s == "COMPLETED"
            }
            "PENDING" -> matchesSearch.filter {
                val s = it.status.uppercase()
                s == "PENDING" || s == "NEEDS_REVIEW"
            }
            "FAILED" -> matchesSearch.filter {
                val s = it.status.uppercase()
                s == "FAILED" || s == "DECLINED" || s == "CANCELLED"
            }
            else -> matchesSearch
        }
    }

    // Group by date
    val groupToday = stringResource(R.string.history_group_today)
    val groupYesterday = stringResource(R.string.history_group_yesterday)
    val groupThisWeek = stringResource(R.string.history_group_this_week)
    val groupedTransactions = remember(filteredTransactions) {
        val today = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        val dateFormat = SimpleDateFormat("dd MMM", Locale("en", "IN"))

        val grouped = linkedMapOf<String, List<Transaction>>()
        filteredTransactions
            .sortedByDescending { it.timestamp }
            .groupBy { transaction ->
                val txCal = Calendar.getInstance().apply { timeInMillis = transaction.timestamp }
                when {
                    isSameDay(txCal, today) -> groupToday
                    isSameDay(txCal, yesterday) -> groupYesterday
                    txCal.after(weekAgo) -> groupThisWeek
                    else -> dateFormat.format(Date(transaction.timestamp))
                }
            }
            .forEach { (key, value) -> grouped[key] = value }
        grouped
    }

    val accent = LocalNetlessAccentTheme.current
    val isDark = LocalNetlessDarkMode.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDark) ZenvestBgDark else ZenvestBgLight
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isDark) ZenvestBgDark else ZenvestBgLight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .align(Alignment.Center)
                    .background(if (isDark) ZenvestBgDark else ZenvestBgLight)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ═══ COMPACT HEADER ═══
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = if (isDark) {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        ZenvestSurfaceDark,
                                        ZenvestCardDark
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(
                                        ZenvestSkyTop,
                                        ZenvestSkyMid
                                    )
                                )
                            }
                        )
                        .border(
                            1.dp,
                            if (isDark) Color(0x33FFFFFF) else Color(0x66FFFFFF),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color.White.copy(alpha = 0.22f) else Color(0x1F0F172A))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { onBackClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.history_back),
                                tint = if (isDark) Color.White else ZenvestTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = stringResource(R.string.history_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else ZenvestTextPrimary,
                            letterSpacing = 0.3.sp,
                            modifier = Modifier.weight(1f)
                        )

                        // Search button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showSearchBar) {
                                        if (isDark) Color.White.copy(alpha = 0.35f) else Color(0x330F172A)
                                    } else {
                                        if (isDark) Color.White.copy(alpha = 0.22f) else Color(0x1F0F172A)
                                    }
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { showSearchBar = !showSearchBar },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.history_search),
                                tint = if (isDark) Color.White else ZenvestTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ═══ SEARCH BAR ═══
                AnimatedVisibility(
                    visible = showSearchBar,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.history_search_placeholder),
                                color = if (isDark) NetlessTextLightGray else ZenvestTextMuted,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isDark) NetlessTextLightGray else ZenvestTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = if (isDark) NetlessTextLightGray else ZenvestTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            focusedBorderColor = if (isDark) accent.primary else ZenvestTextPrimary,
                            unfocusedBorderColor = if (isDark) Color.Transparent else ZenvestPillBorderLight,
                            focusedContainerColor = if (isDark) NetlessDarkGray else Color.White,
                            unfocusedContainerColor = if (isDark) NetlessDarkGray else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }

                // ═══ FILTER CHIPS ═══
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf(
                        "ALL" to stringResource(R.string.history_filter_all),
                        "TODAY" to "Today",
                        "WEEK" to "This Week",
                        "SUCCESS" to stringResource(R.string.history_filter_success),
                        "PENDING" to stringResource(R.string.history_filter_pending),
                        "FAILED" to stringResource(R.string.history_filter_failed)
                    )
                    filters.forEach { (key, label) ->
                        val isSelected = selectedFilter == key
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) {
                                        if (isDark) accent.headerGradientStart else ZenvestTextPrimary
                                    } else {
                                        if (isDark) NetlessDarkGray else Color.White
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) {
                                        if (isDark) accent.accent else ZenvestTextPrimary
                                    } else {
                                        if (isDark) NetlessCardBorder else ZenvestPillBorderLight
                                    },
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedFilter = key }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else if (isDark) NetlessTextLightGray else ZenvestTextSecondary
                            )
                        }
                    }
                }

                // ═══ TRANSACTION LIST ═══
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = accent.headerGradientStart,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                    error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) NetlessMediumGray else Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = NetlessStatusError,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.error_unexpected),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else ZenvestTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(onClick = { transactionViewModel.refresh() }) {
                                    Text(
                                        stringResource(R.string.home_retry),
                                        color = accent.primary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    filteredTransactions.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(if (isDark) NetlessMediumGray else Color(0xFFE2E8F0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = if (isDark) NetlessTextLightGray else ZenvestTextSecondary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(
                                        if (searchQuery.isNotEmpty() || selectedFilter != "ALL") {
                                            R.string.history_no_matches
                                        } else {
                                            R.string.home_no_transactions
                                        }
                                    ),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else ZenvestTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        if (searchQuery.isNotEmpty() || selectedFilter != "ALL") {
                                            R.string.history_try_search
                                        } else {
                                            R.string.history_empty_desc
                                        }
                                    ),
                                    fontSize = 13.sp,
                                    color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
                                )
                            }
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                        ) {
                            groupedTransactions.forEach { (dateLabel, transactions) ->
                                // Date section header
                                item(key = "header_$dateLabel") {
                                    Text(
                                        text = dateLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                }

                                // Transaction items with dividers
                                itemsIndexed(
                                    items = transactions,
                                    key = { _, tx -> tx.transactionId }
                                ) { index, transaction ->
                                    TransactionHistoryItem(
                                        transaction = transaction,
                                        onClick = { selectedTransaction = transaction }
                                    )
                                    if (index < transactions.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(start = 52.dp),
                                            thickness = 0.5.dp,
                                            color = if (isDark) ZenvestDividerDark else ZenvestDividerLight
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail dialog
    selectedTransaction?.let { transaction ->
        TransactionDetailDialog(
            transaction = transaction,
            onDismiss = { selectedTransaction = null },
            onDelete = {
                transactionViewModel.deleteTransaction(transaction)
                selectedTransaction = null
            }
        )
    }
}

// ═══ TRANSACTION ROW — flat, minimal, GPay-style ═══

@Composable
private fun TransactionHistoryItem(
    transaction: Transaction,
    onClick: () -> Unit
) {
    val isDark = LocalNetlessDarkMode.current
    val accent = LocalNetlessAccentTheme.current
    val displayName = transaction.recipientName?.takeIf { it.isNotEmpty() }
        ?: transaction.phoneNumber?.takeIf { it.isNotEmpty() }
        ?: stringResource(R.string.unknown_recipient)
    val initial = displayName.first().uppercaseChar()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransactionAvatar(initial)

        Spacer(modifier = Modifier.width(12.dp))

        // Name + time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else ZenvestTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatTime(transaction.timestamp),
                fontSize = 13.sp,
                color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Amount + status pill: the outcome must be readable at a glance, so
        // SUCCESS and UNVERIFIED never look identical in the list.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.amount_rupees, CurrencyFormat.inr(transaction.amount)),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else ZenvestTextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusPill(transaction.status)
        }
    }
}

@Composable
private fun TransactionAvatar(initial: Char) {
    val isDark = LocalNetlessDarkMode.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isDark) NetlessDarkGray else Color(0xFFE2E8F0)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial.toString(),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White else ZenvestTextPrimary
        )
    }
}

@Composable
private fun StatusPill(status: String) {
    val statusColor = statusColor(status)
    Text(
        text = stringResource(statusLabelRes(status)),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = statusColor,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(statusColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
