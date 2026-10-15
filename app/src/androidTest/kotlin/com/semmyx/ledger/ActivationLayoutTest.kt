package com.semmyx.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.settings.DeveloperActivationPanel
import com.semmyx.ledger.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivationLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun activationRemainsReadableAt320dpAndLargeFont() {
        val premium=PremiumState(developerPurchaseEnabled=true,developerInstallationId="A21C-FEA5-E432",
            developerKeyId="1234567890ABCDEF",activationProblem=ActivationProblem.KEY_MISMATCH)
        compose.setContent { LocalizedContent(AppLanguage.UKRAINIAN) { LedgerTheme {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.4f)) {
                Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    DeveloperActivationPanel(premium,"TG2.example",{},false,{},{},{})
                }
            }
        } } }
        compose.onNodeWithTag("activationKeyId").performScrollTo().assertIsDisplayed()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("activationKeyId").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty());assertFalse(layouts.any { it.hasVisualOverflow })
        NativeScreenshots.save(compose.onRoot().captureToImage(),"activation-320dp-font140-uk")
        compose.onNodeWithTag("activateDeveloperCode").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("activationProblem").performScrollTo().assertIsDisplayed()
    }
}
