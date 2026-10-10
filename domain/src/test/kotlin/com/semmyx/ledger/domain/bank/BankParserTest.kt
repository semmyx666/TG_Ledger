package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.Category
import com.semmyx.ledger.domain.EntryType
import com.semmyx.ledger.domain.LedgerCurrency
import com.semmyx.ledger.domain.bank.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class BankParserTest {
    private val bank = Banks.all.first { it.id == "mono" }
    private val at = Instant.parse("2026-10-07T20:00:00Z").toEpochMilli()
    @Test fun debitExtractsAmountShopCardAndDate() {
        val parsed = BankParser.parse(bank, "Оплата 1 200,50 грн у АТБ; картка *1234; 07.10.2026 22:59", at, ZoneId.of("Europe/Kyiv"))!!
        assertEquals(120050L, parsed.amountMinor)
        assertEquals(EntryType.EXPENSE, parsed.type)
        assertEquals("1234", parsed.cardLast4)
        assertEquals(Category.FOOD, parsed.category)
        assertTrue(parsed.confident)
        assertTrue(parsed.timeFromMessage)
    }
    @Test fun ignoresBalanceOtpAndDeclined() {
        assertNull(BankParser.parse(bank, "Баланс 1200 грн", at))
        assertNull(BankParser.parse(bank, "Код підтвердження оплати 100 грн 123456", at))
        assertNull(BankParser.parse(bank, "Payment declined 10 EUR at Steam", at))
    }
    @Test fun doesNotChooseBalanceAsPurchase() {
        assertEquals(10000L, BankParser.parse(bank, "Оплата 100 грн у АТБ; баланс 900 грн", at)!!.amountMinor)
    }
    @Test fun transfersRequireReview() { assertFalse(BankParser.parse(bank, "Переказ 100 грн", at)!!.confident) }
    @Test fun currencyPrefixAndIncome() {
        assertEquals(1250L, BankParser.parse(bank, "Payment \$12.50 at Steam", at)!!.amountMinor)
        assertEquals(LedgerCurrency.USD, BankParser.parse(bank, "Payment \$12.50 at Steam", at)!!.currency)
        assertEquals(EntryType.INCOME, BankParser.parse(bank, "Зараховано 2000 грн", at)!!.type)
    }
    @Test fun receiptTimeIsNotInventedBankTime() {
        val value = BankParser.parse(bank, "Оплата 100 грн у АТБ", at)!!
        assertEquals(at, value.happenedAtMillis); assertFalse(value.timeFromMessage)
    }
}
