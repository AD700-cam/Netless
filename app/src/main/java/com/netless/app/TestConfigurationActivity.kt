// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.netless.app.constants.PermissionConstants
import com.netless.app.helpers.SetupHelper
import com.netless.app.helpers.TestConfigurationHelper
import com.netless.app.managers.CallType
import com.netless.app.ui.components.NetlessLogo
import com.netless.app.ui.dialogs.Upi123ProgressDialog
import com.netless.app.ui.dialogs.UssdProgressDialog
import com.netless.app.ui.theme.*
import kotlinx.coroutines.delay

class TestConfigurationActivity : ComponentActivity() {
    private lateinit var testHelper: TestConfigurationHelper

    // Phone-call permission group, requested before a test dial.
    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        testHelper.onPhonePermissionsResult(results.values.all { it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize test helper
        testHelper = TestConfigurationHelper(
            this,
            object : TestConfigurationHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread {
                        Toast.makeText(this@TestConfigurationActivity, message, Toast.LENGTH_LONG).show()
                    }
                }

                override fun updateUssdTesting(isTesting: Boolean) {}
                override fun updateUssdDialog(show: Boolean) {}
                override fun updateUssdTestCompleted(completed: Boolean) {}
                override fun updateUpi123Testing(isTesting: Boolean) {}
                override fun updateUpi123TestCompleted(completed: Boolean) {}
                override fun updateUpi123Dialog(show: Boolean) {}
                override fun updateUpi123ConfigurationOptions(show: Boolean) {}
                override fun updateVoiceTesting(isTesting: Boolean) {}
                override fun updateVoiceDialog(show: Boolean) {}
                override fun updateVoiceTestCompleted(completed: Boolean) {}
                override fun updateCallCompleteButton(show: Boolean) {}
                override fun updateUssdProgressMessage(message: String) {}
                override fun updateUssdConfigurationOptions(show: Boolean) {}

                override fun navigateToMain() {
                    val intent = Intent(this@TestConfigurationActivity, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }

                override fun requestPhonePermissions() {
                    phonePermissionLauncher.launch(PermissionConstants.PHONE_PERMISSIONS)
                }
            }
        )

        testHelper.initialize()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NetlessTheme {
                TestConfigurationScreen(testHelper = testHelper)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::testHelper.isInitialized) {
            testHelper.cleanup()
        }
    }
}

