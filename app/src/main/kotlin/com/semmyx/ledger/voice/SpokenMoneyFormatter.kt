package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Explicit words for minus and currency: TTS must never depend on pronunciation of '-' or ₴. */
object SpokenMoneyFormatter {
    fun format(minor: Long, currency: LedgerCurrency, language: AppLanguage): String {
        val uk = language == AppLanguage.UKRAINIAN
        val value = BigDecimal.valueOf(minor, 2).abs()
        val number = NumberFormat.getNumberInstance(Locale.forLanguageTag(language.tag)).apply {
            isGroupingUsed = false; maximumFractionDigits = 2
        }.format(value)
        val unit = when (currency) {
            LedgerCurrency.UAH -> if (uk) "гривень" else "hryvnias"
            LedgerCurrency.USD -> if (uk) "доларів" else "US dollars"
            LedgerCurrency.EUR -> if (uk) "євро" else "euros"
            LedgerCurrency.GBP -> if (uk) "фунтів" else "pounds"
            LedgerCurrency.PLN -> if (uk) "злотих" else "zlotys"
        }
        return (if (minor < 0) if (uk) "мінус " else "minus " else "") + number + " " + unit
    }
}
