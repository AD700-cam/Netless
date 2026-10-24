// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless
//
// Home screen: the entry point to both payment rails (Scan QR and Pay
// Contact), recent payments, and settings. UI only — the transfer
// orchestration and permission gating live in MainActivityHelper.

package com.netless.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netless.app.constants.AppConstants
import com.netless.app.constants.PermissionConstants
import com.netless.app.data.PaymentDetails
import com.netless.app.data.PaymentStatus
import com.netless.app.data.TestResultsManager
import com.netless.app.helpers.MainActivityHelper
import com.netless.app.helpers.SetupHelper
import com.netless.app.managers.PermissionManager
import com.netless.app.payment.PaymentInputValidator
import com.netless.app.payment.Upi123CallStringBuilder
import com.netless.app.payment.messageFor
import com.netless.app.data.SettingsRepository
import com.netless.app.ui.activities.SettingsActivity
import com.netless.app.ui.activities.TransactionHistoryActivity
import com.netless.app.ui.components.AvatarCircle
import com.netless.app.ui.components.NetlessBrandWordmark
import com.netless.app.ui.components.NotificationsDialog
import com.netless.app.ui.components.ProfileDialog
import com.netless.app.ui.components.TransactionDetailDialog
import com.netless.app.ui.dialogs.ContactPickerDialog
import com.netless.app.ui.theme.BlueAccentTheme
import com.netless.app.ui.theme.NetlessAccentBlue
import com.netless.app.ui.theme.NetlessBrandElectricGreen
import com.netless.app.ui.theme.NetlessBrandEmerald
import com.netless.app.ui.theme.NetlessCardBorder
import com.netless.app.ui.theme.NetlessChipBackground
import com.netless.app.ui.theme.NetlessDarkGray
import com.netless.app.ui.theme.NetlessLightGray
import com.netless.app.ui.theme.NetlessMediumGray
import com.netless.app.ui.theme.NetlessOutlineGray
import com.netless.app.ui.theme.NetlessStatusError
import com.netless.app.ui.theme.NetlessSurfaceDim
import com.netless.app.ui.theme.NetlessSurfaceElevated
import com.netless.app.ui.theme.NetlessTextGray
import com.netless.app.ui.theme.NetlessTextLightGray
import com.netless.app.ui.theme.NetlessTextPale
import com.netless.app.ui.theme.NetlessTheme
import com.netless.app.ui.theme.LocalNetlessAccentTheme
import com.netless.app.ui.theme.LocalNetlessDarkMode
import com.netless.app.ui.theme.ZenvestActionCircleDark
import com.netless.app.ui.theme.ZenvestAuroraCyan
import com.netless.app.ui.theme.ZenvestAuroraLavender
import com.netless.app.ui.theme.ZenvestAuroraPeach
import com.netless.app.ui.theme.ZenvestAuroraRose
import com.netless.app.ui.theme.ZenvestBgDark
import com.netless.app.ui.theme.ZenvestBgLight
import com.netless.app.ui.theme.ZenvestCardBorder
import com.netless.app.ui.theme.ZenvestCardBorderDark
import com.netless.app.ui.theme.ZenvestDividerDark
import com.netless.app.ui.theme.ZenvestDividerLight
import com.netless.app.ui.theme.ZenvestPillBgDark
import com.netless.app.ui.theme.ZenvestPillBorderDark
import com.netless.app.ui.theme.ZenvestSheetBgDark
import com.netless.app.ui.theme.ZenvestSheetBgLight
import com.netless.app.ui.theme.ZenvestSkyMid
import com.netless.app.ui.theme.ZenvestSkyTop
import com.netless.app.ui.theme.ZenvestTextMuted
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary
import com.netless.app.ui.theme.statusBackgroundColor
import com.netless.app.ui.theme.statusColor
import com.netless.app.utils.CurrencyFormat
import com.netless.app.utils.findComponentActivity
import com.netless.app.viewmodel.MainUiEvent
import com.netless.app.viewmodel.MainViewModel
import com.netless.app.viewmodel.TransactionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "Netless"
    }

    // Helper for all business logic
    private lateinit var helper: MainActivityHelper

    // Shared with MainScreen (same instance via Compose viewModel()); carries
    // one-shot Activity -> Compose events, replacing the old static callbacks.
    private val mainViewModel: MainViewModel by viewModels()

    // Launches QRScannerActivity and, on return, un-sticks the QR button's
    // "Opening..." state via a QrScannerClosed event.
    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        mainViewModel.onQrScannerClosed()
    }

    // Launches the system "draw over other apps" settings screen; on return,
    // re-checks the permission and reports the outcome via toast (there is
    // no reliable resultCode for this settings screen, so re-checking is
    // the only correct way to know what happened).
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val granted = PermissionManager.canDrawOverlays(this)
        val message = if (granted) {
            getString(R.string.home_overlay_perm_granted_toast)
        } else {
            getString(R.string.home_overlay_perm_required_toast)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Phone-call permission group, requested before dialing or QR scanning.
    // There is no auto-retry: the user re-taps the action once granted.
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (!granted) {
            Toast.makeText(this, R.string.error_permissions_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(R.style.Theme_Netless)

        // Draw edge-to-edge so Compose's statusBarsPadding()/navigationBarsPadding()
        // are the single source of inset padding. The theme previously also set
        // android:fitsSystemWindows=true, which made the decor pad the content as
        // well — a double inset that, depending on inset-dispatch timing, showed
        // intermittent black bars at the top and bottom.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Initialize helper with UI callbacks (matches the 6-method UICallback)
        helper = MainActivityHelper(
            this,
            object : MainActivityHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
                }

                override fun updatePaymentState(paymentState: com.netless.app.states.PaymentState) {
                    Log.d(TAG, "Payment state updated: ${paymentState::class.simpleName}")
                }

                override fun navigateToSetup() {
                    startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                    finish()
                }

                override fun navigateToTestConfiguration() {
                    startActivity(Intent(this@MainActivity, TestConfigurationActivity::class.java))
                    finish()
                }

                override fun finishActivity() {
                    finish()
                }

                override fun showOverlayPermissionExplanation() {
                    mainViewModel.onOverlayPermissionNeeded()
                }

                override fun launchQRScanner(intent: Intent) {
                    qrScannerLauncher.launch(intent)
                }

                override fun requestPhonePermissions() {
                    phonePermissionLauncher.launch(PermissionConstants.PHONE_PERMISSIONS)
                }
            }
        )

        helper.initialize()

        // The launch gate must know which screen to show before rendering, so
        // these two SharedPreferences flags are read synchronously. Reading
        // them faults the prefs file in once (a small, one-time disk read);
        // annotate it as permitted so the debug StrictMode tripwire stays
        // sharp for genuinely unexpected main-thread disk I/O instead of
        // crying wolf on this known-safe read every launch.
        val setupCompleted: Boolean
        val testCompleted: Boolean
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            setupCompleted = helper.isSetupCompleted()
            testCompleted = helper.isTestCompleted()
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }

        if (!setupCompleted) {
            helper.navigateToSetup()
            return
        }
        if (!testCompleted) {
            helper.navigateToTestConfiguration()
            return
        }

        setContent {
            CompositionLocalProvider(LocalNetlessAccentTheme provides BlueAccentTheme) {
                NetlessTheme {
                    MainScreen(
                        onInitiateTransfer = { phoneNumber, amount ->
                            helper.initiateTransfer(phoneNumber, amount)
                        },
                        onQRScanClick = {
                            helper.startQRScanning()
                        },
                        onRequestOverlayPermission = {
                            PermissionManager(this).overlayPermissionSettingsIntent()?.let {
                                overlayPermissionLauncher.launch(it)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        helper.onPause()
    }

    override fun onResume() {
        super.onResume()
        helper.onResume()
    }

    override fun onStop() {
        super.onStop()
        helper.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        helper.onDestroy()
    }
}

// Payment Action Buttons - QR scan + Pay Contact.
//
// Each rail is locked until its own connectivity test has passed: Scan QR
// dials *99#, Pay Contact dials the UPI 123 IVR. Now rendered as horizontal
// pill-cards inspired by the Zenvest fintech design language.
@Composable
fun PaymentActionButtons(
    onQRScanClick: () -> Unit,
    onPayContactClick: () -> Unit,
    isUpi123Ready: Boolean,
    isScanQrReady: Boolean,
    isScanning: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScanQrButton(onQRScanClick, isScanQrReady, isScanning)
        PayContactButton(onPayContactClick, isUpi123Ready)
    }
}

/** Scan QR — pill-card style, full width. */
@Suppress("LongMethod")
@Composable
private fun ScanQrButton(
    onQRScanClick: () -> Unit,
    isScanQrReady: Boolean,
    isScanning: Boolean
) {
    var isQRPressed by remember { mutableStateOf(false) }
    val qrButtonScale by animateFloatAsState(
        targetValue = if (isQRPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "QR Button Scale"
    )
    val accent = LocalNetlessAccentTheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(qrButtonScale)
            .shadow(
                elevation = if (isScanQrReady) 16.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = accent.headerGradientStart.copy(alpha = 0.25f),
                spotColor = accent.headerGradientEnd.copy(alpha = 0.35f)
            )
            .background(
                brush = if (isScanQrReady) {
                    Brush.linearGradient(
                        colors = listOf(
                            accent.headerGradientStart,
                            accent.headerGradientEnd
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(NetlessDarkGray, NetlessMediumGray)
                    )
                },
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (isScanQrReady) 0.18f else 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!isScanning) {
                            isQRPressed = true
                            tryAwaitRelease()
                            isQRPressed = false
                        }
                    },
                    onTap = { if (!isScanning) onQRScanClick() }
                )
            }
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = if (isScanQrReady) 0.22f else 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !isScanQrReady -> Icons.Default.Lock
                        isScanning -> Icons.Default.QrCode
                        else -> Icons.Default.QrCodeScanner
                    },
                    contentDescription = null,
                    tint = if (isScanQrReady) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        !isScanQrReady -> stringResource(R.string.home_setup_ussd)
                        isScanning -> stringResource(R.string.home_scan_opening)
                        else -> stringResource(R.string.home_scan_qr)
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isScanQrReady) Color.White else NetlessTextLightGray
                )
                Text(
                    text = stringResource(R.string.home_action_scan_subtitle),
                    fontSize = 12.sp,
                    color = if (isScanQrReady) Color.White.copy(alpha = 0.75f) else NetlessTextGray
                )
            }
            // Chevron
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Pay Contact — pill-card style, full width. */
@Composable
private fun PayContactButton(
    onPayContactClick: () -> Unit,
    isUpi123Ready: Boolean
) {
    var isPayPressed by remember { mutableStateOf(false) }
    val payButtonScale by animateFloatAsState(
        targetValue = if (isPayPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "Pay Button Scale"
    )
    val accent = LocalNetlessAccentTheme.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(payButtonScale)
            .shadow(
                elevation = if (isUpi123Ready) 12.dp else 2.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = accent.headerGradientStart.copy(alpha = 0.2f),
                spotColor = accent.headerGradientEnd.copy(alpha = 0.3f)
            )
            .background(
                color = if (isUpi123Ready) NetlessDarkGray else NetlessDarkGray,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                brush = if (isUpi123Ready) {
                    Brush.linearGradient(
                        colors = listOf(
                            accent.headerGradientStart.copy(alpha = 0.5f),
                            accent.headerGradientEnd.copy(alpha = 0.5f)
                        )
                    )
                } else {
                    Brush.linearGradient(colors = listOf(NetlessLightGray, NetlessLightGray))
                },
                shape = RoundedCornerShape(20.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPayPressed = true
                        tryAwaitRelease()
                        isPayPressed = false
                    },
                    onTap = { onPayContactClick() }
                )
            }
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isUpi123Ready) accent.primary.copy(alpha = 0.18f)
                        else NetlessMediumGray
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUpi123Ready) Icons.Default.Person else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isUpi123Ready) accent.primary else NetlessTextGray,
                    modifier = Modifier.size(24.dp)
                )
            }
            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isUpi123Ready) R.string.home_action_pay_contact else R.string.home_action_setup_ivr
                    ),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isUpi123Ready) Color.White else NetlessTextLightGray
                )
                Text(
                    text = stringResource(R.string.home_action_pay_subtitle),
                    fontSize = 12.sp,
                    color = NetlessTextGray
                )
            }
            // Gradient badge or arrow
            if (isUpi123Ready) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accent.headerGradientStart, accent.headerGradientEnd)
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "UPI 123",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onInitiateTransfer: (String, String) -> Unit,
    onQRScanClick: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val settingsRepo = remember {
        val app = context.applicationContext as? NetlessApplication
        app?.settingsRepository ?: SettingsRepository(context.applicationContext)
    }
    val settingsState by settingsRepo.settingsFlow.collectAsState()

    var savedBank by remember {
        val spBank = sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "") ?: ""
        mutableStateOf(spBank.ifEmpty { settingsState.bankId })
    }
    var userName by remember { mutableStateOf(settingsState.userName) }
    var userAvatarId by remember { mutableStateOf(settingsState.userAvatarId) }
    var userPhoneNumber by remember {
        mutableStateOf(
            settingsState.userPhoneNumber.ifEmpty {
                sharedPreferences.getString(AppConstants.KEY_USER_PHONE_NUMBER, "") ?: ""
            }
        )
    }
    var showProfileDialog by remember { mutableStateOf(false) }

    // Pay Contact dials the UPI 123 IVR, so it stays inactive until the
    // UPI 123 configuration test has passed (re-checked on every resume so
    // completing the test activates it immediately).
    val testResultsManager = remember { TestResultsManager(context) }
    var isUpi123Ready by remember {
        mutableStateOf(testResultsManager.getTestResults()?.upi123Enabled == true)
    }

    // Scan QR dials *99# on USSD networks, and UPI 123Pay on Jio/non-USSD networks.
    // On Jio, *99# is unsupported, so QR scanning is enabled for Jio SIMs automatically.
    var isUssdReady by remember {
        mutableStateOf(testResultsManager.getTestResults()?.ussdEnabled == true)
    }
    val isScanQrReady = remember(isUssdReady, isUpi123Ready) {
        val isScanToPayUssd = SetupHelper.isScanToPayUssdAvailable(context)
        if (isScanToPayUssd) {
            isUssdReady || isUpi123Ready
        } else {
            isUpi123Ready || !SetupHelper.isPrimarySimUssdCapable(context)
        }
    }

    LaunchedEffect(lifecycle) {
        snapshotFlow { lifecycle.currentState }.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                settingsRepo.reload()
                val spBank = sharedPreferences.getString(AppConstants.KEY_SELECTED_BANK, "") ?: ""
                savedBank = spBank.ifEmpty { settingsRepo.settingsFlow.value.bankId }
                userName = settingsRepo.settingsFlow.value.userName
                userAvatarId = settingsRepo.settingsFlow.value.userAvatarId
                userPhoneNumber = settingsRepo.settingsFlow.value.userPhoneNumber.ifEmpty {
                    sharedPreferences.getString(AppConstants.KEY_USER_PHONE_NUMBER, "") ?: ""
                }
                isUpi123Ready = testResultsManager.getTestResults()?.upi123Enabled == true
                isUssdReady = testResultsManager.getTestResults()?.ussdEnabled == true
            }
        }
    }

    val transactionViewModel: TransactionViewModel = viewModel()
    val recentPayments by transactionViewModel.recentTransactions.collectAsState()
    val selectedTransaction by transactionViewModel.selectedTransaction.collectAsState()
    val isLoading by transactionViewModel.isLoading.collectAsState()
    val error by transactionViewModel.error.collectAsState()

    var showPayContact by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }

    var showSmsPermissionDialog by remember { mutableStateOf(false) }
    var pendingSmsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    val hostActivity = remember(context) { context.findComponentActivity() }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }

    // Runs the queued action (start scan / open pay dialog / initiate transfer)
    // once RECEIVE_SMS is granted; the launcher stays Compose-scoped so no
    // Activity-level callback bridge is needed.
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingSmsAction?.invoke()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.home_sms_permission_toast),
                Toast.LENGTH_LONG
            ).show()
        }
        pendingSmsAction = null
    }

    // POST_NOTIFICATIONS backs the payment-outcome notification — the
    // fallback the result screen relies on when its direct background launch
    // is blocked (see PaymentResultNotifier). It is best-effort, not a
    // prerequisite, so we ask ONCE at the first payment and never block on the
    // result: the payment proceeds whether granted or not. Settings offers a
    // later toggle for anyone who declines.
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort: outcome recorded by the OS; nothing to do here */ }
    val maybeAskNotifications = remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val alreadyAsked = sharedPreferences.getBoolean(AppConstants.KEY_NOTIFICATIONS_ASKED, false)
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!alreadyAsked && !granted) {
                    sharedPreferences.edit()
                        .putBoolean(AppConstants.KEY_NOTIFICATIONS_ASKED, true)
                        .apply()
                    postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    // One-shot events from MainActivity (launchers + business-logic helper),
    // replacing the former static @Volatile callbacks on its companion.
    val mainViewModel: MainViewModel = viewModel()
    LaunchedEffect(Unit) {
        mainViewModel.events.collect { event ->
            when (event) {
                MainUiEvent.QrScannerClosed -> isScanning = false
                MainUiEvent.OverlayPermissionNeeded -> showOverlayPermissionDialog = true
            }
        }
    }

    // Reset scanning + refresh list whenever the app resumes
    LaunchedEffect(lifecycle) {
        snapshotFlow { lifecycle.currentState }.collect { state ->
            if (state == Lifecycle.State.RESUMED) {
                isScanning = false
                transactionViewModel.refresh()
            }
        }
    }

    // Safety: never leave the QR button stuck in "Opening..."
    LaunchedEffect(isScanning) {
        if (isScanning) {
            kotlinx.coroutines.delay(AppConstants.USSD_SESSION_TIMEOUT)
            if (isScanning) isScanning = false
        }
    }

    val selectedBankName = SetupHelper.getBankDisplayName(savedBank)

    val isDark = LocalNetlessDarkMode.current
    var activeNavTab by remember { mutableStateOf(0) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (isDark) ZenvestBgDark else ZenvestBgLight
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF0C1322), Color(0xFF0F172A), ZenvestBgDark)
                        } else {
                            listOf(ZenvestSkyTop, ZenvestSkyMid, ZenvestBgLight)
                        },
                        startY = 0f,
                        endY = 1100f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 440.dp)
                    .align(Alignment.Center)
                    .statusBarsPadding()
            ) {
                // Scrollable main content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // ─── TOP BAR (Logo + Notification Bell + User Avatar) ───
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Netless brand name on left (no logo, no subtitle)
                        NetlessBrandWordmark(
                            isDark = isDark,
                            showLogo = false,
                            showSubtitle = false
                        )

                        // Right icons (Offline Status Bell & Profile Avatar)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Offline Status Badge / Bell
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0x331E293B) else Color(0xCCFFFFFF))
                                    .border(
                                        1.dp,
                                        if (isDark) Color(0x3338BDF8) else Color(0x40CBD5E1),
                                        CircleShape
                                    )
                                    .clickable { showNotificationsDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = "Notifications",
                                    tint = if (isDark) Color.White else ZenvestTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Profile Avatar (Opens Profile Dialog)
                            AvatarCircle(
                                avatarId = userAvatarId,
                                size = 40.dp,
                                onClick = { showProfileDialog = true }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // ─── SIGNATURE ZENVEST FROSTED GLASS BANK CARD ───
                    ZenvestBalanceCard(
                        selectedBankName = selectedBankName,
                        userPhoneNumber = userPhoneNumber,
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // ─── ACTION BUTTONS (Send ↗ | Scan QR ⛶) ───
                    ZenvestActionButtonsRow(
                        onSendClick = {
                            maybeAskNotifications()
                            if (!PermissionManager.canDrawOverlays(context)) {
                                showOverlayPermissionDialog = true
                            } else {
                                showPayContact = true
                            }
                        },
                        onScanClick = {
                            maybeAskNotifications()
                            val hasSms = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECEIVE_SMS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasSms) {
                                pendingSmsAction = {
                                    isScanning = true
                                    onQRScanClick()
                                }
                                showSmsPermissionDialog = true
                            } else {
                                isScanning = true
                                onQRScanClick()
                            }
                        },
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // ─── TRANSACTIONS SHEET ───
                    ZenvestTransactionsSheet(
                        recentPayments = recentPayments,
                        isLoading = isLoading,
                        error = error,
                        onSelectPayment = { paymentId -> transactionViewModel.selectTransaction(paymentId) },
                        onRetry = { transactionViewModel.refresh() },
                        onViewAllClick = {
                            context.startActivity(Intent(context, TransactionHistoryActivity::class.java))
                        },
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ─── DOCKED ZENVEST BOTTOM NAVIGATION BAR ───
                ZenvestBottomNavBar(
                    selectedTab = 0,
                    onSelectTab = { tabIndex ->
                        when (tabIndex) {
                            1 -> context.startActivity(Intent(context, SettingsActivity::class.java))
                        }
                    },
                    isDark = isDark
                )
            }

            // User Profile Dialog with Avatar Templates
            if (showProfileDialog) {
                ProfileDialog(
                    userName = userName,
                    avatarId = userAvatarId,
                    userPhoneNumber = userPhoneNumber,
                    selectedBankName = selectedBankName,
                    onSaveProfile = { newName, newAvatarId, newPhone ->
                        userName = newName
                        userAvatarId = newAvatarId
                        userPhoneNumber = newPhone
                        sharedPreferences.edit()
                            .putString(AppConstants.KEY_USER_NAME, newName)
                            .putInt("user_avatar_id", newAvatarId)
                            .putString(AppConstants.KEY_USER_PHONE_NUMBER, newPhone)
                            .apply()
                        val currentSettings = settingsRepo.settingsFlow.value
                        settingsRepo.saveSettings(
                            currentSettings.copy(
                                bankId = savedBank.ifEmpty { currentSettings.bankId },
                                userName = newName,
                                userAvatarId = newAvatarId,
                                userPhoneNumber = newPhone
                            )
                        )
                    },
                    onDismiss = { showProfileDialog = false }
                )
            }

            // Notifications Dialog
            if (showNotificationsDialog) {
                NotificationsDialog(
                    selectedBankName = selectedBankName,
                    userPhoneNumber = userPhoneNumber,
                    onDismiss = { showNotificationsDialog = false }
                )
            }

            // Detail dialog for a tapped Recent Payments row
            selectedTransaction?.let { transaction ->
                TransactionDetailDialog(
                    transaction = transaction,
                    onDismiss = { transactionViewModel.clearSelectedTransaction() },
                    onDelete = {
                        transactionViewModel.deleteTransaction(transaction)
                        transactionViewModel.clearSelectedTransaction()
                    }
                )
            }

            // Dialogs
            if (showPayContact) {
                PayContactDialog(
                    onDismiss = { showPayContact = false },
                    onConfirm = { phone, amt, _ ->
                        showPayContact = false
                        onInitiateTransfer(phone, amt)
                        if (permissionManager?.checkSMSPermissions() != true) {
                            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        }
                    }
                )
            }

            if (showOverlayPermissionDialog) {
                PermissionExplanationDialog(
                    title = stringResource(R.string.home_perm_overlay_title),
                    message = stringResource(R.string.home_perm_overlay_msg),
                    confirmButtonText = stringResource(R.string.home_perm_grant),
                    onConfirm = {
                        showOverlayPermissionDialog = false
                        onRequestOverlayPermission()
                    },
                    onDismiss = { showOverlayPermissionDialog = false }
                )
            }

            if (showSmsPermissionDialog) {
                PermissionExplanationDialog(
                    title = stringResource(R.string.home_perm_sms_title),
                    message = stringResource(R.string.home_perm_sms_msg),
                    confirmButtonText = stringResource(R.string.home_perm_grant),
                    onConfirm = {
                        showSmsPermissionDialog = false
                        smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                    },
                    onDismiss = {
                        showSmsPermissionDialog = false
                        pendingSmsAction = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ZenvestBalanceCard(
    selectedBankName: String,
    userPhoneNumber: String = "",
    isDark: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = if (isDark) Color.Black.copy(0.6f) else Color(0x3364748B),
                spotColor = if (isDark) Color(0x4038BDF8) else Color(0x290284C7)
            ),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = if (isDark) {
                        Brush.linearGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF111827)),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Color(0xF5FFFFFF), Color(0xEDF1F6FA), Color(0xE8E6F0F6)),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    },
                    shape = RoundedCornerShape(26.dp)
                )
                .border(
                    width = 1.dp,
                    color = if (isDark) ZenvestCardBorderDark else ZenvestCardBorder,
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            // Ethereal Pastel Aurora Waves on the right side of card
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(26.dp))
            ) {
                val width = size.width
                val height = size.height

                if (isDark) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x2600E5FF), Color.Transparent),
                            center = Offset(width * 0.85f, height * 0.45f),
                            radius = width * 0.45f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0x2B8B5CF6), Color.Transparent),
                            center = Offset(width * 0.95f, height * 0.75f),
                            radius = width * 0.5f
                        )
                    )
                } else {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ZenvestAuroraRose.copy(alpha = 0.40f), Color.Transparent),
                            center = Offset(width * 0.90f, height * 0.65f),
                            radius = width * 0.48f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ZenvestAuroraPeach.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(width * 0.82f, height * 0.35f),
                            radius = width * 0.42f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ZenvestAuroraLavender.copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(width * 0.95f, height * 0.40f),
                            radius = width * 0.40f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ZenvestAuroraCyan.copy(alpha = 0.40f), Color.Transparent),
                            center = Offset(width * 0.75f, height * 0.85f),
                            radius = width * 0.38f
                        )
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Top Row: Currency Flag Pill + Offline Active Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Currency Pill Badge (₹ INR)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDark) Color(0x33FFFFFF) else Color(0xB3FFFFFF))
                            .border(
                                1.dp,
                                if (isDark) Color(0x26FFFFFF) else Color(0x40CBD5E1),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF3C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFD97706)
                            )
                        }
                        Text(
                            text = stringResource(R.string.home_card_currency_inr),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else ZenvestTextPrimary
                        )
                    }

                    // 100% Offline Active Badge
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isDark) Color(0x2610B981) else Color(0x1A10B981)
                            )
                            .border(
                                1.dp,
                                NetlessBrandEmerald.copy(alpha = 0.3f),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(NetlessBrandElectricGreen)
                        )
                        Text(
                            text = stringResource(R.string.home_badge_offline_active),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) NetlessBrandEmerald else Color(0xFF047857)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Hero Row: Linked Bank Emblem & Details
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isDark) Color(0x3338BDF8) else Color(0xFFE0F2FE)
                            )
                            .border(
                                1.dp,
                                if (isDark) Color(0x4D38BDF8) else Color(0x66BAE6FD),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = selectedBankName,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else ZenvestTextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Linked Offline Bank Account",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) NetlessBrandEmerald else Color(0xFF059669)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Row: Account Number & Rail protocol
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val last4 = if (userPhoneNumber.length >= 4) {
                        userPhoneNumber.takeLast(4)
                    } else if (userPhoneNumber.isNotEmpty()) {
                        userPhoneNumber
                    } else {
                        "••••"
                    }
                    Text(
                        text = "Account: •••• $last4",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White.copy(0.9f) else ZenvestTextPrimary
                    )

                    Text(
                        text = "NPCI 123Pay Ready",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ZenvestActionButtonsRow(
    onSendClick: () -> Unit,
    onScanClick: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Send ↗ Pill Button
        Row(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .shadow(
                    elevation = if (isDark) 0.dp else 4.dp,
                    shape = RoundedCornerShape(25.dp),
                    spotColor = Color(0x1F000000)
                )
                .clip(RoundedCornerShape(25.dp))
                .background(if (isDark) ZenvestPillBgDark else Color.White)
                .border(
                    1.dp,
                    if (isDark) ZenvestPillBorderDark else Color(0xFFE2E8F0),
                    RoundedCornerShape(25.dp)
                )
                .clickable(onClick = onSendClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_action_send),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.CallMade,
                contentDescription = null,
                tint = if (isDark) Color.White else ZenvestTextPrimary,
                modifier = Modifier.size(17.dp)
            )
        }

        // Scan QR ⛶ Pill Button
        Row(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .shadow(
                    elevation = if (isDark) 0.dp else 4.dp,
                    shape = RoundedCornerShape(25.dp),
                    spotColor = Color(0x1F000000)
                )
                .clip(RoundedCornerShape(25.dp))
                .background(if (isDark) ZenvestPillBgDark else Color.White)
                .border(
                    1.dp,
                    if (isDark) ZenvestPillBorderDark else Color(0xFFE2E8F0),
                    RoundedCornerShape(25.dp)
                )
                .clickable(onClick = onScanClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_action_scan),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = if (isDark) Color.White else ZenvestTextPrimary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun ZenvestTransactionsSheet(
    recentPayments: List<PaymentDetails>,
    isLoading: Boolean,
    error: String?,
    onSelectPayment: (String) -> Unit,
    onRetry: () -> Unit,
    onViewAllClick: () -> Unit,
    isDark: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchOpen by remember { mutableStateOf(false) }
    var selectedDateSort by remember { mutableStateOf("NEWEST") }
    var showSortDropdown by remember { mutableStateOf(false) }

    val sortedAndFilteredPayments = remember(recentPayments, searchQuery, selectedDateSort) {
        var list = recentPayments

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.recipientName?.contains(q, ignoreCase = true) == true ||
                it.phoneNumber.contains(q, ignoreCase = true) ||
                it.amount.toString().contains(q, ignoreCase = true)
            }
        }

        val now = System.currentTimeMillis()
        when (selectedDateSort) {
            "OLDEST" -> list.sortedBy { it.timestamp }
            "TODAY" -> {
                val oneDayAgo = now - 24 * 60 * 60 * 1000L
                list.filter { it.timestamp >= oneDayAgo }.sortedByDescending { it.timestamp }
            }
            "WEEK" -> {
                val oneWeekAgo = now - 7 * 24 * 60 * 60 * 1000L
                list.filter { it.timestamp >= oneWeekAgo }.sortedByDescending { it.timestamp }
            }
            "HIGH" -> list.sortedByDescending { it.amount }
            "LOW" -> list.sortedBy { it.amount }
            else -> list.sortedByDescending { it.timestamp }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                spotColor = Color(0x14000000)
            ),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = if (isDark) ZenvestSheetBgDark else ZenvestSheetBgLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp)
        ) {
            // Header: Filter, "Transactions", Search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter / Date Sort Button
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedDateSort != "NEWEST") {
                                    if (isDark) Color(0x3338BDF8) else Color(0x1F0284C7)
                                } else {
                                    if (isDark) Color(0x26FFFFFF) else Color(0xFFF1F5F9)
                                }
                            )
                            .clickable { showSortDropdown = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedDateSort != "NEWEST") {
                                if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            } else {
                                if (isDark) Color.White else ZenvestTextPrimary
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false },
                        modifier = Modifier.background(if (isDark) Color(0xFF1E293B) else Color.White)
                    ) {
                        val sortOptions = listOf(
                            "NEWEST" to "📅 Newest First",
                            "OLDEST" to "⏳ Oldest First",
                            "TODAY" to "📆 Today Only",
                            "WEEK" to "🗓️ Last 7 Days",
                            "HIGH" to "📈 Highest Amount",
                            "LOW" to "📉 Lowest Amount"
                        )
                        sortOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedDateSort == key) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedDateSort == key) {
                                            if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                                        } else {
                                            if (isDark) Color.White else ZenvestTextPrimary
                                        }
                                    )
                                },
                                onClick = {
                                    selectedDateSort = key
                                    showSortDropdown = false
                                }
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.home_transactions_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else ZenvestTextPrimary
                )

                // Search Toggle Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSearchOpen || searchQuery.isNotEmpty()) {
                                if (isDark) Color(0x3338BDF8) else Color(0x1F0284C7)
                            } else {
                                if (isDark) Color(0x26FFFFFF) else Color(0xFFF1F5F9)
                            }
                        )
                        .clickable { isSearchOpen = !isSearchOpen },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isSearchOpen || searchQuery.isNotEmpty()) {
                            if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                        } else {
                            if (isDark) Color.White else ZenvestTextPrimary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Inline Search Bar when search is toggled open
            if (isSearchOpen) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search recipient, number, or amount...",
                            fontSize = 12.sp,
                            color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        unfocusedBorderColor = if (isDark) Color(0x3338BDF8) else Color(0x33CBD5E1),
                        focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                        unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                        focusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }

            // Active Filter / Sort Indicator Pill
            if (selectedDateSort != "NEWEST" || searchQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sortLabel = when (selectedDateSort) {
                        "OLDEST" -> "Oldest"
                        "TODAY" -> "Today"
                        "WEEK" -> "Last 7 Days"
                        "HIGH" -> "High Amount"
                        "LOW" -> "Low Amount"
                        else -> "Filtered"
                    }
                    Text(
                        text = "Active: $sortLabel${if (searchQuery.isNotEmpty()) " • \"$searchQuery\"" else ""}",
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = {
                            selectedDateSort = "NEWEST"
                            searchQuery = ""
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Reset",
                            fontSize = 11.sp,
                            color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = NetlessAccentBlue,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.home_loading_transactions),
                            fontSize = 13.sp,
                            color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
                        )
                    }
                }

                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.home_failed_load_transactions),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else ZenvestTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = onRetry) {
                            Text(
                                text = stringResource(R.string.home_retry),
                                color = NetlessAccentBlue
                            )
                        }
                    }
                }

                sortedAndFilteredPayments.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0x1A38BDF8) else Color(0xFFE0F2FE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = NetlessAccentBlue,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedDateSort != "NEWEST") "No matching transactions" else stringResource(R.string.home_no_transactions),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else ZenvestTextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedDateSort != "NEWEST") "Try resetting filters" else stringResource(R.string.home_no_transactions_sub),
                            fontSize = 12.sp,
                            color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
                        )
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        sortedAndFilteredPayments.take(5).forEach { payment ->
                            TransactionItem(
                                payment = payment,
                                onClick = { onSelectPayment(payment.id) }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = onViewAllClick,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = stringResource(R.string.home_view_all),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NetlessAccentBlue
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ZenvestBottomNavBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isDark) Color(0xF5111827) else Color(0xF7FFFFFF),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isDark) Color(0x2638BDF8) else Color(0x33CBD5E1)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Pair(Icons.Default.Home, stringResource(R.string.nav_home)),
                Pair(Icons.Outlined.Settings, stringResource(R.string.nav_settings))
            )

            tabs.forEachIndexed { index, pair ->
                val isSelected = selectedTab == index
                val tabBg = if (isSelected) {
                    if (isDark) Color(0x2638BDF8) else Color(0xFFE2E8F0)
                } else Color.Transparent

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(tabBg)
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = pair.first,
                        contentDescription = pair.second,
                        tint = when {
                            isSelected -> if (isDark) Color(0xFF38BDF8) else ZenvestTextPrimary
                            isDark -> ZenvestTextMuted
                            else -> ZenvestTextSecondary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pair.second,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = when {
                            isSelected -> if (isDark) Color(0xFF38BDF8) else ZenvestTextPrimary
                            isDark -> ZenvestTextMuted
                            else -> ZenvestTextSecondary
                        }
                    )
                }
            }
        }
    }
}


