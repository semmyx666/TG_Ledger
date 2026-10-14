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
 *  - API mode: pulls new operations from monobank; SMS mode: imports bank SMS received while the app was closed.
 * Runs every 15 minutes, after reboot, and once whenever the app process starts.
 */
class BankImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repository = (applicationContext as LedgerApplication).container.bankImport
            if (!repository.enabled) return Result.success()
            when (repository.importMode) {
                "API" -> { repository.syncMonobank(); repository.syncPrivat() }
                "SMS" -> BankSmsCatchUp.run(applicationContext, repository)
                else -> if (applicationContext.packageName in NotificationManagerCompat.getEnabledListenerPackages(applicationContext)) {
                    ensureListenerBound(applicationContext, repository)
                }
            }
            Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.retry()
        }
    }

    companion object {
        /**
         * The system sometimes leaves a notification listener unbound after an app update, a crash or aggressive
         * battery management. First ask politely; if it is still not connected, toggle the component, which makes
         * Android bind it again (and it then replays the notifications still in the shade).
         */
        suspend fun ensureListenerBound(context: Context, repository: com.semmyx.ledger.data.bank.BankImportRepository) {
            if (repository.diagnostics.value.connected && BankNotificationListener.replayIfConnected()) return
            val component = ComponentName(context, BankNotificationListener::class.java)
            runCatching { NotificationListenerService.requestRebind(component) }
            kotlinx.coroutines.delay(3_000)
            if (repository.diagnostics.value.connected) return
            val pm = context.packageManager
            runCatching {
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            }
        }

        /** Runs one import pass right now (after switching mode, connecting, or pressing "Sync now"). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("bank-import-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BankImportWorker>().build())
        }

        fun schedule(context: Context) {
            val work = WorkManager.getInstance(context)
            work.enqueueUniquePeriodicWork("bank-import-keepalive", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BankImportWorker>(15, TimeUnit.MINUTES).build())
            work.enqueueUniqueWork("bank-import-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<BankImportWorker>().build())
        }
    }
}
