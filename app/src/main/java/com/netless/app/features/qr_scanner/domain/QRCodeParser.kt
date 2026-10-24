// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.features.qr_scanner.domain

import android.content.Context
import android.net.Uri
import android.util.Log
import com.netless.app.R
import com.netless.app.data.UPIData

/**
 * Strict UPI QR parser.
 *
 * Accepts exactly two shapes:
 *  1. A `upi://` URI (NPCI QR spec) with a structurally valid `pa` (VPA)
 *  2. A bare VPA string (some merchants print raw-VPA QRs)
 *
 * Everything else is [ParseResult.Invalid] with a reason. The old behaviour
 * — regex-fishing `pa=` out of arbitrary text and treating any string
 * containing "@" as a VPA — silently turned random QR codes into "payees"
 * and sent users into a doomed USSD flow.
 */
object QRCodeParser {

    private const val TAG = "QRCodeParser"

    // NPCI VPA shape: local part (letters/digits/._-), an @, and an
    // alphanumeric PSP handle starting with a letter.
    private val VPA_REGEX = Regex("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z][a-zA-Z0-9]{1,64}$")

    // Generic input ceiling for a QR-initiated payment.
    private const val MAX_QR_AMOUNT = 100_000.0

    /**
     * Why a scanned code was rejected.
     *
     * An enum, not a sentence: this parser is pure and unit-tested, and the
     * copy shown over the live camera belongs in `strings.xml` with the rest
     * of it. [messageFor] at the bottom of this file does the mapping at the
     * UI edge.
     */
    enum class Reason {
        EMPTY,
        NOT_A_UPI_QR,
        MALFORMED,
        NO_PAYEE_ADDRESS,
        INVALID_PAYEE_ADDRESS,
        INVALID_AMOUNT,
    }

    sealed class ParseResult {
        data class Valid(val data: UPIData) : ParseResult()
        data class Invalid(val reason: Reason) : ParseResult()
    }

    fun parse(qrCode: String): ParseResult {
        val raw = qrCode.trim()
        if (raw.isEmpty()) return ParseResult.Invalid(Reason.EMPTY)

        return when {
            raw.startsWith("upi://", ignoreCase = true) -> parseUpiUri(raw)
            VPA_REGEX.matches(raw) -> {
                val phone = extractPhoneNumber(raw)
                ParseResult.Valid(
                    UPIData(
                        vpa = raw,
                        payeeName = "",
                        amount = "",
                        transactionNote = "",
                        currency = "INR",
                        phoneNumber = phone
                    )
                )
            }
            else -> ParseResult.Invalid(Reason.NOT_A_UPI_QR)
        }
    }

    /**
     * Extracts an Indian 10-digit mobile number from a VPA or payee address if present.
     * Indian mobile numbers begin with digits 6, 7, 8, or 9 and contain exactly 10 digits.
     * Matches bare 10-digit numbers, numbers prefixed with +91/91/0, or numbers delimited
     * by dots/hyphens/underscores in merchant handles (e.g. bharatpe.9876543210@yesbank).
     */
    @Suppress("ReturnCount")
    fun extractPhoneNumber(vpa: String): String? {
        if (vpa.isBlank()) return null
        val localPart = vpa.substringBefore('@').trim()

        // 1. Direct 10-digit mobile number (e.g., 9876543210@paytm)
        val tenDigitRegex = Regex("^[6-9][0-9]{9}$")
        if (tenDigitRegex.matches(localPart)) {
            return localPart
        }

        // 2. Prefixed with +91, 91, or 0 (e.g., +919876543210@okhdfcbank, 919876543210@ybl, 09876543210@axl)
        val prefixedRegex = Regex("^(?:\\+?91|0)([6-9][0-9]{9})$")
        prefixedRegex.find(localPart)?.let {
            return it.groupValues[1]
        }

        // 3. Embedded in identifier with non-digit boundaries (e.g., bharatpe.9876543210, shop_9876543210)
        val embeddedRegex = Regex("(?:^|[^0-9])([6-9][0-9]{9})(?:$|[^0-9])")
        embeddedRegex.find(localPart)?.let {
            return it.groupValues[1]
        }

        return null
    }

