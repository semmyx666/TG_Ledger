package com.semmyx.ledger.domain

import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode

data class CashBucket(val from: LocalDate, val through: LocalDate, val incomeMinor: Long, val expenseMinor: Long)
data class CategorySlice(val category: Category, val minor: Long, val count: Int, val basisPoints: Int)

object AnalyticsMath {
    /** Monetary values stay Long. Rounded display percentages use largest remainder: total is 100.00%. */
    fun slices(totals: List<CategoryTotal>, type: EntryType): List<CategorySlice> {
        val nonzero = totals.map { total ->
            Triple(total.category, if (type == EntryType.EXPENSE) total.expenseMinor else total.incomeMinor,
                if (type == EntryType.EXPENSE) total.expenseCount else total.incomeCount)
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        val total = nonzero.fold(0L) { acc, row -> Math.addExact(acc, row.second) }
        if (total == 0L) return emptyList()
        val exact = nonzero.map { BigDecimal.valueOf(it.second).multiply(BigDecimal(10000))
            .divide(BigDecimal.valueOf(total), 20, RoundingMode.DOWN) }
        val rounded = exact.map { it.toInt() }.toMutableList()
        val remainder = 10000 - rounded.sum()
        exact.indices.sortedByDescending { exact[it].subtract(BigDecimal(rounded[it])) }.take(remainder)
            .forEach { rounded[it]++ }
        return nonzero.mapIndexed { i, row -> CategorySlice(row.first, row.second, row.third, rounded[i]) }
    }
    /** Screenshot structure: calendar-week fragments inside a chosen month, including zero-valued buckets. */
    fun buckets(activity: List<DailyActivity>, month: YearMonth?, through: LocalDate): List<CashBucket> {
        if (month == null) return activity.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (m, rows) ->
            CashBucket(m.atDay(1), minOf(m.atEndOfMonth(), through), rows.sumOf { it.incomeMinor }, rows.sumOf { it.expenseMinor })
        }
        if (through.isBefore(month.atDay(1))) return emptyList()
        val result = mutableListOf<CashBucket>()
        var start = month.atDay(1)
        while (!start.isAfter(through)) {
            val end = minOf(start.plusDays(7L - start.dayOfWeek.value), through)
            val rows = activity.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            result += CashBucket(start, end, rows.sumOf { it.incomeMinor }, rows.sumOf { it.expenseMinor })
            start = end.plusDays(1)
        }
        return result
    }
}
