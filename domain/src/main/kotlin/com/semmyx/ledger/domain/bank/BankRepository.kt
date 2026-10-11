package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

data class CustomBankCategory(val id: String, val name: String, val colorHex: String)
data class BankSourceSpend(val sourceType: String, val bankId: String?, val totalMinor: Long)
data class BankCategorySpend(val category: Category, val customCategoryId: String?, val customName: String?, val colorHex: String?, val totalMinor: Long)
data class ImportReview(val id: String, val bankId: String, val amountMinor: Long, val currency: LedgerCurrency,
    val type: EntryType, val merchant: String, val cardLast4: String?, val eventAtMillis: Long, val timeFromMessage: Boolean,
    val transfer: Boolean, val status: String)
data class ImportDiagnostics(val connected: Boolean = false, val notificationCount: Int = 0, val bankMessageCount: Int = 0,
    val lastCode: String = "WAITING", val lastPackage: String = "", val lastAtMillis: Long = 0)
/** [error] is a code: BAD_TOKEN, RATE_LIMIT, NETWORK, NO_ACCOUNTS (or null). */
data class MonobankStatus(val connected: Boolean = false, val syncing: Boolean = false, val lastSyncMillis: Long = 0,
    val lastImported: Int = 0, val accounts: Int = 0, val error: String? = null)
interface BankRepository {
    /** "API" (monobank token), "SMS" or "NOTIFY" (bank app notifications). */
    var importMode: String
    val monobankStatus: Flow<MonobankStatus>
    suspend fun connectMonobank(token: String): Boolean
    suspend fun disconnectMonobank()
    suspend fun syncMonobank(): Int
    val diagnostics: Flow<ImportDiagnostics>
    fun configuredPackage(bankId: String): String
    fun setPackage(bankId: String, packageName: String)
    suspend fun importText(bankId: String, text: String): Boolean
    var enabled: Boolean
    var autoImport: Boolean
    fun categories(): Flow<List<CustomBankCategory>>
    fun reviews(): Flow<List<ImportReview>>
    fun sources(currency: LedgerCurrency, from: LocalDate, through: LocalDate): Flow<List<BankSourceSpend>>
    fun categorySpending(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?): Flow<List<BankCategorySpend>>
    fun recent(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?): Flow<List<LedgerEntry>>
    suspend fun accept(id: String, type: EntryType)
    suspend fun ignore(id: String)
    suspend fun createCategory(name: String, hex: String)
    suspend fun clearHistory()
    suspend fun exportEntries(): List<LedgerEntry>
    fun budget(currency: LedgerCurrency): Long
    fun setBudget(currency: LedgerCurrency, minor: Long)
}
