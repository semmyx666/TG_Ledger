package com.semmyx.ledger.presentation.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

object CinematicTokens {
    val Background: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val Muted: Color @Composable get() = LocalAppearance.current.color("muted")
    val Accent: Color @Composable get() = LocalAppearance.current.color("accent")
    val Income: Color @Composable get() = LocalAppearance.current.color("income")
    val Expense: Color @Composable get() = LocalAppearance.current.color("expense")
    @Composable fun category(category: Category, income: Boolean = false): Color = LocalAppearance.current.category(category, income)
}

@Composable
fun CascadeCard(index: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index.coerceAtMost(8) * 55L); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(420), label = "cascadeAlpha")
    val offset by animateFloatAsState(if (visible) 0f else 24f, spring(dampingRatio = .85f), label = "cascadeOffset")
    Surface(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; translationY = offset * density },
        shape = RoundedCornerShape(18.dp), color = CinematicTokens.Surface, shadowElevation = 10.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** UI animation only; the SQL total and final displayed amount remain exact Long minor units. */
@Composable
fun Odometer(minor: Long, currency: LedgerCurrency, language: AppLanguage, color: Color = MaterialTheme.colorScheme.onSurface, positiveSign: Boolean = false) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(minor) { progress.snapTo(0f); progress.animateTo(1f, tween(720, easing = FastOutSlowInEasing)) }
    val value = if (progress.value >= .9999f) minor else (minor.toDouble() * progress.value).roundToLong()
    val text = formatReferenceMoney(value, currency, language, positiveSign = positiveSign)
    val exact = formatReferenceMoney(minor, currency, language, positiveSign = positiveSign)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics(mergeDescendants = true) { contentDescription = exact }) {
        text.forEachIndexed { index, digit ->
            key(index) {
                AnimatedContent(targetState = digit, label = "digit$index", transitionSpec = {
                    (slideInVertically(tween(85)) { it } + fadeIn(tween(85))) togetherWith
                        (slideOutVertically(tween(85)) { -it } + fadeOut(tween(85)))
                }) { item -> Text(item.toString(), style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace, color = color) }
            }
        }
    }
}

