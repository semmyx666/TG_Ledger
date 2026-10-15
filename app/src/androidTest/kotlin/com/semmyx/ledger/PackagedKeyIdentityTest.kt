package com.semmyx.ledger

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.data.billing.DeveloperSubscriptionVerifier
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PackagedKeyIdentityTest {
    @Test fun packagedPublicKeyMatchesManifestAndRepository() {
        val app=ApplicationProvider.getApplicationContext<LedgerApplication>()
        val verifier=DeveloperSubscriptionVerifier(BuildConfig.DEVELOPER_SUBSCRIPTION_PUBLIC_KEY)
        assertTrue(verifier.configured)
        @Suppress("DEPRECATION")
        val metadata=app.packageManager.getApplicationInfo(app.packageName,PackageManager.GET_META_DATA).metaData
        assertEquals(verifier.keyId,metadata.getString("com.semmyx.ledger.ACTIVATION_KEY_ID"))
        assertEquals(verifier.keyId,app.container.billing.state.value.developerKeyId)
    }
}
