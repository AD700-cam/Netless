// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

// =====================================
// 1. SettingsRepository.kt
// =====================================
package com.netless.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "netless_settings",
        Context.MODE_PRIVATE
    )
    private val netlessPrefs: SharedPreferences = context.getSharedPreferences(
        "NetlessPrefs",
        Context.MODE_PRIVATE
    )

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settingsFlow.value = loadSettings()
    }

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<SavedSettings> = _settingsFlow.asStateFlow()

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        netlessPrefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    data class SavedSettings(
        val bankId: String = "sbi",
        val ussdTimeout: Int = 30,
        val smsDetectionEnabled: Boolean = true,
        val overlayEnabled: Boolean = true,
        val debugMode: Boolean = false,
        val setupCompleted: Boolean = false,
        val testConfigCompleted: Boolean = false,
        val darkMode: Boolean = false,
        val soundEnabled: Boolean = true,
        val userName: String = "Abiya",
        val userAvatarId: Int = 0,
        val userPhoneNumber: String = ""
    )

    fun getSettings(): SavedSettings = settingsFlow.value

    fun reload() {
        _settingsFlow.value = loadSettings()
    }

    fun saveSettings(settings: SavedSettings) {
        prefs.edit {
            putString("bank_id", settings.bankId)
            putInt("ussd_timeout", settings.ussdTimeout)
            putBoolean("sms_detection_enabled", settings.smsDetectionEnabled)
            putBoolean("overlay_enabled", settings.overlayEnabled)
            putBoolean("debug_mode", settings.debugMode)
            putBoolean("setup_completed", settings.setupCompleted)
            putBoolean("test_config_completed", settings.testConfigCompleted)
            putBoolean("dark_mode", settings.darkMode)
            putBoolean("sound_enabled", settings.soundEnabled)
            putString("user_name", settings.userName)
            putInt("user_avatar_id", settings.userAvatarId)
            putString("user_phone_number", settings.userPhoneNumber)
        }
        netlessPrefs.edit {
            putString("selected_bank", settings.bankId)
            putString("user_name", settings.userName)
            putInt("user_avatar_id", settings.userAvatarId)
            putString("user_phone_number", settings.userPhoneNumber)
        }
        _settingsFlow.value = settings
    }

    private fun loadSettings(): SavedSettings {
        val bankId = if (netlessPrefs.contains("selected_bank")) {
            netlessPrefs.getString("selected_bank", "") ?: ""
        } else {
            prefs.getString("bank_id", "") ?: ""
        }.ifEmpty { "sbi" }
        val userName = if (netlessPrefs.contains("user_name")) {
            netlessPrefs.getString("user_name", "Abiya") ?: "Abiya"
        } else {
            prefs.getString("user_name", "Abiya") ?: "Abiya"
        }
        val avatarId = if (netlessPrefs.contains("user_avatar_id")) {
            netlessPrefs.getInt("user_avatar_id", 0)
        } else {
            prefs.getInt("user_avatar_id", 0)
        }
        val userPhoneNumber = if (netlessPrefs.contains("user_phone_number")) {
            netlessPrefs.getString("user_phone_number", "") ?: ""
        } else {
            prefs.getString("user_phone_number", "") ?: ""
        }
        return SavedSettings(
            bankId = bankId,
            ussdTimeout = prefs.getInt("ussd_timeout", 30),
            smsDetectionEnabled = prefs.getBoolean("sms_detection_enabled", true),
            overlayEnabled = prefs.getBoolean("overlay_enabled", true),
            debugMode = prefs.getBoolean("debug_mode", false),
            setupCompleted = prefs.getBoolean("setup_completed", false),
            testConfigCompleted = prefs.getBoolean("test_config_completed", false),
            darkMode = prefs.getBoolean("dark_mode", false),
            soundEnabled = prefs.getBoolean("sound_enabled", true),
            userName = userName,
            userAvatarId = avatarId,
            userPhoneNumber = userPhoneNumber
        )
    }

    fun clearAllData() {
        prefs.edit { clear() }
        netlessPrefs.edit { clear() }
        _settingsFlow.value = SavedSettings()
    }

    fun resetToDefaults() {
        saveSettings(SavedSettings())
    }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
