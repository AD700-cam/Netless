// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless
//
// Home screen: the entry point to both payment rails (Scan QR and Pay
// Contact), recent payments, and settings. UI only — the transfer
// orchestration and permission gating live in MainActivityHelper.

package com.netless.app

import android.Manifest
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.PermContactCalendar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netless.app.constants.AppConstants
import com.netless.app.constants.PermissionConstants
import com.netless.app.data.PaymentDetails
import com.netless.app.data.PaymentStatus
import com.netless.app.data.SettingsRepository
import com.netless.app.data.TestResultsManager
import com.netless.app.data.Transaction
import com.netless.app.helpers.MainActivityHelper
import com.netless.app.helpers.SetupHelper
import com.netless.app.managers.PermissionManager
import com.netless.app.payment.PaymentInputValidator
import com.netless.app.payment.Upi123CallStringBuilder
import com.netless.app.payment.messageFor
import com.netless.app.ui.activities.SettingsActivity
import com.netless.app.ui.activities.TransactionHistoryActivity
import com.netless.app.ui.components.AvatarCircle
import com.netless.app.ui.components.NetlessBrandWordmark
import com.netless.app.ui.components.NotificationsDialog
import com.netless.app.ui.components.ProfileDialog
import com.netless.app.ui.components.TransactionDetailDialog
import com.netless.app.ui.components.getAvatarTemplate
import com.netless.app.ui.dialogs.ContactPickerDialog
import com.netless.app.ui.theme.BlueAccentTheme
import com.netless.app.ui.theme.LocalNetlessAccentTheme
import com.netless.app.ui.theme.LocalNetlessDarkMode
import com.netless.app.ui.theme.NetlessAccentBlue
import com.netless.app.ui.theme.NetlessBrandElectricGreen
import com.netless.app.ui.theme.NetlessBrandEmerald
import com.netless.app.ui.theme.NetlessBrandFontFamily
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
    val displayBankName = remember(savedBank, selectedBankName, settingsState.bankId) {
        if (selectedBankName.isNotBlank() && selectedBankName != "Select Bank") {
            selectedBankName
        } else {
            val id = savedBank.ifEmpty { settingsState.bankId }
            SetupHelper.getBankDisplayName(id).takeIf { it.isNotBlank() && it != "Select Bank" }
                ?: "Linked UPI Bank"
        }
    }
    val primaryCarrier = remember(context) { SetupHelper.detectPrimaryCarrier(context) }
    val simRailLabel = remember(primaryCarrier) {
        if (primaryCarrier.isNotBlank()) "Offline UPI • $primaryCarrier" else "Offline UPI • SIM 1"
    }

    val isDark = LocalNetlessDarkMode.current
    var activeNavTab by remember { mutableStateOf(0) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showBankDetailsDialog by remember { mutableStateOf(false) }
    var showServicesDialog by remember { mutableStateOf(false) }
    var demoSelectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090D14)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF080B11),
                            Color(0xFF0C1018),
                            Color(0xFF090D14)
                        )
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

                    // ─── TOP BAR (Avatar Left | NETLESS Center | Bell Right) ───
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Avatar (Opens Profile Dialog)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(elevation = 4.dp, shape = CircleShape)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF00E5FF), Color(0xFFA855F7))
                                    )
                                )
                                .border(1.dp, Color(0x4038BDF8), CircleShape)
                                .clickable { showProfileDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            val template = getAvatarTemplate(userAvatarId)
                            if (userAvatarId == 0) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = stringResource(R.string.profile_title),
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = template.emoji,
                                    fontSize = 20.sp
                                )
                            }
                        }

                        // NETLESS Wordmark (Centered)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "NET",
                                fontFamily = NetlessBrandFontFamily,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = Color.White
                            )
                            Text(
                                text = "LESS",
                                fontFamily = NetlessBrandFontFamily,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = Color(0xFF10B981)
                            )
                        }

                        // Notification Bell (Right)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable { showNotificationsDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = stringResource(R.string.notif_channel_payment_results),
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ─── SIGNATURE ZENVEST FROSTED GLASS BANK CARD ───
                    ZenvestBalanceCard(
                        selectedBankName = displayBankName,
                        userPhoneNumber = userPhoneNumber,
                        railLabel = simRailLabel,
                        onCardClick = { showBankDetailsDialog = true }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

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
                        }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // ─── TRANSACTIONS SHEET ───
                    ZenvestTransactionsSheet(
                        recentPayments = recentPayments,
                        isLoading = isLoading,
                        error = error,
                        onSelectPayment = { paymentId ->
                            if (paymentId.startsWith("demo_")) {
                                val demoTx = when (paymentId) {
                                    "demo_1" -> Transaction(
                                        transactionId = "demo_1",
                                        amount = "500.00",
                                        status = "COMPLETED",
                                        bankName = selectedBankName.ifEmpty { "HDFC Bank" },
                                        smsExcerpt = "Rs 500.00 debited from HDFC Bank AC 9876. " +
                                            "Ref: NPCI/123P/984729104829",
                                        timestamp = System.currentTimeMillis() - 15 * 60 * 1000L,
                                        recipientName = "Cafe Coffee Day",
                                        phoneNumber = "9876543210",
                                        bankRef = "NPCI123P984729"
                                    )
                                    "demo_2" -> Transaction(
                                        transactionId = "demo_2",
                                        amount = "120.50",
                                        status = "COMPLETED",
                                        bankName = selectedBankName.ifEmpty { "HDFC Bank" },
                                        smsExcerpt = "Rs 120.50 debited from HDFC Bank AC 9876. " +
                                            "Ref: NPCI/123P/837291029384",
                                        timestamp = System.currentTimeMillis() - 2 * 3600 * 1000L,
                                        recipientName = "Grocery Mart",
                                        phoneNumber = "9123456789",
                                        bankRef = "NPCI123P837291"
                                    )
                                    else -> Transaction(
                                        transactionId = "demo_3",
                                        amount = "350.00",
                                        status = "COMPLETED",
                                        bankName = selectedBankName.ifEmpty { "HDFC Bank" },
                                        smsExcerpt = "Rs 350.00 debited from HDFC Bank AC 9876. " +
                                            "Ref: NPCI/123P/726194829102",
                                        timestamp = System.currentTimeMillis() - 5 * 3600 * 1000L,
                                        recipientName = "Taxi Ride",
                                        phoneNumber = "9988776655",
                                        bankRef = "NPCI123P726194"
                                    )
                                }
                                demoSelectedTransaction = demoTx
                            } else {
                                transactionViewModel.selectTransaction(paymentId)
                            }
                        },
                        onRetry = { transactionViewModel.refresh() },
                        onViewAllClick = {
                            context.startActivity(Intent(context, TransactionHistoryActivity::class.java))
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // ─── DOCKED ZENVEST BOTTOM NAVIGATION BAR ───
                ZenvestBottomNavBar(
                    selectedTab = activeNavTab,
                    onSelectTab = { tabIndex ->
                        activeNavTab = tabIndex
                        when (tabIndex) {
                            0 -> { /* Home tab - active */ }
                            1 -> {
                                // Services tab - Open Offline Banking Tools Dialog!
                                showServicesDialog = true
                            }
                            2 -> {
                                // Activity tab - Open TransactionHistoryActivity!
                                context.startActivity(Intent(context, TransactionHistoryActivity::class.java))
                            }
                        }
                    }
                )
            }

            // User Profile Dialog with Avatar Templates
            if (showProfileDialog) {
                ProfileDialog(
                    userName = userName,
                    avatarId = userAvatarId,
                    userPhoneNumber = userPhoneNumber,
                    selectedBankName = displayBankName,
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
                    selectedBankName = displayBankName,
                    userPhoneNumber = userPhoneNumber,
                    onDismiss = { showNotificationsDialog = false }
                )
            }

            // Bank Details Dialog (Opens when tapping the frosted bank card)
            if (showBankDetailsDialog) {
                BankDetailsDialog(
                    bankName = displayBankName,
                    userPhoneNumber = userPhoneNumber,
                    onCheckBalance = {
                        showBankDetailsDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.home_card_details_balance_checking),
                            Toast.LENGTH_SHORT
                        ).show()
                        safeLaunchTelephony(context, AppConstants.DEFAULT_UPI_SERVICE_NUMBER)
                    },
                    onSwitchBankOrSim = {
                        showBankDetailsDialog = false
                        context.startActivity(Intent(context, TestConfigurationActivity::class.java))
                    },
                    onDismiss = { showBankDetailsDialog = false }
                )
            }

            // Offline Services Suite Dialog (Opens from bottom navigation Services tab)
            if (showServicesDialog) {
                OfflineServicesDialog(
                    onCheckBalance = {
                        showServicesDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.home_card_details_balance_checking),
                            Toast.LENGTH_SHORT
                        ).show()
                        safeLaunchTelephony(context, AppConstants.DEFAULT_UPI_SERVICE_NUMBER)
                    },
                    onChangeUpiPin = {
                        showServicesDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.service_dialing_pin_reset),
                            Toast.LENGTH_SHORT
                        ).show()
                        safeLaunchTelephony(context, "*99*7#")
                    },
                    onSwitchBankOrSim = {
                        showServicesDialog = false
                        context.startActivity(Intent(context, TestConfigurationActivity::class.java))
                    },
                    onCallHelpline = {
                        showServicesDialog = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.service_dialing_helpline),
                            Toast.LENGTH_SHORT
                        ).show()
                        safeLaunchTelephony(context, "14431")
                    },
                    onDismiss = { showServicesDialog = false }
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

            // Detail dialog for demo item
            demoSelectedTransaction?.let { transaction ->
                TransactionDetailDialog(
                    transaction = transaction,
                    onDismiss = { demoSelectedTransaction = null },
                    onDelete = { demoSelectedTransaction = null }
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
fun BankEmblemIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val color = Color.White
        val stroke = 2.dp.toPx()
        // Outer square frame with rounded corners
        drawRoundRect(
            color = color,
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(w - 2.dp.toPx(), h - 2.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        // Center geometric core
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.32f, h * 0.32f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.36f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSmartChipTracks() {
    val w = size.width
    val h = size.height
    val strokeColor = Color(CHIP_STROKE_COLOR_ARGB).copy(alpha = CHIP_STROKE_ALPHA)

    // Outer etched ring
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(w * CHIP_RING_INSET_LEFT, h * CHIP_RING_INSET_TOP),
        size = androidx.compose.ui.geometry.Size(w * CHIP_RING_WIDTH, h * CHIP_RING_HEIGHT),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(CHIP_CORNER_RADIUS, CHIP_CORNER_RADIUS),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = CHIP_STROKE_WIDTH)
    )

    // Horizontal etched line
    drawLine(
        color = strokeColor,
        start = Offset(0f, h * CHIP_MID_H),
        end = Offset(w, h * CHIP_MID_H),
        strokeWidth = CHIP_STROKE_WIDTH
    )

    // Vertical etched track lines
    drawLine(
        color = strokeColor,
        start = Offset(w * CHIP_TRACK_LEFT, 0f),
        end = Offset(w * CHIP_TRACK_LEFT, h * CHIP_MID_H),
        strokeWidth = CHIP_STROKE_WIDTH
    )
    drawLine(
        color = strokeColor,
        start = Offset(w * CHIP_TRACK_RIGHT, 0f),
        end = Offset(w * CHIP_TRACK_RIGHT, h * CHIP_MID_H),
        strokeWidth = CHIP_STROKE_WIDTH
    )
    drawLine(
        color = strokeColor,
        start = Offset(w * CHIP_TRACK_LEFT, h * CHIP_MID_H),
        end = Offset(w * CHIP_TRACK_LEFT, h),
        strokeWidth = CHIP_STROKE_WIDTH
    )
    drawLine(
        color = strokeColor,
        start = Offset(w * CHIP_TRACK_RIGHT, h * CHIP_MID_H),
        end = Offset(w * CHIP_TRACK_RIGHT, h),
        strokeWidth = CHIP_STROKE_WIDTH
    )
}

@Composable
fun MetallicSmartCardChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFCBD5E1),
                        Color(0xFF94A3B8),
                        Color(0xFFE2E8F0),
                        Color(0xFF64748B)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(100f, 100f)
                )
            )
            .border(0.5.dp, Color(0xFF475569), RoundedCornerShape(6.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawSmartChipTracks()
        }
    }
}

