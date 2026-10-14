package com.semmyx.ledger.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
import com.semmyx.ledger.cat.CatAssistantCard
import com.semmyx.ledger.cat.trackCatTouch
import android.widget.Toast
import com.semmyx.ledger.voice.OptionalVoiceAssistant
import com.semmyx.ledger.voice.VoiceCommand
import com.semmyx.ledger.voice.VoiceEntryParser
import com.semmyx.ledger.voice.VoiceSmallTalk
import java.time.LocalDate
import com.semmyx.ledger.voice.VoiceFinanceQuery
import com.semmyx.ledger.voice.SpokenMoneyFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(model: DashboardViewModel, host: FragmentActivity, onNavigate: (Int) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit,
    speechRecognizerLauncher: ActivityResultLauncher<Intent>, micPermissionLauncher: ActivityResultLauncher<String>,
    setOnSpeechResult: ((Intent?) -> Unit) -> Unit, setOnMicPermissionResult: ((Boolean) -> Unit) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(model, context) {
        model.events.collect { event ->
            when (event) {
                is DashboardEvent.Deleted -> {
                    val result = snackbar.showSnackbar(context.getString(R.string.entry_deleted), context.getString(R.string.undo),
                        withDismissAction = true, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) model.undo(event.token)
                }
                is DashboardEvent.Message -> snackbar.showSnackbar(context.getString(event.message.label()))
            }
        }
    }
    DashboardScreen(state, snackbar, host, onNavigate, onAdd, onEdit, model::delete, model::search, model::filter, model::days, model::loadMore, model::retry,
        model::addVoiceEntry, speechRecognizerLauncher, micPermissionLauncher, setOnSpeechResult, setOnMicPermissionResult,
        onCategory = model::category, onMoreCategory = model::loadMoreCategory,
        onStatistics = { request, reply -> scope.launch {
            try { reply(model.answerStatistics(request)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.storage_error)) }
        } })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(state: DashboardState, snackbar: SnackbarHostState, host: FragmentActivity, onNavigate: (Int) -> Unit,
    onAdd: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit, onSearch: (String) -> Unit,
    onFilter: (EntryType?) -> Unit, onDays: (Int) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit,
    onVoiceEntry: (com.semmyx.ledger.voice.VoiceEntryDraft, String) -> Unit,
    speechRecognizerLauncher: ActivityResultLauncher<Intent>, micPermissionLauncher: ActivityResultLauncher<String>,
    setOnSpeechResult: ((Intent?) -> Unit) -> Unit, setOnMicPermissionResult: ((Boolean) -> Unit) -> Unit,
    onCategory: (Category?) -> Unit, onMoreCategory: () -> Unit,
    onStatistics: (VoiceFinanceQuery.Statistics, (Long) -> Unit) -> Unit) {
    var categoryDetailType by remember { mutableStateOf(EntryType.EXPENSE) }
    if (state.selectedCategory != null) CategoryDetails(state, initialType = categoryDetailType,
        onClose = { onCategory(null) }, onDays = onDays, onMore = onMoreCategory,
        onEdit = onEdit, onSelectCategory = { onCategory(it) })
    Scaffold(
        topBar = { TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerMark(Modifier.size(24.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            }
        }, actions = {
            TextButton(onClick = { onNavigate(1) }) { Text(state.preferences.currency.name) }
        }) },
        bottomBar = { LedgerBottomBar(0, onNavigate) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) },
            text = { Text(stringResource(R.string.add_entry)) }, modifier = Modifier.testTag("addEntry")) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // The two Activity Result launchers are registered once, unconditionally, at the very top
        // of AppRoot -- before the PIN screen even shows -- because a launcher must be registered
        // before the host Activity passes STARTED, and this Dashboard route is only entered after
        // PIN unlock, long after STARTED. Only the *handling* of a result is Dashboard-specific
        // (it needs today's balance/spend and the current language), so that part is wired up
        // below via a plain callback that AppRoot invokes; SideEffect below keeps it pointed at the
        // latest state on every recomposition. The speech engine itself is still created lazily on
        // first use, so free-tier and opted-out users still pay no extra cost.
        val voiceContext = LocalContext.current
        var voiceAssistant by remember(host) { mutableStateOf<OptionalVoiceAssistant?>(null) }
        var listening by remember { mutableStateOf(false) }
        var lastReply by remember { mutableStateOf<String?>(null) }
        DisposableEffect(host) { onDispose { voiceAssistant?.shutdown() } }
        fun ensureAssistant(): OptionalVoiceAssistant = voiceAssistant ?: OptionalVoiceAssistant(host).also { voiceAssistant = it }
        val launchRecognizer: () -> Unit = launch@{
            if (!OptionalVoiceAssistant.isAvailable(voiceContext)) {
                lastReply = voiceContext.getString(R.string.voice_recognizer_unavailable)
                Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_recognizer_unavailable), Toast.LENGTH_LONG).show()
                return@launch
            }
            val assistant = ensureAssistant()
            assistant.stopSpeaking()
            listening = true
            try {
                speechRecognizerLauncher.launch(assistant.recognizerIntent(state.preferences.language))
            } catch (_: android.content.ActivityNotFoundException) {
                listening = false
                lastReply = voiceContext.getString(R.string.voice_recognizer_unavailable)
                Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_recognizer_unavailable), Toast.LENGTH_LONG).show()
            }
        }
        // Speech results are handled in one place: first try to read the phrase as a whole ledger
        // entry ("I bought 1 kg of tomatoes for 100 UAH"), and only fall back to the small set of
        // fixed commands (balance / spent today / open add-entry) when no amount could be found.
        var pendingVoice by remember { mutableStateOf<com.semmyx.ledger.voice.VoiceEntryDraft?>(null) }
        val finishVoiceEntry: (com.semmyx.ledger.voice.VoiceEntryDraft, String) -> Unit = { ready, source ->
            pendingVoice = null
            onVoiceEntry(ready, source)
            val language = state.preferences.language
            val typeLabel = voiceContext.getString(if (ready.type == EntryType.INCOME) R.string.income else R.string.expense)
            val reply = voiceContext.getString(R.string.voice_entry_added, typeLabel,
                formatMoney(ready.amountMinor, state.preferences.currency, language), ready.title, voiceContext.getString(ready.category.label()))
            lastReply = reply
            ensureAssistant().speak(reply, language)
        }
        pendingVoice?.let { ready ->
            AlertDialog(onDismissRequest = { pendingVoice = null }, title = { Text(stringResource(R.string.voice_ask_source)) },
                text = { Text(ready.title + " · " + formatMoney(ready.amountMinor, state.preferences.currency, state.preferences.language)) },
                confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { finishVoiceEntry(ready, "CARD") }, modifier = Modifier.testTag("voiceSourceCard")) { Text(stringResource(R.string.bank_card)) }
                    Button(onClick = { finishVoiceEntry(ready, "CASH") }, modifier = Modifier.testTag("voiceSourceCash")) { Text(stringResource(R.string.bank_cash)) }
                } },
                dismissButton = { TextButton(onClick = { pendingVoice = null }) { Text(stringResource(R.string.cancel)) } })
        }
        val processInput: (String?) -> Unit = { heard ->
                listening = false
                val assistant = ensureAssistant()
                val language = state.preferences.language
                val query = heard?.let { VoiceFinanceQuery.parse(it) }
                val draft = if (query == null) heard?.let { VoiceEntryParser.parse(it) } else null
                when {
                    query == VoiceFinanceQuery.Balance -> {
                        val reply = voiceContext.getString(R.string.voice_reply_balance,
                            SpokenMoneyFormatter.format(state.snapshot.balance.netMinor, state.preferences.currency, language))
                        lastReply = reply; assistant.speak(reply, language)
                    }
                    query is VoiceFinanceQuery.Statistics -> onStatistics(query) { amount ->
                        val period = voiceContext.getString(when (query.period) {
                            VoiceFinanceQuery.Period.TODAY -> R.string.period_today
                            VoiceFinanceQuery.Period.WEEK -> R.string.last_7_days
                            VoiceFinanceQuery.Period.MONTH -> R.string.period_month
                            VoiceFinanceQuery.Period.YEAR -> R.string.period_year
                            VoiceFinanceQuery.Period.ALL -> R.string.all_time
                        })
                        val category = query.category?.let { voiceContext.getString(it.label()) } ?: voiceContext.getString(R.string.all_categories)
                        val kind = voiceContext.getString(if (query.type == EntryType.EXPENSE) R.string.expenses else R.string.income)
                        val reply = voiceContext.getString(R.string.voice_statistics, kind, period, category,
                            SpokenMoneyFormatter.format(amount, state.preferences.currency, language))
                        lastReply = reply; assistant.speak(reply, language)
                    }
                    draft != null -> {
                        val title = draft.title.ifBlank { voiceContext.getString(draft.category.label()) }
                        val ready = draft.copy(title = title)
                        val said = heard?.let { VoiceEntryParser.detectSource(it) }
                        if (said != null) finishVoiceEntry(ready, said)
                        else {
                            // The cat always needs to know how it was paid before it writes the entry.
                            pendingVoice = ready
                            val ask = voiceContext.getString(R.string.voice_ask_source)
                            lastReply = ask
                            assistant.speak(ask, language)
                        }
                    }
                    heard != null -> when (OptionalVoiceAssistant.classify(heard)) {
                        VoiceCommand.ShowBalance -> {
                            val reply = voiceContext.getString(R.string.voice_reply_balance,
                                SpokenMoneyFormatter.format(state.snapshot.balance.netMinor, state.preferences.currency, language))
                            lastReply = reply
                            assistant.speak(reply, language)
                        }
                        VoiceCommand.ShowSpentToday -> {
                            val spentToday = state.snapshot.activity.find { it.date == LocalDate.now() }?.expenseMinor ?: 0L
                            val reply = voiceContext.getString(R.string.voice_reply_spent_today,
                                formatMoney(spentToday, state.preferences.currency, language))
                            lastReply = reply
                            assistant.speak(reply, language)
                        }
                        VoiceCommand.OpenAddEntry -> {
                            val reply = voiceContext.getString(R.string.voice_reply_opening_entry)
                            lastReply = reply
                            assistant.speak(reply, language)
                            onAdd()
                        }
                        null -> {
                            // Not one of the fixed commands either -- try small talk ("hi", "how
                            // are you", "thanks", ...) before giving up, so the cat feels like
                            // something you can actually talk with, not just issue commands to.
                            val reply = smallTalkReply(voiceContext, heard)
                                ?: voiceContext.getString(R.string.voice_reply_not_understood)
                            lastReply = reply
                            assistant.speak(reply, language)
                        }
                    }
                    else -> {
                        val reply = voiceContext.getString(R.string.voice_reply_not_understood)
                        lastReply = reply
                        assistant.speak(reply, language)
                    }
                }
            }
        SideEffect {
            setOnSpeechResult { data ->
                if (state.premium && state.preferences.premiumAssistantEnabled && data != null)
                    processInput(ensureAssistant().extractSpokenText(data))
                else listening = false
            }
            setOnMicPermissionResult { granted ->
                if (granted) launchRecognizer()
                else {
                    listening = false
                    Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
        }
        DisposableEffect(host) {
            onDispose { setOnSpeechResult {}; setOnMicPermissionResult {} }
        }
        val onVoice: () -> Unit = {
            if (!OptionalVoiceAssistant.isAvailable(voiceContext)) {
                Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_recognizer_unavailable), Toast.LENGTH_LONG).show()
            } else if (ContextCompat.checkSelfPermission(voiceContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                launchRecognizer()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Box(Modifier.fillMaxSize().trackCatTouch().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loading) LoadingState()
            else if (state.failed) EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.storage_error), stringResource(R.string.retry), onRetry)
            else LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("ledgerList"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "greeting", contentType = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (state.preferences.displayName.isBlank()) stringResource(R.string.dashboard_heading)
                            else stringResource(R.string.greeting, state.preferences.displayName), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.dashboard_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "balance", contentType = "summary") { CompactBalanceSummary(state) }
                // The cat is always on the home screen; without Premium it is shown locked and opens the Premium card.
                if (!state.premium || state.preferences.premiumAssistantEnabled) {
                    item(key = "assistant", contentType = "assistant") {
                        CatAssistantCard(state.preferences.language, listening, lastReply,
                            onTalk = if (state.premium) onVoice else ({ onNavigate(1) }),
                            skin = state.preferences.mascotSkin, animations = state.preferences.mascotAnimations,
                            onText = { processInput(it) }, balanceMinor = state.snapshot.balance.netMinor, locked = !state.premium)
                    }
                }

                item(key = "chart", contentType = "chart") {
                    ActivityChart(state.snapshot.activity, state.preferences.currency, state.preferences.language, state.premium,
                        state.days, onDays, onPremium = { onNavigate(1) })
                }
                item(key = "categories", contentType = "summary") {
                    CategorySummary(state, onCategory = { category, type -> categoryDetailType = type; onCategory(category) },
                        onMore = { onNavigate(3) })
                }
                item(key = "search", contentType = "filter") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.activity), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(value = state.query, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().testTag("searchEntries"),
                            singleLine = true, label = { Text(stringResource(R.string.search_entries)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, stringResource(R.string.clear_search)) } })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = state.type == null, onClick = { onFilter(null) }, label = { Text(stringResource(R.string.all)) })
                            FilterChip(colors = FilterChipDefaults.filterChipColors(labelColor = MaterialTheme.colorScheme.secondary, selectedLabelColor = MaterialTheme.colorScheme.secondary, selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .18f)), selected = state.type == EntryType.INCOME, onClick = { onFilter(EntryType.INCOME) }, label = { Text(stringResource(R.string.income)) })
                            FilterChip(colors = FilterChipDefaults.filterChipColors(labelColor = MaterialTheme.colorScheme.tertiary, selectedLabelColor = MaterialTheme.colorScheme.tertiary, selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .18f)), selected = state.type == EntryType.EXPENSE, onClick = { onFilter(EntryType.EXPENSE) }, label = { Text(stringResource(R.string.expenses)) })
                        }
                        Text(stringResource(R.string.search_scope), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.snapshot.entries.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        val filtered = state.query.isNotBlank() || state.type != null
                        ContentCard {
                            EmptyState(stringResource(if (filtered) R.string.no_matches else R.string.empty_ledger_title),
                                stringResource(if (filtered) R.string.no_matches_body else R.string.empty_ledger_body),
                                stringResource(if (filtered) R.string.clear_filters else R.string.add_first_entry),
                                onAction = { if (filtered) { onSearch(""); onFilter(null) } else onAdd() })
                        }
                    }
                }
                items(state.snapshot.entries, key = { it.id }, contentType = { "entry" }) { entry ->
                    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) onDelete(entry.id)
                        false // Room owns removal; never leave an off-screen row after a failed delete.
                    })
                    SwipeToDismissBox(state = dismissState, enableDismissFromStartToEnd = false,
                        modifier = Modifier.animateItem(), backgroundContent = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(24.dp),
                                contentAlignment = Alignment.CenterEnd) {
                                Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_entry), tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }) {
                        EntryRow(entry, state.preferences.language, { onEdit(entry.id) }, { onDelete(entry.id) })
                    }
                }
                if (state.canLoadMore) item(key = "more", contentType = "action") {
                    OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
                }
            }
        }
    }
}

