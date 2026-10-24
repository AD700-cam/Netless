// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.utils

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, thread-safe rate limiter and debouncer.
 *
 * Prevents:
 * - Duplicate button clicks / rapid double-taps
 * - USSD invocation storms (*99# spam)
 * - Rapid re-dial attempts within telephony cooldown
 */
object RateLimiter {

    private val lastActionTimestamps = ConcurrentHashMap<String, Long>()

    const val DEFAULT_DEBOUNCE_MS = 1500L
    const val TELEPHONY_DEBOUNCE_MS = 3000L

    /**
     * Checks if an action with [key] can be executed based on [cooldownMs].
     * If permitted, updates the timestamp and returns true; otherwise false.
     */
    fun canPerform(key: String, cooldownMs: Long = DEFAULT_DEBOUNCE_MS): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastActionTimestamps[key] ?: 0L
        if (now - last >= cooldownMs) {
            lastActionTimestamps[key] = now
            return true
        }
        return false
    }

    /**
     * Executes [action] only if the cooldown has elapsed for [key].
     */
    inline fun debounce(key: String, cooldownMs: Long = DEFAULT_DEBOUNCE_MS, action: () -> Unit) {
        if (canPerform(key, cooldownMs)) {
            action()
        }
    }

    /**
     * Clears all recorded timestamps.
     */
    fun reset() {
        lastActionTimestamps.clear()
    }
}
