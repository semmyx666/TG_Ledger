package com.semmyx.ledger.domain

import org.junit.Assert.*
import org.junit.Test

class ColorPreferencesTest {
    @Test fun normalizeValidHex() {
        assertEquals("#6FAEFF", ColorPreferences.normalizeHex(" 6faeff "))
        assertEquals("#000000", ColorPreferences.normalizeHex("#000000"))
        assertEquals("#FFFFFF", ColorPreferences.normalizeHex("ffffff"))
    }
    @Test fun rejectMalformedOrTransparentColors() {
        listOf("", "#fff", "#GG0011", "#FF001122", "red", "#12 3456").forEach { assertNull(ColorPreferences.normalizeHex(it)) }
    }
    @Test fun brandAndFinancialColorsAreIndependent() {
        assertEquals("#6FAEFF", ColorPreferences.defaultHex("accent"))
        assertEquals("#48D597", ColorPreferences.defaultHex("income"))
        assertEquals("#FF647C", ColorPreferences.defaultHex("expense"))
    }
    @Test fun eachCategoryHasSeparateExpenseAndIncomeKeys() {
        Category.entries.forEach { category ->
            val expense = ColorPreferences.categoryKey(category, EntryType.EXPENSE)
            val income = ColorPreferences.categoryKey(category, EntryType.INCOME)
            assertNotEquals(expense, income)
            listOf(expense, income).forEach { key ->
                assertTrue(ColorPreferences.validKey(key))
                assertNotNull(ColorPreferences.normalizeHex(ColorPreferences.defaultHex(key)))
            }
        }
        assertFalse(ColorPreferences.validKey("category:EXPENSE:UNKNOWN"))
        assertFalse(ColorPreferences.validKey("arbitrary"))
    }
}
