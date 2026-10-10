package com.semmyx.ledger.data.bank

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import com.semmyx.ledger.data.local.*
import com.semmyx.ledger.data.repository.toDomain
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Imports and acknowledgements are atomic with the existing ledger.
 * No HTTP, no raw text storage, no PAN storage.
 *
 * ## Cross-source deduplication
 *
 * A single physical card payment typically generates **two** push notifications:
 *   1. The wallet/payment app (Google Pay, Apple Pay) fires almost instantly.
 *   2. The issuing bank (PrivatBank, mono…) fires 30–120 s later.
 *
 * Both carry the same amount / currency / type.  Without cross-source dedup the
 * ledger would record UAH 100 twice.
 *
 * Strategy (two-tier, both run inside the same Room transaction):
 *   - Tier 1 [same-bank, ±90 s]: prevents re-delivery of the exact same push.
 *   - Tier 2 [cross-bank, ±180 s]: matches any inbox row with identical
 *     (amount, currency, type) regardless of which bank sent it.
 *     If a cross-source match exists the new row status = CROSS_DUPLICATE_REVIEW
 *     so the user can still inspect and accept/reject it.  If auto-import is on,
 *     cross-duplicates are silently ignored to avoid double-counting.
 */
class BankImportRepository(private val db: LedgerDatabase, context: Context) : BankRepository {

    private val ctx = context.applicationContext
    private val config = ctx.getSharedPreferences("bank_import", Context.MODE_PRIVATE)
    private val diagnosticState = MutableStateFlow(ImportDiagnostics())
    override val diagnostics = diagnosticState.asStateFlow()

    fun connected(value: Boolean) { diagnosticState.update { it.copy(connected = value) } }

    fun note(code: String, packageName: String = "", notification: Boolean = false, bankMessage: Boolean = false) {
        diagnosticState.update { old ->
            old.copy(
                lastCode = code,
                lastPackage = packageName.ifBlank { old.lastPackage },
                lastAtMillis = System.currentTimeMillis(),
                notificationCount = old.notificationCount + if (notification) 1 else 0,
                bankMessageCount = old.bankMessageCount + if (bankMessage) 1 else 0,
            )
        }
    }

    override fun configuredPackage(bankId: String): String =
        config.getString("package_$bankId", null)
            ?: Banks.all.firstOrNull { it.id == bankId }?.packages?.firstOrNull().orEmpty()

    override fun setPackage(bankId: String, packageName: String) {
        require(Banks.all.any { it.id == bankId })
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")))
        config.edit().putString("package_$bankId", packageName.trim()).apply()
    }

    fun bankForPackage(packageName: String): Bank? =
        Banks.all.firstOrNull { config.getString("package_${it.id}", null) == packageName }
            ?: Banks.byPackage(packageName)

    override suspend fun importText(bankId: String, text: String): Boolean {
        val bank = Banks.all.firstOrNull { it.id == bankId } ?: return false
        if (BankParser.parse(bank, text, System.currentTimeMillis()) == null) { note("UNRECOGNIZED"); return false }
        receive(bank, text, "MANUAL_TEXT:$bankId:${UUID.randomUUID()}", System.currentTimeMillis(), "MANUAL_TEXT", manual = true)
        return true
    }

    private val dao = db.bankDao()

    override fun categories() = dao.categories().map { rows -> rows.map { CustomBankCategory(it.id, it.name, it.colorHex) } }

    override fun reviews() = dao.inbox().map { rows ->
        rows.map {
            ImportReview(
                it.id, it.bankId, it.amountMinor, LedgerCurrency.valueOf(it.currency),
                EntryType.valueOf(it.type), it.merchant, it.cardLast4, it.eventAtMillis,
                it.timeFromMessage, it.transfer, it.status,
            )
        }
    }

    override fun sources(currency: LedgerCurrency, from: LocalDate, through: LocalDate) =
        dao.sources(currency.name, from.toEpochDay(), through.toEpochDay()).map { rows ->
            rows.map { BankSourceSpend(it.sourceType, it.bankId, it.totalMinor) }
        }

    override fun categorySpending(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?) =
        dao.detailCategories(currency.name, from.toEpochDay(), through.toEpochDay(), source, bank).map { rows ->
            rows.map { BankCategorySpend(Category.valueOf(it.category), it.customCategoryId, it.customName, it.customColor, it.totalMinor) }
        }

    override fun recent(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?) =
        dao.recent(currency.name, from.toEpochDay(), through.toEpochDay(), source, bank).map { rows -> rows.map { it.toDomain() } }

    override suspend fun exportEntries() = dao.exportEntries().map { it.toDomain() }
    override suspend fun clearHistory() { dao.clearInbox() }
    override fun budget(currency: LedgerCurrency): Long = config.getLong("budget_${currency.name}", 0L)
    override fun setBudget(currency: LedgerCurrency, minor: Long) { require(minor >= 0); config.edit().putLong("budget_${currency.name}", minor).apply() }

    override var enabled: Boolean
        get() = config.getBoolean("enabled", false)
        set(value) { config.edit().putBoolean("enabled", value).apply() }

    override var autoImport: Boolean
        get() = config.getBoolean("auto", false)
        set(value) { config.edit().putBoolean("auto", value).apply() }

    // -------------------------------------------------------------------------
    // Core receive path
    // -------------------------------------------------------------------------