// Turns a small-talk topic into the actual reply text, localized to the app's current EN/UK
// language via string resources (VoiceSmallTalk itself only recognizes the topic, it never
// produces user-facing text). Returns null when the phrase was not small talk at all, so the
// caller can fall back to "I didn't understand that".
private fun smallTalkReply(context: android.content.Context, heard: String): String? =
    when (VoiceSmallTalk.classify(heard)) {
        VoiceSmallTalk.Topic.GREETING -> context.getString(R.string.voice_smalltalk_greeting)
        VoiceSmallTalk.Topic.HOW_ARE_YOU -> context.getString(R.string.voice_smalltalk_how_are_you)
        VoiceSmallTalk.Topic.THANKS -> context.getString(R.string.voice_smalltalk_thanks)
        VoiceSmallTalk.Topic.WHO_ARE_YOU -> context.getString(R.string.voice_smalltalk_who_are_you)
        VoiceSmallTalk.Topic.BYE -> context.getString(R.string.voice_smalltalk_bye)
        VoiceSmallTalk.Topic.COMPLIMENT -> context.getString(R.string.voice_smalltalk_compliment)
        VoiceSmallTalk.Topic.JOKE -> context.getString(R.string.voice_smalltalk_joke)
        null -> null
    }

/** One compact card instead of a tall balance card and two separate totals. */
@Composable
fun CompactBalanceSummary(state: DashboardState) {
    ContentCard(Modifier.testTag("balanceSummary")) {
        val balance = state.snapshot.balance
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.3f &&
                listOf(balance.netMinor, balance.incomeMinor, balance.expenseMinor).all {
                    formatMoney(it, state.preferences.currency, state.preferences.language).length <= 14
                }
            @Composable fun Metric(label: Int, amount: Long, color: Color, tag: String, modifier: Modifier = Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicText(formatMoney(amount, state.preferences.currency, state.preferences.language),
                        style = MaterialTheme.typography.titleMedium.copy(color = color),
                        maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp, stepSize = 1.sp),
                        modifier = Modifier.fillMaxWidth().testTag(tag))
                }
            }
            if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(R.string.total_balance, balance.netMinor, MaterialTheme.colorScheme.primary, "totalBalance", Modifier.weight(1f))
                Metric(R.string.income, balance.incomeMinor, MaterialTheme.colorScheme.secondary, "totalIncome", Modifier.weight(1f))
                Metric(R.string.expenses, balance.expenseMinor, MaterialTheme.colorScheme.tertiary, "totalExpenses", Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(R.string.total_balance, balance.netMinor, MaterialTheme.colorScheme.primary, "totalBalance")
                // Each metric gets the entire line at large system font sizes.
                Metric(R.string.income, balance.incomeMinor, MaterialTheme.colorScheme.secondary, "totalIncome")
                Metric(R.string.expenses, balance.expenseMinor, MaterialTheme.colorScheme.tertiary, "totalExpenses")
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry, language: AppLanguage, onEdit: () -> Unit, onDelete: () -> Unit) {
    val categoryTint = LocalAppearance.current.category(entry.category, entry.type == EntryType.INCOME)
    val isIncome = entry.type == EntryType.INCOME
    Card(
        modifier  = Modifier.fillMaxWidth().testTag("entry:${entry.id}"),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Category icon in a soft pill — fixed size, never squishes
            Surface(shape = MaterialTheme.shapes.medium, color = categoryTint.copy(alpha = .14f)) {
                Icon(entry.category.icon(), null, Modifier.padding(11.dp).size(22.dp), tint = categoryTint)
            }
            // Title + date — weight(1f) ensures it never pushes the amount off-screen
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${stringResource(entry.category.label())} · ${formatDate(entry.date, language)}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Amount + delete icon — right-aligned, amounts never wrap
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(
                        if (isIncome) R.string.positive_amount else R.string.negative_amount,
                        formatMoney(entry.amountMinor, entry.currency, language),
                    ),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = if (isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_named_entry, entry.title),
                    modifier = Modifier.size(16.dp).clickable(onClick = onDelete),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
                )
            }
        }
    }
}

