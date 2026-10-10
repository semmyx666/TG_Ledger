package com.semmyx.ledger.bank

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.semmyx.ledger.LedgerApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Keeps bank import alive without the app being open:
 *  - re-binds the notification listener if the system dropped it (its start-up replays pending notifications),
 *  - imports bank SMS received while the app was closed (sideload variant with READ_SMS).
 * Runs every 15 minutes, after reboot, and once whenever the app process starts.
 */
class BankImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repository = (applicationContext as LedgerApplication).container.bankImport
            if (!repository.enabled) return Result.success()
            if (applicationContext.packageName in NotificationManagerCompat.getEnabledListenerPackages(applicationContext)) {
                runCatching {
                    NotificationListenerService.requestRebind(ComponentName(applicationContext, BankNotificationListener::class.java))
                }
            }
            BankSmsCatchUp.run(applicationContext, repository)
            Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val work = WorkManager.getInstance(context)
            work.enqueueUniquePeriodicWork("bank-import-keepalive", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BankImportWorker>(15, TimeUnit.MINUTES).build())
            work.enqueueUniqueWork("bank-import-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<BankImportWorker>().build())
        }
    }
}
