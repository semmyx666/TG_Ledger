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
        // Our own "imported" confirmations must never be re-read as bank messages.
        if (sbn.packageName == packageName) return
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

        val text = readText(notification)
        if (text.isBlank()) { repository.note("EMPTY_TEXT"); return }
        if (!repository.enabled) { repository.note("DISABLED"); return }

        // NonCancellable: a started import must finish even if the service is being torn down.
        scope.launch(NonCancellable) {
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

    /**
     * Banks put the shop into the title and the amount into the text, or use big/inbox styles.
     * Everything readable is joined, first line = title, duplicates removed.
     */
    private fun readText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = LinkedHashSet<String>()
        fun add(value: CharSequence?) { value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add) }
        add(extras.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach(::add)
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))
        if (parts.size < 2) add(notification.tickerText)
        // Remove parts that are entirely contained in a longer part (BIG_TEXT repeats TEXT).
        val list = parts.toList()
        return list.filter { part -> list.none { other -> other !== part && other.length > part.length && other.contains(part) } }
            .joinToString("\n").take(8001)
    }

    override fun onDestroy() {
        runCatching { repository().connected(false) }
        scope.cancel()
        super.onDestroy()
    }
}