@Composable
fun ContactlessNfcWave(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 20.dp, height = 24.dp)) {
        val waveColor = Color(0xFF94A3B8)
        val strokeWidth = 2.dp.toPx()
        val centerY = size.height / 2f

        for (i in 1..3) {
            val r = (i * 7).dp.toPx()
            drawArc(
                color = waveColor,
                startAngle = -35f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(-r + 2.dp.toPx(), centerY - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )
        }
    }
}

@Composable
fun BankCardItem(
    bankName: String,
    railLabel: String,
    last4: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x66000000),
                spotColor = Color(0x4D10B981)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xCC1E293B),
                            Color(0xB80F172A),
                            Color(0xCC090E17)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x99FFFFFF),
                            Color(0x3338BDF8),
                            Color(0x1F10B981),
                            Color(0x1F818CF8)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            // Glass reflection highlight bar across top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0x66FFFFFF),
                                Color(0x4038BDF8),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Inner subtle radial light sheen for glass refraction
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1838BDF8), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.22f),
                        radius = size.width * 0.45f
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Row: Bank Emblem + Name (Left) & Glass Rail Pill (Right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BankEmblemIcon()
                        Text(
                            text = bankName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Glassmorphic rail pill badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x2610B981))
                            .border(0.8.dp, Color(0x4D10B981), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = railLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6EE7B7)
                            )
                        }
                    }
                }

                // Middle Row: Metallic Chip + Contactless Wave
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MetallicSmartCardChip()
                    ContactlessNfcWave()
                }

                // Bottom Row: Masked Account Number + Telephony Hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "•••• $last4",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color.White
                    )

                    Text(
                        text = stringResource(R.string.home_card_details_title),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZenvestBalanceCard(
    selectedBankName: String,
    userPhoneNumber: String = "",
    railLabel: String = stringResource(R.string.home_card_offline_upi),
    onCardClick: () -> Unit
) {
    val last4 = if (userPhoneNumber.length >= 4) userPhoneNumber.takeLast(4) else "9876"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Soft ambient radial glow behind the glass card
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .align(Alignment.Center)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3310B981), Color(0x2238BDF8), Color.Transparent),
                    center = Offset(size.width * 0.45f, size.height * 0.5f),
                    radius = size.width * 0.5f
                )
            )
        }

        BankCardItem(
            bankName = selectedBankName,
            railLabel = railLabel,
            last4 = last4,
            onClick = onCardClick
        )
    }
}


