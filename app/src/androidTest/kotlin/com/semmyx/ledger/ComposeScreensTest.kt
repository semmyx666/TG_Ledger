package com.semmyx.ledger

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.domain.AppLanguage
import com.semmyx.ledger.auth.AuthState
import com.semmyx.ledger.auth.AuthenticationScreen
import com.semmyx.ledger.support.SupportSheet
import com.semmyx.ledger.theme.LedgerTheme
import com.semmyx.ledger.theme.LocalizedContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeScreensTest {
    @get:Rule val compose = createComposeRule()
    @Test fun switchingLanguageUpdatesExistingCompositionImmediately() {
        var language by mutableStateOf(AppLanguage.ENGLISH)
        var rememberedToken: Any? = null
        var latestToken: Any? = null
        compose.setContent {
            LocalizedContent(language) {
                LedgerTheme {
                    val token = remember { Any() }
                    SideEffect { if (rememberedToken == null) rememberedToken = token; latestToken = token }
                    Column {
                        Text(stringResource(R.string.settings))
                        Button(onClick = { language = AppLanguage.UKRAINIAN }) { Text("Switch") }
                    }
                }
            }
        }
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Switch").performClick()
        compose.onNodeWithText("Налаштування").assertIsDisplayed()
        compose.runOnIdle { assertSame(rememberedToken, latestToken) }
    }
    @Test fun pinKeypadHasAccessibleButtonsWithoutRevealingThePin() {
        val input = mutableListOf<Int>()
        var erased = false
        var help = false
        compose.setContent { LocalizedContent(AppLanguage.ENGLISH) { LedgerTheme {
            AuthenticationScreen(AuthState(setup = false, digits = 2), false, { input.add(it) }, { erased = true }, { input.clear() }, { input.clear() }, { help = true })
        } } }
        compose.onNodeWithTag("pinKey7").performClick()
        compose.runOnIdle { assertEquals(listOf(7), input) }
        compose.onNodeWithTag("pinDots").assertContentDescriptionEquals("2 of 6 PIN digits entered")
    }
    @Test fun supportCardCopiesHandleAndLaunchesOnlyAfterClick() {
        var contacted = false
        var dismissed = false
        compose.setContent { LocalizedContent(AppLanguage.ENGLISH) { LedgerTheme {
            SupportSheet(onDismiss = { dismissed = true }, onContact = { contacted = true })
        } } }
        compose.runOnIdle { assertFalse(contacted); assertFalse(dismissed) }
        compose.onNodeWithTag("copySupport").performClick()
        compose.onNodeWithText("Username copied").assertIsDisplayed()
        compose.onNodeWithTag("contactTelegram").performClick()
        compose.runOnIdle { assertTrue(contacted) }
    }
    @Test fun helpShowsMajorReleaseAndExplicitlyKeepsBankApi() {
        compose.setContent { LocalizedContent(AppLanguage.UKRAINIAN) { LedgerTheme {
            com.semmyx.ledger.support.HelpScreen({}, {})
        } } }
        compose.onNodeWithTag("releaseHighlights").assertTextEquals("Що нового у " + BuildConfig.VERSION_NAME)
        compose.onNodeWithTag("releaseHighlightsBody").assertTextContains("API monobank збережено",substring=true)
        compose.onNodeWithTag("releaseHighlightsBody").assertTextContains("API ПриватБанку",substring=true)
    }

    @Test fun activationRequestIncludesTheInstalledKeyAndFailedInputIsNotDiscarded() {
        var input by mutableStateOf("bad-code")
        var copied = ""
        var attempts = 0
        val premium = com.semmyx.ledger.domain.PremiumState(developerPurchaseEnabled=true,
            developerInstallationId="AB12-CD34-EF56",developerKeyId="1234567890ABCDEF",
            activationProblem=com.semmyx.ledger.domain.ActivationProblem.KEY_MISMATCH)
        compose.setContent { LocalizedContent(AppLanguage.ENGLISH) { LedgerTheme {
            com.semmyx.ledger.settings.DeveloperActivationPanel(premium,input,{input=it},false,{attempts++},{copied=it},{})
        } } }
        compose.onNodeWithTag("copyActivationRequest").performClick()
        compose.runOnIdle { assertEquals("TGREQ1|AB12-CD34-EF56|1234567890ABCDEF|ledger_plus",copied) }
        compose.onNodeWithTag("activateDeveloperCode").performClick()
        compose.onNodeWithTag("activationCodeField").assertTextContains("bad-code")
        compose.onNodeWithTag("activationProblem").assertTextContains("different signing key",substring=true)
        compose.runOnIdle { assertEquals(1,attempts) }
    }
    @Test fun unconfiguredActivationIsExplainedRatherThanHidden() {
        compose.setContent { LocalizedContent(AppLanguage.UKRAINIAN) { LedgerTheme {
            com.semmyx.ledger.settings.DeveloperActivationPanel(com.semmyx.ledger.domain.PremiumState(),"",{},false,{},{},{})
        } } }
        compose.onNodeWithTag("activationNotConfigured").assertIsDisplayed()
        compose.onNodeWithTag("activateDeveloperCode").assertDoesNotExist()
    }

}
