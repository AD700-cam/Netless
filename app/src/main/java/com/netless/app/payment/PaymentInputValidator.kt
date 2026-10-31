// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

@file:Suppress("TooManyFunctions", "ReturnCount")

package com.netless.app.payment

import com.netless.app.constants.AppConstants

/**
 * Pure validation of user-entered payment inputs — no Android dependencies,
 * so it is directly unit-testable. Extracted from CallManager, which needed
 * an Activity/Service Context and thus couldn't be exercised in a JVM test.
 * The dial-string construction that consumes these values is validated
 * separately in [Upi123CallStringBuilder].
 */
object PaymentInputValidator {

    private const val PHONE_DIGITS_LENGTH = 10
    private const val PREFIX_INDIA_LENGTH = 2
    private const val PREFIX_ZERO_LENGTH = 1
    private val INDIAN_MOBILE_REGEX = Regex("^[6-9][0-9]{9}$")
    private val NON_DIGITS_REGEX = Regex("[^0-9]")

    /** Exactly 10 digits, no leading zero (per [AppConstants.PHONE_NUMBER_PATTERN]). */
    fun isValidPhoneNumber(phoneNumber: String?): Boolean {
        if (phoneNumber.isNullOrBlank()) return false
        return phoneNumber.matches(Regex(AppConstants.PHONE_NUMBER_PATTERN))
    }

    /**
     * Extracts a clean 10-digit Indian mobile number from various formats:
     * e.g., "+91 98765 43210" -> "9876543210"
     * "919876543210" -> "9876543210"
     * "09876543210" -> "9876543210"
     * "98765-43210" -> "9876543210"
     * "9876543210" -> "9876543210"
     * Returns null if no valid 10-digit number starting with 6-9 is found.
     */
    @Suppress("CyclomaticComplexMethod")
    fun extract10DigitIndianPhone(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val digits = input.replace(NON_DIGITS_REGEX, "")
        val len = digits.length
        return when {
            len == PHONE_DIGITS_LENGTH && digits.matches(INDIAN_MOBILE_REGEX) -> digits
            len >= (PHONE_DIGITS_LENGTH + PREFIX_INDIA_LENGTH) &&
                digits.startsWith("91") &&
                digits.substring(PREFIX_INDIA_LENGTH, PREFIX_INDIA_LENGTH + PHONE_DIGITS_LENGTH)
                    .matches(INDIAN_MOBILE_REGEX) ->
                digits.substring(PREFIX_INDIA_LENGTH, PREFIX_INDIA_LENGTH + PHONE_DIGITS_LENGTH)
            len >= (PHONE_DIGITS_LENGTH + PREFIX_ZERO_LENGTH) &&
                digits.startsWith("0") &&
                digits.substring(PREFIX_ZERO_LENGTH, PREFIX_ZERO_LENGTH + PHONE_DIGITS_LENGTH)
                    .matches(INDIAN_MOBILE_REGEX) ->
                digits.substring(PREFIX_ZERO_LENGTH, PREFIX_ZERO_LENGTH + PHONE_DIGITS_LENGTH)
            len >= PHONE_DIGITS_LENGTH &&
                digits.take(PHONE_DIGITS_LENGTH).matches(INDIAN_MOBILE_REGEX) ->
                digits.take(PHONE_DIGITS_LENGTH)
            len > PHONE_DIGITS_LENGTH &&
                digits.takeLast(PHONE_DIGITS_LENGTH).matches(INDIAN_MOBILE_REGEX) ->
                digits.takeLast(PHONE_DIGITS_LENGTH)
            else -> null
        }
    }

    /**
     * Sanitizes raw phone input string for text fields.
     * If a valid 10-digit phone number is embedded or pasted (e.g. "+91 98765 43210"),
     * it extracts the 10 digits directly.
     * For manual progressive typing, strips non-digits and caps at 10 digits.
     */
    fun sanitizePhoneNumber(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim()
        val digits = trimmed.replace(NON_DIGITS_REGEX, "")
        return extract10DigitIndianPhone(trimmed) ?: when {
            digits.startsWith("91") && digits.length > PHONE_DIGITS_LENGTH ->
                digits.drop(PREFIX_INDIA_LENGTH).take(PHONE_DIGITS_LENGTH)
            digits.startsWith("0") && digits.length > PHONE_DIGITS_LENGTH ->
                digits.drop(PREFIX_ZERO_LENGTH).take(PHONE_DIGITS_LENGTH)
            else -> digits.take(PHONE_DIGITS_LENGTH)
        }
    }

