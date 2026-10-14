package com.semmyx.ledger.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.UiMessage
import com.semmyx.ledger.common.guarded
import com.semmyx.ledger.voice.VoiceEntryDraft
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

data class DashboardState(
    val preferences: UserPreferences = UserPreferences(),
    val snapshot: DashboardSnapshot = DashboardSnapshot(),
    val query: String = "",
    val type: EntryType? = null,
    val days: Int = 7,
    val premium: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val canLoadMore: Boolean = false,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val selectedCategory: Category? = null,
    val categoryEntries: List<LedgerEntry> = emptyList(),
    val categoryCanLoadMore: Boolean = false,
)
sealed interface DashboardEvent {
    data class Deleted(val token: DeletionToken) : DashboardEvent
    data class Message(val message: UiMessage) : DashboardEvent
}
private data class QuerySpec(val query: String = "", val type: EntryType? = null, val limit: Int = 50, val days: Int = 7, val retry: Int = 0, val category: Category? = null, val categoryLimit: Int = 50)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val entries: EntryRepository,
    preferences: PreferencesRepository,
    premium: PremiumRepository,
    private val observe: ObserveDashboard,
    private val clock: Clock,
    private val saveEntry: SaveEntry,
) : ViewModel() {
    private val spec = MutableStateFlow(QuerySpec())
    private val eventChannel = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    // Re-evaluate the window across local midnight without restarting the app.
    private val today = flow {
        while (true) {
            emit(LocalDate.now(clock))
            val next = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
            delay((next - clock.millis()).coerceAtLeast(1000))
        }
    }.distinctUntilChanged()
    val state = combine(preferences.preferences, premium.state, spec, today) { prefs, access, query, date ->
        DashboardRequest(prefs, access.entitled, query, date)
    }.flatMapLatest { request ->
        val window = if (request.premium) request.spec.days else 7
        val filter = EntryFilter(request.prefs.currency, request.spec.query, request.spec.type, request.spec.limit + 1)
        val from = FinanceWindow.from(window, request.today)
        val categoryRows = request.spec.category?.let { category ->
            entries.observeEntries(EntryFilter(request.prefs.currency, limit = request.spec.categoryLimit + 1,
                category = category, from = from, through = request.today))
        } ?: flowOf(emptyList<LedgerEntry>())
        combine(observe(filter, from, request.today), entries.observeCategoryTotals(request.prefs.currency, from, request.today), categoryRows) { snapshot, totals, rows ->
                DashboardState(request.prefs, snapshot.copy(entries = snapshot.entries.take(request.spec.limit)),
                    request.spec.query, request.spec.type, window, request.premium, loading = false,
                    canLoadMore = snapshot.entries.size > request.spec.limit, categoryTotals = totals,
                    selectedCategory = request.spec.category, categoryEntries = rows.take(request.spec.categoryLimit),
                    categoryCanLoadMore = rows.size > request.spec.categoryLimit)
            }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(DashboardState(preferences = request.prefs, query = request.spec.query, type = request.spec.type,
                    days = window, premium = request.premium, loading = false, failed = true))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun search(query: String) { spec.update { it.copy(query = query.take(120), limit = 50) } }
    fun filter(type: EntryType?) { spec.update { it.copy(type = type, limit = 50) } }
    fun days(value: Int) { if (value in listOf(0, 7, 30)) spec.update { it.copy(days = value, categoryLimit = 50) } }
    fun loadMore() { spec.update { it.copy(limit = if (it.limit < Int.MAX_VALUE - 51) it.limit + 50 else Int.MAX_VALUE - 1) } }
    fun category(value: Category?) { spec.update { it.copy(category = value, categoryLimit = 50) } }
    fun loadMoreCategory() { spec.update { it.copy(categoryLimit = if (it.categoryLimit < Int.MAX_VALUE - 51) it.categoryLimit + 50 else Int.MAX_VALUE - 1) } }
    fun retry() { spec.update { it.copy(retry = it.retry + 1) } }
    fun delete(id: String) {
        guarded(onError = { eventChannel.trySend(DashboardEvent.Message(UiMessage.STORAGE)) }) {
            entries.delete(id)?.let { eventChannel.send(DashboardEvent.Deleted(it)) }
        }
    }
    fun undo(token: DeletionToken) {
        guarded(onError = { eventChannel.trySend(DashboardEvent.Message(UiMessage.STORAGE)) }) {
            if (!entries.undo(token)) eventChannel.send(DashboardEvent.Message(UiMessage.UNDO_EXPIRED))
        }
    }
    // Saves an entry parsed from a free-form spoken sentence (see VoiceEntryParser). The current
    // display currency is attached here rather than in the parser, since the parser has no access
    // to user preferences and money spoken aloud is always assumed to be in the user's own currency.
    fun addVoiceEntry(draft: VoiceEntryDraft, sourceType: String = "UNKNOWN") {
        guarded(onError = { eventChannel.trySend(DashboardEvent.Message(UiMessage.STORAGE)) }) {
            saveEntry(LedgerEntry(
                id = UUID.randomUUID().toString(),
                title = draft.title,
                amountMinor = draft.amountMinor,
                currency = state.value.preferences.currency,
                type = draft.type,
                category = draft.category,
                date = LocalDate.now(clock),
                note = draft.note,
                sourceType = sourceType,
            ))
        }
    }
    suspend fun answerStatistics(request: com.semmyx.ledger.voice.VoiceFinanceQuery.Statistics): Long {
        val now = LocalDate.now(clock)
        return entries.statistics(state.value.preferences.currency, request.from(now), now, request.type, request.category)
    }
    private data class DashboardRequest(val prefs: UserPreferences, val premium: Boolean, val spec: QuerySpec, val today: LocalDate)
}
