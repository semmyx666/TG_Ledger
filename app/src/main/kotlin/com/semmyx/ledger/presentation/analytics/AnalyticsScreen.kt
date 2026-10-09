package com.semmyx.ledger.presentation.analytics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun AnalyticsRoute(model: AnalyticsViewModel, onBack: () -> Unit, onCurrency: () -> Unit, onEdit: (String) -> Unit, onHistory: (YearMonth?) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    AnalyticsScreen(state, onBack, onCurrency, model::month, model::focus, model::more, model::retry, onEdit, onHistory = { onHistory(state.month) })
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(state: AnalyticsState, onBack: () -> Unit, onCurrency: () -> Unit,
    onMonth: (YearMonth?) -> Unit, onFocus: (AnalyticsFocus?) -> Unit, onMore: () -> Unit,
    onRetry: () -> Unit, onEdit: (String) -> Unit, onHistory: () -> Unit) {
    var monthPicker by remember { mutableStateOf(false) }
    BackHandler(state.focus != null) { onFocus(null) }
    val locale = Locale.forLanguageTag(state.preferences.language.tag)
    val monthLabel = state.month?.format(DateTimeFormatter.ofPattern("LLLL, yyyy", locale)) ?: stringResource(R.string.all_time)
    if (monthPicker) {
        var draft by remember(state.month) { mutableStateOf(state.month ?: YearMonth.now()) }
        AlertDialog(onDismissRequest = { monthPicker = false }, title = { Text(stringResource(R.string.cinematic_period)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(draft.format(DateTimeFormatter.ofPattern("LLLL, yyyy", locale)))
                    Row {
                        TextButton(onClick = { if (draft > YearMonth.of(2000, 1)) draft = draft.minusMonths(1) }) { Text("‹") }
                        TextButton(onClick = { if (draft < YearMonth.now()) draft = draft.plusMonths(1) }) { Text("›") }
                    }
                    TextButton(onClick = { onMonth(null); monthPicker = false }) { Text(stringResource(R.string.all_time)) }
                }
            }, confirmButton = { TextButton(onClick = { onMonth(draft); monthPicker = false }) { Text(stringResource(R.string.cinematic_apply)) } },
            dismissButton = { TextButton(onClick = { monthPicker = false }) { Text(stringResource(R.string.back)) } })
    }
    Surface(color = CinematicTokens.Background, contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(targetState = state.focus, label = "categoryHero", transitionSpec = {
                fadeIn(tween(380)) togetherWith fadeOut(tween(240))
            }) { focus ->
                val scope = this
                if (focus == null) {
                    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.back)) }
                                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
                                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Icon(Icons.Default.History, null); Text(stringResource(R.string.cinematic_history)) }
                                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .15f), modifier = Modifier.weight(1f)) {
                                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                                Icon(Icons.Default.BarChart, null, tint = CinematicTokens.Accent)
                                                Text(stringResource(R.string.analytics_title), color = CinematicTokens.Accent)
                                            }
                                        }
                                    }
                                }
                            }
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { monthPicker = true }) { Text(monthLabel); Icon(Icons.Default.CalendarMonth, null) }
                                OutlinedButton(onClick = onCurrency) { Text(state.preferences.currency.name); Icon(Icons.Default.Payments, null) }
                            }
                        }
                        if (state.loading) item { LoadingState(Modifier.height(200.dp)) }
                        else if (state.failed) item { EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.storage_error), stringResource(R.string.retry), onRetry) }
                        else {
                            item {
                                CascadeCard(0) {
                                    Text(stringResource(R.string.cinematic_expenses_period, monthLabel), color = CinematicTokens.Muted)
                                    Odometer(-state.expenseMinor, state.preferences.currency, state.preferences.language, color = CinematicTokens.Expense)
                                    state.previousExpenseMinor?.let { previous ->
                                        val delta = state.expenseMinor - previous
                                        Text(stringResource(R.string.cinematic_comparison, formatReferenceMoney(abs(delta), state.preferences.currency, state.preferences.language)),
                                            color = if (delta > 0) CinematicTokens.Expense else CinematicTokens.Income)
                                        Text(stringResource(if (delta > 0) R.string.cinematic_more_than_previous else R.string.cinematic_less_than_previous), color = CinematicTokens.Muted)
                                    }
                                    PremiumBars(state.buckets, state.preferences.currency, state.preferences.language)
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(if (state.month == null) R.string.cinematic_month_average else R.string.cinematic_week_average),
                                            modifier = Modifier.fillMaxWidth(), color = CinematicTokens.Muted, style = MaterialTheme.typography.bodyMedium)
                                        Text(formatReferenceMoney(if (state.buckets.isEmpty()) 0 else state.expenseMinor / state.buckets.size,
                                            state.preferences.currency, state.preferences.language),
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            maxLines = 1, softWrap = false, color = CinematicTokens.Expense,
                                            style = MaterialTheme.typography.headlineSmall)
                                    }
                                }
                            }
                            listOf(EntryType.EXPENSE, EntryType.INCOME).forEachIndexed { index, type ->
                                item(key = type) {
                                    val hero = Modifier.sharedBounds(rememberSharedContentState("focus-$type-all"), scope)
                                    CascadeCard(index + 1, hero) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(stringResource(if (type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), color = LocalAppearance.current.type(type))
                                            TextButton(onClick = { onFocus(AnalyticsFocus(type)) }) { Text(stringResource(R.string.cinematic_more), color = CinematicTokens.Accent) }
                                        }
                                        val slices = AnalyticsMath.slices(state.totals, type)
                                        if (slices.isEmpty()) Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            slices.take(4).forEach { slice ->
                                                val tint = CinematicTokens.category(slice.category, type == EntryType.INCOME)
                                                Column(Modifier.weight(1f).sharedBounds(rememberSharedContentState("focus-$type-${slice.category}"), scope)
                                                    .clickable { onFocus(AnalyticsFocus(type, slice.category)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Box(Modifier.size(48.dp).background(tint.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                                                        Icon(slice.category.icon(), stringResource(slice.category.label()), tint = tint)
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor,
                                                        state.preferences.currency, state.preferences.language), style = MaterialTheme.typography.labelSmall,
                                                        color = if (type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                                                    Text(percent(slice.basisPoints, state.preferences.language), color = CinematicTokens.Muted,
                                                        style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val key = "focus-${focus.type}-${focus.category ?: "all"}"
                    Surface(Modifier.fillMaxSize().sharedBounds(rememberSharedContentState(key), scope), color = CinematicTokens.Background) {
                        val slices = AnalyticsMath.slices(state.totals, focus.type)
                        val selected = slices.find { it.category == focus.category }
                        val total = slices.sumOf { it.minor }
                        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(18.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onFocus(null) }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                                    Text(stringResource(if (focus.type == EntryType.EXPENSE) R.string.expenses else R.string.income), style = MaterialTheme.typography.headlineSmall)
                                }
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.reference_total_for, monthLabel), color = CinematicTokens.Muted)
                                    Odometer(if (focus.type == EntryType.INCOME) total else -total,
                                        state.preferences.currency, state.preferences.language,
                                        if (focus.type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense, positiveSign = focus.type == EntryType.INCOME)
                                }
                                PremiumDonut(slices, focus.type, focus.category, state.preferences.currency, state.preferences.language) { category ->
                                    onFocus(AnalyticsFocus(focus.type, category))
                                }
                            }
                            item {
                                CascadeCard(0) {
                                    Text(stringResource(if (focus.type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), color = LocalAppearance.current.type(focus.type))
                                    slices.forEachIndexed { index, slice ->
                                        CategoryRow(slice, focus.type, state) { onFocus(AnalyticsFocus(focus.type, slice.category)) }
                                        if (index != slices.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                            item {
                                Text(focus.category?.let { stringResource(it.label()) } ?: stringResource(R.string.all_categories), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.cinematic_transactions), color = CinematicTokens.Muted)
                            }
                            if (state.transactions.isEmpty()) item { Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted) }
                            items(state.transactions, key = { it.id }) { entry ->
                                Surface(shape = RoundedCornerShape(16.dp), color = CinematicTokens.Surface,
                                    modifier = Modifier.fillMaxWidth().clickable { onEdit(entry.id) }) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(entry.title); Text(formatDate(entry.date, state.preferences.language), color = CinematicTokens.Muted)
                                        Text(formatReferenceMoney(if (entry.type == EntryType.INCOME) entry.amountMinor else -entry.amountMinor,
                                            entry.currency, state.preferences.language), color = if (entry.type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                                        if (entry.note.isNotBlank()) Text(entry.note, color = CinematicTokens.Muted)
                                    }
                                }
                            }
                            if (state.canLoadMore) item { TextButton(onClick = onMore) { Text(stringResource(R.string.load_more)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(slice: CategorySlice, type: EntryType, state: AnalyticsState,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = CinematicTokens.category(slice.category, type == EntryType.INCOME)
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(46.dp).background(color.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(slice.category.icon(), null, tint = color)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(slice.category.label()))
            Text(stringResource(R.string.category_transaction_count, slice.count), color = CinematicTokens.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor, state.preferences.currency, state.preferences.language), color = LocalAppearance.current.type(type))
            Text(percent(slice.basisPoints, state.preferences.language), color = CinematicTokens.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
