package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PaymentSource { UNKNOWN, CASH, CARD }
data class Bank(val id: String, val name: String, val packages: Set<String>, val senders: Set<String>)
object Banks {
    val all = listOf(
        Bank("privat", "ПриватБанк", setOf("com.pb", "com.privatbank.ap24"), setOf("privatbank", "privat", "privat24", "3700")),
        Bank("mono", "monobank", setOf("com.ftband.mono"), setOf("monobank", "mono")),
        Bank("sense", "Sense Bank", setOf("ua.alfabank.mobile.android", "ua.sensebank.mobile.android"), setOf("sensebank", "sense", "alfabank")),
        Bank("abank", "А-Банк", setOf("ua.com.abank", "ua.com.abank.android"), setOf("abank", "a-bank", "а-банк")),
        Bank("oschad", "Ощадбанк", setOf("ua.oschadbank.online"), setOf("oschadbank", "oschad", "ощадбанк")),
        Bank("pumb", "ПУМБ", setOf("com.fuib.android.spot.online"), setOf("pumb", "fuib", "пумб")),
        Bank("revolut", "Revolut", setOf("com.revolut.revolut"), setOf("revolut")),
        Bank("wise", "Wise", setOf("com.transferwise.android"), setOf("wise", "transferwise")),
        Bank("n26", "N26", setOf("de.number26.android"), setOf("n26"))
    )
    fun byPackage(value: String) = all.firstOrNull { value in it.packages }
    fun bySender(value: String) = all.firstOrNull { value.trim().lowercase(Locale.ROOT) in it.senders }
    fun name(id: String?) = all.firstOrNull { it.id == id }?.name ?: id.orEmpty()
}
data class ParsedBankOperation(val bankId: String, val amountMinor: Long, val currency: LedgerCurrency,
    val type: EntryType, val merchant: String, val cardLast4: String?, val happenedAtMillis: Long,
    val timeFromMessage: Boolean, val transfer: Boolean, val confident: Boolean, val category: Category)

