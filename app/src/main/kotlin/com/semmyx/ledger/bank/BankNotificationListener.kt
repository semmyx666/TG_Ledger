package com.semmyx.ledger.bank

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.semmyx.ledger.LedgerApplication
import com.semmyx.ledger.domain.bank.Banks
import kotlinx.coroutines.*

/**
 * Listens to posted notifications from registered bank/wallet apps and routes them
 * to [com.semmyx.ledger.data.bank.BankImportRepository].
 *
 * Works entirely in the background: the service is bound by the OS (no Activity
 * needed) and uses the application's long-lived [LedgerApplication.applicationScope].
 * Cross-source deduplication happens inside the repository transaction, so even if
 * Google Pay fires first and PrivatBank fires 60 s later for the same tap, only one
 * entry appears in the ledger.
 */
class BankNotificationListener : NotificationListenerService() {

    /**
     * Own supervisor scope for fire-and-forget import coroutines.
     * Cancelled in onDestroy so pending work is cleaned up if the OS kills the service.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureImportChannel()
    }

    override fun onListenerConnected() {
        (application as LedgerApplication).container.bankImport.connected(true)
    }

    override fun onListenerDisconnected() {
        (application as LedgerApplication).container.bankImport.connected(false)
        // Request an immediate rebind rather than waiting for the OS back-off timer.
        requestRebind(android.content.ComponentName(this, BankNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val repository = (application as LedgerApplication).container.bankImport
        val bank = repository.bankForPackage(sbn.packageName)

        repository.note(
            if (bank == null) "UNSUPPORTED_APP" else "BANK_RECEIVED",
            sbn.packageName,
            notification = true,
            bankMessage = bank != null,
        )
        bank ?: return

        val notification = sbn.notification
        // Group summary notifications carry no useful transaction data; skip them.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            repository.note("GROUP_SUMMARY")
            return
        }

        val extras = notification.extras
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = "$title\n$body".take(8001)

        if (body.isBlank()) { repository.note("EMPTY_TEXT"); return }
        if (!repository.enabled) { repository.note("DISABLED"); return }

        scope.launch {
            try {
                repository.receive(
                    bank, text,
                    eventKey = "PUSH:${sbn.packageName}:${sbn.key}:${sbn.postTime}:$text",
                    at = sbn.postTime,
                    origin = "PUSH",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                repository.note("ERROR")
                android.util.Log.w("BankImport", "Background import failed; no message contents logged")
            }
        }
    }

    override fun onDestroy() {
        (application as LedgerApplication).container.bankImport.connected(false)
        scope.cancel()
        super.onDestroy()
    }

    // -----------------------------------------------------------------------
    // Notification channel for background import confirmations
    // -----------------------------------------------------------------------

    private fun ensureImportChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = IMPORT_CHANNEL_ID
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Ledger Import", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Silent confirmation when a transaction is auto-imported in the background"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                },
            )
        }
    }

    companion object {
        const val IMPORT_CHANNEL_ID = "ledger_import"
    }
}