    /** A positive amount within the generic input range (₹1 … ₹1,00,000). */
    fun isValidAmount(amount: String?): Boolean {
        val amountValue = amount?.toDoubleOrNull() ?: return false
        return amountValue >= AppConstants.MIN_AMOUNT_VALUE && amountValue <= AppConstants.MAX_AMOUNT_VALUE
    }

    private fun isWholeRupees(value: Double?): Boolean {
        val v = value ?: return false
        return v > 0 && v % 1.0 == 0.0 && v <= Long.MAX_VALUE
    }

    /**
     * Normalizes an amount string by stripping whitespace and removing
     * trailing zero decimals (e.g., "10.00" -> "10", "10.0" -> "10", "10" -> "10").
     * If the amount cannot be converted to a clean whole number without loss of precision,
     * returns trimmed input.
     */
    fun normalizeToWholeRupees(amount: String?): String {
        if (amount.isNullOrBlank()) return ""
        val trimmed = amount.trim()
        val d = trimmed.toDoubleOrNull()
        return if (isWholeRupees(d)) d!!.toLong().toString() else trimmed
    }

    /**
     * Validates whether an amount is valid for the UPI 123Pay IVR flow.
     * Must normalize to whole rupees between ₹1 and ₹4,999.
     */
    fun isValidUpi123Amount(amount: String?): Boolean {
        if (amount.isNullOrBlank()) return false
        val value = normalizeToWholeRupees(amount).toLongOrNull()
        val min = AppConstants.MIN_AMOUNT_VALUE.toLong()
        val max = AppConstants.UPI123PAY_MAX_AMOUNT.toLong()
        return value != null && value in min..max
    }

    /**
     * Validates whether a Virtual Payment Address (UPI ID / VPA) is well-formed.
     * Example: name@okhdfcbank, 9876543210@paytm
     */
    fun isValidVpa(vpa: String?): Boolean {
        if (vpa.isNullOrBlank()) return false
        val trimmed = vpa.trim()
        val vpaRegex = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")
        return trimmed.matches(vpaRegex)
    }

    /**
     * Real-time validation helper for Indian mobile phone numbers.
     * Returns a human-friendly error message, or null if valid.
     */
    fun getPhoneValidationError(phone: String?): String? {
        if (phone.isNullOrBlank()) return "Phone number cannot be empty"
        val clean = sanitizePhoneNumber(phone)
        return when {
            clean.length < PHONE_DIGITS_LENGTH -> "Enter 10-digit mobile number"
            !clean.matches(INDIAN_MOBILE_REGEX) -> "Number must start with 6, 7, 8, or 9"
            else -> null
        }
    }

    /**
     * Real-time validation helper for payment amounts.
     * Returns a human-friendly error message, or null if valid.
     */
    fun getAmountValidationError(amount: String?, isUpi123: Boolean = false): String? {
        if (amount.isNullOrBlank()) return "Enter payment amount"
        val d = amount.toDoubleOrNull() ?: return "Enter a valid numeric amount"
        if (d < AppConstants.MIN_AMOUNT_VALUE) return "Minimum amount is ₹1"
        if (isUpi123) {
            if (d > AppConstants.UPI123PAY_MAX_AMOUNT) return "UPI 123Pay limit is ₹5,000 per transaction"
            if (!isWholeRupees(d)) return "UPI 123Pay requires whole rupee amounts"
        } else {
            if (d > AppConstants.MAX_AMOUNT_VALUE) return "Maximum amount is ₹1,00,000"
        }
        return null
    }

    /**
     * Real-time validation helper for UPI IDs (VPAs).
     * Returns a human-friendly error message, or null if valid.
     */
    fun getVpaValidationError(vpa: String?): String? {
        if (vpa.isNullOrBlank()) return "UPI ID cannot be empty"
        if (!vpa.contains("@")) return "UPI ID must include '@' (e.g. name@bank)"
        return if (!isValidVpa(vpa)) "Enter a valid UPI ID" else null
    }
}
