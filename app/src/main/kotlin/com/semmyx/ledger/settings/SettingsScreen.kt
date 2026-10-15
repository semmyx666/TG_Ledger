package com.semmyx.ledger.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.semmyx.ledger.platform.HighRefreshRate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.R
import com.semmyx.ledger.diagnostics.CrashLogger
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(model: SettingsViewModel, biometricAvailable: Boolean, onNavigate: (Int) -> Unit,
    onPurchase: () -> Unit, onSupport: () -> Unit, onLock: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val display = LocalView.current.display
    val supportedRates = HighRefreshRate.supported(display)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var name by remember(state.preferences.displayName) { mutableStateOf(state.preferences.displayName) }
    var activationCode by remember { mutableStateOf("") }
    var crashReport by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(model, context) { model.events.collect { snackbar.showSnackbar(context.getString(it.label())) } }
    if (state.failed) {
        EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.storage_error), stringResource(R.string.back), { onNavigate(0) })
        return
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = {
        IconButton(onClick = { onNavigate(0) }) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
    }, actions = {
        IconButton(onClick = onLock) { Icon(Icons.Default.Lock, stringResource(R.string.lock_now)) }
    }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "profile") { ContentCard {
                Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.local_profile), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.display_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = { model.saveName(name) }, enabled = name.trim() != state.preferences.displayName) { Text(stringResource(R.string.save_profile)) }
            } }
            item(key = "language") { ContentCard {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.language_instant), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        FilterChip(selected = state.preferences.language == language, onClick = { model.language(language) }, modifier = Modifier.testTag("language:${language.tag}"),
                            label = { Text(stringResource(if (language == AppLanguage.ENGLISH) R.string.language_en else R.string.language_uk)) })
                    }
                }
            } }
            item(key = "bankImport") { ContentCard {
                Text(stringResource(R.string.bank_import_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.bank_consent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onNavigate(4) }) { Text(stringResource(R.string.bank_dashboard)) }
            } }
            item(key = "customColors") { ColorSettings(state.preferences, model::color, model::resetColors) }
            item(key = "appearance") { ContentCard {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { theme ->
                        FilterChip(selected = state.preferences.theme == theme, onClick = { model.theme(theme) }, label = {
                            Text(stringResource(when (theme) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }))
                        })
                    }
                }
            } }
            item(key = "frameRate") { ContentCard {
                Text(stringResource(R.string.fps_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.fps_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 60, 90, 120).forEach { rate ->
                        FilterChip(selected = state.preferences.refreshRateHz == rate,
                            enabled = rate == 0 || rate in supportedRates,
                            onClick = { model.refreshRate(rate) }, modifier = Modifier.testTag("fps:$rate"),
                            label = { Text(if (rate == 0) stringResource(R.string.fps_auto) else stringResource(R.string.fps_value, rate)) })
                    }
                }
                Text(stringResource(R.string.fps_supported, supportedRates.sorted().joinToString(" / ")),
                    style = MaterialTheme.typography.bodySmall)
                if (state.preferences.refreshRateHz != 0 && state.preferences.refreshRateHz !in supportedRates)
                    Text(stringResource(R.string.fps_fallback), color = MaterialTheme.colorScheme.error)
            } }
            item(key = "currency") { ContentCard {
                Text(stringResource(R.string.default_currency), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.currency_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerCurrency.entries.forEach { currency ->
                        FilterChip(selected = state.preferences.currency == currency, onClick = { model.currency(currency) }, label = { Text(currency.name) })
                    }
                }
            } }
            item(key = "security") { ContentCard {
                Text(stringResource(R.string.security), style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.biometric_unlock), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (biometricAvailable) R.string.biometric_explanation else R.string.biometric_unavailable),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.preferences.biometricsEnabled, onCheckedChange = model::biometrics,
                        enabled = biometricAvailable || state.preferences.biometricsEnabled, modifier = Modifier.testTag("biometricSwitch"))
                }
                Text(stringResource(R.string.auto_lock_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onLock) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lock_now)) }
            } }
            item(key = "premium") { ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.premium_name), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.premium_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• " + stringResource(R.string.premium_feature_trends), style = MaterialTheme.typography.bodyMedium)
                    Text("• " + stringResource(R.string.premium_feature_mascot), style = MaterialTheme.typography.bodyMedium)
                    Text("• " + stringResource(R.string.premium_feature_support), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.premium.entitled) {
                    Text(stringResource(R.string.premium_active), color = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(when (state.premium.source) {
                        PremiumSource.OFFICIAL_DEVELOPER -> R.string.premium_source_developer
                        PremiumSource.TRIAL -> R.string.premium_source_trial
                        else -> R.string.premium_source_play
                    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.premium.expiresAtMillis?.let {
                        Text(stringResource(R.string.premium_expires, formatDateTime(it, state.preferences.language)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(if (state.premium.source == PremiumSource.GOOGLE_PLAY) R.string.premium_offline_notice
                        else R.string.premium_local_notice), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.premium_assistant_toggle), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.premium_assistant_toggle_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.preferences.premiumAssistantEnabled, onCheckedChange = model::premiumAssistant, modifier = Modifier.testTag("mascotSwitch"))
                    }
                }
                if (!state.premium.entitled || state.premium.expiresAtMillis != null) {
                    val price = state.premium.formattedPrice
                    Button(onClick = onPurchase, enabled = state.premium.configured && price != null && !state.premium.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (price == null) stringResource(R.string.premium_unlock) else stringResource(R.string.premium_buy, price))
                    }
                    if (!state.premium.configured) Text(stringResource(R.string.billing_configuration), style = MaterialTheme.typography.bodyMedium)
                }
                if (!state.premium.entitled && state.premium.trialAvailable) {
                    Text(stringResource(R.string.trial_explanation))
                    OutlinedButton(onClick = { model.startTrial(TrialPolicy.DAYS) }) { Text(stringResource(R.string.trial_14)) }
                }
                if (state.premium.entitled) {
                    Text(stringResource(R.string.mascot_style))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("gray" to R.string.mascot_gray, "sky" to R.string.mascot_sky).forEach { (value, name) ->
                            FilterChip(selected = state.preferences.mascotSkin == value, onClick = { model.skin(value) },
                                label = { Text(stringResource(name)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.mascot_animations), Modifier.weight(1f))
                        Switch(checked = state.preferences.mascotAnimations, onCheckedChange = model::mascotAnimations)
                    }
                }
                if (state.premium.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.premium.issue?.takeUnless { it == BillingIssue.CONFIGURATION || it == BillingIssue.ACTIVATION_CODE }?.let {
                    Text(stringResource(it.label()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                RestorePurchasesAction(enabled = !state.premium.busy, onClick = model::restorePurchases)
            } }
            if (!state.premium.entitled || state.premium.expiresAtMillis != null || state.premium.source == PremiumSource.OFFICIAL_DEVELOPER) item(key = "developerPurchase") { ContentCard {
                DeveloperActivationPanel(state.premium, activationCode, { activationCode = it }, state.activating,
                    onActivate = { model.activateDeveloperCode(activationCode) { activationCode = "" } },
                    onCopy = { request ->
                        clipboard.setText(AnnotatedString(request))
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.activation_request_copied)) }
                    }, onSupport = onSupport)
            } }
            item(key = "rates") { ContentCard {
                Text(stringResource(R.string.reference_rates), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.rates_disclaimer), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (state.rates.isEmpty()) Text(stringResource(R.string.rates_empty), style = MaterialTheme.typography.bodyMedium)
                val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag(state.preferences.language.tag)).apply { maximumFractionDigits = 4 }
                state.rates.forEach { rate ->
                    Text(stringResource(R.string.rate_value, rate.currency.name, formatter.format(rate.uahPerUnit)), style = MaterialTheme.typography.bodyLarge)
                }
                state.rates.minByOrNull { it.date }?.let { rate ->
                    Text(stringResource(R.string.rates_as_of, formatDate(rate.date, state.preferences.language)), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = model::refreshRates, enabled = !state.refreshing) { Text(stringResource(if (state.refreshing) R.string.refreshing else R.string.refresh_rates)) }
            } }
            item(key = "support") { ContentCard {
                Text(stringResource(R.string.support), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.support_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.contact_telegram)) }
                OutlinedButton(onClick = {
                    val saved = CrashLogger.readLastCrash(context)
                    if (saved == null) scope.launch { snackbar.showSnackbar(context.getString(R.string.diagnostics_none)) }
                    else crashReport = saved
                }, modifier = Modifier.fillMaxWidth().testTag("showCrashReport")) { Text(stringResource(R.string.diagnostics_show_button)) }
            } }
        }
    }
    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.diagnostics_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(report))
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.diagnostics_copied)) }
                }) { Text(stringResource(R.string.diagnostics_copy)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { CrashLogger.clear(context); crashReport = null }) { Text(stringResource(R.string.diagnostics_clear)) }
                    TextButton(onClick = { crashReport = null }) { Text(stringResource(R.string.diagnostics_close)) }
                }
            },
        )
    }
}
