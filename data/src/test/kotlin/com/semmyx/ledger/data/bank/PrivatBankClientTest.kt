package com.semmyx.ledger.data.bank
import com.google.gson.JsonParser
import com.semmyx.ledger.domain.*
import okhttp3.mockwebserver.*
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*

class PrivatBankClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: PrivatBankClient
    private val row = """{"AUT_MY_ACC":"UA111111111111111111111111111","AUT_CNTR_NAM":"Сільпо","CCY":"UAH","FL_REAL":"r","PR_PR":"r","SUM":"125.50","REF":"ref1","REFN":"1","TRANTYPE":"D","DATE_TIME_DAT_OD_TIM_P":"14.10.2026 12:01:02","OSND":"Оплата"}"""
    @Before fun before() { server = MockWebServer().also { it.start() }; client = PrivatBankClient(baseUrl = server.url("/").toString()) }
    @After fun after() { server.shutdown() }
    @Test fun readOnlyRequestPaginationAndExactMoney() {
        server.enqueue(MockResponse().setBody("""{"status":"SUCCESS","exist_next_page":true,"next_page_id":"cursor1","transactions":[$row]}"""))
        server.enqueue(MockResponse().setBody("""{"status":"SUCCESS","exist_next_page":false,"transactions":[$row]}"""))
        val rows = client.statement("test-token", LocalDate.of(2026,10,1), LocalDate.of(2026,10,7))
        assertEquals(1, rows.size); assertEquals(12550L, rows[0].parsed.amountMinor)
        assertEquals(EntryType.EXPENSE, rows[0].parsed.type)
        assertEquals(Category.FOOD, rows[0].parsed.category)
        val first = server.takeRequest()
        assertEquals("GET", first.method); assertEquals("test-token", first.getHeader("token"))
        assertEquals("01-10-2026", first.requestUrl!!.queryParameter("startDate"))
        assertEquals("cursor1", server.takeRequest().requestUrl!!.queryParameter("followId"))
    }
    @Test fun onlyBookedRowsAndSupportedCurrenciesAreImported() {
        fun map(text: String) = PrivatBankClient.map(JsonParser.parseString(text).asJsonObject)
        assertNull(map(row.replace("\"PR_PR\":\"r\"", "\"PR_PR\":\"p\"")))
        assertNull(map(row.replace("\"FL_REAL\":\"r\"", "\"FL_REAL\":\"i\"")))
        assertNull(map(row.replace("\"UAH\"", "\"JPY\"")))
        assertNull(map(row.replace("125.50", "0")))
        assertEquals(EntryType.INCOME, map(row.replace("\"D\"", "\"C\""))!!.parsed.type)
        assertNull(map(row.replace("14.10.2026", "31.02.2026")))
    }
    @Test fun repeatedOrMissingCursorFailsRatherThanSilentlyLosingHistory() {
        repeat(2) { server.enqueue(MockResponse().setBody("""{"status":"SUCCESS","exist_next_page":true,"next_page_id":"same","transactions":[]}""")) }
        try { client.statement("test", LocalDate.now(), LocalDate.now()); fail() }
        catch (e: PrivatBankClient.ApiException) { assertEquals(422, e.code) }
    }
    @Test fun badCredentialsAreReported() {
        server.enqueue(MockResponse().setResponseCode(403))
        try { client.statement("test", LocalDate.now(), LocalDate.now()); fail() }
        catch (e: PrivatBankClient.ApiException) { assertEquals(403, e.code) }
    }
}
