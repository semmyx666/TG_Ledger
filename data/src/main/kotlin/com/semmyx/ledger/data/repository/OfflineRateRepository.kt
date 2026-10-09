package com.semmyx.ledger.data.repository

import com.semmyx.ledger.data.local.RateDao
import com.semmyx.ledger.data.local.RateEntity
import com.semmyx.ledger.data.remote.RateApi
import com.semmyx.ledger.domain.*
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineRateRepository(private val dao: RateDao, private val api: RateApi, private val clock: Clock) : RateRepository {
    private val refreshLock = Mutex()
    override val rates = dao.observe().map { rows -> rows.map {
        ExchangeRate(LedgerCurrency.valueOf(it.currency), it.uahPerUnit, LocalDate.ofEpochDay(it.epochDay), it.fetchedAtMillis)
    } }
    override suspend fun refresh() = refreshLock.withLock {
        val fetchedAt = clock.millis()
        val allowed = LedgerCurrency.entries.map { it.name }.toSet() - "UAH"
        val rows = api.latest().mapNotNull { dto ->
            val code = dto.currency?.takeIf { it in allowed } ?: return@mapNotNull null
            val value = dto.rate?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            val date = try { LocalDate.parse(dto.exchangeDate, DateTimeFormatter.ofPattern("dd.MM.yyyy")) }
                catch (_: Exception) { return@mapNotNull null }
            RateEntity(code, value, date.toEpochDay(), fetchedAt)
        }
        // Invalid or partial payloads must not destroy a known-good offline cache.
        if (rows.map { it.currency }.toSet() != allowed) throw IOException("Incomplete rate response")
        dao.upsert(rows)
    }
}
