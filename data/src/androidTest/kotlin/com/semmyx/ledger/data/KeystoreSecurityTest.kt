package com.semmyx.ledger.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.data.security.*
import com.semmyx.ledger.domain.PinResult
import java.io.File
import java.security.KeyStore
import java.time.Clock
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSecurityTest {
    private lateinit var isolated: Context
    private lateinit var repository: KeystoreSecurityRepository
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = base.packageName + ".isolatedsecuritytest"
            override fun getNoBackupFilesDir(): File = File(base.cacheDir, "isolated-vault-test").apply { mkdirs() }
        }
        clean()
        repository = KeystoreSecurityRepository(isolated, KeystoreVault(isolated), Clock.systemUTC())
    }
    @After fun clean() {
        if (::isolated.isInitialized) {
            isolated.noBackupFilesDir.deleteRecursively()
            isolated.noBackupFilesDir.mkdirs()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(isolated.packageName + ".vault.v1") }
        }
    }
    @Test fun pinIsSealedAndSurvivesRepositoryRecreation() = runTest {
        assertFalse(repository.hasPin())
        val pin = "938472".toCharArray()
        repository.createPin(pin)
        assertTrue(pin.all { it == 0.toChar() })
        assertTrue(repository.hasPin())
        val bytes = File(isolated.noBackupFilesDir, "security.vault").readBytes()
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("938472"))
        val reopened = KeystoreSecurityRepository(isolated, KeystoreVault(isolated), Clock.systemUTC())
        assertEquals(PinResult.Success, reopened.verifyPin("938472".toCharArray()))
    }
    @Test fun repeatedWrongAttemptsPersistALockout() = runTest {
        repository.createPin("938472".toCharArray())
        repeat(4) { assertEquals(PinResult.Incorrect, repository.verifyPin("000000".toCharArray())) }
        assertTrue(repository.verifyPin("000000".toCharArray()) is PinResult.Locked)
        val reopened = KeystoreSecurityRepository(isolated, KeystoreVault(isolated), Clock.systemUTC())
        assertTrue(reopened.lockoutSeconds() > 0)
        assertTrue(reopened.verifyPin("938472".toCharArray()) is PinResult.Locked)
    }
    @Test fun corruptedCiphertextFailsClosed() = runTest {
        repository.createPin("938472".toCharArray())
        val file = File(isolated.noBackupFilesDir, "security.vault")
        val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
        try { repository.hasPin(); fail("Corruption must not reset the PIN") }
        catch (_: javax.crypto.AEADBadTagException) { assertTrue(file.exists()) }
    }
}