    private fun getQueryParamCaseInsensitive(uri: Uri, paramName: String): String? {
        val direct = uri.getQueryParameter(paramName)
        val uppercase = uri.getQueryParameter(paramName.uppercase())
        val fallback = try {
            val matchingName = uri.queryParameterNames.firstOrNull { it.equals(paramName, ignoreCase = true) }
            matchingName?.let { uri.getQueryParameter(it) }
        } catch (_: Exception) {
            null
        }
        return direct.takeUnless { it.isNullOrBlank() }
            ?: uppercase.takeUnless { it.isNullOrBlank() }
            ?: fallback
    }

    private fun parseUpiUri(raw: String): ParseResult {
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            return ParseResult.Invalid(Reason.MALFORMED)
        }

        val vpa = try {
            getQueryParamCaseInsensitive(uri, "pa")?.trim().orEmpty()
        } catch (e: UnsupportedOperationException) {
            return ParseResult.Invalid(Reason.MALFORMED)
        }
        if (vpa.isEmpty()) return ParseResult.Invalid(Reason.NO_PAYEE_ADDRESS)
        if (!VPA_REGEX.matches(vpa)) {
            Log.w(TAG, "Rejected structurally invalid VPA in QR")
            return ParseResult.Invalid(Reason.INVALID_PAYEE_ADDRESS)
        }

        val amountParam = getQueryParamCaseInsensitive(uri, "am")?.trim().orEmpty()
        if (amountParam.isNotEmpty()) {
            val amount = amountParam.toDoubleOrNull()
            if (amount == null || amount <= 0 || amount > MAX_QR_AMOUNT) {
                return ParseResult.Invalid(Reason.INVALID_AMOUNT)
            }
            // At most two decimal places per the NPCI spec
            if (amountParam.matches(Regex("^[0-9]+(\\.[0-9]{1,2})?$")).not()) {
                return ParseResult.Invalid(Reason.INVALID_AMOUNT)
            }
        }

        val payeeName = getQueryParamCaseInsensitive(uri, "pn")
            ?.trim()
            ?.replace(Regex("[\\p{Cntrl}]"), "")
            ?.take(99)
            .orEmpty()
        val note = getQueryParamCaseInsensitive(uri, "tn")?.trim()?.take(99).orEmpty()

        val phoneParam = getQueryParamCaseInsensitive(uri, "phone")?.trim()
            ?: getQueryParamCaseInsensitive(uri, "mobile")?.trim()
        val extractedPhone = phoneParam?.let { extractPhoneNumber("$it@upi") } ?: extractPhoneNumber(vpa)

        return ParseResult.Valid(
            UPIData(
                vpa = vpa,
                payeeName = payeeName,
                amount = amountParam,
                transactionNote = note,
                currency = "INR",
                phoneNumber = extractedPhone
            )
        )
    }

    fun isValidUPIQRCode(qrCode: String): Boolean = parse(qrCode) is ParseResult.Valid

    /**
     * Legacy adapter for call sites built around "empty VPA means invalid".
     * Prefer [parse] — it carries the rejection reason.
     */
    fun parseUPIQRCode(qrCode: String): UPIData {
        return when (val result = parse(qrCode)) {
            is ParseResult.Valid -> result.data
            is ParseResult.Invalid -> {
                Log.w(TAG, "QR rejected: ${result.reason.name}")
                UPIData(vpa = "", payeeName = "", amount = "", transactionNote = "", currency = "INR")
            }
        }
    }
}

/**
 * The UI edge where a [QRCodeParser.Reason] becomes the sentence shown in the
 * scanner's inline banner ("Not a valid UPI payment QR — %1$s. Try another
 * code.").
 *
 * An extension rather than a method on the enum, so the parser's decision
 * logic stays free of resource lookups and keeps its plain-JVM tests.
 */
fun QRCodeParser.Reason.messageFor(context: Context): String = when (this) {
    QRCodeParser.Reason.EMPTY -> context.getString(R.string.qr_reason_empty)
    QRCodeParser.Reason.NOT_A_UPI_QR -> context.getString(R.string.qr_reason_not_upi)
    QRCodeParser.Reason.MALFORMED -> context.getString(R.string.qr_reason_malformed)
    QRCodeParser.Reason.NO_PAYEE_ADDRESS -> context.getString(R.string.qr_reason_no_payee)
    QRCodeParser.Reason.INVALID_PAYEE_ADDRESS -> context.getString(R.string.qr_reason_invalid_payee)
    QRCodeParser.Reason.INVALID_AMOUNT -> context.getString(R.string.qr_reason_invalid_amount)
}
