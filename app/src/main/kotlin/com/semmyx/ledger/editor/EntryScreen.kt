package com.semmyx.ledger.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryRoute(model: EntryViewModel, language: AppLanguage, onBack: () -> Unit, customCategories: List<com.semmyx.ledger.domain.bank.CustomBankCategory> = emptyList()) {
    val state by model.state.collectAsStateWithLifecycle()
    var dateDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<EditorState?>(null) }
    LaunchedEffect(state.loading) { if (!state.loading && initial == null) initial = state }
    LaunchedEffect(model) { model.saved.collect { onBack() } }
    fun changed(): Boolean = initial?.let { old ->
        old.title != state.title || old.amount != state.amount || old.type != state.type || old.category != state.category || old.date != state.date || old.note != state.note || old.sourceType != state.sourceType || old.bankId != state.bankId || old.customCategoryId != state.customCategoryId
    } ?: false
    fun attemptBack() { if (!state.busy) { if (changed()) discardDialog = true else onBack() } }
    BackHandler { attemptBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (state.editing) R.string.edit_entry else R.string.add_entry)) }, navigationIcon = {
        IconButton(onClick = ::attemptBack, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }) }) { padding ->
        if (state.loading) LoadingState(Modifier.padding(padding))
        else if (state.loadFailed) EmptyState(stringResource(R.string.load_error_title), stringResource(state.error?.label() ?: R.string.storage_error),
            stringResource(R.string.back), onBack, Modifier.padding(padding))
        else Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.entry_currency_notice, state.currency.name), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.tertiary), selected = state.type == EntryType.EXPENSE, onClick = { model.type(EntryType.EXPENSE) }, enabled = !state.busy,
                    label = { Text(stringResource(R.string.expense)) })
                FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.secondary), selected = state.type == EntryType.INCOME, onClick = { model.type(EntryType.INCOME) }, enabled = !state.busy,
                    label = { Text(stringResource(R.string.income)) })
            }
            OutlinedTextField(value = state.amount, onValueChange = model::amount, label = { Text(stringResource(R.string.amount)) },
                suffix = { Text(state.currency.name) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validation == ValidationIssue.AMOUNT, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("entryAmount"),
                supportingText = { if (state.validation == ValidationIssue.AMOUNT) Text(stringResource(R.string.amount_invalid)) })
            OutlinedTextField(value = state.title, onValueChange = model::title, label = { Text(stringResource(R.string.entry_title)) }, singleLine = true,
                isError = state.validation == ValidationIssue.TITLE, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("entryTitle"),
                supportingText = { Text(stringResource(if (state.validation == ValidationIssue.TITLE) R.string.title_invalid else R.string.title_hint)) })
            Text(stringResource(R.string.bank_source), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.sourceType == "CASH", onClick = { model.source("CASH") }, label = { Text(stringResource(R.string.bank_cash)) })
                FilterChip(selected = state.sourceType == "CARD", onClick = { model.source("CARD") }, label = { Text(stringResource(R.string.bank_card)) })
            }
            if (state.sourceType == "CARD") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.semmyx.ledger.domain.bank.Banks.all.forEach { bank ->
                        FilterChip(selected = state.bankId == bank.id, onClick = { model.bank(bank.id) }, label = { Text(bank.name) })
                    }
                }
            }
            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAppearance.current.category(category, state.type == EntryType.INCOME).copy(alpha = .18f), selectedLabelColor = LocalAppearance.current.category(category, state.type == EntryType.INCOME)), selected = state.customCategoryId == null && state.category == category, onClick = { model.category(category) }, enabled = !state.busy,
                        label = { Text(stringResource(category.label())) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                customCategories.forEach { category ->
                    FilterChip(selected = state.customCategoryId == category.id, onClick = { model.customCategory(category.id) },
                        label = { Text(category.name) })
                }
            }
            OutlinedButton(onClick = { dateDialog = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.CalendarMonth, null)
                Spacer(Modifier.width(12.dp))
                Text(formatDate(state.date, language))
            }
            if (state.validation == ValidationIssue.DATE) Text(stringResource(R.string.date_invalid), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(value = state.note, onValueChange = model::note, label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !state.busy,
                supportingText = { Text(stringResource(R.string.character_count, state.note.length, 240)) })
            state.error?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
            Button(onClick = model::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("saveEntry")) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save_entry))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (discardDialog) AlertDialog(onDismissRequest = { discardDialog = false }, title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = { TextButton(onClick = { discardDialog = false; onBack() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discardDialog = false }) { Text(stringResource(R.string.keep_editing)) } })
    if (dateDialog) {
        val today = LocalDate.now()
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 2000..today.year, selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val value = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return value.year >= 2000 && !value.isAfter(today)
                }
                override fun isSelectableYear(year: Int): Boolean = year in 2000..today.year
            })
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { model.date(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                dateDialog = false
            }) { Text(stringResource(R.string.done)) }
        }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text(stringResource(R.string.cancel)) } }) {
            DatePicker(state = picker)
        }
    }
}
