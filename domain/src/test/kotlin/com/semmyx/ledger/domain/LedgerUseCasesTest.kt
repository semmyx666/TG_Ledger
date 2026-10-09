package com.semmyx.ledger.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LedgerUseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC)
    private val date = LocalDate.of(2026, 9, 8)
    private fun entry(id: String = "one", currency: LedgerCurrency = LedgerCurrency.UAH, type: EntryType = EntryType.EXPENSE) =
        LedgerEntry(id, "  Coffee  ", 1250, currency, type, Category.FOOD, date, " note ")

    @Test fun saveTrimsTextAndUpdateDoesNotDuplicate() = runTest {
        val repo = MemoryEntryRepository()
        val save = SaveEntry(repo, clock)
        save(entry())
        assertEquals("Coffee", repo.find("one")?.title)
        save(entry().copy(amountMinor = 2500))
        assertEquals(1, repo.observeBalance(LedgerCurrency.UAH).first().entryCount)
        assertEquals(2500L, repo.find("one")?.amountMinor)
    }
    @Test fun futureDatesAreRejected() = runTest {
        try { SaveEntry(MemoryEntryRepository(), clock)(entry().copy(date = date.plusDays(1))); fail("Expected validation") }
        catch (error: EntryValidationException) { assertEquals(ValidationIssue.DATE, error.issue) }
    }
    @Test fun currenciesNeverMixAndChartsZeroFillMissingDays() = runTest {
        val repo = MemoryEntryRepository()
        repo.save(entry("uah"))
        repo.save(entry("usd", LedgerCurrency.USD, EntryType.INCOME).copy(amountMinor = 999999))
        val snapshot = ObserveDashboard(repo)(EntryFilter(LedgerCurrency.UAH), date.minusDays(6), date).first()
        assertEquals(7, snapshot.activity.size)
        assertEquals(0L, snapshot.balance.incomeMinor)
        assertEquals(-1250L, snapshot.balance.netMinor)
        assertEquals(0L, snapshot.activity.first().expenseMinor)
        assertEquals(1250L, snapshot.activity.last().expenseMinor)
    }
    @Test fun searchDoesNotChangeOverallTotals() = runTest {
        val repo = MemoryEntryRepository()
        repo.save(entry())
        val snapshot = ObserveDashboard(repo)(EntryFilter(LedgerCurrency.UAH, "missing"), date, date).first()
        assertTrue(snapshot.entries.isEmpty())
        assertEquals(1250L, snapshot.balance.expenseMinor)
    }
    @Test fun deleteAndUndoAreIdempotent() = runTest {
        val repo = MemoryEntryRepository()
        repo.save(entry())
        val token = requireNotNull(repo.delete("one"))
        assertNull(repo.find("one"))
        assertNull(repo.delete("one"))
        assertTrue(repo.undo(token))
        assertFalse(repo.undo(token))
        assertNotNull(repo.find("one"))
    }
}
