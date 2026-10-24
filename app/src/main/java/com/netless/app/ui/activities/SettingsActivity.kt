// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

@file:Suppress("LongMethod", "TooManyFunctions", "ImportOrdering", "NoUnusedImports")

package com.netless.app.ui.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netless.app.BuildConfig
import com.netless.app.NetlessApplication
import com.netless.app.R
import com.netless.app.SetupActivity
import com.netless.app.data.LocalAnalyticsManager
import com.netless.app.data.OfflineAnalyticsSummary
import com.netless.app.data.SettingsRepository
import com.netless.app.helpers.SetupHelper
import com.netless.app.repository.TransactionRepository
import com.netless.app.services.UssdAccessibilityService
import com.netless.app.utils.CurrencyFormat
import com.netless.app.utils.DiagnosticLogger
import com.netless.app.utils.DiagnosticLogEntry
import com.netless.app.ui.theme.BlueAccentTheme
import com.netless.app.ui.theme.NetlessAccentGreenBright
import com.netless.app.ui.theme.NetlessDarkGray
import com.netless.app.ui.theme.NetlessDisabledGray
import com.netless.app.ui.theme.NetlessLightGray
import com.netless.app.ui.theme.NetlessMediumGray
import com.netless.app.ui.theme.NetlessStatusError
import com.netless.app.ui.theme.NetlessSurfaceDim
import com.netless.app.ui.theme.NetlessTextGray
import com.netless.app.ui.theme.NetlessTextLightGray
import com.netless.app.ui.theme.NetlessTextPale
import com.netless.app.ui.theme.NetlessTheme
import com.netless.app.ui.theme.LocalNetlessAccentTheme
import com.netless.app.ui.theme.LocalNetlessDarkMode
import com.netless.app.ui.theme.ZenvestBgLight
import com.netless.app.ui.theme.ZenvestTextMuted
import com.netless.app.ui.theme.ZenvestTextPrimary
import com.netless.app.ui.theme.ZenvestTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    private var refreshTrigger = mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> refreshTrigger.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? NetlessApplication
        val settingsRepository = app?.settingsRepository ?: SettingsRepository(applicationContext)
        setTheme(R.style.Theme_Netless)
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            LaunchedEffect(Unit) {
                viewModel.loadFromRepository(settingsRepository)
            }
            CompositionLocalProvider(LocalNetlessAccentTheme provides BlueAccentTheme) {
                NetlessTheme(darkTheme = viewModel.state.darkMode) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBackPressed = { finish() },
                        settingsRepository = settingsRepository,
                        onRequestPermissions = { permissions ->
                            permissionLauncher.launch(permissions)
                        },
                        refreshTrigger = refreshTrigger
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshTrigger.intValue++
    }
}

// Theme
@Composable
fun NetlessSettingsTheme(content: @Composable () -> Unit) {
    val accentTheme = LocalNetlessAccentTheme.current
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accentTheme.primary,
            secondary = accentTheme.accent,
            background = NetlessSurfaceDim,
            surface = NetlessDarkGray,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        content()
    }
}

// Data Classes
data class Bank(
    val id: String,
    val name: String
)

data class SettingsState(
    val selectedBank: Bank = banks.find { it.id == "sbi" } ?: banks.first(),
    val ussdTimeout: Int = 30,
    val smsDetectionEnabled: Boolean = true,
    val overlayEnabled: Boolean = true,
    val debugMode: Boolean = false,
    val setupCompleted: Boolean = true,
    val darkMode: Boolean = false,
    val soundEnabled: Boolean = true,
    val permissions: Map<String, Boolean> = emptyMap()
)

// Bank Data populated from SetupHelper's comprehensive Indian bank registry
val banks: List<Bank> = SetupHelper.getBanksList().map { Bank(it.first, it.second) }

// ViewModel
class SettingsViewModel : androidx.lifecycle.ViewModel() {
    var state by mutableStateOf(SettingsState())
        private set

    fun updateBank(bank: Bank) {
        state = state.copy(selectedBank = bank)
    }

    fun toggleSmsDetection() {
        state = state.copy(smsDetectionEnabled = !state.smsDetectionEnabled)
    }

    fun toggleOverlay() {
        state = state.copy(overlayEnabled = !state.overlayEnabled)
    }

