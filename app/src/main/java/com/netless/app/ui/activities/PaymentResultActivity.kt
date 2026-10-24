// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.netless.app.MainActivity
import com.netless.app.NetlessApplication
import com.netless.app.R
import com.netless.app.data.SettingsRepository
import com.netless.app.data.TransactionStatus
import com.netless.app.helpers.TransactionDetector
import com.netless.app.utils.CurrencyFormat
import com.netless.app.utils.PaymentSoundManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PaymentResultActivity : AppCompatActivity() {

    private var isDarkMode: Boolean = false
    private lateinit var rootLayout: View
    private lateinit var statusCircle: View
    private lateinit var tickImageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var statusExplainerText: TextView
    private lateinit var amountText: TextView
    private lateinit var detailsCard: CardView
    private lateinit var bankLabel: TextView
    private lateinit var bankNameText: TextView
    private lateinit var refLabel: TextView
    private lateinit var transactionIdText: TextView
    private lateinit var dateTimeLabel: TextView
    private lateinit var dateTimeText: TextView
    private lateinit var upiIdLayout: LinearLayout
    private lateinit var upiIdLabel: TextView
    private lateinit var upiIdText: TextView
    private lateinit var recipientLayout: LinearLayout
    private lateinit var recipientLabel: TextView
    private lateinit var recipientText: TextView
    private lateinit var doneButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read user's chosen theme: default is clean Zenvest Light mode
        val settingsRepo = (applicationContext as? NetlessApplication)?.settingsRepository
            ?: SettingsRepository(applicationContext)
        isDarkMode = settingsRepo.getSettings().darkMode

        setupSystemUI(isDarkMode)

        setContentView(R.layout.activity_payment_success)

        initViews()
        applyTheme(isDarkMode)
        loadTransactionData()
        startAnimations()

        // Replaces the deprecated onBackPressed() override
        onBackPressedDispatcher.addCallback(this) {
            navigateToMain()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetViewsForAnimation()
        loadTransactionData()
        startAnimations()
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.root_layout)
        statusCircle = findViewById(R.id.iv_status_circle)
        tickImageView = findViewById(R.id.iv_success_tick)
        statusText = findViewById(R.id.tv_status)
        statusExplainerText = findViewById(R.id.tv_status_explainer)
        amountText = findViewById(R.id.tv_amount)
        detailsCard = findViewById(R.id.card_details)
        bankLabel = findViewById(R.id.tv_bank_label)
        bankNameText = findViewById(R.id.tv_bank_name)
        refLabel = findViewById(R.id.tv_ref_label)
        transactionIdText = findViewById(R.id.tv_transaction_id)
        dateTimeLabel = findViewById(R.id.tv_date_time_label)
        dateTimeText = findViewById(R.id.tv_date_time)
        upiIdLayout = findViewById(R.id.layout_upi_id)
        upiIdLabel = findViewById(R.id.tv_upi_id_label)
        upiIdText = findViewById(R.id.tv_upi_id)
        recipientLayout = findViewById(R.id.layout_recipient)
        recipientLabel = findViewById(R.id.tv_recipient_label)
        recipientText = findViewById(R.id.tv_recipient_name)
        doneButton = findViewById(R.id.btn_done)

        findViewById<View>(R.id.btn_copy_ref)?.setOnClickListener {
            val ref = transactionIdText.text?.toString().orEmpty()
            if (ref.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Bank Reference", ref)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(this, R.string.ref_copied, Toast.LENGTH_SHORT).show()
            }
        }

        resetViewsForAnimation()

        doneButton.setOnClickListener {
            navigateToMain()
        }
    }

    /** Apply Zenvest Light theme (white card, dark slate text) or Dark theme. */
    private fun applyTheme(isDark: Boolean) {
        val bgColor = if (isDark) Color.parseColor("#090D16") else Color.parseColor("#F8FAFC")
        rootLayout.setBackgroundColor(bgColor)

        val cardBg = if (isDark) Color.parseColor("#111827") else Color.WHITE
        detailsCard.setCardBackgroundColor(cardBg)
        detailsCard.cardElevation = if (isDark) 0f else (resources.displayMetrics.density * 3f)

        val labelColor = if (isDark) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
        recipientLabel.setTextColor(labelColor)
        bankLabel.setTextColor(labelColor)
        refLabel.setTextColor(labelColor)
        dateTimeLabel.setTextColor(labelColor)
        upiIdLabel.setTextColor(labelColor)
        statusExplainerText.setTextColor(labelColor)

        val valueColor = if (isDark) Color.WHITE else Color.parseColor("#0F172A")
        recipientText.setTextColor(valueColor)
        bankNameText.setTextColor(valueColor)
        transactionIdText.setTextColor(valueColor)
        dateTimeText.setTextColor(valueColor)
        upiIdText.setTextColor(valueColor)

        val copyRefBtn = findViewById<TextView>(R.id.btn_copy_ref)
        copyRefBtn?.setTextColor(if (isDark) Color.parseColor("#38BDF8") else Color.parseColor("#0284C7"))

        if (isDark) {
            doneButton.setBackgroundResource(R.drawable.button_gradient_bg)
        } else {
            doneButton.setBackgroundResource(R.drawable.button_zenvest_primary)
        }
        doneButton.setTextColor(Color.WHITE)
    }

    /** Return the animated views to their pre-animation (hidden) state. */
    private fun resetViewsForAnimation() {
        statusCircle.alpha = 0f
        statusCircle.scaleX = 0.2f
        statusCircle.scaleY = 0.2f
        tickImageView.alpha = 0f
        statusText.alpha = 0f
        statusExplainerText.alpha = 0f
        amountText.alpha = 0f
        detailsCard.alpha = 0f
        doneButton.alpha = 0f
    }

    private fun loadTransactionData() {
        val transactionId = intent.getStringExtra("transaction_id") ?: "N/A"
        val amount = intent.getStringExtra("amount") ?: "0"
        val status = intent.getStringExtra("status") ?: "UNKNOWN"
        val bankName = intent.getStringExtra("bank_name") ?: "Bank"
        val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
        val upiId = intent.getStringExtra("upi_id")
        val transactionType = intent.getStringExtra("transaction_type") ?: "DEBIT"
        val recipientName = intent.getStringExtra("recipient_name") // NEW
        val phoneNumber = intent.getStringExtra("phone_number") // NEW

        // Get operation type from detector
        val detector = TransactionDetector.getInstance(this)
        val operationType = detector.getOperationType() ?: ""

        // Render the outcome the bank actually reported — this screen is
        // launched for every parsed confirmation, not only successes. Every
        // property is set explicitly (never relying on layout defaults) so an
        // onNewIntent re-render from a different status resets cleanly.
        //
        // Color language: SUCCESS wears the brand look (blue gradient circle,
        // blue heading, green amount). Non-success outcomes wear their status
        // color on circle + heading + amount so a FAILED result can never be
        // mistaken for a success at a glance. The glyph is always white.
        when (status) {
            TransactionStatus.CANCELLED -> {
                statusText.text = getString(R.string.payment_status_cancelled)
                statusExplainerText.text = getString(R.string.status_explainer_cancelled)
                statusExplainerText.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_error)
                applyStatusAccent(R.color.unverified_grey)
            }
            TransactionStatus.FAILED -> {
                statusText.text = getString(R.string.payment_status_failed)
                statusExplainerText.text = getString(R.string.status_explainer_failed)
                statusExplainerText.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_error)
                applyStatusAccent(R.color.error_red)
            }
            TransactionStatus.NEEDS_REVIEW -> {
                statusText.text = getString(R.string.payment_status_needs_review)
                statusExplainerText.text = getString(R.string.status_explainer_needs_review)
                statusExplainerText.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_error)
                applyStatusAccent(R.color.warning_orange)
            }
            TransactionStatus.SUCCESS -> {
                statusText.text = getString(R.string.payment_status_success)
                statusExplainerText.visibility = View.GONE
                tickImageView.setImageResource(R.drawable.ic_check_white)
                applySuccessAccent()
            }
            else -> {
                // UNVERIFIED, or any unexpected status — the outcome is not a
                // confirmed success, so render it neutral (grey, question glyph)
                // and NEVER fall open to the green success look. Only an explicit
                // SUCCESS above earns the success styling.
                statusText.text = getString(R.string.payment_status_unverified)
                statusExplainerText.text = getString(R.string.status_explainer_unverified)
                statusExplainerText.visibility = View.VISIBLE
                tickImageView.setImageResource(R.drawable.ic_unverified)
                applyStatusAccent(R.color.unverified_grey)
            }
        }
        tickImageView.setColorFilter(ContextCompat.getColor(this, android.R.color.white))
        amountText.text = getString(R.string.amount_rupees, formatAmount(amount))

        // Handle recipient/sender display - UPDATED LOGIC
        when {
            !recipientName.isNullOrEmpty() -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = getString(counterpartyLabelFor(status, transactionType))
                recipientText.text = recipientName
            }
            !phoneNumber.isNullOrEmpty() -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = getString(
                    if (transactionType == "CREDIT") R.string.recipient_from else R.string.recipient_to
                )
                recipientText.text = phoneNumber
            }
            operationType == "UPI_123" && detector.getPhoneNumber() != null -> {
                recipientLayout.visibility = View.VISIBLE
                recipientLabel.text = getString(R.string.recipient_to)
                recipientText.text = detector.getPhoneNumber()
            }
            else -> {
                recipientLayout.visibility = View.GONE
            }
        }

        // Bank name - show if different from recipient
        bankNameText.text = bankName

        transactionIdText.text = bankReferenceOf(transactionId)
        dateTimeText.text = formatDateTime(timestamp)

        // Show UPI ID if available
        if (!upiId.isNullOrEmpty()) {
            upiIdLayout.visibility = View.VISIBLE
            upiIdText.text = upiId
        } else {
            upiIdLayout.visibility = View.GONE
        }
    }

    /** SUCCESS look: emerald gradient circle, slate/white heading, emerald amount. */
    private fun applySuccessAccent() {
        statusCircle.backgroundTintList = null
        statusCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_success_bg)
        statusText.setTextColor(if (isDarkMode) Color.WHITE else Color.parseColor("#0F172A"))
        amountText.setTextColor(Color.parseColor("#10B981"))
    }

    /** Non-success look: one status colour across circle, heading and amount. */
    private fun applyStatusAccent(colorRes: Int) {
        val color = ContextCompat.getColor(this, colorRes)
        statusCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_status_bg)
        statusCircle.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        statusText.setTextColor(color)
        amountText.setTextColor(color)
    }

    private fun formatAmount(amount: String): String = CurrencyFormat.inr(amount)

    /**
     * The label above the counterparty's name, for the outcome being rendered.
     *
     * "Paid to" and "Received from" are past tense: they assert that the money
     * moved, so only a confirmed SUCCESS earns them. Under a red "Payment
     * Failed" heading the assertion is simply false — the card read "Paid to
     * SHARMA STORE" directly below an explainer saying any debited amount is
     * normally auto-reversed, so the screen contradicted itself and the more
     * concrete-looking line is the one a worried user believes. NEEDS_REVIEW
     * and UNVERIFIED have the same problem for the opposite reason: nobody
     * knows yet whether it moved, which is the whole point of those states.
     *
     * The neutral "To"/"From" names the counterparty without claiming an
     * outcome, which is all the row was ever there to do.
     */
    private fun counterpartyLabelFor(status: String, transactionType: String): Int {
        val isCredit = transactionType == "CREDIT"
        return when {
            status != TransactionStatus.SUCCESS ->
                if (isCredit) R.string.recipient_from else R.string.recipient_to
            isCredit -> R.string.recipient_received_from
            else -> R.string.recipient_paid_to
        }
    }

    /**
     * The part of a stored transaction id a user can actually act on.
     *
     * Ids are stored as `<bank reference>_<timestamp>` — the timestamp keeps
     * rows unique when a bank reuses a reference, and is meaningless to the
     * reader. Shown whole it produced "125012501250…85952823651", ellipsised
     * through the middle, which is precisely the half a user needs to match
     * this payment against their bank statement. Generated ids (`TXN…`, no
     * separator) are shown as-is.
     */
    private fun bankReferenceOf(transactionId: String): String =
        transactionId.substringBeforeLast('_').ifBlank { transactionId }

    private fun formatDateTime(timestamp: Long): String {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return formatter.format(Date(timestamp))
    }

    private fun startAnimations() {
        val status = intent.getStringExtra("status") ?: "UNKNOWN"

        // Animate tick only on genuine success; never play celebration on cancel/failure
        if (status == TransactionStatus.SUCCESS) {
            Handler(Looper.getMainLooper()).postDelayed({
                animateTickMark()
            }, 200)
        } else {
            statusCircle.alpha = 1f
            statusCircle.scaleX = 1f
            statusCircle.scaleY = 1f
            tickImageView.alpha = 1f
            tickImageView.scaleX = 1f
            tickImageView.scaleY = 1f
            tickImageView.rotation = 0f
        }

        // Fade in status text (and its explainer line, when visible)
        Handler(Looper.getMainLooper()).postDelayed({
            statusText.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
            statusExplainerText.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }, 800)

        // Fade in amount
        Handler(Looper.getMainLooper()).postDelayed({
            amountText.animate()
                .alpha(1f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(400)
                .withEndAction {
                    amountText.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }, 1200)

        // Slide up details card
        Handler(Looper.getMainLooper()).postDelayed({
            detailsCard.translationY = 100f
            detailsCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }, 1600)

        // Fade in done button
        Handler(Looper.getMainLooper()).postDelayed({
            doneButton.animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }, 2000)
    }

    private fun animateTickMark() {
        // Play celebratory chime and haptics
        PaymentSoundManager.playSuccessSound(this)

        // 1. Circle pops open with overshoot
        statusCircle.alpha = 1f
        statusCircle.scaleX = 0f
        statusCircle.scaleY = 0f
        statusCircle.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(450)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
            .start()

        // 2. Tick mark scales up with energetic spring
        tickImageView.alpha = 0f
        tickImageView.scaleX = 0.1f
        tickImageView.scaleY = 0.1f
        tickImageView.rotation = -20f

        tickImageView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setDuration(500)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
            .withEndAction {
                // Subtle celebratory pulse
                tickImageView.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .setDuration(150)
                    .withEndAction {
                        tickImageView.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun setupSystemUI(isDark: Boolean) {
        val window = window ?: return
        val decorView = window.decorView
        val insetsController = WindowCompat.getInsetsController(window, decorView)

        if (isDark) {
            window.statusBarColor = Color.parseColor("#090D16")
            window.navigationBarColor = Color.parseColor("#090D16")
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        } else {
            window.statusBarColor = Color.parseColor("#F8FAFC")
            window.navigationBarColor = Color.parseColor("#F8FAFC")
            insetsController.isAppearanceLightStatusBars = true
            insetsController.isAppearanceLightNavigationBars = true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupSystemUI(isDarkMode)
        }
    }
}
