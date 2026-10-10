package com.semmyx.ledger.bank

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.semmyx.ledger.data.bank.BankImportRepository
import com.semmyx.ledger.domain.bank.Banks
import java.util.concurrent.TimeUnit

/**
 * Reads bank SMS that arrived while the app was closed (needs READ_SMS, sideload variant only).
 * Without the permission it does nothing. Messages already recorded are skipped by event key.
 */
object BankSmsCatchUp {
    private const val FIRST_RUN_DAYS = 30L
    private const val OVERLAP_MINUTES = 10L

    suspend fun run(context: Context, repository: BankImportRepository) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return
        val prefs = context.getSharedPreferences("bank_import", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = prefs.getLong("sms_scan_at", now - TimeUnit.DAYS.toMillis(FIRST_RUN_DAYS)) - TimeUnit.MINUTES.toMillis(OVERLAP_MINUTES)
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val sender = cursor.getString(0).orEmpty()
                val bank = Banks.bySender(sender) ?: continue
                val text = cursor.getString(1).orEmpty().take(8001)
                if (text.isBlank()) continue
                val sent = cursor.getLong(3)
                val at = if (sent > 0) sent else cursor.getLong(2)
                // Same event key as BankSmsReceiver so a live message and its catch-up copy are one record.
                repository.receive(bank, text, "SMS:$sender:$at:$text", at, "SMS")
            }
        }
        prefs.edit().putLong("sms_scan_at", now).apply()
    }
}
