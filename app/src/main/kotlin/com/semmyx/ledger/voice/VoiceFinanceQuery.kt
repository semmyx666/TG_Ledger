package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.time.LocalDate

/** Structured intent resolver. Queries are resolved BEFORE monetary entries, never saved as purchases. */
sealed interface VoiceFinanceQuery {
    data object Balance : VoiceFinanceQuery
    enum class Period { TODAY, WEEK, MONTH, YEAR, ALL }
    data class Statistics(val period: Period, val type: EntryType, val category: Category?) : VoiceFinanceQuery {
        fun from(today: LocalDate): LocalDate = when (period) {
            Period.TODAY -> today
            Period.WEEK -> today.minusDays(6)
            Period.MONTH -> today.withDayOfMonth(1)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> FinanceWindow.EARLIEST
        }
    }
    companion object {
        fun parse(heard: String): VoiceFinanceQuery? {
            val text = heard.lowercase().trim()
            val balance = listOf("баланс", "balance", "скільки грошей", "сколько денег").any { it in text }
            val hasNumber = Regex("\\d").containsMatchIn(text)
            if (balance && !hasNumber) return Balance
            val expense = listOf("витрат", "потрат", "расход", "spent", "spend", "expense").any { it in text }
            val income = listOf("дохід", "доход", "заробив", "income", "earned").any { it in text }
            if (!expense && !income) return null
            val period = when {
                listOf("весь час", "все время", "all time", "увесь час").any { it in text } -> Period.ALL
                listOf("місяц", "месяц", "month").any { it in text } -> Period.MONTH
                listOf("тиж", "недел", "week").any { it in text } -> Period.WEEK
                listOf("за рік", "за год", "year").any { it in text } -> Period.YEAR
                listOf("сьогодні", "сегодня", "today").any { it in text } -> Period.TODAY
                else -> return null
            }
            // "I spent 100 today" describes an entry, not a statistics request.
            if (hasNumber) return null
            return Statistics(period, if (income) EntryType.INCOME else EntryType.EXPENSE, category(text))
        }
        fun category(text: String): Category? = listOf(
            Category.TRANSFER to listOf("переказ", "перевод", "transfer"),
            Category.DINING to listOf("ресторан", "restaurant", "кафе", "cafe"),
            Category.CASHBACK to listOf("кешбек", "кэшбек", "cashback"),
            Category.FOOD to listOf("продукт", "їж", "еду", "питан", "food", "grocer"),
            Category.TRANSPORT to listOf("транспорт", "таксі", "такси", "transport", "fuel"),
            Category.HOME to listOf("дім", "житло", "дом", "оренд", "комунал", "home", "rent"),
            Category.HEALTH to listOf("здоров", "ліки", "лекар", "health", "medicine"),
            Category.LEISURE to listOf("розваг", "развлеч", "leisure", "entertainment"),
            Category.SALARY to listOf("зарплат", "salary"),
            Category.OTHER to listOf("інше", "прочее", "other")
        ).firstOrNull { (_, words) -> words.any { it in text } }?.first
    }
}
