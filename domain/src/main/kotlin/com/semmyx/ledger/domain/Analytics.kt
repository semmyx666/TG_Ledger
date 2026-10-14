package com.semmyx.ledger.domain

import java.time.LocalDate

data class CategoryTotal(val category: Category, val incomeMinor: Long, val expenseMinor: Long, val count: Int, val incomeCount: Int = 0, val expenseCount: Int = 0)

/** Zero is the all-time sentinel; calendar windows never start before supported entries. */
object FinanceWindow {
    val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)
    fun from(days: Int, today: LocalDate): LocalDate = if (days == 0) EARLIEST else today.minusDays(days.toLong() - 1)
}

object TrialPolicy {
    /** One trial only: 14 days. */
    const val DAYS = 14
    fun validDays(days: Int) = days == DAYS
    fun active(start: Long, end: Long, now: Long, lastSeen: Long): Boolean =
        start > 0 && end > start && now >= start && now >= lastSeen && now < end
}
