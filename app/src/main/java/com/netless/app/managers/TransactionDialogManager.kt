// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.managers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.netless.app.R

/**
 * Manages transaction-related dialogs based on payment session outcomes.
 *
 * When constructed with a Service context the dialog window type is set to
 * TYPE_APPLICATION_OVERLAY so the dialog can actually display over the
 * dialer (a plain AlertDialog from a Service throws BadTokenException —
 * previously these silently degraded to toasts).
 *
 * [onAnyDismiss] fires whenever the user dismisses a result dialog, letting
 * the caller acknowledge the payment session so the result isn't re-shown.
 */
class TransactionDialogManager(
    private val context: Context,
    private val onAnyDismiss: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "TransactionDialogManager"
    }

    private fun AlertDialog.prepareForOverlayDisplay(): AlertDialog {
        if (context !is Activity) {
            window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        return this
    }

    private fun dismissed(which: String) {
        Log.d(TAG, "$which dialog dismissed")
        onAnyDismiss?.invoke()
    }

    private fun showZenvestDialog(
        title: String,
        message: String,
        buttonText: String,
        iconRes: Int,
        iconTint: Int,
        iconBgColor: Int,
        dismissTag: String,
        acknowledgeSession: Boolean = true
    ) {
        try {
            val isDark = try {
                com.netless.app.data.SettingsRepository(context).getSettings().darkMode
            } catch (_: Exception) {
                false
            }

            val inflater = android.view.LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_zenvest_alert, null)

            val container = dialogView.findViewById<android.view.View>(R.id.dialog_container)
            val iconBg = dialogView.findViewById<android.view.View>(R.id.dialog_icon_bg)
            val iconView = dialogView.findViewById<android.widget.ImageView>(R.id.dialog_icon)
            val titleView = dialogView.findViewById<android.widget.TextView>(R.id.dialog_title)
            val messageView = dialogView.findViewById<android.widget.TextView>(R.id.dialog_message)
            val actionButton = dialogView.findViewById<android.widget.Button>(R.id.dialog_button)

            if (isDark) {
                container.setBackgroundResource(R.drawable.bg_zenvest_dialog_dark)
                titleView.setTextColor(android.graphics.Color.WHITE)
                messageView.setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                actionButton.setBackgroundResource(R.drawable.btn_zenvest_dialog_dark)
            } else {
                container.setBackgroundResource(R.drawable.bg_zenvest_dialog_light)
                titleView.setTextColor(android.graphics.Color.parseColor("#0F172A"))
                messageView.setTextColor(android.graphics.Color.parseColor("#475569"))
                actionButton.setBackgroundResource(R.drawable.btn_zenvest_dialog_light)
            }

            // Circular icon badge
            val badgeDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(iconBgColor)
            }
            iconBg.background = badgeDrawable

            iconView.setImageResource(iconRes)
            iconView.setColorFilter(iconTint)

            titleView.text = title
            messageView.text = message
            actionButton.text = buttonText

            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .setCancelable(false)
                .create()
                .prepareForOverlayDisplay()

            actionButton.setOnClickListener {
                dialog.dismiss()
                if (acknowledgeSession) {
                    dismissed(dismissTag)
                }
            }

            dialog.show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show $dismissTag dialog", e)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            if (acknowledgeSession) {
                onAnyDismiss?.invoke()
            }
        }
    }

    /**
     * Show dialog when user cancels the transaction by ending the call early
     */
    fun showTransactionCancelled() {
        showZenvestDialog(
            title = context.getString(R.string.dialog_cancelled_title),
            message = context.getString(R.string.dialog_cancelled_message),
            buttonText = context.getString(R.string.action_ok),
            iconRes = R.drawable.ic_phone_hangup,
            iconTint = android.graphics.Color.parseColor("#EF4444"),
            iconBgColor = android.graphics.Color.parseColor("#26EF4444"),
            dismissTag = "Transaction cancelled"
        )
    }

    /**
     * Show dialog when user cancels the transaction using the terminate button
     */
    fun showTransactionCancelledByUser() {
        showZenvestDialog(
            title = context.getString(R.string.dialog_cancelled_title),
            message = context.getString(R.string.dialog_cancelled_by_user_message),
            buttonText = context.getString(R.string.action_ok),
            iconRes = R.drawable.ic_close,
            iconTint = android.graphics.Color.parseColor("#EF4444"),
            iconBgColor = android.graphics.Color.parseColor("#26EF4444"),
            dismissTag = "Transaction cancelled by user"
        )
    }

    /**
     * Show dialog when the bank SMS reports the transaction failed
     */
    fun showTransactionFailed() {
        showZenvestDialog(
            title = context.getString(R.string.dialog_failed_title),
            message = context.getString(R.string.dialog_failed_message),
            buttonText = context.getString(R.string.action_ok),
            iconRes = R.drawable.ic_error,
            iconTint = android.graphics.Color.parseColor("#EF4444"),
            iconBgColor = android.graphics.Color.parseColor("#26EF4444"),
            dismissTag = "Transaction failed"
        )
    }

    /**
     * Call finished normally; the request was sent and the bank's
     * confirmation (callback call + SMS) is still on its way.
     */
    fun showAwaitingConfirmation() {
        showZenvestDialog(
            title = context.getString(R.string.dialog_awaiting_title),
            message = context.getString(R.string.dialog_awaiting_message),
            buttonText = context.getString(R.string.action_got_it),
            iconRes = R.drawable.ic_check_circle,
            iconTint = android.graphics.Color.parseColor("#0284C7"),
            iconBgColor = android.graphics.Color.parseColor("#260284C7"),
            dismissTag = "Awaiting confirmation",
            acknowledgeSession = false
        )
    }
}

