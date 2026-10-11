package com.semmyx.ledger.bank

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.semmyx.ledger.LedgerApplication
import com.semmyx.ledger.domain.bank.Banks
import kotlinx.coroutines.*

/** SMS import mode: reads each new bank SMS as it arrives (historical inbox is read by [BankSmsCatchUp]). */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().originatingAddress.orEmpty()
        val bank = Banks.bySender(sender) ?: return
        val repository = (context.applicationContext as LedgerApplication).container.bankImport
        if (!repository.enabled || repository.importMode != "SMS") return
        val text = messages.joinToString("") { it.messageBody.orEmpty() }.take(8001)
        val at = messages.minOf { it.timestampMillis }
        val pending = goAsync()
        (context.applicationContext as LedgerApplication).applicationScope.launch(Dispatchers.IO) {
            try { withTimeout(7000) { repository.receive(bank, text, "SMS:$sender:$at:$text", at, "SMS") } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { android.util.Log.w("BankImport", "Local SMS import failed; no message contents logged") }
            finally { pending.finish() }
        }
    }
}
