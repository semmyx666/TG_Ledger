package com.semmyx.ledger.domain.bank
import org.junit.Assert.*
import org.junit.Test
class BankPeriodAccessTest {
    @Test fun freeOnlyTodayAndNoAllTimeBypass() {
        BankPeriodAccess.periods.forEach { assertEquals("DAY", BankPeriodAccess.effective(it, false)) }
    }
    @Test fun premiumAndTrialCanUseEveryPeriod() {
        BankPeriodAccess.periods.forEach { assertEquals(it, BankPeriodAccess.effective(it, true)) }
    }
    @Test fun expiredAccessFallsBackAndInvalidPeriodIsRejected() {
        assertEquals("DAY", BankPeriodAccess.effective("MONTH", false))
        assertEquals("DAY", BankPeriodAccess.effective("INVALID", true))
    }
}
