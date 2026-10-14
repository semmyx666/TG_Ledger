package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.Category
import com.semmyx.ledger.domain.EntryType
import com.semmyx.ledger.domain.Money

/**
 * Turns a free-form spoken sentence into a ready-to-save ledger entry, so the user can just
 * describe what happened ("I bought 1 kg of tomatoes for 100 UAH") instead of using the small set
 * of fixed voice commands in OptionalVoiceAssistant. Deliberately kept in its own file with no
 * Android/Compose imports, so it stays easy to read, extend with more keywords, and unit-test.
 *
 * This is a best-effort, fully offline, rule-based reader -- not a language model -- so it
 * understands common Ukrainian, Russian, and English money phrasing (many Ukrainian speakers mix
 * Russian words when talking about money, so both are always checked together) but can still
 * misread an unusual sentence. When it can't find any amount at all it returns null so the caller
 * can fall back to the fixed voice commands or a "didn't understand" reply.
 */
data class VoiceEntryDraft(
    val type: EntryType,
    val category: Category,
    val amountMinor: Long,
    /** May be blank; the caller should fall back to the category's display name. */
    val title: String,
    val note: String,
)

object VoiceEntryParser {

    private val CARD_WORDS = Regex("карт|card|безнал|безготівк", RegexOption.IGNORE_CASE)
    private val CASH_WORDS = Regex("готівк|наличн|налом|cash", RegexOption.IGNORE_CASE)

    /** "CARD" / "CASH" when the sentence already says how it was paid; null when the cat must ask. */
    fun detectSource(heard: String): String? {
        val card = CARD_WORDS.containsMatchIn(heard)
        val cash = CASH_WORDS.containsMatchIn(heard)
        return when { card && !cash -> "CARD"; cash && !card -> "CASH"; else -> null }
    }

    fun parse(heard: String): VoiceEntryDraft? {
        val text = heard.trim()
        if (text.isBlank()) return null
        val amountMatch = AMOUNT_WITH_CURRENCY.find(text) ?: run {
            val bareNumbers = BARE_NUMBER.findAll(text).toList()
            if (bareNumbers.isEmpty()) null else bareNumbers.last()
        } ?: return null
        // BARE_NUMBER has no capturing group at all, so its pattern's group count is 0 -- indexing
        // amountMatch.groups[1] directly for that case throws IndexOutOfBoundsException("No group
        // 1") instead of returning null. groupValues.getOrNull(1) checks the list size first (which
        // reflects the *pattern's* group count, 0 there), so it safely returns null without ever
        // touching the missing group.
        val numberText = (amountMatch.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: amountMatch.value)
            .replace(" ", "").replace("\u00A0", "")
        val amountMinor = Money.parseMinor(numberText) ?: return null

        val lower = text.lowercase()
        val transactionIntent = (INCOME_KEYWORDS + EXPENSE_KEYWORDS).any { lower.contains(it) }
        if (!transactionIntent && AMOUNT_WITH_CURRENCY.find(text) == null) return null
        if (listOf("не купив", "не купил", "не витратив", "не потратил", "did not buy", "did not spend").any { it in lower }) return null
        val type = when {
            EXPENSE_KEYWORDS.any { lower.contains(it) } -> EntryType.EXPENSE
            INCOME_KEYWORDS.any { lower.contains(it) } -> EntryType.INCOME
            else -> EntryType.EXPENSE
        }
        val category = CATEGORY_KEYWORDS.firstOrNull { (_, keywords) -> keywords.any { lower.contains(it) } }?.first
            ?: Category.OTHER

        var remainder = text.removeRange(amountMatch.range)
        for (pattern in FILLER_PATTERNS) remainder = pattern.replace(remainder, " ")
        val title = remainder
            .replace(PUNCTUATION, " ")
            .split(WHITESPACE)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .take(60)

        return VoiceEntryDraft(type, category, amountMinor, title, text.take(240))
    }

