package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

enum class PaymentSource { UNKNOWN, CASH, CARD }

/**
 * [packages] are exact Android package names; [packageHints] are fragments that identify the same app
 * when the vendor ships it under a different id (regional builds, beta channels).
 */
data class Bank(
    val id: String, val name: String, val packages: Set<String>, val senders: Set<String>,
    val packageHints: Set<String> = emptySet(),
)

object Banks {
    const val WALLET_ID = "wallet"
    val all = listOf(
        Bank("privat", "ПриватБанк", setOf("ua.privatbank.ap24", "com.privatbank.ap24", "com.pb"), setOf("privatbank", "privat", "privat24", "3700"), setOf("privatbank", "ap24")),
        Bank("mono", "monobank", setOf("com.ftband.mono"), setOf("monobank", "mono"), setOf("ftband", "monobank")),
        Bank("sense", "Sense Bank", setOf("ua.alfabank.mobile.android", "ua.sensebank.mobile.android"), setOf("sensebank", "sense", "alfabank"), setOf("alfabank", "sensebank")),
        Bank("abank", "А-Банк", setOf("ua.com.abank", "ua.com.abank.android"), setOf("abank", "a-bank", "а-банк"), setOf("abank")),
        Bank("oschad", "Ощадбанк", setOf("ua.oschadbank.online"), setOf("oschadbank", "oschad", "ощадбанк"), setOf("oschad")),
        Bank("pumb", "ПУМБ", setOf("com.fuib.android.spot.online"), setOf("pumb", "fuib", "пумб"), setOf("fuib", "pumb")),
        Bank("revolut", "Revolut", setOf("com.revolut.revolut"), setOf("revolut"), setOf("revolut")),
        Bank("wise", "Wise", setOf("com.transferwise.android"), setOf("wise", "transferwise"), setOf("transferwise")),
        Bank("n26", "N26", setOf("de.number26.android"), setOf("n26"), setOf("number26")),
        // Wallet apps announce the same tap as the issuing bank; the pair is collapsed by cross-source deduplication.
        Bank("other", "Інший банк / Other bank", emptySet(), emptySet()),
        Bank(WALLET_ID, "Google Wallet", setOf("com.google.android.apps.walletnfcrel", "com.samsung.android.spay"), emptySet(), setOf("walletnfcrel")),
    )
    fun byPackage(value: String): Bank? =
        all.firstOrNull { value in it.packages } ?: all.firstOrNull { bank -> bank.packageHints.any { value.contains(it, ignoreCase = true) } }
    /** SMS sender ids vary ("PRIVATBANK", "Privat24", "monobank", "SENSE BANK"): exact match, else a name fragment (4+ chars). */
    fun bySender(value: String): Bank? {
        val sender = value.trim().lowercase(Locale.ROOT)
        if (sender.isEmpty()) return null
        return all.firstOrNull { sender in it.senders }
            ?: all.firstOrNull { bank -> bank.senders.any { it.length >= 4 && sender.contains(it) } }
    }
    fun name(id: String?) = all.firstOrNull { it.id == id }?.name ?: id.orEmpty()
}

data class ParsedBankOperation(val bankId: String, val amountMinor: Long, val currency: LedgerCurrency,
    val type: EntryType, val merchant: String, val cardLast4: String?, val happenedAtMillis: Long,
    val timeFromMessage: Boolean, val transfer: Boolean, val confident: Boolean, val category: Category)

/**
 * Pure offline parser for bank push / SMS texts (Ukrainian, Russian, English and transliterated).
 *
 * A message becomes an operation only when it carries an amount **and** a clear direction (sign or
 * keyword). OTP codes, declines, ads, balance-only texts and conversion amounts are ignored.
 * `confident` means: single amount, known direction -> safe to save without asking.
 */
