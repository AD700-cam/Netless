// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.helpers

import android.content.Context
import com.netless.app.R
import com.netless.app.data.SettingsRepository

/**
 * Helper class containing all business logic for SetupActivity
 * This separates the UI concerns from the business logic
 */
class SetupHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val PREFS = "NetlessPrefs"
        private const val KEY_USER_REPORTED_USSD_NOT_WORKING = "user_reported_ussd_not_working"

        /**
         * Detects the primary SIM operator automatically via TelephonyManager / SubscriptionManager.
         * Returns "jio", "airtel", "vodafone", "bsnl", or empty string if undetectable.
         */
        fun detectPrimaryCarrier(context: Context): String {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                val simName = telephonyManager?.simOperatorName?.lowercase() ?: ""
                val networkName = telephonyManager?.networkOperatorName?.lowercase() ?: ""
                val simOp = telephonyManager?.simOperator ?: ""
                val combined = "$simName $networkName"

                if (combined.contains("jio") || (simOp.startsWith("405") && simOp.length >= 5)) {
                    return "jio"
                }
                if (combined.contains("airtel") || combined.contains("bharti")) {
                    return "airtel"
                }
                if (combined.contains("vodafone") || combined.contains("idea") || combined.contains("vi")) {
                    return "vodafone"
                }
                if (combined.contains("bsnl") || combined.contains("cellone")) {
                    return "bsnl"
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_PHONE_STATE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val activeSubscriptions = subManager?.activeSubscriptionInfoList
                        if (!activeSubscriptions.isNullOrEmpty()) {
                            val first = activeSubscriptions[0]
                            val subCarrier = (first.carrierName?.toString() ?: "").lowercase()
                            val subDisplay = (first.displayName?.toString() ?: "").lowercase()
                            val subCombined = "$subCarrier $subDisplay"
                            if (subCombined.contains("jio")) return "jio"
                            if (subCombined.contains("airtel") || subCombined.contains("bharti")) return "airtel"
                            if (subCombined.contains("vodafone") || subCombined.contains("idea") || subCombined.contains("vi")) return "vodafone"
                            if (subCombined.contains("bsnl")) return "bsnl"
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("SetupHelper", "Carrier auto-detection fallback: ${e.message}")
            }
            return ""
        }

        /**
         * Checks if device has dual active SIM cards inserted.
         */
        fun hasDualSim(context: Context): Boolean {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? android.telephony.SubscriptionManager
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_PHONE_STATE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        val count = subManager?.activeSubscriptionInfoCount ?: 0
                        return count > 1
                    }
                }
            } catch (_: Exception) {}
            return false
        }

        /** Jio (and similar) — primary SIM carrier does not support *99# for this flow. */
        fun isPrimarySimUssdCapable(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val primarySim = prefs.getString("selected_primary_sim", "") ?: ""
            if (primarySim.isNotBlank()) {
                return primarySim != "jio"
            }
            val detected = detectPrimaryCarrier(context)
            return detected != "jio"
        }

        /** User chose “It doesn't work for me” during *99# setup test. */
        fun hasUserReportedUssdNotWorking(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, false)
        }

        fun setUserReportedUssdNotWorking(context: Context, reported: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_USER_REPORTED_USSD_NOT_WORKING, reported)
                .apply()
        }

        internal fun clearUserReportedUssdNotWorking(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_USER_REPORTED_USSD_NOT_WORKING)
                .apply()
        }

        /** Scan-to-pay (USSD) is allowed: carrier supports *99# and user did not opt out in setup. */
        fun isScanToPayUssdAvailable(context: Context): Boolean {
            return isPrimarySimUssdCapable(context) && !hasUserReportedUssdNotWorking(context)
        }

        /**
         * Returns a user-facing block message if scan-to-pay cannot be used at all, or null if available.
         * Scan-to-pay is now available on all carriers: USSD-capable networks use *99#, while Jio and
         * non-USSD environments seamlessly route via the UPI 123Pay voice rail.
         */
        @Suppress("UnusedParameter", "FunctionOnlyReturningConstant")
        fun getScanToPayBlockedMessage(context: Context): String? = null

        /**
         * Comprehensive registry of supported Indian banks.
         */
        fun getBanksList(): List<Pair<String, String>> = listOf(
            // Major Public Sector Banks
            "sbi" to "State Bank of India (SBI)",
            "pnb" to "Punjab National Bank (PNB)",
            "bob" to "Bank of Baroda",
            "canara" to "Canara Bank",
            "union" to "Union Bank of India",
            "boi" to "Bank of India",
            "indian" to "Indian Bank",
            "central" to "Central Bank of India",
            "iob" to "Indian Overseas Bank",
            "uco" to "UCO Bank",
            "maharashtra" to "Bank of Maharashtra",
            "psb" to "Punjab & Sind Bank",

            // Major Private Sector Banks
            "hdfc" to "HDFC Bank",
            "icici" to "ICICI Bank",
            "axis" to "Axis Bank",
            "kotak" to "Kotak Mahindra Bank",
            "indusind" to "IndusInd Bank",
            "yes" to "Yes Bank",
            "federal" to "Federal Bank",
            "idbi" to "IDBI Bank",
            "idfc" to "IDFC FIRST Bank",
            "south_indian" to "South Indian Bank",
            "bandhan" to "Bandhan Bank",
            "rbl" to "RBL Bank",
            "karur_vysya" to "Karur Vysya Bank",
            "city_union" to "City Union Bank",
            "karnataka" to "Karnataka Bank",

            // Payments & Small Finance Banks
            "airtel_pb" to "Airtel Payments Bank",
            "ippb" to "India Post Payments Bank (IPPB)",
            "paytm_pb" to "Paytm Payments Bank",
            "fino" to "Fino Payments Bank",
            "au_sfb" to "AU Small Finance Bank",
            "equitas" to "Equitas Small Finance Bank",
            "ujjivan" to "Ujjivan Small Finance Bank"
        )

        /**
         * Resolves any bank ID, code, or custom name to a clean display name.
         * Guarantees that ANY user-selected bank persists cleanly and never falls back to PNB.
         */
        fun getBankDisplayName(bankIdOrName: String): String {
            if (bankIdOrName.isBlank()) return "Select Bank"
            val allBanks = getBanksList()
            val matchById = allBanks.find { it.first.equals(bankIdOrName, ignoreCase = true) }
            if (matchById != null) return matchById.second

            val matchByName = allBanks.find { it.second.equals(bankIdOrName, ignoreCase = true) }
            if (matchByName != null) return matchByName.second

            val partialMatch = allBanks.find {
                it.second.contains(bankIdOrName, ignoreCase = true) ||
                    bankIdOrName.contains(it.first, ignoreCase = true)
            }
            if (partialMatch != null) return partialMatch.second

            // New bank selected by user or custom bank: display as entered rather than defaulting to PNB!
            return bankIdOrName
        }
    }

    /**
     * Interface for UI callbacks
     */
    interface UICallback {
        fun showToast(message: String)
        fun navigateToTestConfiguration()
    }

    /**
     * Data class for validation results
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )

    /**
     * Data class for setup data
     */
    data class SetupData(
        val selectedBank: String,
        val selectedPrimarySim: String,
        val isDualSimEnabled: Boolean,
        val selectedSecondarySim: String,
        val disclaimerAccepted: Boolean,
        val userName: String = "Abiya",
        val avatarId: Int = 0,
        val userPhoneNumber: String = ""
    )

    /**
     * Get available banks
     */
    fun getBanks(): List<Pair<String, String>> = getBanksList()

    /**
     * Get available SIM carriers
     */
    fun getSimCarriers(): List<Pair<String, String>> {
        return listOf(
            "jio" to "Jio",
            "airtel" to "Airtel",
            "vodafone" to "Vodafone",
            "bsnl" to "BSNL"
        )
    }

    /**
     * Get secondary SIM options (excluding primary SIM)
     */
    fun getSecondarySimOptions(primarySim: String): List<Pair<String, String>> {
        val allCarriers = getSimCarriers()
        return if (primarySim.isNotEmpty()) {
            allCarriers.filter { it.first != primarySim }
        } else {
            allCarriers
        }
    }

    /**
     * Validate disclaimer acceptance
     */
    fun validateDisclaimer(disclaimerAccepted: Boolean): ValidationResult {
        if (!disclaimerAccepted) {
            return ValidationResult(false, "Please accept the disclaimer to proceed")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate bank selection
     */
    fun validateBankSelection(selectedBank: String): ValidationResult {
        if (selectedBank.isEmpty()) {
            return ValidationResult(false, "Please select your bank")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate primary SIM selection
     */
    fun validatePrimarySimSelection(selectedPrimarySim: String): ValidationResult {
        if (selectedPrimarySim.isEmpty()) {
            return ValidationResult(false, "Please select your primary SIM")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate secondary SIM selection (only if dual SIM is enabled)
     */
    fun validateSecondarySimSelection(
        isDualSimEnabled: Boolean,
        selectedSecondarySim: String
    ): ValidationResult {
        if (isDualSimEnabled && selectedSecondarySim.isEmpty()) {
            return ValidationResult(false, "Please select your secondary SIM")
        }
        return ValidationResult(true, "")
    }

    /**
     * Validate complete form
     */
    fun validateForm(setupData: SetupData): ValidationResult {
        // Validate disclaimer
        val disclaimerValidation = validateDisclaimer(setupData.disclaimerAccepted)
        if (!disclaimerValidation.isValid) {
            return disclaimerValidation
        }

        // Validate bank selection
        val bankValidation = validateBankSelection(setupData.selectedBank)
        if (!bankValidation.isValid) {
            return bankValidation
        }

        // Validate primary SIM selection
        val primarySimValidation = validatePrimarySimSelection(setupData.selectedPrimarySim)
        if (!primarySimValidation.isValid) {
            return primarySimValidation
        }

        // Validate secondary SIM selection
        val secondarySimValidation = validateSecondarySimSelection(
            setupData.isDualSimEnabled,
            setupData.selectedSecondarySim
        )
        if (!secondarySimValidation.isValid) {
            return secondarySimValidation
        }

        return ValidationResult(true, "")
    }

    /**
     * Save setup data to SharedPreferences
     */
    fun saveSetupData(setupData: SetupData) {
        val sharedPreferences = context.getSharedPreferences("NetlessPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putBoolean("setup_completed", true)
            .putString("selected_bank", setupData.selectedBank)
            .putString("selected_primary_sim", setupData.selectedPrimarySim)
            .putBoolean("is_dual_sim_enabled", setupData.isDualSimEnabled)
            .putString("selected_secondary_sim", setupData.selectedSecondarySim)
            .putBoolean("disclaimer_accepted", setupData.disclaimerAccepted)
            .putString("user_name", setupData.userName)
            .putInt("user_avatar_id", setupData.avatarId)
            .putString("user_phone_number", setupData.userPhoneNumber)
            .apply()

        try {
            val app = context.applicationContext as? com.netless.app.NetlessApplication
            val repo = app?.settingsRepository ?: SettingsRepository(context.applicationContext)
            val current = repo.settingsFlow.value
            repo.saveSettings(
                current.copy(
                    bankId = setupData.selectedBank,
                    setupCompleted = true,
                    userName = setupData.userName,
                    userAvatarId = setupData.avatarId,
                    userPhoneNumber = setupData.userPhoneNumber
                )
            )
        } catch (_: Exception) {}
    }

    /**
     * Complete setup process
     */
    fun completeSetup(setupData: SetupData) {
        val validationResult = validateForm(setupData)

        if (validationResult.isValid) {
            // Save setup data
            saveSetupData(setupData)

            // Show success message
            uiCallback.showToast(context.getString(R.string.status_setup_complete))

            // Navigate to test configuration
            uiCallback.navigateToTestConfiguration()
        } else {
            // Show validation error
            uiCallback.showToast(validationResult.errorMessage)
        }
    }

    /**
     * Check if setup is already completed
     */
    fun isSetupCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences("NetlessPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("setup_completed", false)
    }
}
