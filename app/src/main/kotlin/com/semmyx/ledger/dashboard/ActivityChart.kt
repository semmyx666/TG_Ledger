package com.semmyx.ledger.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.ContentCard
import com.semmyx.ledger.common.formatMoney
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityChart(days: List<DailyActivity>, currency: LedgerCurrency, language: AppLanguage, premium: Boolean,
    selectedDays: Int, onDays: (Int) -> Unit, onPremium: () -> Unit) {
    val plotted = remember(days, selectedDays) {
        if (selectedDays != 0 || days.size <= 48) days else {
            val chunk = (days.size + 47) / 48
            days.chunked(chunk).map { block -> DailyActivity(block.first().date, block.sumOf { it.incomeMinor }, block.sumOf { it.expenseMinor }) }
        }
    }
    val income = days.sumOf { it.incomeMinor }
    val expense = days.sumOf { it.expenseMinor }
    val maxValue = plotted.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) }?.coerceAtLeast(1) ?: 1
    val summary = if (selectedDays == 0) stringResource(R.string.all_time) else stringResource(R.string.chart_description, selectedDays, formatMoney(income, currency, language), formatMoney(expense, currency, language))
    val incomeColor = MaterialTheme.colorScheme.secondary
    val expenseColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    var shown by remember(days) { mutableStateOf(false) }
    LaunchedEffect(days) { shown = true }
    // Compose respects the system animator duration scale, including disabled animations.
    val progress by animateFloatAsState(if (shown) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "chartEntrance")
    ContentCard {
        Text(stringResource(R.string.cash_flow), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedDays == 7, onClick = { onDays(7) }, label = { Text(stringResource(R.string.last_7_days)) })
            FilterChip(selected = selectedDays == 30, onClick = { if (premium) onDays(30) else onPremium() },
                label = { Text(stringResource(if (premium) R.string.last_30_days else R.string.last_30_days_plus)) })
        }
        FilterChip(selected = selectedDays == 0, onClick = { if (premium) onDays(0) else onPremium() },
            label = { Text(stringResource(R.string.all_time)) })
        if (income == 0L && expense == 0L) {
            Text(stringResource(R.string.chart_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(formatMoney(maxValue, currency, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = summary }) {
                val base = size.height - 1.dp.toPx()
                repeat(3) { line ->
                    val y = base * line / 2f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val step = size.width / plotted.size.coerceAtLeast(1)
                val barWidth = step * .28f
                plotted.forEachIndexed { index, day ->
                    val incomeHeight = (day.incomeMinor.toDouble() / maxValue * (base - 4.dp.toPx())).toFloat() * progress
                    val expenseHeight = (day.expenseMinor.toDouble() / maxValue * (base - 4.dp.toPx())).toFloat() * progress
                    val x = step * index + step * .16f
                    if (incomeHeight > 0) drawRoundRect(incomeColor, Offset(x, base - incomeHeight), Size(barWidth, incomeHeight), CornerRadius(2.dp.toPx()))
                    if (expenseHeight > 0) drawRoundRect(expenseColor, Offset(x + barWidth + step * .08f, base - expenseHeight), Size(barWidth, expenseHeight), CornerRadius(2.dp.toPx()))
                }
            }
            if (days.isNotEmpty()) {
                val formatter = remember(language) { DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag(language.tag)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(days.first().date.format(formatter), style = MaterialTheme.typography.bodyMedium)
                    Text(days.last().date.format(formatter), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // Position is a second visual cue; the chart is not color-only.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.legend_income, formatMoney(income, currency, language)), color = incomeColor, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.legend_expense, formatMoney(expense, currency, language)), color = expenseColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
