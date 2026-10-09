package com.semmyx.ledger.domain

import org.junit.Assert.*
import org.junit.Test

class MoneyAndPolicyTest {
    @Test fun decimalParsingNeverUsesBinaryFloatingPoint() {
        assertEquals(10L, Money.parseMinor("0.10"))
        assertEquals(1250L, Money.parseMinor("12,50"))
        assertEquals(100L, Money.parseMinor("1"))
        assertEquals(Money.MAX_ENTRY_MINOR, Money.parseMinor("99999999.99"))
    }
    @Test fun invalidAndAmbiguousAmountsAreRejected() {
        listOf("0", "-1", "1.001", "1,234.56", "1e3", "NaN", "", "100000000.00", "١٢").forEach { assertNull(it, Money.parseMinor(it)) }
    }
    @Test fun formatterRoundTripsMinorUnits() {
        for (minor in listOf(1L, 99L, 100L, 101L, Money.MAX_ENTRY_MINOR)) assertEquals(minor, Money.parseMinor(Money.editable(minor)))
    }
    @Test fun pinRequiresSixAsciiDigits() {
        assertTrue(PinPolicy.valid("123456".toCharArray()))
        assertFalse(PinPolicy.valid("12345".toCharArray()))
        assertFalse(PinPolicy.valid("１２３４５６".toCharArray()))
    }
    @Test fun delaysEscalateAndHaveAnUpperBound() {
        assertEquals(0L, PinPolicy.delaySeconds(4))
        assertEquals(30L, PinPolicy.delaySeconds(5))
        assertEquals(60L, PinPolicy.delaySeconds(6))
        assertEquals(3600L, PinPolicy.delaySeconds(1000))
    }
}
