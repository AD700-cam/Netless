// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

@file:Suppress(
    "ReturnCount", "MaxLineLength", "MagicNumber", "PropertyWrapping", "Wrapping",
    "ArgumentListWrapping", "StringTemplate", "LongMethod", "CyclomaticComplexMethod",
    "TooGenericExceptionCaught", "Indentation"
)

package com.netless.app.services

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.netless.app.NetlessApplication
import com.netless.app.data.TransactionStatus
import com.netless.app.receivers.PaymentResultNotifier
import com.netless.app.repository.TransactionRepository
import com.netless.app.ui.activities.PaymentResultActivity
import com.netless.app.utils.DiagnosticLogger
import com.netless.app.utils.PaymentSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * High-speed Zero-Touch USSD Confirmation Service.
 *
 * Observes native telephony and dialer USSD dialogs. When the terminal USSD prompt
 * displays ("Your payment has been successful with the Refid 12345..."), this service
 * captures the outcome, extracts the bank reference and amount, and confirms the payment
 * immediately: updates the encrypted database, plays the harmonic chime, posts a receipt
 * notification, and launches the success screen — completely zero-touch.
 *
 * Runs 100% on-device with zero network access and strict data privacy.
 */
class UssdAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UssdAccessibility"
        const val ACTION_USSD_PAYMENT_CONFIRMED = "com.netless.app.USSD_PAYMENT_CONFIRMED"
        const val EXTRA_REF_ID = "ref_id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_STATUS = "status"
        const val EXTRA_RAW_TEXT = "raw_text"

        // Rate-limiting timestamp to prevent duplicate event firing for the same prompt
        private var lastDetectedTime = 0L
        private var lastDetectedRef = ""

        /**
         * Checks if the Accessibility Service is enabled in Android Settings.
         */
        fun isEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${UssdAccessibilityService::class.java.canonicalName}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                    componentName.contains(UssdAccessibilityService::class.java.simpleName, ignoreCase = true)
                ) {
                    return true
                }
            }
            return false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Collect all text from event record
        val sb = StringBuilder()
        for (item in event.text) {
            sb.append(item).append(" ")
        }

        // Inspect source node
        event.source?.let { extractTextFromNode(it, sb) }

        // Also inspect root in active window
        rootInActiveWindow?.let { extractTextFromNode(it, sb) }

        // Inspect interactive windows (system dialog overlays)
        try {
            for (window in windows) {
                window.root?.let { extractTextFromNode(it, sb) }
            }
        } catch (_: Exception) {}

        val fullText = sb.toString().trim()
        if (fullText.isBlank()) return

        inspectUssdText(fullText)
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        val text = node.text
        if (!text.isNullOrBlank()) {
            sb.append(text).append(" ")
        }
        val desc = node.contentDescription
        if (!desc.isNullOrBlank()) {
            sb.append(desc).append(" ")
        }
        for (i in 0 until node.childCount) {
            extractTextFromNode(node.getChild(i), sb)
        }
    }

    /**
     * Inspects captured screen text for *99# USSD terminal payment outcome.
     */
    private fun inspectUssdText(text: String) {
        val lower = text.lowercase()

        // Check for terminal success indicator
        val hasSuccessIndicator = (
            lower.contains("payment has been successful") ||
            lower.contains("payment successful") ||
            lower.contains("payment is successful") ||
            lower.contains("txn successful") ||
            lower.contains("transaction successful") ||
            lower.contains("paid successfully") ||
            lower.contains("transferred successfully") ||
            (lower.contains("successful") && (
                lower.contains("refid") || lower.contains("ref no") ||
                lower.contains("ref id") || lower.contains("ref:") || lower.contains("reference")
            )) ||
            (lower.contains("debited") && lower.contains("successful"))
        )

        if (!hasSuccessIndicator) return

        val now = System.currentTimeMillis()
        if (now - lastDetectedTime < 3000L) {
            // Deduplicate rapid repeat events
            return
        }

        Log.d(TAG, "Detected USSD payment success confirmation on screen!")

        // Extract Ref ID
        val refRegex = Regex("(?:refid|ref\\s*id|ref\\s*no|reference\\s*no|ref|rrn)[:\\s]+([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
        val refMatch = refRegex.find(text)
        val refId = refMatch?.groups?.get(1)?.value ?: "REF${now}"

        if (refId == lastDetectedRef && now - lastDetectedTime < 10000L) {
            return
        }
        lastDetectedRef = refId
        lastDetectedTime = now

        // Extract Amount if present in dialog text
        val amountRegex = Regex("(?:Rs\\.?|INR|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(text)
        val amount = amountMatch?.groups?.get(1)?.value?.replace(",", "") ?: ""

        Log.d(TAG, "Extracted USSD confirmation: RefId=$refId, Amount=$amount")
        DiagnosticLogger.log("USSD", "Confirmed USSD Payment: Ref=$refId, Amount=$amount")

        // 1. Direct Session Manager Update
        val sessionManager = NetlessApplication.from(this)?.paymentSessionManager
        val confirmedTxnId = sessionManager?.onUssdConfirmed(refId, amount)

        // 2. Direct Room DB Update
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = TransactionRepository.getInstance(applicationContext)
                val updatedCount = repo.confirmLatestPending(
                    bankRef = refId,
                    bankName = "NPCI / *99# USSD",
                    smsExcerpt = "USSD Payment Successful · Ref $refId",
                    verifiedAt = now
                )
                Log.d(TAG, "Room DB updated with USSD confirmation: $updatedCount rows")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist USSD confirmation in DB: ${e.message}", e)
            }
        }

        // 3. Procedural Celebratory Audio & Haptics
        try {
            PaymentSoundManager.playSuccessSound(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play success sound: ${e.message}")
        }

        // 4. Build Intent & Launch Result Activity Directly
        val resultIntent = Intent(applicationContext, PaymentResultActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("transaction_id", confirmedTxnId ?: refId)
            putExtra("amount", amount)
            putExtra("status", TransactionStatus.SUCCESS)
            putExtra("bank_name", "NPCI / *99# USSD")
            putExtra("message", "Payment Confirmed via USSD · Ref $refId")
            putExtra("timestamp", now)
            putExtra("transaction_type", "DEBIT")
        }

        // 5. Post Guaranteed-Reachable System Notification
        PaymentResultNotifier.notifyResult(applicationContext, resultIntent)

        // 6. Direct Launch of PaymentResultActivity
        try {
            startActivity(resultIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch PaymentResultActivity directly: ${e.message}")
        }

        // 7. Broadcast for any listening activity
        val intent = Intent(ACTION_USSD_PAYMENT_CONFIRMED).apply {
            putExtra(EXTRA_REF_ID, refId)
            putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_STATUS, "SUCCESS")
            putExtra(EXTRA_RAW_TEXT, text)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "UssdAccessibilityService interrupted")
    }
}
