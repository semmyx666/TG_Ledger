package com.semmyx.ledger

import androidx.lifecycle.ViewModelStore
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.auth.AuthViewModel
import com.semmyx.ledger.navigation.AppViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @Test fun appStartsLockedAndLockClearsSession() = runTest {
        val preferences = TestPreferences()
        val model = AppViewModel(preferences, TestSecurity(configured = true))
        val owner = ViewModelStore().apply { put("app", model) }
        try {
            runCurrent()
            assertTrue(model.state.value.ready)
            assertFalse(model.state.value.unlocked)
            model.unlock(); assertTrue(model.state.value.unlocked)
            model.lock(); assertFalse(model.state.value.unlocked)
            assertEquals(1L, model.state.value.lockEpoch)
        } finally { owner.clear() }
    }
    @Test fun preferenceChangesFlowThroughWithoutReplacingTheViewModel() = runTest {
        val preferences = TestPreferences()
        val model = AppViewModel(preferences, TestSecurity(true))
        val owner = ViewModelStore().apply { put("app", model) }
        try {
            runCurrent()
            preferences.setLanguage(AppLanguage.UKRAINIAN)
            runCurrent()
            assertEquals(AppLanguage.UKRAINIAN, model.state.value.preferences.language)
        } finally { owner.clear() }
    }
    @Test fun setupRequiresTwoMatchingPins() = runTest {
        val security = TestSecurity(false)
        val model = AuthViewModel(true, security)
        val owner = ViewModelStore().apply { put("auth", model) }
        var unlocked = false
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.unlocked.collect { unlocked = true } }
        try {
            runCurrent()
            listOf(1, 2, 3, 4, 5, 6).forEach(model::digit)
            assertTrue(model.state.value.confirming)
            assertFalse(unlocked)
            listOf(1, 2, 3, 4, 5, 6).forEach(model::digit)
            runCurrent()
            assertTrue(unlocked)
            assertTrue(security.hasPin())
        } finally { collector.cancel(); owner.clear() }
    }
}

private class TestSecurity(private var configured: Boolean) : SecurityRepository {
    private var code = "123456"
    override suspend fun hasPin() = configured
    override suspend fun createPin(pin: CharArray) { check(!configured); code = pin.concatToString(); pin.fill(0.toChar()); configured = true }
    override suspend fun verifyPin(pin: CharArray): PinResult = try { if (pin.concatToString() == code) PinResult.Success else PinResult.Incorrect } finally { pin.fill(0.toChar()) }
    override suspend fun lockoutSeconds() = 0L
}
private class TestPreferences : PreferencesRepository {
    private val value = MutableStateFlow(UserPreferences(onboardingComplete = true))
    override val preferences = value.asStateFlow()
    override suspend fun setName(name: String) { value.update { it.copy(displayName = name) } }
    override suspend fun setCurrency(currency: LedgerCurrency) { value.update { it.copy(currency = currency) } }
    override suspend fun setTheme(theme: ThemeMode) { value.update { it.copy(theme = theme) } }
    override suspend fun setLanguage(language: AppLanguage) { value.update { it.copy(language = language) } }
    override suspend fun completeOnboarding() { value.update { it.copy(onboardingComplete = true) } }
    override suspend fun setBiometrics(enabled: Boolean) { value.update { it.copy(biometricsEnabled = enabled) } }
    override suspend fun setPremiumAssistant(enabled: Boolean) { value.update { it.copy(premiumAssistantEnabled = enabled) } }
}
