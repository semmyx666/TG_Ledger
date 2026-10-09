package com.semmyx.ledger.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

interface EntryRepository {
    fun observeEntries(filter: EntryFilter): Flow<List<LedgerEntry>>
    fun observeBalance(currency: LedgerCurrency): Flow<Balance>
    fun observeActivity(currency: LedgerCurrency, from: LocalDate, through: LocalDate): Flow<List<DailyActivity>>
    fun observeCategoryTotals(currency: LedgerCurrency, from: LocalDate, through: LocalDate): Flow<List<CategoryTotal>> =
        observeEntries(EntryFilter(currency, limit = Int.MAX_VALUE)).map { rows ->
            rows.filter { !it.date.isBefore(from) && !it.date.isAfter(through) }.groupBy { it.category }.map { (category, values) ->
                CategoryTotal(category, values.filter { it.type == EntryType.INCOME }.sumOf { it.amountMinor },
                    values.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountMinor }, values.size,
                    values.count { it.type == EntryType.INCOME }, values.count { it.type == EntryType.EXPENSE })
            }
        }
    suspend fun statistics(currency: LedgerCurrency, from: LocalDate, through: LocalDate, type: EntryType, category: Category?): Long =
        observeCategoryTotals(currency, from, through).first().filter { category == null || it.category == category }
            .sumOf { if (type == EntryType.EXPENSE) it.expenseMinor else it.incomeMinor }
    suspend fun find(id: String): LedgerEntry?
    suspend fun save(entry: LedgerEntry)
    suspend fun delete(id: String): DeletionToken?
    suspend fun undo(token: DeletionToken): Boolean
    suspend fun purgeDeletedBefore(epochMillis: Long)
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setName(name: String)
    suspend fun setCurrency(currency: LedgerCurrency)
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun completeOnboarding()
    suspend fun setBiometrics(enabled: Boolean)
    suspend fun setPremiumAssistant(enabled: Boolean)
    suspend fun setMascotSkin(value: String) { }
    suspend fun setMascotAnimations(enabled: Boolean) { }
    suspend fun setRefreshRate(hz: Int) { }
    suspend fun setColor(key: String, hex: String?) { }
    suspend fun resetColors() { }
}

interface SecurityRepository {
    suspend fun hasPin(): Boolean
    suspend fun createPin(pin: CharArray)
    suspend fun verifyPin(pin: CharArray): PinResult
    suspend fun lockoutSeconds(): Long
}

interface RateRepository {
    val rates: Flow<List<ExchangeRate>>
    suspend fun refresh()
}

/**
 * The Activity needed for a Play purchase is deliberately NOT part of the domain.
 *
 * Two independent entitlement sources exist for the same non-consumable
 * product: Google Play, and a developer-issued offline activation code (for
 * testing before/alongside a Play listing, or for direct manual sales, e.g.
 * over Telegram). Either one alone can grant `entitled`.
 */
interface PremiumRepository {
    val state: StateFlow<PremiumState>
    suspend fun refresh()
    /** Verifies and, if valid, stores a developer-issued activation code. Returns true if it granted access. */
    suspend fun activateDeveloperToken(token: String): Boolean
    /** Stable id to hand the developer so they can bind a code to this installation. */
    suspend fun developerInstallationId(): String
    suspend fun startTrial(days: Int): Boolean = false
    fun clearIssue()
}
