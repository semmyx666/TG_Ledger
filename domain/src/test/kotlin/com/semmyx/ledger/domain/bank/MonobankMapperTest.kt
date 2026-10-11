package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*
import org.junit.Assert.*
import org.junit.Test

class MonobankMapperTest {
    @Test fun expenseUsesMccAndSignedAmount() {
        val op = MonobankMapper.map(MonobankStatementItem("a1", 1_700_000_000, "Сільпо", 5411, -12550), LedgerCurrency.UAH, "1234")!!
        assertEquals(12550L, op.amountMinor); assertEquals(EntryType.EXPENSE, op.type)
        assertEquals(Category.FOOD, op.category); assertEquals("1234", op.cardLast4)
        assertEquals(1_700_000_000_000L, op.happenedAtMillis); assertTrue(op.confident)
    }
    @Test fun incomeAndTransfer() {
        val op = MonobankMapper.map(MonobankStatementItem("a2", 1, "Від: Іван П.", 4829, 50000), LedgerCurrency.UAH, null)!!
        assertEquals(EntryType.INCOME, op.type); assertTrue(op.transfer); assertEquals(Category.TRANSFER, op.category)
    }
    @Test fun unknownMccFallsBackToName() {
        assertEquals(Category.TRANSPORT, MonobankMapper.map(MonobankStatementItem("a3", 1, "WOG", 0, -100), LedgerCurrency.UAH, null)!!.category)
    }
    @Test fun zeroAndBlankAreSkipped() {
        assertNull(MonobankMapper.map(MonobankStatementItem("a4", 1, "x", 0, 0), LedgerCurrency.UAH, null))
        assertNull(MonobankMapper.map(MonobankStatementItem("", 1, "x", 0, -5), LedgerCurrency.UAH, null))
    }
    @Test fun currencyCodes() {
        assertEquals(LedgerCurrency.UAH, MonobankMapper.currency(980)); assertEquals(LedgerCurrency.USD, MonobankMapper.currency(840))
        assertNull(MonobankMapper.currency(392))
    }
}
