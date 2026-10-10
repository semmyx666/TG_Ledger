package com.semmyx.ledger.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.UiMessage
import com.semmyx.ledger.common.guarded
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class SettingsState(
    val preferences: UserPreferences = UserPreferences(),
    val premium: PremiumState = PremiumState(),
    val rates: List<ExchangeRate> = emptyList(),
    val refreshing: Boolean = false,
    val failed: Boolean = false,
)

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val premium: PremiumRepository,
    private val rates: RateRepository,
    private val configureBiometrics: suspend (Boolean) -> Unit,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val eventChannel = Channel<UiMessage>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val state = combine(preferences.preferences, premium.state, rates.rates, refreshing) { prefs, access, rates, busy ->
        SettingsState(prefs, access, rates, busy)
    }.catch { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        emit(SettingsState(failed = true))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    fun saveName(value: String) {
        if (value.trim().length > 40) { eventChannel.trySend(UiMessage.NAME_INVALID); return }
        change { preferences.setName(value); eventChannel.send(UiMessage.SAVED) }
    }
    fun currency(value: LedgerCurrency) = change { preferences.setCurrency(value) }
    fun theme(value: ThemeMode) = change { preferences.setTheme(value) }
    fun language(value: AppLanguage) = change { preferences.setLanguage(value) }
    fun biometrics(enabled: Boolean) {
        guarded(onError = { eventChannel.trySend(UiMessage.BIOMETRIC_UNAVAILABLE) }) {
            configureBiometrics(enabled)
            preferences.setBiometrics(enabled)
        }
    }
    /** Only meaningful while entitled; the mascot never renders for free accounts regardless of this flag. */
    fun premiumAssistant(enabled: Boolean) = change { preferences.setPremiumAssistant(enabled) }
    fun color(key: String, hex: String?) = change { preferences.setColor(key, hex) }
    fun resetColors() = change { preferences.resetColors() }
    fun refreshRate(hz: Int) = change { preferences.setRefreshRate(hz) }
    fun skin(value: String) = change { preferences.setMascotSkin(value) }
    fun mascotAnimations(value: Boolean) = change { preferences.setMascotAnimations(value) }
    fun startTrial(days: Int) = change {
        val granted = premium.startTrial(days)
        if (granted) preferences.setPremiumAssistant(true)
        eventChannel.send(if (granted) UiMessage.PREMIUM_ACTIVATED else UiMessage.ACTIVATION_INVALID)
    }
    fun restorePurchases() = change { premium.refresh() }
    /** Verifies a developer-issued Telegram-sale activation code and grants TG Plus offline if valid. */
    fun activateDeveloperCode(code: String) {
        if (code.isBlank()) { eventChannel.trySend(UiMessage.ACTIVATION_INVALID); return }
        guarded(onError = { eventChannel.trySend(UiMessage.STORAGE) }) {
            val granted = premium.activateDeveloperToken(code)
            eventChannel.send(if (granted) UiMessage.PREMIUM_ACTIVATED else UiMessage.ACTIVATION_INVALID)
        }
    }
    fun refreshRates() {
        if (refreshing.value) return
        guarded(onError = { refreshing.value = false; eventChannel.trySend(UiMessage.RATE_OFFLINE) }) {
            refreshing.value = true
            try { rates.refresh() } finally { refreshing.value = false }
        }
    }
    private fun change(block: suspend () -> Unit) {
        guarded(onError = { eventChannel.trySend(UiMessage.STORAGE) }) { block() }
    }
}
