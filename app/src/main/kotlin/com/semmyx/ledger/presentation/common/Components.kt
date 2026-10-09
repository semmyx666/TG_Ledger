package com.semmyx.ledger.presentation.common

import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

fun formatMoney(minor: Long, currency: LedgerCurrency, language: AppLanguage): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag(language.tag)).apply {
        this.currency = Currency.getInstance(currency.name)
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(minor, 2))

fun formatDate(date: LocalDate, language: AppLanguage): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.tag)))

/** Used for the developer-activation expiry date, which is a real instant rather than a calendar day. */
fun formatDateTime(epochMillis: Long, language: AppLanguage): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.tag)))

// ---------------------------------------------------------------------------
// Category / message labels
// ---------------------------------------------------------------------------

@StringRes fun Category.label(): Int = when (this) {
    Category.FOOD      -> R.string.category_food
    Category.TRANSPORT -> R.string.category_transport
    Category.HOME      -> R.string.category_home
    Category.HEALTH    -> R.string.category_health
    Category.LEISURE   -> R.string.category_leisure
    Category.SALARY    -> R.string.category_salary
    Category.OTHER     -> R.string.category_other
    Category.TRANSFER  -> R.string.category_transfer
    Category.DINING    -> R.string.category_dining
    Category.CASHBACK  -> R.string.category_cashback
}

fun Category.icon(): ImageVector = when (this) {
    Category.FOOD      -> Icons.Default.ShoppingCart
    Category.TRANSPORT -> Icons.Default.DirectionsBus
    Category.HOME      -> Icons.Default.Home
    Category.HEALTH    -> Icons.Default.FavoriteBorder
    Category.LEISURE   -> Icons.Default.LocalActivity
    Category.SALARY    -> Icons.Default.WorkOutline
    Category.OTHER     -> Icons.Default.ReceiptLong
    Category.TRANSFER  -> Icons.Default.DoubleArrow
    Category.DINING    -> Icons.Default.Restaurant
    Category.CASHBACK  -> Icons.Default.CurrencyExchange
}

@StringRes fun UiMessage.label(): Int = when (this) {
    UiMessage.STORAGE              -> R.string.storage_error
    UiMessage.SAVED                -> R.string.saved
    UiMessage.DELETED              -> R.string.entry_deleted
    UiMessage.UNDO_EXPIRED         -> R.string.undo_expired
    UiMessage.RATE_OFFLINE         -> R.string.rate_offline
    UiMessage.BIOMETRIC_UNAVAILABLE -> R.string.biometric_unavailable
    UiMessage.NAME_INVALID         -> R.string.name_invalid
    UiMessage.NOT_FOUND            -> R.string.entry_not_found
    UiMessage.PREMIUM_ACTIVATED    -> R.string.premium_activated
    UiMessage.ACTIVATION_INVALID   -> R.string.activation_invalid
}

@StringRes fun BillingIssue.label(): Int = when (this) {
    BillingIssue.UNAVAILABLE    -> R.string.billing_unavailable
    BillingIssue.CONFIGURATION  -> R.string.billing_configuration
    BillingIssue.NETWORK        -> R.string.billing_network
    BillingIssue.PENDING        -> R.string.billing_pending
    BillingIssue.VERIFICATION   -> R.string.billing_verification
    BillingIssue.ACKNOWLEDGEMENT -> R.string.billing_acknowledgement
    BillingIssue.ACTIVATION_CODE -> R.string.billing_activation_code
}

// ---------------------------------------------------------------------------
// Brand mark
// ---------------------------------------------------------------------------

@Composable
fun LedgerMark(modifier: Modifier = Modifier, color: Color = LocalAppearance.current.color("icons")) {
    Canvas(modifier.size(40.dp)) {
        val w = size.width / 5f
        listOf(0.42f, 0.68f, 1f).forEachIndexed { index, height ->
            drawRoundRect(
                color,
                Offset(index * w * 1.6f, size.height * (1 - height)),
                Size(w, size.height * height),
                CornerRadius(w * .2f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Navigation bar
// ---------------------------------------------------------------------------

@Composable
fun LedgerBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        R.string.overview, R.string.settings, R.string.help,
        R.string.analytics_title, R.string.bank_dashboard,
    )
    val icons = listOf(
        Icons.Default.SpaceDashboard, Icons.Default.Tune, Icons.Default.HelpOutline,
        Icons.Default.BarChart, Icons.Default.AccountBalance,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            NavigationBarItem(
                selected = isSelected,
                onClick  = { onSelect(index) },
                icon = {
                    Icon(
                        icons[index], null,
                        tint = LocalAppearance.current.color("icons")
                            .copy(alpha = if (isSelected) 1f else .55f),
                    )
                },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Content card  — used everywhere for grouped sections
// ---------------------------------------------------------------------------

/**
 * Premium card with a subtle gradient tint on the top-left corner.
 * Text inside never overflows: Columns use fillMaxWidth; callers must
 * supply maxLines / overflow on individual Text composables as needed.
 */
@Composable
fun ContentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape  = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Empty / loading states
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                Icons.Default.ReceiptLong, null,
                Modifier.padding(20.dp).size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            title,
            style     = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines  = 3,
            overflow  = TextOverflow.Ellipsis,
        )
        Text(
            description,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.bodyMedium,
            maxLines  = 5,
            overflow  = TextOverflow.Ellipsis,
        )
        FilledTonalButton(
            onClick   = onAction,
            modifier  = Modifier.heightIn(min = 48.dp),
        ) { Text(action, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