/** Pure offline parser. OTP/ads/refusals are ignored; ambiguous texts never become automatic expenses. */
object BankParser {
    private val amount = Regex("([+-]?[0-9]{1,3}(?:[ \\u00A0\\u202F][0-9]{3})+(?:[.,][0-9]{1,2})?|[+-]?[0-9]+(?:[.,][0-9]{1,2})?)\\s*(UAH|USD|EUR|грн\\.?|гривень|гривні|₴|\\$|€)", RegexOption.IGNORE_CASE)
    private val amountPrefix = Regex("(UAH|USD|EUR|\\$|€|₴)\\s*([+-]?[0-9]+(?:[.,][0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val expense = Regex("(?:списан[оі]?|витрат[аи]|оплат[аи]|покупк[аи]|купівл[яі]|purchase|paid|payment|spent|debit(?:ed)?)", RegexOption.IGNORE_CASE)
    private val income = Regex("(?:зарахован[оі]?|поповнення|пополнение|надходження|зачислен[оа]?|received|credited|top[ -]?up|refund|повернення)", RegexOption.IGNORE_CASE)
    private val transfer = Regex("(?:переказ|перевод|transfer|p2p)", RegexOption.IGNORE_CASE)
    private val otp = Regex("(?:одноразов|one[ -]?time|verification code|код\\s+(?:входу|підтвердження|подтверждения)|otp|пароль|password)", RegexOption.IGNORE_CASE)
    private val declined = Regex("(?:відхилен|отклонен|declined|failed|невдал|недостатньо|insufficient)", RegexOption.IGNORE_CASE)
    private val card = Regex("(?:карт[аикоуі]*|card|[＊*•]{2,})\\s*[:№#*•-]*\\s*([0-9]{4})(?![0-9])", RegexOption.IGNORE_CASE)
    private val merchant = Regex("(?:merchant|магазин|мерчант|торговець|at|у|в)\\s*[:=-]?\\s+([^;\\n]+)", RegexOption.IGNORE_CASE)
    private val time = Regex("([0-9]{2}[./][0-9]{2}[./][0-9]{4})[ ,T]+([0-9]{2}:[0-9]{2}(?::[0-9]{2})?)")
    fun parse(bank: Bank, text: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): ParsedBankOperation? {
        if (text.length > 8000 || otp.containsMatchIn(text) || declined.containsMatchIn(text)) return null
        val matches = amount.findAll(text).filter { found ->
            val prefix = text.substring(maxOf(0, found.range.first - 35), found.range.first)
            !Regex("(?:баланс|залишок|остаток|balance|available|ліміт|limit)\\s*[:=-]?\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix)
        }.toList()
        val prefixes = amountPrefix.findAll(text).filter { found ->
            val prefix = text.substring(maxOf(0, found.range.first - 35), found.range.first)
            !Regex("(?:баланс|залишок|остаток|balance|available|ліміт|limit)\\s*[:=-]?\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix)
        }.toList()
        if (matches.isEmpty() && prefixes.isEmpty()) return null
        val debit = expense.containsMatchIn(text); val credit = income.containsMatchIn(text); val moving = transfer.containsMatchIn(text)
        if (!debit && !credit && !moving && !text.contains(Regex("[+-]\\s*[0-9]"))) return null
        val selected = matches.firstOrNull()
        val rawAmount = (selected?.groupValues?.get(1) ?: prefixes.first().groupValues[2]).replace(Regex("[ \\u00A0\\u202F]"), "").replace(',', '.')
        val money = try { BigDecimal(rawAmount).abs().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() } catch (_: Exception) { return null }
        if (money <= 0 || money > Money.MAX_ENTRY_MINOR) return null
        val cur = (selected?.groupValues?.get(2) ?: prefixes.first().groupValues[1]).uppercase(Locale.ROOT)
        val currency = when (cur) { "USD", "$" -> LedgerCurrency.USD; "EUR", "€" -> LedgerCurrency.EUR; else -> LedgerCurrency.UAH }
        val type = if (credit && !debit || rawAmount.startsWith("+")) EntryType.INCOME else EntryType.EXPENSE
        var shop = merchant.find(text)?.groupValues?.getOrNull(1)?.let {
            it.replace(Regex("(?:баланс|залишок|balance|карт[аикоуі]*|card|[0-9]{2}[./][0-9]{2}[./][0-9]{4}).*$", RegexOption.IGNORE_CASE), "").trim().take(60)
        }.orEmpty()
        if (shop.isBlank()) {
            val excluded = Regex("(?:баланс|залишок|остаток|balance|available|карт[аикоуі]*|card|[0-9]{2}:[0-9]{2})", RegexOption.IGNORE_CASE)
            shop = text.lines().map { line -> amount.replace(amountPrefix.replace(line, ""), "").trim(' ', ':', '-', '+') }
                .firstOrNull { line -> line.length in 2..60 && !line.equals(bank.name, ignoreCase = true) && !excluded.containsMatchIn(line) &&
                    !expense.matches(line) && !income.matches(line) && !transfer.matches(line) && !line.all { it.isDigit() || it.isWhitespace() } }.orEmpty()
        }
        val timestamp = time.find(text)?.let { match ->
            try {
                val date = LocalDate.parse(match.groupValues[1].replace('/', '.'), DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))
                val clock = LocalTime.parse(match.groupValues[2])
                date.atTime(clock).atZone(zone).toInstant().toEpochMilli().takeIf { it <= receivedAt + 300_000L && it >= receivedAt - 366L * 86400000L }
            } catch (_: Exception) { null }
        }
        return ParsedBankOperation(bank.id, money, currency, type, shop, card.find(text)?.groupValues?.getOrNull(1),
            timestamp ?: receivedAt, timestamp != null, moving, matches.size + prefixes.size == 1 && debit != credit && !moving && shop.isNotBlank() && (merchant.containsMatchIn(text) || categorize(shop) != Category.OTHER), categorize(shop))
    }
    fun categorize(merchant: String): Category {
        val value = merchant.lowercase(Locale.ROOT)
        return when {
            listOf("атб", "atb", "сільпо", "silpo", "ашан", "auchan", "novus", "metro").any(value::contains) -> Category.FOOD
            listOf("wog", "okko", "ugo", "uber", "bolt", "uklon").any(value::contains) -> Category.TRANSPORT
            listOf("steam", "megogo", "netflix", "spotify", "cinema").any(value::contains) -> Category.LEISURE
            listOf("аптек", "pharmacy", "clinic").any(value::contains) -> Category.HEALTH
            listOf("restaurant", "кафе", "cafe", "mcdonald", "kfc").any(value::contains) -> Category.DINING
            else -> Category.OTHER
        }
    }
}
