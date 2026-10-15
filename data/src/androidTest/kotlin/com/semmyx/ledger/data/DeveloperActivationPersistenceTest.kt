package com.semmyx.ledger.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.data.billing.DeveloperSubscriptionVerifier
import com.semmyx.ledger.data.billing.PlayPremiumRepository
import com.semmyx.ledger.data.security.KeystoreVault
import com.semmyx.ledger.domain.*
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeveloperActivationPersistenceTest {
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private val pair=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKey=Base64.getEncoder().encodeToString(pair.public.encoded)
    private val clock=Clock.fixed(Instant.ofEpochMilli(1767225600000L),ZoneOffset.UTC)
    @Before fun setup() {
        val base=ApplicationProvider.getApplicationContext<Context>()
        context=object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getPackageName()=base.packageName+".activationtest"
            override fun getNoBackupFilesDir()=File(base.cacheDir,"activation-vault-test").apply { mkdirs() }
        }
        cleanVault();scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
    }
    private fun cleanVault() {
        context.noBackupFilesDir.deleteRecursively();context.noBackupFilesDir.mkdirs()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null);deleteEntry(context.packageName+".vault.v1") }
    }
    @After fun clean() { scope.cancel();cleanVault() }
    private fun repository()=PlayPremiumRepository(context,KeystoreVault(context),scope,clock,"",publicKey,"ledger_plus")
    private fun token(id:String,end:String):String {
        val kid=DeveloperSubscriptionVerifier(publicKey).keyId
        val raw="v2|$kid|$id|ledger_plus|${clock.millis()}|$end".toByteArray()
        val sig=Signature.getInstance("SHA256withRSA").run { initSign(pair.private);update(raw);sign() }
        val encoder=Base64.getUrlEncoder().withoutPadding()
        return "TG2.$kid.${encoder.encodeToString(raw)}.${encoder.encodeToString(sig)}"
    }
    @Test fun codeSurvivesRealAndroidKeystoreAndRepositoryRecreation()=runBlocking {
        val first=repository();val id=first.developerInstallationId();val end=clock.millis()+5*86400000L
        assertTrue(first.activateDeveloperToken(token(id,end.toString()).chunked(50).joinToString("\n")))
        val granted=withTimeout(5000) { first.state.first { it.source==PremiumSource.OFFICIAL_DEVELOPER } }
        assertEquals(end,granted.expiresAtMillis)
        val second=repository()
        val restored=withTimeout(5000) { second.state.first { it.source==PremiumSource.OFFICIAL_DEVELOPER } }
        assertEquals(end,restored.expiresAtMillis)
        assertEquals(DeveloperSubscriptionVerifier(publicKey).keyId,restored.developerKeyId)
        val bytes=File(context.noBackupFilesDir,"security.vault").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(bytes.contains("developerActivationCode"));assertFalse(bytes.contains("TG2."))
    }
    @Test fun failedCodeCannotRemoveExistingAccessAndOldShortCodeCannotShortenIt()=runBlocking {
        val repo=repository();val id=repo.developerInstallationId()
        assertTrue(repo.activateDeveloperToken(token(id,"")))
        assertTrue(repo.activateDeveloperToken(token(id,(clock.millis()+86400000L).toString())))
        assertFalse(repo.activateDeveloperToken(token("0000-0000-0000",(clock.millis()+86400000L).toString())))
        val state=withTimeout(5000) { repo.state.first { it.activationProblem==ActivationProblem.DEVICE } }
        assertTrue(state.entitled);assertEquals(PremiumSource.OFFICIAL_DEVELOPER,state.source);assertNull(state.expiresAtMillis)
        assertEquals("",KeystoreVault(context).read { json -> json.optString("developerActivationCode").let { code ->
            DeveloperSubscriptionVerifier(publicKey).verify(code,id,"ledger_plus",clock.millis())?.expiresAtMillis?.toString().orEmpty()
        } })
    }
}
