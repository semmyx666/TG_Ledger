package com.semmyx.ledger.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.semmyx.ledger.history.*
import java.time.YearMonth
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.semmyx.ledger.platform.HighRefreshRate
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.semmyx.ledger.AppContainer
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.ThemeMode
import com.semmyx.ledger.platform.BiometricUnlocker
import com.semmyx.ledger.support.SupportNavigator
import com.semmyx.ledger.auth.*
import com.semmyx.ledger.common.*
import com.semmyx.ledger.dashboard.*
import com.semmyx.ledger.analytics.*
import com.semmyx.ledger.bank.*
import com.semmyx.ledger.editor.*
import com.semmyx.ledger.onboarding.OnboardingScreen
import com.semmyx.ledger.settings.*
import com.semmyx.ledger.support.*
import com.semmyx.ledger.theme.*

@Composable
fun AppRoot(host: FragmentActivity, model: AppViewModel, container: AppContainer) {
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides host, LocalOnBackPressedDispatcherOwner provides host) {
        AppRootContent(host, model, container)
    }
}

@Composable
private fun AppRootContent(host: FragmentActivity, model: AppViewModel, container: AppContainer) {
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(state.preferences.refreshRateHz) {
        HighRefreshRate.request(host, state.preferences.refreshRateHz)
        onPauseOrDispose { }
    }
    var supportVisible by remember { mutableStateOf(false) }
    // Registered unconditionally here, in AppRoot's very first composition (before the PIN
    // screen even shows), never inside the Dashboard graph. An Activity Result launcher must be
    // registered before the host Activity passes STARTED; the Dashboard route is only entered
    // after the user unlocks with their PIN -- long after the Activity is already STARTED -- so
    // registering it there always throws "LifecycleOwners must call register before they are
    // STARTED", whether that registration sits inside a lazy list item or at the top of the
    // screen. Only the *handling* of a result is Dashboard-specific, so that part is supplied
    // later via a mutable callback the Dashboard route swaps in once it has real data.
    var onSpeechResult by remember { mutableStateOf<(Intent?) -> Unit>({}) }
    var onMicPermissionResult by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val speechRecognizerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> onSpeechResult(result.data) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onMicPermissionResult(granted) }
    LocalizedContent(state.preferences.language) {
        LedgerTheme(state.preferences.theme, state.preferences.colorOverrides) {
            val dark = when (state.preferences.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false
            }
            SideEffect {
                WindowCompat.getInsetsController(host.window, host.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // Never cross-fade private content into a lock screen.
                when {
                    !state.ready -> LoadingState()
                    state.failed -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.security_error), stringResource(R.string.retry), model::retry)
                        TextButton(onClick = { supportVisible = true }) { Text(stringResource(R.string.need_help)) }
                    }
                    !state.preferences.onboardingComplete -> OnboardingScreen(model::completeOnboarding)
                    !state.unlocked -> AuthenticationRoute(host, container, state, onUnlock = {
                        if (host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.unlock()
                    }, onSupport = { supportVisible = true })
                    else -> UnlockedGraph(host, container, state, model::lock, { container.billing.launchPurchase(host) }, { supportVisible = true },
                        speechRecognizerLauncher, micPermissionLauncher, { onSpeechResult = it }, { onMicPermissionResult = it })
                }
                if (supportVisible) SupportSheet(onDismiss = { supportVisible = false }, onContact = {
                    supportVisible = false
                    SupportNavigator.open(host)
                })
            }
        }
    }
}

@Composable
private fun AuthenticationRoute(host: FragmentActivity, container: AppContainer, appState: AppState, onUnlock: () -> Unit, onSupport: () -> Unit) {
    // The auth-attempt owner is disposed on background lock; cancellation wipes PIN buffers.
    val store = remember(appState.lockEpoch) { ViewModelStore() }
    val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }
    DisposableEffect(store) { onDispose { store.clear() } }
    val model: AuthViewModel = viewModel(viewModelStoreOwner = owner, factory = LedgerViewModelFactory(AuthViewModel::class.java) {
        AuthViewModel(!appState.hasPin, container.security)
    })
    val auth by model.state.collectAsStateWithLifecycle()
    val unlocker = remember(store, host) { BiometricUnlocker(host, container.biometricKeys) }
    DisposableEffect(unlocker) { onDispose { unlocker.cancel() } }
    LaunchedEffect(model) { model.unlocked.collect { onUnlock() } }
    val title = stringResource(R.string.biometric_title)
    val subtitle = stringResource(R.string.biometric_subtitle)
    val negative = stringResource(R.string.use_pin)
    AuthenticationScreen(auth, appState.preferences.biometricsEnabled && container.biometricAvailable(), model::digit, model::erase,
        model::startOver, onBiometric = { unlocker.authenticate(title, subtitle, negative, onUnlock, model::biometricFailed) }, onSupport = onSupport)
}

