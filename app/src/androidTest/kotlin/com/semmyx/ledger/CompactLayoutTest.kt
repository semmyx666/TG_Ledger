package com.semmyx.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.dashboard.*
import com.semmyx.ledger.settings.RestorePurchasesAction
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.theme.*
import java.io.File
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactLayoutTest {
    @get:Rule val compose = createComposeRule()
    private fun screenshot(name: String) { NativeScreenshots.save(compose.onRoot().captureToImage(),name) }
    private fun content(width: Dp, fontScale: Float) {
        compose.setContent {
            LocalizedContent(AppLanguage.UKRAINIAN) { LedgerTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                    Column(Modifier.width(width).padding(8.dp)) {
                        CompactBalanceSummary(DashboardState(snapshot = DashboardSnapshot(balance = Balance(250000,123450,2)), loading = false))
                        RestorePurchasesAction(true) {}
                    }
                }
            } }
        }
    }
    private fun checkTextFits(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Missing text layout: $tag", layouts.isNotEmpty())
        assertFalse("Text overflow: $tag: ${layouts.map { "size=${it.size} width=${it.didOverflowWidth} height=${it.didOverflowHeight} constraints=${it.layoutInput.constraints}" }}", layouts.any { it.hasVisualOverflow })
        if (tag == "restorePurchases") assertTrue("Restore label squeezed into letters", layouts.all { it.lineCount <= 2 })
    }
    @Test fun smallPhoneHasOneBalanceCardAndReadableRestore() {
        content(288.dp, 1f)
        compose.onAllNodesWithTag("balanceSummary").assertCountEquals(1)
        screenshot("layout-precheck-${System.currentTimeMillis()}")
        listOf("totalBalance","totalIncome","totalExpenses").forEach(::checkTextFits)
        // Button merges its text semantics; use the descendant Text to inspect actual line breaks.
        compose.onNodeWithText("Відновити покупки",useUnmergedTree=true).assertIsDisplayed()
        screenshot("compact-288dp-uk")
    }
    @Test fun enlargedSystemFontRemainsWithinPhoneBounds() {
        content(320.dp, 1.7f)
        screenshot("layout-precheck-${System.currentTimeMillis()}")
        listOf("totalBalance","totalIncome","totalExpenses").forEach(::checkTextFits)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Відновити покупки",useUnmergedTree=true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("Restore overflow: ${layouts.map { "size=${it.size} width=${it.didOverflowWidth} height=${it.didOverflowHeight} constraints=${it.layoutInput.constraints}" }}", layouts.any { it.hasVisualOverflow })
        assertTrue(layouts.all { it.lineCount <= 2 })
        screenshot("compact-320dp-font170-uk")
    }
    @Test fun normalPhoneUsesThreeCompactMetrics() {
        content(360.dp, 1f)
        val positions = listOf("totalBalance","totalIncome","totalExpenses")
            .map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.left }
        assertTrue("Expected three horizontal metrics, viewport may be too narrow: $positions",
            positions[0] < positions[1] && positions[1] < positions[2])
        screenshot("layout-precheck-${System.currentTimeMillis()}")
        listOf("totalBalance","totalIncome","totalExpenses").forEach(::checkTextFits)
        screenshot("compact-360dp-uk")
    }
}
