// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.netless.app.NetlessApplication
import com.netless.app.R
import com.netless.app.managers.TransactionDialogManager
import com.netless.app.payment.PaymentSessionManager
import com.netless.app.states.PaymentState
import com.netless.app.telephony.CallStateCoordinator
import com.netless.app.telephony.DeviceCallState
import com.netless.app.utils.OverlayLogger
import com.netless.app.utils.PhoneNumberUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CallOverlayService : Service() {

    companion object {
        private const val TAG = "CallOverlayService"
        private const val ACTION_START_OVERLAY = "START_OVERLAY"
        private const val ACTION_STOP_OVERLAY = "STOP_OVERLAY"
        private const val TIMEOUT_DURATION = 40000L // 40 seconds timeout
        private const val TIMEOUT_EXTENSION_MS = 10000L // re-arm step while device not idle
        private const val MAX_TIMEOUT_EXTENSIONS = 3 // bounded: +30s worst case

        // Progress pacing for the in-call phase. The IVR emits no progress
        // events, so between "call answered" and "request sent" there is
        // nothing real to report — the bar used to simply park at 45% for the
        // whole call, which reads as stalled. It now creeps toward a CEILING
        // it can never pass on a timer: only the real WaitingForVerification
        // transition fills it. The bar can therefore sit short of full, but it
        // can never claim the payment is through when it isn't. This is pacing,
        // not the fake step-engine deleted in 88f555e, which asserted outcomes.
        private const val PROGRESS_CONNECTING = 0.10f
        private const val PROGRESS_IN_CALL_START = 0.20f
        private const val PROGRESS_IN_CALL_CEILING = 0.90f
        private const val PROGRESS_IN_CALL_CREEP_MS = 30000L
        private const val PROGRESS_MILESTONE_ANIM_MS = 1000L

        // How long "Sending your payment request…" shows before the overlay
        // explains that the PIN is entered on the bank's call, not here.
        private const val STEP_PIN_NOTICE_DELAY_MS = 2500L

        // Let the filled bar be seen before the overlay winds down.
        private const val PROGRESS_COMPLETE_HOLD_MS = 700L

        // Cleared in onDestroy(), so the reference is bounded by the service
        // lifecycle. Lint's StaticFieldLeak is baselined for this line; see
        // SECURITY.md "Known deferred issues".
        private var serviceInstance: CallOverlayService? = null

        /**
         * Shows the call overlay with payment details
         */
        fun showOverlay(context: Context, phoneNumber: String, amount: String) {
            try {
                Log.d(TAG, "=== Starting call overlay for $phoneNumber, amount $amount ===")
                val intent = Intent(context, CallOverlayService::class.java).apply {
                    putExtra("action", ACTION_START_OVERLAY)
                    putExtra("phone_number", phoneNumber)
                    putExtra("amount", amount)
                }
                context.startService(intent)
                Log.d(TAG, "Call overlay service start intent dispatched")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start call overlay service: ${e.message}")
            }
        }

        /**
         * Hides the call overlay
         */
        fun hideOverlay(context: Context) {
            try {
                val intent = Intent(context, CallOverlayService::class.java).apply {
                    putExtra("action", ACTION_STOP_OVERLAY)
                }
                context.startService(intent)
                Log.d(TAG, "Call overlay hide requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide call overlay: ${e.message}")
            }
        }
    }

    private val binder = CallOverlayBinder()
    private var isOverlayActive = false
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isOverlayShowing = false

    // Timeout management
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private var timeoutExtensions = 0
    private var isCallDetected = false
    private var hasBeenOffHook = false
    private var serviceStartTime = 0L
    private var pendingPhoneNumber = ""
    private var pendingAmount = ""

    // SMS detection handled by new system

    // Call/payment state observation and dialog management
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var coordinator: CallStateCoordinator? = null
    private var sessionManager: PaymentSessionManager? = null
    private var coordinatorAcquired = false
    private var resultHandled = false
    private var dialogManager: TransactionDialogManager? = null
    private var isDarkMode = false

    // Call management
    private var callManager: com.netless.app.managers.CallManager? = null

    // Retry bookkeeping for overlay creation (see showOverlayWithRetry).
    private var retryCount = 0
    private val maxRetries = 3

    // In-call progress pacing; both are cancelled on every state change and on
    // hide, so a settled outcome can never be overwritten by a stale animation.
    private val pacingHandler = Handler(Looper.getMainLooper())
    private var progressAnimator: android.animation.ObjectAnimator? = null
    private var inCallTicker: Runnable? = null
    private var inCallSecondsElapsed = 0

    inner class CallOverlayBinder : Binder() {
        fun getService(): CallOverlayService = this@CallOverlayService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // Deliberately NOT a foreground service. On targetSdk 35 an FGS needs a
    // foregroundServiceType the app can't honestly claim ("phoneCall"
    // requires MANAGE_OWN_CALLS + a ConnectionService; "specialUse" invites
    // Play review friction). Survival is covered without it: the service is
    // started while the app is foreground, and once the call goes OFFHOOK
    // its TYPE_APPLICATION_OVERLAY window keeps the process perceptible for
    // the call's duration; the pre-OFFHOOK gap is bounded by the 40s(+30s)
    // watchdog, well inside the started-service grace period. Revisit only
    // if field logs ever show mid-call service death.
    override fun onCreate() {
        super.onCreate()
        val startTime = System.currentTimeMillis()
        serviceInstance = this
        serviceStartTime = startTime

        OverlayLogger.logServiceEvent(
            "SERVICE_CREATED",
            mapOf(
                "timestamp" to startTime
            )
        )

        // Initialize timeout handler
        timeoutHandler = Handler(Looper.getMainLooper())

        // Initialize dialog manager with Theme_Netless; dismissing a result dialog acknowledges
        // the payment session so the result isn't shown again elsewhere.
        dialogManager = TransactionDialogManager(androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_Netless)) {
            sessionManager?.acknowledgeResult()
        }

        // Initialize call manager
        callManager = com.netless.app.managers.CallManager(this)

        // Observe the single app-wide call-state authority and the payment
        // session. These replace the service's own PhoneStateListener and
        // the old duration-based outcome guessing.
        NetlessApplication.from(this)?.let { app ->
            coordinator = app.callStateCoordinator
            sessionManager = app.paymentSessionManager
            coordinator?.acquire("call-overlay-service")
            coordinatorAcquired = true

            serviceScope.launch {
                app.callStateCoordinator.callState.collect { state ->
                    when (state) {
                        is DeviceCallState.OffHook -> {
                            hasBeenOffHook = true
                            onCallDetected()
                        }
                        is DeviceCallState.Idle -> {
                            if (hasBeenOffHook) {
                                callManager?.restoreCallVolume()
                                hideOverlayInternal()
                            }
                        }
                        else -> Unit
                    }
                }
            }

            serviceScope.launch {
                app.paymentSessionManager.paymentState.collectLatest { state ->
                    onPaymentStateChanged(state)
                }
            }
        }

        Log.d(TAG, "CallOverlayService initialization complete")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CallOverlayService started with action: ${intent?.getStringExtra("action")}")

        when (intent?.getStringExtra("action")) {
            ACTION_START_OVERLAY -> {
                val phoneNumber = intent.getStringExtra("phone_number") ?: ""
                val amount = intent.getStringExtra("amount") ?: ""
                showOverlayInternal(phoneNumber, amount)
            }
            ACTION_STOP_OVERLAY -> {
                hideOverlayInternal()
            }
        }

        // NOT sticky: a system restart with a null intent would only produce
        // a zombie service with no pending payment to protect.
        return START_NOT_STICKY
    }

    /**
     * Drives overlay content and result dialogs from the payment session.
     * Duration heuristics never decide outcomes — the session does.
     */
    private fun onPaymentStateChanged(state: PaymentState) {
        when (state) {
            is PaymentState.Initiating -> {
                updateOverlayStatus(
                    status = getString(R.string.overlay_processing_payment),
                    step = getString(R.string.overlay_connecting),
                    progressFraction = PROGRESS_CONNECTING
                )
            }
            is PaymentState.InProgress -> {
                updateOverlayStatus(
                    status = "Processing Payment",
                    step = getString(R.string.overlay_sending_request),
                    progressFraction = PROGRESS_IN_CALL_START
                )
            }
            is PaymentState.WaitingForVerification -> {
                cancelProgressPacing()
                updateTimelineStep(5)
                overlayView?.findViewById<TextView>(R.id.timeRemainingText)?.text = getString(R.string.action_done)
                // The request is genuinely through — this is the only thing
                // that fills the bar.
                updateOverlayStatus(
                    status = getString(R.string.overlay_request_sent),
                    step = getString(R.string.overlay_request_sent),
                    progressFraction = 1f
                )
                pacingHandler.postDelayed(
                    { finishWithResult { dialogManager?.showAwaitingConfirmation() } },
                    PROGRESS_COMPLETE_HOLD_MS
                )
            }
            is PaymentState.Success -> {
                cancelProgressPacing()
                updateTimelineStep(5)
                overlayView?.findViewById<TextView>(R.id.timeRemainingText)?.text = getString(R.string.action_done)
                vibrate()
                endCallOnConfirmation()
                // PaymentResultActivity (launched by the SMS pipeline) is the
                // success surface; no extra dialog from the service.
                finishWithResult { }
            }
            is PaymentState.Failed -> {
                cancelProgressPacing()
                endCallOnConfirmation()
                finishWithResult { dialogManager?.showTransactionFailed() }
            }
            is PaymentState.NeedsReview -> {
                cancelProgressPacing()
                endCallOnConfirmation()
                // PaymentResultActivity (launched by the SMS pipeline with
                // the NEEDS_REVIEW status extra) is the review surface; no
                // extra dialog from the service.
                finishWithResult { }
            }
            is PaymentState.Cancelled -> {
                cancelProgressPacing()
                val byUser = state.reason.contains("by user", ignoreCase = true)
                finishWithResult {
                    if (byUser) {
                        dialogManager?.showTransactionCancelledByUser()
                    } else {
                        dialogManager?.showTransactionCancelled()
                    }
                }
            }
            is PaymentState.Timeout -> {
                cancelProgressPacing()
                // No bank SMS by the deadline means there is no confirmed
                // payment to report: the row is discarded and nothing is
                // shown. Just wind the overlay down.
                finishWithResult { }
            }
            else -> Unit
        }
    }

    /**
     * Hangs up once the bank's SMS has settled the payment.
     *
     * Only the confirmation decides an outcome, and by the time it arrives the
     * IVR has nothing left to say — leaving the call up just burns the user's
     * airtime and makes them hang up on a call that is already finished.
     *
     * Best-effort by design: [CallManager.terminateCall] returns false when no
     * call is live (the usual case — the user has normally hung up before the
     * SMS lands) or when ANSWER_PHONE_CALLS was denied, in which case the user
     * simply ends it themselves as before. Nothing about the recorded outcome
     * depends on this.
     */
    private fun endCallOnConfirmation() {
        val ended = runCatching { callManager?.terminateCall() ?: false }.getOrDefault(false)
        Log.d(TAG, "Confirmation arrived - auto-hangup ${if (ended) "succeeded" else "not needed"}")
    }

    /** Hide the overlay, show the result surface once, and wind the service down. */
    private fun finishWithResult(showDialog: () -> Unit) {
        if (resultHandled) return
        resultHandled = true
        callManager?.restoreCallVolume()
        hideOverlayInternal()
        showDialog()
        Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 1000)
    }

    /**
     * Updates the illuminated 4-stage transaction timeline.
     * Step 1: 0-4s (Dialing NPCI Rail)
     * Step 2: 4-15s (Automated DTMF Routing)
     * Step 3: 15-25s (Payee Confirmation)
     * Step 4: 25-40s (Enter Secret UPI PIN)
     * Step 5: Completed (Transaction Verified)
     */
    private fun updateTimelineStep(step: Int) {
        val view = overlayView ?: return
        Handler(Looper.getMainLooper()).post {
            val step1Node = view.findViewById<android.widget.ImageView>(R.id.step1Node)
            val step1Title = view.findViewById<TextView>(R.id.step1Title)
            val step1Desc = view.findViewById<TextView>(R.id.step1Desc)
            val step1Time = view.findViewById<TextView>(R.id.step1Time)
            val line1to2 = view.findViewById<View>(R.id.line1to2)

            val step2Node = view.findViewById<android.widget.ImageView>(R.id.step2Node)
            val step2Title = view.findViewById<TextView>(R.id.step2Title)
            val step2Desc = view.findViewById<TextView>(R.id.step2Desc)
            val step2Time = view.findViewById<TextView>(R.id.step2Time)
            val line2to3 = view.findViewById<View>(R.id.line2to3)

            val step3Node = view.findViewById<android.widget.ImageView>(R.id.step3Node)
            val step3Title = view.findViewById<TextView>(R.id.step3Title)
            val step3Desc = view.findViewById<TextView>(R.id.step3Desc)
            val step3Time = view.findViewById<TextView>(R.id.step3Time)
            val line3to4 = view.findViewById<View>(R.id.line3to4)

            val step4Node = view.findViewById<android.widget.ImageView>(R.id.step4Node)
            val step4Title = view.findViewById<TextView>(R.id.step4Title)
            val step4Desc = view.findViewById<TextView>(R.id.step4Desc)
            val step4Time = view.findViewById<TextView>(R.id.step4Time)

            val activeColor = if (isDarkMode) ContextCompat.getColor(this, R.color.overlay_text_primary) else Color.parseColor("#0F172A")
            val cyanColor = if (isDarkMode) ContextCompat.getColor(this, R.color.overlay_cyan) else Color.parseColor("#0284C7")
            val emeraldColor = ContextCompat.getColor(this, R.color.overlay_emerald)
            val inactiveColor = if (isDarkMode) ContextCompat.getColor(this, R.color.overlay_step_inactive) else Color.parseColor("#94A3B8")
            val secondaryColor = if (isDarkMode) ContextCompat.getColor(this, R.color.overlay_text_secondary) else Color.parseColor("#64748B")

            when (step) {
                1 -> {
                    step1Node?.setImageResource(R.drawable.timeline_step_node_active)
                    step1Title?.setTextColor(activeColor)
                    step1Time?.setTextColor(cyanColor)
                    step1Desc?.setTextColor(secondaryColor)

                    step2Node?.setImageResource(R.drawable.timeline_step_node_pending)
                    step2Title?.setTextColor(inactiveColor)
                    step2Time?.setTextColor(inactiveColor)
                    step2Desc?.setTextColor(inactiveColor)
                    line1to2?.setBackgroundColor(inactiveColor)

                    step3Node?.setImageResource(R.drawable.timeline_step_node_pending)
                    step3Title?.setTextColor(inactiveColor)
                    step3Time?.setTextColor(inactiveColor)
                    step3Desc?.setTextColor(inactiveColor)
                    line2to3?.setBackgroundColor(inactiveColor)

                    step4Node?.setImageResource(R.drawable.timeline_step_node_pending)
                    step4Title?.setTextColor(inactiveColor)
                    step4Time?.setTextColor(inactiveColor)
                    step4Desc?.setTextColor(inactiveColor)
                    line3to4?.setBackgroundColor(inactiveColor)
                }
                2 -> {
                    step1Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step1Title?.setTextColor(emeraldColor)
                    step1Time?.setTextColor(emeraldColor)
                    line1to2?.setBackgroundColor(emeraldColor)

                    step2Node?.setImageResource(R.drawable.timeline_step_node_active)
                    step2Title?.setTextColor(activeColor)
                    step2Time?.setTextColor(cyanColor)
                    step2Desc?.setTextColor(secondaryColor)

                    step3Node?.setImageResource(R.drawable.timeline_step_node_pending)
                    step3Title?.setTextColor(inactiveColor)
                    step3Time?.setTextColor(inactiveColor)
                    step3Desc?.setTextColor(inactiveColor)
                    line2to3?.setBackgroundColor(inactiveColor)

                    step4Node?.setImageResource(R.drawable.timeline_step_node_pending)
                    step4Title?.setTextColor(inactiveColor)
                    step4Time?.setTextColor(inactiveColor)
                    step4Desc?.setTextColor(inactiveColor)
                    line3to4?.setBackgroundColor(inactiveColor)
                }
                3 -> {
                    step1Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step1Title?.setTextColor(emeraldColor)
                    step1Time?.setTextColor(emeraldColor)
                    line1to2?.setBackgroundColor(emeraldColor)

                    step2Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step2Title?.setTextColor(emeraldColor)
                    step2Time?.setTextColor(emeraldColor)
                    line2to3?.setBackgroundColor(emeraldColor)

                    step3Node?.setImageResource(R.drawable.timeline_step_node_active)
                    step3Title?.setTextColor(activeColor)
                    step3Time?.setTextColor(cyanColor)
                    step3Desc?.setTextColor(secondaryColor)

                    step4Node?.setImageResource(R.drawable.timeline_step_node_pending)
                    step4Title?.setTextColor(inactiveColor)
                    step4Time?.setTextColor(inactiveColor)
                    step4Desc?.setTextColor(inactiveColor)
                    line3to4?.setBackgroundColor(inactiveColor)
                }
                4 -> {
                    step1Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step1Title?.setTextColor(emeraldColor)
                    step1Time?.setTextColor(emeraldColor)
                    line1to2?.setBackgroundColor(emeraldColor)

                    step2Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step2Title?.setTextColor(emeraldColor)
                    step2Time?.setTextColor(emeraldColor)
                    line2to3?.setBackgroundColor(emeraldColor)

                    step3Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step3Title?.setTextColor(emeraldColor)
                    step3Time?.setTextColor(emeraldColor)
                    line3to4?.setBackgroundColor(emeraldColor)

                    step4Node?.setImageResource(R.drawable.timeline_step_node_active)
                    step4Title?.setTextColor(cyanColor)
                    step4Time?.setTextColor(cyanColor)
                    step4Desc?.setTextColor(activeColor)
                }
                else -> {
                    // All completed (5)
                    step1Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step1Title?.setTextColor(emeraldColor)
                    line1to2?.setBackgroundColor(emeraldColor)

                    step2Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step2Title?.setTextColor(emeraldColor)
                    line2to3?.setBackgroundColor(emeraldColor)

                    step3Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step3Title?.setTextColor(emeraldColor)
                    line3to4?.setBackgroundColor(emeraldColor)

                    step4Node?.setImageResource(R.drawable.timeline_step_node_completed)
                    step4Title?.setTextColor(emeraldColor)
                }
            }
        }
    }

    /**
     * Advances the completing line toward [PROGRESS_IN_CALL_CEILING] while the user is on
     * the IVR call, running a live countdown timer and advancing the timeline milestones.
     */
    private fun startInCallPacing() {
        val view = overlayView ?: return
        val bar = view.findViewById<android.widget.ProgressBar>(R.id.progressBar) ?: return
        val percent = view.findViewById<TextView>(R.id.progressPercent)
        val timeRemaining = view.findViewById<TextView>(R.id.timeRemainingText)
        val stepTextView = view.findViewById<TextView>(R.id.stepText)

        inCallSecondsElapsed = 0
        updateTimelineStep(1)

        // Smooth progress animation toward ceiling (90%)
        val creep = Runnable {
            progressAnimator = android.animation.ObjectAnimator.ofInt(
                bar,
                "progress",
                bar.progress,
                (PROGRESS_IN_CALL_CEILING * 1000).toInt()
            ).apply {
                duration = PROGRESS_IN_CALL_CREEP_MS
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { anim ->
                    val value = (anim.animatedValue as Int)
                    percent?.text = getString(R.string.percent_format, value / 10)
                }
                start()
            }
        }
        pacingHandler.postDelayed(creep, PROGRESS_MILESTONE_ANIM_MS)

        // 1-second ticker for live countdown and timeline stage transitions
        inCallTicker = object : Runnable {
            override fun run() {
                inCallSecondsElapsed++
                val elapsedClamped = inCallSecondsElapsed.coerceAtMost(40)
                timeRemaining?.text = getString(R.string.overlay_time_format, elapsedClamped)

                when {
                    inCallSecondsElapsed < 4 -> {
                        updateTimelineStep(1)
                        stepTextView?.text = getString(R.string.overlay_ivr_step_connected)
                    }
                    inCallSecondsElapsed in 4..14 -> {
                        updateTimelineStep(2)
                        stepTextView?.text = getString(R.string.overlay_ivr_step_routing)
                    }
                    inCallSecondsElapsed in 15..24 -> {
                        updateTimelineStep(3)
                        stepTextView?.text = getString(R.string.overlay_ivr_step_confirming)
                    }
                    inCallSecondsElapsed in 25..39 -> {
                        updateTimelineStep(4)
                        stepTextView?.text = getString(R.string.overlay_ivr_step_pin_prompt)
                    }
                    else -> {
                        // 40s reached: IVR automation complete. Cut the call so network settles & confirmation lands
                        updateTimelineStep(5)
                        stepTextView?.text = getString(R.string.overlay_ivr_step_completed)
                        callManager?.restoreCallVolume()
                        val autoCut = runCatching { callManager?.terminateCall() ?: false }.getOrDefault(false)
                        Log.d(TAG, "40 seconds reached: auto-cut IVR call -> $autoCut")
                    }
                }

                if (inCallSecondsElapsed < 40) {
                    pacingHandler.postDelayed(this, 1000L)
                }
            }
        }
        pacingHandler.postDelayed(inCallTicker!!, 1000L)
    }

    /** Stops the in-call pacing and ticker so a settled state is never overwritten. */
    private fun cancelProgressPacing() {
        progressAnimator?.cancel()
        progressAnimator = null
        inCallTicker?.let { pacingHandler.removeCallbacks(it) }
        inCallTicker = null
        pacingHandler.removeCallbacksAndMessages(null)
    }

    /** Update the overlay texts/progress to reflect the real session state. */
    private fun updateOverlayStatus(status: String, step: String, progressFraction: Float) {
        Handler(Looper.getMainLooper()).post {
            overlayView?.let { view ->
                view.findViewById<TextView>(R.id.statusText)?.text = status
                view.findViewById<TextView>(R.id.stepText)?.text = step
                val progressBar = view.findViewById<android.widget.ProgressBar>(R.id.progressBar)
                val target = (progressFraction * 1000).toInt().coerceIn(0, 1000)
                progressBar?.let { animateProgressBar(it, target) }
                view.findViewById<TextView>(R.id.progressPercent)?.text =
                    getString(R.string.percent_format, target / 10)
            }
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    200,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(200)
        }
    }

    /**
     * Check if overlay permission is granted
     */
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true // Pre-Marshmallow doesn't need this permission
        }
    }

    /**
     * Show error toast when overlay permission is not granted
     */
    private fun showPermissionErrorToast() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                this,
                "Overlay permission required for call protection. Please grant permission in Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Show overlay with retry mechanism
     */
    private fun showOverlayWithRetry(phoneNumber: String, amount: String) {
        try {
            createSystemOverlay(phoneNumber, amount)
            retryCount = 0 // Reset on success
            Log.d(TAG, "Overlay created successfully")
        } catch (e: Exception) {
            if (retryCount < maxRetries) {
                retryCount++
                Log.w(TAG, "Overlay creation failed, retrying ($retryCount/$maxRetries): ${e.message}")
                Handler(Looper.getMainLooper()).postDelayed({
                    showOverlayWithRetry(phoneNumber, amount)
                }, (1000 * retryCount).toLong()) // Exponential backoff
            } else {
                Log.e(TAG, "Max retries exceeded for overlay creation, giving up")
                showOverlayErrorToast()
            }
        }
    }

    /**
     * Show error toast when overlay creation fails
     */
    private fun showOverlayErrorToast() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                this,
                "Failed to show call protection overlay. Please try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showOverlayInternal(phoneNumber: String, amount: String) {
        try {
            Log.d(TAG, "=== CallOverlayService.showOverlayInternal() ===")
            Log.d(TAG, "Call overlay service started")
            Log.d(TAG, "Current state - isOverlayActive: $isOverlayActive, isCallDetected: $isCallDetected")

            // Reset state for a new call.
            resetServiceState()

            // Store phone number and amount for when call is detected
            pendingPhoneNumber = phoneNumber
            pendingAmount = amount

            // Start timeout timer - overlay will be hidden after 40 seconds if no call detected
            startTimeoutTimer()

            Log.d(TAG, "=== CallOverlayService ready for call detection ===")
            // Immediately trigger overlay display since call was just initiated by CallManager
            onCallDetected()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start call overlay service: ${e.message}")
        }
    }

    /**
     * Reset service state for new call
     */
    private fun resetServiceState() {
        Log.d(TAG, "Resetting service state for new call")
        isOverlayActive = false
        isOverlayShowing = false
        isCallDetected = false
        hasBeenOffHook = false
        resultHandled = false
        pendingPhoneNumber = ""
        pendingAmount = ""

        // Cancel any existing timeouts
        timeoutRunnable?.let {
            timeoutHandler?.removeCallbacks(it)
        }

        // Hide any existing overlay
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
                overlayView = null
            } catch (e: Exception) {
                Log.e(TAG, "Error removing existing overlay: ${e.message}")
            }
        }

        Log.d(TAG, "Service state reset complete")
    }

    /**
     * Start the 40-second timeout timer.
     *
     * Before declaring "the call never started", the runnable consults the
     * coordinator's live state: if the device is ringing/off-hook (or the
     * OFFHOOK collector is simply lagging), the timer re-arms in 10s steps
     * (max 3, +30s total) instead of cancelling a session whose call is in
     * fact connecting. Only a persistently Idle device cancels.
     */
    @Suppress("TooGenericExceptionCaught") // see comment below: must never crash the main looper mid-payment
    private fun startTimeoutTimer() {
        Log.d(TAG, "Starting 40-second timeout timer")

        timeoutExtensions = 0
        timeoutRunnable = Runnable {
            val shouldGiveUp = try {
                if (isCallDetected) {
                    false
                } else {
                    val deviceBusy = coordinator?.callState?.value !is DeviceCallState.Idle
                    if (deviceBusy && timeoutExtensions < MAX_TIMEOUT_EXTENSIONS) {
                        timeoutExtensions++
                        Log.d(
                            TAG,
                            "Timeout reached but device not idle - re-arming " +
                                "($timeoutExtensions/$MAX_TIMEOUT_EXTENSIONS)"
                        )
                        timeoutHandler?.postDelayed(timeoutRunnable!!, TIMEOUT_EXTENSION_MS)
                        false
                    } else {
                        Log.d(TAG, "Timeout reached - no call detected, stopping service")
                        true
                    }
                }
            } catch (e: Exception) {
                // This Runnable executes on the main looper (timeoutHandler =
                // Handler(Looper.getMainLooper())): an uncaught exception here
                // would crash the whole app AND skip the session notification
                // below, stranding the payment until the 10-minute deadline.
                // Log and still give up cleanly rather than let either happen.
                Log.e(TAG, "Error in timeout timer: ${e.message}", e)
                true
            }

            if (shouldGiveUp) {
                // The dial never produced an active call; close the session
                // honestly instead of leaving it to the 10-minute deadline.
                sessionManager?.onCallNeverStarted()
                hideOverlayInternal()
                stopSelf()
            }
        }

        timeoutHandler?.postDelayed(timeoutRunnable!!, TIMEOUT_DURATION)
    }

    /**
     * Handle call detection - show overlay when call is actually dialed
     */
    fun onCallDetected() {
        OverlayLogger.logCallDetection(
            pendingPhoneNumber,
            "CALL_DETECTED",
            "isCallDetected: $isCallDetected, isOverlayActive: $isOverlayActive"
        )

        Log.d(TAG, "=== onCallDetected() called ===")
        Log.d(TAG, "isCallDetected: $isCallDetected")
        Log.d(TAG, "isOverlayActive: $isOverlayActive")
        Log.d(TAG, "serviceInstance: $serviceInstance")

        // Ensure service instance is available.
        if (serviceInstance == null) {
            serviceInstance = this
        }

        // New call (not one already detected) — reset state.
        if (!isCallDetected) {
            Log.d(TAG, "New call detected - resetting state")
            isCallDetected = true
        } else {
            Log.d(TAG, "Call already detected, checking if overlay is active")
            if (isOverlayActive) {
                Log.d(TAG, "Overlay already active, ignoring duplicate detection")
                return
            }
        }

        if (pendingPhoneNumber.isNullOrBlank() || pendingAmount.isNullOrBlank()) {
            Log.e(TAG, "Missing pending payment data - cannot show overlay")
            return
        }

        Log.d(TAG, "Call detected - showing overlay")

        // Cancel timeout timer since call was detected
        timeoutRunnable?.let {
            timeoutHandler?.removeCallbacks(it)
            Log.d(TAG, "Timeout timer cancelled")
        }

        // Overlay must be created on the main thread; retried, see showOverlayWithRetry.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            try {
                Log.d(TAG, "Creating system overlay synchronously on main thread...")
                showOverlayWithRetry(pendingPhoneNumber, pendingAmount)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create overlay: ${e.message}")
                showOverlayErrorToast()
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                try {
                    Log.d(TAG, "Creating system overlay on main thread...")
                    showOverlayWithRetry(pendingPhoneNumber, pendingAmount)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create overlay: ${e.message}")
                    showOverlayErrorToast()
                }
            }
        }
    }

    private fun updateOverlayDetails(phoneNumber: String, amount: String) {
        val view = overlayView ?: return
        val amountText = view.findViewById<TextView>(R.id.amountText)
        val recipientText = view.findViewById<TextView>(R.id.currentStepText)
        amountText?.text = getString(R.string.amount_rupees, amount)
        recipientText?.text = PhoneNumberUtils.formatPhoneForDisplay(phoneNumber, 4)
    }

    private fun createSystemOverlay(phoneNumber: String, amount: String) {
        Log.d(TAG, "=== createSystemOverlay() called ===")

        // Check overlay permission before creating overlay
        if (!checkOverlayPermission()) {
            OverlayLogger.logPermissionEvent("SYSTEM_ALERT_WINDOW", false, "overlay_creation")
            Log.e(TAG, "Overlay permission not granted - cannot show overlay")
            showPermissionErrorToast()
            return
        }

        OverlayLogger.logPermissionEvent("SYSTEM_ALERT_WINDOW", true, "overlay_creation")

        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            Log.d(TAG, "WindowManager obtained: $windowManager")

            // If overlay is already active and showing, update details and return without duplicate inflation
            if (isOverlayShowing && overlayView != null) {
                Log.d(TAG, "Overlay is already active on screen, updating transaction details")
                updateOverlayDetails(phoneNumber, amount)
                return
            }

            // Clean up any old overlay view instance before inflating fresh
            if (overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    Log.w(TAG, "Cleaned up prior overlayView: ${e.message}")
                }
                overlayView = null
            }

            val themedContext = android.view.ContextThemeWrapper(this, R.style.Theme_Netless)
            val inflater = LayoutInflater.from(themedContext)
            Log.d(TAG, "Themed LayoutInflater obtained: $inflater")

            isDarkMode = try {
                com.netless.app.data.SettingsRepository(this).getSettings().darkMode
            } catch (_: Exception) {
                false
            }

            overlayView = inflater.inflate(R.layout.call_overlay_netless, null)
            Log.d(TAG, "Overlay view inflated: $overlayView")

            // Adapt overlay container and inner cards to active theme
            val overlayContainer = overlayView?.findViewById<View>(R.id.overlayContainer)
            val detailsCard = overlayView?.findViewById<View>(R.id.detailsCard)
            val pinPromptCard = overlayView?.findViewById<View>(R.id.pinPromptCard)
            val layoutIvrSteps = overlayView?.findViewById<View>(R.id.layoutIvrSteps)
            val securityBadge = overlayView?.findViewById<TextView>(R.id.securityBadge)
            val terminateButton = overlayView?.findViewById<android.widget.Button>(R.id.terminateButton)
            val progressBar = overlayView?.findViewById<android.widget.ProgressBar>(R.id.progressBar)

            if (isDarkMode) {
                overlayContainer?.setBackgroundResource(R.drawable.frosted_glass_background)
                detailsCard?.setBackgroundResource(R.drawable.overlay_card_dark_bg)
                pinPromptCard?.setBackgroundResource(R.drawable.overlay_card_dark_bg)
                layoutIvrSteps?.setBackgroundResource(R.drawable.overlay_card_dark_bg)
                securityBadge?.setBackgroundResource(R.drawable.overlay_card_dark_bg)
                securityBadge?.setTextColor(Color.parseColor("#38BDF8"))
                terminateButton?.setBackgroundResource(R.drawable.overlay_button_cancel_dark)
                terminateButton?.setTextColor(Color.parseColor("#EF4444"))
                progressBar?.progressDrawable = ContextCompat.getDrawable(this, R.drawable.dialer_style_progress)
            } else {
                overlayContainer?.setBackgroundResource(R.drawable.frosted_glass_light_background)
                detailsCard?.setBackgroundResource(R.drawable.overlay_card_light_bg)
                pinPromptCard?.setBackgroundResource(R.drawable.overlay_card_light_bg)
                layoutIvrSteps?.setBackgroundResource(R.drawable.overlay_card_light_bg)
                securityBadge?.setBackgroundResource(R.drawable.overlay_card_light_bg)
                securityBadge?.setTextColor(Color.parseColor("#0284C7"))
                terminateButton?.setBackgroundResource(R.drawable.overlay_button_cancel_light)
                terminateButton?.setTextColor(Color.parseColor("#DC2626"))
                progressBar?.progressDrawable = ContextCompat.getDrawable(this, R.drawable.overlay_progress_bar_light)
            }

            // Set up the overlay brand wordmark
            val netlessLogo = overlayView?.findViewById<TextView>(R.id.netlessLogo)
            val brandFirstColor = if (isDarkMode) Color.WHITE else Color.parseColor("#0F172A")
            val brandSpannable = SpannableStringBuilder().apply {
                append("NET")
                setSpan(ForegroundColorSpan(brandFirstColor), 0, 3, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                append("LESS")
                setSpan(ForegroundColorSpan(0xFF10B981.toInt()), 3, 7, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            netlessLogo?.text = brandSpannable

            val amountText = overlayView?.findViewById<TextView>(R.id.amountText)
            val recipientText = overlayView?.findViewById<TextView>(R.id.currentStepText)
            val statusText = overlayView?.findViewById<TextView>(R.id.statusText)
            val stepText = overlayView?.findViewById<TextView>(R.id.stepText)
            val progressPercent = overlayView?.findViewById<TextView>(R.id.progressPercent)
            val timeRemaining = overlayView?.findViewById<TextView>(R.id.timeRemainingText)

            if (!isDarkMode) {
                amountText?.setTextColor(Color.parseColor("#0F172A"))
                statusText?.setTextColor(Color.parseColor("#0F172A"))
                stepText?.setTextColor(Color.parseColor("#64748B"))
                recipientText?.setTextColor(Color.parseColor("#0F172A"))
                timeRemaining?.setTextColor(Color.parseColor("#0284C7"))
                progressPercent?.setTextColor(Color.parseColor("#10B981"))
            } else {
                amountText?.setTextColor(Color.WHITE)
                statusText?.setTextColor(Color.WHITE)
                stepText?.setTextColor(Color.parseColor("#94A3B8"))
                recipientText?.setTextColor(Color.WHITE)
                timeRemaining?.setTextColor(Color.parseColor("#38BDF8"))
                progressPercent?.setTextColor(Color.parseColor("#10B981"))
            }

            // Set transaction details
            amountText?.text = getString(R.string.amount_rupees, amount)

            // Format phone number to show last 4 digits using PhoneNumberUtils
            val formattedPhone = PhoneNumberUtils.formatPhoneForDisplay(phoneNumber, 4)
            recipientText?.text = formattedPhone

            // Initialize progress & timeline
            progressBar?.max = 1000
            progressBar?.progress = 0
            progressPercent?.text = getString(R.string.percent_format, 0)
            timeRemaining?.text = getString(R.string.overlay_time_format, 0)
            statusText?.text = getString(R.string.overlay_processing_payment)
            stepText?.text = getString(R.string.overlay_connecting)
            updateTimelineStep(1)

            // Layout parameters tuned for visibility over the system dialer.
            val layoutParams = WindowManager.LayoutParams().apply {
                // Higher-priority window type so the overlay sits above the dialer.
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR // Higher priority than SYSTEM_ALERT
                }

                // Flags chosen so the overlay shows over the dialer and never steals touch outside.
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }

                format = PixelFormat.TRANSLUCENT
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER

                // Window title, so the overlay is identifiable in `dumpsys window`.
                title = "Netless Overlay"
            }

            // Ensure overlayView is immediately fully visible
            overlayView?.alpha = 1f
            overlayView?.scaleX = 1f
            overlayView?.scaleY = 1f
            overlayView?.translationY = 0f
            overlayView?.visibility = View.VISIBLE

            // Clean up any stale parent attachment before adding
            if (overlayView?.parent != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing existing overlayView parent: ${e.message}")
                }
            }

            // Add the overlay view with error handling
            try {
                Log.d(TAG, "Adding overlay view to WindowManager...")
                Log.d(TAG, "overlayView: $overlayView")
                Log.d(TAG, "layoutParams: $layoutParams")

                windowManager?.addView(overlayView, layoutParams)
                isOverlayActive = true
                isOverlayShowing = true

                OverlayLogger.logOverlayState(
                    "CREATED",
                    true,
                    details = mapOf(
                        "phoneNumber" to phoneNumber,
                        "amount" to amount,
                        "isOverlayActive" to isOverlayActive,
                        "isOverlayShowing" to isOverlayShowing
                    )
                )

                Log.d(TAG, "System overlay created and shown successfully")
                Log.d(TAG, "isOverlayActive: $isOverlayActive, isOverlayShowing: $isOverlayShowing")

                // Fluid entrance animation (alpha 0.85 -> 1.0) ensuring view is always visible
                try {
                    overlayView?.alpha = 0.85f
                    overlayView?.animate()
                        ?.alpha(1f)
                        ?.setDuration(300)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                        ?.start()
                } catch (e: Exception) {
                    overlayView?.alpha = 1f
                }

                // Overlay content reflects the real payment session state
                // (no fake timed progress, no separate call monitor — both
                // are driven by the collectors wired up in onCreate()).
                // Kept below PROGRESS_IN_CALL_START so the bar only ever moves
                // forward: dial 10% → call answered 20% → creep to 90% over
                // PROGRESS_IN_CALL_CREEP_MS → 100% only when the request is
                // genuinely sent.
                updateOverlayStatus(
                    status = getString(R.string.overlay_processing_payment),
                    step = getString(R.string.overlay_connecting),
                    progressFraction = PROGRESS_CONNECTING
                )

                // Setup toggle collapse button
                setupToggleCollapseButton()

                // Setup terminate button
                setupTerminateButton()

                // Immediately kick off live 40-second timeline progress and stage illumination
                startInCallPacing()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add overlay view: ${e.message}")
                // Clean up on failure
                overlayView = null
                isOverlayActive = false
                isOverlayShowing = false
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create system overlay: ${e.message}")
        }
    }

    /**
     * Sets up toggle collapse/close button allowing user to cancel/dismiss overlay
     * and terminate the active call.
     */
    private fun setupToggleCollapseButton() {
        val toggleButton = overlayView?.findViewById<android.widget.ImageButton>(R.id.btnToggleCollapse)
        toggleButton?.setOnClickListener {
            Log.d(TAG, "Overlay top-right close button clicked - terminating call")
            handleTerminateCall()
        }
    }

    /**
     * Setup terminate button click handler
     */
    private fun setupTerminateButton() {
        val terminateButton = overlayView?.findViewById<android.widget.Button>(R.id.terminateButton)
        if (terminateButton != null) {
            Log.d(TAG, "Terminate button found, setting up click listener")
            terminateButton.setOnClickListener {
                Log.d(TAG, "Terminate button clicked - starting termination process")
                handleTerminateCall()
            }
        } else {
            Log.e(TAG, "Terminate button not found in overlay view!")
        }
    }

    /**
     * Handle terminate call button click
     */
    private fun handleTerminateCall() {
        Log.d(TAG, "=== HANDLING TERMINATE CALL REQUEST ===")

        sessionManager?.onUserCancelled()

        try {
            com.netless.app.helpers.TransactionDetector.getInstance(this).stopOperation()
            callManager?.restoreCallVolume()

            val callTerminated = callManager?.terminateCall() ?: false
            Log.d(TAG, "Call terminated programmatically: $callTerminated")
            if (!callTerminated) {
                Log.w(TAG, "Failed to terminate call programmatically")
                Toast.makeText(
                    this,
                    "Unable to end the call automatically. Please hang up manually.",
                    Toast.LENGTH_LONG
                ).show()
            }

            hideOverlayInternal()
            dialogManager?.showTransactionCancelledByUser()
            Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling terminate call: ${e.message}", e)
            hideOverlayInternal()
            Handler(Looper.getMainLooper()).postDelayed({ stopSelf() }, 1000)
        }
    }

    private fun animateProgressBar(progressBar: android.widget.ProgressBar, targetProgress: Int) {
        // Tracked in the same field as the in-call creep so the two can never
        // run at once and drag the bar in opposite directions.
        progressAnimator?.cancel()
        progressAnimator = android.animation.ObjectAnimator.ofInt(
            progressBar,
            "progress",
            progressBar.progress,
            targetProgress
        ).apply {
            duration = PROGRESS_MILESTONE_ANIM_MS
            start()
        }
    }

    private fun hideOverlayInternal() {
        try {
            // Stop pacing first: a running animator must never outlive the
            // view it drives.
            cancelProgressPacing()
            if (!isOverlayActive || overlayView == null) {
                Log.w(TAG, "Overlay not active, ignoring hide request")
                return
            }

            Log.d(TAG, "Hiding system overlay")

            // Remove the overlay view from window manager
            windowManager?.removeView(overlayView)
            overlayView = null
            isOverlayActive = false
            isOverlayShowing = false

            Log.d(TAG, "System overlay hidden successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide system overlay: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            // Cancel timeout timer
            timeoutRunnable?.let {
                timeoutHandler?.removeCallbacks(it)
            }

            // Stop observing call/payment state
            serviceScope.cancel()
            if (coordinatorAcquired) {
                coordinator?.release("call-overlay-service")
                coordinatorAcquired = false
            }

            hideOverlayInternal() // Clean up overlay if still active
        } catch (e: Exception) {
            Log.e(TAG, "Error during service teardown: ${e.message}")
        }
        isOverlayActive = false
        isOverlayShowing = false
        isCallDetected = false
        hasBeenOffHook = false

        serviceInstance = null
        Log.d(TAG, "CallOverlayService destroyed - ready for restart")
    }
}