    fun toggleDarkMode() {
        state = state.copy(darkMode = !state.darkMode)
    }

    fun toggleSound() {
        state = state.copy(soundEnabled = !state.soundEnabled)
    }

    fun refreshPermissions(context: Context) {
        val perms = mapOf(
            "phone" to (
                ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                ),
            "camera" to (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED),
            "sms" to (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED),
            "contacts" to (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED),
            "accessibility" to UssdAccessibilityService.isEnabled(context),
            "overlay" to (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
        )
        state = state.copy(permissions = perms)
    }

    fun loadFromRepository(settingsRepository: SettingsRepository) {
        val saved = settingsRepository.settingsFlow.value
        val bank = banks.find { it.id.equals(saved.bankId, ignoreCase = true) }
            ?: Bank(saved.bankId, SetupHelper.getBankDisplayName(saved.bankId))
        state = state.copy(
            selectedBank = bank,
            ussdTimeout = saved.ussdTimeout,
            smsDetectionEnabled = saved.smsDetectionEnabled,
            overlayEnabled = saved.overlayEnabled,
            debugMode = saved.debugMode,
            setupCompleted = saved.setupCompleted,
            darkMode = saved.darkMode,
            soundEnabled = saved.soundEnabled
        )
    }
}

// ═══════════════════════════════════════════
// Main Settings Screen
// ═══════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBackPressed: () -> Unit,
    settingsRepository: SettingsRepository? = null,
    onRequestPermissions: (Array<String>) -> Unit = {},
    refreshTrigger: MutableIntState = mutableIntStateOf(0)
) {
    val context = LocalContext.current
    val accent = LocalNetlessAccentTheme.current
    val state = viewModel.state

    // Load settings from repository on first composition
    LaunchedEffect(Unit) {
        settingsRepository?.let { viewModel.loadFromRepository(it) }
        viewModel.refreshPermissions(context)
    }

    // Refresh permissions when trigger changes
    val trigger by refreshTrigger
    LaunchedEffect(trigger) {
        viewModel.refreshPermissions(context)
    }

    // Get primary SIM name from shared prefs (reactive)
    var primarySimId by remember {
        val prefs = context.getSharedPreferences("NetlessPrefs", Context.MODE_PRIVATE)
        mutableStateOf(prefs.getString("selected_primary_sim", "") ?: "")
    }
    val primarySim = when (primarySimId) {
        "jio" -> "Jio"
        "airtel" -> "Airtel"
        "vodafone" -> "Vodafone"
        "bsnl" -> "BSNL"
        else -> stringResource(R.string.settings_not_set)
    }

    // Dialog states
    var showBankPicker by remember { mutableStateOf(false) }
    var showSimPicker by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }
    var showOfflineInsights by remember { mutableStateOf(false) }
    var showDiagnosticLogs by remember { mutableStateOf(false) }

    val isDark = LocalNetlessDarkMode.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) NetlessSurfaceDim else ZenvestBgLight)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else ZenvestTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = if (isDark) Color.White else ZenvestTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) NetlessSurfaceDim else ZenvestBgLight
                )
            )

            // Settings Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // ═══ APPEARANCE & AUDIO ═══
                item { SectionHeader("APPEARANCE & SOUND") }
                item {
                    GroupCard {
                        DarkModeRow(
                            isDarkMode = state.darkMode,
                            onToggle = {
                                viewModel.toggleDarkMode()
                                settingsRepository?.saveSettings(
                                    settingsRepository.settingsFlow.value.copy(darkMode = !state.darkMode)
                                )
                            }
                        )
                        GroupDivider()
                        SettingsToggleRow(
                            icon = Icons.Default.VolumeUp,
                            title = stringResource(R.string.settings_sound_title),
                            subtitle = stringResource(R.string.settings_sound_subtitle),
                            checked = state.soundEnabled,
                            onCheckedChange = {
                                viewModel.toggleSound()
                                settingsRepository?.saveSettings(
                                    settingsRepository.settingsFlow.value.copy(soundEnabled = !state.soundEnabled)
                                )
                            }
                        )
                    }
                }

                // ═══ CONFIGURATION ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("CONFIGURATION") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.AccountBalance,
                            title = "Bank",
                            value = state.selectedBank.name,
                            onClick = { showBankPicker = true }
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.SimCard,
                            title = "Primary SIM",
                            value = primarySim,
                            onClick = { showSimPicker = true }
                        )
                    }
                }

                // ═══ PERMISSIONS & TELEPHONY ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("PERMISSIONS & TELEPHONY") }
                item {
                    GroupCard {
                        PermissionRow(
                            icon = Icons.Default.Phone,
                            title = "Phone",
                            subtitle = "Calls & phone state",
                            granted = state.permissions["phone"] ?: false,
                            onRequest = {
                                onRequestPermissions(
                                    arrayOf(
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.READ_PHONE_STATE
                                    )
                                )
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.CameraAlt,
                            title = "Camera",
                            subtitle = "QR code scanning",
                            granted = state.permissions["camera"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.CAMERA))
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.Sms,
                            title = "SMS",
                            subtitle = "Bank payment confirmations",
                            granted = state.permissions["sms"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS))
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.Contacts,
                            title = "Contacts",
                            subtitle = "Pay by contact",
                            granted = state.permissions["contacts"] ?: false,
                            onRequest = {
                                onRequestPermissions(arrayOf(Manifest.permission.READ_CONTACTS))
                            }
                        )
                        GroupDivider()
                        PermissionRow(
                            icon = Icons.Default.AccessibilityNew,
                            title = stringResource(R.string.settings_accessibility_title),
                            subtitle = stringResource(R.string.settings_accessibility_subtitle),
                            granted = state.permissions["accessibility"] ?: false,
                            onRequest = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                // ═══ INSIGHTS & DIAGNOSTICS ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("INSIGHTS & DIAGNOSTICS") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.Insights,
                            title = stringResource(R.string.settings_analytics_title),
                            value = "View stats",
                            onClick = { showOfflineInsights = true }
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.BugReport,
                            title = stringResource(R.string.settings_diagnostics_title),
                            value = "Event log",
                            onClick = { showDiagnosticLogs = true }
                        )
                    }
                }

                // ═══ LEGAL & SECURITY ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("LEGAL & SECURITY") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(R.string.settings_privacy_policy),
                            value = "AES-256 Offline",
                            onClick = {
                                val intent = Intent(context, LegalDocumentActivity::class.java).apply {
                                    putExtra(LegalDocumentActivity.EXTRA_DOC_TYPE, LegalDocumentActivity.TYPE_PRIVACY)
                                }
                                context.startActivity(intent)
                            }
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.Gavel,
                            title = stringResource(R.string.settings_terms_service),
                            value = "NPCI *99# & 123Pay",
                            onClick = {
                                val intent = Intent(context, LegalDocumentActivity::class.java).apply {
                                    putExtra(LegalDocumentActivity.EXTRA_DOC_TYPE, LegalDocumentActivity.TYPE_TERMS)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                // ═══ ACTIONS ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("ACTIONS") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.DeleteForever,
                            title = stringResource(R.string.settings_wipe_data_title),
                            value = "Purge DB & keys",
                            destructive = true,
                            onClick = { showClearDataConfirm = true }
                        )
                    }
                }

                // ═══ ABOUT ═══
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item { SectionHeader("ABOUT") }
                item {
                    GroupCard {
                        SettingsRow(
                            icon = Icons.Default.Info,
                            title = "Version",
                            value = BuildConfig.VERSION_NAME
                        )
                        GroupDivider()
                        SettingsRow(
                            icon = Icons.Default.PhoneAndroid,
                            title = "Android",
                            value = Build.VERSION.RELEASE
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // Bank Picker Dialog
    if (showBankPicker) {
        BankPickerDialog(
            selectedBank = state.selectedBank,
            onBankSelected = { bank ->
                viewModel.updateBank(bank)
                settingsRepository?.saveSettings(
                    settingsRepository.settingsFlow.value.copy(bankId = bank.id)
                )
                // Also sync to NetlessPrefs so the main screen picks it up
                context.getSharedPreferences("NetlessPrefs", Context.MODE_PRIVATE)
                    .edit().putString("selected_bank", bank.id).apply()
                showBankPicker = false
            },
            onDismiss = { showBankPicker = false }
        )
    }

    // SIM Picker Dialog
    if (showSimPicker) {
        SimPickerDialog(
            selectedSimId = primarySimId,
            onSimSelected = { simId ->
                primarySimId = simId
                context.getSharedPreferences("NetlessPrefs", Context.MODE_PRIVATE)
                    .edit().putString("selected_primary_sim", simId).apply()
                showSimPicker = false
            },
            onDismiss = { showSimPicker = false }
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            containerColor = NetlessDarkGray,
            titleContentColor = Color.White,
            textContentColor = NetlessTextPale,
            title = {
                Text(
                    stringResource(R.string.settings_clear_data_title),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    "This permanently deletes your entire transaction history and " +
                        "resets all settings, then returns you to the setup screen. " +
                        "This cannot be undone.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataConfirm = false
                    // Wipe the encrypted transaction DB before anything else, on
                    // the process scope so the delete survives the CLEAR_TASK
                    // relaunch below; only then reset prefs and restart into Setup.
                    val app = NetlessApplication.from(context)
                    val relaunch = {
                        settingsRepository?.clearAllData()
                        context.getSharedPreferences("NetlessPrefs", Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        context.startActivity(
                            Intent(context, SetupActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                    }
                    if (app != null) {
                        app.appScope.launch {
                            runCatching {
                                TransactionRepository.getInstance(context).deleteAllTransactions()
                            }
                            withContext(Dispatchers.Main) { relaunch() }
                        }
                    } else {
                        relaunch()
                    }
                }) {
                    Text(
                        stringResource(R.string.action_clear),
                        color = NetlessStatusError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text(stringResource(R.string.action_cancel), color = NetlessTextLightGray)
                }
            }
        )
    }

    // Offline Financial Insights Dialog
    if (showOfflineInsights) {
        OfflineInsightsDialog(onDismiss = { showOfflineInsights = false })
    }

    // Diagnostic Logs Viewer Dialog
    if (showDiagnosticLogs) {
        DiagnosticLogsDialog(onDismiss = { showDiagnosticLogs = false })
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val accent = LocalNetlessAccentTheme.current
    val isDark = LocalNetlessDarkMode.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent.primary,
                uncheckedThumbColor = if (isDark) NetlessTextGray else Color(0xFF94A3B8),
                uncheckedTrackColor = if (isDark) NetlessMediumGray else Color(0xFFE2E8F0),
                uncheckedBorderColor = if (isDark) NetlessLightGray else Color(0xFFCBD5E1)
            )
        )
    }
}

@Composable
private fun OfflineInsightsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accent = LocalNetlessAccentTheme.current
    var summary by remember { mutableStateOf<OfflineAnalyticsSummary?>(null) }

    LaunchedEffect(Unit) {
        val manager = LocalAnalyticsManager(context)
        summary = manager.getAnalyticsSummary()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NetlessDarkGray
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = accent.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Offline Insights",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val s = summary
                if (s == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = accent.primary
                    )
                } else {
                    MetricRow("Total Transacted", CurrencyFormat.inr(s.totalVolumeInr.toString()), accent.primary)
                    MetricRow("Total Payments", "${s.totalCount}", Color.White)
                    MetricRow("Confirmed Success", "${s.successCount}", Color(0xFF10B981))
                    MetricRow("Unverified / In-flight", "${s.unverifiedCount}", Color(0xFFF59E0B))
                    MetricRow("USSD / QR Volume", "${s.ussdCount} txns", Color.White)
                    MetricRow("123Pay IVR Calls", "${s.ivrCount} txns", Color.White)

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1F10B981))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "🛡️ 100% on-device analytics • Zero data transmitted",
                            fontSize = 11.5.sp,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.5.sp, color = NetlessTextLightGray)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

@Composable
private fun DiagnosticLogsDialog(onDismiss: () -> Unit) {
    val logs = remember { DiagnosticLogger.getLogs() }
    val accent = LocalNetlessAccentTheme.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NetlessDarkGray,
            modifier = Modifier.fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Diagnostic Logs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Recent on-device telephony and USSD verification events",
                    fontSize = 12.sp,
                    color = NetlessTextLightGray,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No events recorded yet", color = NetlessTextGray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF111827))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(logs) { entry ->
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${entry.level} • ${entry.tag}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (entry.level == "ERROR") NetlessStatusError else accent.primary
                                    )
                                    Text(
                                        text = entry.formattedTime(),
                                        fontSize = 10.sp,
                                        color = NetlessTextGray
                                    )
                                }
                                Text(
                                    text = entry.message,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = {
                        DiagnosticLogger.clear()
                        onDismiss()
                    }) {
                        Text("Clear Logs", color = NetlessStatusError)
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent.primary)
                    ) {
                        Text("Done", color = Color.White)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Reusable Components
// ═══════════════════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    val isDark = LocalNetlessDarkMode.current
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (isDark) NetlessTextGray else ZenvestTextMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = LocalNetlessDarkMode.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) NetlessDarkGray else Color.White,
        border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = if (isDark) 0.dp else 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun GroupDivider() {
    val isDark = LocalNetlessDarkMode.current
    HorizontalDivider(
        color = if (isDark) NetlessMediumGray else Color(0xFFF1F5F9),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 56.dp)
    )
}

