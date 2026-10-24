// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

@file:Suppress("LongMethod", "MaxLineLength", "NoWildcardImports", "NoUnusedImports")

package com.netless.app.ui.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netless.app.R
import com.netless.app.data.SettingsRepository
import com.netless.app.ui.theme.NetlessSurfaceDim
import com.netless.app.ui.theme.NetlessTheme
import com.netless.app.ui.theme.ZenvestBgLight
import com.netless.app.ui.theme.ZenvestTextMuted
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary

/**
 * Clean document viewer for Privacy Policy and Terms of Service.
 */
class LegalDocumentActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DOC_TYPE = "doc_type"
        const val TYPE_PRIVACY = "privacy"
        const val TYPE_TERMS = "terms"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Netless)

        val docType = intent.getStringExtra(EXTRA_DOC_TYPE) ?: TYPE_PRIVACY
        val isDarkMode = try {
            SettingsRepository(applicationContext).getSettings().darkMode
        } catch (_: Exception) {
            false
        }

        setContent {
            NetlessTheme(darkTheme = isDarkMode) {
                LegalDocumentScreen(
                    docType = docType,
                    isDark = isDarkMode,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalDocumentScreen(
    docType: String,
    isDark: Boolean,
    onBack: () -> Unit
) {
    val title = if (docType == LegalDocumentActivity.TYPE_PRIVACY) {
        "Privacy Policy"
    } else {
        "Terms of Service"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else ZenvestTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isDark) Color.White else ZenvestTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) NetlessSurfaceDim else ZenvestBgLight
                )
            )
        },
        containerColor = if (isDark) NetlessSurfaceDim else ZenvestBgLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (docType == LegalDocumentActivity.TYPE_PRIVACY) {
                LegalSection(
                    heading = "1. 100% Offline Architecture",
                    body = "FlowPay (Netless) operates strictly on-device without any central servers, third-party analytics SDKs, or cloud telemetry. No transaction records, balances, or personal identifiers ever leave your mobile device.",
                    isDark = isDark
                )
                LegalSection(
                    heading = "2. Cryptographic Security",
                    body = "All local database tables are safeguarded with AES-256 SQLCipher encryption. Keys are derived securely using Android Keystore hardware-backed security modules.",
                    isDark = isDark
                )
                LegalSection(
                    heading = "3. Bank SMS & Clipboard Privacy",
                    body = "FlowPay processes incoming bank SMS notifications strictly in-memory using an ephemeral parser. Raw SMS messages remain in your native messaging app. Payee VPAs placed on the clipboard are explicitly marked as sensitive and automatically wiped from the clipboard once payment initiates.",
                    isDark = isDark
                )
                LegalSection(
                    heading = "4. Zero PIN Access",
                    body = "FlowPay never asks for, records, or transmits your secret 4-digit or 6-digit UPI PIN. Your UPI PIN is entered exclusively within the official NPCI telephony IVR audio stream or carrier USSD prompt.",
                    isDark = isDark
                )
                LegalSection(
                    heading = "5. Complete Data Deletion",
                    body = "You retain complete control of your data. Tapping 'Delete Account & Wipe Device Data' in Settings instantly purges all database files, cryptographic keys, and user preferences permanently.",
                    isDark = isDark
                )
            } else {
                LegalSection(
                    heading = "1. Nature of Service",
                    body = "FlowPay provides an offline accessibility and telephony bridge interfacing with National Payments Corporation of India (NPCI) official *99# USSD and UPI 123Pay services. FlowPay does not hold, custody, or process funds directly.",
                    isDark = isDark
                )
                LegalSection(
                    heading = "2. Regulatory Compliance",
                    body = "All payments executed via USSD or IVR are governed by Reserve Bank of India (RBI) and NPCI offline payment regulations. Maximum transaction limits are set at ₹5,000 for UPI 123Pay and ₹1,00,000 for standard UPI rails.",
                    isDark = isDark
                )
                LegalSection(
                    heading = "3. Carrier Availability",
                    body = "USSD (*99#) features require an active GSM telecom provider (Airtel, Vi, BSNL). On carriers that do not support USSD (such as Jio), FlowPay automatically provides fallback via UPI 123Pay IVR calling.",
                    isDark = isDark
                )
                LegalSection(
                    heading = "4. User Responsibility",
                    body = "Users are solely responsible for ensuring the confidentiality of their secret UPI PIN and confirming transaction details before authorizing debit instructions.",
                    isDark = isDark
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LegalSection(
    heading: String,
    body: String,
    isDark: Boolean
) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = heading,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
        )
    }
}
