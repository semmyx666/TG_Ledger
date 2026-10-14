package com.semmyx.ledger

import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.semmyx.ledger.bank.BankNotificationListener
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android listener + another installed app + Room. No open ledger Activity. API 26 and 36. */
@RunWith(AndroidJUnit4::class)
class BankBackgroundImportTest {
    private val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
    private val repo get() = app.container.bankImport
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val listener get() = "${app.packageName}/com.semmyx.ledger.bank.BankNotificationListener"
    private val fixture = "com.semmyx.ledger.bankfixture/.FixtureReceiver"
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .use { it.readBytes().toString(Charsets.UTF_8) }
    private fun waitFor(timeoutMs: Long = 30_000, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (check()) return true; Thread.sleep(250) }
        return check()
    }
    private fun entries() = runBlocking { repo.exportEntries() }
    private fun post(id: Int, cents: Int, at: Long, style: String = "big") {
        val response = shell("am broadcast --receiver-foreground -n $fixture --ei id $id --ei cents $cents --el at $at --es style $style")
        assertFalse("Fixture failed: $response", response.contains("Exception"))
    }
    @Before fun setUp() {
        assertTrue("Install bank-fixture APK first", shell("pm path com.semmyx.ledger.bankfixture").contains("package:"))
        repo.enabled = true; repo.autoImport = true; repo.importMode = "NOTIFY"
        repo.setPackage("mono", "com.semmyx.ledger.bankfixture")
        runBlocking {
            repo.clearHistory()
            entries().filter { it.origin == "PUSH" }.forEach { app.container.entries.delete(it.id) }
        }
        shell("am broadcast -n $fixture --ez cancel true")
        shell("cmd notification allow_listener $listener")
        assertTrue("Listener did not bind", waitFor { repo.diagnostics.value.connected })
    }
    @After fun tearDown() {
        shell("am broadcast -n $fixture --ez cancel true")
        repo.setPackage("mono", "com.ftband.mono"); repo.importMode = "NOTIFY"
    }
    @Test fun pushIsImportedWithoutAnActivityAndExactReplayIsIdempotent() {
        val at = System.currentTimeMillis()
        post(1, 12550, at)
        assertTrue("No background import: ${repo.diagnostics.value.lastCode}", waitFor { entries().any { it.amountMinor == 12550L } })
        val imported = entries().first { it.amountMinor == 12550L }
        assertEquals(com.semmyx.ledger.domain.EntryType.EXPENSE, imported.type)
        assertEquals(com.semmyx.ledger.domain.Category.FOOD, imported.category)
        post(1, 12550, at)
        Thread.sleep(1500)
        BankNotificationListener.replayIfConnected()
        Thread.sleep(1500)
        assertEquals(1, entries().count { it.amountMinor == 12550L })
    }
    @Test fun twoMinuteOldPushIsRecoveredWhenListenerReconnects() {
        shell("cmd notification disallow_listener $listener")
        assertTrue("Listener did not disconnect", waitFor { !repo.diagnostics.value.connected })
        val at = System.currentTimeMillis() - 120_000
        post(2, 33445, at)
        Thread.sleep(800)
        assertFalse(entries().any { it.amountMinor == 33445L })
        shell("cmd notification allow_listener $listener")
        assertTrue("Active notification not replayed: ${repo.diagnostics.value.lastCode}", waitFor { entries().any { it.amountMinor == 33445L } })
        assertEquals(at, entries().first { it.amountMinor == 33445L }.eventAtMillis)
    }
    @Test fun inboxStyleContainsTwoSeparatePayments() {
        post(3, 44556, System.currentTimeMillis(), "inbox")
        assertTrue(waitFor { entries().any { it.amountMinor == 44556L } && entries().any { it.amountMinor == 1122L } })
    }
    @Test fun messagesStyleWorksWithoutSmsOrApiToken() {
        post(4, 55667, System.currentTimeMillis(), "messaging")
        assertTrue(waitFor { entries().any { it.amountMinor == 55667L } })
    }
    @Test fun switchingBackToPushReplaysWhileListenerIsAlreadyConnected() {
        repo.importMode = "API"
        post(5, 66778, System.currentTimeMillis())
        Thread.sleep(800)
        assertFalse(entries().any { it.amountMinor == 66778L })
        repo.importMode = "NOTIFY"
        assertTrue(BankNotificationListener.replayIfConnected())
        assertTrue(waitFor { entries().any { it.amountMinor == 66778L } })
    }
}
