package com.semmyx.ledger.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

object Money {
    const val MAX_ENTRY_MINOR = 9_999_999_999L
    /** Accept comma OR dot decimals, not ambiguous grouping or rounding. */
    fun parseMinor(input: String): Long? {
        val normalized = input.trim().replace(',', '.')
        if (!Regex("[0-9]{1,8}(\\.[0-9]{1,2})?").matches(normalized)) return null
        return try {
            BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact().takeIf { it in 1..MAX_ENTRY_MINOR }
        } catch (_: ArithmeticException) { null }
    }
    fun editable(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
}

class SaveEntry(private val repository: EntryRepository, private val clock: Clock) {
    suspend operator fun invoke(entry: LedgerEntry) {
        val cleaned = entry.copy(title = entry.title.trim(), note = entry.note.trim())
        if (cleaned.title.isEmpty() || cleaned.title.length > 60) throw EntryValidationException(ValidationIssue.TITLE)
        if (cleaned.amountMinor !in 1..Money.MAX_ENTRY_MINOR) throw EntryValidationException(ValidationIssue.AMOUNT)
        if (cleaned.note.length > 240) throw EntryValidationException(ValidationIssue.NOTE)
        if (cleaned.date.isAfter(LocalDate.now(clock)) || cleaned.date.year < 2000) throw EntryValidationException(ValidationIssue.DATE)
        repository.save(cleaned)
    }
}

data class DashboardSnapshot(
    val entries: List<LedgerEntry> = emptyList(),
    val balance: Balance = Balance(),
    val activity: List<DailyActivity> = emptyList(),
)

class ObserveDashboard(private val repository: EntryRepository) {
    operator fun invoke(filter: EntryFilter, from: LocalDate, through: LocalDate): Flow<DashboardSnapshot> =
        combine(repository.observeEntries(filter), repository.observeBalance(filter.currency),
            repository.observeActivity(filter.currency, from, through)) { entries, balance, activity ->
            val byDate = activity.associateBy { it.date }
            val days = if (java.time.temporal.ChronoUnit.DAYS.between(from, through) > 90) activity else generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(through) }
                .map { byDate[it] ?: DailyActivity(it, 0, 0) }.toList()
            DashboardSnapshot(entries, balance, days)
        }
}

/** Persisted escalating delay limits brute force; the repository seals its state. */
object PinPolicy {
    const val LENGTH = 6
    const val PBKDF2_ITERATIONS = 210_000
    fun valid(pin: CharArray): Boolean = pin.size == LENGTH && pin.all { it in '0'..'9' }
    fun delaySeconds(failures: Int): Long = if (failures < 5) 0L else
        (30L * (1L shl (failures - 5).coerceAtMost(7))).coerceAtMost(3600L)
}
