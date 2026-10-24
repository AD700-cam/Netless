// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "NoMultipleSpaces", "SpacingAroundParens")

package com.netless.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.netless.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural Audio & Haptic confirmation engine.
 *
 * Synthesizes an authentic, pleasant, ascending 3-note harmonic payment success chime
 * (similar to the signature Google Pay / PhonePe / Paytm cash chime) using AudioTrack.
 *
 * Benefits:
 * - 100% offline — zero network access required
 * - Zero external assets / binary audio files
 * - Zero third-party audio dependencies
 * - Ultra-low latency playback
 */
object PaymentSoundManager {

    private const val TAG = "PaymentSoundManager"
    private const val SAMPLE_RATE = 44100

    /**
     * Pre-synthesized audio PCM buffer for the payment success chime.
     * Notes: C6 (1046.5 Hz) -> E6 (1318.5 Hz) -> G6 (1567.98 Hz) with harmonic overtones
     * and exponential decay envelope for a bright, bell-like, celebratory cash tone.
     */
    private val chimePcmBuffer: ByteArray by lazy {
        generateHarmonicChimePcm()
    }

    /**
     * Plays the payment success chime and triggers a crisp celebratory haptic pulse.
     * Respects user preference in Settings.
     */
    fun playSuccessSound(context: Context) {
        val appContext = context.applicationContext

        // Check if sound is enabled in Settings (default true)
        val isSoundEnabled = try {
            val settings = SettingsRepository(appContext).getSettings()
            settings.soundEnabled
        } catch (_: Exception) {
            true
        }

        // Haptic feedback (always crisp and subtle)
        playSuccessHaptics(appContext)

        if (!isSoundEnabled) {
            Log.d(TAG, "Payment sound is disabled in settings")
            return
        }

        // Play procedural PCM audio asynchronously
        CoroutineScope(Dispatchers.Default).launch {
            try {
                playPcmChime()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play payment success sound: ${e.message}")
            }
        }
    }

    /**
     * Triggers a double-pulse celebratory haptic vibration.
     */
    @Suppress("DEPRECATION")
    private fun playSuccessHaptics(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Short double tap: pulse 35ms, wait 60ms, pulse 55ms
                val timings = longArrayOf(0, 35, 60, 55)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                vibrator.vibrate(longArrayOf(0, 40, 60, 60), -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic pulse failed: ${e.message}")
        }
    }

    /**
     * Plays the pre-synthesized PCM buffer using an AudioTrack instance.
     */
    private fun playPcmChime() {
        val buffer = chimePcmBuffer
        val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                android.media.AudioManager.STREAM_NOTIFICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffer.size,
                AudioTrack.MODE_STATIC
            )
        }

        try {
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            // Wait for track playback to complete before releasing
            val durationMs = (buffer.size / 2 * 1000L) / SAMPLE_RATE
            Thread.sleep(durationMs + 100)
        } finally {
            try {
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Synthesizes 16-bit PCM audio for an ascending 3-note major chord:
     * Note 1: 1046.50 Hz (C6), starts at 0ms, duration 160ms
     * Note 2: 1318.51 Hz (E6), starts at 110ms, duration 160ms
     * Note 3: 1567.98 Hz (G6) + 2093.00 Hz (C7 sparkle), starts at 220ms, duration 400ms
     * Total length: ~650ms
     */
    private fun generateHarmonicChimePcm(): ByteArray {
        val totalDurationSeconds = 0.65
        val totalSamples = (SAMPLE_RATE * totalDurationSeconds).toInt()
        val shortBuffer = ShortArray(totalSamples)

        val notes = listOf(
            Triple(1046.50, 0.00, 0.22),  // C6
            Triple(1318.51, 0.11, 0.24),  // E6
            Triple(1567.98, 0.22, 0.42)   // G6
        )

        for (n in notes) {
            val freq = n.first
            val startSec = n.second
            val durSec = n.third
            val startIdx = (startSec * SAMPLE_RATE).toInt()
            val endIdx = minOf(totalSamples, ( (startSec + durSec) * SAMPLE_RATE).toInt() )

            for (i in startIdx until endIdx) {
                val t = (i - startIdx).toDouble() / SAMPLE_RATE
                // Exponential decay envelope
                val envelope = exp(-7.0 * (t / durSec))
                // Fundamental + rich octave overtone
                val sampleVal = sin(2.0 * PI * freq * t) * 0.75 +
                    sin(2.0 * PI * freq * 2.0 * t) * 0.25

                val amplitude = (sampleVal * envelope * 24000.0).toInt().coerceIn(-32767, 32767).toShort()
                val current = shortBuffer[i].toInt()
                val mixed = (current + amplitude).coerceIn(-32767, 32767).toShort()
                shortBuffer[i] = mixed
            }
        }

        // Convert 16-bit PCM shorts to little-endian byte array
        val byteBuffer = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val s = shortBuffer[i].toInt()
            byteBuffer[i * 2] = (s and 0xFF).toByte()
            byteBuffer[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }

        return byteBuffer
    }
}