@Composable
fun BankDetailsDialog(
    bankName: String,
    userPhoneNumber: String,
    onCheckBalance: () -> Unit,
    onSwitchBankOrSim: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF161F2E),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BankEmblemIcon()
                        Text(
                            text = bankName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Account Number & Masked Phone
                val last4 = if (userPhoneNumber.length >= 4) userPhoneNumber.takeLast(4) else "9876"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.home_card_account_number),
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = "•••• $last4",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.home_card_offline_limit_label),
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = stringResource(R.string.home_offline_limit_value),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Rail Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B1019))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_card_details_sim_slot),
                        fontSize = 12.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.home_card_details_ivr_rail),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = stringResource(R.string.home_card_details_ussd_rail),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Check Balance + Switch SIM / Bank
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Check Balance button
                    Button(
                        onClick = onCheckBalance,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.home_card_details_balance_btn),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Switch SIM / Bank button
                    OutlinedButton(
                        onClick = onSwitchBankOrSim,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF10B981)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.home_card_details_switch_btn),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OfflineServicesDialog(
    onCheckBalance: () -> Unit,
    onChangeUpiPin: () -> Unit,
    onSwitchBankOrSim: () -> Unit,
    onCallHelpline: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF131A26),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.services_dialog_title),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.services_dialog_subtitle),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.detail_close),
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Service items list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Check Balance
                    ServiceItemRow(
                        icon = Icons.Default.AccountBalance,
                        iconTint = Color(0xFF10B981),
                        title = stringResource(R.string.service_check_balance_title),
                        description = stringResource(R.string.service_check_balance_desc),
                        onClick = onCheckBalance
                    )

                    // 2. Change UPI PIN
                    ServiceItemRow(
                        icon = Icons.Default.LockReset,
                        iconTint = Color(0xFF38BDF8),
                        title = stringResource(R.string.service_change_pin_title),
                        description = stringResource(R.string.service_change_pin_desc),
                        onClick = onChangeUpiPin
                    )

                    // 3. Offline Limits
                    ServiceItemRow(
                        icon = Icons.Default.Shield,
                        iconTint = Color(0xFF818CF8),
                        title = stringResource(R.string.service_limits_title),
                        description = stringResource(R.string.service_limits_desc),
                        badgeText = "PROTECTED",
                        onClick = {}
                    )

                    // 4. Switch Bank / SIM
                    ServiceItemRow(
                        icon = Icons.Default.SimCard,
                        iconTint = Color(0xFFF59E0B),
                        title = stringResource(R.string.service_switch_bank_title),
                        description = stringResource(R.string.service_switch_bank_desc),
                        onClick = onSwitchBankOrSim
                    )

                    // 5. NPCI Helpline
                    ServiceItemRow(
                        icon = Icons.Default.SupportAgent,
                        iconTint = Color(0xFFF43F5E),
                        title = stringResource(R.string.service_helpline_title),
                        description = stringResource(R.string.service_helpline_desc),
                        onClick = onCallHelpline
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B))
                ) {
                    Text(
                        text = stringResource(R.string.detail_close),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1B2333),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(iconTint.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = iconTint
                    )
                }
            }
        }
    }
}


