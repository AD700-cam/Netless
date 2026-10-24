// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.payment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure validation extracted from CallManager. These guard the two user
 * inputs — recipient number and amount — before any dial is attempted.
 */
class PaymentInputValidatorTest {

    @Test
    fun `valid 10-digit numbers are accepted`() {
        assertTrue(PaymentInputValidator.isValidPhoneNumber("9876543210"))
        assertTrue(PaymentInputValidator.isValidPhoneNumber("1234567890"))
    }

    @Test
    fun `phone numbers with a leading zero are rejected`() {
        assertFalse(PaymentInputValidator.isValidPhoneNumber("0123456789"))
    }

    @Test
    fun `phone numbers of the wrong length are rejected`() {
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765"))
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765432109"))
    }

    @Test
    fun `non-numeric, blank, and null phone numbers are rejected`() {
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765abcde"))
        assertFalse(PaymentInputValidator.isValidPhoneNumber("98765 4321"))
        assertFalse(PaymentInputValidator.isValidPhoneNumber(""))
        assertFalse(PaymentInputValidator.isValidPhoneNumber(null))
    }

    @Test
    fun `amounts within the range are accepted`() {
        assertTrue(PaymentInputValidator.isValidAmount("1"))
        assertTrue(PaymentInputValidator.isValidAmount("500"))
        assertTrue(PaymentInputValidator.isValidAmount("100000"))
        assertTrue(PaymentInputValidator.isValidAmount("99.99"))
    }

    @Test
    fun `amounts below the minimum or above the ceiling are rejected`() {
        assertFalse(PaymentInputValidator.isValidAmount("0"))
        assertFalse(PaymentInputValidator.isValidAmount("0.5"))
        assertFalse(PaymentInputValidator.isValidAmount("100001"))
    }

    @Test
    fun `non-numeric, blank, and null amounts are rejected`() {
        assertFalse(PaymentInputValidator.isValidAmount("abc"))
        assertFalse(PaymentInputValidator.isValidAmount(""))
        assertFalse(PaymentInputValidator.isValidAmount(null))
    }

    @Test
    fun `normalizeToWholeRupees normalizes whole rupee and zero-decimal amounts`() {
        org.junit.Assert.assertEquals("10", PaymentInputValidator.normalizeToWholeRupees("10"))
        org.junit.Assert.assertEquals("10", PaymentInputValidator.normalizeToWholeRupees("10.00"))
        org.junit.Assert.assertEquals("10", PaymentInputValidator.normalizeToWholeRupees("10.0"))
        org.junit.Assert.assertEquals("500", PaymentInputValidator.normalizeToWholeRupees(" 500.00 "))
        org.junit.Assert.assertEquals("10.50", PaymentInputValidator.normalizeToWholeRupees("10.50"))
        org.junit.Assert.assertEquals("", PaymentInputValidator.normalizeToWholeRupees(""))
        org.junit.Assert.assertEquals("", PaymentInputValidator.normalizeToWholeRupees(null))
    }

    @Test
    fun `isValidUpi123Amount validates amounts within UPI 123Pay limit`() {
        assertTrue(PaymentInputValidator.isValidUpi123Amount("1"))
        assertTrue(PaymentInputValidator.isValidUpi123Amount("10"))
        assertTrue(PaymentInputValidator.isValidUpi123Amount("10.00"))
        assertTrue(PaymentInputValidator.isValidUpi123Amount("4999"))
        assertTrue(PaymentInputValidator.isValidUpi123Amount("4999.00"))

        assertFalse(PaymentInputValidator.isValidUpi123Amount("0"))
        assertFalse(PaymentInputValidator.isValidUpi123Amount("-10"))
        assertFalse(PaymentInputValidator.isValidUpi123Amount("10.50"))
        assertFalse(PaymentInputValidator.isValidUpi123Amount("5000"))
        assertFalse(PaymentInputValidator.isValidUpi123Amount("10000"))
        assertFalse(PaymentInputValidator.isValidUpi123Amount("abc"))
        assertFalse(PaymentInputValidator.isValidUpi123Amount(null))
    }

    @Test
    fun `extract10DigitIndianPhone extracts valid 10-digit phone across formats`() {
        val v = PaymentInputValidator
        org.junit.Assert.assertEquals("9876543210", v.extract10DigitIndianPhone("+91 98765 43210"))
        org.junit.Assert.assertEquals("9876543210", v.extract10DigitIndianPhone("+919876543210"))
        org.junit.Assert.assertEquals("9876543210", v.extract10DigitIndianPhone("09876543210"))
        org.junit.Assert.assertEquals("9876543210", v.extract10DigitIndianPhone("919876543210"))
        org.junit.Assert.assertEquals("9876543210", v.extract10DigitIndianPhone("98765-43210"))
        org.junit.Assert.assertEquals("9876543210", v.extract10DigitIndianPhone("9876543210"))
        org.junit.Assert.assertNull(v.extract10DigitIndianPhone("12345"))
        org.junit.Assert.assertNull(
            v.extract10DigitIndianPhone("0112345678")
        )
        org.junit.Assert.assertNull(v.extract10DigitIndianPhone(null))
    }

    @Test
    fun `sanitizePhoneNumber handles progressive typing and paste cleanly`() {
        val v = PaymentInputValidator
        org.junit.Assert.assertEquals("9876543210", v.sanitizePhoneNumber("+91 98765 43210"))
        org.junit.Assert.assertEquals("9876543210", v.sanitizePhoneNumber("09876543210"))
        org.junit.Assert.assertEquals("9876", v.sanitizePhoneNumber("9876"))
        org.junit.Assert.assertEquals(
            "9876543210",
            v.sanitizePhoneNumber("9876543210123")
        )
        org.junit.Assert.assertEquals("", v.sanitizePhoneNumber(""))
        org.junit.Assert.assertEquals("", v.sanitizePhoneNumber(null))
    }
}
