package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*

/** One row of monobank's personal `statement` API ([amount] is signed minor units of the account currency). */
data class MonobankStatementItem(
    val id: String, val timeSeconds: Long, val description: String, val mcc: Int,
    val amount: Long, val comment: String = "",
)

/** Pure mapping of monobank API rows to ledger operations. No network, no Android. */
object MonobankMapper {
    fun currency(code: Int): LedgerCurrency? = when (code) {
        980 -> LedgerCurrency.UAH; 840 -> LedgerCurrency.USD; 978 -> LedgerCurrency.EUR
        826 -> LedgerCurrency.GBP; 985 -> LedgerCurrency.PLN; else -> null
    }

    fun map(item: MonobankStatementItem, accountCurrency: LedgerCurrency, cardLast4: String?): ParsedBankOperation? {
        if (item.id.isBlank() || item.amount == 0L) return null
        val minor = Math.abs(item.amount)
        if (minor > Money.MAX_ENTRY_MINOR) return null
        val description = item.description.replace(Regex("\\s+"), " ").trim().take(60)
        val transfer = item.mcc == 4829 || description.startsWith("Від:", true) || description.startsWith("До:", true) ||
            description.startsWith("From:", true) || description.startsWith("To:", true)
        val type = if (item.amount > 0) EntryType.INCOME else EntryType.EXPENSE
        val category = when {
            transfer -> Category.TRANSFER
            else -> byMcc(item.mcc) ?: BankParser.categorize(description)
        }
        return ParsedBankOperation(
            bankId = "mono", amountMinor = minor, currency = accountCurrency, type = type, merchant = description,
            cardLast4 = cardLast4, happenedAtMillis = item.timeSeconds * 1000L, timeFromMessage = true,
            transfer = transfer, confident = true, category = category,
        )
    }

    /** Merchant category codes (ISO 18245) -> app categories; unknown codes fall back to name matching. */
    fun byMcc(mcc: Int): Category? = when (mcc) {
        5411, 5422, 5441, 5451, 5462, 5499, 5300, 5311 -> Category.FOOD
        5811, 5812, 5813, 5814 -> Category.DINING
        4111, 4112, 4121, 4131, 4784, 5541, 5542, 7523, 7512 -> Category.TRANSPORT
        5912, 5122, 8011, 8021, 8031, 8041, 8042, 8043, 8049, 8050, 8062, 8071, 8099 -> Category.HEALTH
        7832, 7841, 7922, 7929, 7932, 7933, 7941, 7991, 7992, 7993, 7994, 7995, 7996, 7997, 7998, 7999, 5815, 5816, 5817, 5818, 5735 -> Category.LEISURE
        4900, 4814, 4899, 5200, 5211, 5251, 5712, 5713, 5719, 7349 -> Category.HOME
        else -> null
    }
}
