package com.semmyx.ledger.bank

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.semmyx.ledger.LedgerApplication
import kotlinx.coroutines.*

/**
 * Reads bank / wallet notifications in the background, with no Activity needed.
 *
 * Imports are written straight to the database by [com.semmyx.ledger.data.bank.BankImportRepository];
 * the same notification delivered twice (live + replay) is ignored by its event key, and one card tap
 * reported by two apps (e.g. Google Pay + PrivatBank) is collapsed by cross-source deduplication.
 *
 * Catch-up: every time the system (re)binds this service we replay the notifications that are still
 * in the shade, so anything that arrived while the service was dead, the phone rebooted or the app
 * was force-closed is still recorded.
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repository() = (application as LedgerApplication).container.bankImport

    override fun onListenerConnected() {
        try {
            repository().connected(true)
            val pending = try { activeNotifications } catch (_: Exception) { null }
            pending?.forEach { handle(it) }
        } catch (e: Exception) {
            android.util.Log.w("BankImport", "Catch-up failed: ${e.javaClass.simpleName}")
        }
    }

    override fun onListenerDisconnected() {
        runCatching { repository().connected(false) }
        // Ask for an immediate rebind instead of waiting for the OS back-off timer.
        runCatching { requestRebind(ComponentName(this, BankNotificationListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try { handle(sbn) } catch (e: Exception) {
            // A failure here must never crash the process: that would stop all background import.
            android.util.Log.w("BankImport", "Notification handling failed: ${e.javaClass.simpleName}")
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        val repository = repository()
        val bank = repository.bankForPackage(sbn.packageName)
        repository.note(
            if (bank == null) "UNSUPPORTED_APP" else "BANK_RECEIVED",
            sbn.packageName, notification = true, bankMessage = bank != null,
        )
        bank ?: return

        val notification = sbn.notification
        // Group summaries carry no transaction data.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) { repository.note("GROUP_SUMMARY"); return }

        val extras = notification.extras
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
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
        runCatching { repository().connected(false) }
        scope.cancel()
        super.onDestroy()
    }
}
