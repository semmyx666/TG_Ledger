package com.semmyx.ledger

import androidx.compose.foundation.layout.*
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.bank.*
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankScreenLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
    private val owner = ViewModelStore()
    @After fun cleanup() { compose.runOnUiThread { owner.clear() } }
    @Test fun bankPeriodsOpenPremiumAndCompactScreenStaysReadable() {
        val access = object : PremiumRepository {
            override val state = MutableStateFlow(PremiumState())
            override suspend fun refresh() {}
            override suspend fun activateDeveloperToken(token: String) = false
            override suspend fun developerInstallationId() = "test"
            override fun clearIssue() {}
        }
        val model = BankViewModel(app.container.bankImport,app.container.preferences,access,app.container.clock)
        owner.put("bank",model)
        var requests = 0
        compose.setContent {
            val registryOwner = LocalActivityResultRegistryOwner.current
            val backOwner = LocalOnBackPressedDispatcherOwner.current
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner, LocalOnBackPressedDispatcherOwner provides backOwner) {
            LocalizedContent(AppLanguage.UKRAINIAN) { LedgerTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.4f)) {
                Box(Modifier.width(320.dp).fillMaxHeight()) {
                    BankScreen(model,app.container.bankImport, {}, {}, {}, { requests++ })
                }
            }
        } } } }
        compose.onNodeWithTag("bankPeriod:WEEK").performClick()
        compose.runOnIdle { assertEquals(1,requests); assertEquals("DAY",model.state.value.filter.period) }
        compose.onNodeWithTag("bankPeriod:MONTH").performClick()
        compose.runOnIdle { assertEquals(2,requests) }
        val file = File(app.getExternalFilesDir(null),"screenshots/bank-320dp-font140-uk.png").also { it.parentFile!!.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        compose.onNodeWithTag("bankSetup").performClick()
        compose.onNodeWithTag("bankList").performScrollToNode(hasText("Push банку (рекомендовано)"))
        compose.onNodeWithText("Push банку (рекомендовано)").assertIsDisplayed()
    }
}
