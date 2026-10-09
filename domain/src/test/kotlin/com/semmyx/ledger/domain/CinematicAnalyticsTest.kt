package com.semmyx.ledger.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CinematicAnalyticsTest {
    @Test fun zeroHasNoInventedSlice() {
        assertTrue(AnalyticsMath.slices(emptyList(), EntryType.EXPENSE).isEmpty())
        assertTrue(AnalyticsMath.slices(listOf(CategoryTotal(Category.FOOD, 0, 0, 0)), EntryType.INCOME).isEmpty())
    }
    @Test fun oneCategoryIsExactlyOneHundredPercent() {
        val slice = AnalyticsMath.slices(listOf(CategoryTotal(Category.SALARY, 104300, 0, 13, 13, 0)), EntryType.INCOME).single()
        assertEquals(10000, slice.basisPoints)
        assertEquals(13, slice.count)
    }
    @Test fun thirdsAddToOneHundredWithoutFloatingPointDrift() {
        val values = listOf(Category.FOOD, Category.HOME, Category.HEALTH).map { CategoryTotal(it, 0, 100, 1, 0, 1) }
        val slices = AnalyticsMath.slices(values, EntryType.EXPENSE)
        assertEquals(10000, slices.sumOf { it.basisPoints })
        assertEquals(listOf(3334, 3333, 3333), slices.map { it.basisPoints })
    }
    @Test fun countIsSpecificToTransactionType() {
        val total = CategoryTotal(Category.OTHER, 50000, 70000, 15, 13, 2)
        assertEquals(13, AnalyticsMath.slices(listOf(total), EntryType.INCOME).single().count)
        assertEquals(2, AnalyticsMath.slices(listOf(total), EntryType.EXPENSE).single().count)
    }
    @Test fun august2026MatchesReferenceWeekFragments() {
        val buckets = AnalyticsMath.buckets(emptyList(), YearMonth.of(2026, 8), LocalDate.of(2026, 8, 31))
        assertEquals(listOf(1 to 2, 3 to 9, 10 to 16, 17 to 23, 24 to 30, 31 to 31),
            buckets.map { it.from.dayOfMonth to it.through.dayOfMonth })
        assertTrue(buckets.all { it.expenseMinor == 0L })
    }
    @Test fun leapMonthNeverLeaksIntoMarch() {
        val buckets = AnalyticsMath.buckets(emptyList(), YearMonth.of(2024, 2), LocalDate.of(2024, 2, 29))
        assertEquals(LocalDate.of(2024, 2, 29), buckets.last().through)
    }
    @Test fun weekTotalsEqualSourceTotals() {
        val rows = listOf(DailyActivity(LocalDate.of(2026, 8, 1), 100, 200), DailyActivity(LocalDate.of(2026, 8, 8), 400, 600))
        val buckets = AnalyticsMath.buckets(rows, YearMonth.of(2026, 8), LocalDate.of(2026, 8, 31))
        assertEquals(800L, buckets.sumOf { it.expenseMinor })
        assertEquals(500L, buckets.sumOf { it.incomeMinor })
    }
}