    // Stems rather than full words on purpose, so a single entry (e.g. "грив") also matches every
    // declined form spoken aloud ("гривень", "гривні", "гривня", "гривнями", ...).
    private val CURRENCY_STEMS = listOf(
        "грн", "грив\\p{L}*", "uah", "\u20B4",
        "usd", "дол\\p{L}*", "\\$",
        "eur", "євро", "евро", "\u20AC",
        "gbp", "фунт\\p{L}*", "\u00A3",
        "pln", "злот\\p{L}*",
    )
    private val AMOUNT_WITH_CURRENCY = Regex(
        "((?:\\d{1,3}(?:[ \u00A0]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?)\\s*(?:" + CURRENCY_STEMS.joinToString("|") + ")",
        RegexOption.IGNORE_CASE,
    )
    private val BARE_NUMBER = Regex("(?:\\d{1,3}(?:[ \u00A0]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?")
    private val PUNCTUATION = Regex("[,.;:!?\u2014\u2013]+")
    private val WHITESPACE = Regex("\\s+")

    private val INCOME_KEYWORDS = listOf(
        // Russian
        "получил", "получила", "получила", "зарплату", "зарплата", "зарплаты", "доход", "поступ", "продал", "продала",
        "аванс", "премия", "кэшбек", "кешбек", "пополнил", "пополнила", "пополнение", "вернули", "возврат", "вернул",
        // Ukrainian
        "отримав", "отримала", "надійшло", "надходження", "поповнення", "поповнив", "поповнила", "зарплату",
        "дохід", "продав", "продала", "премія", "повернення", "повернули",
        // English
        "received", "salary", "income", "paycheck", "got paid", "paid me", "refund", "cashback", "sold", "deposit", "bonus",
    )
    private val EXPENSE_KEYWORDS = listOf(
        // Russian
        "купил", "купила", "потратил", "потратила", "заплатил", "заплатила", "оплатил", "оплатила", "приобрел", "приобрела",
        // Ukrainian
        "купив", "купила", "витратив", "витратила", "заплатив", "заплатила", "оплатив", "оплатила", "придбав", "придбала",
        // English
        "bought", "spent", "purchased", "paid for", "paid",
    )
    private val CATEGORY_KEYWORDS: List<Pair<Category, List<String>>> = listOf(
        Category.CASHBACK to listOf("кешбек", "кэшбек", "cashback"),
        Category.DINING to listOf("ресторан", "restaurant", "кафе", "cafe", "бар", "bar"),
        Category.TRANSFER to listOf("переказ", "перевод", "transfer"),
        Category.SALARY to listOf("зарплат", "заробітн", "оклад", "salary", "paycheck", "аванс", "прем", "bonus"),
        Category.FOOD to listOf(
            "томат", "помідор", "продукт", "їж", "еда", "кава", "coffee", "ресторан", "restaurant",
            "кафе", "cafe", "хліб", "хлеб", "м'яс", "мяс", "овоч", "овощ", "фрукт", "обід", "обед", "вечер",
            "ужин", "завтрак", "снідан", "піцц", "pizza", "grocery", "food", "супермаркет", "магазин",
        ),
        Category.TRANSPORT to listOf(
            "такс", "taxi", "автобус", "bus", "метро", "metro", "бензин", "паливо", "fuel", "gas",
            "проїзд", "проезд", "uber", "bolt", "парковк", "parking", "поїзд", "поезд", "квиток", "билет",
        ),
        Category.HOME to listOf(
            "квартир", "оренд", "аренд", "rent", "комунал", "utilities", "меблі", "мебель", "furniture", "ремонт", "repair",
        ),
        Category.HEALTH to listOf(
            "лікар", "врач", "доктор", "doctor", "аптек", "pharmacy", "лік", "лекарств", "medicine",
            "стоматолог", "dentist", "лікарн", "больниц", "hospital",
        ),
        Category.LEISURE to listOf(
            "кіно", "кино", "movie", "cinema", "ігр", "игр", "game", "розваг", "развлеч", "entertainment",
            "подорож", "путешеств", "travel", "відпочин", "отдых", "vacation", "концерт", "concert", "театр", "theater",
        ),
    )
    private val FILLER_WORDS = (INCOME_KEYWORDS + EXPENSE_KEYWORDS + listOf(
        "привіт", "привет", "hello", "hi", "hey",
        "я", "i", "мені", "мне", "мою", "my",
        "за", "на", "в", "у", "по", "for", "of", "on", "in", "the", "a", "an",
    )).distinct()
    // Explicit Unicode letter/number boundaries keep behavior consistent across Android/JVM.
    private val FILLER_PATTERNS = FILLER_WORDS.map {
        Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])")
    }
}