@Composable
private fun ZenvestActionButtonsRow(
    onSendClick: () -> Unit,
    onScanClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Send ↗ Pill Button (Solid White)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(27.dp),
                    spotColor = Color(0x33000000)
                )
                .clip(RoundedCornerShape(27.dp))
                .background(Color.White)
                .clickable(onClick = onSendClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_action_send),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.CallMade,
                contentDescription = null,
                tint = Color(0xFF0F172A),
                modifier = Modifier.size(19.dp)
            )
        }

        // Scan QR ⛶ Pill Button (Dark Glass with Radiant Emerald Border)
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(Color(0xFF0A0F18))
                .border(
                    1.5.dp,
                    Color(0xFF10B981),
                    RoundedCornerShape(27.dp)
                )
                .clickable(onClick = onScanClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.home_action_scan),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF10B981)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(19.dp)
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
    onViewAllClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                spotColor = Color.Black
            ),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xFF111722)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            // Top Drag Handle Bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF334155))
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Header: "Recent Payments" (Left) & "View All" (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_recent_payments),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                TextButton(
                    onClick = onViewAllClick,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_view_all),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF10B981)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Color(0xFF10B981),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.home_loading_transactions),
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.home_failed_load_transactions),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(onClick = onRetry) {
                            Text(
                                text = stringResource(R.string.home_retry),
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                recentPayments.isEmpty() -> {
                    val demoPayments = remember {
                        listOf(
                            PaymentDetails(
                                id = "demo_1",
                                recipientName = "Cafe Coffee Day",
                                phoneNumber = "9876543210",
                                amount = 500.0,
                                timestamp = System.currentTimeMillis() - 15 * 60 * 1000L,
                                status = PaymentStatus.COMPLETED
                            ),
                            PaymentDetails(
                                id = "demo_2",
                                recipientName = "Grocery Mart",
                                phoneNumber = "9123456789",
                                amount = 120.50,
                                timestamp = System.currentTimeMillis() - 2 * 3600 * 1000L,
                                status = PaymentStatus.COMPLETED
                            ),
                            PaymentDetails(
                                id = "demo_3",
                                recipientName = "Taxi Ride",
                                phoneNumber = "9988776655",
                                amount = 350.0,
                                timestamp = System.currentTimeMillis() - 5 * 3600 * 1000L,
                                status = PaymentStatus.COMPLETED
                            )
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        demoPayments.forEach { payment ->
                            TransactionItem(
                                payment = payment,
                                onClick = { onSelectPayment(payment.id) }
                            )
                        }
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        recentPayments.take(5).forEach { payment ->
                            TransactionItem(
                                payment = payment,
                                onClick = { onSelectPayment(payment.id) }
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
    onSelectTab: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0C1017),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            Color(0xFF1E293B)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(Icons.Filled.Home, stringResource(R.string.nav_home), 0),
                Triple(Icons.Default.Widgets, stringResource(R.string.nav_services), 1),
                Triple(Icons.Default.ReceiptLong, stringResource(R.string.nav_activity), 2)
            )

            tabs.forEach { (icon, label, index) ->
                val isSelected = selectedTab == index
                val tabColor = if (isSelected) Color(0xFF10B981) else Color(0xFF64748B)

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectTab(index) }
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tabColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = tabColor
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionItem(payment: PaymentDetails, onClick: () -> Unit) {
    val displayName = payment.recipientName ?: payment.phoneNumber
    val isSuccess = payment.status == PaymentStatus.COMPLETED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Payee Name + "Offline" subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.home_card_offline_subtitle),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        }

        // Right Column: Amount + Status Badge
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.amount_rupees, CurrencyFormat.inr(payment.amount)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSuccess) Color(0x2610B981) else Color(0x26EF4444))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isSuccess) {
                        stringResource(R.string.home_status_offline_success)
                    } else {
                        stringResource(R.string.status_label_failed)
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSuccess) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }
        }
    }

    HorizontalDivider(
        color = Color(0xFF1E293B),
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

private fun safeLaunchTelephony(context: Context, serviceNumber: String) {
    val encodedUri = android.net.Uri.parse("tel:" + android.net.Uri.encode(serviceNumber))
    val hasCallPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED

    val intent = if (hasCallPermission) {
        Intent(Intent.ACTION_CALL, encodedUri)
    } else {
        Intent(Intent.ACTION_DIAL, encodedUri)
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("MainActivity", "Telephony activity not found for $serviceNumber", e)
    } catch (e: SecurityException) {
        Log.w("MainActivity", "Call permission denied for $serviceNumber", e)
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, encodedUri))
        } catch (inner: ActivityNotFoundException) {
            Log.w("MainActivity", "Fallback dialer activity not found for $serviceNumber", inner)
        }
    }
}

// ---------------------------------------------------------------------------
// EMV chip-tile drawing constants — used by drawSmartChipTracks().
// Declared as top-level const val so Detekt's MagicNumber rule ignores them.
// ---------------------------------------------------------------------------
private const val CHIP_STROKE_COLOR_ARGB = 0xFF334155L
private const val CHIP_STROKE_ALPHA = 0.7f
private const val CHIP_STROKE_WIDTH = 1.2f
private const val CHIP_RING_INSET_LEFT = 0.18f // fraction of chip width
private const val CHIP_RING_INSET_TOP = 0.15f // fraction of chip height
private const val CHIP_RING_WIDTH = 0.64f
private const val CHIP_RING_HEIGHT = 0.70f
private const val CHIP_CORNER_RADIUS = 4f
private const val CHIP_MID_H = 0.5f // horizontal midline
private const val CHIP_TRACK_LEFT = 0.38f // left vertical contact track
private const val CHIP_TRACK_RIGHT = 0.62f // right vertical contact track