@Composable
fun PremiumBars(buckets: List<CashBucket>, currency: LedgerCurrency, language: AppLanguage) {
    val palette = LocalAppearance.current
    // Bound rendering work for multi-year history. Every bucket remains available in SQL/category details.
    val bars = remember(buckets) { if (buckets.size <= 48) buckets else buckets.chunked((buckets.size + 47) / 48).map { block ->
        CashBucket(block.first().from, block.last().through, block.sumOf { it.incomeMinor }, block.sumOf { it.expenseMinor })
    } }
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) { progress.snapTo(0f); progress.animateTo(1f, tween(850, easing = FastOutSlowInEasing)) }
    val maxValue = bars.maxOfOrNull { it.expenseMinor }?.coerceAtLeast(1) ?: 1
    val description = stringResource(R.string.cinematic_chart_accessibility)
    val numeric = remember(language) { NumberFormat.getNumberInstance(Locale.forLanguageTag(language.tag)).apply { maximumFractionDigits = 0 } }
    val axisMaximum = remember(maxValue) {
        val units = maxValue.toDouble() / 100
        val magnitude = 10.0.pow(floor(log10(units.coerceAtLeast(1.0))))
        (ceil(units / magnitude) * magnitude * 100).toLong().coerceAtLeast(100)
    }
    val axisLabels = listOf(numeric.format(axisMaximum / 100.0), numeric.format(axisMaximum / 200.0), "0")
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall
    val labelWidthPx = axisLabels.maxOf { measurer.measure(AnnotatedString(it), style = axisStyle, softWrap = false).size.width }
    val axisWidth = with(LocalDensity.current) { labelWidthPx.toDp() } + 10.dp
    Row(Modifier.fillMaxWidth().height(184.dp)) {
        Column(Modifier.width(axisWidth).fillMaxHeight().padding(bottom = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(axisLabels[0], maxLines = 1, softWrap = false, color = CinematicTokens.Muted, style = axisStyle)
            Text(axisLabels[1], maxLines = 1, softWrap = false, color = CinematicTokens.Muted, style = axisStyle)
            Text(axisLabels[2], maxLines = 1, softWrap = false, color = CinematicTokens.Muted, style = axisStyle)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }
                .pointerInput(bars) { detectTapGestures { point ->
                    if (bars.isNotEmpty()) selected = ((point.x / size.width) * bars.size).toInt().coerceIn(0, bars.lastIndex)
                } }) {
                val baseline = size.height - 12.dp.toPx()
                repeat(3) { index ->
                    val y = baseline * index / 2
                    drawLine(palette.color("grid"), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                }
                val step = size.width / bars.size.coerceAtLeast(1)
                bars.forEachIndexed { index, bucket ->
                    val stagger = (progress.value * 1.3f - index.toFloat() / bars.size.coerceAtLeast(1) * .3f).coerceIn(0f, 1f)
                    val h = (bucket.expenseMinor.toDouble() / axisMaximum * (baseline - 8.dp.toPx()) * stagger).toFloat()
                    if (h > 0) {
                        val x = step * index + step * .12f
                        val brush = Brush.verticalGradient(listOf(palette.color("chartTop"), palette.color("chartBottom")), baseline - h, baseline)
                        drawRoundRect(brush, Offset(x, baseline - h), Size(step * .76f, h), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                        if (selected == index) drawRoundRect(Color.White.copy(alpha = .24f), Offset(x, baseline - h), Size(step * .76f, h), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                    }
                }
            }
            selected?.let { index ->
                val bucket = bars[index]
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .95f), shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).widthIn(max = 220.dp), shadowElevation = 8.dp) {
                    Text(stringResource(R.string.cinematic_tooltip, formatDate(bucket.from, language),
                        formatDate(bucket.through, language), formatReferenceMoney(bucket.expenseMinor, currency, language)),
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    // Accessible equivalent to Canvas touch targets; also gives each column its exact date range.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        bars.take(6).forEachIndexed { index, bucket ->
            TextButton(onClick = { selected = index }, contentPadding = PaddingValues(2.dp)) {
                Text(if (bucket.from.month == bucket.through.month) "${bucket.from.dayOfMonth}–${bucket.through.dayOfMonth}"
                    else "${bucket.from.monthValue}/${bucket.from.year}", style = MaterialTheme.typography.labelSmall, color = CinematicTokens.Muted)
            }
        }
    }
    if (bars.size > 6) Text(stringResource(R.string.cinematic_more_buckets, bars.size), color = CinematicTokens.Muted)
}

/** Large reference-style ring: exploded active sector, circular badge, exact center readout. */
@Composable
fun PremiumDonut(slices: List<CategorySlice>, type: EntryType, selected: Category?, currency: LedgerCurrency,
    language: AppLanguage, onSelect: (Category) -> Unit) {
    val palette = LocalAppearance.current
    val total = slices.sumOf { it.minor }
    val active = slices.find { it.category == selected } ?: slices.firstOrNull()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) { progress.snapTo(0f); progress.animateTo(1f, tween(850, easing = FastOutSlowInEasing)) }
    val description = stringResource(if (type == EntryType.INCOME) R.string.cinematic_income_categories else R.string.cinematic_expense_categories)
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }.pointerInput(slices, selected) {
            detectTapGestures { point ->
                var start = -90f
                val outer = size.width * .475f
                val inner = size.width * .247f
                for (slice in slices) {
                    val sweep = (slice.minor.toDouble() / total.coerceAtLeast(1) * 360).toFloat()
                    val middle = (start + sweep / 2) * PI.toFloat() / 180
                    val lift = if (slices.size > 1 && slice.category == active?.category) size.width * .023f else 0f
                    val dx = point.x - size.width / 2 - cos(middle) * lift
                    val dy = point.y - size.height / 2 - sin(middle) * lift
                    val radius = hypot(dx, dy)
                    val angle = (atan2(dy, dx) * 180 / PI.toFloat() - start + 720) % 360
                    if (radius in inner..outer && angle <= sweep) { onSelect(slice.category); break }
                    start += sweep
                }
            }
        }) {
            val outer = size.minDimension * .475f; val inner = size.minDimension * .247f
            val origin = Offset(size.width / 2, size.height / 2)
            drawCircle(palette.color("surface"), radius = (outer + inner) / 2, center = origin, style = Stroke(outer - inner))
            var start = -90f
            slices.forEach { slice ->
                val sweep = (slice.minor.toDouble() / total.coerceAtLeast(1) * 360).toFloat()
                val gap = if (slices.size == 1) 0f else minOf(.85f, sweep * .08f)
                val middle = (start + sweep / 2) * PI.toFloat() / 180
                val lift = if (slices.size > 1 && slice.category == active?.category) size.width * .023f else 0f
                val center = origin + Offset(cos(middle) * lift, sin(middle) * lift)
                val visibleSweep = ((sweep - gap) * progress.value).coerceAtLeast(0f)
                if (visibleSweep > 0f) {
                    val color = palette.category(slice.category, type == EntryType.INCOME)
                    val path = ringSegment(center, outer, inner, start + gap / 2, visibleSweep)
                    val shadow = ringSegment(center + Offset(2.dp.toPx(), 3.dp.toPx()), outer, inner, start + gap / 2, visibleSweep)
                    drawPath(shadow, Color.Black.copy(alpha = .65f))
                    drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = .78f), color, color.copy(alpha = .92f)),
                        Offset(0f, 0f), Offset(size.width, size.height)))
                }
                start += sweep
            }
            if (slices.isEmpty()) drawCircle(palette.color("grid"), radius = (outer + inner) / 2, center = origin, style = Stroke(outer - inner))
        }
        Column(Modifier.fillMaxWidth(.46f), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (active == null) Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted)
            else {
                val tint = CinematicTokens.category(active.category, type == EntryType.INCOME)
                Box(Modifier.size(44.dp).background(tint.copy(alpha = .13f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Icon(active.category.icon(), null, tint = tint, modifier = Modifier.size(27.dp))
                }
                Text(stringResource(active.category.label()), style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(formatReferenceMoney(if (type == EntryType.INCOME) active.minor else -active.minor, currency, language,
                    showCurrency = false, positiveSign = type == EntryType.INCOME), color = palette.type(type))
                Text(percent(active.basisPoints, language), color = CinematicTokens.Muted)
            }
        }
    }
}

