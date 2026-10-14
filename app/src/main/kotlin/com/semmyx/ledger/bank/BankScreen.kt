package com.semmyx.ledger.bank

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.BuildConfig
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import com.semmyx.ledger.common.*
import com.semmyx.ledger.theme.LocalAppearance
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BankScreen(model: BankViewModel, repository: com.semmyx.ledger.data.bank.BankImportRepository, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, onPremium: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
    val reviews by model.reviews.collectAsStateWithLifecycle()
    val custom by model.customCategories.collectAsStateWithLifecycle()
    val enabled by model.enabled.collectAsStateWithLifecycle()
    val automatic by model.auto.collectAsStateWithLifecycle()
    val failed by model.error.collectAsStateWithLifecycle()
    val revision by model.revision.collectAsStateWithLifecycle()
    val mode by model.mode.collectAsStateWithLifecycle()
    val mono by model.mono.collectAsStateWithLifecycle()
    val privat by model.privat.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalAppearance.current
    var notificationAccess by remember { mutableStateOf(false) }
    var confirmationAccess by remember { mutableStateOf(false) }
    var smsAccess by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    var packageDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var privatToken by remember { mutableStateOf("") }
    var connectingPrivat by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var smsScan by remember { mutableStateOf<BankSmsCatchUp.Result?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var setupExpanded by remember { mutableStateOf(false) }
    
    fun openSettings(action: String, data: Uri? = null) {
        try { context.startActivity(Intent(action, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { exportFailed = true }
    }
    val confirmationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { confirmationAccess = it }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        smsAccess = result[Manifest.permission.RECEIVE_SMS] == true
        if (smsAccess) BankImportWorker.schedule(context)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            try { withContext(Dispatchers.IO) {
                val rows = repository.exportEntries()
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write("id,merchant,amount_minor,currency,type,source,bank,category,card_last4,event_time,date,origin\n")
                    fun csv(value: String): String {
                        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'" + value else value
                        return "\"" + safe.replace("\"", "\"\"") + "\""
                    }
                    rows.forEach { e -> writer.write(listOf(e.id, e.title, e.amountMinor.toString(), e.currency.name,
                        e.type.name, e.sourceType, Banks.name(e.bankId), custom.firstOrNull { it.id == e.customCategoryId }?.name ?: e.category.name,
                        e.cardLast4.orEmpty(), e.eventAtMillis?.toString().orEmpty(), e.date.toString(), e.origin).joinToString(",", transform = ::csv) + "\n") }
                } ?: error("Export destination unavailable")
            }; exportFailed = false } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { exportFailed = true }
        }
    }
    LifecycleResumeEffect(Unit) {
        notificationAccess = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        confirmationAccess = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        smsAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (repository.importMode == "NOTIFY") BankImportWorker.runNow(context)
        onPauseOrDispose { }
    }
    val currency = state.preferences.currency
    val language = state.preferences.language
    val total = state.categories.sumOf { it.totalMinor }
    val budget = remember(currency, revision) { model.budget(currency) }
    var budgetText by remember(currency, budget) { mutableStateOf(if (budget > 0) Money.editable(budget) else "") }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.bank_dashboard)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
    }, actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, stringResource(R.string.add_entry)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("bankList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (failed || exportFailed || state.failed) item { Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error) }
            item {
                OutlinedButton(onClick = { setupExpanded = !setupExpanded }, modifier = Modifier.fillMaxWidth().testTag("bankSetup")) {
                    Icon(if (setupExpanded) Icons.Default.ExpandLess else Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bank_setup))
                }
            }
            if (setupExpanded) {
            item { ContentCard {
                Text(stringResource(R.string.bank_import_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.bank_consent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.bank_enabled), Modifier.weight(1f)); Switch(enabled, model::enabled) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.bank_auto), Modifier.weight(1f)); Switch(automatic, model::auto, enabled = enabled) }
                Text(stringResource(R.string.import_mode_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NOTIFY" to R.string.import_mode_notify, "API" to R.string.import_mode_api, "SMS" to R.string.import_mode_sms).forEach { (value, title) ->
                        FilterChip(selected = mode == value, onClick = { model.mode(value); BankImportWorker.runNow(context) }, label = { Text(stringResource(title)) })
                    }
                }
                when (mode) {
                    "API" -> {
                        Text(stringResource(R.string.api_howto), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { openSettings(Intent.ACTION_VIEW, Uri.parse("https://api.monobank.ua/")) }) { Text(stringResource(R.string.api_open_site)) }
                        if (!mono.connected) {
                            OutlinedTextField(token, { token = it.trim().take(200) }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(stringResource(R.string.api_token_label)) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                isError = mono.error == "BAD_TOKEN")
                            Button(enabled = token.length >= 20 && !connecting, onClick = {
                                connecting = true
                                scope.launch {
                                    if (repository.connectMonobank(token)) { token = ""; BankImportWorker.runNow(context) }
                                    connecting = false
                                }
                            }) { Text(stringResource(if (connecting) R.string.api_connecting else R.string.api_connect)) }
                        } else {
                            Text(stringResource(R.string.api_connected, mono.accounts.coerceAtLeast(1)), color = MaterialTheme.colorScheme.secondary)
                            if (mono.lastSyncMillis > 0) Text(stringResource(R.string.api_last_sync,
                                Instant.ofEpochMilli(mono.lastSyncMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))) + " · " +
                                stringResource(R.string.api_imported_last, mono.lastImported), style = MaterialTheme.typography.bodySmall)
                            if (mono.syncing) Text(stringResource(R.string.api_syncing), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = !mono.syncing, onClick = { BankImportWorker.runNow(context) }) { Text(stringResource(R.string.api_sync_now)) }
                                TextButton(onClick = { scope.launch { repository.disconnectMonobank() } }) { Text(stringResource(R.string.api_disconnect)) }
                            }
                        }
                        mono.error?.let { code ->
                            Text(stringResource(when (code) {
                                "BAD_TOKEN" -> R.string.api_error_bad_token; "RATE_LIMIT" -> R.string.api_error_rate
                                "NO_ACCOUNTS" -> R.string.api_error_no_accounts; else -> R.string.api_error_network
                            }), color = MaterialTheme.colorScheme.error)
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.privat_api_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.privat_api_howto), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { openSettings(Intent.ACTION_VIEW, Uri.parse("https://privatbank.ua/business/intehratsiya")) }) { Text(stringResource(R.string.privat_api_site)) }
                        if (!privat.connected) {
                            OutlinedTextField(privatToken, { privatToken = it.trim().take(2048) }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(stringResource(R.string.privat_api_token)) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            Button(enabled = privatToken.length >= 20 && !connectingPrivat, onClick = {
                                connectingPrivat = true
                                scope.launch {
                                    try { if (repository.connectPrivat(privatToken)) { privatToken = ""; BankImportWorker.runNow(context) } }
                                    finally { connectingPrivat = false }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (connectingPrivat) R.string.api_connecting else R.string.api_connect)) }
                        } else {
                            Text(stringResource(R.string.privat_api_connected))
                            if (privat.lastSyncMillis > 0) Text(stringResource(R.string.api_last_sync,
                                Instant.ofEpochMilli(privat.lastSyncMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled = !privat.syncing, onClick = { BankImportWorker.runNow(context) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.api_sync_now)) }
                            TextButton(onClick = { scope.launch { repository.disconnectPrivat() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.api_disconnect)) }
                        }
                        privat.error?.let { Text(stringResource(R.string.privat_api_error), color = MaterialTheme.colorScheme.error) }
                        Text(stringResource(R.string.other_api_note), style = MaterialTheme.typography.bodySmall)
                    }
                    "SMS" -> {
                        Text(stringResource(R.string.sms_howto), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(if (smsAccess) R.string.bank_sms_yes else R.string.bank_sms_no))
                        if (!smsAccess) Button(onClick = { smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }) { Text(stringResource(R.string.bank_sms_permission)) }
                        OutlinedButton(enabled = smsAccess && !scanning, onClick = {
                            scanning = true
                            scope.launch { smsScan = withContext(Dispatchers.IO) { BankSmsCatchUp.run(context, repository, 30) }; scanning = false }
                        }) { Text(stringResource(if (scanning) R.string.sms_scanning else R.string.sms_scan)) }
                        smsScan?.let { r ->
                            Text(if (!r.permission) stringResource(R.string.bank_sms_no)
                                else stringResource(R.string.sms_scan_result, r.bankMessages, r.imported, r.queued), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.sms_restricted_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text(stringResource(R.string.push_howto), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(if (notificationAccess) R.string.bank_access_yes else R.string.bank_access_no))
                        OutlinedButton(onClick = { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text(stringResource(R.string.bank_notification_permission)) }
                    }
                }
                TextButton(onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)) }) { Text(stringResource(R.string.bank_app_permissions)) }
                TextButton(onClick = { openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) { Text(stringResource(R.string.bank_battery)) }
                if (!confirmationAccess && android.os.Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { confirmationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_confirm_permission)) }
                else Text(stringResource(R.string.import_confirm_enabled), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { privacy = true }) { Text(stringResource(R.string.bank_privacy)) }
            } }
            item { ContentCard {
                Text(stringResource(R.string.import_diagnostics), style = MaterialTheme.typography.titleMedium)
                if (mode == "NOTIFY") {
                Text(stringResource(if (diagnostics.connected) R.string.import_connected else R.string.import_disconnected))
                Text(stringResource(R.string.import_counts, diagnostics.notificationCount, diagnostics.bankMessageCount))
                Text(stringResource(importReason(diagnostics.lastCode)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (diagnostics.lastPackage.isNotBlank()) Text(diagnostics.lastPackage, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    if (!notificationAccess) openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    else try { android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(context, com.semmyx.ledger.bank.BankNotificationListener::class.java)) }
                    catch (_: Exception) { exportFailed = true }
                }) { Text(stringResource(R.string.import_reconnect)) }
                TextButton(onClick = { packageDialog = true }) { Text(stringResource(R.string.import_package_settings)) }
                }
                Button(onClick = { pasteDialog = true }) { Text(stringResource(R.string.import_paste)) }
                Text(stringResource(R.string.import_queue_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAY" to R.string.bank_day, "WEEK" to R.string.bank_week, "MONTH" to R.string.bank_month, "ALL" to R.string.all_time).forEach { (period, title) ->
                    FilterChip(modifier = Modifier.testTag("bankPeriod:$period"), selected = state.filter.period == period, onClick = { if (period == "DAY" || state.premium) model.period(period) else onPremium() }, label = {
                        Text(stringResource(title) + if (period != "DAY" && !state.premium) " · Plus" else "")
                    })
                }
            } }
            item { ContentCard {
                Text(stringResource(R.string.expenses), color = palette.type(EntryType.EXPENSE))
                Text(formatMoney(total, currency, language), style = MaterialTheme.typography.headlineMedium, color = palette.type(EntryType.EXPENSE))
                FilterChip(selected = state.filter.source == null, onClick = { model.source(null) }, label = { Text(stringResource(R.string.all)) })
                state.sources.forEach { source ->
                    val name = when (source.sourceType) { "CASH" -> stringResource(R.string.bank_cash); "CARD" -> Banks.name(source.bankId); else -> stringResource(R.string.bank_unknown) }
                    Row(Modifier.fillMaxWidth().clickable { model.source(source.sourceType, source.bankId) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f)); Text(formatMoney(source.totalMinor, currency, language))
                    }
                }
                if (state.filter.period == "MONTH" && budget > 0) {
                    Text(stringResource(R.string.bank_budget_progress, formatMoney(state.sources.sumOf { it.totalMinor }, currency, language), formatMoney(budget, currency, language)))
                    LinearProgressIndicator(progress = { (state.sources.sumOf { it.totalMinor }.toDouble() / budget).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            } }
            item { ContentCard {
                Text(stringResource(R.string.bank_category_stats), style = MaterialTheme.typography.titleMedium)
                state.categories.forEach { cat ->
                    val color = cat.colorHex?.let { Color(0xFF000000L or it.removePrefix("#").toLong(16)) } ?: palette.category(cat.category, false)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).background(color, CircleShape))
                        Text(cat.customName ?: stringResource(cat.category.label()), Modifier.weight(1f))
                        Text(formatMoney(cat.totalMinor, currency, language))
                    }
                    LinearProgressIndicator(progress = { if (total == 0L) 0f else (cat.totalMinor.toDouble() / total).toFloat() }, modifier = Modifier.fillMaxWidth(), color = color)
                }
                TextButton(onClick = { categoryDialog = true }) { Text(stringResource(R.string.bank_new_category)) }
                custom.forEach { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            if (reviews.isNotEmpty()) item { Text(stringResource(R.string.bank_review_count, reviews.size), style = MaterialTheme.typography.titleLarge) }
            items(reviews, key = { "review-" + it.id }) { item -> ContentCard {
                // Bank name + merchant — can be long, allow 2 lines
                Text(
                    Banks.name(item.bankId) + " · " + item.merchant.ifBlank { "—" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    formatMoney(item.amountMinor, item.currency, language),
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.type(item.type),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Instant.ofEpochMilli(item.eventAtMillis).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.forLanguageTag(language.tag))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.cardLast4?.let { Text("•••• $it", style = MaterialTheme.typography.bodySmall) }
                if (!item.timeFromMessage) Text(stringResource(R.string.bank_time_received), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (item.transfer) Text(stringResource(R.string.bank_transfer_review), style = MaterialTheme.typography.bodySmall)
                // Duplicate warnings
                when (item.status) {
                    "DUPLICATE_REVIEW" -> Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.bank_duplicate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    "CROSS_DUPLICATE_REVIEW" -> Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SwapHoriz, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text("Можливий дублікат з іншого банку/гаманця", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.accept(item.id, EntryType.EXPENSE) }) { Text(stringResource(R.string.expenses), color = palette.type(EntryType.EXPENSE)) }
                    TextButton(onClick = { model.accept(item.id, EntryType.INCOME) }) { Text(stringResource(R.string.income), color = palette.type(EntryType.INCOME)) }
                    TextButton(onClick = { model.ignore(item.id) }) { Text(stringResource(R.string.bank_ignore)) }
                }
            } }

            item { Text(stringResource(R.string.bank_recent), style = MaterialTheme.typography.titleLarge) }
            items(state.entries, key = { it.id }) { entry -> ContentCard(Modifier.clickable { onEdit(entry.id) }) {
                Text(entry.title)
                Text(formatMoney(entry.amountMinor, entry.currency, language), color = palette.type(entry.type))
                Text(formatDate(entry.date, language) + " · " + if (entry.sourceType == "CASH") stringResource(R.string.bank_cash) else Banks.name(entry.bankId))
            } }
            item { Text(stringResource(R.string.bank_limit_recent), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { ContentCard {
                Text(stringResource(R.string.bank_budget), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(budgetText, { budgetText = it.take(16) }, singleLine = true, label = { Text(currency.name) }, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = Money.parseMinor(budgetText) != null, onClick = { model.setBudget(currency, budgetText) }) { Text(stringResource(R.string.save_entry)) }
                if (budget > 0) TextButton(onClick = { model.resetBudget(currency) }) { Text(stringResource(R.string.bank_budget_disable)) }
                OutlinedButton(onClick = { export.launch("TG-Ledger-transactions.csv") }) { Text(stringResource(R.string.bank_export)) }
                TextButton(onClick = { clearDialog = true }) { Text(stringResource(R.string.bank_clear_history)) }
            } }
        }
    }
    if (pasteDialog) {
        var bankId by remember { mutableStateOf(Banks.all.first().id) }
        var body by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }
        val selected = Banks.all.first { it.id == bankId }
        val preview = remember(bankId, body) { BankParser.parse(selected, body, System.currentTimeMillis()) }
        AlertDialog(onDismissRequest = { if (!submitting) pasteDialog = false }, title = { Text(stringResource(R.string.import_paste)) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Banks.all.forEach { bank ->
                    FilterChip(selected = bank.id == bankId, onClick = { bankId = bank.id }, label = { Text(bank.name) })
                } }
                OutlinedTextField(body, { body = it.take(8000) }, label = { Text(stringResource(R.string.import_text)) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, enabled = !submitting)
                if (preview != null) {
                    Text(formatMoney(preview.amountMinor, preview.currency, language), style = MaterialTheme.typography.titleLarge)
                    Text(preview.merchant.ifBlank { selected.name })
                    Text(stringResource(if (preview.type == EntryType.INCOME) R.string.income else R.string.expenses), color = palette.type(preview.type))
                    preview.cardLast4?.let { Text("•••• $it") }
                } else if (body.isNotBlank()) Text(stringResource(R.string.import_unrecognized), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.import_manual_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(enabled = preview != null && !submitting, onClick = {
            submitting = true
            scope.launch {
                try { if (withContext(Dispatchers.IO) { repository.importText(bankId, body) }) { body = ""; pasteDialog = false } else exportFailed = true }
                catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { exportFailed = true }
                finally { submitting = false }
            }
        }) { Text(stringResource(R.string.import_to_queue)) } }, dismissButton = { TextButton(enabled = !submitting, onClick = { body = ""; pasteDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (packageDialog) {
        var bankId by remember { mutableStateOf(Banks.all.first().id) }
        var packageName by remember(bankId) { mutableStateOf(repository.configuredPackage(bankId)) }
        AlertDialog(onDismissRequest = { packageDialog = false }, title = { Text(stringResource(R.string.import_package_settings)) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_package_warning))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Banks.all.forEach { bank ->
                    FilterChip(selected = bank.id == bankId, onClick = { bankId = bank.id }, label = { Text(bank.name) })
                } }
                OutlinedTextField(packageName, { packageName = it.trim().take(200) }, singleLine = true, label = { Text("package · com.example.bank") })
                Text(diagnostics.lastPackage, style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")), onClick = { model.configurePackage(bankId, packageName); packageDialog = false }) { Text(stringResource(R.string.save_entry)) } },
            dismissButton = { TextButton(onClick = { packageDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (categoryDialog) {
        var name by remember { mutableStateOf("") }; var hex by remember { mutableStateOf("#6FAEFF") }
        AlertDialog(onDismissRequest = { categoryDialog = false }, title = { Text(stringResource(R.string.bank_new_category)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.category)) }, singleLine = true)
                OutlinedTextField(hex, { hex = it.take(7) }, label = { Text("HEX · #RRGGBB") }, singleLine = true, isError = ColorPreferences.normalizeHex(hex) == null)
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank() && ColorPreferences.normalizeHex(hex) != null, onClick = { model.createCategory(name, hex); categoryDialog = false }) { Text(stringResource(R.string.save_entry)) } },
            dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text(stringResource(R.string.bank_clear_history)) },
        text = { Text(stringResource(R.string.bank_clear_warning)) }, confirmButton = { TextButton(onClick = { model.clearHistory(); clearDialog = false }) { Text(stringResource(R.string.bank_clear_history)) } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.cancel)) } })
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text(stringResource(R.string.bank_privacy)) },
        text = { Text(stringResource(R.string.bank_privacy_body)) }, confirmButton = { TextButton(onClick = { privacy = false }) { Text(stringResource(R.string.done)) } })
}

private fun importReason(code: String): Int = when (code) {
    "DISABLED" -> R.string.import_disabled
    "UNSUPPORTED_APP" -> R.string.import_unsupported
    "EMPTY_TEXT" -> R.string.import_empty
    "GROUP_SUMMARY" -> R.string.import_group
    "UNRECOGNIZED" -> R.string.import_unrecognized
    "AUTO_IMPORTED" -> R.string.import_auto_done
    "QUEUED", "REVIEW", "CROSS_DUPLICATE_REVIEW" -> R.string.import_queued
    "DUPLICATE_REVIEW" -> R.string.bank_duplicate
    "REDELIVERED" -> R.string.import_repeated
    "ERROR" -> R.string.storage_error
    else -> R.string.import_waiting
}
