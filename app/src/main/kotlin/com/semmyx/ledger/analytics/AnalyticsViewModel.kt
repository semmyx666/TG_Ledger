package com.semmyx.ledger.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AnalyticsFocus(val type: EntryType, val category: Category? = null)
data class AnalyticsState(
    val preferences: UserPreferences = UserPreferences(),
    val month: YearMonth? = null,
    val totals: List<CategoryTotal> = emptyList(),
    val buckets: List<CashBucket> = emptyList(),
    val previousExpenseMinor: Long? = null,
    val focus: AnalyticsFocus? = null,
    val transactions: List<LedgerEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val expenseMinor: Long get() = totals.sumOf { it.expenseMinor }
    val incomeMinor: Long get() = totals.sumOf { it.incomeMinor }
}
private data class AnalyticsSpec(val month: YearMonth?, val focus: AnalyticsFocus? = null, val limit: Int = 50, val retry: Int = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(private val repository: EntryRepository, preferences: PreferencesRepository,
    private val clock: Clock) : ViewModel() {
    private val spec = MutableStateFlow(AnalyticsSpec(YearMonth.now(clock)))
    private val today = flow {
        while (true) {
            val day = LocalDate.now(clock); emit(day)
            delay((day.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli() - clock.millis()).coerceAtLeast(1000))
        }
    }
    val state = combine(preferences.preferences, spec, today) { prefs, request, day -> Triple(prefs, request, day) }
        .flatMapLatest { (prefs, request, day) ->
            val from = request.month?.atDay(1) ?: FinanceWindow.EARLIEST
            val through = minOf(request.month?.atEndOfMonth() ?: day, day)
            val previous = request.month?.minusMonths(1)
            // For the current month compare equal elapsed days, not partial month vs full past month.
            val previousThrough = previous?.let { if (request.month == YearMonth.from(day))
                it.atDay(day.dayOfMonth.coerceAtMost(it.lengthOfMonth())) else it.atEndOfMonth() }
            val comparison = if (previous != null && previousThrough != null)
                repository.observeCategoryTotals(prefs.currency, previous.atDay(1), previousThrough).map { rows -> rows.sumOf { it.expenseMinor } }
                else flowOf<Long?>(null)
            val detail = request.focus?.let { focus -> repository.observeEntries(EntryFilter(prefs.currency,
                type = focus.type, limit = request.limit + 1, category = focus.category, from = from, through = through)) }
                ?: flowOf(emptyList<LedgerEntry>())
            combine(repository.observeCategoryTotals(prefs.currency, from, through),
                repository.observeActivity(prefs.currency, from, through), comparison, detail) { totals, activity, prior, entries ->
                AnalyticsState(prefs, request.month, totals, AnalyticsMath.buckets(activity, request.month, through), prior,
                    request.focus, entries.take(request.limit), entries.size > request.limit, loading = false)
            }.catch { e ->
                if (e is CancellationException) throw e
                emit(AnalyticsState(preferences = prefs, month = request.month, focus = request.focus, loading = false, failed = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState(month = YearMonth.now(clock)))
    fun month(value: YearMonth?) { if (value == null || value in YearMonth.of(2000, 1)..YearMonth.now(clock)) spec.update { it.copy(month = value, limit = 50) } }
    fun focus(value: AnalyticsFocus?) { spec.update { it.copy(focus = value, limit = 50) } }
    fun more() { spec.update { it.copy(limit = if (it.limit < Int.MAX_VALUE - 51) it.limit + 50 else Int.MAX_VALUE - 1) } }
    fun retry() { spec.update { it.copy(retry = it.retry + 1) } }
}
