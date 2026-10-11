package com.semmyx.ledger.data.bank

import com.semmyx.ledger.domain.LedgerCurrency
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MonobankClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: MonobankClient
    @Before fun setUp() { server = MockWebServer().also { it.start() }; client = MonobankClient(baseUrl = server.url("/").toString()) }
    @After fun tearDown() { server.shutdown() }

    @Test fun accountsAreParsedAndTokenIsSentAsHeader() {
        server.enqueue(MockResponse().setBody("""{"clientId":"c","name":"N","accounts":[
            {"id":"acc1","currencyCode":980,"type":"black","maskedPan":["537541******1234"],"balance":1000},
            {"id":"acc2","currencyCode":840,"type":"white","maskedPan":[],"balance":5},
            {"id":"acc3","currencyCode":980,"type":"eAid","maskedPan":[]},
            {"id":"acc4","currencyCode":392,"type":"black","maskedPan":[]}]}"""))
        val accounts = client.accounts("secret-token")
        assertEquals(listOf("acc1", "acc2"), accounts.map { it.id })
        assertEquals("1234", accounts[0].cardLast4); assertNull(accounts[1].cardLast4)
        assertEquals(LedgerCurrency.USD, accounts[1].currency)
        val request = server.takeRequest()
        assertEquals("/personal/client-info", request.path); assertEquals("secret-token", request.getHeader("X-Token"))
    }

    @Test fun statementRowsAreParsed() {
        server.enqueue(MockResponse().setBody("""[{"id":"s1","time":1700000000,"description":"Сільпо","mcc":5411,"amount":-12550,"balance":1,"currencyCode":980},
            {"id":"s2","time":1700000100,"description":null,"mcc":4829,"amount":50000}]"""))
        val rows = client.statement("t", "acc1", 1699000000, 1700000200)
        assertEquals(2, rows.size)
        assertEquals(-12550L, rows[0].amount); assertEquals("Сільпо", rows[0].description); assertEquals(5411, rows[0].mcc)
        assertEquals("", rows[1].description)
        assertEquals("/personal/statement/acc1/1699000000/1700000200", server.takeRequest().path)
    }

    @Test fun errorsCarryTheHttpCode() {
        server.enqueue(MockResponse().setResponseCode(403))
        val bad = try { client.accounts("x"); null } catch (e: MonobankClient.ApiException) { e }
        assertTrue(bad!!.invalidToken)
        server.enqueue(MockResponse().setResponseCode(429))
        val slow = try { client.statement("x", "a", 1, 2); null } catch (e: MonobankClient.ApiException) { e }
        assertTrue(slow!!.rateLimited)
    }
}