@Suppress("LongMethod")
@Composable
fun TransactionItem(payment: PaymentDetails, onClick: () -> Unit) {
    val isDark = LocalNetlessDarkMode.current
    val status = payment.status.name
    val color = statusColor(status)
    val bgColor = statusBackgroundColor(status)
    val displayName = payment.recipientName ?: payment.phoneNumber
    val initial = displayName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString()
        ?: stringResource(R.string.currency_inr_symbol)

    // Map the initial to a deterministic pastel accent color
    val avatarGradient = remember(initial) {
        val seed = initial.firstOrNull()?.code ?: 0
        val palettes = listOf(
            listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
            listOf(Color(0xFF0EA5E9), Color(0xFF2563EB)),
            listOf(Color(0xFF10B981), Color(0xFF059669)),
            listOf(Color(0xFFF59E0B), Color(0xFFEA580C)),
            listOf(Color(0xFFEC4899), Color(0xFFBE185D))
        )
        palettes[seed % palettes.size]
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gradient initial avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(elevation = 2.dp, shape = CircleShape)
                    .background(
                        Brush.linearGradient(avatarGradient),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color.White else ZenvestTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDate(payment.timestamp),
                    fontSize = 11.sp,
                    color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.amount_rupees, CurrencyFormat.inr(payment.amount)),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else ZenvestTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = when (payment.status) {
                        PaymentStatus.COMPLETED -> stringResource(R.string.status_label_success)
                        PaymentStatus.FAILED -> stringResource(R.string.status_label_failed)
                        PaymentStatus.CANCELLED -> stringResource(R.string.status_label_cancelled)
                        PaymentStatus.PENDING -> stringResource(R.string.status_label_pending)
                        PaymentStatus.NEEDS_REVIEW -> stringResource(R.string.status_label_needs_review)
                        PaymentStatus.UNVERIFIED -> stringResource(R.string.status_label_unverified)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            }
        }
    }

    HorizontalDivider(
        color = if (isDark) ZenvestDividerDark else ZenvestDividerLight,
        thickness = 0.5.dp,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
fun PayContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findComponentActivity() }
    var phoneNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    // UPI 123Pay's IVR will not accept 5000 or more (AppConstants
    // .UPI123PAY_MAX_AMOUNT = 4999), so the cap is enforced before dialling.
    val isOverCap = (amount.toLongOrNull() ?: 0L) > AppConstants.UPI123PAY_MAX_AMOUNT.toLong()
    var selectedContactName by remember { mutableStateOf<String?>(null) }
    var showContactPicker by remember { mutableStateOf(false) }
    var showContactPermissionDialog by remember { mutableStateOf(false) }
    val permissionManager = remember(hostActivity) {
        hostActivity?.let { PermissionManager(it) }
    }
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showContactPicker = true
    }

    val isDark = LocalNetlessDarkMode.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) NetlessDarkGray else Color.White,
        title = {
            Text(
                text = stringResource(R.string.home_pay_contact_title),
                color = if (isDark) Color.White else ZenvestTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                selectedContactName?.let { name ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = LocalNetlessAccentTheme.current.accent.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = LocalNetlessAccentTheme.current.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.home_pay_contact_sending_to, name),
                                color = LocalNetlessAccentTheme.current.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { input ->
                            phoneNumber = PaymentInputValidator.sanitizePhoneNumber(input)
                            selectedContactName = null
                        },
                        label = {
                            Text(stringResource(R.string.home_field_mobile_label), color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary)
                        },
                        placeholder = {
                            Text(stringResource(R.string.home_field_mobile_hint), color = if (isDark) NetlessTextGray else ZenvestTextMuted)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            focusedBorderColor = if (isDark) NetlessOutlineGray else Color(0xFF94A3B8),
                            unfocusedBorderColor = if (isDark) NetlessLightGray else Color(0xFFCBD5E1),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    IconButton(
                        onClick = {
                            val pm = permissionManager
                            if (pm == null) {
                                Toast.makeText(
                                    context.applicationContext,
                                    R.string.error_cannot_open_contacts,
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@IconButton
                            }
                            if (pm.hasContactPermission()) {
                                showContactPicker = true
                            } else {
                                showContactPermissionDialog = true
                            }
                        },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .size(48.dp)
                            .background(
                                color = LocalNetlessAccentTheme.current.accent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PermContactCalendar,
                            contentDescription = stringResource(R.string.detail_label_recipient),
                            tint = LocalNetlessAccentTheme.current.accent
                        )
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } && it.length <= 6) {
                            amount = it
                        }
                    },
                    prefix = {
                        Text(
                            text = stringResource(R.string.currency_inr_symbol),
                            color = if (isDark) Color.White else ZenvestTextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    },
                    label = {
                        Text(stringResource(R.string.home_field_amount_label), color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary)
                    },
                    placeholder = {
                        Text(stringResource(R.string.home_field_amount_hint), color = if (isDark) NetlessTextGray else ZenvestTextMuted)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = isOverCap,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                        unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                        focusedBorderColor = if (isOverCap) NetlessStatusError else if (isDark) NetlessOutlineGray else Color(0xFF94A3B8),
                        unfocusedBorderColor = if (isOverCap) NetlessStatusError else if (isDark) NetlessLightGray else Color(0xFFCBD5E1),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )

                // Quick Amount Helper Chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.home_quick_amounts_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickAmounts = listOf("100", "200", "500", "1000", "2000")
                        quickAmounts.forEach { chipAmt ->
                            val isSelected = amount == chipAmt
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) {
                                            LocalNetlessAccentTheme.current.accent.copy(alpha = 0.25f)
                                        } else if (isDark) {
                                            NetlessChipBackground
                                        } else {
                                            Color(0xFFF1F5F9)
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) {
                                            LocalNetlessAccentTheme.current.accent
                                        } else if (isDark) {
                                            NetlessCardBorder
                                        } else {
                                            Color(0xFFE2E8F0)
                                        },
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { amount = chipAmt }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.amount_rupees, chipAmt),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) LocalNetlessAccentTheme.current.accent else Color.White
                                )
                            }
                        }
                    }
                }

                // Told here, in the dialog, while the number can still be
                // corrected. The IVR itself only rejects an over-cap amount
                // mid-call, after the user has already dialled.
                if (isOverCap) {
                    Text(
                        text = Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context),
                        color = NetlessStatusError,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            val canTransfer = phoneNumber.length == 10 && amount.isNotEmpty() &&
                amount != "0" && !isOverCap
            TextButton(
                onClick = { onConfirm(phoneNumber, amount, selectedContactName) },
                enabled = canTransfer
            ) {
                Text(
                    stringResource(R.string.action_transfer),
                    color = if (canTransfer) {
                        LocalNetlessAccentTheme.current.accent
                    } else {
                        NetlessTextGray
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = NetlessTextLightGray)
            }
        }
    )

    if (showContactPicker) {
        ContactPickerDialog(
            onDismiss = { showContactPicker = false },
            onContactSelected = { contact ->
                phoneNumber = contact.phoneNumber
                selectedContactName = contact.name
                showContactPicker = false
            }
        )
    }

    if (showContactPermissionDialog) {
        PermissionExplanationDialog(
            title = stringResource(R.string.home_perm_contacts_title),
            message = stringResource(R.string.home_perm_contacts_msg),
            confirmButtonText = stringResource(R.string.home_perm_grant),
            onConfirm = {
                showContactPermissionDialog = false
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
            onDismiss = { showContactPermissionDialog = false }
        )
    }
}

@Composable
fun PermissionExplanationDialog(
    title: String,
    message: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetlessDarkGray,
        title = {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = NetlessTextPale,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = LocalNetlessAccentTheme.current.accent
                )
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_not_now), color = NetlessTextLightGray)
            }
        }
    )
}

// Utility functions
fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale("en", "IN"))
    return formatter.format(Date(timestamp))
}
