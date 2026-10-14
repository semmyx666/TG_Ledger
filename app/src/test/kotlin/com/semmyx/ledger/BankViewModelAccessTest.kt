package com.semmyx.ledger
import androidx.lifecycle.ViewModelStore
import com.semmyx.ledger.bank.BankViewModel
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.lang.reflect.Proxy
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BankViewModelAccessTest {
    @get:Rule val main = MainDispatcherRule()
    @Test fun freeQueriesOnlyTodayAndExpiredPremiumCannotKeepThirtyDays() = runTest {
        val now = LocalDate.of(2026,10,14)
        var from: LocalDate? = null
        val repo = Proxy.newProxyInstance(BankRepository::class.java.classLoader,arrayOf(BankRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getDiagnostics" -> MutableStateFlow(ImportDiagnostics())
                "getMonobankStatus", "getPrivatStatus" -> MutableStateFlow(MonobankStatus())
                "getEnabled", "getAutoImport" -> true
                "getImportMode" -> "NOTIFY"
                "sources" -> { from = args!![1] as LocalDate; flowOf(emptyList<BankSourceSpend>()) }
                "categorySpending" -> flowOf(emptyList<BankCategorySpend>())
                "recent" -> flowOf(emptyList<LedgerEntry>())
                "categories" -> flowOf(emptyList<CustomBankCategory>())
                "reviews" -> flowOf(emptyList<ImportReview>())
                else -> error("Unexpected method ${method.name}")
            }
        } as BankRepository
        val preferences = Proxy.newProxyInstance(PreferencesRepository::class.java.classLoader,arrayOf(PreferencesRepository::class.java)) { _, _, _ -> MutableStateFlow(UserPreferences()) } as PreferencesRepository
        val access = MutableStateFlow(PremiumState())
        val premium = object : PremiumRepository {
            override val state = access
            override suspend fun refresh() {}
            override suspend fun activateDeveloperToken(token: String) = false
            override suspend fun developerInstallationId() = "test"
            override fun clearIssue() {}
        }
        val model = BankViewModel(repo,preferences,premium,Clock.fixed(now.atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC))
        val store = ViewModelStore().apply { put("bank",model) }
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        try {
            model.period("MONTH"); runCurrent()
            assertEquals("DAY",model.state.value.filter.period); assertEquals(now,from)
            access.value = PremiumState(entitled=true, source=PremiumSource.TRIAL); runCurrent()
            assertEquals(now.minusDays(29),from); assertEquals("MONTH",model.state.value.filter.period)
            access.value = PremiumState(entitled=false); runCurrent()
            assertEquals("DAY",model.state.value.filter.period); assertEquals(now,from)
            model.period("ALL"); runCurrent(); assertEquals(now,from)
        } finally { collector.cancel(); store.clear() }
    }
}
