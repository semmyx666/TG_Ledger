package com.semmyx.ledger.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.EntryType
import com.semmyx.ledger.domain.bank.Banks
import com.semmyx.ledger.common.*
import com.semmyx.ledger.theme.LocalAppearance

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(model: HistoryViewModel, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val palette = LocalAppearance.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.cinematic_history)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(model.month?.toString() ?: stringResource(R.string.all_time)) }
            item { OutlinedTextField(state.request.query, model::search, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.history_search)) }) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.request.type == null, onClick = { model.type(null) }, label = { Text(stringResource(R.string.all)) })
                EntryType.entries.forEach { type ->
                    FilterChip(selected = state.request.type == type, onClick = { model.type(type) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = palette.type(type), selectedLabelColor = palette.type(type)),
                        label = { Text(stringResource(if (type == EntryType.INCOME) R.string.income else R.string.expenses)) })
                }
            } }
            if (state.loading) item { LoadingState() }
            else if (state.failed) item { Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error) }
            else if (state.rows.isEmpty()) item { Text(stringResource(R.string.category_empty)) }
            items(state.rows, key = { it.id }) { entry -> ContentCard(Modifier.clickable { onEdit(entry.id) }) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(formatMoney(if (entry.type == EntryType.EXPENSE) -entry.amountMinor else entry.amountMinor, entry.currency, state.preferences.language), color = palette.type(entry.type))
                Text(formatDate(entry.date, state.preferences.language))
                Text(when (entry.sourceType) { "CASH" -> stringResource(R.string.bank_cash); "CARD" -> Banks.name(entry.bankId); else -> stringResource(R.string.bank_unknown) })
            } }
            if (state.hasMore) item { OutlinedButton(onClick = model::more, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) } }
        }
    }
}
