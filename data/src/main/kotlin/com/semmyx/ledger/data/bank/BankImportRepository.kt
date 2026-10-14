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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Imports and acknowledgements are atomic with the existing ledger.
 * Push parsing is offline. API clients only read statements; no raw text or PAN storage.
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
class BankImportRepository(
    private val db: LedgerDatabase, context: Context,
    private val vault: com.semmyx.ledger.data.security.KeystoreVault? = null,
    private val monobank: MonobankClient = MonobankClient(),
    private val privat: PrivatBankClient = PrivatBankClient(),
) : BankRepository {

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
        get() = config.getBoolean("enabled", true)
        set(value) { config.edit().putBoolean("enabled", value).apply() }

    override var autoImport: Boolean
        get() = config.getBoolean("auto", true)
        set(value) { config.edit().putBoolean("auto", value).apply() }

    override var importMode: String
        get() = config.getString("mode", "NOTIFY") ?: "NOTIFY"
        set(value) { require(value in listOf("API", "SMS", "NOTIFY")); config.edit().putString("mode", value).apply() }

    // -------------------------------------------------------------------------
    // monobank personal API (token mode)
    // -------------------------------------------------------------------------

    private val monoState = MutableStateFlow(MonobankStatus(
        connected = config.getString("mono_accounts", null) != null,
        lastSyncMillis = config.getLong("mono_last_sync", 0L),
    ))
    override val monobankStatus = monoState.asStateFlow()
    private val syncLock = kotlinx.coroutines.sync.Mutex()

    private fun savedAccounts(): List<MonobankClient.Account> =
        config.getString("mono_accounts", null)?.split(';')?.mapNotNull { row ->
            val f = row.split('|')
            if (f.size < 4) null else MonobankClient.Account(f[0], runCatching { LedgerCurrency.valueOf(f[1]) }.getOrNull() ?: return@mapNotNull null, f[2], f[3].ifBlank { null })
        }.orEmpty()

    private fun saveAccounts(accounts: List<MonobankClient.Account>) {
        config.edit().putString("mono_accounts", accounts.joinToString(";") { "${it.id}|${it.currency.name}|${it.type}|${it.cardLast4.orEmpty()}" }).apply()
    }

    /** Validates the token with monobank, stores it encrypted (Android Keystore) and remembers the accounts. */
    override suspend fun connectMonobank(token: String): Boolean = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.length !in 20..200 || !clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            monoState.update { it.copy(error = "BAD_TOKEN") }; return@withContext false
        }
        try {
            val accounts = monobank.accounts(clean)
            if (accounts.isEmpty()) { monoState.update { it.copy(error = "NO_ACCOUNTS") }; return@withContext false }
            val store = vault ?: error("Secure storage unavailable")
            store.update { it.put("monoToken", clean); Unit }
            saveAccounts(accounts)
            config.edit().remove("mono_last_sync").putString("mode", "API").apply()
            monoState.value = MonobankStatus(connected = true, accounts = accounts.size)
            true
        } catch (e: MonobankClient.ApiException) {
            monoState.update { it.copy(error = if (e.invalidToken) "BAD_TOKEN" else if (e.rateLimited) "RATE_LIMIT" else "NETWORK") }; false
        } catch (e: java.io.IOException) {
            monoState.update { it.copy(error = "NETWORK") }; false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            monoState.update { it.copy(error = "NETWORK") }; false
        }
    }

    override suspend fun disconnectMonobank() = withContext(Dispatchers.IO) {
        runCatching { vault?.update { it.remove("monoToken"); Unit } }
        config.edit().remove("mono_accounts").remove("mono_last_sync").apply()
        monoState.value = MonobankStatus()
    }

    /**
     * Pulls new operations for every saved account. monobank allows one statement request per account
     * per 60 s, so accounts are fetched one minute apart. Safe to call repeatedly: operations are keyed by id.
     */
    override suspend fun syncMonobank(): Int = withContext(Dispatchers.IO) {
        if (!syncLock.tryLock()) return@withContext 0
        try {
            val token = vault?.read { it.optString("monoToken", "") }.orEmpty()
            val accounts = savedAccounts()
            if (token.isBlank() || accounts.isEmpty()) { monoState.update { it.copy(connected = false) }; return@withContext 0 }
            monoState.update { it.copy(syncing = true, error = null) }
            val nowSec = System.currentTimeMillis() / 1000
            val last = config.getLong("mono_last_sync", 0L) / 1000
            val from = if (last > 0) maxOf(last - 3600, nowSec - 31L * 86400) else nowSec - 30L * 86400
            var imported = 0
            accounts.take(4).forEachIndexed { index, account ->
                if (index > 0) kotlinx.coroutines.delay(61_000)
                val rows = monobank.statement(token, account.id, from, nowSec)
                rows.sortedBy { it.timeSeconds }.forEach { row ->
                    val op = MonobankMapper.map(row, account.currency, account.cardLast4) ?: return@forEach
                    val result = store(op, "MONO:${row.id}", row.timeSeconds * 1000, "API", authoritative = true)
                    if (result == "AUTO_IMPORTED") imported++
                }
            }
            val now = System.currentTimeMillis()
            config.edit().putLong("mono_last_sync", now).apply()
            monoState.update { it.copy(connected = true, syncing = false, lastSyncMillis = now, lastImported = imported, error = null, accounts = accounts.size) }
            imported
        } catch (e: MonobankClient.ApiException) {
            monoState.update { it.copy(syncing = false, error = if (e.invalidToken) "BAD_TOKEN" else if (e.rateLimited) "RATE_LIMIT" else "NETWORK") }
            0
        } catch (e: java.io.IOException) {
            monoState.update { it.copy(syncing = false, error = "NETWORK") }; 0
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            monoState.update { it.copy(syncing = false, error = "NETWORK") }; 0
        } finally { syncLock.unlock() }
    }

    // -------------------------------------------------------------------------
    // Core receive path
    // -------------------------------------------------------------------------

    /** Parses a bank text (push/SMS/pasted) and stores it. Returns the outcome code. */
    suspend fun receive(
        bank: Bank, text: String, eventKey: String, at: Long,
        origin: String, manual: Boolean = false,
    ): String {
        if (!enabled && !manual) { note("DISABLED"); return "DISABLED" }
        val parsed = BankParser.parse(bank, text, at) ?: run { note("UNRECOGNIZED"); return "UNRECOGNIZED" }
        return store(parsed, eventKey, at, origin, manual)
    }

    /**
     * Stores an already parsed operation. [authoritative] sources (the bank's own API, unique id per
     * operation) skip the heuristic duplicate checks: the event key alone guarantees "once".
     */
    suspend fun store(
        parsed: ParsedBankOperation, eventKey: String, at: Long, origin: String,
        manual: Boolean = false, authoritative: Boolean = false,
    ): String {
        var result = "QUEUED"
        db.withTransaction {
            // --- Tier 1: same-bank re-delivery guard ---
            val sameBankDup = !authoritative && dao.similar(
                parsed.bankId, parsed.amountMinor, parsed.currency.name, parsed.type.name,
                parsed.happenedAtMillis, parsed.cardLast4, parsed.merchant,
            ) > 0

            // --- Tier 2: cross-source dedup (wallet + bank for the same tap) ---
            // Only applies when the same-bank check didn't already flag it.
            val crossSourceDup = !authoritative && !sameBankDup && dao.similarCrossSource(
                parsed.amountMinor, parsed.currency.name, parsed.type.name, parsed.happenedAtMillis,
            ) > 0

            // With auto-import on, repeats are dropped silently: a bank re-posting the same push, or a wallet
            // app and the issuing bank announcing the same tap. Otherwise they wait in the review queue.
            val walletInvolved = Banks.WALLET_ID == parsed.bankId || crossSourceDup && dao.walletTwin(
                parsed.amountMinor, parsed.currency.name, parsed.type.name, parsed.happenedAtMillis) > 0
            val silentDrop = !manual && autoImport && crossSourceDup && walletInvolved
            val status = when {
                silentDrop -> "DUPLICATE_IGNORED"
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
            if (!manual && autoImport && parsed.confident && !parsed.transfer && status == "REVIEW") {
                acceptInside(item, parsed.type)
                result = "AUTO_IMPORTED"
                
            }
        }
        // Notify only AFTER commit. A denied notification permission must never undo the entry.
        if (result == "AUTO_IMPORTED" && System.currentTimeMillis() - parsed.happenedAtMillis in 0 until 15 * 60_000L) postImportNotification(parsed)
        note(result)
        return result
    }

    private val privatState = MutableStateFlow(MonobankStatus(
        connected = config.getBoolean("privat_connected", false),
        lastSyncMillis = config.getLong("privat_last_sync", 0L)))
    override val privatStatus = privatState.asStateFlow()
    private val privatLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun connectPrivat(token: String): Boolean = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.length !in 20..2048 || clean.any { it.code !in 33..126 }) {
            privatState.update { it.copy(error = "BAD_TOKEN") }; return@withContext false
        }
        try {
            val today = LocalDate.now(ZoneId.of("Europe/Kyiv"))
            // Validate access before storing credentials. No payment endpoints are called.
            privat.statement(clean, today, today)
            val store = vault ?: error("Secure storage unavailable")
            store.update { it.put("privatToken", clean); Unit }
            config.edit().putBoolean("privat_connected", true).remove("privat_last_sync").putString("mode", "API").apply()
            privatState.value = MonobankStatus(connected = true)
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { privatState.update { it.copy(error = privatError(e)) }; false }
    }

    override suspend fun disconnectPrivat() = withContext(Dispatchers.IO) {
        vault?.update { it.remove("privatToken"); Unit }
        config.edit().remove("privat_connected").remove("privat_last_sync").apply()
        privatState.value = MonobankStatus()
    }

    override suspend fun syncPrivat(): Int = withContext(Dispatchers.IO) {
        if (!privatLock.tryLock()) return@withContext 0
        try {
            if (!enabled || !config.getBoolean("privat_connected", false)) return@withContext 0
            val token = vault?.read { it.optString("privatToken", "") }.orEmpty()
            if (token.isBlank()) { privatState.update { it.copy(connected = false) }; return@withContext 0 }
            privatState.update { it.copy(syncing = true, error = null) }
            val now = System.currentTimeMillis()
            val zone = ZoneId.of("Europe/Kyiv")
            val through = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val last = config.getLong("privat_last_sync", 0L)
            val from = if (last > 0) Instant.ofEpochMilli(last).atZone(zone).toLocalDate().minusDays(1).coerceAtLeast(through.minusDays(29)) else through.minusDays(29)
            var imported = 0
            // Short bounded windows also accommodate banks with restrictive statement date limits.
            var begin = from
            while (!begin.isAfter(through)) {
                val end = begin.plusDays(6).coerceAtMost(through)
                privat.statement(token, begin, end).forEach { row ->
                    val key = "PRIVAT:${row.key}"
                    if (store(row.parsed, key, row.parsed.happenedAtMillis, "API", authoritative = true) == "AUTO_IMPORTED") imported++
                }
                begin = end.plusDays(1)
            }
            config.edit().putLong("privat_last_sync", now).apply()
            privatState.update { it.copy(connected = true, syncing = false, lastSyncMillis = now, lastImported = imported, error = null) }
            imported
        } catch (e: CancellationException) { privatState.update { it.copy(syncing = false) }; throw e }
        catch (e: Exception) { privatState.update { it.copy(syncing = false, error = privatError(e)) }; 0 }
        finally { privatLock.unlock() }
    }

    private fun privatError(e: Exception): String = when ((e as? PrivatBankClient.ApiException)?.code) {
        401, 403 -> "BAD_TOKEN"; 429 -> "RATE_LIMIT"; else -> "NETWORK"
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
            "",
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
        val channelId = "ledger_import_v2"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Saved bank payments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Background bank import events"
                    setShowBadge(false)
                },
            )
        }
        val amountText = Money.editable(parsed.amountMinor)
        val title = "${Banks.name(parsed.bankId)}: ${parsed.merchant.ifBlank { Banks.name(parsed.bankId) }}"
        val body = "${if (parsed.type == EntryType.EXPENSE) "−" else "+"} $amountText ${parsed.currency.name}"
        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(title)
            .setContentText(body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(android.app.PendingIntent.getActivity(ctx, 0,
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: android.content.Intent(),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
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