    suspend fun receive(
        bank: Bank, text: String, eventKey: String, at: Long,
        origin: String, manual: Boolean = false,
    ) {
        if (!enabled && !manual) { note("DISABLED"); return }
        val parsed = BankParser.parse(bank, text, at) ?: run { note("UNRECOGNIZED"); return }

        var result = "QUEUED"
        db.withTransaction {
            // --- Tier 1: same-bank re-delivery guard ---
            val sameBankDup = dao.similar(
                parsed.bankId, parsed.amountMinor, parsed.currency.name, parsed.type.name,
                parsed.happenedAtMillis, parsed.cardLast4, parsed.merchant,
            ) > 0

            // --- Tier 2: cross-source dedup (wallet + bank for the same tap) ---
            // Only applies when the same-bank check didn't already flag it.
            val crossSourceDup = !sameBankDup && dao.similarCrossSource(
                parsed.amountMinor, parsed.currency.name, parsed.type.name, parsed.happenedAtMillis,
            ) > 0

            val status = when {
                sameBankDup   -> "DUPLICATE_REVIEW"
                crossSourceDup -> "CROSS_DUPLICATE_REVIEW"
                else          -> "REVIEW"
            }

            // groupKey: SHA-256(amount|currency|type|5-min bucket) — links twin events
            val bucket = parsed.happenedAtMillis / 300_000L   // 5-minute windows
            val groupKey = digest("${parsed.amountMinor}|${parsed.currency}|${parsed.type}|$bucket")

            val item = BankInboxEntity(
                id = UUID.randomUUID().toString(),
                eventKey = digest(eventKey),
                bankId = parsed.bankId,
                amountMinor = parsed.amountMinor,
                currency = parsed.currency.name,
                type = parsed.type.name,
                merchant = parsed.merchant,
                cardLast4 = parsed.cardLast4,
                eventAtMillis = parsed.happenedAtMillis,
                receivedAtMillis = at,
                timeFromMessage = parsed.timeFromMessage,
                transfer = parsed.transfer,
                category = parsed.category.name,
                origin = origin,
                status = status,
                groupKey = groupKey,
            )

            if (dao.insertInbox(item) == -1L) { result = "REDELIVERED"; return@withTransaction }
            result = status

            // Auto-import: only for confident single-source entries.
            // Cross-source duplicates are NEVER auto-imported — the user must review.
            if (!manual && autoImport && parsed.confident && !sameBankDup && !crossSourceDup) {
                acceptInside(item, parsed.type)
                result = "AUTO_IMPORTED"
                postImportNotification(parsed)
            }
        }
        note(result)
    }

    // -------------------------------------------------------------------------
    // Accept / ignore
    // -------------------------------------------------------------------------

    override suspend fun accept(id: String, type: EntryType) {
        db.withTransaction {
            val item = dao.inboxItem(id) ?: return@withTransaction
            if (item.status !in listOf("REVIEW", "DUPLICATE_REVIEW", "CROSS_DUPLICATE_REVIEW")) return@withTransaction
            acceptInside(item, type)
        }
    }

    private suspend fun acceptInside(item: BankInboxEntity, type: EntryType) {
        val id = "bank-${item.id}"
        val date = Instant.ofEpochMilli(item.eventAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val title = item.merchant.ifBlank { Banks.name(item.bankId) }.take(60)
        val entity = EntryEntity(
            id, title, item.amountMinor, item.currency, type.name, item.category,
            date.toEpochDay(),
            if (item.transfer) "Confirmed external transfer" else "",
            title.lowercase(Locale.ROOT),
            item.receivedAtMillis,
            sourceType = "CARD",
            bankId = item.bankId,
            cardLast4 = item.cardLast4,
            eventAtMillis = item.eventAtMillis,
            origin = item.origin,
        )
        db.ledgerDao().insert(entity)
        dao.status(item.id, "IMPORTED", id)
    }

    override suspend fun ignore(id: String) {
        db.withTransaction {
            val item = dao.inboxItem(id) ?: return@withTransaction
            if (item.status in listOf("REVIEW", "DUPLICATE_REVIEW", "CROSS_DUPLICATE_REVIEW"))
                dao.status(id, "IGNORED", null)
        }
    }

    override suspend fun createCategory(name: String, hex: String) {
        require(name.trim().length in 1..40)
        val color = ColorPreferences.normalizeHex(hex) ?: error("Invalid color")
        dao.addCategory(CustomCategoryEntity(UUID.randomUUID().toString(), name.trim(), color))
    }

    // -------------------------------------------------------------------------
    // Background notification
    // -------------------------------------------------------------------------

    /**
     * Posts a silent notification so the user knows something was auto-imported
     * while the app was in the background.  The channel is created lazily; on
     * Android < O the call is a no-op.
     */
    private fun postImportNotification(parsed: ParsedBankOperation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "ledger_import"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Ledger Import", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Background bank import events"
                    setShowBadge(false)
                },
            )
        }
        val amountText = Money.editable(parsed.amountMinor)
        val title = "${parsed.bankId.replaceFirstChar { it.uppercase() }}: ${parsed.merchant.ifBlank { parsed.bankId }}"
        val body = "${if (parsed.type == EntryType.EXPENSE) "−" else "+"} $amountText ${parsed.currency.name}"
        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Use a stable ID derived from the amount+time so rapid arrivals don't stack
        val notifId = (parsed.amountMinor xor parsed.happenedAtMillis).toInt()
        try { nm.notify(notifId, notification) } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    companion object {
        fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
