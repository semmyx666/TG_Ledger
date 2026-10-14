package com.semmyx.ledger.data.bank

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Privat24 Business AutoClient. GET statements only; not the API for retail Privat24 cards. */
class PrivatBankClient(
    private val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(45, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://acp.privatbank.ua",
) {
    data class Operation(val key: String, val parsed: ParsedBankOperation)
    class ApiException(val code: Int) : IOException("PrivatBank request failed ($code)")

    fun statement(token: String, from: LocalDate, through: LocalDate): List<Operation> {
        require(!through.isBefore(from))
        val result = mutableListOf<Operation>()
        var cursor: String? = null
        val cursors = mutableSetOf<String>()
        // Pagination is bounded; a broken/repeated cursor cannot loop forever or silently truncate.
        repeat(100) {
            val url = (baseUrl.trimEnd('/') + "/api/statements/transactions").toHttpUrl().newBuilder()
                .addQueryParameter("startDate", from.format(DateTimeFormatter.ofPattern("dd-MM-uuuu")))
                .addQueryParameter("endDate", through.format(DateTimeFormatter.ofPattern("dd-MM-uuuu")))
                .addQueryParameter("limit", "100")
            cursor?.let { url.addQueryParameter("followId", it) }
            val request = Request.Builder().url(url.build()).get().header("token", token)
                .header("User-Agent", "TG-Ledger/3.3.3")
                .header("Content-Type", "application/json;charset=utf8").header("Accept", "application/json").build()
            val root = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code)
                JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            }
            if (root.string("status") != "SUCCESS") throw ApiException(422)
            root.getAsJsonArray("transactions")?.forEach { row -> map(row.asJsonObject)?.let(result::add) }
            val more = root.string("exist_next_page").equals("true", ignoreCase = true)
            if (!more) return result.distinctBy { it.key }
            cursor = root.string("next_page_id").takeIf { it.isNotBlank() } ?: throw ApiException(422)
            if (!cursors.add(cursor!!)) throw ApiException(422)
        }
        throw ApiException(422)
    }

    companion object {
        private fun JsonObject.string(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        fun map(row: JsonObject): Operation? {
            if (row.string("PR_PR") != "r" || row.string("FL_REAL") != "r") return null
            val type = when (row.string("TRANTYPE")) { "D" -> EntryType.EXPENSE; "C" -> EntryType.INCOME; else -> return null }
            val currency = runCatching { LedgerCurrency.valueOf(row.string("CCY")) }.getOrNull() ?: return null
            val minor = runCatching { BigDecimal(row.string("SUM")).abs().movePointRight(2).longValueExact() }.getOrNull() ?: return null
            if (minor <= 0 || minor > Money.MAX_ENTRY_MINOR) return null
            val ref = row.string("REF"); val refn = row.string("REFN"); val account = row.string("AUT_MY_ACC")
            if (ref.isBlank() || refn.isBlank() || account.isBlank()) return null
            val timestamp = runCatching {
                LocalDateTime.parse(row.string("DATE_TIME_DAT_OD_TIM_P"),
                    DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss").withResolverStyle(ResolverStyle.STRICT))
                    .atZone(ZoneId.of("Europe/Kyiv")).toInstant().toEpochMilli()
            }.getOrNull() ?: return null
            val merchant = row.string("AUT_CNTR_NAM").take(60).ifBlank { "ПриватБанк" }
            val purpose = row.string("OSND")
            val transfer = Regex("між власними|власн.*рахунк|между своими|own accounts", RegexOption.IGNORE_CASE).containsMatchIn(purpose)
            val category = if (transfer) Category.TRANSFER else BankParser.categorize(merchant)
            return Operation("$account|$ref|$refn", ParsedBankOperation("privat", minor, currency, type,
                merchant, null, timestamp, true, transfer, !transfer, category))
        }
    }
}
