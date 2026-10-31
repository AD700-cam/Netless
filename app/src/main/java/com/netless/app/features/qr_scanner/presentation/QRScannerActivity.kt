// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

@file:Suppress("ImportOrdering", "NoUnusedImports")

package com.netless.app.features.qr_scanner.presentation

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.telecom.TelecomManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.netless.app.NetlessApplication
import com.netless.app.R
import com.netless.app.data.TransactionSource
import com.netless.app.data.UPIData
import com.netless.app.features.qr_scanner.domain.QRCodeAnalyzer
import com.netless.app.features.qr_scanner.domain.QRCodeParser
import com.netless.app.features.qr_scanner.domain.messageFor
import com.netless.app.helpers.SetupHelper
import com.netless.app.helpers.TransactionDetector
import com.netless.app.managers.CallManager
import com.netless.app.managers.PermissionManager
import com.netless.app.payment.PaymentInputValidator
import com.netless.app.payment.PaymentSessionManager
import com.netless.app.repository.TransactionRepository
import com.netless.app.states.PaymentState
import com.netless.app.data.TransactionStatus
import com.netless.app.services.CallOverlayService
import com.netless.app.services.UssdAccessibilityService
import com.netless.app.ui.activities.PaymentResultActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : ComponentActivity() {

    companion object {
        const val RESULT_CANCELLED = 0
        const val RESULT_SUCCESS = 1
        const val RESULT_ERROR = 2

        /** How long the invalid-QR banner stays up before fading out. */
        private const val SCAN_ERROR_VISIBLE_MS = 3500L

        /**
         * How long the waiting screen stays up before handing the user back
         * to the main screen. Derived from the session's own verification
         * deadline so the two can never drift: this used to be a hardcoded
         * 150 s, a quarter of the deadline, which meant a user still working
         * through the *99# menus (paste VPA, amount, UPI PIN) lost the screen
         * — and, before the `finishedByWaitingScreenTimeout` guard, the
         * payment with it.
         */
        /** Automated verification timeout for USSD dialing before falling back to background watch. */
        private const val USSD_VERIFY_TIMEOUT_MS = PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS

        /** Longest edge a gallery image is downsampled to before decoding. */
        private const val GALLERY_MAX_EDGE_PX = 2048
    }

    private lateinit var viewFinder: PreviewView
    private lateinit var scannerOverlay: View
    private lateinit var progressBar: View
    private lateinit var tvStatus: TextView
    private lateinit var tvScanError: TextView
    private lateinit var btnBack: View
    private lateinit var btnTerminate: View
    private lateinit var layoutWaitingActions: android.widget.LinearLayout
    private lateinit var btnViewRecentPayments: android.widget.Button
    private lateinit var btnCancelPayment: android.widget.Button
    private var activePayeeVpa: String = ""
    private var activeAmount: String = ""
    private lateinit var topBar: View
    private lateinit var instructionsBox: View
    private lateinit var instructionsHeaderInitial: View
    private lateinit var instructionsExpanded: View
    private lateinit var btnFlash: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var bottomActionBar: View
    private lateinit var scanLine: View
    private var scanLineAnimator: android.animation.ObjectAnimator? = null
    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var isFlashOn = false
    private lateinit var permissionManager: PermissionManager
    private var isUSSDProcessActive = false
    private var messageHandler: android.os.Handler? = null
    private var smsTimeoutHandler: android.os.Handler? = null
    private var smsTimeoutRunnable: Runnable? = null

    // Centralized handler for all delayed callbacks — cleaned up in onDestroy
    private val mainHandler = Handler(Looper.getMainLooper())

    // Guards against processing the same QR more than once per flow.
    @Volatile
    private var isProcessingQRCode = false

    // Ensures USSD is dialed once per payment flow — without this, returning
    // from the dialer re-triggers the dial and loops.
    @Volatile
    private var hasDialedUSSD = false

    // Set when this screen closes itself on [WAITING_SCREEN_TIMEOUT_MS]
    // rather than because the user abandoned the payment. The two must not be
    // conflated: onDestroy cancels the session on `isFinishing`, and a cancel
    // disarms the SMS window (PaymentWindowObserver), but the SMS window
    // deliberately outlives the verification deadline by a grace margin so a
    // slow bank confirmation is never dropped. When this timer was a hardcoded
    // 150 s — a quarter of the deadline — reaching that cancel path capped the
    // QR rail's real confirmation window at 150 s: a user still inside the
    // *99# menus (paste VPA, amount, UPI PIN) when it fired had the window
    // closed under them, so the payment went through and the confirming SMS
    // arrived to a closed window — money moved, history said CANCELLED, and
    // nothing surfaced. The timer may dismiss the screen; it may not end the
    // payment.
    @Volatile
    private var finishedByWaitingScreenTimeout = false

    // Set when this screen closes because a UPI 123Pay payment was successfully
    // handed off to CallOverlayService and telephony dialer. onDestroy must not
    // cancel the payment session on this exit path.
    @Volatile
    private var finishedByPaymentInitiated = false

    private var pendingCallAction: (() -> Unit)? = null
    private var paymentInputDialog: android.app.AlertDialog? = null

    // Whether this flow put a payee VPA on the clipboard (wiped in onDestroy).
    private var didCopyVpa = false

    // Permission launcher for runtime permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionResult(permissions)
    }

    // Permission launcher for CALL_PHONE permission in payment flows (USSD or UPI 123Pay)
    private val callPhonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val action = pendingCallAction ?: { dialUSSD() }
            pendingCallAction = null
            action.invoke()
        } else {
            pendingCallAction = null
            showError(getString(R.string.error_call_permission_required_payments))
        }
    }

    /**
     * Android's photo picker. Deliberately NOT a storage permission: the
     * picker returns a one-shot read grant for the single image the user
     * chose, so the app never gains access to the gallery as a whole. That
     * keeps the "no storage permission" claim in README/ARCHITECTURE true.
     */
    private val galleryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) {
            Log.d("QRScanner", "Gallery pick cancelled")
        } else {
            decodeQrFromGallery(uri)
        }
    }

    /**
     * Keeps the floating chrome clear of the system bars.
     *
     * On targetSdk 35 the window is edge-to-edge, and this activity applied no
     * insets at all — so the instructions card sat at y=0, underneath the
     * status bar and the display cutout, with its first line clipped. The
     * camera preview is deliberately left full-bleed; only the overlay chrome
     * is inset, so the viewfinder still fills the screen.
     */
    private fun applyWindowInsets() {
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            (instructionsBox.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = bars.top + resources.getDimensionPixelSize(R.dimen.qr_chrome_gap)
                instructionsBox.layoutParams = it
            }
            (topBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.topMargin = bars.top
                topBar.layoutParams = it
            }
            (bottomActionBar.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                it.bottomMargin = bars.bottom +
                    resources.getDimensionPixelSize(R.dimen.qr_bottom_bar_gap)
                bottomActionBar.layoutParams = it
            }
            windowInsets
        }
    }

    /**
     * Check if the activity is still alive and safe to access views
     */
    private fun isActivityAlive(): Boolean = !isFinishing && !isDestroyed

    // Broadcast receiver for USSD overlay dismissal
    private val overlayDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "DISMISS_OVERLAY") {
                Log.d("QRScanner", "USSD overlay dismissed - continuing to wait for SMS")
                if (isUSSDProcessActive) {
                    // Don't close immediately, wait for SMS
                    updateBlackScreenStatus(R.string.qr_status_ussd_completed)
                }
            }
        }
    }

    // Broadcast receiver for SMS detection
    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.netless.app.SMS_RECEIVED") {
                Log.d("QRScanner", "SMS received - transaction successful!")
                if (isUSSDProcessActive) {
                    handleSMSReceived(intent)
                }
            }
        }
    }

    // Broadcast receiver for automated USSD confirmation detection
    private val ussdConfirmedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UssdAccessibilityService.ACTION_USSD_PAYMENT_CONFIRMED) {
                val refId = intent.getStringExtra(UssdAccessibilityService.EXTRA_REF_ID) ?: ""
                val amount = intent.getStringExtra(UssdAccessibilityService.EXTRA_AMOUNT)
                Log.d("QRScanner", "USSD confirmation broadcast received - Ref: $refId, Amount: $amount")
                handleUssdConfirmed(refId, amount)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.d("QRScanner", "=== QR SCANNER ACTIVITY CREATED ===")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_qr_scanner)

            SetupHelper.getScanToPayBlockedMessage(this)?.let { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                setResult(RESULT_CANCELLED)
                finish()
                return
            }

            // Initialize views
            viewFinder = findViewById(R.id.viewFinder)
            scannerOverlay = findViewById(R.id.scannerOverlay)
            progressBar = findViewById(R.id.progressBar)
            tvStatus = findViewById(R.id.tvStatus)
            tvScanError = findViewById(R.id.tvScanError)
            btnBack = findViewById(R.id.btnBack)
            btnTerminate = findViewById(R.id.btnTerminate)
            layoutWaitingActions = findViewById(R.id.layoutWaitingActions)
            btnCancelPayment = findViewById(R.id.btnCancelPayment)
            btnViewRecentPayments = findViewById(R.id.btnViewRecentPayments)
            btnViewRecentPayments.setOnClickListener {
                Log.d("QRScanner", "View in Recent Payments clicked")
                setResult(RESULT_SUCCESS)
                finish()
            }
            topBar = findViewById(R.id.topBar)
            instructionsBox = findViewById(R.id.instructionsBox)
            instructionsHeaderInitial = findViewById(R.id.instructionsHeaderInitial)
            instructionsExpanded = findViewById(R.id.instructionsExpanded)
            btnFlash = findViewById(R.id.btnFlash)
            btnGallery = findViewById(R.id.btnGallery)
            bottomActionBar = findViewById(R.id.bottomActionBar)
            scanLine = findViewById(R.id.scanLine)

            applyWindowInsets()

            Log.d("QRScanner", "Views initialized successfully")

            // Initialize permission manager
            permissionManager = PermissionManager(this)
            Log.d("QRScanner", "Permission manager initialized")

            cameraExecutor = Executors.newSingleThreadExecutor()
            Log.d("QRScanner", "Camera executor created")

            btnBack.setOnClickListener {
                Log.d("QRScanner", "Back button clicked")
                if (isUSSDProcessActive || hasDialedUSSD) {
                    showCancelConfirmationDialog()
                } else {
                    setResult(RESULT_CANCELLED)
                    finish()
                }
            }

            btnTerminate.setOnClickListener {
                Log.d("QRScanner", "Terminate button clicked")
                showCancelConfirmationDialog()
            }

            btnCancelPayment.setOnClickListener {
                Log.d("QRScanner", "Cancel payment clicked")
                showCancelConfirmationDialog()
            }

            btnFlash.setOnClickListener {
                isFlashOn = !isFlashOn
                camera?.cameraControl?.enableTorch(isFlashOn)
                btnFlash.setImageResource(
                    if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                )
                btnFlash.setBackgroundResource(
                    if (isFlashOn) {
                        R.drawable.bottom_action_button_active_bg
                    } else {
                        R.drawable.bottom_action_button_bg
                    }
                )
            }

            btnGallery.setOnClickListener {
                Log.d("QRScanner", "Gallery picker opened")
                galleryPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }

            // Register broadcast receivers
            val overlayFilter = IntentFilter("DISMISS_OVERLAY")
            LocalBroadcastManager.getInstance(this).registerReceiver(overlayDismissReceiver, overlayFilter)

            val smsFilter = IntentFilter("com.netless.app.SMS_RECEIVED")
            LocalBroadcastManager.getInstance(this).registerReceiver(smsReceiver, smsFilter)

            val ussdFilter = IntentFilter(UssdAccessibilityService.ACTION_USSD_PAYMENT_CONFIRMED)
            LocalBroadcastManager.getInstance(this).registerReceiver(ussdConfirmedReceiver, ussdFilter)

            Log.d("QRScanner", "Starting permission check...")
            checkPermissionsAndStartCamera()
        } catch (e: Exception) {
            Log.e("QRScanner", "Error in onCreate", e)
            Toast.makeText(this, R.string.error_qr_scanner_init, Toast.LENGTH_LONG).show()
            setResult(RESULT_ERROR)
            finish()
        }
    }

    /**
     * Check CAMERA permission before starting camera
     */
    private fun checkPermissionsAndStartCamera() {
        Log.d("QRScanner", "Checking CAMERA permission before starting camera")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("QRScanner", "Camera permission not granted, requesting...")
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            Log.d("QRScanner", "Camera permission already granted, starting camera")
            startCamera()
        }
    }

    /**
     * Handle CAMERA permission request results
     */
    private fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true

        if (cameraGranted) {
            Log.d("QRScanner", "Camera permission granted, starting camera")
            startCamera()
        } else {
            Log.w("QRScanner", "Camera permission denied")
            Toast.makeText(
                this,
                "Camera permission is required for QR scanning",
                Toast.LENGTH_LONG
            ).show()
            setResult(RESULT_ERROR)
            finish()
        }
    }

    private fun startCamera() {
        try {
            Log.d("QRScanner", "Starting camera initialization...")
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

            cameraProviderFuture.addListener({
                try {
                    if (!isActivityAlive()) return@addListener

                    val cameraProvider = cameraProviderFuture.get()
                    Log.d("QRScanner", "Camera provider obtained successfully")

                    // Preview
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(viewFinder.surfaceProvider)
                    }
                    Log.d("QRScanner", "Preview configured")

                    // QR Code Analysis
                    imageAnalyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                cameraExecutor,
                                QRCodeAnalyzer { qrCode ->
                                    processQRCode(qrCode)
                                }
                            )
                        }
                    Log.d("QRScanner", "Image analyzer configured")

                    // Select back camera
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        // Unbind use cases before rebinding
                        cameraProvider.unbindAll()
                        Log.d("QRScanner", "Previous use cases unbound")

                        // Bind use cases to camera — safe null check instead of !!
                        val analyzer = imageAnalyzer ?: run {
                            Log.e("QRScanner", "Image analyzer is null, cannot bind camera")
                            return@addListener
                        }
                        camera = cameraProvider.bindToLifecycle(
                            this, cameraSelector, preview, analyzer
                        )
                        Log.d("QRScanner", "Camera use cases bound successfully")
                        runOnUiThread { startScanLineAnimation() }
                    } catch (exc: Exception) {
                        Log.e("QRScanner", "Use case binding failed", exc)
                        runOnUiThread {
                            if (!isActivityAlive()) return@runOnUiThread
                            Toast.makeText(
                                this,
                                R.string.error_camera_init_failed,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("QRScanner", "Error in camera setup", e)
                    runOnUiThread {
                        if (!isActivityAlive()) return@runOnUiThread
                        Toast.makeText(this, R.string.error_camera_setup, Toast.LENGTH_LONG).show()
                    }
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e("QRScanner", "Failed to start camera", e)
            Toast.makeText(this, R.string.error_camera_setup, Toast.LENGTH_LONG).show()
        }
    }

    private fun processQRCode(qrCode: String) {
        try {
            // Already processing a QR — a second decode must not dial again.
            if (isProcessingQRCode) {
                Log.d("QRScanner", "Already processing QR code, ignoring duplicate detection")
                return
            }

            // Set processing flag
            isProcessingQRCode = true

            // Stop scanning
            imageAnalyzer?.clearAnalyzer()

            Log.d("QRScanner", "QR Code detected: ${qrCode.take(100)}...")

            // Show loading
            runOnUiThread {
                if (!isActivityAlive()) return@runOnUiThread
                scannerOverlay.visibility = View.GONE
                bottomActionBar.visibility = View.GONE
                scanLineAnimator?.cancel()
                progressBar.visibility = View.VISIBLE
                tvStatus.text = getString(R.string.qr_processing)
                tvStatus.visibility = View.VISIBLE
            }

            // Parse UPI data. A code that isn't a usable UPI QR is a normal,
            // expected thing to point a scanner at — it must not be treated
            // like a payment failure, and the parser's specific reason is far
            // more useful to the user than a generic "invalid".
            when (val result = QRCodeParser.parse(qrCode)) {
                is QRCodeParser.ParseResult.Valid -> {
                    Log.d("QRScanner", "Successfully parsed UPI data")
                    handleParsedQrCode(result.data)
                }
                is QRCodeParser.ParseResult.Invalid -> {
                    Log.w("QRScanner", "QR rejected: ${result.reason.name}")
                    showInvalidQrCode(result.reason.messageFor(this))
                }
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Error processing QR code", e)
            showError(getString(R.string.error_qr_processing))
            // Reset processing flag on error
            isProcessingQRCode = false
        }
    }

    private fun handleParsedQrCode(upiData: UPIData) {
        val isUssdAvailable = SetupHelper.isScanToPayUssdAvailable(this)
        if (isUssdAvailable) {
            initiateUSSDPayment(upiData)
            return
        }

        // On non-USSD carriers (Jio) or where USSD is disabled, route via UPI 123Pay
        val extractedPhone = upiData.phoneNumber ?: QRCodeParser.extractPhoneNumber(upiData.vpa)
        val normalizedAmount = PaymentInputValidator.normalizeToWholeRupees(upiData.amount)
        val hasValidAmount = PaymentInputValidator.isValidUpi123Amount(normalizedAmount)

        if (extractedPhone != null) {
            if (hasValidAmount) {
                proceedWithUpi123Payment(extractedPhone, normalizedAmount, upiData)
            } else {
                showPaymentInputDialog(
                    upiData = upiData,
                    phone = extractedPhone,
                    requirePhoneInput = false,
                    requireAmountInput = true
                )
            }
        } else {
            showPaymentInputDialog(
                upiData = upiData,
                phone = null,
                requirePhoneInput = true,
                requireAmountInput = !hasValidAmount
            )
        }
    }

    @Suppress("LongMethod")
    private fun showPaymentInputDialog(
        upiData: UPIData,
        phone: String?,
        requirePhoneInput: Boolean,
        requireAmountInput: Boolean
    ) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread

            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_payment_input, null)
            val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
            val tvDialogPayee = dialogView.findViewById<TextView>(R.id.tvDialogPayee)
            val tvDialogNotice = dialogView.findViewById<TextView>(R.id.tvDialogNotice)
            val layoutPhoneInput = dialogView.findViewById<View>(R.id.layoutPhoneInput)
            val etPhoneNumber = dialogView.findViewById<EditText>(R.id.etPhoneNumber)
            val layoutAmountInput = dialogView.findViewById<View>(R.id.layoutAmountInput)
            val etAmount = dialogView.findViewById<EditText>(R.id.etAmount)
            val tvDialogError = dialogView.findViewById<TextView>(R.id.tvDialogError)
            val btnDialogCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
            val btnDialogPay = dialogView.findViewById<Button>(R.id.btnDialogPay)

            val payeeDisplay = if (upiData.payeeName.isNotBlank()) {
                getString(R.string.qr_dialog_payee_format, upiData.payeeName, upiData.vpa)
            } else {
                upiData.vpa
            }
            tvDialogPayee.text = payeeDisplay

            if (requirePhoneInput) {
                tvDialogTitle.setText(R.string.qr_dialog_jio_title)
                tvDialogNotice.visibility = View.VISIBLE
                layoutPhoneInput.visibility = View.VISIBLE
            } else {
                tvDialogTitle.setText(R.string.qr_dialog_enter_amount_title)
                tvDialogNotice.visibility = View.GONE
                layoutPhoneInput.visibility = View.GONE
            }

            if (requireAmountInput) {
                layoutAmountInput.visibility = View.VISIBLE
                val prefill = PaymentInputValidator.normalizeToWholeRupees(upiData.amount)
                if (PaymentInputValidator.isValidUpi123Amount(prefill)) {
                    etAmount.setText(prefill)
                }

                dialogView.findViewById<View>(R.id.chipAmount50)?.setOnClickListener {
                    etAmount.setText(getString(R.string.amount_val_50))
                    etAmount.setSelection(etAmount.text.length)
                }
                dialogView.findViewById<View>(R.id.chipAmount100)?.setOnClickListener {
                    etAmount.setText(getString(R.string.amount_val_100))
                    etAmount.setSelection(etAmount.text.length)
                }
                dialogView.findViewById<View>(R.id.chipAmount200)?.setOnClickListener {
                    etAmount.setText(getString(R.string.amount_val_200))
                    etAmount.setSelection(etAmount.text.length)
                }
                dialogView.findViewById<View>(R.id.chipAmount500)?.setOnClickListener {
                    etAmount.setText(getString(R.string.amount_val_500))
                    etAmount.setSelection(etAmount.text.length)
                }
                dialogView.findViewById<View>(R.id.chipAmount1000)?.setOnClickListener {
                    etAmount.setText(getString(R.string.amount_val_1000))
                    etAmount.setSelection(etAmount.text.length)
                }
            } else {
                layoutAmountInput.visibility = View.GONE
            }

            val builder = android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .setOnCancelListener {
                    paymentInputDialog = null
                    resumeScanning()
                }
            val dialog = builder.create()

            paymentInputDialog = dialog
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            btnDialogCancel.setOnClickListener {
                dialog.dismiss()
                paymentInputDialog = null
                resumeScanning()
            }

            btnDialogPay.setOnClickListener {
                val targetPhone = if (requirePhoneInput) {
                    etPhoneNumber.text?.toString()?.trim().orEmpty()
                } else {
                    phone.orEmpty()
                }

                val rawAmount = if (requireAmountInput) {
                    etAmount.text?.toString()?.trim().orEmpty()
                } else {
                    upiData.amount.trim()
                }
                val targetAmount = PaymentInputValidator.normalizeToWholeRupees(rawAmount)

                if (requirePhoneInput && !PaymentInputValidator.isValidPhoneNumber(targetPhone)) {
                    tvDialogError.setText(R.string.qr_dialog_error_phone)
                    tvDialogError.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                if (!PaymentInputValidator.isValidUpi123Amount(targetAmount)) {
                    tvDialogError.setText(R.string.qr_dialog_error_amount)
                    tvDialogError.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                tvDialogError.visibility = View.GONE
                dialog.dismiss()
                paymentInputDialog = null
                proceedWithUpi123Payment(targetPhone, targetAmount, upiData)
            }

            dialog.show()
        }
    }

    private fun proceedWithUpi123Payment(phoneNumber: String, amount: String, upiData: UPIData) {
        try {
            Log.d("QRScanner", "=== STARTING UPI 123PAY QR PAYMENT PROCESS ===")
            val cleanPhone = phoneNumber.filter(Char::isDigit)
            val cleanAmount = PaymentInputValidator.normalizeToWholeRupees(amount)

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("QRScanner", "Requesting CALL_PHONE permission for UPI 123Pay")
                pendingCallAction = { proceedWithUpi123Payment(cleanPhone, cleanAmount, upiData) }
                callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                return
            }

            activePayeeVpa = upiData.vpa
            activeAmount = cleanAmount

            val sessionManager = NetlessApplication.from(this)?.paymentSessionManager
            val sessionTxnId = sessionManager?.begin(
                phoneNumber = cleanPhone,
                amount = cleanAmount,
                upiId = upiData.vpa,
                source = TransactionSource.QR
            )

            try {
                TransactionDetector.getInstance(this).startOperation(
                    operationType = "UPI_123",
                    expectedAmount = cleanAmount,
                    phoneNumber = cleanPhone,
                    sessionTxnId = sessionTxnId
                )
                Log.d("QRScanner", "TransactionDetector monitoring started for UPI 123Pay QR payment")
            } catch (e: Exception) {
                Log.e("QRScanner", "Failed to start TransactionDetector: ${e.message}")
            }

            showBlackScreenWithStatus(R.string.qr_status_initiating_123pay)

            val callManager = CallManager(this)
            CallOverlayService.showOverlay(this, cleanPhone, cleanAmount)
            val success = callManager.initiateUPI123Call(cleanPhone, cleanAmount)
            if (success) {
                Log.d("QRScanner", "UPI 123Pay call initiated successfully, overlay shown")
                finishedByPaymentInitiated = true
                setResult(RESULT_SUCCESS)
                finish()
            } else {
                Log.e("QRScanner", "Failed to initiate UPI 123Pay call")
                CallOverlayService.hideOverlay(this)
                sessionManager?.onDialFailed("Could not start the payment call")
                TransactionDetector.getInstance(this).stopOperation()
                showError(getString(R.string.error_payment_init_failed))
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Unexpected error in proceedWithUpi123Payment: ${e.message}", e)
            showError(getString(R.string.error_unexpected))
        }
    }

    private fun initiateUSSDPayment(upiData: UPIData) {
        // Always proceed with payment - skip amount dialog
        // VPA will be copied to clipboard and USSD will be dialed instantly
        proceedWithPayment(upiData)
    }

    private fun proceedWithPayment(upiData: UPIData) {
        try {
            Log.d("QRScanner", "=== STARTING SIMPLE QR PAYMENT PROCESS ===")
            activePayeeVpa = upiData.vpa
            activeAmount = upiData.amount ?: ""

            // Validate VPA
            if (upiData.vpa.isBlank()) {
                Log.e("QRScanner", "VPA is blank, cannot proceed")
                showError(getString(R.string.error_qr_invalid_vpa))
                return
            }

            if (!beginQrPaymentSession(upiData)) return

            // Show black screen with status
            showBlackScreenWithStatus(R.string.qr_status_processing_payment)

            // 1. Copy VPA to clipboard so the user can paste it into the USSD
            //    menu. It is a payee identifier (PII), so it is flagged
            //    sensitive and wiped in onDestroy when the flow ends.
            copyVpaToClipboard(upiData.vpa)

            // 2. Dial USSD code after a short delay (only once per flow)
            mainHandler.postDelayed({
                try {
                    if (!isActivityAlive()) return@postDelayed
                    if (hasDialedUSSD) {
                        Log.d("QRScanner", "USSD already dialed this flow, skipping duplicate dial")
                        return@postDelayed
                    }
                    hasDialedUSSD = true
                    checkAccessibilityAndDial()
                } catch (e: Exception) {
                    Log.e("QRScanner", "Failed to dial USSD: ${e.message}", e)
                    showError(getString(R.string.error_ussd_call_failed))
                }
            }, 1000) // 1 second delay to ensure overlay is ready
        } catch (e: Exception) {
            Log.e("QRScanner", "Unexpected error in proceedWithPayment: ${e.message}", e)
            showError(getString(R.string.error_unexpected))
        }
    }

    private fun checkAccessibilityAndDial() {
        if (UssdAccessibilityService.isEnabled(this)) {
            Log.d("QRScanner", "Accessibility enabled. Zero-touch USSD active.")
        } else {
            Log.d("QRScanner", "Accessibility not enabled. SMS and overlay monitoring active.")
        }
        updateBlackScreenStatus(R.string.qr_status_initiating_call)
        dialUSSD()
    }

    /**
     * Starts the payment session (PENDING row + verification deadline, same
     * lifecycle as the manual flow) and opens the SMS operation window for
     * it. Returns false — after showing the user why — when the payment must
     * not proceed. The QR flow used to bypass the session manager entirely,
     * so an unconfirmed QR payment left no trace at all.
     */
    private fun beginQrPaymentSession(upiData: UPIData): Boolean {
        val sessionManager = NetlessApplication.from(this)?.paymentSessionManager
        val sessionTxnId = sessionManager?.begin(
            phoneNumber = "",
            amount = upiData.amount ?: "",
            upiId = upiData.vpa,
            source = TransactionSource.QR
        )
        // No "already in progress" refusal: begin() supersedes any session
        // still in flight, so a scan is never blocked by a previous payment
        // the bank never confirmed.
        return try {
            TransactionDetector.getInstance(this).startOperation(
                operationType = "QR_SCAN",
                expectedAmount = upiData.amount,
                sessionTxnId = sessionTxnId
            )
            Log.d("QRScanner", "SMS monitoring started for QR payment")
            true
        } catch (e: IllegalStateException) {
            Log.e("QRScanner", "Failed to start SMS monitoring: ${e.message}")
            sessionManager?.onDialFailed("Could not start SMS monitoring")
            showError(getString(R.string.error_payment_init_failed))
            false
        }
    }

    /**
     * Copies the payee VPA to the clipboard so the user can paste it into the
     * USSD menu. Flagged sensitive on Android 13+ so it stays out of clipboard
     * previews; [clearVpaClipboard] wipes it when the flow ends.
     */
    private fun copyVpaToClipboard(vpa: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("VPA", vpa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
            didCopyVpa = true
            Log.d("QRScanner", "VPA copied to clipboard successfully")
            // No status update here: the system already shows its own
            // "copied" confirmation on Android 13+, and step 1 of the
            // on-screen instructions already tells the user to paste it.
            // Announcing it a third time was just noise.
        } catch (e: SecurityException) {
            // Not critical — the flow can continue without the clipboard copy.
            Log.e("QRScanner", "Failed to copy VPA to clipboard: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("QRScanner", "Failed to copy VPA to clipboard: ${e.message}")
        }
    }

    /**
     * Clears the payee VPA so it doesn't linger for other apps.
     *
     * Uses clearPrimaryClip() rather than writing an empty clip: on Android
     * 13+ EVERY setPrimaryClip() raises the system "Copied to clipboard"
     * chip, so overwriting produced a second, baffling "copied" popup at the
     * moment the session ended — after the user had already finished. Clearing
     * is not a copy, so nothing is announced, and it genuinely empties the
     * clipboard instead of parking an empty string on it.
     */
    private fun clearVpaClipboard() {
        if (!didCopyVpa) return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.clearPrimaryClip()
            didCopyVpa = false
            Log.d("QRScanner", "VPA cleared from clipboard")
        } catch (e: SecurityException) {
            Log.e("QRScanner", "Error clearing clipboard: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e("QRScanner", "Error clearing clipboard: ${e.message}")
        }
    }

    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    private fun dialUSSD() {
        val ussdCode = "*99*1*3#"
        val encodedHash = Uri.encode("#")
        val ussd = ussdCode.replace("#", encodedHash)

        Log.d("QRScanner", "Preparing to dial USSD: $ussdCode")

        // Check CALL_PHONE permission before dialing — use modern launcher API
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("QRScanner", "CALL_PHONE permission not granted - requesting")
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            return
        }

        updateBlackScreenStatus(R.string.qr_status_call_initiated)

        var callStarted = false
        val callUri = Uri.parse("tel:$ussd")

        // Tier 1: Try ACTION_CALL with selected SIM handle if available
        val accountHandle = try {
            CallManager(this).getPhoneAccountHandleForSelectedSim()
        } catch (e: Exception) {
            Log.w("QRScanner", "Failed resolving PhoneAccountHandle: ${e.message}")
            null
        }

        if (accountHandle != null) {
            try {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = callUri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)
                }
                startActivity(intent)
                callStarted = true
                Log.d("QRScanner", "USSD call initiated with PhoneAccountHandle: ${accountHandle.id}")
            } catch (e: SecurityException) {
                Log.w("QRScanner", "ACTION_CALL with handle rejected by OS: ${e.message}. Retrying plain.")
            } catch (e: Exception) {
                Log.w("QRScanner", "ACTION_CALL with handle failed: ${e.message}. Retrying plain.")
            }
        }

        // Tier 2: Standard ACTION_CALL without extra account handle (standard for third-party apps)
        if (!callStarted) {
            try {
                val plainIntent = Intent(Intent.ACTION_CALL).apply {
                    data = callUri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(plainIntent)
                callStarted = true
                Log.d("QRScanner", "USSD call initiated with plain ACTION_CALL")
            } catch (e: SecurityException) {
                Log.w("QRScanner", "Plain ACTION_CALL threw SecurityException: ${e.message}. Trying ACTION_DIAL.")
            } catch (e: Exception) {
                Log.w("QRScanner", "Plain ACTION_CALL failed: ${e.message}. Trying ACTION_DIAL.")
            }
        }

        // Tier 3: ACTION_DIAL fallback
        if (!callStarted) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = callUri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(dialIntent)
                callStarted = true
                Log.d("QRScanner", "USSD call opened via ACTION_DIAL fallback")
            } catch (e: Exception) {
                Log.e("QRScanner", "ACTION_DIAL fallback failed: ${e.message}", e)
            }
        }

        if (callStarted) {
            Log.d("QRScanner", "USSD call successfully initiated, entering waiting phase")
            startUSSDProcessMessages()
        } else {
            Log.e("QRScanner", "All attempts to initiate USSD call failed")
            NetlessApplication.from(this)?.paymentSessionManager
                ?.onDialFailed("Could not start the payment call")
            showError(getString(R.string.error_ussd_call_failed))
        }
    }

    /**
     * Start extended message sequence during USSD process
     */
    private fun startUSSDProcessMessages() {
        Log.d("QRScanner", "Entering USSD wait phase")
        isUSSDProcessActive = true

        // Honest, static status. Netless cannot see the USSD menu or the bank's
        // progress, so it does not fake step-by-step progress — it states what
        // the user should do and what it is genuinely waiting for.
        updateBlackScreenStatus(R.string.qr_status_complete_in_dialer)

        // Wait for the confirmation SMS, giving up after the timeout below.
        startSMSTimeout()
    }

    /**
     * Hand the user back to the main screen if no confirmation has arrived by
     * the time the payment session's own verification deadline is up. This is
     * a UI convenience only — see [WAITING_SCREEN_TIMEOUT_MS] and
     * [finishedByWaitingScreenTimeout]; it must never end the payment.
     */
    private fun startSMSTimeout() {
        Log.d("QRScanner", "Starting waiting-screen timer (35000ms)")

        smsTimeoutHandler = Handler(Looper.getMainLooper())
        smsTimeoutRunnable = Runnable {
            if (!isActivityAlive()) return@Runnable
            Log.d("QRScanner", "Waiting-screen timeout reached - showing bank pending dialog")
            if (isUSSDProcessActive) {
                finishedByWaitingScreenTimeout = true
                android.app.AlertDialog.Builder(this)
                    .setTitle(R.string.qr_waiting_bank_saved_title)
                    .setMessage(R.string.qr_waiting_bank_saved_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.action_ok) { _, _ ->
                        setResult(RESULT_SUCCESS)
                        finish()
                    }
                    .show()
            }
        }

        // 35 seconds gives the user time to complete the 4 steps in USSD dialer
        smsTimeoutHandler?.postDelayed(smsTimeoutRunnable!!, USSD_VERIFY_TIMEOUT_MS)
    }

    /**
     * Handle automated USSD confirmation detected by UssdAccessibilityService.
     */
    private fun handleUssdConfirmed(refId: String, amount: String?) {
        Log.d("QRScanner", "handleUssdConfirmed: refId=$refId, amount=$amount")
        finishedByPaymentInitiated = true

        // Cancel all timeouts and handlers
        smsTimeoutRunnable?.let { smsTimeoutHandler?.removeCallbacks(it) }
        messageHandler?.removeCallbacksAndMessages(null)

        val sessionManager = NetlessApplication.from(this)?.paymentSessionManager
        val confirmedTxnId = sessionManager?.onUssdConfirmed(refId, amount)

        val finalAmount = amount?.takeIf { it.isNotBlank() } ?: activeAmount
        val resultIntent = Intent(this, PaymentResultActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("transaction_id", confirmedTxnId ?: refId)
            putExtra("amount", finalAmount)
            putExtra("status", TransactionStatus.SUCCESS)
            putExtra("bank_name", "NPCI / *99# USSD")
            putExtra("message", "Payment Confirmed via USSD · Ref $refId")
            putExtra("timestamp", System.currentTimeMillis())
            putExtra("upi_id", activePayeeVpa)
            putExtra("transaction_type", "DEBIT")
        }
        startActivity(resultIntent)
        setResult(RESULT_SUCCESS)
        finish()
    }

    /**
     * Handle SMS received
     */
    private fun handleSMSReceived(intent: Intent) {
        Log.d("QRScanner", "SMS received - finishing QRScannerActivity immediately")

        // Cancel all timeouts and handlers
        smsTimeoutRunnable?.let {
            smsTimeoutHandler?.removeCallbacks(it)
        }
        messageHandler?.removeCallbacksAndMessages(null)

        // Dismiss USSD overlay

        // Hide any black screen or status messages
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            try {
                // Hide status text, instructions box, waiting actions and terminate button
                tvStatus.visibility = View.GONE
                instructionsBox.visibility = View.GONE
                layoutWaitingActions.visibility = View.GONE
                btnTerminate.visibility = View.GONE
                topBar.visibility = View.VISIBLE // Show top bar again

                // Reset instruction states for next time
                instructionsHeaderInitial.visibility = View.VISIBLE
                instructionsHeaderInitial.alpha = 1f
                instructionsExpanded.visibility = View.GONE
                instructionsExpanded.alpha = 1f

                // Reset background to normal
                findViewById<View>(
                    android.R.id.content
                ).setBackgroundColor(ContextCompat.getColor(this, R.color.background_secondary))
            } catch (e: Exception) {
                Log.e("QRScanner", "Error hiding black screen: ${e.message}")
            }
        }

        // Finish this activity immediately - SimpleSMSReceiver will handle showing success screen
        Log.d("QRScanner", "Finishing QRScannerActivity - SimpleSMSReceiver will show success screen")
        setResult(RESULT_SUCCESS)
        finish()
    }

    /**
     * Terminate USSD process and return to main screen
     */
    private fun terminateUSSDProcess() {
        try {
            Log.d("QRScanner", "Terminating USSD process...")

            // Stop USSD process
            isUSSDProcessActive = false

            // Cancel all timeouts and handlers
            messageHandler?.removeCallbacksAndMessages(null)
            smsTimeoutRunnable?.let {
                smsTimeoutHandler?.removeCallbacks(it)
            }

            // Stop SMS monitoring
            try {
                TransactionDetector.getInstance(this).stopOperation()
                Log.d("QRScanner", "SMS monitoring stopped")
            } catch (e: Exception) {
                Log.e("QRScanner", "Failed to stop SMS monitoring: ${e.message}")
            }

            // The user explicitly aborted: mark the session row CANCELLED so
            // it doesn't linger PENDING waiting for a payment nobody made.
            NetlessApplication.from(this)?.paymentSessionManager?.onUserCancelled()

            // Show termination message briefly
            runOnUiThread {
                if (!isActivityAlive()) return@runOnUiThread
                updateBlackScreenStatus(R.string.qr_status_terminated)
                layoutWaitingActions.visibility = View.GONE
                btnTerminate.visibility = View.GONE
            }

            // Return to main screen after brief delay
            mainHandler.postDelayed({
                if (!isActivityAlive()) return@postDelayed
                Log.d("QRScanner", "Returning to main screen after termination")
                setResult(RESULT_CANCELLED)
                finish()
            }, 1500)
        } catch (e: Exception) {
            Log.e("QRScanner", "Error terminating USSD process: ${e.message}", e)
            // Fallback: just close the activity
            setResult(RESULT_ERROR)
            finish()
        }
    }

    /**
     * Shows confirmation dialog before terminating an in-flight payment
     */
    private fun showCancelConfirmationDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.qr_dialog_cancel_confirm_title)
            .setMessage(R.string.qr_dialog_cancel_confirm_message)
            .setPositiveButton(R.string.qr_dialog_cancel_confirm_proceed) { _, _ ->
                terminateUSSDProcess()
            }
            .setNegativeButton(R.string.qr_dialog_cancel_confirm_wait) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show waiting screen with status message and Zenvest theme styling
     */
    private fun showBlackScreenWithStatus(@StringRes messageRes: Int = R.string.qr_status_processing_payment) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            val message = getString(messageRes)
            Log.d("QRScanner", "Showing waiting screen with status: $message")

            // Hide camera and scanner elements
            viewFinder.visibility = View.GONE
            scannerOverlay.visibility = View.GONE
            bottomActionBar.visibility = View.GONE
            scanLineAnimator?.cancel()
            progressBar.visibility = View.GONE
            topBar.visibility = View.GONE // Hide top bar to prevent overlap

            // Show instructions box at the top with initial state
            instructionsBox.visibility = View.VISIBLE
            instructionsHeaderInitial.visibility = View.VISIBLE
            instructionsExpanded.visibility = View.GONE

            // Animate transition to expanded state after 4 seconds
            mainHandler.postDelayed({
                if (!isActivityAlive()) return@postDelayed
                animateInstructionsTransition()
            }, 4000)

            // Show waiting screen with status
            tvStatus.setText(messageRes)
            tvStatus.visibility = View.VISIBLE
            tvStatus.textSize = 17f

            val isDark = com.netless.app.data.SettingsRepository(this).getSettings().darkMode
            if (isDark) {
                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
                findViewById<View>(android.R.id.content).setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))
            } else {
                tvStatus.setTextColor(android.graphics.Color.parseColor("#0F172A"))
                findViewById<View>(android.R.id.content).setBackgroundColor(android.graphics.Color.parseColor("#F8FAFC"))
            }

            // Show Zenvest waiting actions (I've Completed Payment & Cancel Payment)
            layoutWaitingActions.visibility = View.VISIBLE
            btnTerminate.visibility = View.GONE
        }
    }

    /**
     * Animate transition from initial header to expanded steps list
     */
    private fun animateInstructionsTransition() {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            Log.d("QRScanner", "Animating instructions transition to expanded state")

            // Fade out initial header
            instructionsHeaderInitial.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    if (!isActivityAlive()) return@withEndAction
                    instructionsHeaderInitial.visibility = View.GONE

                    // Fade in expanded view
                    instructionsExpanded.alpha = 0f
                    instructionsExpanded.visibility = View.VISIBLE
                    instructionsExpanded.animate()
                        .alpha(1f)
                        .setDuration(400)
                        .start()
                }
                .start()
        }
    }

    /**
     * Update the status message on the black waiting screen.
     *
     * Takes a string resource, never a [String], and that is the point: this
     * is user-visible payment copy, and every literal that ever reached it
     * was invisible to both copy gates. lint's `HardcodedText` never leaves
     * XML, and `build.yml`'s grep is line-oriented — one call site here opens
     * its paren on one line and puts the literal on the next, so a gate
     * widened to match `updateBlackScreenStatus\(\s*"` reported green while a
     * hardcoded string sat in the payment flow. Requiring a resource id makes
     * `updateBlackScreenStatus("…")` a compile error instead of something a
     * regex has to be taught to notice.
     *
     * [formatArg] is a single optional argument rather than a `vararg`
     * deliberately: only one caller formats, and a spread would trip detekt's
     * default `SpreadOperator` rule (the build sets `buildUponDefaultConfig`),
     * whose baseline only ratchets down.
     */
    private fun updateBlackScreenStatus(@StringRes messageRes: Int, formatArg: Any? = null) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            val message =
                if (formatArg == null) getString(messageRes) else getString(messageRes, formatArg)
            Log.d("QRScanner", "Updating black screen status: $message")
            tvStatus.text = message
        }
    }

    /**
     * Decodes a QR out of an image the user picked from their gallery, then
     * hands it to the very same [processQRCode] the live camera uses — so a
     * gallery scan and a camera scan are indistinguishable downstream.
     *
     * Useful when the payee sent a QR as a photo, or the code is on a screen
     * the camera can't focus on.
     */
    private fun decodeQrFromGallery(uri: Uri) {
        lifecycleScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        // getPixels() throws on a HARDWARE bitmap, which is
                        // what ImageDecoder hands back by default — the QR
                        // decoder needs readable pixels.
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                        // Cap the working size: gallery photos can be 100MP,
                        // and a QR stays readable far below that.
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > GALLERY_MAX_EDGE_PX) {
                            decoder.setTargetSampleSize(longest / GALLERY_MAX_EDGE_PX + 1)
                        }
                    }
                    QRCodeAnalyzer.decodeBitmap(bitmap).also { bitmap.recycle() }
                }.onFailure { Log.w("QRScanner", "Gallery image could not be read: ${it.message}") }
            }

            if (!isActivityAlive()) return@launch
            when {
                decoded.isFailure -> showInvalidQrCode(getString(R.string.qr_gallery_unreadable))
                decoded.getOrNull() == null -> showInvalidQrCode(getString(R.string.qr_gallery_no_code))
                else -> {
                    // A gallery pick is an explicit user action: let it through
                    // even if a previous scan left the guard set.
                    isProcessingQRCode = false
                    processQRCode(decoded.getOrNull()!!)
                }
            }
        }
    }

    /**
     * A scanned code that isn't a usable UPI QR. This is NOT an error state:
     * the camera keeps running, the banner explains what was wrong, and
     * scanning re-arms so the user can just aim at another code.
     *
     * Previously this went through [showError], which blanked the preview to a
     * black screen with red "Error:" text AND fired a duplicate toast, then sat
     * dead for three seconds — which reads as the scanner having crashed.
     */
    private fun showInvalidQrCode(reason: String) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            tvScanError.text = getString(R.string.qr_invalid_banner, reason)
            tvScanError.visibility = View.VISIBLE

            // Re-arm immediately: the next frame holding a good QR should be
            // accepted even while the banner is still on screen.
            isProcessingQRCode = false

            mainHandler.removeCallbacks(hideScanErrorRunnable)
            mainHandler.postDelayed(hideScanErrorRunnable, SCAN_ERROR_VISIBLE_MS)
        }
    }

    private val hideScanErrorRunnable = Runnable {
        if (isActivityAlive() && ::tvScanError.isInitialized) {
            tvScanError.visibility = View.GONE
        }
    }

    private fun resumeScanning() {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            try {
                Log.d("QRScanner", "Resuming QR scanning")
                // Reset to camera view
                viewFinder.visibility = View.VISIBLE
                scannerOverlay.visibility = View.VISIBLE
                bottomActionBar.visibility = View.VISIBLE
                topBar.visibility = View.VISIBLE // Show top bar again
                tvStatus.visibility = View.GONE
                progressBar.visibility = View.GONE
                instructionsBox.visibility = View.GONE
                btnTerminate.visibility = View.GONE
                // Reset flash state
                isFlashOn = false
                camera?.cameraControl?.enableTorch(false)
                btnFlash.setImageResource(R.drawable.ic_flash_off)
                btnFlash.setBackgroundResource(R.drawable.bottom_action_button_bg)
                startScanLineAnimation()

                // Reset instruction states
                instructionsHeaderInitial.visibility = View.VISIBLE
                instructionsHeaderInitial.alpha = 1f
                instructionsExpanded.visibility = View.GONE
                instructionsExpanded.alpha = 1f

                findViewById<View>(
                    android.R.id.content
                ).setBackgroundColor(ContextCompat.getColor(this, android.R.color.black))

                // Resuming: clear both the processing and dialed flags.
                isProcessingQRCode = false
                hasDialedUSSD = false
                if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                    imageAnalyzer?.clearAnalyzer()
                    imageAnalyzer?.setAnalyzer(
                        cameraExecutor,
                        QRCodeAnalyzer { qrCode ->
                            processQRCode(qrCode)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("QRScanner", "Failed to resume scanning", e)
                Toast.makeText(this, R.string.error_qr_resume_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            if (!isActivityAlive()) return@runOnUiThread
            Log.e("QRScanner", "Showing error: $message")

            // Show error on black screen
            updateBlackScreenStatus(R.string.qr_error_prefix, message)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))

            // Show toast for additional feedback
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

            // Resume scanning after 3 seconds with proper error recovery
            mainHandler.postDelayed({
                resumeScanning()
            }, 3000)
        }
    }

    private fun startScanLineAnimation() {
        if (!::scanLine.isInitialized) return
        scanLineAnimator?.cancel()
        val density = resources.displayMetrics.density
        // Scanner frame is 260dp; margins 12dp each side; line is 2dp — travel ~234dp
        val maxTranslation = ((260 - 12 * 2 - 2) * density)
        scanLineAnimator = android.animation.ObjectAnimator.ofFloat(
            scanLine, "translationY", 0f, maxTranslation
        ).apply {
            duration = 2000
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("QRScanner", "Activity paused - stopping camera and terminating USSD overlay")

        // Send app paused broadcast to USSD overlay service
        try {
            val intent = Intent("APP_PAUSED")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d("QRScanner", "App paused broadcast sent")
        } catch (e: Exception) {
            Log.e("QRScanner", "Error sending app paused broadcast: ${e.message}")
        }

        // Terminate USSD overlay when activity is paused

        try {
            imageAnalyzer?.clearAnalyzer()
            // Do NOT reset isProcessingQRCode here: when user goes to dialer we pause;
            // if we reset the flag, onResume would re-attach the analyzer and dial again in a loop.
            // Flag is reset only in onDestroy or in showError() when resuming scanning.
        } catch (e: Exception) {
            Log.e("QRScanner", "Error stopping camera on pause: ${e.message}", e)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("QRScanner", "Activity stopped - terminating USSD overlay")

        // Send app stopped broadcast to USSD overlay service
        try {
            val intent = Intent("APP_STOPPED")
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            Log.d("QRScanner", "App stopped broadcast sent")
        } catch (e: Exception) {
            Log.e("QRScanner", "Error sending app stopped broadcast: ${e.message}")
        }

        // Terminate USSD overlay when activity is stopped
    }

    @Suppress("CyclomaticComplexMethod", "MagicNumber")
    override fun onResume() {
        super.onResume()
        Log.d("QRScanner", "Activity resumed - checking payment status")

        if (hasDialedUSSD) {
            btnViewRecentPayments.visibility = View.VISIBLE

            val sessionState = NetlessApplication.from(this)?.paymentSessionManager?.paymentState?.value
            if (sessionState is PaymentState.Success) {
                Log.d("QRScanner", "onResume: PaymentSessionManager already in Success state!")
                handleUssdConfirmed(sessionState.bankReference ?: "", sessionState.amount)
                return
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val repo = TransactionRepository.getInstance(applicationContext)
                    val recents = repo.getRecentTransactions(1).firstOrNull()?.firstOrNull()
                    if (recents != null && recents.status == TransactionStatus.SUCCESS &&
                        System.currentTimeMillis() - recents.timestamp < 300_000L
                    ) {
                        withContext(Dispatchers.Main) {
                            if (isActivityAlive()) {
                                Log.d("QRScanner", "onResume: Found confirmed transaction in Room DB!")
                                handleUssdConfirmed(recents.bankRef ?: recents.transactionId, recents.amount)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("QRScanner", "Error checking confirmed transactions on resume: ${e.message}")
                }
            }
        }

        try {
            // Guard against shutdown executor
            if (!::cameraExecutor.isInitialized || cameraExecutor.isShutdown) {
                Log.w("QRScanner", "Camera executor not available, skipping analyzer reattach")
                return
            }
            if (imageAnalyzer != null && !isProcessingQRCode) {
                // Clear the previous analyzer first; leaving it attached stacks analyzers.
                imageAnalyzer?.clearAnalyzer()
                imageAnalyzer?.setAnalyzer(
                    cameraExecutor,
                    QRCodeAnalyzer { qrCode ->
                        processQRCode(qrCode)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("QRScanner", "Error restarting camera on resume: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        try {
            Log.d("QRScanner", "Destroying QR scanner activity...")

            // Cancel ALL pending callbacks from centralized handler
            mainHandler.removeCallbacksAndMessages(null)

            // Send app destroyed broadcast to USSD overlay service
            try {
                val intent = Intent("APP_DESTROYED")
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d("QRScanner", "App destroyed broadcast sent")
            } catch (e: Exception) {
                Log.e("QRScanner", "Error sending app destroyed broadcast: ${e.message}")
            }

            // Terminate USSD overlay when activity is destroyed

            // Stop USSD process
            isUSSDProcessActive = false
            messageHandler?.removeCallbacksAndMessages(null)
            smsTimeoutRunnable?.let {
                smsTimeoutHandler?.removeCallbacks(it)
            }

            paymentInputDialog?.dismiss()
            paymentInputDialog = null

            // If the user already dialed USSD (*99*1*3#) or payment handoff completed,
            // the payment request is already in-flight with the carrier/NPCI.
            // Under NO condition should we cancel the session or kill TransactionDetector
            // simply because this screen finished/closed. Keep SMS monitoring alive!
            val shouldCancelSession = isFinishing &&
                !finishedByWaitingScreenTimeout &&
                !finishedByPaymentInitiated &&
                !hasDialedUSSD

            if (shouldCancelSession) {
                Log.d("QRScanner", "Cancelling payment session because user abandoned prior to USSD dial")
                NetlessApplication.from(this)?.paymentSessionManager?.onUserCancelled()
                try {
                    TransactionDetector.getInstance(this).stopOperation()
                } catch (_: Exception) {}
            } else if (hasDialedUSSD) {
                Log.d("QRScanner", "Activity destroying with USSD dialed - keeping payment session and SMS detector active!")
            }

            // Reset processing flags AFTER cancellation check
            isProcessingQRCode = false
            hasDialedUSSD = false

            // Wipe the payee VPA from the clipboard now the flow is over.
            clearVpaClipboard()

            // Cancel scan line animation
            scanLineAnimator?.cancel()
            scanLineAnimator = null

            // Unregister broadcast receivers
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(overlayDismissReceiver)
                LocalBroadcastManager.getInstance(this).unregisterReceiver(smsReceiver)
                LocalBroadcastManager.getInstance(this).unregisterReceiver(ussdConfirmedReceiver)
            } catch (e: Exception) {
                Log.e("QRScanner", "Error unregistering receivers: ${e.message}")
            }

            // Clear image analyzer first
            imageAnalyzer?.clearAnalyzer()
            imageAnalyzer = null

            // Shutdown camera executor safely
            if (::cameraExecutor.isInitialized && !cameraExecutor.isShutdown) {
                cameraExecutor.shutdown()
                Log.d("QRScanner", "Camera executor shutdown")
            }

            Log.d("QRScanner", "QR scanner activity destroyed successfully")
        } catch (e: Exception) {
            Log.e("QRScanner", "Error during activity destruction: ${e.message}", e)
        } finally {
            super.onDestroy()
        }
    }
}