object BankParser {
    private const val NUM = "(?:[0-9]{1,3}(?:[ .,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)"
    private const val WORD_CUR = "(?:UAH|USD|EUR|GBP|PLN|грн\\.?|гривень|гривні|гривня|zł|zl)(?![\\p{L}])"
    private const val SYMBOL_CUR = "[₴$€£]"
    private val suffixAmount = Regex("([+-])?\\s*($NUM)\\s*($SYMBOL_CUR|$WORD_CUR)", RegexOption.IGNORE_CASE)
    private val prefixAmount = Regex("(?<![\\p{L}])($SYMBOL_CUR|UAH|USD|EUR|GBP|PLN)\\s*([+-])?\\s*($NUM)", RegexOption.IGNORE_CASE)

    private val nonTransactionAmount = Regex(
        "(?:баланс|бал\\.?|залишок|остаток|доступн\\w*|available|balance|bal\\.?|ліміт|лимит|limit|кредитн\\w*|кешбек\\w*|кэшбэк\\w*|cashback|комісі\\w*|комиссия|fee|commission|до сплати|мін\\w*\\s+платіж|минимальный платеж)\\s*[:=-]?\\s*$",
        RegexOption.IGNORE_CASE)
    private val refund = Regex("(?:повернен|возврат|refund|reversal|скасуванн|відміна оплати)", RegexOption.IGNORE_CASE)
    private val expense = Regex(
        "(?:списан|потрат|потрач|сплачен|розрахува|знят[оті]|зняття|снят|витрат|видатк|оплат|покупк|купівл|куплен|платіж|платеж|purchase|paid|payment|spent|debit|withdraw|charged|відправлен|sent|spysan|spisan|snyat|znyat|oplat|splat|pokupk|kupivl|platizh)",
        RegexOption.IGNORE_CASE)
    private val income = Regex(
        "(?:зарахован|зарахув|поповнен|пополнен|надходжен|зачислен|отримано|получен|received|credited|top[ -]?up|deposit|salary|зарплат|zarahov|zarakhov|zarahuv|zarakhuv|zachisl|popovnen|popolnen|nadkhodzh|nadhodzh)",
        RegexOption.IGNORE_CASE)
    private val transfer = Regex("(?:переказ|перевод|transfer|p2p|perekaz|perevod)", RegexOption.IGNORE_CASE)
    private val transferIn = Regex("(?:від\\s|от\\s|from\\s|отримано|получен|received|зарахован|зачислен)", RegexOption.IGNORE_CASE)
    private val transferOut = Regex("(?:\\sна\\s|\\sto\\s|відправлен|отправлен|sent|списан|знят)", RegexOption.IGNORE_CASE)
    private val otp = Regex("(?:одноразов|one[ -]?time|verification code|код\\s+(?:входу|підтвердження|подтверждения|безпеки)|otp|пароль|password|cvv)", RegexOption.IGNORE_CASE)
    private val declined = Regex("(?:відхилен|отклонен|declined|failed|невдал|недостатньо|insufficient|не вдалос|заблоковано)", RegexOption.IGNORE_CASE)
    private val promo = Regex("(?:кредит до|кредит на|до \\d[\\d ]*\\s*(?:грн|₴)\\s+(?:на|під|без)|акці[яї]|знижк|пропозиці|промо|promo|розіграш|виграй|вигра|реклам|оформи|отримай)", RegexOption.IGNORE_CASE)
    private val card = Regex("(?:карт[аикоуі]*|card|[＊*•·]{1,})\\s*[:№#*•·.-]*\\s*([0-9]{4})(?![0-9])", RegexOption.IGNORE_CASE)
    private val label = Regex("(?:merchant|магазин|мерчант|торговець|торгова точка|місце|место|place|details|description|опис|описание|призначення|назначение)\\s*[:=]\\s*([^;\\n]+)", RegexOption.IGNORE_CASE)
    private val preposition = Regex("(?<![\\p{L}\\p{N}])(?:at|у|в|in)\\s+([^;\\n,]+)", RegexOption.IGNORE_CASE)
    private val dateTime = Regex("([0-9]{2}[./][0-9]{2}[./][0-9]{4})[ ,T]+([0-9]{2}:[0-9]{2}(?::[0-9]{2})?)")
    private val clockOnly = Regex("(?<![0-9:.])([0-9]{1,2}:[0-9]{2})(?![0-9:])")
    private val trailer = Regex(
        "\\s*(?:[;|•·]|(?:баланс|залишок|остаток|бал\\.|balance|bal\\.|карт[аикоуі]*|card|рахунок|доступно)(?![\\p{L}\\p{N}])|[0-9]{2}[./][0-9]{2}[./][0-9]{2,4}|[0-9]{1,2}:[0-9]{2}|[＊*•]{1,}\\s*[0-9]{4}).*$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val genericTitle = Regex(
        "(?:оплата|покупка|купівля|списання|списано|зарахування|зараховано|поповнення|переказ|платіж|транзакці\\w*|нова транзакція|операці\\w*|витрата|надходження|payment|purchase|transaction|new transaction|transfer|debit|credit|card payment|оплата карткою|apple pay|google pay|google wallet|samsung pay|карт[аиок]*|card|\\p{P}|\\s)+",
        RegexOption.IGNORE_CASE)
    private val emoji = Regex("[\\p{So}\\p{Cs}\\u200d\\uFE0F]+")

