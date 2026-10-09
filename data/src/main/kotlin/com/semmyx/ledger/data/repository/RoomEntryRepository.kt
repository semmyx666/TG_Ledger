package com.semmyx.ledger.data.repository

import com.semmyx.ledger.data.local.EntryEntity
import com.semmyx.ledger.data.local.LedgerDao
import com.semmyx.ledger.domain.*
import java.text.Normalizer
import java.util.Locale
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.map

class RoomEntryRepository(private val dao: LedgerDao, private val clock: Clock) : EntryRepository {
    override fun observeEntries(filter: EntryFilter) = dao.observeEntries(
        filter.currency.name, likePattern(filter.query), filter.type?.name, filter.limit.coerceAtLeast(1),
        filter.category?.name, filter.from?.toEpochDay(), filter.through?.toEpochDay()
    ).map { rows -> rows.map { it.toDomain() } }
    override fun observeBalance(currency: LedgerCurrency) = dao.observeBalance(currency.name)
        .map { Balance(it.incomeMinor, it.expenseMinor, it.entryCount) }
    override fun observeActivity(currency: LedgerCurrency, from: LocalDate, through: LocalDate) =
        dao.observeActivity(currency.name, from.toEpochDay(), through.toEpochDay())
            .map { rows -> rows.map { DailyActivity(LocalDate.ofEpochDay(it.epochDay), it.incomeMinor, it.expenseMinor) } }
    override fun observeCategoryTotals(currency: LedgerCurrency, from: LocalDate, through: LocalDate) =
        dao.observeCategoryTotals(currency.name, from.toEpochDay(), through.toEpochDay()).map { rows ->
            rows.map { CategoryTotal(Category.valueOf(it.category), it.incomeMinor, it.expenseMinor, it.count, it.incomeCount, it.expenseCount) }
        }
    override suspend fun statistics(currency: LedgerCurrency, from: LocalDate, through: LocalDate, type: EntryType, category: Category?) =
        dao.statistics(currency.name, from.toEpochDay(), through.toEpochDay(), type.name, category?.name)
    override suspend fun find(id: String): LedgerEntry? = dao.find(id)?.toDomain()
    override suspend fun save(entry: LedgerEntry) = dao.save(entry.toEntity(clock.millis()))
    override suspend fun delete(id: String): DeletionToken? {
        val now = clock.millis()
        return if (dao.delete(id, now) == 1) DeletionToken(id, now) else null
    }
    override suspend fun undo(token: DeletionToken) = dao.undo(token.id, token.deletedAtMillis) == 1
    override suspend fun purgeDeletedBefore(epochMillis: Long) = dao.purge(epochMillis)

    companion object {
        fun likePattern(query: String): String = "%" + normalizeSearch(query.trim())
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}

internal fun EntryEntity.toDomain() = LedgerEntry(id, title, amountMinor, LedgerCurrency.valueOf(currency),
    EntryType.valueOf(type), Category.valueOf(category), LocalDate.ofEpochDay(epochDay), note, sourceType, bankId, cardLast4, eventAtMillis, customCategoryId, origin)
private fun LedgerEntry.toEntity(created: Long) = EntryEntity(id, title, amountMinor, currency.name, type.name, category.name,
    date.toEpochDay(), note, normalizeSearch(title + " " + note), created, sourceType = sourceType, bankId = bankId, cardLast4 = cardLast4,
    eventAtMillis = eventAtMillis, customCategoryId = customCategoryId, origin = origin)
private fun normalizeSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
