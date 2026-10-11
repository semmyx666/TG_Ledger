package com.semmyx.ledger.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BankFilter(val period: String = "MONTH", val source: String? = null, val bank: String? = null)
data class BankDashboardState(val preferences: UserPreferences = UserPreferences(), val filter: BankFilter = BankFilter(),
    val sources: List<BankSourceSpend> = emptyList(), val categories: List<BankCategorySpend> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(), val failed: Boolean = false)
@OptIn(ExperimentalCoroutinesApi::class)
class BankViewModel(private val repo: BankRepository, preferences: PreferencesRepository, private val clock: Clock) : ViewModel() {
    private val filter = MutableStateFlow(BankFilter())
    private val today = MutableStateFlow(LocalDate.now(clock))
    init { viewModelScope.launch {
        while (isActive) {
            val nextDay = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
            delay((nextDay - clock.millis()).coerceAtLeast(1000L))
            today.value = LocalDate.now(clock)
        }
    } }
    val diagnostics = repo.diagnostics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportDiagnostics())
    fun configurePackage(bankId: String, packageName: String) = task { repo.setPackage(bankId, packageName) }
    val customCategories = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reviews = repo.reviews().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val enabled = MutableStateFlow(repo.enabled)
    val mode = MutableStateFlow(repo.importMode)
    val mono = repo.monobankStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonobankStatus())
    fun mode(value: String) { repo.importMode = value; mode.value = value }
    val auto = MutableStateFlow(repo.autoImport)
    val error = MutableStateFlow(false)
    val revision = MutableStateFlow(0)
    val state = combine(preferences.preferences, filter, today) { prefs, selected, date -> Triple(prefs, selected, date) }.flatMapLatest { (prefs, selected, through) ->
        val from = when (selected.period) { "DAY" -> through; "WEEK" -> through.minusDays(6); "ALL" -> LocalDate.of(2000, 1, 1); else -> through.withDayOfMonth(1) }
        combine(repo.sources(prefs.currency, from, through), repo.categorySpending(prefs.currency, from, through, selected.source, selected.bank),
            repo.recent(prefs.currency, from, through, selected.source, selected.bank)) { sources, cats, entries -> BankDashboardState(prefs, selected, sources, cats, entries) }
    }.catch { emit(BankDashboardState(failed = true)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BankDashboardState())
    fun period(value: String) { filter.update { it.copy(period = value) } }
    fun source(value: String?, bank: String? = null) { filter.update { it.copy(source = value, bank = bank) } }
    fun enabled(value: Boolean) { repo.enabled = value; enabled.value = value }
    fun auto(value: Boolean) { repo.autoImport = value; auto.value = value }
    fun accept(id: String, type: EntryType) = task { repo.accept(id, type) }
    fun ignore(id: String) = task { repo.ignore(id) }
    fun createCategory(name: String, hex: String) = task { repo.createCategory(name, hex) }
    fun clearHistory() = task { repo.clearHistory() }
    fun budget(currency: LedgerCurrency) = repo.budget(currency)
    fun resetBudget(currency: LedgerCurrency) = task { repo.setBudget(currency, 0L); revision.value++ }
    fun setBudget(currency: LedgerCurrency, amount: String) = task {
        val parsed = Money.parseMinor(amount) ?: kotlin.error("Invalid amount")
        repo.setBudget(currency, parsed); revision.value++
    }
    private fun task(block: suspend () -> Unit) { viewModelScope.launch { try { block(); error.value = false }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { error.value = true } } }
}
