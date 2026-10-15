package com.semmyx.ledger

import androidx.lifecycle.ViewModelStore
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.settings.SettingsViewModel
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsActivationTest {
    @get:Rule val main = MainDispatcherRule()
    private class Premium : PremiumRepository {
        override val state = MutableStateFlow(PremiumState())
        var result = false
        var calls = 0
        var wait: CompletableDeferred<Unit>? = null
        override suspend fun refresh() {}
        override suspend fun activateDeveloperToken(token: String): Boolean { calls++;wait?.await();return result }
        override suspend fun developerInstallationId() = "A21C-FEA5-E432"
        override fun clearIssue() {}
    }
    private fun model(premium: Premium): SettingsViewModel {
        val preferences=Proxy.newProxyInstance(PreferencesRepository::class.java.classLoader,arrayOf(PreferencesRepository::class.java)) { _, _, _ ->
            MutableStateFlow(UserPreferences())
        } as PreferencesRepository
        val rates=object:RateRepository {
            override val rates=flowOf(emptyList<ExchangeRate>())
            override suspend fun refresh() {}
        }
        return SettingsViewModel(preferences,premium,rates,{})
    }
    @Test fun failedActivationDoesNotClearTheCallersInputButSuccessDoes()=runTest {
        val premium=Premium();val model=model(premium);val store=ViewModelStore().apply { put("settings",model) }
        var cleared=false
        try {
            model.activateDeveloperCode("bad-code") { cleared=true };runCurrent();assertFalse(cleared)
            premium.result=true
            model.activateDeveloperCode("valid-code") { cleared=true };runCurrent();assertTrue(cleared)
        } finally { store.clear() }
    }
    @Test fun repeatedTapWhileVerifyingIsIgnoredAndBusyAlwaysClears()=runTest {
        val premium=Premium().apply { wait=CompletableDeferred() };val model=model(premium)
        val store=ViewModelStore().apply { put("settings",model) }
        val collect=backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        try {
            model.activateDeveloperCode("code");model.activateDeveloperCode("code");runCurrent()
            assertEquals(1,premium.calls);assertTrue(model.state.value.activating)
            premium.wait!!.complete(Unit);runCurrent();assertFalse(model.state.value.activating)
        } finally { collect.cancel();store.clear() }
    }
}