private fun ringSegment(center: Offset, outer: Float, inner: Float, start: Float, sweep: Float): Path {
    val radians = start * PI.toFloat() / 180
    if (sweep >= 359.999f) {
        // Skia arcTo(360) is not a reliable filled annulus: two closed ovals define the hole explicitly.
        return Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(center.x - outer, center.y - outer, center.x + outer, center.y + outer))
            addOval(Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner))
        }
    }
    return Path().apply {
        moveTo(center.x + cos(radians) * outer, center.y + sin(radians) * outer)
        arcTo(Rect(center.x - outer, center.y - outer, center.x + outer, center.y + outer), start, sweep, false)
        val finish = (start + sweep) * PI.toFloat() / 180
        lineTo(center.x + cos(finish) * inner, center.y + sin(finish) * inner)
        arcTo(Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner), start + sweep, -sweep, false)
        close()
    }
}

fun formatReferenceMoney(minor: Long, currency: LedgerCurrency, language: AppLanguage,
    showCurrency: Boolean = true, positiveSign: Boolean = false): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag(language.tag)).apply {
        minimumFractionDigits = if (minor % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return (if (positiveSign && minor > 0) "+" else "") + formatter.format(BigDecimal.valueOf(minor, 2)).replace('-', '−') +
        (if (showCurrency) " " + currency.name else "")
}

fun percent(basisPoints: Int, language: AppLanguage): String = if (basisPoints == 10000) "100%" else String.format(Locale.forLanguageTag(language.tag), "%.2f%%", basisPoints / 100.0)
