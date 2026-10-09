package com.semmyx.ledger.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.analytics.*

/** Reference-style category cards on the MAIN screen, not hidden in a separate analytics route. */
@Composable
fun CategorySummary(state: DashboardState, onCategory: (Category, EntryType) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { type ->
            val slices = AnalyticsMath.slices(state.categoryTotals, type)
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), color = LocalAppearance.current.type(type))
                        TextButton(onClick = onMore) { Text(stringResource(R.string.cinematic_more), color = CinematicTokens.Accent) }
                    }
                    if (slices.isEmpty()) Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        slices.take(4).forEach { slice ->
                            val tint = CinematicTokens.category(slice.category, type == EntryType.INCOME)
                            Column(Modifier.weight(1f).clickable { onCategory(slice.category, type) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(48.dp).background(tint.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(slice.category.icon(), stringResource(slice.category.label()), tint = tint)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor,
                                    state.preferences.currency, state.preferences.language, showCurrency = false, positiveSign = type == EntryType.INCOME),
                                    color = if (type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryDetails(state: DashboardState, initialType: EntryType, onClose: () -> Unit, onDays: (Int) -> Unit,
    onMore: () -> Unit, onEdit: (String) -> Unit, onSelectCategory: (Category) -> Unit) {
    val category = state.selectedCategory ?: return
    var type by remember(initialType) { mutableStateOf(initialType) }
    val slices = AnalyticsMath.slices(state.categoryTotals, type)
    val total = slices.sumOf { it.minor }
    val selected = slices.find { it.category == category }
    val period = stringResource(when (state.days) { 0 -> R.string.all_time; 30 -> R.string.last_30_days; else -> R.string.last_7_days })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
                TopAppBar(title = { Text(stringResource(if (type == EntryType.EXPENSE) R.string.expenses else R.string.income)) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface))
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7 to R.string.last_7_days, 30 to R.string.last_30_days, 0 to R.string.all_time).forEach { (value, title) ->
                                FilterChip(selected = state.days == value, enabled = value == 7 || state.premium,
                                    onClick = { onDays(value) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.primary, labelColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    label = { Text(stringResource(title), maxLines = 1, style = MaterialTheme.typography.labelMedium) })
                            }
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(EntryType.EXPENSE to R.string.expenses, EntryType.INCOME to R.string.income).forEach { (choice, title) ->
                                    Surface(modifier = Modifier.weight(1f), color = if (type == choice) LocalAppearance.current.type(choice).copy(alpha = .18f) else Color.Transparent,
                                        shape = RoundedCornerShape(11.dp), onClick = { type = choice }) {
                                        Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                                            Text(stringResource(title), color = LocalAppearance.current.type(choice).copy(alpha = if (type == choice) 1f else .78f))
                                        }
                                    }
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(when (state.days) {
                                0 -> R.string.total_all_time
                                30 -> R.string.total_thirty_days
                                else -> R.string.total_seven_days
                            }), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            Text(formatReferenceMoney(if (type == EntryType.INCOME) total else -total, state.preferences.currency,
                                state.preferences.language, positiveSign = type == EntryType.INCOME), style = MaterialTheme.typography.headlineSmall,
                                color = if (type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                        }
                        PremiumDonut(slices, type, category, state.preferences.currency, state.preferences.language, onSelectCategory)
                    }
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(if (type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), style = MaterialTheme.typography.titleLarge)
                                slices.forEachIndexed { index, slice ->
                                    val tint = CinematicTokens.category(slice.category, type == EntryType.INCOME)
                                    Row(Modifier.fillMaxWidth().clickable { onSelectCategory(slice.category) }.padding(vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(Modifier.size(46.dp).background(tint.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(slice.category.icon(), null, tint = tint)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(slice.category.label()))
                                            Text(stringResource(R.string.category_transaction_count, slice.count), style = MaterialTheme.typography.bodySmall, color = CinematicTokens.Muted)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor, state.preferences.currency,
                                                state.preferences.language, showCurrency = false, positiveSign = type == EntryType.INCOME), color = LocalAppearance.current.type(type))
                                            Text(percent(slice.basisPoints, state.preferences.language), color = CinematicTokens.Muted)
                                        }
                                    }
                                    if (index != slices.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                    item {
                        Text(stringResource(category.label()), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.category_transaction_count, selected?.count ?: 0), color = CinematicTokens.Muted)
                    }
                    items(state.categoryEntries.filter { it.type == type }, key = { it.id }) { entry ->
                        Card(onClick = { onClose(); onEdit(entry.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.title); Text(formatDate(entry.date, state.preferences.language), color = CinematicTokens.Muted)
                                Text(formatReferenceMoney(if (type == EntryType.INCOME) entry.amountMinor else -entry.amountMinor, entry.currency,
                                    state.preferences.language, positiveSign = type == EntryType.INCOME), color = LocalAppearance.current.type(type))
                                if (entry.note.isNotBlank()) Text(entry.note, color = CinematicTokens.Muted)
                            }
                        }
                    }
                    if (state.categoryCanLoadMore) item { OutlinedButton(onClick = onMore) { Text(stringResource(R.string.load_more)) } }
                }
            }
        }
    }
}
