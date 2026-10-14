package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FinanceUpgradeTest {
    private val today = LocalDate.of(2026, 10, 4)
    @Test fun monthlyQueryResolvesCurrentCalendarMonth() {
        val query = VoiceFinanceQuery.parse("Скай витрати за місяць") as VoiceFinanceQuery.Statistics
        assertEquals(EntryType.EXPENSE, query.type)
        assertEquals(LocalDate.of(2026, 10, 1), query.from(today))
        assertNull(query.category)
    }
    @Test fun monthlyFoodQuery() {
        val query = VoiceFinanceQuery.parse("Скай витрати за місяць за продукти") as VoiceFinanceQuery.Statistics
        assertEquals(Category.FOOD, query.category)
        assertEquals(VoiceFinanceQuery.Period.MONTH, query.period)
    }
    @Test fun russianAndEnglishQueries() {
        assertEquals(Category.TRANSPORT, (VoiceFinanceQuery.parse("расходы за месяц на транспорт") as VoiceFinanceQuery.Statistics).category)
        assertEquals(Category.FOOD, (VoiceFinanceQuery.parse("expenses this month for food") as VoiceFinanceQuery.Statistics).category)
    }
    @Test fun periodsAndIncome() {
        assertEquals(today.minusDays(6), (VoiceFinanceQuery.parse("витрати за тиждень") as VoiceFinanceQuery.Statistics).from(today))
        assertEquals(FinanceWindow.EARLIEST, (VoiceFinanceQuery.parse("витрати за весь час") as VoiceFinanceQuery.Statistics).from(today))
        assertEquals(EntryType.INCOME, (VoiceFinanceQuery.parse("дохід за місяць") as VoiceFinanceQuery.Statistics).type)
        assertEquals(today.withDayOfYear(1), (VoiceFinanceQuery.parse("витрати за рік") as VoiceFinanceQuery.Statistics).from(today))
    }
    @Test fun aPurchaseIsNotAStatisticsQuestion() {
        assertNull(VoiceFinanceQuery.parse("я витратив 100 грн сьогодні"))
        assertEquals(10000L, VoiceEntryParser.parse("я витратив 100 грн сьогодні")!!.amountMinor)
    }
    @Test fun missingCurrencyNeverIndexesMissingRegexGroup() {
        assertEquals(10000L, VoiceEntryParser.parse("купив томати за 100")!!.amountMinor)
        assertEquals(200050L, VoiceEntryParser.parse("отримав зарплату 2000,50")!!.amountMinor)
        assertNull(VoiceEntryParser.parse("привіт"))
    }
    @Test fun quantityIsNotThePrice() {
        val entry = VoiceEntryParser.parse("привет я купил 1 кг томатов за 100 грн")!!
        assertEquals(10000L, entry.amountMinor)
        assertEquals(Category.FOOD, entry.category)
        assertEquals(EntryType.EXPENSE, entry.type)
    }
    @Test fun salaryAndGroupedAmounts() {
        val entry = VoiceEntryParser.parse("я получил зарплату 1 000 000 гривен")!!
        assertEquals(100000000L, entry.amountMinor)
        assertEquals(Category.SALARY, entry.category)
        assertEquals(EntryType.INCOME, entry.type)
    }
    @Test fun conversationsAndNegationsDoNotCreateEntries() {
        assertNull(VoiceEntryParser.parse("привіт мені 20 років"))
        assertNull(VoiceEntryParser.parse("я не купив продукти за 100 грн"))
        assertEquals(VoiceSmallTalk.Topic.GREETING, VoiceSmallTalk.classify("привет"))
    }
    @Test fun spokenNegativeHasAnExplicitMinusWord() {
        assertEquals("мінус 2000 гривень", SpokenMoneyFormatter.format(-200000, LedgerCurrency.UAH, AppLanguage.UKRAINIAN))
        assertEquals("minus 2000 hryvnias", SpokenMoneyFormatter.format(-200000, LedgerCurrency.UAH, AppLanguage.ENGLISH))
        assertFalse(SpokenMoneyFormatter.format(200000, LedgerCurrency.UAH, AppLanguage.UKRAINIAN).contains("мінус"))
        assertTrue(SpokenMoneyFormatter.format(Long.MIN_VALUE, LedgerCurrency.UAH, AppLanguage.ENGLISH).startsWith("minus "))
    }
    @Test fun zeroIsNotDebt() {
        assertEquals("0 гривень", SpokenMoneyFormatter.format(0, LedgerCurrency.UAH, AppLanguage.UKRAINIAN))
    }
}
