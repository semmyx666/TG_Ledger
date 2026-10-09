package com.semmyx.ledger.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.UiMessage
import com.semmyx.ledger.presentation.common.guarded
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditorState(
    val editing: Boolean,
    val loading: Boolean = true,
    val title: String = "",
    val amount: String = "",
    val currency: LedgerCurrency = LedgerCurrency.UAH,
    val type: EntryType = EntryType.EXPENSE,
    val category: Category = Category.OTHER,
    val date: LocalDate,
    val note: String = "",
    val busy: Boolean = false,
    val validation: ValidationIssue? = null,
    val error: UiMessage? = null,
    val loadFailed: Boolean = false,
    val sourceType: String = "CASH",
    val bankId: String? = null,
    val cardLast4: String? = null,
    val customCategoryId: String? = null,
    val eventAtMillis: Long? = null,
    val origin: String = "MANUAL",
)

class EntryViewModel(
    entryId: String?,
    private val repository: EntryRepository,
    preferences: PreferencesRepository,
    private val saveEntry: SaveEntry,
    private val clock: Clock,
) : ViewModel() {
    private val id = entryId ?: UUID.randomUUID().toString()
    private val mutableState = MutableStateFlow(EditorState(editing = entryId != null, date = LocalDate.now(clock)))
    val state = mutableState.asStateFlow()
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()
    init {
        guarded(onError = { mutableState.update { it.copy(loading = false, error = UiMessage.STORAGE, loadFailed = true) } }) {
            if (entryId == null) {
                val prefs = preferences.preferences.first()
                mutableState.update { it.copy(currency = prefs.currency, loading = false) }
            } else {
                val entry = repository.find(entryId)
                mutableState.value = if (entry == null) state.value.copy(loading = false, error = UiMessage.NOT_FOUND, loadFailed = true)
                else EditorState(true, false, entry.title, Money.editable(entry.amountMinor), entry.currency,
                    entry.type, entry.category, entry.date, entry.note, sourceType = entry.sourceType, bankId = entry.bankId,
                    cardLast4 = entry.cardLast4, customCategoryId = entry.customCategoryId, eventAtMillis = entry.eventAtMillis, origin = entry.origin)
            }
        }
    }
    fun title(value: String) { mutableState.update { it.copy(title = value.take(60), validation = null) } }
    fun amount(value: String) { mutableState.update { it.copy(amount = value.take(12), validation = null) } }
    fun type(value: EntryType) { mutableState.update { it.copy(type = value) } }
    fun category(value: Category) { mutableState.update { it.copy(category = value, customCategoryId = null) } }
    fun customCategory(id: String) { mutableState.update { it.copy(category = Category.OTHER, customCategoryId = id) } }
    fun source(value: String) { require(value in listOf("UNKNOWN", "CASH", "CARD")); mutableState.update { it.copy(sourceType = value, bankId = if (value == "CARD") it.bankId ?: "privat" else null, cardLast4 = if (value == "CARD") it.cardLast4 else null) } }
    fun bank(id: String) { mutableState.update { it.copy(bankId = id, sourceType = "CARD") } }
    fun date(value: LocalDate) { mutableState.update { it.copy(date = value, eventAtMillis = value.atStartOfDay(clock.zone).toInstant().toEpochMilli(), validation = null) } }
    fun note(value: String) { mutableState.update { it.copy(note = value.take(240), validation = null) } }
    fun save() {
        if (state.value.busy || state.value.loading || state.value.loadFailed) return
        val form = state.value
        val minor = Money.parseMinor(form.amount)
        if (minor == null) { mutableState.update { it.copy(validation = ValidationIssue.AMOUNT) }; return }
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                saveEntry(LedgerEntry(id, form.title, minor, form.currency, form.type, form.category, form.date, form.note, form.sourceType, form.bankId, form.cardLast4,
                    form.eventAtMillis ?: if (form.date == LocalDate.now(clock)) clock.millis() else form.date.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                    form.customCategoryId, form.origin))
                savedChannel.send(Unit)
            } catch (error: EntryValidationException) {
                mutableState.update { it.copy(validation = error.issue) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(error = UiMessage.STORAGE) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
