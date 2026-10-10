package com.semmyx.ledger

import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test of the real background path: a notification posted by another app is read by the
 * system-bound [com.semmyx.ledger.bank.BankNotificationListener] while no Activity is on screen, parsed and
 * written to the ledger. "com.android.shell" plays the role of the bank app (its package is mapped to monobank).
 */
@RunWith(AndroidJUnit4::class)
class BankBackgroundImportTest {
    private val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
    private val repo get() = app.container.bankImport
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation
    private val listener get() = "${app.packageName}/com.semmyx.ledger.bank.BankNotificationListener"

    private fun shell(command: String): String {
        val fd = automation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun waitFor(timeoutMs: Long, check: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) { if (check()) return true; Thread.sleep(500) }
        return check()
    }

    private fun entries() = runBlocking { repo.exportEntries() }

    @Before fun setUp() {
        repo.enabled = true
        repo.autoImport = true
        repo.setPackage("mono", "com.android.shell")
        shell("cmd notification allow_listener $listener")
        val current = shell("settings get secure enabled_notification_listeners").trim()
        if (!current.contains(listener)) {
            shell("settings put secure enabled_notification_listeners ${if (current.isBlank() || current == "null") listener else "$current:$listener"}")
        }
        assertTrue("Notification listener did not bind", waitFor(30_000) { repo.diagnostics.value.connected })
    }

    @After fun tearDown() { repo.setPackage("mono", "com.ftband.mono") }

    @Test fun bankPushIsImportedInBackgroundAndRepeatIsIgnored() {
        // No spaces: UiAutomation tokenises the command line without quote handling.
        val probe = shell("cmd notification post -t Probe probe_tag probe_text")
        assumeTrue("cmd notification post unsupported on this API: $probe", probe.isNotBlank() && !probe.contains("Unknown", true) && !probe.contains("Exception", true))

        shell("cmd notification post -t Сільпо bank_a −125,50₴|Залишок:1000,00₴")
        assertTrue("Bank push was not imported in the background; last=${repo.diagnostics.value.lastCode}",
            waitFor(30_000) { entries().any { it.amountMinor == 12550L } })
        val imported = entries().first { it.amountMinor == 12550L }
        assertEquals("Сільпо", imported.title)
        assertEquals(com.semmyx.ledger.domain.EntryType.EXPENSE, imported.type)
        assertEquals(com.semmyx.ledger.domain.Category.FOOD, imported.category)

        // The same push delivered again must not create a second entry.
        shell("cmd notification post -t Сільпо bank_b −125,50₴|Залишок:1000,00₴")
        Thread.sleep(5_000)
        assertEquals(1, entries().count { it.amountMinor == 12550L })
    }
}