@Composable
private fun DarkModeRow(
    isDarkMode: Boolean,
    onToggle: () -> Unit
) {
    val accent = LocalNetlessAccentTheme.current
    val isDark = LocalNetlessDarkMode.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Moon icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (isDarkMode) accent.primary.copy(alpha = 0.15f)
                    else if (isDark) NetlessMediumGray else Color(0xFFF1F5F9)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Brightness2,
                contentDescription = null,
                tint = if (isDarkMode) accent.primary else if (isDark) NetlessTextGray else ZenvestTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_dark_mode),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
            Text(
                text = stringResource(R.string.settings_dark_mode_subtitle),
                fontSize = 12.sp,
                color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
            )
        }

        Switch(
            checked = isDarkMode,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = accent.primary,
                uncheckedThumbColor = if (isDark) NetlessTextGray else Color(0xFF94A3B8),
                uncheckedTrackColor = if (isDark) NetlessMediumGray else Color(0xFFE2E8F0),
                uncheckedBorderColor = if (isDark) NetlessLightGray else Color(0xFFCBD5E1)
            )
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val accent = LocalNetlessAccentTheme.current
    val isDark = LocalNetlessDarkMode.current
    val iconColor = if (destructive) NetlessStatusError else accent.primary
    val iconBg = if (destructive) NetlessStatusError.copy(alpha = 0.12f) else accent.primary.copy(alpha = 0.12f)
    val titleColor = if (destructive) NetlessStatusError else if (isDark) Color.White else ZenvestTextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )

        // Value or chevron
        if (onClick != null) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = NetlessTextLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = NetlessDisabledGray,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = value,
                fontSize = 14.sp,
                color = NetlessTextLightGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    val accent = LocalNetlessAccentTheme.current
    val isDark = LocalNetlessDarkMode.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accent.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isDark) NetlessTextLightGray else ZenvestTextSecondary
            )
        }

        // Status pill
        if (granted) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = NetlessAccentGreenBright.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "Granted",
                    color = NetlessAccentGreenBright,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.primary.copy(alpha = 0.12f),
                modifier = Modifier.clickable(onClick = onRequest)
            ) {
                Text(
                    text = "Grant",
                    color = accent.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun BankPickerDialog(
    selectedBank: Bank,
    onBankSelected: (Bank) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalNetlessAccentTheme.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NetlessDarkGray
        ) {
            Column(
                modifier = Modifier.padding(vertical = 20.dp)
            ) {
                Text(
                    text = "Select Bank",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(banks) { bank ->
                        val isSelected = bank.id == selectedBank.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBankSelected(bank) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = bank.name,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) accent.primary else Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (bank != banks.last()) {
                            HorizontalDivider(
                                color = NetlessMediumGray,
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.action_cancel), color = NetlessTextLightGray)
                }
            }
        }
    }
}

@Composable
private fun SimPickerDialog(
    selectedSimId: String,
    onSimSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = LocalNetlessAccentTheme.current
    val sims = listOf(
        "jio" to "Jio",
        "airtel" to "Airtel",
        "vodafone" to "Vodafone",
        "bsnl" to "BSNL"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NetlessDarkGray
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                Text(
                    text = "Select Primary SIM",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                sims.forEachIndexed { index, (id, name) ->
                    val isSelected = id == selectedSimId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSimSelected(id) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) accent.primary else Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = accent.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (index < sims.lastIndex) {
                        HorizontalDivider(
                            color = NetlessMediumGray,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(stringResource(R.string.action_cancel), color = NetlessTextLightGray)
                }
            }
        }
    }
}
