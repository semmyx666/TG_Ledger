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
 * process was killed is still recorded. Force-stop requires opening the app again; dismissed
 * notifications are not recoverable through this Android API.
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repository() = (application as LedgerApplication).container.bankImport

    override fun onListenerConnected() {
        current = java.lang.ref.WeakReference(this)
        try {
            repository().connected(true)
            replayActive()
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
        // Our own "imported" confirmations must never be re-read as bank messages.
        if (sbn.packageName == packageName) return
        val repository = repository()
        if (repository.importMode != "NOTIFY") return
        val bank = repository.bankForPackage(sbn.packageName)
        repository.note(
            if (bank == null) "UNSUPPORTED_APP" else "BANK_RECEIVED",
            sbn.packageName, notification = true, bankMessage = bank != null,
        )
        bank ?: return

        val notification = sbn.notification
        // Group summaries carry no transaction data.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) { repository.note("GROUP_SUMMARY"); return }

        val messages = BankNotificationDecoder.messages(notification, bank, sbn.postTime)
        if (messages.isEmpty()) { repository.note("EMPTY_TEXT"); return }
        if (!repository.enabled) { repository.note("DISABLED"); return }
        scope.launch {
            // Finish a started database import if Android tears down the listener mid-write.
            withContext(NonCancellable) {
                messages.forEach { message ->
                    try {
                        repository.receive(bank, message.text,
                            eventKey = "PUSH:${sbn.packageName}:${sbn.key}:${message.at}:${message.text}",
                            at = message.at, origin = "PUSH")
                    } catch (_: Exception) {
                        repository.note("ERROR")
                        android.util.Log.w("BankImport", "Background import failed; no message contents logged")
                    }
                }
            }
        }
    }

    private fun replayActive() {
        runCatching { activeNotifications?.sortedBy { it.postTime }?.forEach(::handle) }
            .onFailure { repository().note("ERROR") }
    }

    companion object {
        @Volatile private var current = java.lang.ref.WeakReference<BankNotificationListener>(null)
        /** Also used when switching into push mode while the listener is already bound. */
        fun replayIfConnected(): Boolean {
            val listener = current.get() ?: return false
            listener.replayActive()
            return true
        }
    }

    override fun onDestroy() {
        if (current.get() === this) current.clear()
        runCatching { repository().connected(false) }
        scope.cancel()
        super.onDestroy()
    }
}
