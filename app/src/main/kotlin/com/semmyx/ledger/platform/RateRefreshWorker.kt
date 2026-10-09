package com.semmyx.ledger.platform

import android.content.Context
import androidx.work.*
import com.semmyx.ledger.LedgerApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Only public rates are refreshed in the background; personal entries never leave Room. */
class RateRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LedgerApplication).container
        return try {
            container.entries.purgeDeletedBefore(container.clock.millis() - TimeUnit.DAYS.toMillis(1))
            container.rates.refresh()
            Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (runAttemptCount >= 4) Result.failure() else Result.retry()
        }
    }
    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
            val periodic = PeriodicWorkRequestBuilder<RateRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("reference-rates", ExistingPeriodicWorkPolicy.UPDATE, periodic)
        }
    }
}
