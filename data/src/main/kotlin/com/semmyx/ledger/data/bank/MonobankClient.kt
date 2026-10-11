package com.semmyx.ledger.data.bank

import com.google.gson.JsonParser
import com.semmyx.ledger.domain.LedgerCurrency
import com.semmyx.ledger.domain.bank.MonobankMapper
import com.semmyx.ledger.domain.bank.MonobankStatementItem
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal client for monobank's personal API (https://api.monobank.ua/docs/). Read-only; the token is
 * sent only to this host in the `X-Token` header. Blocking calls: run on a background dispatcher.
 */
class MonobankClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://api.monobank.ua",
) {
    data class Account(val id: String, val currency: LedgerCurrency, val type: String, val cardLast4: String?)

    class ApiException(val httpCode: Int, message: String) : IOException(message) {
        val invalidToken get() = httpCode == 403 || httpCode == 401
        val rateLimited get() = httpCode == 429
    }

    private fun get(path: String, token: String): String {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path).header("X-Token", token).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.code, "monobank HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    fun accounts(token: String): List<Account> {
        val root = JsonParser.parseString(get("/personal/client-info", token)).asJsonObject
        return root.getAsJsonArray("accounts")?.mapNotNull { element ->
            val a = element.asJsonObject
            val currency = MonobankMapper.currency(a.get("currencyCode")?.asInt ?: return@mapNotNull null) ?: return@mapNotNull null
            val type = a.get("type")?.asString.orEmpty()
            if (type == "eAid") return@mapNotNull null // state-aid wallets are not spending accounts
            val mask = a.getAsJsonArray("maskedPan")?.firstOrNull()?.asString
            Account(a.get("id").asString, currency, type, mask?.takeLast(4)?.takeIf { it.all(Char::isDigit) })
        }.orEmpty()
    }

    fun statement(token: String, accountId: String, fromSeconds: Long, toSeconds: Long): List<MonobankStatementItem> {
        val array = JsonParser.parseString(get("/personal/statement/$accountId/$fromSeconds/$toSeconds", token)).asJsonArray
        return array.mapNotNull { element ->
            val o = element.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            MonobankStatementItem(
                id = id,
                timeSeconds = o.get("time")?.asLong ?: return@mapNotNull null,
                description = o.get("description")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                mcc = o.get("mcc")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                amount = o.get("amount")?.asLong ?: return@mapNotNull null,
                comment = o.get("comment")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            )
        }
    }
}
