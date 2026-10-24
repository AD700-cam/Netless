// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.netless.app.payment.PaymentInputValidator
import com.netless.app.ui.theme.*

@Composable
fun ProfileDialog(
    userName: String,
    avatarId: Int,
    userPhoneNumber: String = "",
    selectedBankName: String,
    onSaveProfile: (name: String, avatarId: Int, phoneNumber: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalNetlessDarkMode.current
    var isEditing by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(userName) }
    var editingPhone by remember { mutableStateOf(userPhoneNumber) }
    var selectedAvatarId by remember { mutableIntStateOf(avatarId) }

    val currentTemplate = getAvatarTemplate(if (isEditing) selectedAvatarId else avatarId)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = if (isDark) ZenvestSurfaceDark else Color.White,
            tonalElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isDark) ZenvestCardBorderDark else ZenvestPillBorderLight
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Title + Action/Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isEditing) {
                        IconButton(
                            onClick = {
                                isEditing = false
                                editingName = userName
                                editingPhone = userPhoneNumber
                                selectedAvatarId = avatarId
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isEditing) "Edit Profile" else "Your Profile",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else ZenvestTextPrimary
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Hero Avatar Preview
                AvatarCircle(
                    avatarId = if (isEditing) selectedAvatarId else avatarId,
                    size = 80.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Anime character label badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0x3338BDF8) else Color(0x1F0284C7)
                ) {
                    Text(
                        text = "${currentTemplate.emoji} ${currentTemplate.name} • ${currentTemplate.anime}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isEditing) {
                    // ─── VIEW PROFILE MODE ───
                    Text(
                        text = userName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else ZenvestTextPrimary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Payment Identity Details Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) ZenvestCardDark else Color(0xFFF1F5F9))
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = NetlessBrandEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Linked Bank: $selectedBankName",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White else ZenvestTextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = NetlessAccentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "100% Offline • Keystore Secured",
                                fontSize = 12.sp,
                                color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                            )
                        }

                        val maskedPhone = if (userPhoneNumber.length >= 4) {
                            "•••• " + userPhoneNumber.takeLast(4)
                        } else if (userPhoneNumber.isNotEmpty()) {
                            userPhoneNumber
                        } else {
                            "••••"
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App Account: $maskedPhone",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color.White.copy(0.9f) else ZenvestTextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Edit Profile Action Button
                    Button(
                        onClick = { isEditing = true },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color(0xFF38BDF8) else ZenvestTextPrimary,
                            contentColor = if (isDark) Color(0xFF0F172A) else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Edit Profile",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // ─── EDIT PROFILE MODE ───
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = { Text("Display Name", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isDark) NetlessAccentBlue else ZenvestTextPrimary,
                            unfocusedBorderColor = if (isDark) NetlessCardBorder else ZenvestPillBorderLight,
                            focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            focusedContainerColor = if (isDark) ZenvestCardDark else Color(0xFFF8FAFC),
                            unfocusedContainerColor = if (isDark) ZenvestCardDark else Color(0xFFF8FAFC)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editingPhone,
                        onValueChange = { input ->
                            editingPhone = PaymentInputValidator.sanitizePhoneNumber(input)
                        },
                        label = { Text("Mobile Number (App Account)", fontSize = 12.sp) },
                        placeholder = { Text("10-digit mobile number", fontSize = 12.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isDark) NetlessAccentBlue else ZenvestTextPrimary,
                            unfocusedBorderColor = if (isDark) NetlessCardBorder else ZenvestPillBorderLight,
                            focusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            unfocusedTextColor = if (isDark) Color.White else ZenvestTextPrimary,
                            focusedContainerColor = if (isDark) ZenvestCardDark else Color(0xFFF8FAFC),
                            unfocusedContainerColor = if (isDark) ZenvestCardDark else Color(0xFFF8FAFC)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Avatar Selection: Header "Choose Avatar"
                    Text(
                        text = "Choose Avatar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) ZenvestTextMuted else ZenvestTextSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Anime Avatar Row with Character Names
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(AVATAR_TEMPLATES) { template ->
                            val isSelected = selectedAvatarId == template.id
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedAvatarId = template.id }
                                    .padding(4.dp)
                            ) {
                                AvatarCircle(
                                    avatarId = template.id,
                                    size = 48.dp,
                                    isSelected = isSelected
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = template.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        if (isDark) Color(0xFF38BDF8) else ZenvestTextPrimary
                                    } else {
                                        if (isDark) ZenvestTextMuted else ZenvestTextSecondary
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Save Changes Button
                    Button(
                        onClick = {
                            val oldName = userName
                            val finalName = if (editingName.isNotBlank()) editingName.trim() else if (oldName.isNotBlank()) oldName else "User"
                            val finalPhone = editingPhone.trim()
                            onSaveProfile(finalName, selectedAvatarId, finalPhone)
                            onDismiss()

                            val message = if (oldName != finalName) {
                                "Your profile has updated from $oldName to $finalName"
                            } else {
                                "Profile updated: $finalName"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) NetlessBrandEmerald else ZenvestTextPrimary,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Save Changes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
