package com.semmyx.ledger.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Executable fake with real filtering, totals and undo semantics; no unimplemented test methods. */
class MemoryEntryRepository : EntryRepository {
    private val items = MutableStateFlow<Map<String, LedgerEntry>>(emptyMap())
    private val deleted = mutableMapOf<String, Pair<DeletionToken, LedgerEntry>>()
    private var sequence = 0L
    override fun observeEntries(filter: EntryFilter) = items.map { rows -> rows.values.filter {
        it.currency == filter.currency && (filter.type == null || it.type == filter.type) &&
            (filter.category == null || it.category == filter.category) &&
            (filter.from == null || !it.date.isBefore(filter.from)) &&
            (filter.through == null || !it.date.isAfter(filter.through)) &&
            (it.title.contains(filter.query, true) || it.note.contains(filter.query, true))
    }.sortedByDescending { it.date }.take(filter.limit) }
    override fun observeBalance(currency: LedgerCurrency) = items.map { rows ->
        val selected = rows.values.filter { it.currency == currency }
        Balance(selected.filter { it.type == EntryType.INCOME }.sumOf { it.amountMinor },
            selected.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountMinor }, selected.size)
    }
    override fun observeActivity(currency: LedgerCurrency, from: LocalDate, through: LocalDate) = items.map { rows ->
        rows.values.filter { it.currency == currency && !it.date.isBefore(from) && !it.date.isAfter(through) }
            .groupBy { it.date }.map { (date, entries) -> DailyActivity(date,
                entries.filter { it.type == EntryType.INCOME }.sumOf { it.amountMinor },
                entries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountMinor }) }.sortedBy { it.date }
    }
    override suspend fun find(id: String) = items.value[id]
    override suspend fun save(entry: LedgerEntry) { items.value = items.value + (entry.id to entry) }
    override suspend fun delete(id: String): DeletionToken? {
        val entry = items.value[id] ?: return null
        val token = DeletionToken(id, ++sequence)
        deleted[id] = token to entry
        items.value = items.value - id
        return token
    }
    override suspend fun undo(token: DeletionToken): Boolean {
        val saved = deleted[token.id]?.takeIf { it.first == token } ?: return false
        deleted.remove(token.id)
        items.value = items.value + (token.id to saved.second)
        return true
    }
    override suspend fun purgeDeletedBefore(epochMillis: Long) { deleted.entries.removeAll { it.value.first.deletedAtMillis < epochMillis } }
}
