package com.semmyx.ledger.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import java.time.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class HistoryRequest(val query: String = "", val type: EntryType? = null, val limit: Int = 50)
data class HistoryState(val preferences: UserPreferences = UserPreferences(), val request: HistoryRequest = HistoryRequest(),
    val rows: List<LedgerEntry> = emptyList(), val hasMore: Boolean = false, val failed: Boolean = false, val loading: Boolean = true)
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(repository: EntryRepository, preferences: PreferencesRepository, clock: Clock, val month: YearMonth?) : ViewModel() {
    private val request = MutableStateFlow(HistoryRequest())
    val state = combine(preferences.preferences, request) { prefs, input -> prefs to input }.flatMapLatest { (prefs, input) ->
        val today = LocalDate.now(clock)
        val from = month?.atDay(1) ?: LocalDate.of(2000, 1, 1)
        val through = month?.atEndOfMonth()?.let { if (it < today) it else today } ?: today
        repository.observeEntries(EntryFilter(prefs.currency, input.query, input.type, input.limit + 1, from = from, through = through))
            .map { rows -> HistoryState(prefs, input, rows.take(input.limit), rows.size > input.limit, loading = false) }
            .catch { emit(HistoryState(prefs, input, failed = true, loading = false)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())
    fun search(value: String) { request.update { it.copy(query = value.take(80), limit = 50) } }
    fun type(value: EntryType?) { request.update { it.copy(type = value, limit = 50) } }
    fun more() { request.update { it.copy(limit = (it.limit + 50).coerceAtMost(100000)) } }
}