@Composable
private fun UnlockedGraph(host: FragmentActivity, container: AppContainer, appState: AppState, onLock: () -> Unit, onPurchase: () -> Unit, onSupport: () -> Unit,
    speechRecognizerLauncher: ActivityResultLauncher<Intent>, micPermissionLauncher: ActivityResultLauncher<String>,
    setOnSpeechResult: ((Intent?) -> Unit) -> Unit, setOnMicPermissionResult: ((Boolean) -> Unit) -> Unit) {
    val nav = rememberNavController()
    val routes = listOf("dashboard", "settings", "help", "analytics", "banks")
    val onNavigate: (Int) -> Unit = { index ->
        // Home is the only screen with the bottom bar. Every other tab opens as its own full screen
        // with a close (X) button; closing, or pressing Back, always returns to Home.
        if (index == 0) nav.popBackStack("dashboard", inclusive = false)
        else nav.navigate(routes[index]) {
            popUpTo("dashboard")
            launchSingleTop = true
        }
    }
    NavHost(navController = nav, startDestination = "dashboard") {
        composable("dashboard") {
            val model: DashboardViewModel = viewModel(factory = LedgerViewModelFactory(DashboardViewModel::class.java) {
                DashboardViewModel(container.entries, container.preferences, container.premium, container.observeDashboard, container.clock, container.saveEntry)
            })
            DashboardRoute(model, host, onNavigate, onAdd = { nav.navigate("entry/new") }, onEdit = { nav.navigate("entry/$it") },
                speechRecognizerLauncher = speechRecognizerLauncher, micPermissionLauncher = micPermissionLauncher,
                setOnSpeechResult = setOnSpeechResult, setOnMicPermissionResult = setOnMicPermissionResult)
        }
        composable("settings") {
            val model: SettingsViewModel = viewModel(factory = LedgerViewModelFactory(SettingsViewModel::class.java) {
                SettingsViewModel(container.preferences, container.premium, container.rates, container::configureBiometrics)
            })
            SettingsRoute(model, container.biometricAvailable(), onNavigate, onPurchase, onSupport, onLock)
        }
        composable("help") { HelpScreen(onClose = { onNavigate(0) }, onSupport = onSupport) }
        composable("analytics") {
            val model: AnalyticsViewModel = viewModel(factory = LedgerViewModelFactory(AnalyticsViewModel::class.java) {
                AnalyticsViewModel(container.entries, container.preferences, container.clock)
            })
            AnalyticsRoute(model, onBack = { onNavigate(0) }, onCurrency = { onNavigate(1) },
                onEdit = { nav.navigate("entry/$it") },
                onHistory = { month -> nav.navigate(if (month == null) "history" else "history?month=$month") })
        }
        composable("history?month={month}", arguments = listOf(navArgument("month") { type = NavType.StringType; nullable = true; defaultValue = null })) { backStack ->
            val month = backStack.arguments?.getString("month")?.let { value -> runCatching { YearMonth.parse(value) }.getOrNull() }
            val historyModel: HistoryViewModel = viewModel(factory = LedgerViewModelFactory(HistoryViewModel::class.java) {
                HistoryViewModel(container.entries, container.preferences, container.clock, month)
            })
            HistoryScreen(historyModel, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("entry/$it") })
        }
        composable("banks") {
            val bankModel: BankViewModel = viewModel(factory = LedgerViewModelFactory(BankViewModel::class.java) {
                BankViewModel(container.bankImport, container.preferences, container.clock)
            })
            BankScreen(bankModel, container.bankImport, onBack = { onNavigate(0) }, onAdd = { nav.navigate("entry/new") },
                onEdit = { nav.navigate("entry/$it") })
        }
        composable("entry/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id")?.takeUnless { it == "new" }
            val model: EntryViewModel = viewModel(factory = LedgerViewModelFactory(EntryViewModel::class.java) {
                EntryViewModel(id, container.entries, container.preferences, container.saveEntry, container.clock)
            })
            val categories by container.bankImport.categories().collectAsStateWithLifecycle(initialValue = emptyList())
            EntryRoute(model, appState.preferences.language, onBack = { nav.popBackStack() }, customCategories = categories)
        }
    }
}
