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

    private fun parse(text: String, bankId: String = "mono") = BankParser.parse(Banks.all.first { it.id == bankId }, text, at, ZoneId.of("Europe/Kyiv"))

    // ---- Realistic push formats: title on the first line, body below (as the listener builds them) ----
    @Test fun monoUnicodeMinusWithMerchantInTitle() {
        val p = parse("Сільпо\n−1 234,56 ₴ · Залишок 10 000,00 ₴")!!
        assertEquals(123456L, p.amountMinor); assertEquals(EntryType.EXPENSE, p.type)
        assertEquals("Сільпо", p.merchant); assertEquals(Category.FOOD, p.category); assertTrue(p.confident)
    }
    @Test fun monoIncomePlusSign() {
        val p = parse("Іван П.\n+500,00 ₴ Баланс 1 500,00 ₴")!!
        assertEquals(50000L, p.amountMinor); assertEquals(EntryType.INCOME, p.type); assertTrue(p.confident)
    }
    @Test fun privatPurchaseWithGluedCurrencyAndBalance() {
        val p = parse("Оплата 150.50UAH Картка *4455 WOG Баланс 2000.00UAH", "privat")!!
        assertEquals(15050L, p.amountMinor); assertEquals("4455", p.cardLast4); assertEquals(EntryType.EXPENSE, p.type)
        assertEquals(Category.TRANSPORT, p.category); assertTrue(p.confident)
    }
    @Test fun transliteratedPrivatSms() {
        val p = parse("Spysannya 99.00UAH. Karta *1111 Netflix. Bal. 500.00UAH", "privat")!!
        assertEquals(9900L, p.amountMinor); assertEquals(EntryType.EXPENSE, p.type)
    }
    @Test fun incomeKeywordTransliterated() {
        assertEquals(EntryType.INCOME, parse("Zarakhuvannya 2000.00UAH na kartku *1234", "privat")!!.type)
    }
    @Test fun titleIsGenericSoMerchantComesFromBody() {
        val p = parse("Оплата\n320 грн, магазин: Аптека Доброго Дня")!!
        assertEquals(32000L, p.amountMinor); assertEquals(Category.HEALTH, p.category)
    }
    @Test fun merchantMissingStillImportsWithBlankMerchant() {
        val p = parse("Списано 77,00 грн")!!
        assertEquals(7700L, p.amountMinor); assertTrue(p.confident); assertEquals("", p.merchant)
    }
    @Test fun conversionAmountIsNotSecondPurchase() {
        val p = parse("Оплата 10.00 USD (412,50 грн) у Steam")!!
        assertEquals(1000L, p.amountMinor); assertEquals(LedgerCurrency.USD, p.currency); assertTrue(p.confident)
    }
    @Test fun twoDifferentAmountsAreNotConfident() {
        assertFalse(parse("Оплата 100 грн та 250 грн у АТБ")!!.confident)
    }
    @Test fun refundIsIncome() { assertEquals(EntryType.INCOME, parse("Повернення оплати 250 грн АТБ")!!.type) }
    @Test fun transferDirections() {
        assertEquals(EntryType.INCOME, parse("Переказ від Олена К. 300 грн")!!.type)
        assertEquals(EntryType.EXPENSE, parse("Переказ на картку 300 грн")!!.type)
        assertTrue(parse("Переказ на картку 300 грн")!!.transfer)
    }
    @Test fun adsAndCashbackOnlyAreIgnored() {
        assertNull(parse("Кредит до 50 000 грн за акцією!"))
        assertNull(parse("Кешбек 15 грн нараховано"))
        assertNull(parse("Ваш ліміт 20 000 грн"))
        assertNull(parse("Hello! Your card is ready"))
    }
    @Test fun thousandSeparators() {
        assertEquals(120050L, parse("Оплата 1,200.50 грн")!!.amountMinor)
        assertEquals(120050L, parse("Оплата 1.200,50 грн")!!.amountMinor)
        assertEquals(1200000L, parse("Оплата 12 000 грн")!!.amountMinor)
    }
    @Test fun clockOnlyUsesTodayNotFuture() {
        val p = parse("Оплата 10 грн Кава 22:50")!!
        assertTrue(p.timeFromMessage)
        assertTrue(p.happenedAtMillis <= at + 300_000L)
    }
    @Test fun walletAndBankPackagesAreRecognised() {
        assertEquals("privat", Banks.byPackage("ua.privatbank.ap24")?.id)
        assertEquals("mono", Banks.byPackage("com.ftband.mono")?.id)
        assertEquals("wallet", Banks.byPackage("com.google.android.apps.walletnfcrel")?.id)
        assertEquals("sense", Banks.byPackage("ua.alfabank.mobile.android.beta")?.id)
        assertNull(Banks.byPackage("org.telegram.messenger"))
        assertNull(Banks.byPackage("com.whatsapp"))
    }

    @Test fun senderMatchingIsLenient() {
        assertEquals("privat", Banks.bySender("PRIVATBANK")?.id)
        assertEquals("privat", Banks.bySender("Privat24")?.id)
        assertEquals("mono", Banks.bySender("monobank")?.id)
        assertEquals("sense", Banks.bySender("SENSE BANK")?.id)
        assertNull(Banks.bySender("+380501234567"))
        assertNull(Banks.bySender("Mom"))
    }
}
