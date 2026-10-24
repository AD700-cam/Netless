// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

data class DiagnosticLogEntry(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

/**
 * Diagnostic in-memory event logger.
 *
 * Captures the last 100 system events (USSD prompts, telephony states, SMS detections)
 * strictly locally on device so users or technicians can troubleshoot carrier issues
 * without exposing sensitive user data to any network or cloud servers.
 */
object DiagnosticLogger {

    private const val MAX_LOGS = 100
    private val logDeque = ConcurrentLinkedDeque<DiagnosticLogEntry>()

    fun log(tag: String, message: String, level: String = "INFO") {
        val entry = DiagnosticLogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )
        logDeque.addFirst(entry)
        while (logDeque.size > MAX_LOGS) {
            logDeque.pollLast()
        }
    }

    fun d(tag: String, message: String) = log(tag, message, "DEBUG")
    fun i(tag: String, message: String) = log(tag, message, "INFO")
    fun w(tag: String, message: String) = log(tag, message, "WARN")
    fun e(tag: String, message: String) = log(tag, message, "ERROR")

    fun getLogs(): List<DiagnosticLogEntry> = logDeque.toList()

    fun clear() {
        logDeque.clear()
    }
}
