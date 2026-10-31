// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.netless.app.helpers.SetupHelper
import com.netless.app.payment.PaymentInputValidator
import com.netless.app.ui.components.AVATAR_TEMPLATES
import com.netless.app.ui.components.AvatarCircle
import com.netless.app.ui.components.NetlessLogo
import com.netless.app.ui.components.NetlessBrandWordmark
import com.netless.app.ui.theme.*

class SetupActivity : ComponentActivity() {
    private lateinit var setupHelper: SetupHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize setup helper
        setupHelper = SetupHelper(
            this,
            object : SetupHelper.UICallback {
                override fun showToast(message: String) {
                    runOnUiThread { Toast.makeText(this@SetupActivity, message, Toast.LENGTH_LONG).show() }
                }

                override fun navigateToTestConfiguration() {
                    val intent = Intent(this@SetupActivity, TestConfigurationActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        )

        setTheme(R.style.Theme_Netless)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            NetlessTheme {
                SetupScreen(setupHelper = setupHelper)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(setupHelper: SetupHelper) {
    val isDark = LocalNetlessDarkMode.current
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("NetlessPrefs", Context.MODE_PRIVATE) }

    // Form states initialized with existing preferences if available
    var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var userPhoneNumber by remember { mutableStateOf(prefs.getString("user_phone_number", "") ?: "") }
    var selectedAvatarId by remember { mutableIntStateOf(prefs.getInt("user_avatar_id", 0)) }
    val detectedCarrier = remember { SetupHelper.detectPrimaryCarrier(context) }
    val detectedDualSim = remember { SetupHelper.hasDualSim(context) }
    var selectedBank by remember { mutableStateOf(prefs.getString("selected_bank", "sbi") ?: "sbi") }
    var selectedPrimarySim by remember {
        val saved = prefs.getString("selected_primary_sim", "") ?: ""
        mutableStateOf(saved.ifEmpty { detectedCarrier.ifEmpty { "airtel" } })
    }
    var selectedSecondarySim by remember { mutableStateOf(prefs.getString("selected_secondary_sim", "") ?: "") }
    var isDualSimEnabled by remember {
        val saved = prefs.getBoolean("is_dual_sim_enabled", false)
        mutableStateOf(if (prefs.contains("is_dual_sim_enabled")) saved else detectedDualSim)
    }
    var disclaimerAccepted by remember { mutableStateOf(prefs.getBoolean("disclaimer_accepted", false)) }

    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val setupData = SetupHelper.SetupData(
            selectedBank = selectedBank,
            selectedPrimarySim = selectedPrimarySim,
            isDualSimEnabled = isDualSimEnabled,
            selectedSecondarySim = selectedSecondarySim,
            disclaimerAccepted = disclaimerAccepted,
            userName = userName.trim().ifEmpty { "User" },
            avatarId = selectedAvatarId,
            userPhoneNumber = userPhoneNumber.trim()
        )
        setupHelper.completeSetup(setupData)
    }

    val banks = setupHelper.getBanks()
    val simCarriers = setupHelper.getSimCarriers()
    val secondarySimOptions = setupHelper.getSecondarySimOptions(selectedPrimarySim)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = if (isDark) {
                    Brush.verticalGradient(
                        listOf(Color(0xFF0F172A), ZenvestBgDark)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(ZenvestSkyTop, ZenvestSkyMid, ZenvestBgLight)
                    )
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .align(Alignment.Center)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // ─── BRAND HEADER ───
            SetupHeaderCard(isDark = isDark)

            Spacer(modifier = Modifier.height(18.dp))

            // ─── 1. USER PROFILE & AVATAR ───
            ProfileSection(
                userName = userName,
                onNameChange = { userName = it },
                userPhoneNumber = userPhoneNumber,
                onPhoneNumberChange = { userPhoneNumber = it },
                selectedAvatarId = selectedAvatarId,
                onSelectAvatar = { selectedAvatarId = it },
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 2. BANK SELECTION ───
            BankSelectionSection(
                banks = banks,
                selectedBank = selectedBank,
                onBankSelected = { selectedBank = it },
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 3. SIM SELECTION ───
            SimCardSelectionSection(
                simCarriers = simCarriers,
                selectedPrimarySim = selectedPrimarySim,
                selectedSecondarySim = selectedSecondarySim,
                isDualSimEnabled = isDualSimEnabled,
                onPrimarySimSelected = { selectedPrimarySim = it },
                onSecondarySimSelected = { selectedSecondarySim = it },
                onDualSimToggled = { isDualSimEnabled = it },
                secondarySimOptions = secondarySimOptions,
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── 4. DISCLAIMER ───
            DisclaimerSection(
                isAccepted = disclaimerAccepted,
                onAcceptedChange = { disclaimerAccepted = it },
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Validation check
            val isFormComplete = userName.isNotBlank() &&
                selectedBank.isNotBlank() &&
                selectedPrimarySim.isNotBlank() &&
                (!isDualSimEnabled || selectedSecondarySim.isNotBlank()) &&
                disclaimerAccepted

            // ─── 5. COMPLETE SETUP BUTTON ───
            CompleteSetupButton(
                enabled = isFormComplete,
                onCompleteSetup = {
                    val allGranted = permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                    if (!allGranted) {
                        permissionsLauncher.launch(permissionsToRequest)
                    } else {
                        val setupData = SetupHelper.SetupData(
                            selectedBank = selectedBank,
                            selectedPrimarySim = selectedPrimarySim,
                            isDualSimEnabled = isDualSimEnabled,
                            selectedSecondarySim = selectedSecondarySim,
                            disclaimerAccepted = disclaimerAccepted,
                            userName = userName.trim().ifEmpty { "User" },
                            avatarId = selectedAvatarId,
                            userPhoneNumber = userPhoneNumber.trim()
                        )
                        setupHelper.completeSetup(setupData)
                    }
                },
                isDark = isDark
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SetupHeaderCard(isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NetlessLogo(size = 56.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "NET",
                fontFamily = NetlessBrandFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
            Text(
                text = "LESS",
                fontFamily = NetlessBrandFontFamily,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = Color(0xFF10B981)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("—", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Offline UPI Payments",
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xCCFFFFFF) else Color(0x99000000)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("—", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) Color(0x3338BDF8) else Color(0x260284C7),
            border = BorderStroke(0.5.dp, if (isDark) Color(0x4D38BDF8) else Color(0x330284C7))
        ) {
            Text(
                text = stringResource(R.string.setup_step_badge),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ZenvestSetupCard(
    isDark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 4.dp else 6.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = if (isDark) Color.Black.copy(0.4f) else Color(0x1F64748B),
                spotColor = if (isDark) Color(0x2638BDF8) else Color(0x1F0284C7)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) ZenvestCardDark else Color.White
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isDark) ZenvestCardBorderDark else Color(0x33CBD5E1)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun SetupSectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDark: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = if (isDark) Color(0x3338BDF8) else Color(0x1A0284C7),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
            )
        }
    }
}

@Composable
private fun SetupFieldLabel(text: String, isDark: Boolean) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun ProfileSection(
    userName: String,
    onNameChange: (String) -> Unit,
    userPhoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    selectedAvatarId: Int,
    onSelectAvatar: (Int) -> Unit,
    isDark: Boolean
) {
    ZenvestSetupCard(isDark = isDark) {
        SetupSectionHeader(
            icon = Icons.Default.Person,
            title = stringResource(R.string.profile_title),
            subtitle = stringResource(R.string.profile_avatar_title),
            isDark = isDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupFieldLabel(stringResource(R.string.profile_name_label), isDark)

        OutlinedTextField(
            value = userName,
            onValueChange = onNameChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.profile_name_hint),
                    color = if (isDark) Color(0x66FFFFFF) else Color(0x66000000)
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                unfocusedBorderColor = if (isDark) Color(0x3338BDF8) else Color(0x33CBD5E1),
                focusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                unfocusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary
            ),
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
        )

        Spacer(modifier = Modifier.height(14.dp))

        SetupFieldLabel("Mobile Number (Linked with Bank)", isDark)

        OutlinedTextField(
            value = userPhoneNumber,
            onValueChange = { input ->
                onPhoneNumberChange(PaymentInputValidator.sanitizePhoneNumber(input))
            },
            placeholder = {
                Text(
                    text = "10-digit mobile number",
                    color = if (isDark) Color(0x66FFFFFF) else Color(0x66000000)
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                unfocusedBorderColor = if (isDark) Color(0x3338BDF8) else Color(0x33CBD5E1),
                focusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                unfocusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary
            ),
            textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupFieldLabel(stringResource(R.string.profile_avatar_title), isDark)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AVATAR_TEMPLATES.forEach { avatar ->
                val isSelected = avatar.id == selectedAvatarId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) {
                                if (isDark) Color(0x3310B981) else Color(0x1F10B981)
                            } else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 0.dp,
                            color = if (isSelected) Color(0xFF10B981) else Color.Transparent,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelectAvatar(avatar.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    AvatarCircle(
                        avatarId = avatar.id,
                        size = 48.dp,
                        isSelected = isSelected
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = avatar.name,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) {
                            Color(0xFF10B981)
                        } else {
                            if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankSelectionSection(
    banks: List<Pair<String, String>>,
    selectedBank: String,
    onBankSelected: (String) -> Unit,
    isDark: Boolean
) {
    ZenvestSetupCard(isDark = isDark) {
        SetupSectionHeader(
            icon = Icons.Default.AccountBalance,
            title = stringResource(R.string.bank_selection),
            subtitle = stringResource(R.string.choose_primary_bank),
            isDark = isDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupFieldLabel(stringResource(R.string.setup_select_bank), isDark)

        var expanded by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }
        val filteredBanks = remember(searchQuery, banks) {
            if (searchQuery.isBlank()) banks
            else banks.filter {
                it.second.contains(searchQuery, ignoreCase = true) ||
                    it.first.contains(searchQuery, ignoreCase = true)
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = SetupHelper.getBankDisplayName(selectedBank).takeIf { it != "Select Bank" }
                    ?: stringResource(R.string.setup_choose_your_bank),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    unfocusedBorderColor = if (isDark) Color(0x3338BDF8) else Color(0x33CBD5E1),
                    focusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                    unfocusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                    focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                    unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                    focusedTrailingIconColor = if (isDark) Color.White else ZenvestTextPrimary,
                    unfocusedTrailingIconColor = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                ),
                shape = RoundedCornerShape(14.dp),
                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                    searchQuery = ""
                },
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .background(if (isDark) Color(0xFF1E293B) else Color.White)
            ) {
                // Search Input inside Dropdown for all Indian banks
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search Indian bank...",
                            fontSize = 13.sp,
                            color = if (isDark) Color(0x88FFFFFF) else Color(0x88000000)
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                        focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                        unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary
                    ),
                    textStyle = TextStyle(fontSize = 13.sp)
                )

                if (filteredBanks.isEmpty()) {
                    Text(
                        text = "No matching bank found",
                        fontSize = 13.sp,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    filteredBanks.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (isDark) Color.White else ZenvestTextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (value == selectedBank) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onBankSelected(value)
                                expanded = false
                                searchQuery = ""
                            },
                            modifier = Modifier.background(
                                if (value == selectedBank) {
                                    if (isDark) Color(0x2638BDF8) else Color(0x140284C7)
                                } else Color.Transparent
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Verified NPCI badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDark) Color(0x1A10B981) else Color(0x14059669),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🛡️", fontSize = 12.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.setup_linked_bank_note),
                fontSize = 11.sp,
                color = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimCardSelectionSection(
    simCarriers: List<Pair<String, String>>,
    selectedPrimarySim: String,
    selectedSecondarySim: String,
    isDualSimEnabled: Boolean,
    onPrimarySimSelected: (String) -> Unit,
    onSecondarySimSelected: (String) -> Unit,
    onDualSimToggled: (Boolean) -> Unit,
    secondarySimOptions: List<Pair<String, String>>,
    isDark: Boolean
) {
    ZenvestSetupCard(isDark = isDark) {
        SetupSectionHeader(
            icon = Icons.Default.SimCard,
            title = stringResource(R.string.sim_card_selection),
            subtitle = stringResource(R.string.configure_upi_payments),
            isDark = isDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        SetupFieldLabel(stringResource(R.string.setup_primary_sim), isDark)

        var primaryExpanded by remember { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = primaryExpanded,
            onExpandedChange = { primaryExpanded = !primaryExpanded }
        ) {
            OutlinedTextField(
                value = simCarriers.find { it.first == selectedPrimarySim }?.second
                    ?: stringResource(R.string.setup_select_primary_sim),
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = primaryExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    unfocusedBorderColor = if (isDark) Color(0x3338BDF8) else Color(0x33CBD5E1),
                    focusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                    unfocusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                    focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                    unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                    focusedTrailingIconColor = if (isDark) Color.White else ZenvestTextPrimary,
                    unfocusedTrailingIconColor = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                ),
                shape = RoundedCornerShape(14.dp),
                textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
            )

            ExposedDropdownMenu(
                expanded = primaryExpanded,
                onDismissRequest = { primaryExpanded = false },
                modifier = Modifier.background(if (isDark) Color(0xFF1E293B) else Color.White)
            ) {
                simCarriers.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = if (isDark) Color.White else ZenvestTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (value == selectedPrimarySim) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onPrimarySimSelected(value)
                            primaryExpanded = false
                        },
                        modifier = Modifier.background(
                            if (value == selectedPrimarySim) {
                                if (isDark) Color(0x2638BDF8) else Color(0x140284C7)
                            } else Color.Transparent
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dual SIM Checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDualSimToggled(!isDualSimEnabled) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val accentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 2.dp,
                        color = if (isDualSimEnabled) accentColor else if (isDark) Color(0x44FFFFFF) else Color(0x33000000),
                        shape = CircleShape
                    )
                    .background(
                        color = if (isDualSimEnabled) accentColor else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDualSimEnabled) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = stringResource(R.string.enable_dual_sim),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else ZenvestTextPrimary
            )
        }

        // Secondary SIM Section
        if (isDualSimEnabled) {
            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = if (isDark) ZenvestDividerDark else ZenvestDividerLight,
                thickness = 1.dp
            )

            Spacer(modifier = Modifier.height(14.dp))

            SetupFieldLabel(stringResource(R.string.secondary_sim), isDark)

            var secondaryExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = secondaryExpanded,
                onExpandedChange = { secondaryExpanded = !secondaryExpanded }
            ) {
                OutlinedTextField(
                    value = secondarySimOptions.find { it.first == selectedSecondarySim }?.second
                        ?: stringResource(R.string.setup_select_secondary_sim),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = secondaryExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                        unfocusedBorderColor = if (isDark) Color(0x3338BDF8) else Color(0x33CBD5E1),
                        focusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                        unfocusedContainerColor = if (isDark) Color(0xFF111827) else Color(0xFFF8FAFC),
                        focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                        unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                        focusedTrailingIconColor = if (isDark) Color.White else ZenvestTextPrimary,
                        unfocusedTrailingIconColor = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
                )

                ExposedDropdownMenu(
                    expanded = secondaryExpanded,
                    onDismissRequest = { secondaryExpanded = false },
                    modifier = Modifier.background(if (isDark) Color(0xFF1E293B) else Color.White)
                ) {
                    secondarySimOptions.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (isDark) Color.White else ZenvestTextPrimary,
                                    fontSize = 15.sp
                                )
                            },
                            onClick = {
                                onSecondarySimSelected(value)
                                secondaryExpanded = false
                            },
                            modifier = Modifier.background(
                                if (value == selectedSecondarySim) {
                                    if (isDark) Color(0x2638BDF8) else Color(0x140284C7)
                                } else Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DisclaimerSection(
    isAccepted: Boolean,
    onAcceptedChange: (Boolean) -> Unit,
    isDark: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val accentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    ZenvestSetupCard(isDark = isDark) {
        SetupSectionHeader(
            icon = Icons.Outlined.Info,
            title = stringResource(R.string.disclaimer),
            subtitle = stringResource(R.string.setup_please_read_before_continuing),
            isDark = isDark
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.Top
        ) {
            // Checkbox
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onAcceptedChange(!isAccepted) }
                    .border(
                        width = 2.dp,
                        color = if (isAccepted) accentColor else if (isDark) Color(0x44FFFFFF) else Color(0x33000000),
                        shape = CircleShape
                    )
                    .background(
                        color = if (isAccepted) accentColor else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isAccepted) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { isExpanded = !isExpanded }
            ) {
                Text(
                    text = stringResource(
                        if (isExpanded) {
                            R.string.disclaimer_text
                        } else {
                            R.string.disclaimer_summary
                        }
                    ),
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xDDFFFFFF) else Color(0xDD0F172A),
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (isExpanded) {
                            R.string.setup_show_less
                        } else {
                            R.string.setup_show_more
                        }
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
fun CompleteSetupButton(
    enabled: Boolean,
    onCompleteSetup: () -> Unit,
    isDark: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = RoundedCornerShape(27.dp),
                ambientColor = if (isDark) Color.Black.copy(0.5f) else Color(0x3364748B),
                spotColor = if (isDark) Color(0x4D38BDF8) else Color(0x330284C7)
            )
            .alpha(if (enabled) 1f else 0.45f),
        shape = RoundedCornerShape(27.dp),
        color = if (isDark) Color(0xFF38BDF8) else ZenvestTextPrimary,
        onClick = { if (enabled) onCompleteSetup() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.complete_setup),
                color = if (isDark) Color(0xFF0F172A) else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (isDark) Color(0xFF0F172A) else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
