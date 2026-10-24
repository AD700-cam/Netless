// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Netless

package com.netless.app.data

import android.content.Context
import kotlinx.coroutines.flow.firstOrNull

/**
 * Summary metrics of offline payments.
 */
data class OfflineAnalyticsSummary(
    val totalVolumeInr: Double,
    val totalCount: Int,
    val successCount: Int,
    val unverifiedCount: Int,
    val failedCount: Int,
    val ussdCount: Int,
    val ivrCount: Int,
    val averageAmountInr: Double
)

/**
 * 100% On-Device, Privacy-Preserving Analytics Manager.
 *
 * Computes financial metrics strictly locally from the encrypted Room database.
 * Never connects to the internet or sends telemetry to any cloud server.
 */
class LocalAnalyticsManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun getAnalyticsSummary(): OfflineAnalyticsSummary {
        val allTxns = db.transactionDao().getAllTransactions().firstOrNull() ?: emptyList()

        var totalVolume = 0.0
        var totalCount = 0
        var successCount = 0
        var unverifiedCount = 0
        var failedCount = 0
        var ussdCount = 0
        var ivrCount = 0

        for (txn in allTxns) {
            totalCount++
            val amount = txn.amount.toDoubleOrNull() ?: 0.0

            when (txn.status.uppercase()) {
                TransactionStatus.SUCCESS, "COMPLETED", "SUCCESSFUL" -> {
                    successCount++
                    totalVolume += amount
                }
                TransactionStatus.UNVERIFIED, TransactionStatus.PENDING -> {
                    unverifiedCount++
                }
                TransactionStatus.FAILED, TransactionStatus.CANCELLED -> {
                    failedCount++
                }
            }

            if (txn.source == TransactionSource.QR || txn.bankName.contains("USSD", ignoreCase = true)) {
                ussdCount++
            } else {
                ivrCount++
            }
        }

        val avgAmount = if (successCount > 0) totalVolume / successCount else 0.0

        return OfflineAnalyticsSummary(
            totalVolumeInr = totalVolume,
            totalCount = totalCount,
            successCount = successCount,
            unverifiedCount = unverifiedCount,
            failedCount = failedCount,
            ussdCount = ussdCount,
            ivrCount = ivrCount,
            averageAmountInr = avgAmount
        )
    }
}
