package com.semmyx.ledger.domain

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class UpgradePolicyTest {
    @Test fun trialDurationsAndBoundary() {
        assertTrue(TrialPolicy.validDays(7)); assertTrue(TrialPolicy.validDays(14))
        assertFalse(TrialPolicy.validDays(30)); assertFalse(TrialPolicy.validDays(0))
        assertTrue(TrialPolicy.active(100, 200, 199, 150))
        assertFalse(TrialPolicy.active(100, 200, 200, 150))
        assertFalse(TrialPolicy.active(100, 200, 149, 150))
        assertFalse(TrialPolicy.active(0, 200, 100, 100))
    }
    @Test fun categoryDetailsAndSumAreNotLimitedToFirstFifty() = runTest {
        val repository = MemoryEntryRepository()
        val day = LocalDate.of(2026, 10, 4)
        repeat(125) { id -> repository.save(LedgerEntry("$id", "Food $id", 100,
            LedgerCurrency.UAH, EntryType.EXPENSE, Category.FOOD, day)) }
        repository.save(LedgerEntry("other", "Taxi", 9000, LedgerCurrency.UAH, EntryType.EXPENSE, Category.TRANSPORT, day))
        repository.save(LedgerEntry("old", "Food last month", 7000, LedgerCurrency.UAH, EntryType.EXPENSE, Category.FOOD, day.minusMonths(1)))
        val filter = EntryFilter(LedgerCurrency.UAH, limit = 51, category = Category.FOOD, from = day.withDayOfMonth(1), through = day)
        assertEquals(51, repository.observeEntries(filter).first().size)
        assertEquals(125, repository.observeEntries(filter.copy(limit = 150)).first().size)
        assertEquals(12500L, repository.statistics(LedgerCurrency.UAH, day.withDayOfMonth(1), day, EntryType.EXPENSE, Category.FOOD))
        assertEquals(19500L, repository.statistics(LedgerCurrency.UAH, FinanceWindow.EARLIEST, day, EntryType.EXPENSE, Category.FOOD))
    }
}