    private data class Hit(val range: IntRange, val minor: Long, val currency: LedgerCurrency, val sign: Char?)

    private fun normalize(text: String) = text
        .replace(Regex("[\\u2212\\u2012\\u2013\\u2014\\u2796\\uFE63\\uFF0D]"), "-")
        .replace(Regex("[\\u00A0\\u202F\\u2009\\u2007\\u200B]"), " ")
        .replace("\r", "")

    private fun currencyOf(token: String): LedgerCurrency? = when (token.trim().trimEnd('.').uppercase(Locale.ROOT)) {
        "USD", "$" -> LedgerCurrency.USD
        "EUR", "€" -> LedgerCurrency.EUR
        "GBP", "£" -> LedgerCurrency.GBP
        "PLN", "ZŁ", "ZL" -> LedgerCurrency.PLN
        "UAH", "₴", "ГРН", "ГРИВЕНЬ", "ГРИВНІ", "ГРИВНЯ" -> LedgerCurrency.UAH
        else -> null
    }

    /** "1 200,50" "1,200.50" "1200.5" "1.200,50" -> minor units, or null. */
    internal fun minorOf(raw: String): Long? {
        var value = raw.replace(" ", "")
        val lastComma = value.lastIndexOf(','); val lastDot = value.lastIndexOf('.')
        value = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) value.replace(".", "").replace(',', '.') else value.replace(",", "")
            lastComma >= 0 || lastDot >= 0 -> {
                val sep = if (lastComma >= 0) ',' else '.'
                val parts = value.split(sep)
                if (parts.size > 2) value.replace(sep.toString(), "")
                else if (parts[1].length == 3 && parts[0].length in 1..3 && parts[0] != "0") parts[0] + parts[1]
                else parts[0] + "." + parts[1]
            }
            else -> value
        }
        return try { BigDecimal(value).abs().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() } catch (_: Exception) {
            try { BigDecimal(value).abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() } catch (_: Exception) { null }
        }
    }

    private fun hits(text: String): List<Hit> {
        val found = mutableListOf<Hit>()
        suffixAmount.findAll(text).forEach { m ->
            val minor = minorOf(m.groupValues[2]) ?: return@forEach
            val currency = currencyOf(m.groupValues[3]) ?: return@forEach
            found += Hit(m.range, minor, currency, m.groupValues[1].firstOrNull())
        }
        prefixAmount.findAll(text).forEach { m ->
            if (found.any { it.range.first <= m.range.last && m.range.first <= it.range.last }) return@forEach
            val minor = minorOf(m.groupValues[3]) ?: return@forEach
            val currency = currencyOf(m.groupValues[1]) ?: return@forEach
            found += Hit(m.range, minor, currency, m.groupValues[2].firstOrNull())
        }
        return found.sortedBy { it.range.first }.map { hit ->
            // The sign may sit just before the currency token ("$-12.50") or before a gap ("- 100").
            if (hit.sign != null) hit else {
                val before = text.substring(maxOf(0, hit.range.first - 2), hit.range.first).trimEnd()
                hit.copy(sign = before.lastOrNull()?.takeIf { it == '-' || it == '+' })
            }
        }
    }

    fun parse(bank: Bank, rawText: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): ParsedBankOperation? {
        if (rawText.length > 8000) return null
        val text = normalize(rawText)
        if (otp.containsMatchIn(text) || declined.containsMatchIn(text)) return null

        val primary = hits(text).filter { hit ->
            val prefix = text.substring(maxOf(0, hit.range.first - 28), hit.range.first)
            !nonTransactionAmount.containsMatchIn(prefix)
        }
        val first = primary.firstOrNull() ?: return null
        // Other amounts in the same currency are ambiguous; other currencies are conversions of the same payment.
        val ambiguous = primary.drop(1).any { it.currency == first.currency && it.minor != first.minor }
        if (first.minor <= 0 || first.minor > Money.MAX_ENTRY_MINOR) return null

        val isRefund = refund.containsMatchIn(text)
        val debit = expense.containsMatchIn(text) && !isRefund
        val credit = income.containsMatchIn(text) || isRefund
        val moving = transfer.containsMatchIn(text)
        val signed = first.sign
        if (!debit && !credit && !moving && signed == null) return null
        if (promo.containsMatchIn(text) && signed == null && !debit && !credit) return null

        var directionKnown = true
        val type: EntryType = when {
            signed == '+' -> EntryType.INCOME
            signed == '-' -> EntryType.EXPENSE
            isRefund -> EntryType.INCOME
            credit && !debit -> EntryType.INCOME
            debit && !credit -> EntryType.EXPENSE
            debit && credit -> { directionKnown = false; if ((income.find(text)?.range?.first ?: Int.MAX_VALUE) < (expense.find(text)?.range?.first ?: Int.MAX_VALUE)) EntryType.INCOME else EntryType.EXPENSE }
            moving && transferIn.containsMatchIn(text) && !transferOut.containsMatchIn(text) -> EntryType.INCOME
            moving && transferOut.containsMatchIn(text) && !transferIn.containsMatchIn(text) -> EntryType.EXPENSE
            else -> { directionKnown = false; EntryType.EXPENSE }
        }

        val shop = merchant(bank, text, first)
        val timestamp = timestamp(text, receivedAt, zone)
        val category = when {
            moving -> Category.TRANSFER
            type == EntryType.INCOME && Regex("зарплат|salary", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Category.SALARY
            else -> categorize(shop)
        }
        return ParsedBankOperation(
            bank.id, first.minor, first.currency, type, shop, card.find(text)?.groupValues?.getOrNull(1),
            timestamp ?: receivedAt, timestamp != null, moving, directionKnown && !ambiguous, category,
        )
    }

    private fun timestamp(text: String, receivedAt: Long, zone: ZoneId): Long? {
        dateTime.find(text)?.let { match ->
            try {
                val date = LocalDate.parse(match.groupValues[1].replace('/', '.'), DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT))
                val clock = LocalTime.parse(match.groupValues[2])
                return date.atTime(clock).atZone(zone).toInstant().toEpochMilli().takeIf { it <= receivedAt + 300_000L && it >= receivedAt - 366L * 86400000L }
            } catch (_: Exception) { return null }
        }
        // "HH:mm" alone: take today's date if that moment is not in the future (otherwise yesterday).
        clockOnly.find(text)?.let { match ->
            try {
                val clock = LocalTime.parse(match.groupValues[1].padStart(5, '0'))
                val today = Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
                var moment = today.atTime(clock).atZone(zone).toInstant().toEpochMilli()
                if (moment > receivedAt + 300_000L) moment -= 86_400_000L
                return moment.takeIf { receivedAt - moment <= 6 * 3_600_000L }
            } catch (_: Exception) { return null }
        }
        return null
    }

    private fun clean(value: String): String {
        var out = value.replace(emoji, " ")
        out = out.replace(trailer, "")
        out = out.trim(' ', ':', '-', '+', '.', ',', '"', '\'', '«', '»', '(', ')', '|')
        return out.replace(Regex("\\s{2,}"), " ").take(60)
    }

    private fun merchant(bank: Bank, text: String, first: Hit): String {
        label.find(text)?.groupValues?.getOrNull(1)?.let(::clean)?.takeIf { it.length >= 2 }?.let { return it }
        val badStart = Regex("^(?:карт|card|рахунок|счет|account|[0-9]|[＊*•])", RegexOption.IGNORE_CASE)
        val after = text.substring(minOf(text.length, first.range.last + 1))
        preposition.findAll(after).map { clean(it.groupValues[1]) }
            .firstOrNull { it.length >= 2 && !badStart.containsMatchIn(it) && !genericTitle.matches(it) }?.let { return it }
        preposition.findAll(text).map { clean(it.groupValues[1]) }
            .firstOrNull { it.length >= 2 && !badStart.containsMatchIn(it) && !genericTitle.matches(it) }?.let { return it }
        // Many apps put the shop into the notification title (first line) and the amount into the body.
        val noAmounts = suffixAmount.replace(prefixAmount.replace(text, ""), "")
        val keywords = Regex("\\p{L}*(?:${expense.pattern}|${income.pattern}|${transfer.pattern})\\p{L}*", RegexOption.IGNORE_CASE)
        return noAmounts.lines().asSequence()
            .map { line -> clean(line.replace(card, " ").replace(keywords, " ")) }
            .firstOrNull { line ->
                line.length in 2..60 && !line.equals(bank.name, ignoreCase = true) && !genericTitle.matches(line) &&
                    !nonTransactionAmount.containsMatchIn(line) && !line.all { it.isDigit() || it.isWhitespace() || it in ".,:*-" } &&
                    !Regex("^(?:баланс|залишок|остаток|balance|bal)", RegexOption.IGNORE_CASE).containsMatchIn(line)
            }.orEmpty()
    }

    fun categorize(merchant: String): Category {
        val value = merchant.lowercase(Locale.ROOT)
        return when {
            listOf("атб", "atb", "сільпо", "silpo", "ашан", "auchan", "novus", "метро", "metro", "varus", "варус", "фора", "fora", "lidl", "biedronka", "продукт", "супермаркет", "grocery", "market", "маркет", "ева ", "eva ").any(value::contains) -> Category.FOOD
            listOf("wog", "okko", "ugo", "uber", "bolt", "uklon", "укрзалізниц", "ukrzaliznytsia", "азс", "shell", "socar", "паливо", "taxi", "таксі").any(value::contains) -> Category.TRANSPORT
            listOf("steam", "megogo", "netflix", "spotify", "cinema", "кіно", "youtube", "playstation", "xbox", "multiplex").any(value::contains) -> Category.LEISURE
            listOf("аптек", "pharmacy", "clinic", "клінік", "подорожник", "анц", "лікарн").any(value::contains) -> Category.HEALTH
            listOf("restaurant", "ресторан", "кафе", "cafe", "mcdonald", "kfc", "pizza", "піц", "sushi", "суші", "coffee", "кав", "starbucks", "burger", "бургер").any(value::contains) -> Category.DINING
            listOf("епіцентр", "epicentr", "jysk", "ikea", "комунал", "жкг", "kyivstar", "київстар", "vodafone", "lifecell", "інтернет").any(value::contains) -> Category.HOME
            else -> Category.OTHER
        }
    }
}