@Composable
fun TestConfigurationScreen(testHelper: TestConfigurationHelper) {
    val context = LocalContext.current
    val isDark = LocalNetlessDarkMode.current

    val testStates by testHelper.testStatesFlow.collectAsState()
    val ussdTestCompleted = testStates.ussdTestCompleted
    val upi123TestCompleted = testStates.upi123TestCompleted
    val ussdTesting = testStates.ussdTesting
    val upi123Testing = testStates.upi123Testing
    val showUssdDialog = testStates.showUssdDialog
    val showUpi123Dialog = testStates.showUpi123Dialog
    val showUssdConfigurationOptions = testStates.showUssdConfigurationOptions
    val showUpi123ConfigurationOptions = testStates.showUpi123ConfigurationOptions
    val ussdProgressMessage = testStates.ussdProgressMessage
    var pendingDial by remember { mutableStateOf<(() -> Unit)?>(null) }

    val isJioSim = !SetupHelper.isPrimarySimUssdCapable(context)
    var showAirtelGuide by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (isDark) {
                    Brush.verticalGradient(listOf(Color(0xFF0F172A), ZenvestBgDark))
                } else {
                    Brush.verticalGradient(listOf(ZenvestSkyTop, ZenvestSkyMid, ZenvestBgLight))
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 440.dp)
                .align(Alignment.Center)
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── TOP BAR: BACK TO SETUP ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            context.startActivity(Intent(context, SetupActivity::class.java))
                            (context as? ComponentActivity)?.finish()
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Setup",
                        tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Back to Setup",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ─── HEADER CARD: FROSTED ZENVEST ───
            ZenvestSetupCard(isDark = isDark) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NetlessLogo(size = 52.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NET",
                            fontFamily = NetlessBrandFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = if (isDark) Color.White else ZenvestTextPrimary
                        )
                        Text(
                            text = "LESS",
                            fontFamily = NetlessBrandFontFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                            color = Color(0xFF10B981)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Verify Offline Telephony Rail",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color.White else ZenvestTextPrimary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark) Color(0x3338BDF8) else Color(0x260284C7),
                        border = BorderStroke(0.5.dp, if (isDark) Color(0x4D38BDF8) else Color(0x330284C7))
                    ) {
                        Text(
                            text = "Step 2 of 2",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── NETWORK DETECTION CARD (JIO vs AIRTEL/GSM) ───
            ZenvestSetupCard(isDark = isDark) {
                if (isJioSim) {
                    // JIO 4G/5G VoLTE Detected Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (isDark) Color(0x3310B981) else Color(0x20059669), CircleShape)
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Jio Network Detected",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else ZenvestTextPrimary
                            )
                            Text(
                                text = "Pure 4G/5G VoLTE • 123PAY Call Optimized",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Jio operates 100% over VoLTE and does not support GSM *99# USSD. Your offline payments route exclusively and smoothly via the automated 123PAY Calling Service (08045163666).",
                        fontSize = 12.5.sp,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                        lineHeight = 17.5.sp
                    )
                } else {
                    // AIRTEL / GSM DUAL-RAIL DETECTED CARD
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (isDark) Color(0x3338BDF8) else Color(0x200284C7), CircleShape)
                                .border(1.dp, if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📶", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Airtel / GSM Carrier Detected",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else ZenvestTextPrimary
                            )
                            Text(
                                text = "Dual Rail: 123PAY Call + *99# USSD",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Your SIM supports both hands-free 123PAY automated call transfers and carrier *99# USSD banking menus.",
                        fontSize = 12.5.sp,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                        lineHeight = 17.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── TEST BUTTONS CONTAINER ───
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isJioSim) {
                    // For Jio SIM users:
                    // 1. UPI123 Calling Service Test Button (PRIMARY & REQUIRED FOR JIO)
                    ZenvestTestCard(
                        title = "123PAY Calling Service",
                        code = "08045163666",
                        description = "Automated offline IVR transfer rail. Dials & routes recipient and amount automatically.",
                        isCompleted = upi123TestCompleted,
                        isTesting = upi123Testing,
                        isDark = isDark,
                        onClick = {
                            if (!upi123TestCompleted && !upi123Testing) {
                                pendingDial = {
                                    testHelper.initiateUpi123Test()
                                }
                            }
                        }
                    )

                    // 2. Carrier *99# USSD Rail (LOCKED FOR JIO)
                    ZenvestTestCard(
                        title = "Carrier *99# USSD Rail",
                        code = "*99#",
                        description = "Jio operates 100% on VoLTE and does not support GSM *99# USSD. 123PAY calling service is your active rail.",
                        isCompleted = false,
                        isTesting = false,
                        isDark = isDark,
                        isLocked = true,
                        onClick = {}
                    )
                } else {
                    // For Airtel / GSM users:
                    // 1. CARRIER *99# USSD RAIL (PRIORITY #1 - FIRST CARD FOR AIRTEL)
                    val userReportedUssdIssue = SetupHelper.hasUserReportedUssdNotWorking(context)
                    ZenvestTestCard(
                        title = "Carrier *99# USSD Rail",
                        code = "*99#",
                        description = if (userReportedUssdIssue) {
                            stringResource(R.string.testcfg_ussd_reported_issue)
                        } else {
                            "Primary text-based GSM banking menu for Airtel. Fast, offline USSD rail."
                        },
                        isCompleted = ussdTestCompleted || userReportedUssdIssue,
                        isTesting = ussdTesting,
                        isDark = isDark,
                        onClick = {
                            if (!ussdTestCompleted && !ussdTesting) {
                                pendingDial = {
                                    testHelper.initiateCall(CallType.USSD)
                                }
                            }
                        }
                    )

                    // AIRTEL / GSM STEP-BY-STEP GUIDE ACCORDION
                    ZenvestSetupCard(isDark = isDark) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAirtelGuide = !showAirtelGuide },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📋", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Airtel *99# Step-by-Step Guide",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color.White else ZenvestTextPrimary
                                )
                            }
                            Icon(
                                imageVector = if (showAirtelGuide) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle Guide",
                                tint = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                            )
                        }

                        AnimatedVisibility(visible = showAirtelGuide) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AirtelGuideStep(step = "1", text = "Dial *99# on your Airtel phone keypad.", isDark = isDark)
                                AirtelGuideStep(step = "2", text = "Reply with '1' to select 'Send Money'.", isDark = isDark)
                                AirtelGuideStep(step = "3", text = "Choose 'Mobile No' or 'UPI ID' and enter payee details.", isDark = isDark)
                                AirtelGuideStep(step = "4", text = "Enter amount and type your secret UPI PIN directly on the screen.", isDark = isDark)
                            }
                        }
                    }

                    // 2. 123PAY CALLING SERVICE (SECONDARY RAIL)
                    ZenvestTestCard(
                        title = "123PAY Calling Service",
                        code = "08045163666",
                        description = "Automated offline IVR transfer rail. Dials & routes recipient and amount automatically.",
                        isCompleted = upi123TestCompleted,
                        isTesting = upi123Testing,
                        isDark = isDark,
                        onClick = {
                            if (!upi123TestCompleted && !upi123Testing) {
                                pendingDial = {
                                    testHelper.initiateUpi123Test()
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // ─── CONTINUE BUTTON (GATED BY TEST PASS) ───
            val canContinue = testHelper.canContinue()
            val allTestsCompleted = testHelper.allTestsCompleted()

            Button(
                onClick = { testHelper.continueToMain() },
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(
                        elevation = if (canContinue) 10.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = Color(0x6610B981)
                    ),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (allTestsCompleted) Color(0xFF10B981) else Color(0xFF0284C7),
                    disabledContainerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
                )
            ) {
                Text(
                    text = when {
                        allTestsCompleted -> "All Tests Passed! Enter Netless"
                        isJioSim && upi123TestCompleted -> "Enter Netless"
                        isJioSim && !upi123TestCompleted -> "Complete 123PAY Test Call to Continue"
                        !isJioSim && canContinue -> "Enter Netless"
                        !isJioSim && !ussdTestCompleted -> "Complete *99# USSD Test to Continue"
                        else -> "Complete Required Test to Continue"
                    },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canContinue) Color.White else if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ─── SKIP TEST LINK ───
            Text(
                text = stringResource(R.string.testcfg_skip_tests_link),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { testHelper.skipTests() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ─── USSD PROGRESS DIALOG ───
        UssdProgressDialog(
            isVisible = showUssdDialog || showUssdConfigurationOptions,
            progressMessage = ussdProgressMessage,
            showConfigurationOptions = showUssdConfigurationOptions,
            onConfigured = { testHelper.handleUssdConfigurationConfirmation(true) },
            onNotConfigured = { testHelper.handleUssdConfigurationConfirmation(false) },
            onDismiss = { testHelper.dismissUssdDialog(fromDoesNotWork = false) },
            onDoesNotWork = { testHelper.dismissUssdDialog(fromDoesNotWork = true) }
        )

        // ─── UPI123 PROGRESS DIALOG ───
        Upi123ProgressDialog(
            isVisible = showUpi123Dialog || showUpi123ConfigurationOptions,
            showConfigurationOptions = showUpi123ConfigurationOptions,
            onConfigured = { testHelper.handleUpi123ConfigurationConfirmation(true) },
            onNotConfigured = { testHelper.handleUpi123ConfigurationConfirmation(false) },
            onDismiss = { testHelper.dismissUpi123Dialog() }
        )

        // ─── CONSENT DIALOG (ZENVEST STYLED) ───
        pendingDial?.let { dial ->
            AlertDialog(
                onDismissRequest = { pendingDial = null },
                containerColor = if (isDark) ZenvestSurfaceDark else Color.White,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        text = "Carrier Test Call Notice",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (isDark) Color.White else ZenvestTextPrimary
                    )
                },
                text = {
                    Text(
                        text = "This will initiate a real test connection to your carrier's NPCI 123PAY rail to verify network readiness on your SIM.",
                        fontSize = 13.5.sp,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val action = dial
                            pendingDial = null
                            action()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Test Call", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDial = null }) {
                        Text("Cancel", color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun ZenvestTestCard(
    title: String,
    code: String,
    description: String,
    isCompleted: Boolean,
    isTesting: Boolean,
    isDark: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isLocked) {
                    if (isDark) Color(0xFF131A26) else Color(0xFFF1F5F9)
                } else if (isDark) {
                    ZenvestCardDark
                } else {
                    Color.White
                }
            )
            .border(
                width = 1.dp,
                color = when {
                    isCompleted -> Color(0xFF10B981).copy(alpha = 0.5f)
                    isLocked -> if (isDark) Color(0x33475569) else Color(0xFFE2E8F0)
                    isDark -> ZenvestCardBorderDark
                    else -> ZenvestPillBorderLight
                },
                shape = RoundedCornerShape(20.dp)
            )
            .then(
                if (isLocked) Modifier else Modifier.clickable { onClick() }
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = when {
                            isCompleted -> Color(0x2610B981)
                            isLocked -> if (isDark) Color(0x2664748B) else Color(0x1F64748B)
                            isDark -> Color(0x3338BDF8)
                            else -> Color(0x1F0284C7)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isLocked -> Icons.Default.Lock
                        code == "*99#" -> UssdIcon
                        else -> UpiIcon
                    },
                    contentDescription = null,
                    tint = when {
                        isCompleted -> Color(0xFF10B981)
                        isLocked -> if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        isDark -> Color(0xFF38BDF8)
                        else -> Color(0xFF0284C7)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isLocked -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            isDark -> Color.White
                            else -> ZenvestTextPrimary
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "($code)",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isCompleted -> Color(0xFF10B981)
                            isLocked -> if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                            else -> Color(0xFF0284C7)
                        },
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Status indicator
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLocked -> {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0x33475569) else Color(0xFFE2E8F0)
                        ) {
                            Text(
                                text = "LOCKED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    isTesting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color(0xFF0284C7),
                            strokeWidth = 2.5.dp
                        )
                    }
                    isCompleted -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFF10B981), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CheckIcon,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(
                                    1.5.dp,
                                    if (isDark) Color(0x4D475569) else Color(0x40CBD5E1),
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AirtelGuideStep(step: String, text: String, isDark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
            lineHeight = 16.sp
        )
    }
}

// Custom Icons
val UssdIcon: ImageVector
    get() = ImageVector.Builder(
        name = "ussd_phone",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(6.62f, 10.79f)
            curveTo(8.06f, 13.62f, 10.38f, 15.93f, 13.21f, 17.38f)
            lineTo(15.41f, 15.18f)
            curveTo(15.68f, 14.91f, 16.08f, 14.82f, 16.43f, 14.94f)
            curveTo(17.55f, 15.31f, 18.76f, 15.51f, 20f, 15.51f)
            curveTo(20.55f, 15.51f, 21f, 15.96f, 21f, 16.51f)
            lineTo(21f, 20f)
            curveTo(21f, 20.55f, 20.55f, 21f, 20f, 21f)
            curveTo(10.61f, 21f, 3f, 13.39f, 3f, 4f)
            curveTo(3f, 3.45f, 3.45f, 3f, 4f, 3f)
            lineTo(7.5f, 3f)
            curveTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f)
            curveTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f)
            curveTo(9.18f, 7.92f, 9.1f, 8.31f, 8.82f, 8.59f)
            lineTo(6.62f, 10.79f)
            close()
        }
    }.build()

val UpiIcon: ImageVector
    get() = ImageVector.Builder(
        name = "upi_payment",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
            moveTo(20f, 4f)
            lineTo(4f, 4f)
            curveTo(2.89f, 4f, 2.01f, 4.89f, 2.01f, 6f)
            lineTo(2f, 18f)
            curveTo(2f, 19.11f, 2.89f, 20f, 4f, 20f)
            lineTo(20f, 20f)
            curveTo(21.11f, 20f, 22f, 19.11f, 22f, 18f)
            lineTo(22f, 6f)
            curveTo(22f, 4.89f, 21.11f, 4f, 20f, 4f)
            close()
            moveTo(20f, 18f)
            lineTo(4f, 18f)
            lineTo(4f, 12f)
            lineTo(20f, 12f)
            lineTo(20f, 18f)
            close()
            moveTo(20f, 8f)
            lineTo(4f, 8f)
            lineTo(4f, 6f)
            lineTo(20f, 6f)
            lineTo(20f, 8f)
            close()
        }
    }.build()

val CheckIcon: ImageVector
    get() = ImageVector.Builder(
        name = "check",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 3f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round
        ) {
            moveTo(6f, 12f)
            lineTo(10f, 16f)
            lineTo(18f, 8f)
        }
    }.build()
