# TG Ledger 3.2.0 — cat appearance fix — complete file-by-file source

130 implementation, configuration and test files, included without truncation.

The same files are editable at the paths below. Build instructions, architectural notes, visual references and verification limitations are in README.md and docs/.


## .github/workflows/android.yml

```yaml
name: Android validation
on:
  push:
  pull_request:
permissions:
  contents: read
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - name: Install SDK
        run: sdkmanager 'platforms;android-36' 'build-tools;35.0.0'
      - name: Resource and SQL audit
        run: python3 tools/audit_project.py
      - name: Build, unit tests, lint and release shrinking
        run: |
          chmod +x gradlew
          ./gradlew :domain:test :data:testPlayDebugUnitTest :data:testLegacyV7DebugUnitTest :app:testPlayDebugUnitTest :app:assemblePlayDebug :app:assembleLegacyV7Debug :app:assemblePlayRelease :app:lintPlayDebug :app:lintPlayRelease --stacktrace
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: validation-reports
          path: |
            **/build/reports/
            data/schemas/
            verification/source-audit.json
  instrumented:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        api: [26, 36]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - name: Enable emulator acceleration
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Device tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api }}
          arch: x86_64
          target: google_apis
          disable-animations: true
          script: chmod +x gradlew && ./gradlew :data:connectedPlayDebugAndroidTest :app:connectedPlayDebugAndroidTest --stacktrace
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: device-reports-api-${{ matrix.api }}
          path: '**/build/reports/androidTests/'
```

## .gitignore

```text
.gradle/
.kotlin/
.idea/
**/build/
local.properties
secrets.properties
*.jks
*.keystore
*.pem
tools/developer_private_key.pem
tools/developer_public_key.der
.DS_Store
verification/local-*
gradle/wrapper/gradle-wrapper.jar

__pycache__/
*.pyc
```

## app/build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// No credentials are checked in. A Play license public key is NOT a secret;
// it is supplied per published application and validated before release.
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val playKey = providers.gradleProperty("PLAY_LICENSE_KEY").orElse("")
val product = providers.gradleProperty("PREMIUM_PRODUCT_ID").orElse("ledger_plus")
val developerKey = providers.gradleProperty("DEVELOPER_SUBSCRIPTION_PUBLIC_KEY").orElse("")
val legacyApproved = providers.gradleProperty("LEGACY_BILLING_EXTENSION_APPROVED").orElse("false")

android {
    namespace = "com.semmyx.ledger"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.semmyx.ledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "3.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PLAY_LICENSE_KEY", quoted(playKey.get()))
        buildConfigField("String", "PREMIUM_PRODUCT_ID", quoted(product.get()))
        buildConfigField("String", "DEVELOPER_SUBSCRIPTION_PUBLIC_KEY", quoted(developerKey.get()))
        resourceConfigurations += listOf("en", "uk")
    }
    flavorDimensions += "billing"
    productFlavors {
        create("play") { dimension = "billing" }
        create("legacyV7") { dimension = "billing" }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint { abortOnError = true; checkReleaseBuilds = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
// A v7 artifact is useful for compatibility testing, but must never silently
// ship after its Play deadline. An explicit approved-extension flag is required.
tasks.configureEach {
    if (name == "bundleLegacyV7Release" || name == "assembleLegacyV7Release") {
        doFirst {
            check(legacyApproved.get().toBoolean()) {
                "Billing v7 passed its submission deadline. Use playRelease, or confirm a Play-approved extension with -PLEGACY_BILLING_EXTENSION_APPROVED=true."
            }
        }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data")) // Used only by the composition root / platform adapters.
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.1")
    implementation("androidx.navigation:navigation-compose:2.9.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("androidx.room:room-runtime:2.7.2") // RoomDatabase.close at app composition root.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    "playImplementation"("com.android.billingclient:billing-ktx:8.0.0")
    "legacyV7Implementation"("com.android.billingclient:billing-ktx:7.1.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

## app/proguard-rules.pro

```text
# Retrofit / Gson consumer rules come from the data module and their libraries.
# Room and Compose ship their own shrinker rules. Do not globally keep the app.
-keepattributes Signature,InnerClasses,EnclosingMethod
```

## app/src/androidTest/kotlin/com/semmyx/ledger/ComposeScreensTest.kt

```kotlin
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
import com.semmyx.ledger.presentation.auth.AuthState
import com.semmyx.ledger.presentation.auth.AuthenticationScreen
import com.semmyx.ledger.presentation.support.SupportSheet
import com.semmyx.ledger.presentation.theme.LedgerTheme
import com.semmyx.ledger.presentation.theme.LocalizedContent
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
}
```

## app/src/legacyV7/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <application>
        <receiver android:name=".bank.BankSmsReceiver" android:exported="true" android:permission="android.permission.BROADCAST_SMS">
            <intent-filter><action android:name="android.provider.Telephony.SMS_RECEIVED" /></intent-filter>
        </receiver>
    </application>
</manifest>
```

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="com.android.vending.BILLING" />
    <!-- Only requested at runtime when a TG Plus subscriber taps the voice assistant button. -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <queries>
        <package android:name="org.telegram.messenger" />
        <package android:name="org.telegram.messenger.web" />
        <package android:name="org.thunderdog.challegram" />
        <!-- Lets us reliably detect whether any speech-recognition app is installed before
             launching the voice assistant, instead of the intent silently failing to resolve
             on Android 11+ package-visibility rules. -->
        <intent>
            <action android:name="android.speech.action.RECOGNIZE_SPEECH" />
        </intent>
        <intent>
            <action android:name="android.speech.RecognitionService" />
        </intent>
    </queries>
    <application
        android:name=".LedgerApplication"
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        tools:targetApi="31"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:usesCleartextTraffic="false"
        android:theme="@style/Theme.Ledger">
        <activity android:name=".platform.SupportWebActivity" android:exported="false" />
        <activity android:name=".MainActivity" android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.Ledger.Starting">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service android:name=".bank.BankNotificationListener" android:label="@string/bank_import_title"
            android:exported="true" android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
            <intent-filter><action android:name="android.service.notification.NotificationListenerService" /></intent-filter>
        </service>
    </application>
</manifest>
```

## app/src/main/kotlin/com/semmyx/ledger/AppContainer.kt

```kotlin
package com.semmyx.ledger

import android.content.Context
import androidx.biometric.BiometricManager
import com.semmyx.ledger.data.billing.PlayPremiumRepository
import com.semmyx.ledger.data.local.LedgerDatabase
import com.semmyx.ledger.data.remote.RateApi
import com.semmyx.ledger.data.repository.DataStorePreferences
import com.semmyx.ledger.data.repository.OfflineRateRepository
import com.semmyx.ledger.data.repository.RoomEntryRepository
import com.semmyx.ledger.data.security.BiometricKeys
import com.semmyx.ledger.data.security.KeystoreSecurityRepository
import com.semmyx.ledger.data.security.KeystoreVault
import com.semmyx.ledger.domain.*
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The single composition root. ViewModels know domain interfaces, not Room / Retrofit / Play. */
class AppContainer(context: Context, scope: CoroutineScope) {
    private val app = context.applicationContext
    val clock: Clock = Clock.systemDefaultZone()
    private val database = LedgerDatabase.create(app)
    private val vault = KeystoreVault(app)
    val bankImport = com.semmyx.ledger.data.repository.BankImportRepository(database, app)
    val entries: EntryRepository = RoomEntryRepository(database.ledgerDao(), clock)
    val preferences: PreferencesRepository = DataStorePreferences(app, scope)
    val security: SecurityRepository = KeystoreSecurityRepository(app, vault, clock)
    val rates: RateRepository = OfflineRateRepository(database.rateDao(), RateApi.create(), clock)
    // Two independent entitlement paths for the same product: Google Play,
    // and a developer-issued activation code for direct / pre-launch sales.
    val billing = PlayPremiumRepository(
        app, vault, scope, clock,
        BuildConfig.PLAY_LICENSE_KEY,
        BuildConfig.DEVELOPER_SUBSCRIPTION_PUBLIC_KEY,
        BuildConfig.PREMIUM_PRODUCT_ID,
    )
    val premium: PremiumRepository = billing
    val biometricKeys = BiometricKeys(app)
    val observeDashboard = ObserveDashboard(entries)
    val saveEntry = SaveEntry(entries, clock)

    fun biometricAvailable(): Boolean = BiometricManager.from(app)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    suspend fun configureBiometrics(enabled: Boolean) = withContext(Dispatchers.IO) {
        if (enabled) {
            check(biometricAvailable()) { "No enrolled strong biometric" }
            biometricKeys.provision()
        } else biometricKeys.remove()
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/LedgerApplication.kt

```kotlin
package com.semmyx.ledger

import android.app.Application
import android.os.Process
import android.util.Log
import com.semmyx.ledger.diagnostics.CrashLogger
import com.semmyx.ledger.platform.RateRefreshWorker
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class LedgerApplication : Application() {
    private val errorHandler = CoroutineExceptionHandler { _, error ->
        // Never log entry titles, PINs, purchase tokens, URLs with secrets, or exception messages.
        Log.e("Ledger", "Background task failed: ${error.javaClass.simpleName}")
    }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + errorHandler)
    val container: AppContainer by lazy { AppContainer(this, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        // Installed first so it wraps (and runs before) the fatal handler below, saving the
        // stack trace to app-internal storage. Settings -> Support can then show it, since we
        // otherwise have no way to see what actually crashed without adb/logcat access.
        CrashLogger.install(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Uncaught failures are NOT swallowed: continuing a corrupt process is unsafe.
            try { Log.e("Ledger", "Fatal failure: ${error.javaClass.simpleName}") }
            finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else { Process.killProcess(Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
        RateRefreshWorker.schedule(this)
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/MainActivity.kt

```kotlin
package com.semmyx.ledger

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModelProvider
import com.semmyx.ledger.platform.HighRefreshRate
import com.semmyx.ledger.presentation.AppRoot
import com.semmyx.ledger.presentation.AppViewModel
import com.semmyx.ledger.presentation.common.LedgerViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private lateinit var appModel: AppViewModel
    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { appModel.lock() }
    }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) appModel.lock()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val container = (application as LedgerApplication).container
        appModel = ViewModelProvider(this, LedgerViewModelFactory(AppViewModel::class.java) {
            AppViewModel(container.preferences, container.security)
        })[AppViewModel::class.java]
        splash.setKeepOnScreenCondition { !appModel.state.value.ready }
        splash.setOnExitAnimationListener { view ->
            if (ValueAnimator.areAnimatorsEnabled()) {
                view.view.animate().alpha(0f).setDuration(180).withEndAction { view.remove() }.start()
            } else view.remove()
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        ContextCompat.registerReceiver(this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { AppRoot(this, appModel, container) }
    }
    override fun onResume() {
        super.onResume()
        val app = application as LedgerApplication
        app.applicationScope.launch { app.container.billing.refreshIfNeeded() }
    }
    override fun onStop() {
        if (!isChangingConfigurations) appModel.lock()
        super.onStop()
    }
    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationListener.kt

```kotlin
package com.semmyx.ledger.bank

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.semmyx.ledger.LedgerApplication
import com.semmyx.ledger.domain.bank.Banks
import kotlinx.coroutines.*

class BankNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onListenerConnected() { (application as LedgerApplication).container.bankImport.connected(true) }
    override fun onListenerDisconnected() {
        (application as LedgerApplication).container.bankImport.connected(false)
        NotificationListenerService.requestRebind(android.content.ComponentName(this, BankNotificationListener::class.java))
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val repository = (application as LedgerApplication).container.bankImport
        val bank = repository.bankForPackage(sbn.packageName)
        repository.note(if (bank == null) "UNSUPPORTED_APP" else "BANK_RECEIVED", sbn.packageName, notification = true, bankMessage = bank != null)
        bank ?: return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) { repository.note("GROUP_SUMMARY"); return }
        val extras = notification.extras
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = "$title\n$body".take(8001)
        if (body.isBlank()) { repository.note("EMPTY_TEXT"); return }
        if (!repository.enabled) { repository.note("DISABLED"); return }
        scope.launch {
            try {
                repository.receive(bank, text, "PUSH:${sbn.packageName}:${sbn.key}:${sbn.postTime}:$text", sbn.postTime, "PUSH")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { repository.note("ERROR"); android.util.Log.w("BankImport", "Local import failed; no message contents logged") }
        }
    }
    override fun onDestroy() { (application as LedgerApplication).container.bankImport.connected(false); scope.cancel(); super.onDestroy() }
}
```

## app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsReceiver.kt

```kotlin
package com.semmyx.ledger.bank

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.semmyx.ledger.LedgerApplication
import com.semmyx.ledger.domain.bank.Banks
import kotlinx.coroutines.*

/** Included in the legacyV7 sideload manifest only. Reads new messages, never the historical SMS inbox. */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().originatingAddress.orEmpty()
        val bank = Banks.bySender(sender) ?: return
        val repository = (context.applicationContext as LedgerApplication).container.bankImport
        if (!repository.enabled) return
        val text = messages.joinToString("") { it.messageBody.orEmpty() }.take(8001)
        val at = messages.minOf { it.timestampMillis }
        val pending = goAsync()
        (context.applicationContext as LedgerApplication).applicationScope.launch(Dispatchers.IO) {
            try { withTimeout(7000) { repository.receive(bank, text, "SMS:$sender:$at:$text", at, "SMS") } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { android.util.Log.w("BankImport", "Local SMS import failed; no message contents logged") }
            finally { pending.finish() }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/diagnostics/CrashLogger.kt

```kotlin
package com.semmyx.ledger.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the most recent uncaught crash to app-internal storage so it survives the process
 * dying, and can be viewed later from Settings -> Support without needing adb/logcat access.
 *
 * Only the exception class name, message, and stack trace (file/line/method names) are stored --
 * never entry titles, PINs, purchase tokens, or other user data, matching the existing logging
 * rule in LedgerApplication.
 */
object CrashLogger {
    private const val FILE_NAME = "last_crash.txt"

    /** Wraps the app's existing uncaught-exception handler so every fatal crash is saved first. */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                writeCrash(appContext, error)
            } catch (_: Throwable) {
                // The logger itself must never cause a second crash while handling the first.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrash(context: Context, error: Throwable) {
        val writer = StringWriter()
        error.printStackTrace(PrintWriter(writer))
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = "TG Ledger crash - $timestamp\n\n$writer"
        File(context.filesDir, FILE_NAME).writeText(text)
    }

    /** Returns the saved crash report, if any, or null when the app hasn't crashed since it was last cleared. */
    fun readLastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/platform/BiometricUnlocker.kt

```kotlin
package com.semmyx.ledger.platform

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.semmyx.ledger.data.security.BiometricKeys
import java.security.SecureRandom

/** A callback without the crypto object is never sufficient to unlock. */
class BiometricUnlocker(
    private val activity: FragmentActivity,
    private val keys: BiometricKeys,
) {
    private var prompt: BiometricPrompt? = null
    fun authenticate(title: String, subtitle: String, negative: String, success: () -> Unit, failure: () -> Unit) {
        try {
            val cipher = keys.authenticationCipher()
            val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val biometricPrompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            val authenticated = requireNotNull(result.cryptoObject?.cipher)
                            require(authenticated === cipher)
                            authenticated.doFinal(challenge) // Keystore enforces a fresh strong-biometric auth.
                            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) success()
                        } catch (_: Exception) { failure() }
                        finally { challenge.fill(0) }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        challenge.fill(0)
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_CANCELED) failure()
                    }
                    override fun onAuthenticationFailed() { failure() }
                })
            prompt = biometricPrompt
            val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)
                .setNegativeButtonText(negative).setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(true).build()
            biometricPrompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            // Invalidated enrollment keys are not recreated at the lock screen; use the PIN.
            failure()
        }
    }
    fun cancel() { prompt?.cancelAuthentication(); prompt = null }
}
```

## app/src/main/kotlin/com/semmyx/ledger/platform/HighRefreshRate.kt

```kotlin
package com.semmyx.ledger.platform

import android.app.Activity
import android.view.Display
import kotlin.math.abs

object HighRefreshRate {
    fun supported(display: Display?): Set<Int> {
        if (display == null) return setOf(60)
        val current = display.mode
        val modes = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        return listOf(60, 90, 120).filter { rate -> modes.any { abs(it.refreshRate - rate) <= 1.1f } }.toSet()
    }
    fun request(activity: Activity, requestedHz: Int = 0) {
        val display = activity.window.decorView.display ?: return
        val current = display.mode
        val modes = display.supportedModes.filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        val exact = modes.filter { requestedHz > 0 && abs(it.refreshRate - requestedHz) <= 1.1f }.firstOrNull()
        val selected = if (requestedHz == 0) null else (exact
            ?: modes.filter { it.refreshRate <= requestedHz + 1.1f }.maxByOrNull { it.refreshRate }
            ?: modes.minByOrNull { it.refreshRate })
        activity.window.attributes = activity.window.attributes.apply {
            // Auto releases the override back to Android, rather than forcing the maximum forever.
            preferredDisplayModeId = selected?.modeId ?: 0
            preferredRefreshRate = selected?.refreshRate ?: 0f
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/platform/RateRefreshWorker.kt

```kotlin
package com.semmyx.ledger.platform

import android.content.Context
import androidx.work.*
import com.semmyx.ledger.LedgerApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Only public rates are refreshed in the background; personal entries never leave Room. */
class RateRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LedgerApplication).container
        return try {
            container.entries.purgeDeletedBefore(container.clock.millis() - TimeUnit.DAYS.toMillis(1))
            container.rates.refresh()
            Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (runAttemptCount >= 4) Result.failure() else Result.retry()
        }
    }
    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
            val periodic = PeriodicWorkRequestBuilder<RateRefreshWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("reference-rates", ExistingPeriodicWorkPolicy.UPDATE, periodic)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/platform/SupportNavigator.kt

```kotlin
package com.semmyx.ledger.platform

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object SupportNavigator {
    const val HANDLE = "@semmyxth"
    const val WEB_URL = "https://t.me/semmyxth"
    private val officialPackages = listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")

    fun open(activity: Activity) {
        if (!openTelegram(activity)) activity.startActivity(Intent(activity, SupportWebActivity::class.java))
    }
    fun openTelegram(activity: Activity): Boolean {
        for (packageName in officialPackages) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=semmyxth")).setPackage(packageName)
            @Suppress("DEPRECATION")
            val resolved = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            if (resolved == null || !resolved.exported || resolved.packageName != packageName) continue
            intent.component = ComponentName(resolved.packageName, resolved.name) // Explicit, not a hijackable chooser.
            try {
                activity.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // App could have been removed between resolution and launch; try the next official client.
                continue
            } catch (_: SecurityException) { continue }
        }
        return false
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/platform/SupportWebActivity.kt

```kotlin
package com.semmyx.ledger.platform

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.LedgerApplication
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.UserPreferences
import com.semmyx.ledger.presentation.theme.LedgerTheme
import com.semmyx.ledger.presentation.theme.LocalizedContent

/** Non-exported HTTPS-only fallback. No JS bridge, file access, SSL bypass, or arbitrary URL input. */
class SupportWebActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by (application as LedgerApplication).container.preferences.preferences
                .collectAsStateWithLifecycle(initialValue = UserPreferences())
            LocalizedContent(prefs.language) {
                LedgerTheme(prefs.theme) {
                    var error by remember { mutableStateOf(false) }
                    var loading by remember { mutableStateOf(true) }
                    var webView by remember { mutableStateOf<WebView?>(null) }
                    BackHandler { if (webView?.canGoBack() == true) webView?.goBack() else finish() }
                    DisposableEffect(Unit) {
                        onDispose {
                            webView?.let { view ->
                                view.stopLoading()
                                (view.parent as? ViewGroup)?.removeView(view)
                                view.destroy()
                            }
                            webView = null
                        }
                    }
                    Scaffold(topBar = {
                        TopAppBar(title = { Text(stringResource(R.string.telegram_support)) }, navigationIcon = {
                            IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                        })
                    }) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            if (loading && !error) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (error) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(stringResource(R.string.support_network_error))
                                    Button(onClick = { error = false; loading = true; webView?.loadUrl(SupportNavigator.WEB_URL) }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                            AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.domStorageEnabled = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                    settings.safeBrowsingEnabled = true
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                            val uri = request.url
                                            if (uri.scheme == "tg" && request.hasGesture()) {
                                                SupportNavigator.openTelegram(this@SupportWebActivity)
                                                return true
                                            }
                                            return uri.scheme != "https" || uri.host != "t.me"
                                        }
                                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                                        override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                                            if (request.isForMainFrame) { error = true; loading = false }
                                        }
                                    }
                                    webView = this
                                    loadUrl(SupportNavigator.WEB_URL)
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/AppRoot.kt

```kotlin
package com.semmyx.ledger.presentation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.semmyx.ledger.presentation.history.*
import java.time.YearMonth
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.semmyx.ledger.platform.HighRefreshRate
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.semmyx.ledger.AppContainer
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.ThemeMode
import com.semmyx.ledger.platform.BiometricUnlocker
import com.semmyx.ledger.platform.SupportNavigator
import com.semmyx.ledger.presentation.auth.*
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.dashboard.*
import com.semmyx.ledger.presentation.analytics.*
import com.semmyx.ledger.presentation.bank.*
import com.semmyx.ledger.presentation.editor.*
import com.semmyx.ledger.presentation.onboarding.OnboardingScreen
import com.semmyx.ledger.presentation.settings.*
import com.semmyx.ledger.presentation.support.*
import com.semmyx.ledger.presentation.theme.*

@Composable
fun AppRoot(host: FragmentActivity, model: AppViewModel, container: AppContainer) {
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides host, LocalOnBackPressedDispatcherOwner provides host) {
        AppRootContent(host, model, container)
    }
}

@Composable
private fun AppRootContent(host: FragmentActivity, model: AppViewModel, container: AppContainer) {
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(state.preferences.refreshRateHz) {
        HighRefreshRate.request(host, state.preferences.refreshRateHz)
        onPauseOrDispose { }
    }
    var supportVisible by remember { mutableStateOf(false) }
    // Registered unconditionally here, in AppRoot's very first composition (before the PIN
    // screen even shows), never inside the Dashboard graph. An Activity Result launcher must be
    // registered before the host Activity passes STARTED; the Dashboard route is only entered
    // after the user unlocks with their PIN -- long after the Activity is already STARTED -- so
    // registering it there always throws "LifecycleOwners must call register before they are
    // STARTED", whether that registration sits inside a lazy list item or at the top of the
    // screen. Only the *handling* of a result is Dashboard-specific, so that part is supplied
    // later via a mutable callback the Dashboard route swaps in once it has real data.
    var onSpeechResult by remember { mutableStateOf<(Intent?) -> Unit>({}) }
    var onMicPermissionResult by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val speechRecognizerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> onSpeechResult(result.data) }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onMicPermissionResult(granted) }
    LocalizedContent(state.preferences.language) {
        LedgerTheme(state.preferences.theme, state.preferences.colorOverrides) {
            val dark = when (state.preferences.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false
            }
            SideEffect {
                WindowCompat.getInsetsController(host.window, host.window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                // Never cross-fade private content into a lock screen.
                when {
                    !state.ready -> LoadingState()
                    state.failed -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.security_error), stringResource(R.string.retry), model::retry)
                        TextButton(onClick = { supportVisible = true }) { Text(stringResource(R.string.need_help)) }
                    }
                    !state.preferences.onboardingComplete -> OnboardingScreen(model::completeOnboarding)
                    !state.unlocked -> AuthenticationRoute(host, container, state, onUnlock = {
                        if (host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.unlock()
                    }, onSupport = { supportVisible = true })
                    else -> UnlockedGraph(host, container, state, model::lock, { container.billing.launchPurchase(host) }, { supportVisible = true },
                        speechRecognizerLauncher, micPermissionLauncher, { onSpeechResult = it }, { onMicPermissionResult = it })
                }
                if (supportVisible) SupportSheet(onDismiss = { supportVisible = false }, onContact = {
                    supportVisible = false
                    SupportNavigator.open(host)
                })
            }
        }
    }
}

@Composable
private fun AuthenticationRoute(host: FragmentActivity, container: AppContainer, appState: AppState, onUnlock: () -> Unit, onSupport: () -> Unit) {
    // The auth-attempt owner is disposed on background lock; cancellation wipes PIN buffers.
    val store = remember(appState.lockEpoch) { ViewModelStore() }
    val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }
    DisposableEffect(store) { onDispose { store.clear() } }
    val model: AuthViewModel = viewModel(viewModelStoreOwner = owner, factory = LedgerViewModelFactory(AuthViewModel::class.java) {
        AuthViewModel(!appState.hasPin, container.security)
    })
    val auth by model.state.collectAsStateWithLifecycle()
    val unlocker = remember(store, host) { BiometricUnlocker(host, container.biometricKeys) }
    DisposableEffect(unlocker) { onDispose { unlocker.cancel() } }
    LaunchedEffect(model) { model.unlocked.collect { onUnlock() } }
    val title = stringResource(R.string.biometric_title)
    val subtitle = stringResource(R.string.biometric_subtitle)
    val negative = stringResource(R.string.use_pin)
    AuthenticationScreen(auth, appState.preferences.biometricsEnabled && container.biometricAvailable(), model::digit, model::erase,
        model::startOver, onBiometric = { unlocker.authenticate(title, subtitle, negative, onUnlock, model::biometricFailed) }, onSupport = onSupport)
}

@Composable
private fun UnlockedGraph(host: FragmentActivity, container: AppContainer, appState: AppState, onLock: () -> Unit, onPurchase: () -> Unit, onSupport: () -> Unit,
    speechRecognizerLauncher: ActivityResultLauncher<Intent>, micPermissionLauncher: ActivityResultLauncher<String>,
    setOnSpeechResult: ((Intent?) -> Unit) -> Unit, setOnMicPermissionResult: ((Boolean) -> Unit) -> Unit) {
    val nav = rememberNavController()
    val routes = listOf("dashboard", "settings", "help", "analytics", "banks")
    val onNavigate: (Int) -> Unit = { index ->
        nav.navigate(routes[index]) {
            popUpTo("dashboard") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    NavHost(navController = nav, startDestination = "dashboard") {
        composable("dashboard") {
            val model: DashboardViewModel = viewModel(factory = LedgerViewModelFactory(DashboardViewModel::class.java) {
                DashboardViewModel(container.entries, container.preferences, container.premium, container.observeDashboard, container.clock, container.saveEntry)
            })
            DashboardRoute(model, host, onNavigate, onAdd = { nav.navigate("entry/new") }, onEdit = { nav.navigate("entry/$it") },
                speechRecognizerLauncher = speechRecognizerLauncher, micPermissionLauncher = micPermissionLauncher,
                setOnSpeechResult = setOnSpeechResult, setOnMicPermissionResult = setOnMicPermissionResult)
        }
        composable("settings") {
            val model: SettingsViewModel = viewModel(factory = LedgerViewModelFactory(SettingsViewModel::class.java) {
                SettingsViewModel(container.preferences, container.premium, container.rates, container::configureBiometrics)
            })
            SettingsRoute(model, container.biometricAvailable(), onNavigate, onPurchase, onSupport, onLock)
        }
        composable("help") { HelpScreen(onNavigate, onSupport) }
        composable("analytics") {
            val model: AnalyticsViewModel = viewModel(factory = LedgerViewModelFactory(AnalyticsViewModel::class.java) {
                AnalyticsViewModel(container.entries, container.preferences, container.clock)
            })
            AnalyticsRoute(model, onBack = { onNavigate(0) }, onCurrency = { onNavigate(1) },
                onEdit = { nav.navigate("entry/$it") },
                onHistory = { month -> nav.navigate(if (month == null) "history" else "history?month=$month") })
        }
        composable("history?month={month}", arguments = listOf(navArgument("month") { type = NavType.StringType; nullable = true; defaultValue = null })) { backStack ->
            val month = backStack.arguments?.getString("month")?.let { value -> runCatching { YearMonth.parse(value) }.getOrNull() }
            val historyModel: HistoryViewModel = viewModel(factory = LedgerViewModelFactory(HistoryViewModel::class.java) {
                HistoryViewModel(container.entries, container.preferences, container.clock, month)
            })
            HistoryScreen(historyModel, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("entry/$it") })
        }
        composable("banks") {
            val bankModel: BankViewModel = viewModel(factory = LedgerViewModelFactory(BankViewModel::class.java) {
                BankViewModel(container.bankImport, container.preferences, container.clock)
            })
            BankScreen(bankModel, container.bankImport, onBack = { onNavigate(0) }, onAdd = { nav.navigate("entry/new") },
                onEdit = { nav.navigate("entry/$it") })
        }
        composable("entry/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id")?.takeUnless { it == "new" }
            val model: EntryViewModel = viewModel(factory = LedgerViewModelFactory(EntryViewModel::class.java) {
                EntryViewModel(id, container.entries, container.preferences, container.saveEntry, container.clock)
            })
            val categories by container.bankImport.categories().collectAsStateWithLifecycle(initialValue = emptyList())
            EntryRoute(model, appState.preferences.language, onBack = { nav.popBackStack() }, customCategories = categories)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/AppViewModel.kt

```kotlin
package com.semmyx.ledger.presentation

import androidx.lifecycle.ViewModel
import com.semmyx.ledger.domain.PreferencesRepository
import com.semmyx.ledger.domain.SecurityRepository
import com.semmyx.ledger.domain.UserPreferences
import com.semmyx.ledger.presentation.common.guarded
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppState(
    val ready: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val hasPin: Boolean = false,
    val unlocked: Boolean = false,
    val failed: Boolean = false,
    val lockEpoch: Long = 0,
)

class AppViewModel(private val preferences: PreferencesRepository, private val security: SecurityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private var bootstrap: Job? = null
    init { retry() }

    fun retry() {
        bootstrap?.cancel()
        mutableState.value = AppState()
        bootstrap = guarded(onError = { mutableState.update { it.copy(ready = true, failed = true, unlocked = false) } }) {
            val configured = security.hasPin()
            mutableState.update { it.copy(hasPin = configured) }
            preferences.preferences.collect { prefs ->
                mutableState.update { it.copy(ready = true, preferences = prefs, failed = false) }
            }
        }
    }
    fun completeOnboarding() {
        guarded(onError = { mutableState.update { it.copy(failed = true) } }) { preferences.completeOnboarding() }
    }
    /** Called only by the PIN verifier event or a successful crypto-bound biometric callback. */
    fun unlock() { mutableState.update { it.copy(unlocked = true, hasPin = true) } }
    fun lock() { mutableState.update { it.copy(unlocked = false, lockEpoch = it.lockEpoch + 1) } }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/analytics/AnalyticsScreen.kt

```kotlin
package com.semmyx.ledger.presentation.analytics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun AnalyticsRoute(model: AnalyticsViewModel, onBack: () -> Unit, onCurrency: () -> Unit, onEdit: (String) -> Unit, onHistory: (YearMonth?) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    AnalyticsScreen(state, onBack, onCurrency, model::month, model::focus, model::more, model::retry, onEdit, onHistory = { onHistory(state.month) })
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalyticsScreen(state: AnalyticsState, onBack: () -> Unit, onCurrency: () -> Unit,
    onMonth: (YearMonth?) -> Unit, onFocus: (AnalyticsFocus?) -> Unit, onMore: () -> Unit,
    onRetry: () -> Unit, onEdit: (String) -> Unit, onHistory: () -> Unit) {
    var monthPicker by remember { mutableStateOf(false) }
    BackHandler(state.focus != null) { onFocus(null) }
    val locale = Locale.forLanguageTag(state.preferences.language.tag)
    val monthLabel = state.month?.format(DateTimeFormatter.ofPattern("LLLL, yyyy", locale)) ?: stringResource(R.string.all_time)
    if (monthPicker) {
        var draft by remember(state.month) { mutableStateOf(state.month ?: YearMonth.now()) }
        AlertDialog(onDismissRequest = { monthPicker = false }, title = { Text(stringResource(R.string.cinematic_period)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(draft.format(DateTimeFormatter.ofPattern("LLLL, yyyy", locale)))
                    Row {
                        TextButton(onClick = { if (draft > YearMonth.of(2000, 1)) draft = draft.minusMonths(1) }) { Text("‹") }
                        TextButton(onClick = { if (draft < YearMonth.now()) draft = draft.plusMonths(1) }) { Text("›") }
                    }
                    TextButton(onClick = { onMonth(null); monthPicker = false }) { Text(stringResource(R.string.all_time)) }
                }
            }, confirmButton = { TextButton(onClick = { onMonth(draft); monthPicker = false }) { Text(stringResource(R.string.cinematic_apply)) } },
            dismissButton = { TextButton(onClick = { monthPicker = false }) { Text(stringResource(R.string.back)) } })
    }
    Surface(color = CinematicTokens.Background, contentColor = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxSize()) {
        SharedTransitionLayout(Modifier.fillMaxSize()) {
            AnimatedContent(targetState = state.focus, label = "categoryHero", transitionSpec = {
                fadeIn(tween(380)) togetherWith fadeOut(tween(240))
            }) { focus ->
                val scope = this
                if (focus == null) {
                    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(18.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.back)) }
                                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f)) {
                                    Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Icon(Icons.Default.History, null); Text(stringResource(R.string.cinematic_history)) }
                                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .15f), modifier = Modifier.weight(1f)) {
                                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                                Icon(Icons.Default.BarChart, null, tint = CinematicTokens.Accent)
                                                Text(stringResource(R.string.analytics_title), color = CinematicTokens.Accent)
                                            }
                                        }
                                    }
                                }
                            }
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(onClick = { monthPicker = true }) { Text(monthLabel); Icon(Icons.Default.CalendarMonth, null) }
                                OutlinedButton(onClick = onCurrency) { Text(state.preferences.currency.name); Icon(Icons.Default.Payments, null) }
                            }
                        }
                        if (state.loading) item { LoadingState(Modifier.height(200.dp)) }
                        else if (state.failed) item { EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.storage_error), stringResource(R.string.retry), onRetry) }
                        else {
                            item {
                                CascadeCard(0) {
                                    Text(stringResource(R.string.cinematic_expenses_period, monthLabel), color = CinematicTokens.Muted)
                                    Odometer(-state.expenseMinor, state.preferences.currency, state.preferences.language, color = CinematicTokens.Expense)
                                    state.previousExpenseMinor?.let { previous ->
                                        val delta = state.expenseMinor - previous
                                        Text(stringResource(R.string.cinematic_comparison, formatReferenceMoney(abs(delta), state.preferences.currency, state.preferences.language)),
                                            color = if (delta > 0) CinematicTokens.Expense else CinematicTokens.Income)
                                        Text(stringResource(if (delta > 0) R.string.cinematic_more_than_previous else R.string.cinematic_less_than_previous), color = CinematicTokens.Muted)
                                    }
                                    PremiumBars(state.buckets, state.preferences.currency, state.preferences.language)
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(if (state.month == null) R.string.cinematic_month_average else R.string.cinematic_week_average),
                                            modifier = Modifier.fillMaxWidth(), color = CinematicTokens.Muted, style = MaterialTheme.typography.bodyMedium)
                                        Text(formatReferenceMoney(if (state.buckets.isEmpty()) 0 else state.expenseMinor / state.buckets.size,
                                            state.preferences.currency, state.preferences.language),
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            maxLines = 1, softWrap = false, color = CinematicTokens.Expense,
                                            style = MaterialTheme.typography.headlineSmall)
                                    }
                                }
                            }
                            listOf(EntryType.EXPENSE, EntryType.INCOME).forEachIndexed { index, type ->
                                item(key = type) {
                                    val hero = Modifier.sharedBounds(rememberSharedContentState("focus-$type-all"), scope)
                                    CascadeCard(index + 1, hero) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(stringResource(if (type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), color = LocalAppearance.current.type(type))
                                            TextButton(onClick = { onFocus(AnalyticsFocus(type)) }) { Text(stringResource(R.string.cinematic_more), color = CinematicTokens.Accent) }
                                        }
                                        val slices = AnalyticsMath.slices(state.totals, type)
                                        if (slices.isEmpty()) Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            slices.take(4).forEach { slice ->
                                                val tint = CinematicTokens.category(slice.category, type == EntryType.INCOME)
                                                Column(Modifier.weight(1f).sharedBounds(rememberSharedContentState("focus-$type-${slice.category}"), scope)
                                                    .clickable { onFocus(AnalyticsFocus(type, slice.category)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Box(Modifier.size(48.dp).background(tint.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
                                                        Icon(slice.category.icon(), stringResource(slice.category.label()), tint = tint)
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor,
                                                        state.preferences.currency, state.preferences.language), style = MaterialTheme.typography.labelSmall,
                                                        color = if (type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                                                    Text(percent(slice.basisPoints, state.preferences.language), color = CinematicTokens.Muted,
                                                        style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val key = "focus-${focus.type}-${focus.category ?: "all"}"
                    Surface(Modifier.fillMaxSize().sharedBounds(rememberSharedContentState(key), scope), color = CinematicTokens.Background) {
                        val slices = AnalyticsMath.slices(state.totals, focus.type)
                        val selected = slices.find { it.category == focus.category }
                        val total = slices.sumOf { it.minor }
                        LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(), contentPadding = PaddingValues(18.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onFocus(null) }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
                                    Text(stringResource(if (focus.type == EntryType.EXPENSE) R.string.expenses else R.string.income), style = MaterialTheme.typography.headlineSmall)
                                }
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stringResource(R.string.reference_total_for, monthLabel), color = CinematicTokens.Muted)
                                    Odometer(if (focus.type == EntryType.INCOME) total else -total,
                                        state.preferences.currency, state.preferences.language,
                                        if (focus.type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense, positiveSign = focus.type == EntryType.INCOME)
                                }
                                PremiumDonut(slices, focus.type, focus.category, state.preferences.currency, state.preferences.language) { category ->
                                    onFocus(AnalyticsFocus(focus.type, category))
                                }
                            }
                            item {
                                CascadeCard(0) {
                                    Text(stringResource(if (focus.type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), color = LocalAppearance.current.type(focus.type))
                                    slices.forEachIndexed { index, slice ->
                                        CategoryRow(slice, focus.type, state) { onFocus(AnalyticsFocus(focus.type, slice.category)) }
                                        if (index != slices.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                            item {
                                Text(focus.category?.let { stringResource(it.label()) } ?: stringResource(R.string.all_categories), style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.cinematic_transactions), color = CinematicTokens.Muted)
                            }
                            if (state.transactions.isEmpty()) item { Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted) }
                            items(state.transactions, key = { it.id }) { entry ->
                                Surface(shape = RoundedCornerShape(16.dp), color = CinematicTokens.Surface,
                                    modifier = Modifier.fillMaxWidth().clickable { onEdit(entry.id) }) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(entry.title); Text(formatDate(entry.date, state.preferences.language), color = CinematicTokens.Muted)
                                        Text(formatReferenceMoney(if (entry.type == EntryType.INCOME) entry.amountMinor else -entry.amountMinor,
                                            entry.currency, state.preferences.language), color = if (entry.type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                                        if (entry.note.isNotBlank()) Text(entry.note, color = CinematicTokens.Muted)
                                    }
                                }
                            }
                            if (state.canLoadMore) item { TextButton(onClick = onMore) { Text(stringResource(R.string.load_more)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(slice: CategorySlice, type: EntryType, state: AnalyticsState,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = CinematicTokens.category(slice.category, type == EntryType.INCOME)
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(46.dp).background(color.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(slice.category.icon(), null, tint = color)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(slice.category.label()))
            Text(stringResource(R.string.category_transaction_count, slice.count), color = CinematicTokens.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor, state.preferences.currency, state.preferences.language), color = LocalAppearance.current.type(type))
            Text(percent(slice.basisPoints, state.preferences.language), color = CinematicTokens.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/analytics/AnalyticsViewModel.kt

```kotlin
package com.semmyx.ledger.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AnalyticsFocus(val type: EntryType, val category: Category? = null)
data class AnalyticsState(
    val preferences: UserPreferences = UserPreferences(),
    val month: YearMonth? = null,
    val totals: List<CategoryTotal> = emptyList(),
    val buckets: List<CashBucket> = emptyList(),
    val previousExpenseMinor: Long? = null,
    val focus: AnalyticsFocus? = null,
    val transactions: List<LedgerEntry> = emptyList(),
    val canLoadMore: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
) {
    val expenseMinor: Long get() = totals.sumOf { it.expenseMinor }
    val incomeMinor: Long get() = totals.sumOf { it.incomeMinor }
}
private data class AnalyticsSpec(val month: YearMonth?, val focus: AnalyticsFocus? = null, val limit: Int = 50, val retry: Int = 0)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(private val repository: EntryRepository, preferences: PreferencesRepository,
    private val clock: Clock) : ViewModel() {
    private val spec = MutableStateFlow(AnalyticsSpec(YearMonth.now(clock)))
    private val today = flow {
        while (true) {
            val day = LocalDate.now(clock); emit(day)
            delay((day.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli() - clock.millis()).coerceAtLeast(1000))
        }
    }
    val state = combine(preferences.preferences, spec, today) { prefs, request, day -> Triple(prefs, request, day) }
        .flatMapLatest { (prefs, request, day) ->
            val from = request.month?.atDay(1) ?: FinanceWindow.EARLIEST
            val through = minOf(request.month?.atEndOfMonth() ?: day, day)
            val previous = request.month?.minusMonths(1)
            // For the current month compare equal elapsed days, not partial month vs full past month.
            val previousThrough = previous?.let { if (request.month == YearMonth.from(day))
                it.atDay(day.dayOfMonth.coerceAtMost(it.lengthOfMonth())) else it.atEndOfMonth() }
            val comparison = if (previous != null && previousThrough != null)
                repository.observeCategoryTotals(prefs.currency, previous.atDay(1), previousThrough).map { rows -> rows.sumOf { it.expenseMinor } }
                else flowOf<Long?>(null)
            val detail = request.focus?.let { focus -> repository.observeEntries(EntryFilter(prefs.currency,
                type = focus.type, limit = request.limit + 1, category = focus.category, from = from, through = through)) }
                ?: flowOf(emptyList<LedgerEntry>())
            combine(repository.observeCategoryTotals(prefs.currency, from, through),
                repository.observeActivity(prefs.currency, from, through), comparison, detail) { totals, activity, prior, entries ->
                AnalyticsState(prefs, request.month, totals, AnalyticsMath.buckets(activity, request.month, through), prior,
                    request.focus, entries.take(request.limit), entries.size > request.limit, loading = false)
            }.catch { e ->
                if (e is CancellationException) throw e
                emit(AnalyticsState(preferences = prefs, month = request.month, focus = request.focus, loading = false, failed = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsState(month = YearMonth.now(clock)))
    fun month(value: YearMonth?) { if (value == null || value in YearMonth.of(2000, 1)..YearMonth.now(clock)) spec.update { it.copy(month = value, limit = 50) } }
    fun focus(value: AnalyticsFocus?) { spec.update { it.copy(focus = value, limit = 50) } }
    fun more() { spec.update { it.copy(limit = if (it.limit < Int.MAX_VALUE - 51) it.limit + 50 else Int.MAX_VALUE - 1) } }
    fun retry() { spec.update { it.copy(retry = it.retry + 1) } }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/analytics/CinematicCharts.kt

```kotlin
package com.semmyx.ledger.presentation.analytics

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.*

object CinematicTokens {
    val Background: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val Muted: Color @Composable get() = LocalAppearance.current.color("muted")
    val Accent: Color @Composable get() = LocalAppearance.current.color("accent")
    val Income: Color @Composable get() = LocalAppearance.current.color("income")
    val Expense: Color @Composable get() = LocalAppearance.current.color("expense")
    @Composable fun category(category: Category, income: Boolean = false): Color = LocalAppearance.current.category(category, income)
}

@Composable
fun CascadeCard(index: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index.coerceAtMost(8) * 55L); visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(420), label = "cascadeAlpha")
    val offset by animateFloatAsState(if (visible) 0f else 24f, spring(dampingRatio = .85f), label = "cascadeOffset")
    Surface(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; translationY = offset * density },
        shape = RoundedCornerShape(18.dp), color = CinematicTokens.Surface, shadowElevation = 10.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** UI animation only; the SQL total and final displayed amount remain exact Long minor units. */
@Composable
fun Odometer(minor: Long, currency: LedgerCurrency, language: AppLanguage, color: Color = MaterialTheme.colorScheme.onSurface, positiveSign: Boolean = false) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(minor) { progress.snapTo(0f); progress.animateTo(1f, tween(720, easing = FastOutSlowInEasing)) }
    val value = if (progress.value >= .9999f) minor else (minor.toDouble() * progress.value).roundToLong()
    val text = formatReferenceMoney(value, currency, language, positiveSign = positiveSign)
    val exact = formatReferenceMoney(minor, currency, language, positiveSign = positiveSign)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics(mergeDescendants = true) { contentDescription = exact }) {
        text.forEachIndexed { index, digit ->
            key(index) {
                AnimatedContent(targetState = digit, label = "digit$index", transitionSpec = {
                    (slideInVertically(tween(85)) { it } + fadeIn(tween(85))) togetherWith
                        (slideOutVertically(tween(85)) { -it } + fadeOut(tween(85)))
                }) { item -> Text(item.toString(), style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace, color = color) }
            }
        }
    }
}

@Composable
fun PremiumBars(buckets: List<CashBucket>, currency: LedgerCurrency, language: AppLanguage) {
    val palette = LocalAppearance.current
    // Bound rendering work for multi-year history. Every bucket remains available in SQL/category details.
    val bars = remember(buckets) { if (buckets.size <= 48) buckets else buckets.chunked((buckets.size + 47) / 48).map { block ->
        CashBucket(block.first().from, block.last().through, block.sumOf { it.incomeMinor }, block.sumOf { it.expenseMinor })
    } }
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(bars) { progress.snapTo(0f); progress.animateTo(1f, tween(850, easing = FastOutSlowInEasing)) }
    val maxValue = bars.maxOfOrNull { it.expenseMinor }?.coerceAtLeast(1) ?: 1
    val description = stringResource(R.string.cinematic_chart_accessibility)
    val numeric = remember(language) { NumberFormat.getNumberInstance(Locale.forLanguageTag(language.tag)).apply { maximumFractionDigits = 0 } }
    val axisMaximum = remember(maxValue) {
        val units = maxValue.toDouble() / 100
        val magnitude = 10.0.pow(floor(log10(units.coerceAtLeast(1.0))))
        (ceil(units / magnitude) * magnitude * 100).toLong().coerceAtLeast(100)
    }
    val axisLabels = listOf(numeric.format(axisMaximum / 100.0), numeric.format(axisMaximum / 200.0), "0")
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall
    val labelWidthPx = axisLabels.maxOf { measurer.measure(AnnotatedString(it), style = axisStyle, softWrap = false).size.width }
    val axisWidth = with(LocalDensity.current) { labelWidthPx.toDp() } + 10.dp
    Row(Modifier.fillMaxWidth().height(184.dp)) {
        Column(Modifier.width(axisWidth).fillMaxHeight().padding(bottom = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(axisLabels[0], maxLines = 1, softWrap = false, color = CinematicTokens.Muted, style = axisStyle)
            Text(axisLabels[1], maxLines = 1, softWrap = false, color = CinematicTokens.Muted, style = axisStyle)
            Text(axisLabels[2], maxLines = 1, softWrap = false, color = CinematicTokens.Muted, style = axisStyle)
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }
                .pointerInput(bars) { detectTapGestures { point ->
                    if (bars.isNotEmpty()) selected = ((point.x / size.width) * bars.size).toInt().coerceIn(0, bars.lastIndex)
                } }) {
                val baseline = size.height - 12.dp.toPx()
                repeat(3) { index ->
                    val y = baseline * index / 2
                    drawLine(palette.color("grid"), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                }
                val step = size.width / bars.size.coerceAtLeast(1)
                bars.forEachIndexed { index, bucket ->
                    val stagger = (progress.value * 1.3f - index.toFloat() / bars.size.coerceAtLeast(1) * .3f).coerceIn(0f, 1f)
                    val h = (bucket.expenseMinor.toDouble() / axisMaximum * (baseline - 8.dp.toPx()) * stagger).toFloat()
                    if (h > 0) {
                        val x = step * index + step * .12f
                        val brush = Brush.verticalGradient(listOf(palette.color("chartTop"), palette.color("chartBottom")), baseline - h, baseline)
                        drawRoundRect(brush, Offset(x, baseline - h), Size(step * .76f, h), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                        if (selected == index) drawRoundRect(Color.White.copy(alpha = .24f), Offset(x, baseline - h), Size(step * .76f, h), androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
                    }
                }
            }
            selected?.let { index ->
                val bucket = bars[index]
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .95f), shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp).widthIn(max = 220.dp), shadowElevation = 8.dp) {
                    Text(stringResource(R.string.cinematic_tooltip, formatDate(bucket.from, language),
                        formatDate(bucket.through, language), formatReferenceMoney(bucket.expenseMinor, currency, language)),
                        Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    // Accessible equivalent to Canvas touch targets; also gives each column its exact date range.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        bars.take(6).forEachIndexed { index, bucket ->
            TextButton(onClick = { selected = index }, contentPadding = PaddingValues(2.dp)) {
                Text(if (bucket.from.month == bucket.through.month) "${bucket.from.dayOfMonth}–${bucket.through.dayOfMonth}"
                    else "${bucket.from.monthValue}/${bucket.from.year}", style = MaterialTheme.typography.labelSmall, color = CinematicTokens.Muted)
            }
        }
    }
    if (bars.size > 6) Text(stringResource(R.string.cinematic_more_buckets, bars.size), color = CinematicTokens.Muted)
}

/** Large reference-style ring: exploded active sector, circular badge, exact center readout. */
@Composable
fun PremiumDonut(slices: List<CategorySlice>, type: EntryType, selected: Category?, currency: LedgerCurrency,
    language: AppLanguage, onSelect: (Category) -> Unit) {
    val palette = LocalAppearance.current
    val total = slices.sumOf { it.minor }
    val active = slices.find { it.category == selected } ?: slices.firstOrNull()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) { progress.snapTo(0f); progress.animateTo(1f, tween(850, easing = FastOutSlowInEasing)) }
    val description = stringResource(if (type == EntryType.INCOME) R.string.cinematic_income_categories else R.string.cinematic_expense_categories)
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = description }.pointerInput(slices, selected) {
            detectTapGestures { point ->
                var start = -90f
                val outer = size.width * .475f
                val inner = size.width * .247f
                for (slice in slices) {
                    val sweep = (slice.minor.toDouble() / total.coerceAtLeast(1) * 360).toFloat()
                    val middle = (start + sweep / 2) * PI.toFloat() / 180
                    val lift = if (slices.size > 1 && slice.category == active?.category) size.width * .023f else 0f
                    val dx = point.x - size.width / 2 - cos(middle) * lift
                    val dy = point.y - size.height / 2 - sin(middle) * lift
                    val radius = hypot(dx, dy)
                    val angle = (atan2(dy, dx) * 180 / PI.toFloat() - start + 720) % 360
                    if (radius in inner..outer && angle <= sweep) { onSelect(slice.category); break }
                    start += sweep
                }
            }
        }) {
            val outer = size.minDimension * .475f; val inner = size.minDimension * .247f
            val origin = Offset(size.width / 2, size.height / 2)
            drawCircle(palette.color("surface"), radius = (outer + inner) / 2, center = origin, style = Stroke(outer - inner))
            var start = -90f
            slices.forEach { slice ->
                val sweep = (slice.minor.toDouble() / total.coerceAtLeast(1) * 360).toFloat()
                val gap = if (slices.size == 1) 0f else minOf(.85f, sweep * .08f)
                val middle = (start + sweep / 2) * PI.toFloat() / 180
                val lift = if (slices.size > 1 && slice.category == active?.category) size.width * .023f else 0f
                val center = origin + Offset(cos(middle) * lift, sin(middle) * lift)
                val visibleSweep = ((sweep - gap) * progress.value).coerceAtLeast(0f)
                if (visibleSweep > 0f) {
                    val color = palette.category(slice.category, type == EntryType.INCOME)
                    val path = ringSegment(center, outer, inner, start + gap / 2, visibleSweep)
                    val shadow = ringSegment(center + Offset(2.dp.toPx(), 3.dp.toPx()), outer, inner, start + gap / 2, visibleSweep)
                    drawPath(shadow, Color.Black.copy(alpha = .65f))
                    drawPath(path, Brush.linearGradient(listOf(color.copy(alpha = .78f), color, color.copy(alpha = .92f)),
                        Offset(0f, 0f), Offset(size.width, size.height)))
                }
                start += sweep
            }
            if (slices.isEmpty()) drawCircle(palette.color("grid"), radius = (outer + inner) / 2, center = origin, style = Stroke(outer - inner))
        }
        Column(Modifier.fillMaxWidth(.46f), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (active == null) Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted)
            else {
                val tint = CinematicTokens.category(active.category, type == EntryType.INCOME)
                Box(Modifier.size(44.dp).background(tint.copy(alpha = .13f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Icon(active.category.icon(), null, tint = tint, modifier = Modifier.size(27.dp))
                }
                Text(stringResource(active.category.label()), style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(formatReferenceMoney(if (type == EntryType.INCOME) active.minor else -active.minor, currency, language,
                    showCurrency = false, positiveSign = type == EntryType.INCOME), color = palette.type(type))
                Text(percent(active.basisPoints, language), color = CinematicTokens.Muted)
            }
        }
    }
}

private fun ringSegment(center: Offset, outer: Float, inner: Float, start: Float, sweep: Float): Path {
    val radians = start * PI.toFloat() / 180
    if (sweep >= 359.999f) {
        // Skia arcTo(360) is not a reliable filled annulus: two closed ovals define the hole explicitly.
        return Path().apply {
            fillType = PathFillType.EvenOdd
            addOval(Rect(center.x - outer, center.y - outer, center.x + outer, center.y + outer))
            addOval(Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner))
        }
    }
    return Path().apply {
        moveTo(center.x + cos(radians) * outer, center.y + sin(radians) * outer)
        arcTo(Rect(center.x - outer, center.y - outer, center.x + outer, center.y + outer), start, sweep, false)
        val finish = (start + sweep) * PI.toFloat() / 180
        lineTo(center.x + cos(finish) * inner, center.y + sin(finish) * inner)
        arcTo(Rect(center.x - inner, center.y - inner, center.x + inner, center.y + inner), start + sweep, -sweep, false)
        close()
    }
}

fun formatReferenceMoney(minor: Long, currency: LedgerCurrency, language: AppLanguage,
    showCurrency: Boolean = true, positiveSign: Boolean = false): String {
    val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag(language.tag)).apply {
        minimumFractionDigits = if (minor % 100L == 0L) 0 else 2
        maximumFractionDigits = 2
    }
    return (if (positiveSign && minor > 0) "+" else "") + formatter.format(BigDecimal.valueOf(minor, 2)).replace('-', '−') +
        (if (showCurrency) " " + currency.name else "")
}

fun percent(basisPoints: Int, language: AppLanguage): String = if (basisPoints == 10000) "100%" else String.format(Locale.forLanguageTag(language.tag), "%.2f%%", basisPoints / 100.0)
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/auth/AuthViewModel.kt

```kotlin
package com.semmyx.ledger.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.PinPolicy
import com.semmyx.ledger.domain.PinResult
import com.semmyx.ledger.domain.SecurityRepository
import com.semmyx.ledger.presentation.common.guarded
import java.security.MessageDigest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AuthError { INCORRECT, MISMATCH, UNAVAILABLE, BIOMETRIC }
data class AuthState(
    val setup: Boolean,
    val confirming: Boolean = false,
    val digits: Int = 0,
    val busy: Boolean = false,
    val lockoutSeconds: Long = 0,
    val error: AuthError? = null,
)

class AuthViewModel(setup: Boolean, private val security: SecurityRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AuthState(setup, busy = true))
    val state = mutableState.asStateFlow()
    private val unlockedChannel = Channel<Unit>(Channel.BUFFERED)
    val unlocked = unlockedChannel.receiveAsFlow()
    // PIN material is not placed in SavedStateHandle, saved instance state, or logs.
    private val input = CharArray(PinPolicy.LENGTH)
    private var length = 0
    private var firstPin: CharArray? = null

    init {
        guarded(onError = { mutableState.update { it.copy(error = AuthError.UNAVAILABLE, busy = true) } }) {
            val configured = security.hasPin()
            mutableState.update { it.copy(setup = !configured, busy = false) }
            while (isActive) {
                val seconds = security.lockoutSeconds()
                mutableState.update { it.copy(lockoutSeconds = seconds) }
                delay(1000)
            }
        }
    }
    fun digit(value: Int) {
        if (value !in 0..9 || state.value.busy || state.value.lockoutSeconds > 0 || length == input.size) return
        input[length++] = ('0'.code + value).toChar()
        mutableState.update { it.copy(digits = length, error = null) }
        if (length == input.size) submit()
    }
    fun erase() {
        if (state.value.busy || length == 0) return
        input[--length] = 0.toChar()
        mutableState.update { it.copy(digits = length, error = null) }
    }
    fun startOver() {
        if (state.value.busy) return
        firstPin?.fill(0.toChar()); firstPin = null
        clearInput()
        mutableState.update { it.copy(confirming = false, error = null) }
    }
    fun biometricFailed() { mutableState.update { it.copy(error = AuthError.BIOMETRIC) } }

    private fun submit() {
        val attempt = input.copyOf()
        if (state.value.setup && firstPin == null) {
            firstPin = attempt
            clearInput()
            mutableState.update { it.copy(confirming = true) }
            return
        }
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                if (state.value.setup) {
                    val first = requireNotNull(firstPin)
                    // Avoid a string comparison that could short-circuit on the first digit.
                    val firstBytes = ByteArray(first.size) { first[it].code.toByte() }
                    val attemptBytes = ByteArray(attempt.size) { attempt[it].code.toByte() }
                    val same = try { MessageDigest.isEqual(firstBytes, attemptBytes) }
                        finally { firstBytes.fill(0); attemptBytes.fill(0) }
                    if (same) {
                        security.createPin(attempt)
                        first.fill(0.toChar()); firstPin = null
                        unlockedChannel.send(Unit)
                    } else {
                        first.fill(0.toChar()); firstPin = null
                        mutableState.update { it.copy(confirming = false, error = AuthError.MISMATCH) }
                    }
                } else {
                    when (val result = security.verifyPin(attempt)) {
                        PinResult.Success -> unlockedChannel.send(Unit)
                        PinResult.Incorrect -> mutableState.update { it.copy(error = AuthError.INCORRECT) }
                        is PinResult.Locked -> mutableState.update { it.copy(lockoutSeconds = result.remainingSeconds, error = null) }
                    }
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                mutableState.update { it.copy(error = AuthError.UNAVAILABLE) }
            } finally {
                attempt.fill(0.toChar())
                clearInput()
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
    private fun clearInput() { input.fill(0.toChar()); length = 0; mutableState.update { it.copy(digits = 0) } }
    override fun onCleared() { input.fill(0.toChar()); firstPin?.fill(0.toChar()); unlockedChannel.close() }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/auth/AuthenticationScreen.kt

```kotlin
package com.semmyx.ledger.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R

@Composable
fun AuthenticationScreen(
    state: AuthState,
    biometrics: Boolean,
    onDigit: (Int) -> Unit,
    onErase: () -> Unit,
    onStartOver: () -> Unit,
    onBiometric: () -> Unit,
    onSupport: () -> Unit,
) {
    val heading = when { !state.setup -> R.string.unlock_title; state.confirming -> R.string.confirm_pin; else -> R.string.create_pin }
    val description = stringResource(R.string.pin_digit_count, state.digits)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(8.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Default.Lock, null, Modifier.padding(20.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(heading), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (state.setup) R.string.pin_setup_subtitle else R.string.pin_unlock_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = description }.testTag("pinDots"),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(6) { index ->
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (index < state.digits) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.lockoutSeconds > 0) Text(stringResource(R.string.pin_locked, state.lockoutSeconds), color = MaterialTheme.colorScheme.error)
        state.error?.let { error ->
            val res = when (error) {
                AuthError.INCORRECT -> R.string.pin_incorrect; AuthError.MISMATCH -> R.string.pin_mismatch
                AuthError.UNAVAILABLE -> R.string.security_error; AuthError.BIOMETRIC -> R.string.biometric_failed
            }
            Text(stringResource(res), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        // Native buttons provide keyboard / switch access and never expose PIN text to autofill.
        Column(Modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in 0..2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (column in 1..3) {
                        val digit = row * 3 + column
                        PinKey(digit, !state.busy && state.lockoutSeconds == 0L, { onDigit(digit) }, Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (biometrics && !state.setup) IconButton(onClick = onBiometric, enabled = !state.busy, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Fingerprint, stringResource(R.string.unlock_biometric), Modifier.size(28.dp))
                    }
                }
                PinKey(0, !state.busy && state.lockoutSeconds == 0L, { onDigit(0) }, Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onErase, enabled = !state.busy && state.digits > 0, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.erase_digit))
                    }
                }
            }
        }
        if (state.confirming) TextButton(onClick = onStartOver, enabled = !state.busy) { Text(stringResource(R.string.start_over)) }
        TextButton(onClick = onSupport) { Text(stringResource(R.string.need_help)) }
        Text(stringResource(R.string.pin_recovery_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PinKey(value: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 64.dp).testTag("pinKey$value"),
        shape = MaterialTheme.shapes.medium, contentPadding = PaddingValues(8.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/bank/BankScreen.kt

```kotlin
package com.semmyx.ledger.presentation.bank

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.BuildConfig
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.theme.LocalAppearance
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BankScreen(model: BankViewModel, repository: BankRepository, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
    val reviews by model.reviews.collectAsStateWithLifecycle()
    val custom by model.customCategories.collectAsStateWithLifecycle()
    val enabled by model.enabled.collectAsStateWithLifecycle()
    val automatic by model.auto.collectAsStateWithLifecycle()
    val failed by model.error.collectAsStateWithLifecycle()
    val revision by model.revision.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalAppearance.current
    var notificationAccess by remember { mutableStateOf(false) }
    var smsAccess by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    var packageDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    
    fun openSettings(action: String, data: Uri? = null) {
        try { context.startActivity(Intent(action, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { exportFailed = true }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsAccess = it }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            try { withContext(Dispatchers.IO) {
                val rows = repository.exportEntries()
                context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write("id,merchant,amount_minor,currency,type,source,bank,category,card_last4,event_time,date,origin\n")
                    fun csv(value: String): String {
                        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'" + value else value
                        return "\"" + safe.replace("\"", "\"\"") + "\""
                    }
                    rows.forEach { e -> writer.write(listOf(e.id, e.title, e.amountMinor.toString(), e.currency.name,
                        e.type.name, e.sourceType, Banks.name(e.bankId), custom.firstOrNull { it.id == e.customCategoryId }?.name ?: e.category.name,
                        e.cardLast4.orEmpty(), e.eventAtMillis?.toString().orEmpty(), e.date.toString(), e.origin).joinToString(",", transform = ::csv) + "\n") }
                } ?: error("Export destination unavailable")
            }; exportFailed = false } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { exportFailed = true }
        }
    }
    LifecycleResumeEffect(Unit) {
        notificationAccess = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        smsAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    val currency = state.preferences.currency
    val language = state.preferences.language
    val total = state.categories.sumOf { it.totalMinor }
    val budget = remember(currency, revision) { model.budget(currency) }
    var budgetText by remember(currency, budget) { mutableStateOf(if (budget > 0) Money.editable(budget) else "") }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.bank_dashboard)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
    }, actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, stringResource(R.string.add_entry)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ContentCard {
                Text(stringResource(R.string.bank_import_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.bank_consent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.bank_enabled), Modifier.weight(1f)); Switch(enabled, model::enabled) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.bank_auto), Modifier.weight(1f)); Switch(automatic, model::auto, enabled = enabled) }
                Text(stringResource(if (notificationAccess) R.string.bank_access_yes else R.string.bank_access_no))
                OutlinedButton(onClick = { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text(stringResource(R.string.bank_notification_permission)) }
                if (BuildConfig.FLAVOR == "legacyV7") {
                    Text(stringResource(if (smsAccess) R.string.bank_sms_yes else R.string.bank_sms_no))
                    OutlinedButton(onClick = { smsPermission.launch(Manifest.permission.RECEIVE_SMS) }) { Text(stringResource(R.string.bank_sms_permission)) }
                } else Text(stringResource(R.string.bank_play_sms), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)) }) { Text(stringResource(R.string.bank_app_permissions)) }
                TextButton(onClick = { privacy = true }) { Text(stringResource(R.string.bank_privacy)) }
            } }
            item { ContentCard {
                Text(stringResource(R.string.import_diagnostics), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (diagnostics.connected) R.string.import_connected else R.string.import_disconnected))
                Text(stringResource(R.string.import_counts, diagnostics.notificationCount, diagnostics.bankMessageCount))
                Text(stringResource(importReason(diagnostics.lastCode)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (diagnostics.lastPackage.isNotBlank()) Text(diagnostics.lastPackage, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    if (!notificationAccess) openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    else try { android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(context, com.semmyx.ledger.bank.BankNotificationListener::class.java)) }
                    catch (_: Exception) { exportFailed = true }
                }) { Text(stringResource(R.string.import_reconnect)) }
                TextButton(onClick = { packageDialog = true }) { Text(stringResource(R.string.import_package_settings)) }
                Button(onClick = { pasteDialog = true }) { Text(stringResource(R.string.import_paste)) }
                Text(stringResource(R.string.import_queue_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (failed || exportFailed || state.failed) item { Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAY" to R.string.bank_day, "WEEK" to R.string.bank_week, "MONTH" to R.string.bank_month, "ALL" to R.string.all_time).forEach { (period, title) ->
                    FilterChip(selected = state.filter.period == period, onClick = { model.period(period) }, label = { Text(stringResource(title)) })
                }
            } }
            item { ContentCard {
                Text(stringResource(R.string.expenses), color = palette.type(EntryType.EXPENSE))
                Text(formatMoney(total, currency, language), style = MaterialTheme.typography.headlineMedium, color = palette.type(EntryType.EXPENSE))
                FilterChip(selected = state.filter.source == null, onClick = { model.source(null) }, label = { Text(stringResource(R.string.all)) })
                state.sources.forEach { source ->
                    val name = when (source.sourceType) { "CASH" -> stringResource(R.string.bank_cash); "CARD" -> Banks.name(source.bankId); else -> stringResource(R.string.bank_unknown) }
                    Row(Modifier.fillMaxWidth().clickable { model.source(source.sourceType, source.bankId) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f)); Text(formatMoney(source.totalMinor, currency, language))
                    }
                }
                if (state.filter.period == "MONTH" && budget > 0) {
                    Text(stringResource(R.string.bank_budget_progress, formatMoney(state.sources.sumOf { it.totalMinor }, currency, language), formatMoney(budget, currency, language)))
                    LinearProgressIndicator(progress = { (state.sources.sumOf { it.totalMinor }.toDouble() / budget).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            } }
            item { ContentCard {
                Text(stringResource(R.string.bank_category_stats), style = MaterialTheme.typography.titleMedium)
                state.categories.forEach { cat ->
                    val color = cat.colorHex?.let { Color(0xFF000000L or it.removePrefix("#").toLong(16)) } ?: palette.category(cat.category, false)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(22.dp).background(color, CircleShape))
                        Text(cat.customName ?: stringResource(cat.category.label()), Modifier.weight(1f))
                        Text(formatMoney(cat.totalMinor, currency, language))
                    }
                    LinearProgressIndicator(progress = { if (total == 0L) 0f else (cat.totalMinor.toDouble() / total).toFloat() }, modifier = Modifier.fillMaxWidth(), color = color)
                }
                TextButton(onClick = { categoryDialog = true }) { Text(stringResource(R.string.bank_new_category)) }
                custom.forEach { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            if (reviews.isNotEmpty()) item { Text(stringResource(R.string.bank_review_count, reviews.size), style = MaterialTheme.typography.titleLarge) }
            items(reviews, key = { "review-" + it.id }) { item -> ContentCard {
                Text(Banks.name(item.bankId) + " · " + item.merchant)
                Text(formatMoney(item.amountMinor, item.currency, language), style = MaterialTheme.typography.titleMedium)
                Text(Instant.ofEpochMilli(item.eventAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.forLanguageTag(language.tag))))
                item.cardLast4?.let { Text("•••• $it") }
                if (!item.timeFromMessage) Text(stringResource(R.string.bank_time_received), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.status == "DUPLICATE_REVIEW") Text(stringResource(R.string.bank_duplicate), color = MaterialTheme.colorScheme.error)
                if (item.transfer) Text(stringResource(R.string.bank_transfer_review))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { model.accept(item.id, EntryType.EXPENSE) }) { Text(stringResource(R.string.expenses), color = palette.type(EntryType.EXPENSE)) }
                    TextButton(onClick = { model.accept(item.id, EntryType.INCOME) }) { Text(stringResource(R.string.income), color = palette.type(EntryType.INCOME)) }
                    TextButton(onClick = { model.ignore(item.id) }) { Text(stringResource(R.string.bank_ignore)) }
                }
            } }
            item { Text(stringResource(R.string.bank_recent), style = MaterialTheme.typography.titleLarge) }
            items(state.entries, key = { it.id }) { entry -> ContentCard(Modifier.clickable { onEdit(entry.id) }) {
                Text(entry.title)
                Text(formatMoney(entry.amountMinor, entry.currency, language), color = palette.type(entry.type))
                Text(formatDate(entry.date, language) + " · " + if (entry.sourceType == "CASH") stringResource(R.string.bank_cash) else Banks.name(entry.bankId))
            } }
            item { Text(stringResource(R.string.bank_limit_recent), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { ContentCard {
                Text(stringResource(R.string.bank_budget), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(budgetText, { budgetText = it.take(16) }, singleLine = true, label = { Text(currency.name) }, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = Money.parseMinor(budgetText) != null, onClick = { model.setBudget(currency, budgetText) }) { Text(stringResource(R.string.save_entry)) }
                if (budget > 0) TextButton(onClick = { model.resetBudget(currency) }) { Text(stringResource(R.string.bank_budget_disable)) }
                OutlinedButton(onClick = { export.launch("TG-Ledger-transactions.csv") }) { Text(stringResource(R.string.bank_export)) }
                TextButton(onClick = { clearDialog = true }) { Text(stringResource(R.string.bank_clear_history)) }
            } }
        }
    }
    if (pasteDialog) {
        var bankId by remember { mutableStateOf(Banks.all.first().id) }
        var body by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }
        val selected = Banks.all.first { it.id == bankId }
        val preview = remember(bankId, body) { BankParser.parse(selected, body, System.currentTimeMillis()) }
        AlertDialog(onDismissRequest = { if (!submitting) pasteDialog = false }, title = { Text(stringResource(R.string.import_paste)) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Banks.all.forEach { bank ->
                    FilterChip(selected = bank.id == bankId, onClick = { bankId = bank.id }, label = { Text(bank.name) })
                } }
                OutlinedTextField(body, { body = it.take(8000) }, label = { Text(stringResource(R.string.import_text)) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, enabled = !submitting)
                if (preview != null) {
                    Text(formatMoney(preview.amountMinor, preview.currency, language), style = MaterialTheme.typography.titleLarge)
                    Text(preview.merchant.ifBlank { selected.name })
                    Text(stringResource(if (preview.type == EntryType.INCOME) R.string.income else R.string.expenses), color = palette.type(preview.type))
                    preview.cardLast4?.let { Text("•••• $it") }
                } else if (body.isNotBlank()) Text(stringResource(R.string.import_unrecognized), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.import_manual_privacy), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(enabled = preview != null && !submitting, onClick = {
            submitting = true
            scope.launch {
                try { if (withContext(Dispatchers.IO) { repository.importText(bankId, body) }) { body = ""; pasteDialog = false } else exportFailed = true }
                catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { exportFailed = true }
                finally { submitting = false }
            }
        }) { Text(stringResource(R.string.import_to_queue)) } }, dismissButton = { TextButton(enabled = !submitting, onClick = { body = ""; pasteDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (packageDialog) {
        var bankId by remember { mutableStateOf(Banks.all.first().id) }
        var packageName by remember(bankId) { mutableStateOf(repository.configuredPackage(bankId)) }
        AlertDialog(onDismissRequest = { packageDialog = false }, title = { Text(stringResource(R.string.import_package_settings)) }, text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.import_package_warning))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Banks.all.forEach { bank ->
                    FilterChip(selected = bank.id == bankId, onClick = { bankId = bank.id }, label = { Text(bank.name) })
                } }
                OutlinedTextField(packageName, { packageName = it.trim().take(200) }, singleLine = true, label = { Text("package · com.example.bank") })
                Text(diagnostics.lastPackage, style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(enabled = packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")), onClick = { model.configurePackage(bankId, packageName); packageDialog = false }) { Text(stringResource(R.string.save_entry)) } },
            dismissButton = { TextButton(onClick = { packageDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (categoryDialog) {
        var name by remember { mutableStateOf("") }; var hex by remember { mutableStateOf("#6FAEFF") }
        AlertDialog(onDismissRequest = { categoryDialog = false }, title = { Text(stringResource(R.string.bank_new_category)) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.category)) }, singleLine = true)
                OutlinedTextField(hex, { hex = it.take(7) }, label = { Text("HEX · #RRGGBB") }, singleLine = true, isError = ColorPreferences.normalizeHex(hex) == null)
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank() && ColorPreferences.normalizeHex(hex) != null, onClick = { model.createCategory(name, hex); categoryDialog = false }) { Text(stringResource(R.string.save_entry)) } },
            dismissButton = { TextButton(onClick = { categoryDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text(stringResource(R.string.bank_clear_history)) },
        text = { Text(stringResource(R.string.bank_clear_warning)) }, confirmButton = { TextButton(onClick = { model.clearHistory(); clearDialog = false }) { Text(stringResource(R.string.bank_clear_history)) } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.cancel)) } })
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text(stringResource(R.string.bank_privacy)) },
        text = { Text(stringResource(R.string.bank_privacy_body)) }, confirmButton = { TextButton(onClick = { privacy = false }) { Text(stringResource(R.string.done)) } })
}

private fun importReason(code: String): Int = when (code) {
    "DISABLED" -> R.string.import_disabled
    "UNSUPPORTED_APP" -> R.string.import_unsupported
    "EMPTY_TEXT" -> R.string.import_empty
    "GROUP_SUMMARY" -> R.string.import_group
    "UNRECOGNIZED" -> R.string.import_unrecognized
    "AUTO_IMPORTED" -> R.string.import_auto_done
    "QUEUED" -> R.string.import_queued
    "DUPLICATE_REVIEW" -> R.string.bank_duplicate
    "REDELIVERED" -> R.string.import_repeated
    "ERROR" -> R.string.storage_error
    else -> R.string.import_waiting
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/bank/BankViewModel.kt

```kotlin
package com.semmyx.ledger.presentation.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BankFilter(val period: String = "MONTH", val source: String? = null, val bank: String? = null)
data class BankDashboardState(val preferences: UserPreferences = UserPreferences(), val filter: BankFilter = BankFilter(),
    val sources: List<BankSourceSpend> = emptyList(), val categories: List<BankCategorySpend> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(), val failed: Boolean = false)
@OptIn(ExperimentalCoroutinesApi::class)
class BankViewModel(private val repo: BankRepository, preferences: PreferencesRepository, private val clock: Clock) : ViewModel() {
    private val filter = MutableStateFlow(BankFilter())
    private val today = MutableStateFlow(LocalDate.now(clock))
    init { viewModelScope.launch {
        while (isActive) {
            val nextDay = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
            delay((nextDay - clock.millis()).coerceAtLeast(1000L))
            today.value = LocalDate.now(clock)
        }
    } }
    val diagnostics = repo.diagnostics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportDiagnostics())
    fun configurePackage(bankId: String, packageName: String) = task { repo.setPackage(bankId, packageName) }
    val customCategories = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reviews = repo.reviews().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val enabled = MutableStateFlow(repo.enabled)
    val auto = MutableStateFlow(repo.autoImport)
    val error = MutableStateFlow(false)
    val revision = MutableStateFlow(0)
    val state = combine(preferences.preferences, filter, today) { prefs, selected, date -> Triple(prefs, selected, date) }.flatMapLatest { (prefs, selected, through) ->
        val from = when (selected.period) { "DAY" -> through; "WEEK" -> through.minusDays(6); "ALL" -> LocalDate.of(2000, 1, 1); else -> through.withDayOfMonth(1) }
        combine(repo.sources(prefs.currency, from, through), repo.categorySpending(prefs.currency, from, through, selected.source, selected.bank),
            repo.recent(prefs.currency, from, through, selected.source, selected.bank)) { sources, cats, entries -> BankDashboardState(prefs, selected, sources, cats, entries) }
    }.catch { emit(BankDashboardState(failed = true)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BankDashboardState())
    fun period(value: String) { filter.update { it.copy(period = value) } }
    fun source(value: String?, bank: String? = null) { filter.update { it.copy(source = value, bank = bank) } }
    fun enabled(value: Boolean) { repo.enabled = value; enabled.value = value }
    fun auto(value: Boolean) { repo.autoImport = value; auto.value = value }
    fun accept(id: String, type: EntryType) = task { repo.accept(id, type) }
    fun ignore(id: String) = task { repo.ignore(id) }
    fun createCategory(name: String, hex: String) = task { repo.createCategory(name, hex) }
    fun clearHistory() = task { repo.clearHistory() }
    fun budget(currency: LedgerCurrency) = repo.budget(currency)
    fun resetBudget(currency: LedgerCurrency) = task { repo.setBudget(currency, 0L); revision.value++ }
    fun setBudget(currency: LedgerCurrency, amount: String) = task {
        val parsed = Money.parseMinor(amount) ?: kotlin.error("Invalid amount")
        repo.setBudget(currency, parsed); revision.value++
    }
    private fun task(block: suspend () -> Unit) { viewModelScope.launch { try { block(); error.value = false }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { error.value = true } } }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/common/Components.kt

```kotlin
package com.semmyx.ledger.presentation.common

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

fun formatMoney(minor: Long, currency: LedgerCurrency, language: AppLanguage): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag(language.tag)).apply {
        this.currency = Currency.getInstance(currency.name)
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(minor, 2))

fun formatDate(date: LocalDate, language: AppLanguage): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.tag)))

/** Used for the developer-activation expiry date, which is a real instant rather than a calendar day. */
fun formatDateTime(epochMillis: Long, language: AppLanguage): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.tag)))

@StringRes fun Category.label(): Int = when (this) {
    Category.FOOD -> R.string.category_food; Category.TRANSPORT -> R.string.category_transport
    Category.HOME -> R.string.category_home; Category.HEALTH -> R.string.category_health
    Category.LEISURE -> R.string.category_leisure; Category.SALARY -> R.string.category_salary
    Category.OTHER -> R.string.category_other
    Category.TRANSFER -> R.string.category_transfer; Category.DINING -> R.string.category_dining; Category.CASHBACK -> R.string.category_cashback
}
fun Category.icon(): ImageVector = when (this) {
    Category.FOOD -> Icons.Default.ShoppingCart; Category.TRANSPORT -> Icons.Default.DirectionsBus
    Category.HOME -> Icons.Default.Home; Category.HEALTH -> Icons.Default.FavoriteBorder
    Category.LEISURE -> Icons.Default.LocalActivity; Category.SALARY -> Icons.Default.WorkOutline
    Category.OTHER -> Icons.Default.ReceiptLong
    Category.TRANSFER -> Icons.Default.DoubleArrow; Category.DINING -> Icons.Default.Restaurant; Category.CASHBACK -> Icons.Default.CurrencyExchange
}
@StringRes fun UiMessage.label(): Int = when (this) {
    UiMessage.STORAGE -> R.string.storage_error
    UiMessage.SAVED -> R.string.saved
    UiMessage.DELETED -> R.string.entry_deleted
    UiMessage.UNDO_EXPIRED -> R.string.undo_expired
    UiMessage.RATE_OFFLINE -> R.string.rate_offline
    UiMessage.BIOMETRIC_UNAVAILABLE -> R.string.biometric_unavailable
    UiMessage.NAME_INVALID -> R.string.name_invalid
    UiMessage.NOT_FOUND -> R.string.entry_not_found
    UiMessage.PREMIUM_ACTIVATED -> R.string.premium_activated
    UiMessage.ACTIVATION_INVALID -> R.string.activation_invalid
}
@StringRes fun BillingIssue.label(): Int = when (this) {
    BillingIssue.UNAVAILABLE -> R.string.billing_unavailable
    BillingIssue.CONFIGURATION -> R.string.billing_configuration
    BillingIssue.NETWORK -> R.string.billing_network
    BillingIssue.PENDING -> R.string.billing_pending
    BillingIssue.VERIFICATION -> R.string.billing_verification
    BillingIssue.ACKNOWLEDGEMENT -> R.string.billing_acknowledgement
    BillingIssue.ACTIVATION_CODE -> R.string.billing_activation_code
}

@Composable
fun LedgerMark(modifier: Modifier = Modifier, color: Color = LocalAppearance.current.color("icons")) {
    Canvas(modifier.size(40.dp)) {
        val w = size.width / 5f
        listOf(0.42f, 0.68f, 1f).forEachIndexed { index, height ->
            drawRoundRect(color, Offset(index * w * 1.6f, size.height * (1 - height)), Size(w, size.height * height), CornerRadius(w * .2f))
        }
    }
}

@Composable
fun LedgerBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(R.string.overview, R.string.settings, R.string.help, R.string.analytics_title, R.string.bank_dashboard)
    val icons = listOf(Icons.Default.SpaceDashboard, Icons.Default.Tune, Icons.Default.HelpOutline, Icons.Default.BarChart, Icons.Default.AccountBalance)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        labels.forEachIndexed { index, label ->
            NavigationBarItem(selected = index == selected, onClick = { onSelect(index) }, icon = { Icon(icons[index], null, tint = LocalAppearance.current.color("icons").copy(alpha = if (index == selected) 1f else .65f)) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
fun ContentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun EmptyState(title: String, description: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.ReceiptLong, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/common/ViewModelSupport.kt

```kotlin
package com.semmyx.ledger.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Cancellation is control flow, not a user-facing error. */
fun ViewModel.guarded(onError: (Exception) -> Unit, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch {
        try { block() } catch (error: Exception) {
            if (error is CancellationException) throw error
            onError(error)
        }
    }

class LedgerViewModelFactory<T : ViewModel>(private val modelClass: Class<T>, private val create: () -> T) : ViewModelProvider.Factory {
    override fun <V : ViewModel> create(modelClass: Class<V>): V {
        require(modelClass.isAssignableFrom(this.modelClass))
        return modelClass.cast(create()) ?: error("Invalid ViewModel type")
    }
}

enum class UiMessage {
    STORAGE,
    SAVED,
    DELETED,
    UNDO_EXPIRED,
    RATE_OFFLINE,
    BIOMETRIC_UNAVAILABLE,
    NAME_INVALID,
    NOT_FOUND,
    PREMIUM_ACTIVATED,
    ACTIVATION_INVALID,
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/dashboard/ActivityChart.kt

```kotlin
package com.semmyx.ledger.presentation.dashboard

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.ContentCard
import com.semmyx.ledger.presentation.common.formatMoney
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityChart(days: List<DailyActivity>, currency: LedgerCurrency, language: AppLanguage, premium: Boolean,
    selectedDays: Int, onDays: (Int) -> Unit, onPremium: () -> Unit) {
    val plotted = remember(days, selectedDays) {
        if (selectedDays != 0 || days.size <= 48) days else {
            val chunk = (days.size + 47) / 48
            days.chunked(chunk).map { block -> DailyActivity(block.first().date, block.sumOf { it.incomeMinor }, block.sumOf { it.expenseMinor }) }
        }
    }
    val income = days.sumOf { it.incomeMinor }
    val expense = days.sumOf { it.expenseMinor }
    val maxValue = plotted.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) }?.coerceAtLeast(1) ?: 1
    val summary = if (selectedDays == 0) stringResource(R.string.all_time) else stringResource(R.string.chart_description, selectedDays, formatMoney(income, currency, language), formatMoney(expense, currency, language))
    val incomeColor = MaterialTheme.colorScheme.secondary
    val expenseColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    var shown by remember(days) { mutableStateOf(false) }
    LaunchedEffect(days) { shown = true }
    // Compose respects the system animator duration scale, including disabled animations.
    val progress by animateFloatAsState(if (shown) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow), label = "chartEntrance")
    ContentCard {
        Text(stringResource(R.string.cash_flow), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selectedDays == 7, onClick = { onDays(7) }, label = { Text(stringResource(R.string.last_7_days)) })
            FilterChip(selected = selectedDays == 30, onClick = { if (premium) onDays(30) else onPremium() },
                label = { Text(stringResource(if (premium) R.string.last_30_days else R.string.last_30_days_plus)) })
        }
        FilterChip(selected = selectedDays == 0, onClick = { if (premium) onDays(0) else onPremium() },
            label = { Text(stringResource(R.string.all_time)) })
        if (income == 0L && expense == 0L) {
            Text(stringResource(R.string.chart_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(formatMoney(maxValue, currency, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = summary }) {
                val base = size.height - 1.dp.toPx()
                repeat(3) { line ->
                    val y = base * line / 2f
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                val step = size.width / plotted.size.coerceAtLeast(1)
                val barWidth = step * .28f
                plotted.forEachIndexed { index, day ->
                    val incomeHeight = (day.incomeMinor.toDouble() / maxValue * (base - 4.dp.toPx())).toFloat() * progress
                    val expenseHeight = (day.expenseMinor.toDouble() / maxValue * (base - 4.dp.toPx())).toFloat() * progress
                    val x = step * index + step * .16f
                    if (incomeHeight > 0) drawRoundRect(incomeColor, Offset(x, base - incomeHeight), Size(barWidth, incomeHeight), CornerRadius(2.dp.toPx()))
                    if (expenseHeight > 0) drawRoundRect(expenseColor, Offset(x + barWidth + step * .08f, base - expenseHeight), Size(barWidth, expenseHeight), CornerRadius(2.dp.toPx()))
                }
            }
            if (days.isNotEmpty()) {
                val formatter = remember(language) { DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag(language.tag)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(days.first().date.format(formatter), style = MaterialTheme.typography.bodyMedium)
                    Text(days.last().date.format(formatter), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // Position is a second visual cue; the chart is not color-only.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.legend_income, formatMoney(income, currency, language)), color = incomeColor, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.legend_expense, formatMoney(expense, currency, language)), color = expenseColor, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/dashboard/CategoryDetails.kt

```kotlin
package com.semmyx.ledger.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.analytics.*

/** Reference-style category cards on the MAIN screen, not hidden in a separate analytics route. */
@Composable
fun CategorySummary(state: DashboardState, onCategory: (Category, EntryType) -> Unit, onMore: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { type ->
            val slices = AnalyticsMath.slices(state.categoryTotals, type)
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), color = LocalAppearance.current.type(type))
                        TextButton(onClick = onMore) { Text(stringResource(R.string.cinematic_more), color = CinematicTokens.Accent) }
                    }
                    if (slices.isEmpty()) Text(stringResource(R.string.category_empty), color = CinematicTokens.Muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        slices.take(4).forEach { slice ->
                            val tint = CinematicTokens.category(slice.category, type == EntryType.INCOME)
                            Column(Modifier.weight(1f).clickable { onCategory(slice.category, type) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(48.dp).background(tint.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(slice.category.icon(), stringResource(slice.category.label()), tint = tint)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor,
                                    state.preferences.currency, state.preferences.language, showCurrency = false, positiveSign = type == EntryType.INCOME),
                                    color = if (type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryDetails(state: DashboardState, initialType: EntryType, onClose: () -> Unit, onDays: (Int) -> Unit,
    onMore: () -> Unit, onEdit: (String) -> Unit, onSelectCategory: (Category) -> Unit) {
    val category = state.selectedCategory ?: return
    var type by remember(initialType) { mutableStateOf(initialType) }
    val slices = AnalyticsMath.slices(state.categoryTotals, type)
    val total = slices.sumOf { it.minor }
    val selected = slices.find { it.category == category }
    val period = stringResource(when (state.days) { 0 -> R.string.all_time; 30 -> R.string.last_30_days; else -> R.string.last_7_days })
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
                TopAppBar(title = { Text(stringResource(if (type == EntryType.EXPENSE) R.string.expenses else R.string.income)) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, titleContentColor = MaterialTheme.colorScheme.onSurface, navigationIconContentColor = MaterialTheme.colorScheme.onSurface))
            }) { padding ->
                LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7 to R.string.last_7_days, 30 to R.string.last_30_days, 0 to R.string.all_time).forEach { (value, title) ->
                                FilterChip(selected = state.days == value, enabled = value == 7 || state.premium,
                                    onClick = { onDays(value) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface,
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.primary, labelColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    label = { Text(stringResource(title), maxLines = 1, style = MaterialTheme.typography.labelMedium) })
                            }
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(EntryType.EXPENSE to R.string.expenses, EntryType.INCOME to R.string.income).forEach { (choice, title) ->
                                    Surface(modifier = Modifier.weight(1f), color = if (type == choice) LocalAppearance.current.type(choice).copy(alpha = .18f) else Color.Transparent,
                                        shape = RoundedCornerShape(11.dp), onClick = { type = choice }) {
                                        Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                                            Text(stringResource(title), color = LocalAppearance.current.type(choice).copy(alpha = if (type == choice) 1f else .78f))
                                        }
                                    }
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(when (state.days) {
                                0 -> R.string.total_all_time
                                30 -> R.string.total_thirty_days
                                else -> R.string.total_seven_days
                            }), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                            Text(formatReferenceMoney(if (type == EntryType.INCOME) total else -total, state.preferences.currency,
                                state.preferences.language, positiveSign = type == EntryType.INCOME), style = MaterialTheme.typography.headlineSmall,
                                color = if (type == EntryType.INCOME) CinematicTokens.Income else CinematicTokens.Expense)
                        }
                        PremiumDonut(slices, type, category, state.preferences.currency, state.preferences.language, onSelectCategory)
                    }
                    item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(if (type == EntryType.EXPENSE) R.string.cinematic_expense_categories else R.string.cinematic_income_categories), style = MaterialTheme.typography.titleLarge)
                                slices.forEachIndexed { index, slice ->
                                    val tint = CinematicTokens.category(slice.category, type == EntryType.INCOME)
                                    Row(Modifier.fillMaxWidth().clickable { onSelectCategory(slice.category) }.padding(vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Box(Modifier.size(46.dp).background(tint.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(slice.category.icon(), null, tint = tint)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(slice.category.label()))
                                            Text(stringResource(R.string.category_transaction_count, slice.count), style = MaterialTheme.typography.bodySmall, color = CinematicTokens.Muted)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(formatReferenceMoney(if (type == EntryType.INCOME) slice.minor else -slice.minor, state.preferences.currency,
                                                state.preferences.language, showCurrency = false, positiveSign = type == EntryType.INCOME), color = LocalAppearance.current.type(type))
                                            Text(percent(slice.basisPoints, state.preferences.language), color = CinematicTokens.Muted)
                                        }
                                    }
                                    if (index != slices.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                    item {
                        Text(stringResource(category.label()), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.category_transaction_count, selected?.count ?: 0), color = CinematicTokens.Muted)
                    }
                    items(state.categoryEntries.filter { it.type == type }, key = { it.id }) { entry ->
                        Card(onClick = { onClose(); onEdit(entry.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.title); Text(formatDate(entry.date, state.preferences.language), color = CinematicTokens.Muted)
                                Text(formatReferenceMoney(if (type == EntryType.INCOME) entry.amountMinor else -entry.amountMinor, entry.currency,
                                    state.preferences.language, positiveSign = type == EntryType.INCOME), color = LocalAppearance.current.type(type))
                                if (entry.note.isNotBlank()) Text(entry.note, color = CinematicTokens.Muted)
                            }
                        }
                    }
                    if (state.categoryCanLoadMore) item { OutlinedButton(onClick = onMore) { Text(stringResource(R.string.load_more)) } }
                }
            }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/dashboard/DashboardScreen.kt

```kotlin
package com.semmyx.ledger.presentation.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.premium.AssistantCharacterCard
import com.semmyx.ledger.presentation.premium.trackCatTouch
import android.widget.Toast
import com.semmyx.ledger.voice.OptionalVoiceAssistant
import com.semmyx.ledger.voice.VoiceCommand
import com.semmyx.ledger.voice.VoiceEntryParser
import com.semmyx.ledger.voice.VoiceSmallTalk
import java.time.LocalDate
import com.semmyx.ledger.voice.FinanceQuery
import com.semmyx.ledger.voice.SpokenMoney
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(model: DashboardViewModel, host: FragmentActivity, onNavigate: (Int) -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit,
    speechRecognizerLauncher: ActivityResultLauncher<Intent>, micPermissionLauncher: ActivityResultLauncher<String>,
    setOnSpeechResult: ((Intent?) -> Unit) -> Unit, setOnMicPermissionResult: ((Boolean) -> Unit) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(model, context) {
        model.events.collect { event ->
            when (event) {
                is DashboardEvent.Deleted -> {
                    val result = snackbar.showSnackbar(context.getString(R.string.entry_deleted), context.getString(R.string.undo),
                        withDismissAction = true, duration = SnackbarDuration.Long)
                    if (result == SnackbarResult.ActionPerformed) model.undo(event.token)
                }
                is DashboardEvent.Message -> snackbar.showSnackbar(context.getString(event.message.label()))
            }
        }
    }
    DashboardScreen(state, snackbar, host, onNavigate, onAdd, onEdit, model::delete, model::search, model::filter, model::days, model::loadMore, model::retry,
        model::addVoiceEntry, speechRecognizerLauncher, micPermissionLauncher, setOnSpeechResult, setOnMicPermissionResult,
        onCategory = model::category, onMoreCategory = model::loadMoreCategory,
        onStatistics = { request, reply -> scope.launch {
            try { reply(model.answerStatistics(request)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.storage_error)) }
        } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(state: DashboardState, snackbar: SnackbarHostState, host: FragmentActivity, onNavigate: (Int) -> Unit,
    onAdd: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit, onSearch: (String) -> Unit,
    onFilter: (EntryType?) -> Unit, onDays: (Int) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit,
    onVoiceEntry: (com.semmyx.ledger.voice.VoiceEntryDraft) -> Unit,
    speechRecognizerLauncher: ActivityResultLauncher<Intent>, micPermissionLauncher: ActivityResultLauncher<String>,
    setOnSpeechResult: ((Intent?) -> Unit) -> Unit, setOnMicPermissionResult: ((Boolean) -> Unit) -> Unit,
    onCategory: (Category?) -> Unit, onMoreCategory: () -> Unit,
    onStatistics: (FinanceQuery.Statistics, (Long) -> Unit) -> Unit) {
    var categoryDetailType by remember { mutableStateOf(EntryType.EXPENSE) }
    if (state.selectedCategory != null) CategoryDetails(state, initialType = categoryDetailType,
        onClose = { onCategory(null) }, onDays = onDays, onMore = onMoreCategory,
        onEdit = onEdit, onSelectCategory = { onCategory(it) })
    Scaffold(
        topBar = { TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedgerMark(Modifier.size(24.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            }
        }, actions = {
            TextButton(onClick = { onNavigate(1) }) { Text(state.preferences.currency.name) }
        }) },
        bottomBar = { LedgerBottomBar(0, onNavigate) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Default.Add, null) },
            text = { Text(stringResource(R.string.add_entry)) }, modifier = Modifier.testTag("addEntry")) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        // The two Activity Result launchers are registered once, unconditionally, at the very top
        // of AppRoot -- before the PIN screen even shows -- because a launcher must be registered
        // before the host Activity passes STARTED, and this Dashboard route is only entered after
        // PIN unlock, long after STARTED. Only the *handling* of a result is Dashboard-specific
        // (it needs today's balance/spend and the current language), so that part is wired up
        // below via a plain callback that AppRoot invokes; SideEffect below keeps it pointed at the
        // latest state on every recomposition. The speech engine itself is still created lazily on
        // first use, so free-tier and opted-out users still pay no extra cost.
        val voiceContext = LocalContext.current
        var voiceAssistant by remember(host) { mutableStateOf<OptionalVoiceAssistant?>(null) }
        var listening by remember { mutableStateOf(false) }
        var lastReply by remember { mutableStateOf<String?>(null) }
        DisposableEffect(host) { onDispose { voiceAssistant?.shutdown() } }
        fun ensureAssistant(): OptionalVoiceAssistant = voiceAssistant ?: OptionalVoiceAssistant(host).also { voiceAssistant = it }
        val launchRecognizer: () -> Unit = launch@{
            if (!OptionalVoiceAssistant.isAvailable(voiceContext)) {
                lastReply = voiceContext.getString(R.string.voice_recognizer_unavailable)
                Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_recognizer_unavailable), Toast.LENGTH_LONG).show()
                return@launch
            }
            val assistant = ensureAssistant()
            assistant.stopSpeaking()
            listening = true
            try {
                speechRecognizerLauncher.launch(assistant.recognizerIntent(state.preferences.language))
            } catch (_: android.content.ActivityNotFoundException) {
                listening = false
                lastReply = voiceContext.getString(R.string.voice_recognizer_unavailable)
                Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_recognizer_unavailable), Toast.LENGTH_LONG).show()
            }
        }
        // Speech results are handled in one place: first try to read the phrase as a whole ledger
        // entry ("I bought 1 kg of tomatoes for 100 UAH"), and only fall back to the small set of
        // fixed commands (balance / spent today / open add-entry) when no amount could be found.
        val processInput: (String?) -> Unit = { heard ->
                listening = false
                val assistant = ensureAssistant()
                val language = state.preferences.language
                val query = heard?.let { FinanceQuery.parse(it) }
                val draft = if (query == null) heard?.let { VoiceEntryParser.parse(it) } else null
                when {
                    query == FinanceQuery.Balance -> {
                        val reply = voiceContext.getString(R.string.voice_reply_balance,
                            SpokenMoney.format(state.snapshot.balance.netMinor, state.preferences.currency, language))
                        lastReply = reply; assistant.speak(reply, language)
                    }
                    query is FinanceQuery.Statistics -> onStatistics(query) { amount ->
                        val period = voiceContext.getString(when (query.period) {
                            FinanceQuery.Period.TODAY -> R.string.period_today
                            FinanceQuery.Period.WEEK -> R.string.last_7_days
                            FinanceQuery.Period.MONTH -> R.string.period_month
                            FinanceQuery.Period.YEAR -> R.string.period_year
                            FinanceQuery.Period.ALL -> R.string.all_time
                        })
                        val category = query.category?.let { voiceContext.getString(it.label()) } ?: voiceContext.getString(R.string.all_categories)
                        val kind = voiceContext.getString(if (query.type == EntryType.EXPENSE) R.string.expenses else R.string.income)
                        val reply = voiceContext.getString(R.string.voice_statistics, kind, period, category,
                            SpokenMoney.format(amount, state.preferences.currency, language))
                        lastReply = reply; assistant.speak(reply, language)
                    }
                    draft != null -> {
                        val title = draft.title.ifBlank { voiceContext.getString(draft.category.label()) }
                        onVoiceEntry(draft.copy(title = title))
                        val typeLabel = voiceContext.getString(if (draft.type == EntryType.INCOME) R.string.income else R.string.expense)
                        val reply = voiceContext.getString(R.string.voice_entry_added, typeLabel,
                            formatMoney(draft.amountMinor, state.preferences.currency, language), title, voiceContext.getString(draft.category.label()))
                        lastReply = reply
                        assistant.speak(reply, language)
                    }
                    heard != null -> when (OptionalVoiceAssistant.classify(heard)) {
                        VoiceCommand.ShowBalance -> {
                            val reply = voiceContext.getString(R.string.voice_reply_balance,
                                SpokenMoney.format(state.snapshot.balance.netMinor, state.preferences.currency, language))
                            lastReply = reply
                            assistant.speak(reply, language)
                        }
                        VoiceCommand.ShowSpentToday -> {
                            val spentToday = state.snapshot.activity.find { it.date == LocalDate.now() }?.expenseMinor ?: 0L
                            val reply = voiceContext.getString(R.string.voice_reply_spent_today,
                                formatMoney(spentToday, state.preferences.currency, language))
                            lastReply = reply
                            assistant.speak(reply, language)
                        }
                        VoiceCommand.OpenAddEntry -> {
                            val reply = voiceContext.getString(R.string.voice_reply_opening_entry)
                            lastReply = reply
                            assistant.speak(reply, language)
                            onAdd()
                        }
                        null -> {
                            // Not one of the fixed commands either -- try small talk ("hi", "how
                            // are you", "thanks", ...) before giving up, so the cat feels like
                            // something you can actually talk with, not just issue commands to.
                            val reply = smallTalkReply(voiceContext, heard)
                                ?: voiceContext.getString(R.string.voice_reply_not_understood)
                            lastReply = reply
                            assistant.speak(reply, language)
                        }
                    }
                    else -> {
                        val reply = voiceContext.getString(R.string.voice_reply_not_understood)
                        lastReply = reply
                        assistant.speak(reply, language)
                    }
                }
            }
        SideEffect {
            setOnSpeechResult { data ->
                if (state.premium && state.preferences.premiumAssistantEnabled && data != null)
                    processInput(ensureAssistant().extractSpokenText(data))
                else listening = false
            }
            setOnMicPermissionResult { granted ->
                if (granted) launchRecognizer()
                else {
                    listening = false
                    Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_permission_denied), Toast.LENGTH_SHORT).show()
                }
            }
        }
        DisposableEffect(host) {
            onDispose { setOnSpeechResult {}; setOnMicPermissionResult {} }
        }
        val onVoice: () -> Unit = {
            if (!OptionalVoiceAssistant.isAvailable(voiceContext)) {
                Toast.makeText(voiceContext, voiceContext.getString(R.string.voice_recognizer_unavailable), Toast.LENGTH_LONG).show()
            } else if (ContextCompat.checkSelfPermission(voiceContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                launchRecognizer()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        Box(Modifier.fillMaxSize().trackCatTouch().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loading) LoadingState()
            else if (state.failed) EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.storage_error), stringResource(R.string.retry), onRetry)
            else LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().testTag("ledgerList"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item(key = "greeting", contentType = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (state.preferences.displayName.isBlank()) stringResource(R.string.dashboard_heading)
                            else stringResource(R.string.greeting, state.preferences.displayName), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.dashboard_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.premium && state.preferences.premiumAssistantEnabled) {
                    item(key = "assistant", contentType = "assistant") {
                        AssistantCharacterCard(state.preferences.language, listening, lastReply, onTalk = onVoice,
                            skin = state.preferences.mascotSkin, animations = state.preferences.mascotAnimations,
                            onText = { processInput(it) }, balanceMinor = state.snapshot.balance.netMinor)
                    }
                }
                item(key = "balance", contentType = "summary") { BalanceCard(state) }
                item(key = "totals", contentType = "summary") { BalanceBreakdown(state) }
                item(key = "chart", contentType = "chart") {
                    ActivityChart(state.snapshot.activity, state.preferences.currency, state.preferences.language, state.premium,
                        state.days, onDays, onPremium = { onNavigate(1) })
                }
                item(key = "categories", contentType = "summary") {
                    CategorySummary(state, onCategory = { category, type -> categoryDetailType = type; onCategory(category) },
                        onMore = { onNavigate(3) })
                }
                item(key = "search", contentType = "filter") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.activity), style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(value = state.query, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().testTag("searchEntries"),
                            singleLine = true, label = { Text(stringResource(R.string.search_entries)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, stringResource(R.string.clear_search)) } })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = state.type == null, onClick = { onFilter(null) }, label = { Text(stringResource(R.string.all)) })
                            FilterChip(colors = FilterChipDefaults.filterChipColors(labelColor = MaterialTheme.colorScheme.secondary, selectedLabelColor = MaterialTheme.colorScheme.secondary, selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .18f)), selected = state.type == EntryType.INCOME, onClick = { onFilter(EntryType.INCOME) }, label = { Text(stringResource(R.string.income)) })
                            FilterChip(colors = FilterChipDefaults.filterChipColors(labelColor = MaterialTheme.colorScheme.tertiary, selectedLabelColor = MaterialTheme.colorScheme.tertiary, selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .18f)), selected = state.type == EntryType.EXPENSE, onClick = { onFilter(EntryType.EXPENSE) }, label = { Text(stringResource(R.string.expenses)) })
                        }
                        Text(stringResource(R.string.search_scope), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (state.snapshot.entries.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        val filtered = state.query.isNotBlank() || state.type != null
                        ContentCard {
                            EmptyState(stringResource(if (filtered) R.string.no_matches else R.string.empty_ledger_title),
                                stringResource(if (filtered) R.string.no_matches_body else R.string.empty_ledger_body),
                                stringResource(if (filtered) R.string.clear_filters else R.string.add_first_entry),
                                onAction = { if (filtered) { onSearch(""); onFilter(null) } else onAdd() })
                        }
                    }
                }
                items(state.snapshot.entries, key = { it.id }, contentType = { "entry" }) { entry ->
                    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) onDelete(entry.id)
                        false // Room owns removal; never leave an off-screen row after a failed delete.
                    })
                    SwipeToDismissBox(state = dismissState, enableDismissFromStartToEnd = false,
                        modifier = Modifier.animateItem(), backgroundContent = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium).padding(24.dp),
                                contentAlignment = Alignment.CenterEnd) {
                                Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_entry), tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }) {
                        EntryRow(entry, state.preferences.language, { onEdit(entry.id) }, { onDelete(entry.id) })
                    }
                }
                if (state.canLoadMore) item(key = "more", contentType = "action") {
                    OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) }
                }
            }
        }
    }
}

// Turns a small-talk topic into the actual reply text, localized to the app's current EN/UK
// language via string resources (VoiceSmallTalk itself only recognizes the topic, it never
// produces user-facing text). Returns null when the phrase was not small talk at all, so the
// caller can fall back to "I didn't understand that".
private fun smallTalkReply(context: android.content.Context, heard: String): String? =
    when (VoiceSmallTalk.classify(heard)) {
        VoiceSmallTalk.Topic.GREETING -> context.getString(R.string.voice_smalltalk_greeting)
        VoiceSmallTalk.Topic.HOW_ARE_YOU -> context.getString(R.string.voice_smalltalk_how_are_you)
        VoiceSmallTalk.Topic.THANKS -> context.getString(R.string.voice_smalltalk_thanks)
        VoiceSmallTalk.Topic.WHO_ARE_YOU -> context.getString(R.string.voice_smalltalk_who_are_you)
        VoiceSmallTalk.Topic.BYE -> context.getString(R.string.voice_smalltalk_bye)
        VoiceSmallTalk.Topic.COMPLIMENT -> context.getString(R.string.voice_smalltalk_compliment)
        VoiceSmallTalk.Topic.JOKE -> context.getString(R.string.voice_smalltalk_joke)
        null -> null
    }

@Composable
private fun BalanceCard(state: DashboardState) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.total_balance), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Default.AccountBalanceWallet, null, tint = LocalAppearance.current.color("icons"))
            }
            Text(formatMoney(state.snapshot.balance.netMinor, state.preferences.currency, state.preferences.language),
                style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("totalBalance"))
            Text(stringResource(R.string.currency_scope, state.preferences.currency.name), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BalanceBreakdown(state: DashboardState) {
    val language = state.preferences.language
    val currency = state.preferences.currency
    @Composable fun Metric(income: Boolean, modifier: Modifier) {
        ContentCard(modifier) {
            Text(stringResource(if (income) R.string.income else R.string.expenses), style = MaterialTheme.typography.bodyMedium,
                color = if (income) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
            Text(formatMoney(if (income) state.snapshot.balance.incomeMinor else state.snapshot.balance.expenseMinor, currency, language),
                style = MaterialTheme.typography.titleMedium, color = if (income) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
        }
    }
    BoxWithConstraints {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { Metric(true, Modifier); Metric(false, Modifier) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(true, Modifier.weight(1f)); Metric(false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry, language: AppLanguage, onEdit: () -> Unit, onDelete: () -> Unit) {
    val categoryTint = LocalAppearance.current.category(entry.category, entry.type == EntryType.INCOME)
    OutlinedCard(Modifier.fillMaxWidth().testTag("entry:${entry.id}")) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.small, color = categoryTint.copy(alpha = .16f)) {
                    Icon(entry.category.icon(), null, Modifier.padding(10.dp).size(22.dp), tint = categoryTint)
                }
                Column(Modifier.weight(1f)) {
                    Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(entry.category.label()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_named_entry, entry.title)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(entry.date, language), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(if (entry.type == EntryType.INCOME) R.string.positive_amount else R.string.negative_amount,
                    formatMoney(entry.amountMinor, entry.currency, language)), Modifier.weight(1.2f), textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold, color = if (entry.type == EntryType.INCOME) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/dashboard/DashboardViewModel.kt

```kotlin
package com.semmyx.ledger.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.UiMessage
import com.semmyx.ledger.presentation.common.guarded
import com.semmyx.ledger.voice.VoiceEntryDraft
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

data class DashboardState(
    val preferences: UserPreferences = UserPreferences(),
    val snapshot: DashboardSnapshot = DashboardSnapshot(),
    val query: String = "",
    val type: EntryType? = null,
    val days: Int = 7,
    val premium: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val canLoadMore: Boolean = false,
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val selectedCategory: Category? = null,
    val categoryEntries: List<LedgerEntry> = emptyList(),
    val categoryCanLoadMore: Boolean = false,
)
sealed interface DashboardEvent {
    data class Deleted(val token: DeletionToken) : DashboardEvent
    data class Message(val message: UiMessage) : DashboardEvent
}
private data class QuerySpec(val query: String = "", val type: EntryType? = null, val limit: Int = 50, val days: Int = 7, val retry: Int = 0, val category: Category? = null, val categoryLimit: Int = 50)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val entries: EntryRepository,
    preferences: PreferencesRepository,
    premium: PremiumRepository,
    private val observe: ObserveDashboard,
    private val clock: Clock,
    private val saveEntry: SaveEntry,
) : ViewModel() {
    private val spec = MutableStateFlow(QuerySpec())
    private val eventChannel = Channel<DashboardEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    // Re-evaluate the window across local midnight without restarting the app.
    private val today = flow {
        while (true) {
            emit(LocalDate.now(clock))
            val next = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
            delay((next - clock.millis()).coerceAtLeast(1000))
        }
    }.distinctUntilChanged()
    val state = combine(preferences.preferences, premium.state, spec, today) { prefs, access, query, date ->
        DashboardRequest(prefs, access.entitled, query, date)
    }.flatMapLatest { request ->
        val window = if (request.premium) request.spec.days else 7
        val filter = EntryFilter(request.prefs.currency, request.spec.query, request.spec.type, request.spec.limit + 1)
        val from = FinanceWindow.from(window, request.today)
        val categoryRows = request.spec.category?.let { category ->
            entries.observeEntries(EntryFilter(request.prefs.currency, limit = request.spec.categoryLimit + 1,
                category = category, from = from, through = request.today))
        } ?: flowOf(emptyList<LedgerEntry>())
        combine(observe(filter, from, request.today), entries.observeCategoryTotals(request.prefs.currency, from, request.today), categoryRows) { snapshot, totals, rows ->
                DashboardState(request.prefs, snapshot.copy(entries = snapshot.entries.take(request.spec.limit)),
                    request.spec.query, request.spec.type, window, request.premium, loading = false,
                    canLoadMore = snapshot.entries.size > request.spec.limit, categoryTotals = totals,
                    selectedCategory = request.spec.category, categoryEntries = rows.take(request.spec.categoryLimit),
                    categoryCanLoadMore = rows.size > request.spec.categoryLimit)
            }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(DashboardState(preferences = request.prefs, query = request.spec.query, type = request.spec.type,
                    days = window, premium = request.premium, loading = false, failed = true))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun search(query: String) { spec.update { it.copy(query = query.take(120), limit = 50) } }
    fun filter(type: EntryType?) { spec.update { it.copy(type = type, limit = 50) } }
    fun days(value: Int) { if (value in listOf(0, 7, 30)) spec.update { it.copy(days = value, categoryLimit = 50) } }
    fun loadMore() { spec.update { it.copy(limit = if (it.limit < Int.MAX_VALUE - 51) it.limit + 50 else Int.MAX_VALUE - 1) } }
    fun category(value: Category?) { spec.update { it.copy(category = value, categoryLimit = 50) } }
    fun loadMoreCategory() { spec.update { it.copy(categoryLimit = if (it.categoryLimit < Int.MAX_VALUE - 51) it.categoryLimit + 50 else Int.MAX_VALUE - 1) } }
    fun retry() { spec.update { it.copy(retry = it.retry + 1) } }
    fun delete(id: String) {
        guarded(onError = { eventChannel.trySend(DashboardEvent.Message(UiMessage.STORAGE)) }) {
            entries.delete(id)?.let { eventChannel.send(DashboardEvent.Deleted(it)) }
        }
    }
    fun undo(token: DeletionToken) {
        guarded(onError = { eventChannel.trySend(DashboardEvent.Message(UiMessage.STORAGE)) }) {
            if (!entries.undo(token)) eventChannel.send(DashboardEvent.Message(UiMessage.UNDO_EXPIRED))
        }
    }
    // Saves an entry parsed from a free-form spoken sentence (see VoiceEntryParser). The current
    // display currency is attached here rather than in the parser, since the parser has no access
    // to user preferences and money spoken aloud is always assumed to be in the user's own currency.
    fun addVoiceEntry(draft: VoiceEntryDraft) {
        guarded(onError = { eventChannel.trySend(DashboardEvent.Message(UiMessage.STORAGE)) }) {
            saveEntry(LedgerEntry(
                id = UUID.randomUUID().toString(),
                title = draft.title,
                amountMinor = draft.amountMinor,
                currency = state.value.preferences.currency,
                type = draft.type,
                category = draft.category,
                date = LocalDate.now(clock),
                note = draft.note,
            ))
        }
    }
    suspend fun answerStatistics(request: com.semmyx.ledger.voice.FinanceQuery.Statistics): Long {
        val now = LocalDate.now(clock)
        return entries.statistics(state.value.preferences.currency, request.from(now), now, request.type, request.category)
    }
    private data class DashboardRequest(val prefs: UserPreferences, val premium: Boolean, val spec: QuerySpec, val today: LocalDate)
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/editor/EntryScreen.kt

```kotlin
package com.semmyx.ledger.presentation.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryRoute(model: EntryViewModel, language: AppLanguage, onBack: () -> Unit, customCategories: List<com.semmyx.ledger.domain.bank.CustomBankCategory> = emptyList()) {
    val state by model.state.collectAsStateWithLifecycle()
    var dateDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var initial by remember { mutableStateOf<EditorState?>(null) }
    LaunchedEffect(state.loading) { if (!state.loading && initial == null) initial = state }
    LaunchedEffect(model) { model.saved.collect { onBack() } }
    fun changed(): Boolean = initial?.let { old ->
        old.title != state.title || old.amount != state.amount || old.type != state.type || old.category != state.category || old.date != state.date || old.note != state.note || old.sourceType != state.sourceType || old.bankId != state.bankId || old.customCategoryId != state.customCategoryId
    } ?: false
    fun attemptBack() { if (!state.busy) { if (changed()) discardDialog = true else onBack() } }
    BackHandler { attemptBack() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(if (state.editing) R.string.edit_entry else R.string.add_entry)) }, navigationIcon = {
        IconButton(onClick = ::attemptBack, enabled = !state.busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }) }) { padding ->
        if (state.loading) LoadingState(Modifier.padding(padding))
        else if (state.loadFailed) EmptyState(stringResource(R.string.load_error_title), stringResource(state.error?.label() ?: R.string.storage_error),
            stringResource(R.string.back), onBack, Modifier.padding(padding))
        else Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.entry_currency_notice, state.currency.name), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.tertiary), selected = state.type == EntryType.EXPENSE, onClick = { model.type(EntryType.EXPENSE) }, enabled = !state.busy,
                    label = { Text(stringResource(R.string.expense)) })
                FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.secondary), selected = state.type == EntryType.INCOME, onClick = { model.type(EntryType.INCOME) }, enabled = !state.busy,
                    label = { Text(stringResource(R.string.income)) })
            }
            OutlinedTextField(value = state.amount, onValueChange = model::amount, label = { Text(stringResource(R.string.amount)) },
                suffix = { Text(state.currency.name) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = state.validation == ValidationIssue.AMOUNT, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("entryAmount"),
                supportingText = { if (state.validation == ValidationIssue.AMOUNT) Text(stringResource(R.string.amount_invalid)) })
            OutlinedTextField(value = state.title, onValueChange = model::title, label = { Text(stringResource(R.string.entry_title)) }, singleLine = true,
                isError = state.validation == ValidationIssue.TITLE, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("entryTitle"),
                supportingText = { Text(stringResource(if (state.validation == ValidationIssue.TITLE) R.string.title_invalid else R.string.title_hint)) })
            Text(stringResource(R.string.bank_source), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.sourceType == "CASH", onClick = { model.source("CASH") }, label = { Text(stringResource(R.string.bank_cash)) })
                FilterChip(selected = state.sourceType == "CARD", onClick = { model.source("CARD") }, label = { Text(stringResource(R.string.bank_card)) })
            }
            if (state.sourceType == "CARD") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.semmyx.ledger.domain.bank.Banks.all.forEach { bank ->
                        FilterChip(selected = state.bankId == bank.id, onClick = { model.bank(bank.id) }, label = { Text(bank.name) })
                    }
                }
            }
            Text(stringResource(R.string.category), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAppearance.current.category(category, state.type == EntryType.INCOME).copy(alpha = .18f), selectedLabelColor = LocalAppearance.current.category(category, state.type == EntryType.INCOME)), selected = state.customCategoryId == null && state.category == category, onClick = { model.category(category) }, enabled = !state.busy,
                        label = { Text(stringResource(category.label())) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                customCategories.forEach { category ->
                    FilterChip(selected = state.customCategoryId == category.id, onClick = { model.customCategory(category.id) },
                        label = { Text(category.name) })
                }
            }
            OutlinedButton(onClick = { dateDialog = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Default.CalendarMonth, null)
                Spacer(Modifier.width(12.dp))
                Text(formatDate(state.date, language))
            }
            if (state.validation == ValidationIssue.DATE) Text(stringResource(R.string.date_invalid), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(value = state.note, onValueChange = model::note, label = { Text(stringResource(R.string.note_optional)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !state.busy,
                supportingText = { Text(stringResource(R.string.character_count, state.note.length, 240)) })
            state.error?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
            Button(onClick = model::save, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("saveEntry")) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.save_entry))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (discardDialog) AlertDialog(onDismissRequest = { discardDialog = false }, title = { Text(stringResource(R.string.discard_title)) },
        text = { Text(stringResource(R.string.discard_body)) },
        confirmButton = { TextButton(onClick = { discardDialog = false; onBack() }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { discardDialog = false }) { Text(stringResource(R.string.keep_editing)) } })
    if (dateDialog) {
        val today = LocalDate.now()
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 2000..today.year, selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val value = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return value.year >= 2000 && !value.isAfter(today)
                }
                override fun isSelectableYear(year: Int): Boolean = year in 2000..today.year
            })
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { model.date(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                dateDialog = false
            }) { Text(stringResource(R.string.done)) }
        }, dismissButton = { TextButton(onClick = { dateDialog = false }) { Text(stringResource(R.string.cancel)) } }) {
            DatePicker(state = picker)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/editor/EntryViewModel.kt

```kotlin
package com.semmyx.ledger.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.UiMessage
import com.semmyx.ledger.presentation.common.guarded
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EditorState(
    val editing: Boolean,
    val loading: Boolean = true,
    val title: String = "",
    val amount: String = "",
    val currency: LedgerCurrency = LedgerCurrency.UAH,
    val type: EntryType = EntryType.EXPENSE,
    val category: Category = Category.OTHER,
    val date: LocalDate,
    val note: String = "",
    val busy: Boolean = false,
    val validation: ValidationIssue? = null,
    val error: UiMessage? = null,
    val loadFailed: Boolean = false,
    val sourceType: String = "CASH",
    val bankId: String? = null,
    val cardLast4: String? = null,
    val customCategoryId: String? = null,
    val eventAtMillis: Long? = null,
    val origin: String = "MANUAL",
)

class EntryViewModel(
    entryId: String?,
    private val repository: EntryRepository,
    preferences: PreferencesRepository,
    private val saveEntry: SaveEntry,
    private val clock: Clock,
) : ViewModel() {
    private val id = entryId ?: UUID.randomUUID().toString()
    private val mutableState = MutableStateFlow(EditorState(editing = entryId != null, date = LocalDate.now(clock)))
    val state = mutableState.asStateFlow()
    private val savedChannel = Channel<Unit>(Channel.BUFFERED)
    val saved = savedChannel.receiveAsFlow()
    init {
        guarded(onError = { mutableState.update { it.copy(loading = false, error = UiMessage.STORAGE, loadFailed = true) } }) {
            if (entryId == null) {
                val prefs = preferences.preferences.first()
                mutableState.update { it.copy(currency = prefs.currency, loading = false) }
            } else {
                val entry = repository.find(entryId)
                mutableState.value = if (entry == null) state.value.copy(loading = false, error = UiMessage.NOT_FOUND, loadFailed = true)
                else EditorState(true, false, entry.title, Money.editable(entry.amountMinor), entry.currency,
                    entry.type, entry.category, entry.date, entry.note, sourceType = entry.sourceType, bankId = entry.bankId,
                    cardLast4 = entry.cardLast4, customCategoryId = entry.customCategoryId, eventAtMillis = entry.eventAtMillis, origin = entry.origin)
            }
        }
    }
    fun title(value: String) { mutableState.update { it.copy(title = value.take(60), validation = null) } }
    fun amount(value: String) { mutableState.update { it.copy(amount = value.take(12), validation = null) } }
    fun type(value: EntryType) { mutableState.update { it.copy(type = value) } }
    fun category(value: Category) { mutableState.update { it.copy(category = value, customCategoryId = null) } }
    fun customCategory(id: String) { mutableState.update { it.copy(category = Category.OTHER, customCategoryId = id) } }
    fun source(value: String) { require(value in listOf("UNKNOWN", "CASH", "CARD")); mutableState.update { it.copy(sourceType = value, bankId = if (value == "CARD") it.bankId ?: "privat" else null, cardLast4 = if (value == "CARD") it.cardLast4 else null) } }
    fun bank(id: String) { mutableState.update { it.copy(bankId = id, sourceType = "CARD") } }
    fun date(value: LocalDate) { mutableState.update { it.copy(date = value, eventAtMillis = value.atStartOfDay(clock.zone).toInstant().toEpochMilli(), validation = null) } }
    fun note(value: String) { mutableState.update { it.copy(note = value.take(240), validation = null) } }
    fun save() {
        if (state.value.busy || state.value.loading || state.value.loadFailed) return
        val form = state.value
        val minor = Money.parseMinor(form.amount)
        if (minor == null) { mutableState.update { it.copy(validation = ValidationIssue.AMOUNT) }; return }
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                saveEntry(LedgerEntry(id, form.title, minor, form.currency, form.type, form.category, form.date, form.note, form.sourceType, form.bankId, form.cardLast4,
                    form.eventAtMillis ?: if (form.date == LocalDate.now(clock)) clock.millis() else form.date.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                    form.customCategoryId, form.origin))
                savedChannel.send(Unit)
            } catch (error: EntryValidationException) {
                mutableState.update { it.copy(validation = error.issue) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(error = UiMessage.STORAGE) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/history/HistoryScreen.kt

```kotlin
package com.semmyx.ledger.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.EntryType
import com.semmyx.ledger.domain.bank.Banks
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.theme.LocalAppearance

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(model: HistoryViewModel, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val palette = LocalAppearance.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.cinematic_history)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(model.month?.toString() ?: stringResource(R.string.all_time)) }
            item { OutlinedTextField(state.request.query, model::search, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.history_search)) }) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.request.type == null, onClick = { model.type(null) }, label = { Text(stringResource(R.string.all)) })
                EntryType.entries.forEach { type ->
                    FilterChip(selected = state.request.type == type, onClick = { model.type(type) },
                        colors = FilterChipDefaults.filterChipColors(labelColor = palette.type(type), selectedLabelColor = palette.type(type)),
                        label = { Text(stringResource(if (type == EntryType.INCOME) R.string.income else R.string.expenses)) })
                }
            } }
            if (state.loading) item { LoadingState() }
            else if (state.failed) item { Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error) }
            else if (state.rows.isEmpty()) item { Text(stringResource(R.string.category_empty)) }
            items(state.rows, key = { it.id }) { entry -> ContentCard(Modifier.clickable { onEdit(entry.id) }) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(formatMoney(if (entry.type == EntryType.EXPENSE) -entry.amountMinor else entry.amountMinor, entry.currency, state.preferences.language), color = palette.type(entry.type))
                Text(formatDate(entry.date, state.preferences.language))
                Text(when (entry.sourceType) { "CASH" -> stringResource(R.string.bank_cash); "CARD" -> Banks.name(entry.bankId); else -> stringResource(R.string.bank_unknown) })
            } }
            if (state.hasMore) item { OutlinedButton(onClick = model::more, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_more)) } }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/history/HistoryViewModel.kt

```kotlin
package com.semmyx.ledger.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import java.time.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class HistoryRequest(val query: String = "", val type: EntryType? = null, val limit: Int = 50)
data class HistoryState(val preferences: UserPreferences = UserPreferences(), val request: HistoryRequest = HistoryRequest(),
    val rows: List<LedgerEntry> = emptyList(), val hasMore: Boolean = false, val failed: Boolean = false, val loading: Boolean = true)
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(repository: EntryRepository, preferences: PreferencesRepository, clock: Clock, val month: YearMonth?) : ViewModel() {
    private val request = MutableStateFlow(HistoryRequest())
    val state = combine(preferences.preferences, request) { prefs, input -> prefs to input }.flatMapLatest { (prefs, input) ->
        val today = LocalDate.now(clock)
        val from = month?.atDay(1) ?: LocalDate.of(2000, 1, 1)
        val through = month?.atEndOfMonth()?.let { if (it < today) it else today } ?: today
        repository.observeEntries(EntryFilter(prefs.currency, input.query, input.type, input.limit + 1, from = from, through = through))
            .map { rows -> HistoryState(prefs, input, rows.take(input.limit), rows.size > input.limit, loading = false) }
            .catch { emit(HistoryState(prefs, input, failed = true, loading = false)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())
    fun search(value: String) { request.update { it.copy(query = value.take(80), limit = 50) } }
    fun type(value: EntryType?) { request.update { it.copy(type = value, limit = 50) } }
    fun more() { request.update { it.copy(limit = (it.limit + 50).coerceAtMost(100000)) } }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/onboarding/OnboardingScreen.kt

```kotlin
package com.semmyx.ledger.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.presentation.common.LedgerMark
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val titles = listOf(R.string.brand_intro_title, R.string.onboard_title_one, R.string.onboard_title_two, R.string.onboard_title_three)
    val bodies = listOf(R.string.brand_intro_body, R.string.onboard_body_one, R.string.onboard_body_two, R.string.onboard_body_three)
    val icons = listOf(Icons.Default.AutoAwesome, Icons.Default.BarChart, Icons.Default.Lock, Icons.Default.CloudOff)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LedgerMark(Modifier.size(28.dp))
            Text(stringResource(R.string.app_name), Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onComplete) { Text(stringResource(R.string.skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(Modifier.size(168.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        if (page == 0) Image(painterResource(R.drawable.brand_sky), null, Modifier.fillMaxSize())
                        else Icon(icons[page], null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(stringResource(titles[page]), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(bodies[page]), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.brand_author_signature), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.brand_design_label), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.brand_copyright), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.page_of, pager.currentPage + 1, 4), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { index ->
                val color by animateColorAsState(if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "pagerIndicator")
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            }
        }
        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
            if (pager.currentPage == 3) onComplete() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }) { Text(stringResource(if (pager.currentPage == 3) R.string.get_started else R.string.next)) }
        Spacer(Modifier.height(8.dp))
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/premium/CatFaceMesh.kt

```kotlin
package com.semmyx.ledger.presentation.premium

import kotlin.math.*

/** One texture, continuously deformed vertices. No eye-open/closed resource switching. */
class CatFaceMesh(val columns: Int = 80, val rows: Int = 80) {
    val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val xs = FloatArray(vertices.size / 2)
    private val ys = FloatArray(xs.size)
    private val head = FloatArray(xs.size)
    private val depth = FloatArray(xs.size)
    private val lids = FloatArray(xs.size)
    private val body = FloatArray(xs.size)
    init {
        var i = 0
        for (row in 0..rows) for (column in 0..columns) {
            val x = column.toFloat() / columns; val y = row.toFloat() / rows
            xs[i] = x; ys[i] = y
            head[i] = exp(-(((x - .5f) / .35f).toDouble().pow(4) + ((y - .37f) / .30f).toDouble().pow(4))).toFloat()
            depth[i] = .10f * exp(-(((x - .5f) / .26f).toDouble().pow(2) + ((y - .42f) / .25f).toDouble().pow(2))).toFloat()
            // Actual eye landmarks measured on cat_amber_base.webp, in normalized texture coordinates.
            val eyeY = .396f
            val left = exp(-(((x - .404f) / .054f).toDouble().pow(4) + ((y - eyeY) / .042f).toDouble().pow(4))).toFloat()
            val right = exp(-(((x - .611f) / .054f).toDouble().pow(4) + ((y - eyeY) / .042f).toDouble().pow(4))).toFloat()
            lids[i] = -(y - eyeY) * maxOf(left, right) * .98f
            body[i] = exp(-(((x - .5f) / .38f).toDouble().pow(4) + ((y - .72f) / .25f).toDouble().pow(4))).toFloat()
            i++
        }
    }
    fun update(pose: CatPose, textureWidth: Int, textureHeight: Int): FloatArray {
        val yaw = pose.yawDegrees.coerceIn(-18f, 18f) * PI.toFloat() / 180f
        val pitch = pose.pitchDegrees.coerceIn(-10f, 10f) * PI.toFloat() / 180f
        val cosine = cos(yaw); val sine = sin(yaw)
        val blink = pose.blink.coerceIn(0f, 1f)
        val expression = (pose.pleased * .045f).coerceIn(0f, .045f)
        for (i in xs.indices) {
            val x = xs[i]; val y = ys[i]
            val h = head[i]
            val dx = h * ((x - .5f) * (cosine - 1) + depth[i] * sine)
            val dy = h * depth[i] * sin(pitch)
            // Upper/lower fur regions move together around the iris. The eye aperture closes continuously.
            val lid = lids[i] * (blink + expression * (1 - blink))
            val breathing = pose.breath * .010f * body[i]
            vertices[i * 2] = (x + dx + (x - .5f) * breathing) * textureWidth
            vertices[i * 2 + 1] = (y + dy + lid - breathing * .25f) * textureHeight
        }
        return vertices
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/premium/LiveCatMotion.kt

```kotlin
package com.semmyx.ledger.presentation.premium

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.semmyx.ledger.R
import kotlinx.coroutines.isActive
import kotlin.math.*

/** Engine-neutral, stable rig channels. Realistic artwork is supplied separately, never fabricated. */
data class CatPose(val breath: Float = 0f, val blink: Float = 0f, val yawDegrees: Float = 0f,
    val pitchDegrees: Float = 0f, val pleased: Float = 0f, val serious: Float = 0f, val listening: Boolean = false)

interface CatRig {
    fun morph(name: String, weight: Float)
    fun head(yawDegrees: Float, pitchDegrees: Float)
}

/** Complete mapping from controller output to a rig; a Filament adapter implements CatRig. */
class CatRigDriver(private val rig: CatRig) {
    fun apply(pose: CatPose) {
        rig.morph("breath", pose.breath)
        rig.morph("blinkLeft", pose.blink)
        rig.morph("blinkRight", pose.blink)
        rig.morph("pleased", pose.pleased)
        rig.morph("serious", pose.serious)
        rig.morph("earsForward", if (pose.listening) 1f else .2f)
        rig.head(pose.yawDegrees, pose.pitchDegrees)
    }
}

class CatMotionController {
    private var yaw = 0f
    private var pitch = 0f
    private var happy = .5f
    private var lastTime = 0.0
    fun step(seconds: Double, balanceMinor: Long, listening: Boolean, target: Offset): CatPose {
        val dt = if (lastTime == 0.0) 1.0 / 120 else (seconds - lastTime).coerceIn(0.0, .1)
        lastTime = seconds
        val smooth = (1.0 - exp(-9.0 * dt)).toFloat()
        yaw += (target.x.coerceIn(-1f, 1f) * 18f - yaw) * smooth
        pitch += (target.y.coerceIn(-1f, 1f) * -10f - pitch) * smooth
        val destination = if (balanceMinor < 0) 0f else if (balanceMinor > 0) 1f else .5f
        happy += (destination - happy) * smooth
        val cycle = seconds % 4.7
        val blink = if (cycle < .22) sin(cycle / .22 * PI).toFloat().coerceIn(0f, 1f) else 0f
        return CatPose(((sin(seconds * 2 * PI / 3.6) + 1) / 2).toFloat(), blink, yaw, pitch,
            happy, 1f - happy, listening)
    }
}

/** Install a licensed asset-backed implementation at the composition root to enable real 3D. */
fun interface CatAssetRenderer {
    @Composable fun Render(pose: CatPose, modifier: Modifier)
}
object CatAssetRegistry {
    var renderer: CatAssetRenderer? by mutableStateOf(null)
}
object CatTouchBus {
    var target by mutableStateOf(Offset.Zero)
}

/** Observe, don't consume: ordinary taps, scrolling, editing and speech buttons still work. */
fun Modifier.trackCatTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull { it.pressed }?.let { change ->
                CatTouchBus.target = Offset((change.position.x / size.width.coerceAtLeast(1) - .5f) * 2,
                    (change.position.y / size.height.coerceAtLeast(1) - .5f) * 2)
            }
        }
    }
}

@Composable
fun LiveCatHost(balanceMinor: Long, listening: Boolean, skin: String, modifier: Modifier, motionEnabled: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { CatMotionController() }
    val balance by rememberUpdatedState(balanceMinor)
    val hears by rememberUpdatedState(listening)
    var pose by remember { mutableStateOf(CatPose()) }
    LaunchedEffect(lifecycle, controller, motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) withFrameNanos { time ->
                pose = controller.step(time / 1e9, balance, hears, CatTouchBus.target)
            }
        }
    }
    val renderer = CatAssetRegistry.renderer
    if (renderer != null) renderer.Render(pose, modifier)
    else MeshCatRenderer(if (motionEnabled) pose else CatPose(pleased = if (balanceMinor > 0) 1f else 0f), modifier, skin = skin)
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/premium/MeshCatRenderer.kt

```kotlin
package com.semmyx.ledger.presentation.premium

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.semmyx.ledger.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single decoded bitmap + a real-time mesh. Blink/yaw never change the resource. */
@Composable
fun MeshCatRenderer(pose: CatPose, modifier: Modifier, skin: String = "gray") {
    val app = LocalContext.current.applicationContext
    // Decode each appearance once. Frame updates and blinking only deform the shared mesh.
    val textures by produceState<Pair<Bitmap?, Bitmap?>?>(initialValue = null, key1 = app) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeResource(app.resources, R.drawable.cat_amber_base) to
                BitmapFactory.decodeResource(app.resources, R.drawable.cat_amber_sky)
        }
    }
    val bitmap = if (skin == "sky") textures?.second ?: textures?.first else textures?.first
    val mesh = remember { CatFaceMesh() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG) }
    Canvas(modifier) {
        val image = bitmap ?: return@Canvas
        val scale = minOf(size.width / image.width, size.height / image.height)
        val left = (size.width - image.width * scale) / 2f
        val top = (size.height - image.height * scale) / 2f
        val verts = mesh.update(pose, image.width, image.height)
        drawIntoCanvas { wrapper ->
            val native = wrapper.nativeCanvas
            val saved = native.save()
            native.translate(left, top)
            native.scale(scale, scale)
            native.drawBitmapMesh(image, mesh.columns, mesh.rows, verts, 0, null, 0, paint)
            native.restoreToCount(saved)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/premium/PremiumMascotAssistant.kt

```kotlin
package com.semmyx.ledger.presentation.premium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.AppLanguage

@Composable
fun AssistantCharacterCard(language: AppLanguage, listening: Boolean, lastReply: String?, onTalk: () -> Unit,
    modifier: Modifier = Modifier, skin: String = "gray", animations: Boolean = true,
    onText: (String) -> Unit = {}, balanceMinor: Long = 0) {
    var text by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val label = stringResource(R.string.voice_assistant_button)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.mascot_card_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            LiveCatHost(balanceMinor, listening, skin, Modifier.fillMaxWidth().height(224.dp)
                .clickable(enabled = !listening, onClickLabel = label, role = Role.Button, onClick = onTalk)
                .semantics { contentDescription = label }.testTag("voiceAssistantButton"), motionEnabled = animations)
            Text(if (listening) stringResource(R.string.voice_assistant_listening) else lastReply ?: stringResource(R.string.cat_tap_hint),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { typing = !typing }) { Text(stringResource(R.string.assistant_text_hint)) }
            AnimatedVisibility(typing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    OutlinedTextField(text, { text = it.take(500) }, modifier = Modifier.fillMaxWidth().testTag("assistantText"),
                        label = { Text(stringResource(R.string.assistant_text_hint)) }, singleLine = true)
                    TextButton(enabled = text.isNotBlank() && !listening, onClick = { val value = text; text = ""; onText(value) }) {
                        Text(stringResource(R.string.assistant_send))
                    }
                }
            }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/settings/ColorSettings.kt

```kotlin
package com.semmyx.ledger.presentation.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import com.semmyx.ledger.presentation.theme.LocalAppearance
import com.semmyx.ledger.presentation.theme.readableOn
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSettings(preferences: UserPreferences, onColor: (String, String?) -> Unit, onReset: () -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(true) }
    var type by remember { mutableStateOf(EntryType.EXPENSE) }
    val palette = LocalAppearance.current
    ContentCard {
        Text(stringResource(R.string.colors_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.colors_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.colors_collapse else R.string.colors_edit))
        }
        if (expanded) {
            ColorPreferences.roles.keys.forEach { role ->
                ColorSettingRow(stringResource(roleLabel(role)), palette.color(role), palette.hex(role)) { editing = role }
            }
            HorizontalDivider()
            Text(stringResource(R.string.colors_categories), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntryType.entries.forEach { choice ->
                    val tint = palette.type(choice)
                    FilterChip(selected = choice == type, onClick = { type = choice },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = tint.copy(alpha = .17f), selectedLabelColor = tint),
                        label = { Text(stringResource(if (choice == EntryType.INCOME) R.string.income else R.string.expenses)) })
                }
            }
            Category.entries.forEach { category ->
                val key = ColorPreferences.categoryKey(category, type)
                ColorSettingRow(stringResource(category.label()), palette.color(key), palette.hex(key)) { editing = key }
            }
            TextButton(onClick = onReset) { Text(stringResource(R.string.colors_reset_all)) }
        }
    }
    editing?.let { key ->
        val label = if (key in ColorPreferences.roles) stringResource(roleLabel(key)) else
            stringResource(Category.entries.first { key.endsWith(":" + it.name) }.label())
        ColorPicker(label, palette.hex(key),
            onDismiss = { editing = null }, onApply = { value -> onColor(key, value); editing = null },
            onReset = { onColor(key, null); editing = null })
    }
}

@Composable
private fun ColorSettingRow(label: String, color: Color, hex: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(color, CircleShape))
        Text(label, Modifier.weight(1f))
        Text(hex, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(title: String, initialHex: String, onDismiss: () -> Unit, onApply: (String) -> Unit, onReset: () -> Unit) {
    val hsv = remember(initialHex) { FloatArray(3).also { AndroidColor.colorToHSV(AndroidColor.parseColor(initialHex), it) } }
    var hue by remember(initialHex) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(initialHex) { mutableFloatStateOf(hsv[1]) }
    var brightness by remember(initialHex) { mutableFloatStateOf(hsv[2]) }
    var hex by remember(initialHex) { mutableStateOf(initialHex) }
    val valid = ColorPreferences.normalizeHex(hex)
    val preview = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    fun updateHex() { hex = String.format(Locale.ROOT, "#%06X", AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF) }
    fun loadHex(value: String) {
        hex = value
        ColorPreferences.normalizeHex(value)?.let { normalized ->
            val values = FloatArray(3)
            AndroidColor.colorToHSV(AndroidColor.parseColor(normalized), values)
            hue = values[0]; saturation = values[1]; brightness = values[2]
        }
    }
    val background = MaterialTheme.colorScheme.background
    val contrast = (maxOf(preview.luminance(), background.luminance()) + .05f) /
        (minOf(preview.luminance(), background.luminance()) + .05f)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = preview, contentColor = readableOn(preview), shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) { Text(valid ?: hex) }
                }
                OutlinedTextField(value = hex, onValueChange = { if (it.length <= 7) loadHex(it) },
                    label = { Text("HEX · #RRGGBB") }, singleLine = true, isError = valid == null, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.color_hue)); Slider(value = hue, onValueChange = { hue = it; updateHex() }, valueRange = 0f..360f)
                Text(stringResource(R.string.color_saturation)); Slider(value = saturation, onValueChange = { saturation = it; updateHex() })
                Text(stringResource(R.string.color_brightness)); Slider(value = brightness, onValueChange = { brightness = it; updateHex() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#6FAEFF", "#48D597", "#FF647C", "#B18CFF", "#FFB95A", "#00D0DA", "#FFFFFF", "#000000").forEach { value ->
                        Box(Modifier.size(32.dp).background(Color(AndroidColor.parseColor(value)), CircleShape).clickable { loadHex(value) })
                    }
                }
                if (valid == null) Text(stringResource(R.string.color_invalid), color = MaterialTheme.colorScheme.error)
                else if (contrast < 3f) Text(stringResource(R.string.color_contrast_warning), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onReset) { Text(stringResource(R.string.color_reset)) }
            }
        }, confirmButton = { TextButton(enabled = valid != null, onClick = { valid?.let(onApply) }) { Text(stringResource(R.string.color_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun roleLabel(role: String): Int = when (role) {
    "accent" -> R.string.color_accent
    "background" -> R.string.color_background
    "surface" -> R.string.color_surface
    "text" -> R.string.color_text
    "muted" -> R.string.color_muted
    "income" -> R.string.income
    "expense" -> R.string.expenses
    "chartTop" -> R.string.color_chart_top
    "chartBottom" -> R.string.color_chart_bottom
    "error" -> R.string.color_error
    "grid" -> R.string.color_grid
    else -> R.string.color_icons
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/settings/SettingsScreen.kt

```kotlin
package com.semmyx.ledger.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.semmyx.ledger.platform.HighRefreshRate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.R
import com.semmyx.ledger.diagnostics.CrashLogger
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.*
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsRoute(model: SettingsViewModel, biometricAvailable: Boolean, onNavigate: (Int) -> Unit,
    onPurchase: () -> Unit, onSupport: () -> Unit, onLock: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val display = LocalView.current.display
    val supportedRates = HighRefreshRate.supported(display)
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var name by remember(state.preferences.displayName) { mutableStateOf(state.preferences.displayName) }
    var activationCode by remember { mutableStateOf("") }
    var crashReport by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(model, context) { model.events.collect { snackbar.showSnackbar(context.getString(it.label())) } }
    if (state.failed) {
        EmptyState(stringResource(R.string.load_error_title), stringResource(R.string.storage_error), stringResource(R.string.back), { onNavigate(0) })
        return
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, actions = {
        IconButton(onClick = onLock) { Icon(Icons.Default.Lock, stringResource(R.string.lock_now)) }
    }) }, bottomBar = { LedgerBottomBar(1, onNavigate) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "profile") { ContentCard {
                Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.local_profile), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.display_name)) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = { model.saveName(name) }, enabled = name.trim() != state.preferences.displayName) { Text(stringResource(R.string.save_profile)) }
            } }
            item(key = "language") { ContentCard {
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.language_instant), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        FilterChip(selected = state.preferences.language == language, onClick = { model.language(language) }, modifier = Modifier.testTag("language:${language.tag}"),
                            label = { Text(stringResource(if (language == AppLanguage.ENGLISH) R.string.language_en else R.string.language_uk)) })
                    }
                }
            } }
            item(key = "bankImport") { ContentCard {
                Text(stringResource(R.string.bank_import_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.bank_consent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { onNavigate(4) }) { Text(stringResource(R.string.bank_dashboard)) }
            } }
            item(key = "customColors") { ColorSettings(state.preferences, model::color, model::resetColors) }
            item(key = "appearance") { ContentCard {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { theme ->
                        FilterChip(selected = state.preferences.theme == theme, onClick = { model.theme(theme) }, label = {
                            Text(stringResource(when (theme) { ThemeMode.SYSTEM -> R.string.theme_system; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark }))
                        })
                    }
                }
            } }
            item(key = "frameRate") { ContentCard {
                Text(stringResource(R.string.fps_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.fps_explanation), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 60, 90, 120).forEach { rate ->
                        FilterChip(selected = state.preferences.refreshRateHz == rate,
                            enabled = rate == 0 || rate in supportedRates,
                            onClick = { model.refreshRate(rate) }, modifier = Modifier.testTag("fps:$rate"),
                            label = { Text(if (rate == 0) stringResource(R.string.fps_auto) else stringResource(R.string.fps_value, rate)) })
                    }
                }
                Text(stringResource(R.string.fps_supported, supportedRates.sorted().joinToString(" / ")),
                    style = MaterialTheme.typography.bodySmall)
                if (state.preferences.refreshRateHz != 0 && state.preferences.refreshRateHz !in supportedRates)
                    Text(stringResource(R.string.fps_fallback), color = MaterialTheme.colorScheme.error)
            } }
            item(key = "currency") { ContentCard {
                Text(stringResource(R.string.default_currency), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.currency_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerCurrency.entries.forEach { currency ->
                        FilterChip(selected = state.preferences.currency == currency, onClick = { model.currency(currency) }, label = { Text(currency.name) })
                    }
                }
            } }
            item(key = "security") { ContentCard {
                Text(stringResource(R.string.security), style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.biometric_unlock), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (biometricAvailable) R.string.biometric_explanation else R.string.biometric_unavailable),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.preferences.biometricsEnabled, onCheckedChange = model::biometrics,
                        enabled = biometricAvailable || state.preferences.biometricsEnabled, modifier = Modifier.testTag("biometricSwitch"))
                }
                Text(stringResource(R.string.auto_lock_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onLock) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.lock_now)) }
            } }
            item(key = "premium") { ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.premium_name), style = MaterialTheme.typography.titleLarge)
                }
                Text(stringResource(R.string.premium_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• " + stringResource(R.string.premium_feature_trends), style = MaterialTheme.typography.bodyMedium)
                    Text("• " + stringResource(R.string.premium_feature_mascot), style = MaterialTheme.typography.bodyMedium)
                    Text("• " + stringResource(R.string.premium_feature_support), style = MaterialTheme.typography.bodyMedium)
                }
                if (state.premium.entitled) {
                    Text(stringResource(R.string.premium_active), color = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(when (state.premium.source) {
                        PremiumSource.OFFICIAL_DEVELOPER -> R.string.premium_source_developer
                        PremiumSource.TRIAL -> R.string.premium_source_trial
                        else -> R.string.premium_source_play
                    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.premium.expiresAtMillis?.let {
                        Text(stringResource(R.string.premium_expires, formatDateTime(it, state.preferences.language)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(stringResource(if (state.premium.source == PremiumSource.GOOGLE_PLAY) R.string.premium_offline_notice
                        else R.string.premium_local_notice), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.premium_assistant_toggle), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.premium_assistant_toggle_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.preferences.premiumAssistantEnabled, onCheckedChange = model::premiumAssistant, modifier = Modifier.testTag("mascotSwitch"))
                    }
                }
                if (!state.premium.entitled || state.premium.expiresAtMillis != null) {
                    val price = state.premium.formattedPrice
                    Button(onClick = onPurchase, enabled = state.premium.configured && price != null && !state.premium.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (price == null) stringResource(R.string.premium_unlock) else stringResource(R.string.premium_buy, price))
                    }
                    if (!state.premium.configured) Text(stringResource(R.string.billing_configuration), style = MaterialTheme.typography.bodyMedium)
                }
                if (!state.premium.entitled && state.premium.trialAvailable) {
                    Text(stringResource(R.string.trial_explanation))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { model.startTrial(7) }) { Text(stringResource(R.string.trial_7)) }
                        OutlinedButton(onClick = { model.startTrial(14) }) { Text(stringResource(R.string.trial_14)) }
                    }
                }
                if (state.premium.entitled) {
                    Text(stringResource(R.string.mascot_style))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("gray" to R.string.mascot_gray, "sky" to R.string.mascot_sky).forEach { (value, name) ->
                            FilterChip(selected = state.preferences.mascotSkin == value, onClick = { model.skin(value) },
                                label = { Text(stringResource(name)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.mascot_animations), Modifier.weight(1f))
                        Switch(checked = state.preferences.mascotAnimations, onCheckedChange = model::mascotAnimations)
                    }
                }
                if (state.premium.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.premium.issue?.takeUnless { it == BillingIssue.CONFIGURATION }?.let {
                    Text(stringResource(it.label()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = model::restorePurchases, enabled = !state.premium.busy) { Text(stringResource(R.string.restore_purchases)) }
            } }
            if (state.premium.developerPurchaseEnabled && (!state.premium.entitled || state.premium.expiresAtMillis != null)) item(key = "developerPurchase") { ContentCard {
                Text(stringResource(R.string.developer_purchase_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.developer_purchase_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.developer_purchase_banner), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.developer_purchase_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.developer_purchase_button)) }
                HorizontalDivider()
                Text(stringResource(R.string.installation_id_label), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.premium.developerInstallationId, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).testTag("installationId"))
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(state.premium.developerInstallationId))
                        scope.launch { snackbar.showSnackbar(context.getString(R.string.installation_id_copied)) }
                    }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.installation_id_copy)) }
                }
                Text(stringResource(R.string.activation_code_label), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = activationCode, onValueChange = { activationCode = it }, modifier = Modifier.fillMaxWidth().testTag("activationCodeField"),
                    singleLine = true, label = { Text(stringResource(R.string.activation_code_hint)) })
                Button(onClick = { model.activateDeveloperCode(activationCode); activationCode = "" }, enabled = activationCode.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.activation_code_button))
                }
            } }
            item(key = "rates") { ContentCard {
                Text(stringResource(R.string.reference_rates), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.rates_disclaimer), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                if (state.rates.isEmpty()) Text(stringResource(R.string.rates_empty), style = MaterialTheme.typography.bodyMedium)
                val formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag(state.preferences.language.tag)).apply { maximumFractionDigits = 4 }
                state.rates.forEach { rate ->
                    Text(stringResource(R.string.rate_value, rate.currency.name, formatter.format(rate.uahPerUnit)), style = MaterialTheme.typography.bodyLarge)
                }
                state.rates.minByOrNull { it.date }?.let { rate ->
                    Text(stringResource(R.string.rates_as_of, formatDate(rate.date, state.preferences.language)), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = model::refreshRates, enabled = !state.refreshing) { Text(stringResource(if (state.refreshing) R.string.refreshing else R.string.refresh_rates)) }
            } }
            item(key = "support") { ContentCard {
                Text(stringResource(R.string.support), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.support_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.contact_telegram)) }
                OutlinedButton(onClick = {
                    val saved = CrashLogger.readLastCrash(context)
                    if (saved == null) scope.launch { snackbar.showSnackbar(context.getString(R.string.diagnostics_none)) }
                    else crashReport = saved
                }, modifier = Modifier.fillMaxWidth().testTag("showCrashReport")) { Text(stringResource(R.string.diagnostics_show_button)) }
            } }
        }
    }
    crashReport?.let { report ->
        AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text(stringResource(R.string.diagnostics_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.diagnostics_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer {
                        Text(report, style = MaterialTheme.typography.bodySmall, modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(report))
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.diagnostics_copied)) }
                }) { Text(stringResource(R.string.diagnostics_copy)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { CrashLogger.clear(context); crashReport = null }) { Text(stringResource(R.string.diagnostics_clear)) }
                    TextButton(onClick = { crashReport = null }) { Text(stringResource(R.string.diagnostics_close)) }
                }
            },
        )
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/settings/SettingsViewModel.kt

```kotlin
package com.semmyx.ledger.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.common.UiMessage
import com.semmyx.ledger.presentation.common.guarded
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class SettingsState(
    val preferences: UserPreferences = UserPreferences(),
    val premium: PremiumState = PremiumState(),
    val rates: List<ExchangeRate> = emptyList(),
    val refreshing: Boolean = false,
    val failed: Boolean = false,
)

class SettingsViewModel(
    private val preferences: PreferencesRepository,
    private val premium: PremiumRepository,
    private val rates: RateRepository,
    private val configureBiometrics: suspend (Boolean) -> Unit,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val eventChannel = Channel<UiMessage>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val state = combine(preferences.preferences, premium.state, rates.rates, refreshing) { prefs, access, rates, busy ->
        SettingsState(prefs, access, rates, busy)
    }.catch { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        emit(SettingsState(failed = true))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    fun saveName(value: String) {
        if (value.trim().length > 40) { eventChannel.trySend(UiMessage.NAME_INVALID); return }
        change { preferences.setName(value); eventChannel.send(UiMessage.SAVED) }
    }
    fun currency(value: LedgerCurrency) = change { preferences.setCurrency(value) }
    fun theme(value: ThemeMode) = change { preferences.setTheme(value) }
    fun language(value: AppLanguage) = change { preferences.setLanguage(value) }
    fun biometrics(enabled: Boolean) {
        guarded(onError = { eventChannel.trySend(UiMessage.BIOMETRIC_UNAVAILABLE) }) {
            configureBiometrics(enabled)
            preferences.setBiometrics(enabled)
        }
    }
    /** Only meaningful while entitled; the mascot never renders for free accounts regardless of this flag. */
    fun premiumAssistant(enabled: Boolean) = change { preferences.setPremiumAssistant(enabled) }
    fun color(key: String, hex: String?) = change { preferences.setColor(key, hex) }
    fun resetColors() = change { preferences.resetColors() }
    fun refreshRate(hz: Int) = change { preferences.setRefreshRate(hz) }
    fun skin(value: String) = change { preferences.setMascotSkin(value) }
    fun mascotAnimations(value: Boolean) = change { preferences.setMascotAnimations(value) }
    fun startTrial(days: Int) = change {
        val granted = premium.startTrial(days)
        if (granted) preferences.setPremiumAssistant(true)
        eventChannel.send(if (granted) UiMessage.PREMIUM_ACTIVATED else UiMessage.ACTIVATION_INVALID)
    }
    fun restorePurchases() = change { premium.refresh() }
    /** Verifies a developer-issued Telegram-sale activation code and grants TG Plus offline if valid. */
    fun activateDeveloperCode(code: String) {
        if (code.isBlank()) { eventChannel.trySend(UiMessage.ACTIVATION_INVALID); return }
        guarded(onError = { eventChannel.trySend(UiMessage.STORAGE) }) {
            val granted = premium.activateDeveloperToken(code)
            eventChannel.send(if (granted) UiMessage.PREMIUM_ACTIVATED else UiMessage.ACTIVATION_INVALID)
        }
    }
    fun refreshRates() {
        if (refreshing.value) return
        guarded(onError = { refreshing.value = false; eventChannel.trySend(UiMessage.RATE_OFFLINE) }) {
            refreshing.value = true
            try { rates.refresh() } finally { refreshing.value = false }
        }
    }
    private fun change(block: suspend () -> Unit) {
        guarded(onError = { eventChannel.trySend(UiMessage.STORAGE) }) { block() }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/support/HelpScreen.kt

```kotlin
package com.semmyx.ledger.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.BuildConfig
import com.semmyx.ledger.R
import com.semmyx.ledger.presentation.common.ContentCard
import com.semmyx.ledger.presentation.common.LedgerBottomBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onNavigate: (Int) -> Unit, onSupport: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.help)) }) }, bottomBar = { LedgerBottomBar(2, onNavigate) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ContentCard {
                Text(stringResource(R.string.help_heading), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.help_intro))
                Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.contact_telegram)) }
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_entries_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_entries_body))
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_privacy_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_privacy_body))
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_security_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_security_body))
            } }
            item { ContentCard {
                Text(stringResource(R.string.help_premium_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_premium_body))
            } }
            item { Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/support/SupportSheet.kt

```kotlin
package com.semmyx.ledger.presentation.support

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.platform.SupportNavigator
import com.semmyx.ledger.presentation.common.ContentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportSheet(onDismiss: () -> Unit, onContact: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.here_to_help), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.support_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ContentCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.lead_developer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.support_handle), style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(modifier = Modifier.testTag("copySupport"), onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.support), SupportNavigator.HANDLE))
                        copied = true
                    }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.copy_handle)) }
                }
                if (copied) Text(stringResource(R.string.handle_copied), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onContact, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("contactTelegram")) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.contact_telegram))
            }
            Text(stringResource(R.string.support_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/theme/AppearancePalette.kt

```kotlin
package com.semmyx.ledger.presentation.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.semmyx.ledger.domain.*

class AppearancePalette(private val overrides: Map<String, String> = emptyMap(), private val defaults: Map<String, String> = emptyMap()) {
    fun hex(key: String): String {
        overrides[key]?.let { return it }
        if (key == "icons") overrides["accent"]?.let { return it }
        if (key.startsWith("category:INCOME:")) overrides["income"]?.let { return it }
        if (key.startsWith("category:EXPENSE:")) overrides["expense"]?.let { return it }
        if (key == "chartTop" || key == "chartBottom") overrides["expense"]?.let { return it }
        return defaults[key] ?: ColorPreferences.defaultHex(key)
    }
    fun color(key: String): Color = Color(0xFF000000L or hex(key).removePrefix("#").toLong(16))
    fun category(category: Category, income: Boolean): Color = color(ColorPreferences.categoryKey(category,
        if (income) EntryType.INCOME else EntryType.EXPENSE))
    fun type(type: EntryType): Color = color(if (type == EntryType.INCOME) "income" else "expense")
}
val LocalAppearance = staticCompositionLocalOf { AppearancePalette() }
fun readableOn(background: Color): Color = if (background.luminance() > .179f) Color.Black else Color.White
```

## app/src/main/kotlin/com/semmyx/ledger/presentation/theme/Theme.kt

```kotlin
package com.semmyx.ledger.presentation.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.semmyx.ledger.domain.AppLanguage
import com.semmyx.ledger.domain.ThemeMode
import java.util.Locale

private val LightColors = lightColorScheme(
    primary = Color(0xFF1665C8), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F0FF), onPrimaryContainer = Color(0xFF123460),
    secondary = Color(0xFF216D56), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2F2EA), onSecondaryContainer = Color(0xFF15533D),
    background = Color(0xFFF6F8FC), onBackground = Color(0xFF142238),
    surface = Color.White, onSurface = Color(0xFF142238),
    surfaceVariant = Color(0xFFEAF0F7), onSurfaceVariant = Color(0xFF526175),
    outline = Color(0xFF788697), outlineVariant = Color(0xFFDCE4EE),
    error = Color(0xFFB3261E), onError = Color.White,
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FAEFF), onPrimary = Color(0xFF061D3C),
    primaryContainer = Color(0xFF152C49), onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFF35CB77), onSecondary = Color(0xFF002D15),
    secondaryContainer = Color(0xFF0C3321), onSecondaryContainer = Color(0xFFB5F5CE),
    background = Color.Black, onBackground = Color(0xFFF5F5F6),
    surface = Color(0xFF1C1D1D), onSurface = Color(0xFFF5F5F6),
    surfaceVariant = Color(0xFF252626), onSurfaceVariant = Color(0xFFA1A4A2),
    outline = Color(0xFF6C706D), outlineVariant = Color(0xFF333633),
    error = Color(0xFFFF6683), onError = Color(0xFF320009),
)
private val LedgerTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun LedgerTheme(mode: ThemeMode = ThemeMode.SYSTEM, overrides: Map<String, String> = emptyMap(), content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val palette = remember(overrides, dark) { AppearancePalette(overrides,
        if (dark) emptyMap() else mapOf("background" to "#F6F8FC", "surface" to "#FFFFFF", "text" to "#142238", "muted" to "#526175")) }
    val base = if (dark) DarkColors else LightColors
    val accent = palette.color("accent")
    val scheme = base.copy(primary = accent, onPrimary = readableOn(accent),
        primaryContainer = accent.copy(alpha = .18f), onPrimaryContainer = accent,
        secondary = palette.color("income"), onSecondary = readableOn(palette.color("income")),
        secondaryContainer = palette.color("income").copy(alpha = .18f), onSecondaryContainer = palette.color("income"),
        surfaceTint = accent, error = palette.color("error"), onError = readableOn(palette.color("error")),
        tertiaryContainer = palette.color("expense").copy(alpha = .18f), onTertiaryContainer = palette.color("expense"),
        tertiary = palette.color("expense"), onTertiary = readableOn(palette.color("expense")),
        background = if (dark || overrides.containsKey("background")) palette.color("background") else base.background,
        surface = if (dark || overrides.containsKey("surface")) palette.color("surface") else base.surface,
        onBackground = if (dark || overrides.containsKey("text")) palette.color("text") else base.onBackground,
        onSurface = if (dark || overrides.containsKey("text")) palette.color("text") else base.onSurface,
        surfaceVariant = if (dark || overrides.containsKey("surface")) palette.color("surface") else base.surfaceVariant,
        onSurfaceVariant = if (dark || overrides.containsKey("muted")) palette.color("muted") else base.onSurfaceVariant,
        outline = palette.color("grid"), outlineVariant = palette.color("grid"))
    CompositionLocalProvider(LocalAppearance provides palette) {
        MaterialTheme(colorScheme = scheme, typography = LedgerTypography,
            shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)), content = content)
    }
}

/**
 * Compose-scoped configuration avoids Activity.recreate() and a process restart.
 * DataStore is the single source of truth. LocalContext + LocalConfiguration
 * invalidate stringResource, date formatting, dialogs and accessibility labels.
 * Both supported languages are LTR. No global Locale.setDefault mutation.
 */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val original = LocalConfiguration.current
    val config = remember(original, language) {
        Configuration(original).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    }
    val localizedContext = remember(base, config) { base.createConfigurationContext(config) }
    CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides config,
        LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}
```

## app/src/main/kotlin/com/semmyx/ledger/voice/FinanceQuery.kt

```kotlin
package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.time.LocalDate

/** Structured intent resolver. Queries are resolved BEFORE monetary entries, never saved as purchases. */
sealed interface FinanceQuery {
    data object Balance : FinanceQuery
    enum class Period { TODAY, WEEK, MONTH, YEAR, ALL }
    data class Statistics(val period: Period, val type: EntryType, val category: Category?) : FinanceQuery {
        fun from(today: LocalDate): LocalDate = when (period) {
            Period.TODAY -> today
            Period.WEEK -> today.minusDays(6)
            Period.MONTH -> today.withDayOfMonth(1)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> FinanceWindow.EARLIEST
        }
    }
    companion object {
        fun parse(heard: String): FinanceQuery? {
            val text = heard.lowercase().trim()
            val balance = listOf("баланс", "balance", "скільки грошей", "сколько денег").any { it in text }
            val hasNumber = Regex("\\d").containsMatchIn(text)
            if (balance && !hasNumber) return Balance
            val expense = listOf("витрат", "потрат", "расход", "spent", "spend", "expense").any { it in text }
            val income = listOf("дохід", "доход", "заробив", "income", "earned").any { it in text }
            if (!expense && !income) return null
            val period = when {
                listOf("весь час", "все время", "all time", "увесь час").any { it in text } -> Period.ALL
                listOf("місяц", "месяц", "month").any { it in text } -> Period.MONTH
                listOf("тиж", "недел", "week").any { it in text } -> Period.WEEK
                listOf("за рік", "за год", "year").any { it in text } -> Period.YEAR
                listOf("сьогодні", "сегодня", "today").any { it in text } -> Period.TODAY
                else -> return null
            }
            // "I spent 100 today" describes an entry, not a statistics request.
            if (hasNumber) return null
            return Statistics(period, if (income) EntryType.INCOME else EntryType.EXPENSE, category(text))
        }
        fun category(text: String): Category? = listOf(
            Category.TRANSFER to listOf("переказ", "перевод", "transfer"),
            Category.DINING to listOf("ресторан", "restaurant", "кафе", "cafe"),
            Category.CASHBACK to listOf("кешбек", "кэшбек", "cashback"),
            Category.FOOD to listOf("продукт", "їж", "еду", "питан", "food", "grocer"),
            Category.TRANSPORT to listOf("транспорт", "таксі", "такси", "transport", "fuel"),
            Category.HOME to listOf("дім", "житло", "дом", "оренд", "комунал", "home", "rent"),
            Category.HEALTH to listOf("здоров", "ліки", "лекар", "health", "medicine"),
            Category.LEISURE to listOf("розваг", "развлеч", "leisure", "entertainment"),
            Category.SALARY to listOf("зарплат", "salary"),
            Category.OTHER to listOf("інше", "прочее", "other")
        ).firstOrNull { (_, words) -> words.any { it in text } }?.first
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistant.kt

```kotlin
package com.semmyx.ledger.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import com.semmyx.ledger.domain.AppLanguage
import java.util.Locale

/**
 * Optional, self-contained voice assistant helper.
 *
 * This file is intentionally isolated from the rest of the app (Models, ViewModels,
 * screens) so it can be adopted gradually without risking regressions elsewhere.
 * It is NOT wired into any screen yet. To use it:
 *  1. Create an instance in an Activity: `val assistant = OptionalVoiceAssistant(activity)`
 *  2. Call `assistant.recognizerIntent(language)` from an ActivityResultLauncher<Intent>,
 *     after requesting the RECORD_AUDIO permission.
 *  3. Read the result with `assistant.extractSpokenText(result.data)` and, optionally,
 *     `OptionalVoiceAssistant.classify(heard)` to react to a few simple EN/UK commands.
 *  4. Call `assistant.speak("...")` any time to have it talk back.
 *  5. Call `assistant.shutdown()` in onDestroy.
 *
 * Recognition uses the standard Android RecognizerIntent (speech-to-text is handled by the
 * device's configured recognizer; this app never receives or stores raw audio). Speech output
 * uses the on-device TextToSpeech engine. See docs/OPTIONAL_VOICE_ASSISTANT.md.
 */
class OptionalVoiceAssistant(activity: Activity) {
    private val app = activity.applicationContext
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var closed = false
    private var pendingSpeech: Pair<String, AppLanguage>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // Some devices (custom ROMs, some emulators, phones with no TTS data installed) can
        // throw here instead of just reporting TextToSpeech.ERROR through the callback. This is
        // a purely optional feature, so never let it take the whole app down with it.
        tts = try {
            TextToSpeech(app) { status ->
                mainHandler.post {
                    if (!closed) {
                        ttsReady = status == TextToSpeech.SUCCESS
                        val pending = pendingSpeech
                        pendingSpeech = null
                        if (ttsReady && pending != null) speak(pending.first, pending.second)
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun speak(text: String, language: AppLanguage = AppLanguage.ENGLISH) {
        if (closed) return
        val engine = tts ?: return
        if (!ttsReady) { pendingSpeech = text to language; return }
        try {
            engine.language = Locale.forLanguageTag(language.tag)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "optionalVoiceAssistant")
        } catch (_: Throwable) {
            // Speaking is best-effort; a missing voice/locale on the device should never crash.
        }
    }

    /** Build the Intent to pass to an ActivityResultLauncher<Intent> for speech recognition. */
    fun recognizerIntent(language: AppLanguage): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

    /** Extract the top recognized utterance from an ActivityResult's data Intent, if any. */
    fun extractSpokenText(resultData: Intent?): String? =
        resultData?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()

    fun shutdown() {
        closed = true
        pendingSpeech = null
        mainHandler.removeCallbacksAndMessages(null)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Throwable) {
            // Ignore -- we're tearing down anyway.
        }
        tts = null
    }

    /** Stop output before opening microphone: the recognizer must not transcribe the cat. */
    fun stopSpeaking() { pendingSpeech = null; try { tts?.stop() } catch (_: Exception) { } }

    companion object {
        /**
         * Whether any app on this device can handle speech recognition. Many phones (custom
         * ROMs, Google-app-free builds, some emulators) have none installed. Always check this
         * -- or catch ActivityNotFoundException around the launch -- before starting the
         * recognizer intent, otherwise the missing handler crashes the app with no on-screen error.
         */
        fun isAvailable(context: Context): Boolean {
            val probe = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            return probe.resolveActivity(context.packageManager) != null
        }

        /**
         * Very small, best-effort command classifier for simple EN/UK phrases such as
         * "what's my balance" / "скільки в мене грошей". Returns null when nothing matches;
         * callers should fall back to just repeating what was heard.
         */
        fun classify(heard: String): VoiceCommand? {
            val text = heard.trim().lowercase()
            return when {
                text.isBlank() -> null
                BALANCE_EN.any { text.contains(it) } || BALANCE_UK.any { text.contains(it) } -> VoiceCommand.ShowBalance
                SPENT_EN.any { text.contains(it) } || SPENT_UK.any { text.contains(it) } -> VoiceCommand.ShowSpentToday
                ADD_EN.any { text.contains(it) } || ADD_UK.any { text.contains(it) } -> VoiceCommand.OpenAddEntry
                else -> null
            }
        }
        private val BALANCE_EN = listOf("balance", "how much money", "how much do i have")
        private val BALANCE_UK = listOf("баланс", "скільки в мене грошей", "скільки грошей")
        private val SPENT_EN = listOf("spent today", "spend today", "what did i spend")
        private val SPENT_UK = listOf("витратив сьогодні", "витратили сьогодні", "скільки витратив")
        private val ADD_EN = listOf("add entry", "new entry", "add expense", "add income")
        private val ADD_UK = listOf("додати запис", "новий запис", "додати витрату", "додати доход")
    }
}

sealed interface VoiceCommand {
    data object ShowBalance : VoiceCommand
    data object ShowSpentToday : VoiceCommand
    data object OpenAddEntry : VoiceCommand
}
```

## app/src/main/kotlin/com/semmyx/ledger/voice/SpokenMoney.kt

```kotlin
package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Explicit words for minus and currency: TTS must never depend on pronunciation of '-' or ₴. */
object SpokenMoney {
    fun format(minor: Long, currency: LedgerCurrency, language: AppLanguage): String {
        val uk = language == AppLanguage.UKRAINIAN
        val value = BigDecimal.valueOf(minor, 2).abs()
        val number = NumberFormat.getNumberInstance(Locale.forLanguageTag(language.tag)).apply {
            isGroupingUsed = false; maximumFractionDigits = 2
        }.format(value)
        val unit = when (currency) {
            LedgerCurrency.UAH -> if (uk) "гривень" else "hryvnias"
            LedgerCurrency.USD -> if (uk) "доларів" else "US dollars"
            LedgerCurrency.EUR -> if (uk) "євро" else "euros"
            LedgerCurrency.GBP -> if (uk) "фунтів" else "pounds"
            LedgerCurrency.PLN -> if (uk) "злотих" else "zlotys"
        }
        return (if (minor < 0) if (uk) "мінус " else "minus " else "") + number + " " + unit
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/voice/VoiceEntryParser.kt

```kotlin
package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.Category
import com.semmyx.ledger.domain.EntryType
import com.semmyx.ledger.domain.Money

/**
 * Turns a free-form spoken sentence into a ready-to-save ledger entry, so the user can just
 * describe what happened ("I bought 1 kg of tomatoes for 100 UAH") instead of using the small set
 * of fixed voice commands in OptionalVoiceAssistant. Deliberately kept in its own file with no
 * Android/Compose imports, so it stays easy to read, extend with more keywords, and unit-test.
 *
 * This is a best-effort, fully offline, rule-based reader -- not a language model -- so it
 * understands common Ukrainian, Russian, and English money phrasing (many Ukrainian speakers mix
 * Russian words when talking about money, so both are always checked together) but can still
 * misread an unusual sentence. When it can't find any amount at all it returns null so the caller
 * can fall back to the fixed voice commands or a "didn't understand" reply.
 */
data class VoiceEntryDraft(
    val type: EntryType,
    val category: Category,
    val amountMinor: Long,
    /** May be blank; the caller should fall back to the category's display name. */
    val title: String,
    val note: String,
)

object VoiceEntryParser {

    fun parse(heard: String): VoiceEntryDraft? {
        val text = heard.trim()
        if (text.isBlank()) return null
        val amountMatch = AMOUNT_WITH_CURRENCY.find(text) ?: run {
            val bareNumbers = BARE_NUMBER.findAll(text).toList()
            if (bareNumbers.isEmpty()) null else bareNumbers.last()
        } ?: return null
        // BARE_NUMBER has no capturing group at all, so its pattern's group count is 0 -- indexing
        // amountMatch.groups[1] directly for that case throws IndexOutOfBoundsException("No group
        // 1") instead of returning null. groupValues.getOrNull(1) checks the list size first (which
        // reflects the *pattern's* group count, 0 there), so it safely returns null without ever
        // touching the missing group.
        val numberText = (amountMatch.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: amountMatch.value)
            .replace(" ", "").replace("\u00A0", "")
        val amountMinor = Money.parseMinor(numberText) ?: return null

        val lower = text.lowercase()
        val transactionIntent = (INCOME_KEYWORDS + EXPENSE_KEYWORDS).any { lower.contains(it) }
        if (!transactionIntent && AMOUNT_WITH_CURRENCY.find(text) == null) return null
        if (listOf("не купив", "не купил", "не витратив", "не потратил", "did not buy", "did not spend").any { it in lower }) return null
        val type = when {
            EXPENSE_KEYWORDS.any { lower.contains(it) } -> EntryType.EXPENSE
            INCOME_KEYWORDS.any { lower.contains(it) } -> EntryType.INCOME
            else -> EntryType.EXPENSE
        }
        val category = CATEGORY_KEYWORDS.firstOrNull { (_, keywords) -> keywords.any { lower.contains(it) } }?.first
            ?: Category.OTHER

        var remainder = text.removeRange(amountMatch.range)
        for (pattern in FILLER_PATTERNS) remainder = pattern.replace(remainder, " ")
        val title = remainder
            .replace(PUNCTUATION, " ")
            .split(WHITESPACE)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
            .take(60)

        return VoiceEntryDraft(type, category, amountMinor, title, text.take(240))
    }

    // Stems rather than full words on purpose, so a single entry (e.g. "грив") also matches every
    // declined form spoken aloud ("гривень", "гривні", "гривня", "гривнями", ...).
    private val CURRENCY_STEMS = listOf(
        "грн", "грив\\p{L}*", "uah", "\u20B4",
        "usd", "дол\\p{L}*", "\\$",
        "eur", "євро", "евро", "\u20AC",
        "gbp", "фунт\\p{L}*", "\u00A3",
        "pln", "злот\\p{L}*",
    )
    private val AMOUNT_WITH_CURRENCY = Regex(
        "((?:\\d{1,3}(?:[ \u00A0]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?)\\s*(?:" + CURRENCY_STEMS.joinToString("|") + ")",
        RegexOption.IGNORE_CASE,
    )
    private val BARE_NUMBER = Regex("(?:\\d{1,3}(?:[ \u00A0]\\d{3})+|\\d+)(?:[.,]\\d{1,2})?")
    private val PUNCTUATION = Regex("[,.;:!?\u2014\u2013]+")
    private val WHITESPACE = Regex("\\s+")

    private val INCOME_KEYWORDS = listOf(
        // Russian
        "получил", "получила", "получила", "зарплату", "зарплата", "зарплаты", "доход", "поступ", "продал", "продала",
        "аванс", "премия", "кэшбек", "кешбек", "пополнил", "пополнила", "пополнение", "вернули", "возврат", "вернул",
        // Ukrainian
        "отримав", "отримала", "надійшло", "надходження", "поповнення", "поповнив", "поповнила", "зарплату",
        "дохід", "продав", "продала", "премія", "повернення", "повернули",
        // English
        "received", "salary", "income", "paycheck", "got paid", "paid me", "refund", "cashback", "sold", "deposit", "bonus",
    )
    private val EXPENSE_KEYWORDS = listOf(
        // Russian
        "купил", "купила", "потратил", "потратила", "заплатил", "заплатила", "оплатил", "оплатила", "приобрел", "приобрела",
        // Ukrainian
        "купив", "купила", "витратив", "витратила", "заплатив", "заплатила", "оплатив", "оплатила", "придбав", "придбала",
        // English
        "bought", "spent", "purchased", "paid for", "paid",
    )
    private val CATEGORY_KEYWORDS: List<Pair<Category, List<String>>> = listOf(
        Category.CASHBACK to listOf("кешбек", "кэшбек", "cashback"),
        Category.DINING to listOf("ресторан", "restaurant", "кафе", "cafe", "бар", "bar"),
        Category.TRANSFER to listOf("переказ", "перевод", "transfer"),
        Category.SALARY to listOf("зарплат", "заробітн", "оклад", "salary", "paycheck", "аванс", "прем", "bonus"),
        Category.FOOD to listOf(
            "томат", "помідор", "продукт", "їж", "еда", "кава", "coffee", "ресторан", "restaurant",
            "кафе", "cafe", "хліб", "хлеб", "м'яс", "мяс", "овоч", "овощ", "фрукт", "обід", "обед", "вечер",
            "ужин", "завтрак", "снідан", "піцц", "pizza", "grocery", "food", "супермаркет", "магазин",
        ),
        Category.TRANSPORT to listOf(
            "такс", "taxi", "автобус", "bus", "метро", "metro", "бензин", "паливо", "fuel", "gas",
            "проїзд", "проезд", "uber", "bolt", "парковк", "parking", "поїзд", "поезд", "квиток", "билет",
        ),
        Category.HOME to listOf(
            "квартир", "оренд", "аренд", "rent", "комунал", "utilities", "меблі", "мебель", "furniture", "ремонт", "repair",
        ),
        Category.HEALTH to listOf(
            "лікар", "врач", "доктор", "doctor", "аптек", "pharmacy", "лік", "лекарств", "medicine",
            "стоматолог", "dentist", "лікарн", "больниц", "hospital",
        ),
        Category.LEISURE to listOf(
            "кіно", "кино", "movie", "cinema", "ігр", "игр", "game", "розваг", "развлеч", "entertainment",
            "подорож", "путешеств", "travel", "відпочин", "отдых", "vacation", "концерт", "concert", "театр", "theater",
        ),
    )
    private val FILLER_WORDS = (INCOME_KEYWORDS + EXPENSE_KEYWORDS + listOf(
        "привіт", "привет", "hello", "hi", "hey",
        "я", "i", "мені", "мне", "мою", "my",
        "за", "на", "в", "у", "по", "for", "of", "on", "in", "the", "a", "an",
    )).distinct()
    // Explicit Unicode letter/number boundaries keep behavior consistent across Android/JVM.
    private val FILLER_PATTERNS = FILLER_WORDS.map {
        Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])")
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/voice/VoiceSmallTalk.kt

```kotlin
package com.semmyx.ledger.voice

/**
 * A tiny rule-based "small talk" layer for the cat assistant, so an everyday greeting like
 * "hi" / "privit" / "privet" gets a friendly reply back instead of "I did not understand
 * that", the way a real conversation partner would react. This is NOT a language model -- it
 * only recognizes a handful of common social phrases in Ukrainian, Russian, and English (many
 * Ukrainian speakers mix Russian words, so both are always checked together) and maps them to
 * one of a few topics. DashboardScreen.kt turns a topic into the actual reply text (localized
 * to the app's current EN/UK language via string resources) and has the cat speak it.
 *
 * This is checked strictly after VoiceEntryParser (an amount always means "log this entry",
 * never chit-chat) and after the fixed balance/spending/add-entry commands, so a real command
 * phrase is never swallowed by small talk.
 */
object VoiceSmallTalk {

    enum class Topic { GREETING, HOW_ARE_YOU, THANKS, WHO_ARE_YOU, BYE, COMPLIMENT, JOKE }

    fun classify(heard: String): Topic? {
        val text = heard.trim().lowercase()
        if (text.isBlank()) return null
        return when {
            containsAny(text, BYE) -> Topic.BYE
            containsAny(text, HOW_ARE_YOU) -> Topic.HOW_ARE_YOU
            containsAny(text, THANKS) -> Topic.THANKS
            containsAny(text, WHO_ARE_YOU) -> Topic.WHO_ARE_YOU
            containsAny(text, COMPLIMENT) -> Topic.COMPLIMENT
            containsAny(text, JOKE) -> Topic.JOKE
            containsAny(text, GREETING) -> Topic.GREETING
            else -> null
        }
    }

    // Explicit Unicode boundaries avoid matching greetings inside longer words.
    private fun containsAny(text: String, phrases: List<String>): Boolean = phrases.any { phrase ->
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{N}])").containsMatchIn(text)
    }

    private val GREETING = listOf(
        "привіт", "привет", "вітаю", "здравствуй", "здрастуй", "доброго дня", "добрий день",
        "добрий вечір", "добрый вечер", "доброе утро", "доброго ранку", "hello", "hi", "hey", "yo",
    )
    private val HOW_ARE_YOU = listOf(
        "як справи", "як ти", "как дела", "как ты", "как поживаешь", "як поживаєш",
        "how are you", "whats up",
    )
    private val THANKS = listOf("дякую", "спасибі", "спасибо", "благодарю", "thanks", "thank you", "thx")
    private val WHO_ARE_YOU = listOf(
        "хто ти", "кто ты", "як тебе звати", "как тебя зовут", "твоє ім'я", "твое имя",
        "who are you", "what are you", "whats your name",
    )
    private val BYE = listOf("бувай", "до побачення", "пока", "прощай", "до зустрічі", "goodbye", "bye", "see you")
    private val COMPLIMENT = listOf(
        "гарний кіт", "хороший кот", "милий кіт", "милый кот", "люблю тебе", "люблю тебя",
        "good cat", "cute cat", "i love you", "good kitty",
    )
    private val JOKE = listOf("жарт", "анекдот", "розкажи щось", "расскажи что", "joke", "tell me a joke")
}
```

## app/src/main/res/drawable/brand_sky.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="180dp" android:height="180dp" android:viewportWidth="180" android:viewportHeight="180">
    <path android:fillColor="#14223C" android:pathData="M90,0 A90,90 0,1 1,90 180 A90,90 0,1 1,90 0"/>
    <path android:fillColor="#91BAF2" android:pathData="M35,109 C42,59 100,23 144,70 C109,52 65,72 55,112 Z"/>
    <path android:strokeColor="#FFFFFF" android:strokeWidth="3" android:fillColor="#00000000" android:pathData="M47,124 L71,100 L90,112 L130,78"/>
    <path android:fillColor="#F4D89F" android:pathData="M127,42 L130,52 L140,55 L130,58 L127,68 L124,58 L114,55 L124,52 Z"/>
</vector>
```

## app/src/main/res/drawable/ic_launcher_foreground.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#7DB8FF" android:pathData="M30,54L42,54L42,77L30,77Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M48,41L60,41L60,77L48,77Z" />
    <path android:fillColor="#7DB8FF" android:pathData="M66,29L78,29L78,77L66,77Z" />
</vector>
```

## app/src/main/res/drawable/ic_splash.xml

```xml
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
        <vector android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
            <group android:name="mark" android:pivotX="54" android:pivotY="54">
                <path android:fillColor="#7DB8FF" android:pathData="M30,54L42,54L42,77L30,77Z" />
                <path android:fillColor="#FFFFFF" android:pathData="M48,41L60,41L60,77L48,77Z" />
                <path android:fillColor="#7DB8FF" android:pathData="M66,29L78,29L78,77L66,77Z" />
            </group>
        </vector>
    </aapt:attr>
    <target android:name="mark">
        <aapt:attr name="android:animation">
            <objectAnimator android:propertyName="rotation" android:valueFrom="-12" android:valueTo="0" android:duration="400" android:valueType="floatType" />
        </aapt:attr>
    </target>
</animated-vector>
```

## app/src/main/res/drawable/mascot_gray.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="104dp" android:height="104dp" android:viewportWidth="104" android:viewportHeight="104">
    <path android:fillColor="#E8EDF5" android:pathData="M52,2 A50,50 0,1 1,52 102 A50,50 0,1 1,52 2"/>
    <path android:fillColor="#9AA5B1" android:pathData="M22,48 L24,14 L45,34 L59,34 L80,14 L82,48 C94,90 10,90 22,48"/>
    <path android:fillColor="#F4B8CB" android:pathData="M28,35 L28,22 L39,34 Z M66,34 L76,22 L76,35 Z"/>
    <path android:fillColor="#25334A" android:pathData="M35,49 A4,5 0,1 1,35 59 A4,5 0,1 1,35 49 M69,49 A4,5 0,1 1,69 59 A4,5 0,1 1,69 49"/>
    <path android:fillColor="#F4B8CB" android:pathData="M47,64 L57,64 L52,70 Z"/>
    <path android:strokeColor="#25334A" android:strokeWidth="1.5" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M52,70 Q46,77 43,71 M52,70 Q58,77 61,71 M32,64 L15,60 M32,70 L14,71 M72,64 L89,60 M72,70 L90,71"/>
</vector>
```

## app/src/main/res/drawable/mascot_sky.xml

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="104dp" android:height="104dp" android:viewportWidth="104" android:viewportHeight="104">
    <path android:fillColor="#E9F4FF" android:pathData="M52,2 A50,50 0,1 1,52 102 A50,50 0,1 1,52 2"/>
    <path android:fillColor="#83B5EE" android:pathData="M22,48 L24,14 L45,34 L59,34 L80,14 L82,48 C94,90 10,90 22,48"/>
    <path android:fillColor="#F4B8CB" android:pathData="M28,35 L28,22 L39,34 Z M66,34 L76,22 L76,35 Z"/>
    <path android:fillColor="#25334A" android:pathData="M35,49 A4,5 0,1 1,35 59 A4,5 0,1 1,35 49 M69,49 A4,5 0,1 1,69 59 A4,5 0,1 1,69 49"/>
    <path android:fillColor="#F4B8CB" android:pathData="M47,64 L57,64 L52,70 Z"/>
    <path android:strokeColor="#25334A" android:strokeWidth="1.5" android:strokeLineCap="round" android:fillColor="#00000000" android:pathData="M52,70 Q46,77 43,71 M52,70 Q58,77 61,71 M32,64 L15,60 M32,70 L14,71 M72,64 L89,60 M72,70 L90,71"/>
</vector>
```

## app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

## app/src/main/res/values-night/themes.xml

```xml
<resources>
    <style name="Theme.Ledger" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowActionModeOverlay">true</item>
        <item name="android:windowBackground">#111925</item>
    </style>
</resources>
```

## app/src/main/res/values-uk/strings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">TG Ledger</string>
    <string name="overview">Огляд</string>
    <string name="settings">Налаштування</string>
    <string name="help">Довідка</string>
    <string name="back">Назад</string>
    <string name="retry">Спробувати ще</string>
    <string name="next">Продовжити</string>
    <string name="skip">Пропустити</string>
    <string name="get_started">Створити мій облік</string>
    <string name="page_of">%1$d / %2$d</string>
    <string name="onboard_title_one">Ваші гроші.\nЗрозуміла картина.</string>
    <string name="onboard_body_one">Обліковуйте доходи та щоденні витрати в одному зручному місці. Баланс оновлюється одразу після змін.</string>
    <string name="onboard_title_two">Приватність за задумом.</string>
    <string name="onboard_body_two">Захистіть доступ шестизначним PIN-кодом і, за бажанням, надійною біометрією. Ваші записи залишаються на цьому пристрої.</string>
    <string name="onboard_title_three">Працює й без інтернету.</string>
    <string name="onboard_body_three">Додавайте, редагуйте та переглядайте записи без інтернету. Без реєстрації, реклами й зайвих відволікань.</string>
    <string name="unlock_title">З поверненням</string>
    <string name="confirm_pin">Підтвердьте PIN-код</string>
    <string name="create_pin">Створіть PIN-код</string>
    <string name="pin_setup_subtitle">Оберіть шість цифр, а потім введіть їх ще раз.</string>
    <string name="pin_unlock_subtitle">Введіть шестизначний PIN-код, щоб продовжити.</string>
    <string name="pin_digit_count">Введено цифр PIN-коду: %1$d із 6</string>
    <string name="pin_locked">Забагато спроб. Повторіть через %1$d с.</string>
    <string name="pin_incorrect">Неправильний PIN-код. Спробуйте ще раз.</string>
    <string name="pin_mismatch">PIN-коди не збігаються. Створіть PIN-код ще раз.</string>
    <string name="pin_recovery_notice">Збережіть PIN-код у надійному місці. Віддаленого скидання немає. Очищення сховища застосунку видаляє всі локальні записи.</string>
    <string name="erase_digit">Стерти останню цифру</string>
    <string name="start_over">Обрати інший PIN-код</string>
    <string name="need_help">Потрібна допомога?</string>
    <string name="security_error">Не вдалося відкрити захищені дані. Спробуйте ще раз. Якщо ключі пристрою скинуто, відновити дані неможливо; очищення сховища застосунку видалить усі записи.</string>
    <string name="biometric_failed">Не вдалося розблокувати біометрією. Скористайтеся PIN-кодом. Якщо біометричні дані змінено, увімкніть біометрію знову в налаштуваннях.</string>
    <string name="unlock_biometric">Розблокувати біометрією</string>
    <string name="biometric_title">Розблокувати TG Ledger</string>
    <string name="biometric_subtitle">Підтвердьте особу, щоб відкрити записи.</string>
    <string name="use_pin">Використати PIN-код</string>
    <string name="load_error_title">Потрібна ваша увага</string>
    <string name="storage_error">Не вдалося прочитати або зберегти дані. Перевірте вільне місце та спробуйте ще раз.</string>
    <string name="saved">Збережено</string>
    <string name="entry_deleted">Запис видалено</string>
    <string name="undo">Скасувати</string>
    <string name="undo_expired">Це видалення вже неможливо скасувати.</string>
    <string name="dashboard_heading">Ваші гроші — зрозуміло.</string>
    <string name="greeting">Вітаємо, %1$s</string>
    <string name="dashboard_subtitle">Трохи більше ясності щодня.</string>
    <string name="total_balance">Загальний баланс</string>
    <string name="currency_scope">За весь час · лише записи в %1$s</string>
    <string name="income">Доходи</string>
    <string name="expenses">Витрати</string>
    <string name="expense">Витрата</string>
    <string name="cash_flow">Рух коштів</string>
    <string name="last_7_days">7 днів</string>
    <string name="last_30_days">30 днів</string>
    <string name="last_30_days_plus">30 днів · Plus</string>
    <string name="chart_description">Рух коштів за %1$d днів: доходи %2$s, витрати %3$s. Окремі записи наведено нижче.</string>
    <string name="chart_empty">Додайте перший запис за цей період, щоб побачити динаміку.</string>
    <string name="legend_income">Доходи · ліві стовпчики · %1$s</string>
    <string name="legend_expense">Витрати · праві стовпчики · %1$s</string>
    <string name="activity">Останні записи</string>
    <string name="search_entries">Пошук за назвою та приміткою</string>
    <string name="clear_search">Очистити пошук</string>
    <string name="all">Усі</string>
    <string name="search_scope">Фільтри змінюють лише список, а не загальний баланс чи діаграму.</string>
    <string name="no_matches">Збігів не знайдено</string>
    <string name="no_matches_body">Змініть пошуковий запит або скиньте фільтри.</string>
    <string name="clear_filters">Скинути фільтри</string>
    <string name="empty_ledger_title">Почнімо з чистого аркуша</string>
    <string name="empty_ledger_body">Додайте дохід або витрату в цій валюті, щоб побачити фінансову картину.</string>
    <string name="add_first_entry">Додати перший запис</string>
    <string name="add_entry">Додати запис</string>
    <string name="edit_entry">Редагувати запис</string>
    <string name="delete_entry">Видалити запис</string>
    <string name="delete_named_entry">Видалити %1$s</string>
    <string name="load_more">Показати більше записів</string>
    <string name="positive_amount">+%1$s</string>
    <string name="negative_amount">−%1$s</string>
    <string name="category_food">Супермаркети та продукти</string>
    <string name="category_transport">Транспорт</string>
    <string name="category_home">Дім</string>
    <string name="category_health">Здоров’я</string>
    <string name="category_leisure">Дозвілля</string>
    <string name="category_salary">Зарплата</string>
    <string name="category_other">Інше</string>
    <string name="entry_currency_notice">Валюта: %1$s. Наявні записи зберігають початкову валюту. Змініть типову валюту в налаштуваннях перед додаванням нового запису.</string>
    <string name="amount">Сума</string>
    <string name="amount_invalid">Введіть від 0,01 до 99 999 999,99 з не більш ніж двома десятковими знаками, без роздільників тисяч.</string>
    <string name="entry_title">Назва</string>
    <string name="title_invalid">Введіть назву від 1 до 60 символів.</string>
    <string name="title_hint">Наприклад: продукти або зарплата</string>
    <string name="category">Категорія</string>
    <string name="date_invalid">Оберіть дату від 1 січня 2000 року до сьогодні.</string>
    <string name="note_optional">Примітка (необов’язково)</string>
    <string name="character_count">%1$d / %2$d</string>
    <string name="save_entry">Зберегти запис</string>
    <string name="entry_not_found">Цього запису вже немає. Поверніться до огляду.</string>
    <string name="done">Готово</string>
    <string name="cancel">Скасувати</string>
    <string name="discard_title">Скасувати зміни?</string>
    <string name="discard_body">Незбережені зміни буде втрачено.</string>
    <string name="discard">Відхилити</string>
    <string name="keep_editing">Продовжити редагування</string>
    <string name="profile">Ваш профіль</string>
    <string name="local_profile">Локальний профіль, а не онлайн-акаунт.</string>
    <string name="display_name">Ім’я для відображення</string>
    <string name="save_profile">Зберегти профіль</string>
    <string name="name_invalid">Використайте не більше 40 символів.</string>
    <string name="language">Мова</string>
    <string name="language_instant">Зміни застосовуються одразу. Перезапуск не потрібен.</string>
    <string name="language_en">English</string>
    <string name="language_uk">Українська</string>
    <string name="appearance">Оформлення</string>
    <string name="theme_system">Системне</string>
    <string name="theme_light">Світле</string>
    <string name="theme_dark">Темне</string>
    <string name="default_currency">Типова валюта</string>
    <string name="currency_explanation">Оберіть валюту для нових записів і огляду. Баланси ведуться окремо для кожної валюти; перемикання не змінює й не конвертує попередні записи.</string>
    <string name="security">Безпека</string>
    <string name="biometric_unlock">Біометричне розблокування</string>
    <string name="biometric_explanation">Використовуйте надійний відбиток пальця чи розпізнавання обличчя. PIN-код також залишається доступним.</string>
    <string name="biometric_unavailable">Додайте підтримувану надійну біометрію в налаштуваннях Android, а потім увімкніть її тут.</string>
    <string name="auto_lock_explanation">Застосунок блокується після переходу у фон або вимкнення екрана. Знімки екрана та попередні перегляди захищено.</string>
    <string name="lock_now">Заблокувати зараз</string>
    <string name="premium_name">TG Plus</string>
    <string name="premium_body">Довічний Plus відкриває розширену статистику, весь час, деталі категорій та помічника Скай. Базовий облік — безкоштовний.</string>
    <string name="premium_active">Plus активовано</string>
    <string name="premium_offline_notice">Доступ працює без інтернету до 7 днів після перевірки. Підключіться до Google Play, щоб оновити перевірку.</string>
    <string name="premium_unlock">Купити довічний Plus</string>
    <string name="premium_buy">Довічний Plus · %1$s</string>
    <string name="restore_purchases">Відновити покупки</string>
    <string name="billing_configuration">Покупки недоступні в цій збірці. Усі безкоштовні функції працюють.</string>
    <string name="billing_unavailable">Google Play недоступний або цей продукт недоступний для вашого акаунта. Після відновлення зв’язку спробуйте відновити покупки.</string>
    <string name="billing_network">Час очікування Google Play вичерпано. Перевірте з’єднання та спробуйте ще раз.</string>
    <string name="billing_pending">Покупка очікує підтвердження. Plus відкриється після завершення та перевірки оплати.</string>
    <string name="billing_verification">Не вдалося перевірити покупку. Новий доступ не надано. Якщо кошти списано, зверніться до підтримки.</string>
    <string name="billing_acknowledgement">Покупку перевірено. Підтвердження Google Play буде повторено; підключіться до інтернету або відновіть покупки.</string>
    <string name="reference_rates">Довідкові курси</string>
    <string name="rates_disclaimer">Збережені курси Національного банку України, а не ціни торгів у реальному часі. Вони не конвертують записи й не змінюють баланси.</string>
    <string name="rates_empty">Збережених курсів ще немає. Облік однаково працює без інтернету.</string>
    <string name="rate_value">1 %1$s = %2$s UAH</string>
    <string name="rates_as_of">Опубліковано на %1$s</string>
    <string name="refresh_rates">Оновити курси</string>
    <string name="refreshing">Оновлення…</string>
    <string name="rate_offline">Не вдалося оновити курси. Попередні збережені курси залишилися без змін.</string>
    <string name="support">Підтримка</string>
    <string name="support_intro">Є запитання чи помітили проблему? Зверніться до головного розробника безпосередньо в Telegram.</string>
    <string name="contact_telegram">Звернутися до підтримки в Telegram</string>
    <string name="diagnostics_show_button">Показати останній звіт про збій</string>
    <string name="diagnostics_none">З моменту останнього встановлення або оновлення додатка збоїв не зафіксовано.</string>
    <string name="diagnostics_title">Останній звіт про збій</string>
    <string name="diagnostics_hint">Тут немає персональних даних — лише технічна помилка та місце, де вона сталася. Скопійте й надішліть розробнику в Telegram, щоб точну причину можна було виправити.</string>
    <string name="diagnostics_copy">Скопіювати</string>
    <string name="diagnostics_copied">Звіт про збій скопійовано</string>
    <string name="diagnostics_clear">Очистити</string>
    <string name="diagnostics_close">Закрити</string>
    <string name="here_to_help">Ми тут, щоб допомогти.</string>
    <string name="lead_developer">Головний розробник</string>
    <string name="support_handle">Офіційний розробник у Telegram</string>
    <string name="copy_handle">Скопіювати ім’я користувача Telegram</string>
    <string name="handle_copied">Ім’я користувача скопійовано</string>
    <string name="support_privacy">Telegram відкривається лише за вашим вибором. Записи, PIN-код чи діагностика не додаються автоматично.</string>
    <string name="telegram_support">Підтримка в Telegram</string>
    <string name="support_network_error">Не вдалося відкрити Telegram. Перевір з’єднання або скористайся кнопкою зв’язку.</string>
    <string name="help_heading">Трохи корисних порад.</string>
    <string name="help_intro">Налаштуйте облік під себе, запис за записом.</string>
    <string name="help_entries_title">Керування записами</string>
    <string name="help_entries_body">Натисніть «Додати запис», щоб записати дохід чи витрату. Торкніться запису для редагування. Змахніть ліворуч або натисніть кнопку видалення; ненадовго з’явиться можливість скасування. Пошук фільтрує назви й примітки в обраній валюті. Натисніть «Показати більше записів», щоб завантажити старіші збіги.</string>
    <string name="help_privacy_title">Ваші дані та приватність</string>
    <string name="help_privacy_body">Записи зберігаються в ізольованому сховищі застосунку Android. База Room не має окремого шифрування SQLCipher; PIN-код захищає доступ до застосунку. Автоматичне резервне копіювання й перенесення даних вимкнено. Видалення застосунку чи очищення сховища видаляє локальні дані. Записи та PIN-коди нікуди не надсилаються. Курси запитуються в НБУ, покупки — у Google Play, а Telegram відкривається лише за вашою дією. Реклами й аналітичних SDK немає.</string>
    <string name="help_security_title">PIN-код і біометрія</string>
    <string name="help_security_body">PIN-код зберігається як повільно обчислюваний перевірочний хеш із сіллю, зашифрований ключем Android Keystore. Неправильні спроби спричиняють дедалі довші затримки. Надійна біометрія використовує окремий ключ для кожної перевірки; після зміни біометричних даних він стає недійсним. Увійдіть за PIN-кодом, вимкніть і знову увімкніть біометрію. Підтримка не може відновити забутий PIN-код або втрачені ключі пристрою.</string>
    <string name="help_premium_title">Покупки й офлайн-доступ</string>
    <string name="help_premium_body">TG Plus у Google Play — довічна покупка без автоматичного продовження. Незавершений платіж не відкриває доступ. Відновлення — з тим самим Google Play акаунтом. Покупки Play потребують періодичної перевірки власності та працюють до 7 днів офлайн. Код розробника може бути довічним або строковим. Локальний пробний доступ надається один раз на 7 АБО 14 днів — без платіжних даних і списань.</string>
    <string name="app_version">Версія %1$s</string>
    <string name="premium_feature_trends">Рух коштів за 30 днів і весь час із деталями категорій</string>
    <string name="premium_feature_mascot">Власний персонаж-помічник на головному екрані</string>
    <string name="premium_feature_support">Пріоритетна увага розробника в Telegram</string>
    <string name="premium_source_play">Активовано через Google Play</string>
    <string name="premium_source_developer">Активовано кодом активації від розробника</string>
    <string name="premium_expires">Активація дійсна до %1$s</string>
    <string name="premium_assistant_toggle">Показувати персонажа-помічника</string>
    <string name="premium_assistant_toggle_hint">Увімкніть або вимкніть маскота на головному екрані. Доступно лише з активною підпискою Plus.</string>
    <string name="developer_purchase_title">Купити безпосередньо в розробника</string>
    <string name="developer_purchase_body">Купи довічний Plus напряму: зв’яжися з розробником у Telegram, узгодь ціну, сплати та отримай підписаний довічний код для свого ID встановлення.</string>
    <string name="developer_purchase_banner">Пряма довічна активація · лише офіційний розробник</string>
    <string name="developer_purchase_note">Примітка: згадайте TG Plus і свій ідентифікатор встановлення, коли пишете розробнику щодо покупки.</string>
    <string name="developer_purchase_button">Написати розробнику в Telegram</string>
    <string name="installation_id_label">Ваш ідентифікатор встановлення</string>
    <string name="installation_id_copy">Скопіювати ідентифікатор встановлення</string>
    <string name="installation_id_copied">Ідентифікатор встановлення скопійовано</string>
    <string name="activation_code_label">Код активації</string>
    <string name="activation_code_hint">Вставте код, який надіслав розробник</string>
    <string name="activation_code_button">Активувати TG Plus</string>
    <string name="billing_activation_code">Цей код активації недійсний, застарів або виданий для іншого пристрою.</string>
    <string name="premium_activated">TG Plus активовано. Користуйтеся новими можливостями.</string>
    <string name="activation_invalid">Не вдалося підтвердити цей код активації. Перевірте його або зверніться до розробника в Telegram.</string>
    <string name="mascot_card_title">Скай · твій помічник</string>
    <string name="mascot_hear_again">Повторити</string>
    <string name="mascot_premium_note">Видно лише коли активний TG Plus.</string>
    <string name="voice_assistant_button">Поговорити з помічником</string>
    <string name="voice_assistant_hint">Торкнися Скай, щоб говорити, або напиши. Наприклад: Скай, витрати за місяць за продукти.</string>
    <string name="voice_assistant_listening">Слухаю\u2026</string>
    <string name="voice_entry_added">Додано %1$s на %2$s: %3$s (%4$s).</string>
    <string name="voice_reply_balance">Ваш баланс: %1$s</string>
    <string name="voice_reply_spent_today">Сьогодні ви витратили %1$s</string>
    <string name="voice_reply_opening_entry">Відкриваю новий запис</string>
    <string name="voice_reply_not_understood">Я не зміг упевнено зрозуміти запит. Запитай про баланс або витрати за місяць, чи опиши одну покупку із сумою.</string>
    <string name="voice_smalltalk_greeting">Привіт! Розкажи, що ти купив або отримав, або запитай про баланс.</string>
    <string name="voice_smalltalk_how_are_you">У мене все гаразд, дякую! З чим допомогти тобі з грошима?</string>
    <string name="voice_smalltalk_thanks">Завжди будь ласка!</string>
    <string name="voice_smalltalk_who_are_you">Я Скай, твій фінансовий помічник. Порахую витрати за періодом і категорією або запишу покупку. Зараз я розумію запити локально за структурованими правилами.</string>
    <string name="voice_smalltalk_bye">Бувай! Скажеш слово -- я тут.</string>
    <string name="voice_smalltalk_compliment">Ой, дякую! Мяу.</string>
    <string name="voice_smalltalk_joke">Мій план заощаджень? Сховати гаманець під лапками. Мяу!</string>
    <string name="voice_permission_denied">Для голосового помічника потрібен доступ до мікрофона</string>
    <string name="voice_recognizer_unavailable">На цьому пристрої не знайдено додатку розпізнавання мови, тому голосові команди недоступні. Персонаж-помічник і все інше працюють як завжди.</string>
    <string name="all_time">За весь час</string>
    <string name="categories">Категорії · натисни для деталей</string>
    <string name="all_categories">усі категорії</string>
    <string name="category_transaction_count">Транзакцій: %1$d</string>
    <string name="category_empty">У цій категорії немає транзакцій за вибраний період.</string>
    <string name="period_today">сьогодні</string>
    <string name="period_month">поточний місяць</string>
    <string name="period_year">поточний рік</string>
    <string name="voice_statistics">%1$s · %2$s · %3$s: %4$s</string>
    <string name="assistant_text_hint">Напиши запит Скай</string>
    <string name="assistant_send">Надіслати Скай</string>
    <string name="premium_source_trial">Пробний доступ · без автоматичних списань</string>
    <string name="premium_local_notice">Доступ перевіряється локально. Без автоматичного продовження чи списань.</string>
    <string name="trial_explanation">Вибери ОДИН пробний період: 7 або 14 днів. Без платіжних даних, списань і автоматичного продовження. Після завершення базовий облік залишається безкоштовним.</string>
    <string name="trial_7">Безкоштовно · 7 днів</string>
    <string name="trial_14">Безкоштовно · 14 днів</string>
    <string name="mascot_style">Вигляд помічника</string>
    <string name="mascot_gray">Сірий кіт</string>
    <string name="mascot_sky">Небесний кіт</string>
    <string name="mascot_animations">Жива анімація кота (дихання, моргання та емоції)</string>
    <string name="brand_intro_title">Твої гроші. Твій простір.</string>
    <string name="brand_intro_body">Спокійний простір для твоїх фінансів. Авторський дизайн, створений із увагою до деталей.</string>
    <string name="brand_design_label">Авторський фінансовий щоденник</string>
    <string name="analytics_title">Аналітика</string>
    <string name="cinematic_history">Історія</string>
    <string name="cinematic_period">Вибрати місяць</string>
    <string name="cinematic_apply">Застосувати</string>
    <string name="cinematic_more">Більше ›</string>
    <string name="cinematic_expenses_period">Витрати · %1$s</string>
    <string name="cinematic_comparison">Різниця: %1$s</string>
    <string name="cinematic_more_than_previous">Більше, ніж за відповідний період попереднього місяця</string>
    <string name="cinematic_less_than_previous">Менше або без змін проти відповідного періоду попереднього місяця</string>
    <string name="cinematic_week_average">Середні витрати за показаний тиждень</string>
    <string name="cinematic_month_average">Середні витрати за активний місяць</string>
    <string name="cinematic_expense_categories">Категорії витрат</string>
    <string name="cinematic_income_categories">Категорії надходжень</string>
    <string name="cinematic_transactions">Транзакції · натисни для редагування</string>
    <string name="cinematic_tooltip">З %1$s до %2$s: %3$s</string>
    <string name="cinematic_chart_accessibility">Витрати за тижнями. Натисни стовпчик або дату для точної суми.</string>
    <string name="cinematic_more_buckets">Груп: %1$d. Натисни стовпчик для точних дат і сум.</string>
    <string name="cat_asset_status">3D-ресурс ще не встановлено · ресурсний preview</string>
    <string name="fps_title">Плавність · частота екрана</string>
    <string name="fps_explanation">Вибери 60, 90 або 120 для свого телефона. Недоступні режими вимкнені. Це запит частоти дисплея; фактичні FPS залежать від Android і швидкості рендерингу.</string>
    <string name="fps_auto">Авто</string>
    <string name="fps_value">%1$d FPS / Гц</string>
    <string name="fps_supported">Заявлені підтримувані режими: %1$s Гц</string>
    <string name="fps_fallback">Цей телефон не підтримує збережений режим. Застосовано доступний нижчий режим.</string>
    <string name="cat_tap_hint">Торкнися Скай та говори. Я поруч, коли потрібен.</string>
    <string name="category_transfer">Перекази</string>
    <string name="category_dining">Ресторани та кафе</string>
    <string name="category_cashback">Кешбек</string>
    <string name="reference_total_for">Усього за %1$s</string>
    <string name="total_all_time">Усього за весь час</string>
    <string name="total_thirty_days">Усього за останні 30 днів</string>
    <string name="total_seven_days">Усього за останні 7 днів</string>
    <string name="colors_title">Кольори й категорії</string>
    <string name="colors_explanation">Типово: синій акцент, зелені доходи та червоні витрати. Змінюйте кольори інтерфейсу й кожної категорії окремо. Зміни зберігаються автоматично.</string>
    <string name="colors_edit">Налаштувати кольори</string>
    <string name="colors_collapse">Згорнути палітру</string>
    <string name="colors_categories">Кольори категорій за типом операції</string>
    <string name="colors_reset_all">Скинути всі кольори</string>
    <string name="color_accent">Акцент / кнопки</string>
    <string name="color_background">Фон екрана</string>
    <string name="color_surface">Картки й панелі</string>
    <string name="color_text">Основний текст</string>
    <string name="color_muted">Допоміжний текст</string>
    <string name="color_chart_top">Градієнт графіка — верх</string>
    <string name="color_chart_bottom">Градієнт графіка — низ</string>
    <string name="color_grid">Сітка графіка / роздільники</string>
    <string name="color_icons">Брендові та інтерфейсні значки</string>
    <string name="color_hue">Відтінок</string>
    <string name="color_saturation">Насиченість</string>
    <string name="color_brightness">Яскравість</string>
    <string name="color_apply">Застосувати колір</string>
    <string name="color_reset">Скинути цей колір</string>
    <string name="color_invalid">Введіть 6 HEX-цифр, наприклад #6FAEFF.</string>
    <string name="color_contrast_warning">Низький контраст із поточним фоном. Цей відтінок може бути погано видимим.</string>
    <string name="color_error">Помилки й попередження</string>
    <string name="bank_dashboard">Банки</string>
    <string name="bank_import_title">Локальний імпорт банківських операцій</string>
    <string name="bank_consent">Добровільно. Обробляються лише банківські застосунки та банківські SMS-відправники. Увімкніть імпорт і надайте доступ. Типово всі операції потребують підтвердження.</string>
    <string name="bank_enabled">Увімкнути банківський імпорт</string>
    <string name="bank_auto">Автоімпорт однозначних операцій</string>
    <string name="bank_access_yes">Доступ до сповіщень надано</string>
    <string name="bank_access_no">Доступ до сповіщень не надано</string>
    <string name="bank_notification_permission">Керувати доступом до сповіщень</string>
    <string name="bank_sms_yes">Доступ до вхідних SMS надано</string>
    <string name="bank_sms_no">Доступ до вхідних SMS не надано</string>
    <string name="bank_sms_permission">Дозволити вхідні банківські SMS</string>
    <string name="bank_play_sms">Play-збірка: лише імпорт сповіщень. Прийом SMS є в окремій збірці для встановлення поза Play; можливі обмеження пристрою або інсталятора.</string>
    <string name="bank_privacy">Приватність імпорту</string>
    <string name="bank_privacy_body">Розпізнавання банківських повідомлень відбувається на пристрої. Повні тексти, OTP-коди й повні номери карток не зберігаються та не надсилаються на сервери. Room зберігає виділені операції та записи підтвердження у приватному сховищі; системні резервні копії вимкнено. Зберігаються лише останні 4 цифри картки. Вимкнення імпорту зупиняє нові записи; для скасування системного доступу відкличте дозволи Android. CSV-експорт виконується лише за вашою дією; обрана папка може належати хмарному провайдеру. PIN блокує застосунок, але не шифрує базу. Курси валют, Play і системне розпізнавання голосу — окремі функції, які можуть використовувати мережу. Імпорт не є підтвердженою банком випискою.</string>
    <string name="bank_day">Сьогодні</string>
    <string name="bank_week">Останні 7 днів</string>
    <string name="bank_month">Цей місяць</string>
    <string name="bank_cash">Готівка</string>
    <string name="bank_card">Банківська картка</string>
    <string name="bank_unknown">Невідоме джерело (старі записи)</string>
    <string name="bank_source">Джерело коштів</string>
    <string name="bank_category_stats">Категорії витрат за джерелом</string>
    <string name="bank_new_category">Створити власну категорію</string>
    <string name="bank_review_count">Потребують підтвердження: %1$d</string>
    <string name="bank_time_received">Час отримання або ручного введення: точний час операції не вказано.</string>
    <string name="bank_duplicate">Можливий дублікат. Підтверджуйте лише якщо це окрема операція.</string>
    <string name="bank_transfer_review">Переказ: ігноруйте рух коштів між власними рахунками. Підтверджуйте лише зовнішні витрати або доходи.</string>
    <string name="bank_ignore">Ігнорувати / між своїми рахунками</string>
    <string name="bank_recent">Останні операції · натисніть для редагування</string>
    <string name="bank_limit_recent">Тут показано до 100 останніх операцій. Підсумки враховують усі записи; основний журнал має пошук і пагінацію.</string>
    <string name="bank_budget">Місячний ліміт витрат</string>
    <string name="bank_budget_progress">Витрати за місяць: %1$s / %2$s</string>
    <string name="bank_export">Експортувати всі операції у CSV</string>
    <string name="bank_clear_history">Очистити історію розпізнавання</string>
    <string name="bank_clear_warning">Чергу підтвердження та відбитки дублікатів буде видалено. Збережені операції залишаться. Після очищення старі сповіщення можуть бути розпізнані знову.</string>
    <string name="bank_app_permissions">Дозволи застосунку Android</string>
    <string name="bank_budget_disable">Вимкнути місячний ліміт</string>
    <string name="history_search">Пошук операцій</string>
    <string name="import_diagnostics">Діагностика імпорту</string>
    <string name="import_connected">Сервіс сповіщень підключено</string>
    <string name="import_disconnected">Сервіс сповіщень не підключено</string>
    <string name="import_counts">Отримано сповіщень: %1$d · банківських: %2$d</string>
    <string name="import_reconnect">Перепідключити сервіс сповіщень</string>
    <string name="import_package_settings">Ідентифікатори банківських застосунків</string>
    <string name="import_package_warning">Укажіть точний package встановленого банківського застосунку. Не призначайте месенджери чи сторонні застосунки. Останній package показано для діагностики; тексти невідомих сповіщень не читаються.</string>
    <string name="import_paste">Вставити банківське повідомлення</string>
    <string name="import_text">Текст банківського повідомлення</string>
    <string name="import_manual_privacy">Текст обробляється локально й не зберігається. Виділені поля потраплять на підтвердження, навіть якщо автоімпорт увімкнено.</string>
    <string name="import_to_queue">На підтвердження</string>
    <string name="import_queue_note">Режим перевірки: операції з’являються нижче в черзі підтвердження. Баланс змінюється лише після підтвердження. Якщо текст не розпізнано, скористайтеся ручним введенням.</string>
    <string name="import_disabled">Імпорт вимкнено. Увімкніть його перед перевіркою нових сповіщень.</string>
    <string name="import_unsupported">Останнє сповіщення від неналаштованого застосунку; його текст проігноровано. Перевірте package банку.</string>
    <string name="import_empty">Android не надав читабельного тексту сповіщення.</string>
    <string name="import_group">Групову зведену картку проігноровано; розпізнаються окремі сповіщення.</string>
    <string name="import_unrecognized">Підтримувану операцію не розпізнано. OTP, лише баланс і відхилені платежі ігноруються. Використайте ручне введення або знеособлений приклад для налаштування парсера.</string>
    <string name="import_auto_done">Операцію збережено автоматично.</string>
    <string name="import_queued">Операція очікує підтвердження нижче.</string>
    <string name="import_repeated">Повторну доставку проігноровано; дублікат не збережено.</string>
    <string name="import_waiting">Очікування нового банківського сповіщення. Минулі сповіщення не скануються.</string>
</resources>
```

## app/src/main/res/values/branding.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Author-provided alias. Edit these two resources to your preferred signature. -->
    <string name="brand_author_signature" translatable="false">S.</string>
    <string name="brand_copyright" translatable="false">© 2026 S. · TG Ledger</string>
</resources>
```

## app/src/main/res/values/strings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">TG Ledger</string>
    <string name="overview">Overview</string>
    <string name="settings">Settings</string>
    <string name="help">Help</string>
    <string name="back">Back</string>
    <string name="retry">Try again</string>
    <string name="next">Continue</string>
    <string name="skip">Skip</string>
    <string name="get_started">Create my ledger</string>
    <string name="page_of">%1$d / %2$d</string>
    <string name="onboard_title_one">Your money.\nA clearer picture.</string>
    <string name="onboard_body_one">Track income and everyday spending in one calm, organized space. See your balance change as you go.</string>
    <string name="onboard_title_two">Private by design.</string>
    <string name="onboard_body_two">Protect access with a six-digit PIN and optional strong biometrics. Your personal ledger stays on this device.</string>
    <string name="onboard_title_three">Life happens offline.</string>
    <string name="onboard_body_three">Add, edit and explore your entries without an internet connection. No account, no ads, no unnecessary noise.</string>
    <string name="unlock_title">Welcome back</string>
    <string name="confirm_pin">Confirm your PIN</string>
    <string name="create_pin">Create a secure PIN</string>
    <string name="pin_setup_subtitle">Choose six digits, then enter them again.</string>
    <string name="pin_unlock_subtitle">Enter your six-digit PIN to continue.</string>
    <string name="pin_digit_count">%1$d of 6 PIN digits entered</string>
    <string name="pin_locked">Too many attempts. Try again in %1$d s.</string>
    <string name="pin_incorrect">Incorrect PIN. Please try again.</string>
    <string name="pin_mismatch">The PINs did not match. Choose your PIN again.</string>
    <string name="pin_recovery_notice">Keep your PIN safe. There is no remote reset. Clearing app storage removes all local entries.</string>
    <string name="erase_digit">Erase last digit</string>
    <string name="start_over">Choose another PIN</string>
    <string name="need_help">Need help?</string>
    <string name="security_error">Protected data could not be opened. Try again. If device keys were reset, local data cannot be recovered; clearing app storage will erase your ledger.</string>
    <string name="biometric_failed">Biometric unlock failed. Use your PIN. If enrollment changed, enable biometrics again in Settings.</string>
    <string name="unlock_biometric">Unlock with biometrics</string>
    <string name="biometric_title">Unlock TG Ledger</string>
    <string name="biometric_subtitle">Verify your identity to open your ledger.</string>
    <string name="use_pin">Use PIN</string>
    <string name="load_error_title">Something needs attention</string>
    <string name="storage_error">Could not read or save local data. Check available storage and try again.</string>
    <string name="saved">Saved</string>
    <string name="entry_deleted">Entry deleted</string>
    <string name="undo">Undo</string>
    <string name="undo_expired">This deletion can no longer be undone.</string>
    <string name="dashboard_heading">Your money, clearly.</string>
    <string name="greeting">Hello, %1$s</string>
    <string name="dashboard_subtitle">A little clarity for every day.</string>
    <string name="total_balance">Total balance</string>
    <string name="currency_scope">All time · %1$s entries only</string>
    <string name="income">Income</string>
    <string name="expenses">Expenses</string>
    <string name="expense">Expense</string>
    <string name="cash_flow">Cash flow</string>
    <string name="last_7_days">7 days</string>
    <string name="last_30_days">30 days</string>
    <string name="last_30_days_plus">30 days · Plus</string>
    <string name="chart_description">Cash flow over %1$d days: income %2$s, expenses %3$s. Individual entries are listed below.</string>
    <string name="chart_empty">Your trend starts with your first entry in this period.</string>
    <string name="legend_income">Income · left bars · %1$s</string>
    <string name="legend_expense">Expenses · right bars · %1$s</string>
    <string name="activity">Recent activity</string>
    <string name="search_entries">Search titles and notes</string>
    <string name="clear_search">Clear search</string>
    <string name="all">All</string>
    <string name="search_scope">Filters affect this list, not your overall balance or chart.</string>
    <string name="no_matches">No matching entries</string>
    <string name="no_matches_body">Try another search or clear the filters.</string>
    <string name="clear_filters">Clear filters</string>
    <string name="empty_ledger_title">A fresh start</string>
    <string name="empty_ledger_body">Add income or an expense in this currency to start building your picture.</string>
    <string name="add_first_entry">Add your first entry</string>
    <string name="add_entry">Add entry</string>
    <string name="edit_entry">Edit entry</string>
    <string name="delete_entry">Delete entry</string>
    <string name="delete_named_entry">Delete %1$s</string>
    <string name="load_more">Show more entries</string>
    <string name="positive_amount">+%1$s</string>
    <string name="negative_amount">−%1$s</string>
    <string name="category_food">Groceries &amp; food</string>
    <string name="category_transport">Transport</string>
    <string name="category_home">Home</string>
    <string name="category_health">Health</string>
    <string name="category_leisure">Leisure</string>
    <string name="category_salary">Salary</string>
    <string name="category_other">Other</string>
    <string name="entry_currency_notice">Currency: %1$s. Existing entries keep their original currency. Change the default in Settings before adding a new entry.</string>
    <string name="amount">Amount</string>
    <string name="amount_invalid">Enter 0.01–99,999,999.99 with at most two decimal places; do not use thousands separators.</string>
    <string name="entry_title">Title</string>
    <string name="title_invalid">Enter a title of 1–60 characters.</string>
    <string name="title_hint">For example: groceries or salary</string>
    <string name="category">Category</string>
    <string name="date_invalid">Choose a date between January 1, 2000 and today.</string>
    <string name="note_optional">Note (optional)</string>
    <string name="character_count">%1$d / %2$d</string>
    <string name="save_entry">Save entry</string>
    <string name="entry_not_found">This entry no longer exists. Return to your overview.</string>
    <string name="done">Done</string>
    <string name="cancel">Cancel</string>
    <string name="discard_title">Discard changes?</string>
    <string name="discard_body">Your unsaved changes will be lost.</string>
    <string name="discard">Discard</string>
    <string name="keep_editing">Keep editing</string>
    <string name="profile">Your profile</string>
    <string name="local_profile">A local profile, not an online account.</string>
    <string name="display_name">Display name</string>
    <string name="save_profile">Save profile</string>
    <string name="name_invalid">Use 40 characters or fewer.</string>
    <string name="language">Language</string>
    <string name="language_instant">Changes appear immediately. No restart needed.</string>
    <string name="language_en">English</string>
    <string name="language_uk">Українська</string>
    <string name="appearance">Appearance</string>
    <string name="theme_system">System</string>
    <string name="theme_light">Light</string>
    <string name="theme_dark">Dark</string>
    <string name="default_currency">Default currency</string>
    <string name="currency_explanation">Choose the currency for new entries and the dashboard. Balances are separate by currency; switching never relabels or converts old entries.</string>
    <string name="security">Security</string>
    <string name="biometric_unlock">Biometric unlock</string>
    <string name="biometric_explanation">Use an enrolled strong fingerprint or face method. Your PIN remains available.</string>
    <string name="biometric_unavailable">Enroll a supported strong biometric in Android settings, then enable it here.</string>
    <string name="auto_lock_explanation">Ledger locks when it goes into the background or the screen turns off. Screenshots and recent-app previews are protected.</string>
    <string name="lock_now">Lock now</string>
    <string name="premium_name">TG Plus</string>
    <string name="premium_body">Lifetime Plus unlocks extended and all-time statistics, category details and Sky assistant. Core tracking stays free.</string>
    <string name="premium_active">Plus is active</string>
    <string name="premium_offline_notice">Access works offline for up to 7 days after verification. Connect to Google Play to renew verification.</string>
    <string name="premium_unlock">Buy lifetime Plus</string>
    <string name="premium_buy">Lifetime Plus · %1$s</string>
    <string name="restore_purchases">Restore purchases</string>
    <string name="billing_configuration">Purchases are not enabled in this build. All free features remain available.</string>
    <string name="billing_unavailable">Google Play is unavailable, or this product is not available for this account. Try restoring purchases after reconnecting.</string>
    <string name="billing_network">The Play connection timed out. Check your connection and try again.</string>
    <string name="billing_pending">Your purchase is pending. Plus unlocks only after payment is completed and verified.</string>
    <string name="billing_verification">This purchase could not be verified. No new access was granted. Contact support if you were charged.</string>
    <string name="billing_acknowledgement">Purchase verified. Google Play confirmation will be retried; please reconnect or restore purchases.</string>
    <string name="reference_rates">Reference rates</string>
    <string name="rates_disclaimer">Cached National Bank of Ukraine rates, not live trading prices. They never convert your entries or change balances.</string>
    <string name="rates_empty">No cached rates yet. Your ledger still works offline.</string>
    <string name="rate_value">1 %1$s = %2$s UAH</string>
    <string name="rates_as_of">Published for %1$s</string>
    <string name="refresh_rates">Refresh rates</string>
    <string name="refreshing">Refreshing…</string>
    <string name="rate_offline">Rates could not be refreshed. Any previously cached rates are unchanged.</string>
    <string name="support">Support</string>
    <string name="support_intro">Questions or a rough edge? Contact the lead developer directly on Telegram.</string>
    <string name="contact_telegram">Contact Support via Telegram</string>
    <string name="diagnostics_show_button">Show last crash report</string>
    <string name="diagnostics_none">No crash has been recorded since the app was last installed or updated.</string>
    <string name="diagnostics_title">Last crash report</string>
    <string name="diagnostics_hint">This contains no personal data — only the technical error and where it happened. Copy it and send it to the developer on Telegram so the exact cause can be fixed.</string>
    <string name="diagnostics_copy">Copy</string>
    <string name="diagnostics_copied">Crash report copied</string>
    <string name="diagnostics_clear">Clear</string>
    <string name="diagnostics_close">Close</string>
    <string name="here_to_help">Here to help.</string>
    <string name="lead_developer">Lead developer</string>
    <string name="support_handle">Official developer on Telegram</string>
    <string name="copy_handle">Copy Telegram username</string>
    <string name="handle_copied">Username copied</string>
    <string name="support_privacy">Telegram opens only when you choose to contact support. No ledger data, PIN or diagnostics are attached automatically.</string>
    <string name="telegram_support">Telegram support</string>
    <string name="support_network_error">Could not open Telegram. Check your connection or use the contact button.</string>
    <string name="help_heading">A little guidance.</string>
    <string name="help_intro">Make Ledger work for you, one entry at a time.</string>
    <string name="help_entries_title">Managing your entries</string>
    <string name="help_entries_body">Tap Add entry to record income or spending. Tap an entry to edit it. Swipe left or use its delete button to remove it; Undo appears briefly. Search filters titles and notes in the selected currency. Use Show more to load older matches.</string>
    <string name="help_privacy_title">Your data &amp; privacy</string>
    <string name="help_privacy_body">Entries stay in the Android app sandbox. The Room database is not separately SQLCipher-encrypted; the PIN protects app access. Automatic backup and device transfer are disabled. Uninstalling or clearing storage deletes local data. No entries or PINs are uploaded. Public rates contact NBU; purchases contact Google Play; support opens Telegram only on your action. There are no ads or analytics SDKs.</string>
    <string name="help_security_title">PIN &amp; biometric access</string>
    <string name="help_security_body">Your PIN is stored only as a salted, slow verifier encrypted with an Android Keystore key. Repeated wrong PINs trigger increasing delays. Strong biometrics use a separate per-use key invalidated when enrollment changes. Use your PIN, then disable and re-enable biometrics after an enrollment change. Support cannot recover a forgotten PIN or lost device keys.</string>
    <string name="help_premium_title">Purchases &amp; offline access</string>
    <string name="help_premium_body">TG Plus on Google Play is a non-consumable lifetime purchase, not an auto-renewing subscription. Pending payments never unlock access. Restore with the same Google Play account. Signed Play purchases require periodic ownership verification and allow up to 7 days offline. Developer codes may be lifetime or time-limited. A local trial is available once for 7 OR 14 days, without payment details or charges.</string>
    <string name="app_version">Version %1$s</string>
    <string name="premium_feature_trends">30-day and all-time cash flow with category details</string>
    <string name="premium_feature_mascot">Your own assistant character on the dashboard</string>
    <string name="premium_feature_support">Priority attention from the developer on Telegram</string>
    <string name="premium_source_play">Active via Google Play</string>
    <string name="premium_source_developer">Active via a developer activation code</string>
    <string name="premium_expires">Activation valid until %1$s</string>
    <string name="premium_assistant_toggle">Show assistant character</string>
    <string name="premium_assistant_toggle_hint">Turn the dashboard mascot on or off. Only visible with an active Plus subscription.</string>
    <string name="developer_purchase_title">Buy directly from the developer</string>
    <string name="developer_purchase_body">Buy lifetime Plus directly: contact the developer on Telegram to confirm the price, pay and receive a signed lifetime code for your installation ID.</string>
    <string name="developer_purchase_banner">Direct lifetime activation · official developer only</string>
    <string name="developer_purchase_note">Note: mention TG Plus and your installation ID when you write to the developer about your purchase.</string>
    <string name="developer_purchase_button">Message the developer on Telegram</string>
    <string name="installation_id_label">Your installation ID</string>
    <string name="installation_id_copy">Copy installation ID</string>
    <string name="installation_id_copied">Installation ID copied</string>
    <string name="activation_code_label">Activation code</string>
    <string name="activation_code_hint">Paste the code the developer sent you</string>
    <string name="activation_code_button">Activate TG Plus</string>
    <string name="billing_activation_code">This activation code is invalid, expired, or was issued for a different device.</string>
    <string name="premium_activated">TG Plus activated. Enjoy your new features.</string>
    <string name="activation_invalid">That activation code could not be verified. Double-check it or contact the developer on Telegram.</string>
    <string name="mascot_card_title">Sky · your assistant</string>
    <string name="mascot_hear_again">Hear that again</string>
    <string name="mascot_premium_note">Visible only while TG Plus is active.</string>
    <string name="voice_assistant_button">Talk to assistant</string>
    <string name="voice_assistant_hint">Tap Sky to speak, or type. Try: expenses this month for food.</string>
    <string name="voice_assistant_listening">Listening\u2026</string>
    <string name="voice_entry_added">Added %1$s of %2$s: %3$s (%4$s).</string>
    <string name="voice_reply_balance">Your balance is %1$s</string>
    <string name="voice_reply_spent_today">You spent %1$s today</string>
    <string name="voice_reply_opening_entry">Opening a new entry</string>
    <string name="voice_reply_not_understood">I could not confidently understand. Ask about your balance or expenses this month, or describe one purchase with its amount.</string>
    <string name="voice_smalltalk_greeting">Hi there! Tell me what you bought or earned, or ask about your balance.</string>
    <string name="voice_smalltalk_how_are_you">I\'m doing great, thanks for asking! How can I help with your money today?</string>
    <string name="voice_smalltalk_thanks">You\'re welcome!</string>
    <string name="voice_smalltalk_who_are_you">I am Sky, your finance companion. I can calculate expenses by period and category or log a purchase. My current understanding works locally with structured rules.</string>
    <string name="voice_smalltalk_bye">Bye! I\'ll be right here when you need me.</string>
    <string name="voice_smalltalk_compliment">Aw, thank you! Meow.</string>
    <string name="voice_smalltalk_joke">My savings strategy? Hide the wallet under my paws. Meow!</string>
    <string name="voice_permission_denied">Microphone access is needed for the voice assistant</string>
    <string name="voice_recognizer_unavailable">No speech recognition app found on this device, so voice commands aren\'t available. You can still use the mascot and everything else.</string>
    <string name="all_time">All time</string>
    <string name="categories">Categories · tap for details</string>
    <string name="all_categories">all categories</string>
    <string name="category_transaction_count">%1$d transactions</string>
    <string name="category_empty">No transactions in this category for this period.</string>
    <string name="period_today">today</string>
    <string name="period_month">current month</string>
    <string name="period_year">current year</string>
    <string name="voice_statistics">%1$s · %2$s · %3$s: %4$s</string>
    <string name="assistant_text_hint">Ask Sky by typing</string>
    <string name="assistant_send">Send to Sky</string>
    <string name="premium_source_trial">Free trial · no automatic charges</string>
    <string name="premium_local_notice">Access is checked locally. No automatic renewal or charges.</string>
    <string name="trial_explanation">Choose ONE trial: 7 or 14 days. No payment details, charges, or automatic renewal. After it ends, core tracking remains free.</string>
    <string name="trial_7">Try free · 7 days</string>
    <string name="trial_14">Try free · 14 days</string>
    <string name="mascot_style">Assistant appearance</string>
    <string name="mascot_gray">Gray cat</string>
    <string name="mascot_sky">Sky cat</string>
    <string name="mascot_animations">Live cat animation (breathing, blinking and expressions)</string>
    <string name="brand_intro_title">Your money. Your rules.</string>
    <string name="brand_intro_body">A quiet space for your finances. Made with care, for the way you live.</string>
    <string name="brand_design_label">An original finance journal</string>
    <string name="analytics_title">Analytics</string>
    <string name="cinematic_history">History</string>
    <string name="cinematic_period">Choose month</string>
    <string name="cinematic_apply">Apply</string>
    <string name="cinematic_more">More ›</string>
    <string name="cinematic_expenses_period">Expenses · %1$s</string>
    <string name="cinematic_comparison">Change: %1$s</string>
    <string name="cinematic_more_than_previous">Higher than the same period last month</string>
    <string name="cinematic_less_than_previous">Lower or unchanged vs the same period last month</string>
    <string name="cinematic_week_average">Average per displayed week</string>
    <string name="cinematic_month_average">Average per active month</string>
    <string name="cinematic_expense_categories">Expense categories</string>
    <string name="cinematic_income_categories">Income categories</string>
    <string name="cinematic_transactions">Transactions · tap to edit</string>
    <string name="cinematic_tooltip">From %1$s to %2$s: %3$s</string>
    <string name="cinematic_chart_accessibility">Expenses by week. Tap a bar or its date to show the exact sum.</string>
    <string name="cinematic_more_buckets">%1$d buckets. Tap any bar for exact dates and amounts.</string>
    <string name="cat_asset_status">3D asset not installed · resource preview</string>
    <string name="fps_title">Smoothness · screen frequency</string>
    <string name="fps_explanation">Choose 60, 90 or 120 for your phone. Unavailable modes are disabled. This requests a display refresh rate; actual FPS depends on Android and rendering performance.</string>
    <string name="fps_auto">Auto</string>
    <string name="fps_value">%1$d FPS / Hz</string>
    <string name="fps_supported">Reported supported modes: %1$s Hz</string>
    <string name="fps_fallback">This phone does not support the saved mode. A supported lower mode is used instead.</string>
    <string name="cat_tap_hint">Tap Sky and speak. I am listening when you need me.</string>
    <string name="category_transfer">Transfers</string>
    <string name="category_dining">Restaurants &amp; cafés</string>
    <string name="category_cashback">Cashback</string>
    <string name="reference_total_for">Total for %1$s</string>
    <string name="total_all_time">Total · all time</string>
    <string name="total_thirty_days">Total · last 30 days</string>
    <string name="total_seven_days">Total · last 7 days</string>
    <string name="colors_title">Colors and categories</string>
    <string name="colors_explanation">Blue brand accent, green income and red expenses by default. Customize interface roles and each category separately. Changes are saved automatically.</string>
    <string name="colors_edit">Customize colors</string>
    <string name="colors_collapse">Collapse palette</string>
    <string name="colors_categories">Category colors by transaction type</string>
    <string name="colors_reset_all">Reset all colors</string>
    <string name="color_accent">App accent / buttons</string>
    <string name="color_background">Screen background</string>
    <string name="color_surface">Cards and panels</string>
    <string name="color_text">Main text</string>
    <string name="color_muted">Secondary text</string>
    <string name="color_chart_top">Chart gradient — top</string>
    <string name="color_chart_bottom">Chart gradient — bottom</string>
    <string name="color_grid">Chart grid / dividers</string>
    <string name="color_icons">Brand and interface icons</string>
    <string name="color_hue">Hue</string>
    <string name="color_saturation">Saturation</string>
    <string name="color_brightness">Brightness</string>
    <string name="color_apply">Apply color</string>
    <string name="color_reset">Reset this color</string>
    <string name="color_invalid">Use six HEX digits, for example #6FAEFF.</string>
    <string name="color_contrast_warning">Low contrast with the current background. This shade may be difficult to see.</string>
    <string name="color_error">Errors and alerts</string>
    <string name="bank_dashboard">Banks</string>
    <string name="bank_import_title">Offline bank import</string>
    <string name="bank_consent">Optional. Only enabled bank apps and bank SMS senders are examined. Turn import on, then grant access. Review mode is the default.</string>
    <string name="bank_enabled">Enable bank import</string>
    <string name="bank_auto">Auto-import confident operations</string>
    <string name="bank_access_yes">Notification access granted</string>
    <string name="bank_access_no">Notification access not granted</string>
    <string name="bank_notification_permission">Manage notification access</string>
    <string name="bank_sms_yes">Incoming SMS access granted</string>
    <string name="bank_sms_no">Incoming SMS access not granted</string>
    <string name="bank_sms_permission">Allow incoming bank SMS</string>
    <string name="bank_play_sms">Play build: notification import only. SMS reception is included in the sideload variant; installer/device restrictions may apply.</string>
    <string name="bank_privacy">Import privacy policy</string>
    <string name="bank_privacy_body">Bank parsing happens on this device. Raw message texts, OTPs and full card numbers are not saved or sent to servers. Room stores extracted operations and review records in private app storage; system backup is disabled. Only the final four card digits are retained. Turning import off stops new ingestion; revoke notification/SMS access in Android settings to remove system access. CSV export is explicit and saves to the location you choose, which may be a cloud provider. PIN is an app lock, not database encryption. Optional exchange-rate, Play and system speech services are separate and may use the network. Imported statements are not bank-verified and must be reviewed.</string>
    <string name="bank_day">Today</string>
    <string name="bank_week">Last 7 days</string>
    <string name="bank_month">This month</string>
    <string name="bank_cash">Cash</string>
    <string name="bank_card">Bank card</string>
    <string name="bank_unknown">Unspecified source (older entries)</string>
    <string name="bank_source">Payment source</string>
    <string name="bank_category_stats">Expense categories by source</string>
    <string name="bank_new_category">Create custom category</string>
    <string name="bank_review_count">Needs confirmation: %1$d</string>
    <string name="bank_time_received">Time is receipt or manual input time; no exact operation timestamp was provided.</string>
    <string name="bank_duplicate">Possible duplicate. Confirm only if this is a separate operation.</string>
    <string name="bank_transfer_review">Transfer: ignore transfers between your own accounts. Confirm only external spending or income.</string>
    <string name="bank_ignore">Ignore / own-account transfer</string>
    <string name="bank_recent">Recent operations · tap to edit</string>
    <string name="bank_limit_recent">This view shows up to 100 recent operations. Totals include every matching entry; the main ledger has search and paging.</string>
    <string name="bank_budget">Monthly expense limit</string>
    <string name="bank_budget_progress">Monthly spending: %1$s / %2$s</string>
    <string name="bank_export">Export all transactions to CSV</string>
    <string name="bank_clear_history">Clear recognition history</string>
    <string name="bank_clear_warning">Pending reviews and duplicate fingerprints will be deleted. Saved transactions are kept. Old notifications could be recognized again after clearing.</string>
    <string name="bank_app_permissions">Android app permissions</string>
    <string name="bank_budget_disable">Disable monthly limit</string>
    <string name="history_search">Search operations</string>
    <string name="import_diagnostics">Import diagnostics</string>
    <string name="import_connected">Notification service is connected</string>
    <string name="import_disconnected">Notification service is not connected</string>
    <string name="import_counts">Notifications received: %1$d · bank messages: %2$d</string>
    <string name="import_reconnect">Reconnect notification service</string>
    <string name="import_package_settings">Bank app identifiers</string>
    <string name="import_package_warning">Use the exact package of your installed bank app. Do not assign messenger or unrelated apps. The last seen package is shown for diagnosis; unknown notification texts are not read.</string>
    <string name="import_paste">Paste bank message</string>
    <string name="import_text">Bank message text</string>
    <string name="import_manual_privacy">Text is processed locally and is not saved. Parsed fields go to confirmation, even when auto-import is on.</string>
    <string name="import_to_queue">Send to confirmation</string>
    <string name="import_queue_note">Review mode: operations appear below under Needs confirmation. They affect your balance only after confirmation. If parsing fails, use manual entry.</string>
    <string name="import_disabled">Import is off. Turn it on before testing new notifications.</string>
    <string name="import_unsupported">Last notification is from an unconfigured app; its text was ignored. Check the bank package mapping.</string>
    <string name="import_empty">Android provided no readable notification text.</string>
    <string name="import_group">Grouped summary ignored; only individual notifications are parsed.</string>
    <string name="import_unrecognized">No supported transaction detected. OTP, balance-only and declined messages are ignored. Use manual entry or supply a redacted format for parser testing.</string>
    <string name="import_auto_done">Operation saved automatically.</string>
    <string name="import_queued">Operation awaits confirmation below.</string>
    <string name="import_repeated">Repeated delivery ignored; no duplicate was saved.</string>
    <string name="import_waiting">Waiting for a new bank notification. Past notification history is not scanned.</string>
</resources>
```

## app/src/main/res/values/themes.xml

```xml
<resources>
    <style name="Theme.Ledger" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowActionModeOverlay">true</item>
        <item name="android:windowBackground">#F6F8FC</item>
    </style>
    <style name="Theme.Ledger.Starting" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#10223F</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_splash</item>
        <item name="windowSplashScreenAnimationDuration">400</item>
        <item name="postSplashScreenTheme">@style/Theme.Ledger</item>
    </style>
    <color name="launcher_background">#10223F</color>
</resources>
```

## app/src/main/res/xml/data_extraction_rules.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
    </device-transfer>
</data-extraction-rules>
```

## app/src/main/res/xml/network_security_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors><certificates src="system" /></trust-anchors>
    </base-config>
</network-security-config>
```

## app/src/test/kotlin/com/semmyx/ledger/presentation/MainDispatcherRule.kt

```kotlin
package com.semmyx.ledger.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) { Dispatchers.setMain(dispatcher) }
    override fun finished(description: Description) { Dispatchers.resetMain() }
}
```

## app/src/test/kotlin/com/semmyx/ledger/presentation/ViewModelTest.kt

```kotlin
package com.semmyx.ledger.presentation

import androidx.lifecycle.ViewModelStore
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.presentation.auth.AuthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @Test fun appStartsLockedAndLockClearsSession() = runTest {
        val preferences = TestPreferences()
        val model = AppViewModel(preferences, TestSecurity(configured = true))
        val owner = ViewModelStore().apply { put("app", model) }
        try {
            runCurrent()
            assertTrue(model.state.value.ready)
            assertFalse(model.state.value.unlocked)
            model.unlock(); assertTrue(model.state.value.unlocked)
            model.lock(); assertFalse(model.state.value.unlocked)
            assertEquals(1L, model.state.value.lockEpoch)
        } finally { owner.clear() }
    }
    @Test fun preferenceChangesFlowThroughWithoutReplacingTheViewModel() = runTest {
        val preferences = TestPreferences()
        val model = AppViewModel(preferences, TestSecurity(true))
        val owner = ViewModelStore().apply { put("app", model) }
        try {
            runCurrent()
            preferences.setLanguage(AppLanguage.UKRAINIAN)
            runCurrent()
            assertEquals(AppLanguage.UKRAINIAN, model.state.value.preferences.language)
        } finally { owner.clear() }
    }
    @Test fun setupRequiresTwoMatchingPins() = runTest {
        val security = TestSecurity(false)
        val model = AuthViewModel(true, security)
        val owner = ViewModelStore().apply { put("auth", model) }
        var unlocked = false
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.unlocked.collect { unlocked = true } }
        try {
            runCurrent()
            listOf(1, 2, 3, 4, 5, 6).forEach(model::digit)
            assertTrue(model.state.value.confirming)
            assertFalse(unlocked)
            listOf(1, 2, 3, 4, 5, 6).forEach(model::digit)
            runCurrent()
            assertTrue(unlocked)
            assertTrue(security.hasPin())
        } finally { collector.cancel(); owner.clear() }
    }
}

private class TestSecurity(private var configured: Boolean) : SecurityRepository {
    private var code = "123456"
    override suspend fun hasPin() = configured
    override suspend fun createPin(pin: CharArray) { check(!configured); code = pin.concatToString(); pin.fill(0.toChar()); configured = true }
    override suspend fun verifyPin(pin: CharArray): PinResult = try { if (pin.concatToString() == code) PinResult.Success else PinResult.Incorrect } finally { pin.fill(0.toChar()) }
    override suspend fun lockoutSeconds() = 0L
}
private class TestPreferences : PreferencesRepository {
    private val value = MutableStateFlow(UserPreferences(onboardingComplete = true))
    override val preferences = value.asStateFlow()
    override suspend fun setName(name: String) { value.update { it.copy(displayName = name) } }
    override suspend fun setCurrency(currency: LedgerCurrency) { value.update { it.copy(currency = currency) } }
    override suspend fun setTheme(theme: ThemeMode) { value.update { it.copy(theme = theme) } }
    override suspend fun setLanguage(language: AppLanguage) { value.update { it.copy(language = language) } }
    override suspend fun completeOnboarding() { value.update { it.copy(onboardingComplete = true) } }
    override suspend fun setBiometrics(enabled: Boolean) { value.update { it.copy(biometricsEnabled = enabled) } }
    override suspend fun setPremiumAssistant(enabled: Boolean) { value.update { it.copy(premiumAssistantEnabled = enabled) } }
}
```

## app/src/test/kotlin/com/semmyx/ledger/voice/CatFaceMeshTest.kt

```kotlin
package com.semmyx.ledger.voice

import com.semmyx.ledger.presentation.premium.CatFaceMesh
import com.semmyx.ledger.presentation.premium.CatPose
import org.junit.Assert.*
import org.junit.Test

class CatFaceMeshTest {
    @Test fun blinkMovesEyeVerticesWithoutChangingTextureOrTopology() {
        val mesh = CatFaceMesh()
        val open = mesh.update(CatPose(), 1024, 1024).copyOf()
        val half = mesh.update(CatPose(blink = .5f), 1024, 1024).copyOf()
        val shut = mesh.update(CatPose(blink = 1f), 1024, 1024).copyOf()
        val eyeColumn = 32
        val above = (30 * 81 + eyeColumn) * 2 + 1
        val below = (34 * 81 + eyeColumn) * 2 + 1
        assertTrue(half[above] > open[above]); assertTrue(shut[above] > half[above])
        assertTrue(half[below] < open[below]); assertTrue(shut[below] < half[below])
        assertTrue(shut[below] - shut[above] < open[below] - open[above])
        assertTrue(shut.all { it.isFinite() })
        assertEquals(open.size, shut.size)
    }
    @Test fun yawInterpolatesContinuousPositionsRatherThanDiscreteFrames() {
        val mesh = CatFaceMesh()
        val left = mesh.update(CatPose(yawDegrees = -10f), 1024, 1024).copyOf()
        val middle = mesh.update(CatPose(yawDegrees = 0f), 1024, 1024).copyOf()
        val right = mesh.update(CatPose(yawDegrees = 10f), 1024, 1024).copyOf()
        val nose = (40 * 81 + 40) * 2
        assertTrue(left[nose] < middle[nose]); assertTrue(right[nose] > middle[nose])
        val fine = mesh.update(CatPose(yawDegrees = .1f), 1024, 1024).copyOf()
        assertTrue(kotlin.math.abs(fine[nose] - middle[nose]) < 1f)
    }
}
```

## app/src/test/kotlin/com/semmyx/ledger/voice/FinanceUpgradeTest.kt

```kotlin
package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FinanceUpgradeTest {
    private val today = LocalDate.of(2026, 10, 4)
    @Test fun monthlyQueryResolvesCurrentCalendarMonth() {
        val query = FinanceQuery.parse("Скай витрати за місяць") as FinanceQuery.Statistics
        assertEquals(EntryType.EXPENSE, query.type)
        assertEquals(LocalDate.of(2026, 10, 1), query.from(today))
        assertNull(query.category)
    }
    @Test fun monthlyFoodQuery() {
        val query = FinanceQuery.parse("Скай витрати за місяць за продукти") as FinanceQuery.Statistics
        assertEquals(Category.FOOD, query.category)
        assertEquals(FinanceQuery.Period.MONTH, query.period)
    }
    @Test fun russianAndEnglishQueries() {
        assertEquals(Category.TRANSPORT, (FinanceQuery.parse("расходы за месяц на транспорт") as FinanceQuery.Statistics).category)
        assertEquals(Category.FOOD, (FinanceQuery.parse("expenses this month for food") as FinanceQuery.Statistics).category)
    }
    @Test fun periodsAndIncome() {
        assertEquals(today.minusDays(6), (FinanceQuery.parse("витрати за тиждень") as FinanceQuery.Statistics).from(today))
        assertEquals(FinanceWindow.EARLIEST, (FinanceQuery.parse("витрати за весь час") as FinanceQuery.Statistics).from(today))
        assertEquals(EntryType.INCOME, (FinanceQuery.parse("дохід за місяць") as FinanceQuery.Statistics).type)
        assertEquals(today.withDayOfYear(1), (FinanceQuery.parse("витрати за рік") as FinanceQuery.Statistics).from(today))
    }
    @Test fun aPurchaseIsNotAStatisticsQuestion() {
        assertNull(FinanceQuery.parse("я витратив 100 грн сьогодні"))
        assertEquals(10000L, VoiceEntryParser.parse("я витратив 100 грн сьогодні")!!.amountMinor)
    }
    @Test fun missingCurrencyNeverIndexesMissingRegexGroup() {
        assertEquals(10000L, VoiceEntryParser.parse("купив томати за 100")!!.amountMinor)
        assertEquals(200050L, VoiceEntryParser.parse("отримав зарплату 2000,50")!!.amountMinor)
        assertNull(VoiceEntryParser.parse("привіт"))
    }
    @Test fun quantityIsNotThePrice() {
        val entry = VoiceEntryParser.parse("привет я купил 1 кг томатов за 100 грн")!!
        assertEquals(10000L, entry.amountMinor)
        assertEquals(Category.FOOD, entry.category)
        assertEquals(EntryType.EXPENSE, entry.type)
    }
    @Test fun salaryAndGroupedAmounts() {
        val entry = VoiceEntryParser.parse("я получил зарплату 1 000 000 гривен")!!
        assertEquals(100000000L, entry.amountMinor)
        assertEquals(Category.SALARY, entry.category)
        assertEquals(EntryType.INCOME, entry.type)
    }
    @Test fun conversationsAndNegationsDoNotCreateEntries() {
        assertNull(VoiceEntryParser.parse("привіт мені 20 років"))
        assertNull(VoiceEntryParser.parse("я не купив продукти за 100 грн"))
        assertEquals(VoiceSmallTalk.Topic.GREETING, VoiceSmallTalk.classify("привет"))
    }
    @Test fun spokenNegativeHasAnExplicitMinusWord() {
        assertEquals("мінус 2000 гривень", SpokenMoney.format(-200000, LedgerCurrency.UAH, AppLanguage.UKRAINIAN))
        assertEquals("minus 2000 hryvnias", SpokenMoney.format(-200000, LedgerCurrency.UAH, AppLanguage.ENGLISH))
        assertFalse(SpokenMoney.format(200000, LedgerCurrency.UAH, AppLanguage.UKRAINIAN).contains("мінус"))
        assertTrue(SpokenMoney.format(Long.MIN_VALUE, LedgerCurrency.UAH, AppLanguage.ENGLISH).startsWith("minus "))
    }
    @Test fun zeroIsNotDebt() {
        assertEquals("0 гривень", SpokenMoney.format(0, LedgerCurrency.UAH, AppLanguage.UKRAINIAN))
    }
}
```

## app/src/test/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistantParserTest.kt

```kotlin
package com.semmyx.ledger.voice

import org.junit.Assert.*
import org.junit.Test

class OptionalVoiceAssistantParserTest {
    @Test fun recognizesEnglishBalanceQuestion() {
        assertEquals(VoiceCommand.ShowBalance, OptionalVoiceAssistant.classify("what's my balance"))
    }
    @Test fun recognizesUkrainianBalanceQuestion() {
        assertEquals(VoiceCommand.ShowBalance, OptionalVoiceAssistant.classify("який в мене баланс"))
    }
    @Test fun recognizesSpentTodayInBothLanguages() {
        assertEquals(VoiceCommand.ShowSpentToday, OptionalVoiceAssistant.classify("what did I spend today"))
        assertEquals(VoiceCommand.ShowSpentToday, OptionalVoiceAssistant.classify("скільки я витратив сьогодні"))
    }
    @Test fun recognizesAddEntryRequests() {
        assertEquals(VoiceCommand.OpenAddEntry, OptionalVoiceAssistant.classify("add a new expense"))
        assertEquals(VoiceCommand.OpenAddEntry, OptionalVoiceAssistant.classify("додати новий запис"))
    }
    @Test fun returnsNullForUnrelatedSpeech() {
        assertNull(OptionalVoiceAssistant.classify("tell me a joke"))
    }
    @Test fun returnsNullForBlankInput() {
        assertNull(OptionalVoiceAssistant.classify("   "))
    }
}
```

## build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.1" apply false
}
```

## data/build.gradle.kts

```kotlin
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.semmyx.ledger.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    flavorDimensions += "billing"
    productFlavors {
        create("play") { dimension = "billing" }
        create("legacyV7") { dimension = "billing" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    testOptions { unitTests.isReturnDefaultValues = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":domain"))
    "playImplementation"("com.android.billingclient:billing-ktx:8.0.0")
    "legacyV7Implementation"("com.android.billingclient:billing-ktx:7.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:2.7.2")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

## data/consumer-rules.pro

```text
# Gson reads DTO fields reflectively, including after release shrinking.
-keep class com.semmyx.ledger.data.remote.RateDto { *; }
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
```

## data/src/androidTest/kotlin/com/semmyx/ledger/data/KeystoreSecurityTest.kt

```kotlin
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
```

## data/src/androidTest/kotlin/com/semmyx/ledger/data/RoomRepositoryTest.kt

```kotlin
package com.semmyx.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.semmyx.ledger.data.local.LedgerDatabase
import com.semmyx.ledger.data.repository.RoomEntryRepository
import com.semmyx.ledger.domain.*
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRepositoryTest {
    private lateinit var db: LedgerDatabase
    private lateinit var repository: RoomEntryRepository
    private fun entry(id: String = "one", title: String = "КАВА 50%") = LedgerEntry(id, title, 3500,
        LedgerCurrency.UAH, EntryType.EXPENSE, Category.FOOD, LocalDate.of(2026, 9, 8))
    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LedgerDatabase::class.java).build()
        repository = RoomEntryRepository(db.ledgerDao(), Clock.systemUTC())
    }
    @After fun closeDatabase() { db.close() }
    @Test fun reactiveCrudAndUnicodeLiteralSearch() = runTest {
        repository.save(entry())
        assertEquals(1, repository.observeEntries(EntryFilter(LedgerCurrency.UAH, "кава")).first().size)
        assertEquals(1, repository.observeEntries(EntryFilter(LedgerCurrency.UAH, "50%")).first().size)
        assertTrue(repository.observeEntries(EntryFilter(LedgerCurrency.USD)).first().isEmpty())
        repository.save(entry().copy(amountMinor = 4500))
        assertEquals(4500L, repository.observeBalance(LedgerCurrency.UAH).first().expenseMinor)
        val token = requireNotNull(repository.delete("one"))
        assertEquals(0, repository.observeBalance(LedgerCurrency.UAH).first().entryCount)
        assertTrue(repository.undo(token))
        assertFalse(repository.undo(token))
    }
    @Test fun anEditCannotResurrectADeletedRow() = runTest {
        repository.save(entry())
        repository.delete("one")
        try { repository.save(entry().copy(title = "Changed")); fail("Deleted row should be protected") }
        catch (_: IllegalStateException) { assertNull(repository.find("one")) }
    }
    @Test fun purgeRemovesOnlyOldTombstones() = runTest {
        repository.save(entry())
        val token = requireNotNull(repository.delete("one"))
        repository.purgeDeletedBefore(token.deletedAtMillis + 1)
        assertFalse(repository.undo(token))
        assertNull(db.ledgerDao().findIncludingDeleted("one"))
    }
}
```

## data/src/legacyV7/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt

```kotlin
package com.semmyx.ledger.data.billing

import android.content.Context
import com.android.billingclient.api.*

internal fun createVersionedBillingClient(context: Context, listener: PurchasesUpdatedListener): BillingClient =
    BillingClient.newBuilder(context).setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build()

internal fun BillingClient.queryVersionedProducts(params: QueryProductDetailsParams,
    callback: (BillingResult, List<ProductDetails>) -> Unit) {
    queryProductDetailsAsync(params) { result, products -> callback(result, products) }
}
internal fun versionedPrice(details: ProductDetails): String? = details.oneTimePurchaseOfferDetails?.formattedPrice
internal fun versionedProductParams(details: ProductDetails): BillingFlowParams.ProductDetailsParams =
    BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()
```

## data/src/main/AndroidManifest.xml

```xml
<manifest />
```

## data/src/main/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifier.kt

```kotlin
package com.semmyx.ledger.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Verifies activation codes issued manually by the developer (e.g. after a Telegram sale),
 * for installs where a Google Play purchase is not available or not yet published.
 * Only the developer's private key (never shipped in the app; see tools/issue_activation_code.py)
 * can issue a code. This class only ever holds the public key and can only verify, never issue.
 *
 * Code format (a plain-text token, safe to paste/copy from a chat app):
 *   base64url(payload) + "." + base64url(signature)
 * payload (before base64url; a pipe-delimited string, not JSON, so this class stays testable
 * without depending on Android's org.json stubs) is:
 *   "v1|<installationId>|<productId>|<issuedAtMillis>|<expiresAtMillisOrEmpty>"
 * signed with SHA256withRSA over the raw payload bytes.
 */
class DeveloperSubscriptionVerifier(base64PublicKey: String) {
    private val publicKey = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim())))
    } catch (_: Exception) { null }
    val configured: Boolean get() = publicKey != null

    data class Activation(
        val installationId: String,
        val productId: String,
        val issuedAtMillis: Long,
        val expiresAtMillis: Long?,
    )

    /**
     * Returns the decoded activation only if the signature is valid, the code targets this
     * exact installation and product, and (when present) it has not expired as of [nowMillis].
     * Returns null for any wrong, tampered, expired, or mismatched code, or when unconfigured.
     */
    fun verify(code: String, installationId: String, productId: String, nowMillis: Long = System.currentTimeMillis()): Activation? {
        val key = publicKey ?: return null
        val trimmed = code.trim()
        val dot = trimmed.lastIndexOf('.')
        if (dot <= 0 || dot == trimmed.length - 1) return null
        val payloadSegment = trimmed.substring(0, dot)
        val signatureSegment = trimmed.substring(dot + 1)
        return try {
            val payloadBytes = Base64.getUrlDecoder().decode(payloadSegment)
            val signatureBytes = Base64.getUrlDecoder().decode(signatureSegment)
            val verified = Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update(payloadBytes)
                verify(signatureBytes)
            }
            if (!verified) return null
            val fields = payloadBytes.toString(Charsets.UTF_8).split("|")
            if (fields.size != 5 || fields[0] != "v1") return null
            val expiry = if (fields[4].isEmpty()) null else fields[4].toLongOrNull() ?: return null
            val issued = fields[3].toLongOrNull() ?: return null
            if (issued <= 0 || issued > nowMillis + 300000 || (expiry != null && expiry <= issued)) return null
            val activation = Activation(
                installationId = fields[1],
                productId = fields[2],
                issuedAtMillis = issued,
                expiresAtMillis = expiry,
            )
            if (activation.installationId != installationId || activation.productId != productId) return null
            if (activation.expiresAtMillis != null && activation.expiresAtMillis <= nowMillis) return null
            activation
        } catch (_: Exception) { null }
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/billing/PlayPremiumRepository.kt

```kotlin
package com.semmyx.ledger.data.billing

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.android.billingclient.api.*
import com.semmyx.ledger.data.security.KeystoreVault
import com.semmyx.ledger.domain.*
import java.security.MessageDigest
import java.time.Clock
import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * One non-consumable premium product ("Ledger Plus"), grantable through two independent,
 * additive paths:
 *  - Google Play (a signed PURCHASED receipt, verified as before).
 *  - A manually issued developer activation code (see DeveloperSubscriptionVerifier), used
 *    for direct/Telegram sales while a Play listing is unavailable or unpublished. This never
 *    talks to a server; codes are asymmetric-signature tokens checked entirely on-device.
 * Either path alone is sufficient; losing one never revokes a still-valid grant from the other.
 */
class PlayPremiumRepository(
    context: Context,
    private val vault: KeystoreVault,
    private val scope: CoroutineScope,
    private val clock: Clock,
    publicKey: String,
    developerPublicKey: String,
    private val productId: String,
) : PremiumRepository, PurchasesUpdatedListener {
    private val app = context.applicationContext
    private val verifier = PurchaseSignatureVerifier(publicKey)
    private val developerVerifier = DeveloperSubscriptionVerifier(developerPublicKey)
    private val installationId = deriveInstallationId(app)
    private val playState = MutableStateFlow(PremiumState(configured = verifier.configured))
    private val developerActivation = MutableStateFlow<DeveloperSubscriptionVerifier.Activation?>(null)
    private data class Trial(val available: Boolean = false, val expires: Long? = null)
    private val trial = MutableStateFlow(Trial())
    private val playExpiry = MutableStateFlow<Long?>(null)
    private var lastNetworkRefresh = 0L
    override val state = combine(playState, developerActivation, trial) { play, dev, _ -> merge(play, dev) }
        .stateIn(scope, SharingStarted.Eagerly, merge(playState.value, developerActivation.value))
    private val client = createVersionedBillingClient(app, this)
    private val refreshLock = Mutex()
    private var details: ProductDetails? = null
    private val cacheTtlMillis = 7L * 24 * 60 * 60 * 1000

    init {
        scope.launch { refreshLocalEntitlements() }
        scope.launch {
            // One timer at the actual next entitlement deadline, not one AES/RSA read per minute.
            combine(developerActivation, trial, playExpiry) { dev, test, play ->
                listOfNotNull(dev?.expiresAtMillis, test.expires, play).minOrNull()
            }.collectLatest { deadline ->
                if (deadline != null) {
                    delay((deadline - clock.millis()).coerceAtLeast(1))
                    refreshLocalEntitlements()
                }
            }
        }
    }

    /** Foreground checks are local; Play discovery is throttled. Restore/purchase explicitly force refresh. */
    suspend fun refreshIfNeeded() {
        refreshLocalEntitlements()
        if (lastNetworkRefresh == 0L || SystemClock.elapsedRealtime() - lastNetworkRefresh > 6 * 60 * 60 * 1000L) refresh()
    }

    private suspend fun refreshLocalEntitlements() {
        try {
            developerActivation.value = cachedDeveloperEntitlement()
            val now = clock.millis()
            val trialStarted = vault.read { it.has("trialStart") }
            trial.value = if (!trialStarted) Trial(available = true) else vault.update { json ->
                val start = json.optLong("trialStart", 0)
                val end = json.optLong("trialEnd", 0)
                val last = json.optLong("trialLastSeen", start)
                if (start == 0L) Trial(available = true) else {
                    val elapsed = SystemClock.elapsedRealtime()
                    val sameBoot = json.optInt("trialBoot", -2) == bootCount()
                    val monotonicEnd = json.optLong("trialElapsedEnd", 0)
                    val valid = !json.optBoolean("trialRevoked", false) && TrialPolicy.active(start, end, now, last) &&
                        (!sameBoot || elapsed < monotonicEnd)
                    if (!valid) json.put("trialRevoked", true)
                    json.put("trialLastSeen", maxOf(last, now))
                    val remaining = if (sameBoot) minOf(end - now, monotonicEnd - elapsed) else end - now
                    Trial(expires = if (valid) now + remaining else null)
                }
            }
            val active = cachedEntitlement()
            playState.update { it.copy(entitled = active) }
            playExpiry.value = if (active) vault.read { it.optLong("premiumCheckedAt") + cacheTtlMillis } else null
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            trial.value = Trial()
            developerActivation.value = null
            playExpiry.value = null
            playState.update { it.copy(entitled = false, issue = BillingIssue.VERIFICATION) }
        }
    }

    /** Exactly one local trial. No card, Play purchase, automatic renewal, or charge involved. */
    override suspend fun startTrial(days: Int): Boolean {
        if (!TrialPolicy.validDays(days)) return false
        return refreshLock.withLock {
            if (state.value.entitled) return@withLock false
            val granted = vault.update { json ->
                if (json.has("trialStart")) false else {
                    val now = clock.millis()
                    json.put("trialStart", now).put("trialEnd", now + days * 86400000L)
                        .put("trialLastSeen", now).put("trialBoot", bootCount())
                        .put("trialElapsedEnd", SystemClock.elapsedRealtime() + days * 86400000L)
                    true
                }
            }
            refreshLocalEntitlements()
            granted
        }
    }

    override suspend fun refresh() = withContext(Dispatchers.Main.immediate) {
        refreshLock.withLock {
            refreshLocalEntitlements()
            lastNetworkRefresh = SystemClock.elapsedRealtime()
            if (!verifier.configured) {
                playState.update { it.copy(configured = false, entitled = false, issue = BillingIssue.CONFIGURATION) }
                return@withLock
            }
            playState.update { it.copy(busy = true, issue = null) }
            try {
                withTimeout(20_000) {
                    ensureConnected()
                    val (result, purchases) = queryOwned()
                    checkBilling(result)
                    val valid = withContext(Dispatchers.Default) { purchases.firstOrNull(::validPurchase) }
                    if (valid == null) {
                        playExpiry.value = null
                        vault.update { it.remove("premiumReceipt"); it.remove("premiumSignature"); it.remove("premiumCheckedAt") }
                        val pending = purchases.any { productId in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }
                        val rejected = purchases.any { productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        playState.update { it.copy(entitled = false, issue = when {
                            rejected -> BillingIssue.VERIFICATION
                            pending -> BillingIssue.PENDING
                            else -> null
                        }) }
                    } else {
                        // Durable grant first, then acknowledgement; both are idempotent.
                        vault.update {
                            it.put("premiumReceipt", valid.originalJson).put("premiumSignature", valid.signature)
                                .put("premiumCheckedAt", clock.millis()).put("premiumCheckedElapsed", SystemClock.elapsedRealtime())
                                .put("premiumCheckedBoot", bootCount())
                        }
                        playExpiry.value = clock.millis() + cacheTtlMillis
                        playState.update { it.copy(entitled = true) }
                        if (!valid.isAcknowledged) {
                            val ack = acknowledge(valid.purchaseToken)
                            if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                                playState.update { it.copy(issue = BillingIssue.ACKNOWLEDGEMENT) }
                            }
                        }
                    }
                    // Product discovery failing never revokes an already verified purchase.
                    val (productResult, products) = queryDetails()
                    details = if (productResult.responseCode == BillingClient.BillingResponseCode.OK)
                        products.firstOrNull { it.productId == productId } else null
                    playState.update { it.copy(formattedPrice = details?.let(::versionedPrice)) }
                }
            } catch (error: TimeoutCancellationException) {
                playState.update { it.copy(issue = BillingIssue.NETWORK) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // Keep only a previously cryptographically verified, time-bounded offline grant.
                val cached = try { cachedEntitlement() } catch (inner: Exception) {
                    if (inner is CancellationException) throw inner
                    false
                }
                playState.update { it.copy(entitled = cached, issue = BillingIssue.UNAVAILABLE) }
            } finally {
                playState.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Verifies a developer-issued activation code (from a manual/Telegram sale) against this
     * exact installation, and — only if valid and not expired — stores it so the grant survives
     * restarts without any network access. Returns false (and sets BillingIssue.ACTIVATION_CODE)
     * for a wrong, tampered, expired, or installation-mismatched code.
     */
    override suspend fun activateDeveloperToken(token: String): Boolean = withContext(Dispatchers.Default) {
        val activation = developerVerifier.verify(token, installationId, productId, clock.millis())
        if (activation == null) {
            playState.update { it.copy(issue = BillingIssue.ACTIVATION_CODE) }
            return@withContext false
        }
        vault.update { it.put("developerActivationCode", token.trim()) }
        developerActivation.value = activation
        playState.update { it.copy(issue = null) }
        true
    }

    override suspend fun developerInstallationId(): String = installationId

    fun launchPurchase(activity: Activity) {
        scope.launch(Dispatchers.Main.immediate) {
            if (state.value.busy || (state.value.entitled && state.value.source != PremiumSource.TRIAL && state.value.expiresAtMillis == null)) return@launch
            refresh() // Do not reuse stale ProductDetails / offer tokens.
            if (state.value.entitled && state.value.source != PremiumSource.TRIAL && state.value.expiresAtMillis == null) return@launch
            val product = details
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (product == null || !verifier.configured || versionedPrice(product) == null) {
                playState.update { it.copy(issue = if (verifier.configured) BillingIssue.UNAVAILABLE else BillingIssue.CONFIGURATION) }
                return@launch
            }
            val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(versionedProductParams(product))).build()
            val result = client.launchBillingFlow(activity, params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK && result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                playState.update { it.copy(issue = BillingIssue.UNAVAILABLE) }
            }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK, BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch { refresh() }
            BillingClient.BillingResponseCode.USER_CANCELED -> clearIssue()
            else -> playState.update { it.copy(busy = false, issue = BillingIssue.UNAVAILABLE) }
        }
    }
    override fun clearIssue() { playState.update { it.copy(issue = null) } }

    private fun merge(play: PremiumState, dev: DeveloperSubscriptionVerifier.Activation?): PremiumState {
        val developerActive = dev != null && (dev.expiresAtMillis == null || dev.expiresAtMillis > clock.millis())
        val trialEnd = trial.value.expires?.takeIf { it > clock.millis() }
        return play.copy(
            entitled = play.entitled || developerActive || trialEnd != null,
            trialAvailable = trial.value.available,
            source = when { play.entitled -> PremiumSource.GOOGLE_PLAY; developerActive -> PremiumSource.OFFICIAL_DEVELOPER; trialEnd != null -> PremiumSource.TRIAL; else -> null },
            expiresAtMillis = when { play.entitled -> null; developerActive -> dev?.expiresAtMillis; else -> trialEnd },
            developerPurchaseEnabled = developerVerifier.configured,
            developerInstallationId = installationId,
        )
    }

    private suspend fun cachedDeveloperEntitlement(): DeveloperSubscriptionVerifier.Activation? = vault.read { json ->
        val code = json.optString("developerActivationCode", "")
        if (code.isBlank()) return@read null
        developerVerifier.verify(code, installationId, productId, clock.millis())
    }

    private suspend fun ensureConnected() {
        if (client.isReady) return
        val result = suspendCancellableCoroutine<BillingResult> { continuation ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (continuation.isActive) continuation.resume(result)
                }
                override fun onBillingServiceDisconnected() {
                    playState.update { it.copy(busy = false, issue = BillingIssue.UNAVAILABLE) }
                }
            })
        }
        checkBilling(result)
    }
    private suspend fun queryOwned() = suspendCancellableCoroutine<Pair<BillingResult, List<Purchase>>> { continuation ->
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (continuation.isActive) continuation.resume(result to purchases)
        }
    }
    private suspend fun queryDetails() = suspendCancellableCoroutine<Pair<BillingResult, List<ProductDetails>>> { continuation ->
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId).setProductType(BillingClient.ProductType.INAPP).build())).build()
        client.queryVersionedProducts(params) { result, products ->
            if (continuation.isActive) continuation.resume(result to products)
        }
    }
    private suspend fun acknowledge(token: String) = suspendCancellableCoroutine<BillingResult> { continuation ->
        client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }
    private fun validPurchase(purchase: Purchase): Boolean = try {
        purchase.purchaseState == Purchase.PurchaseState.PURCHASED && productId in purchase.products &&
            JSONObject(purchase.originalJson).optString("packageName") == app.packageName &&
            verifier.verify(purchase.originalJson, purchase.signature)
    } catch (_: Exception) { false }

    private suspend fun cachedEntitlement(): Boolean = vault.read { json ->
        val checked = json.optLong("premiumCheckedAt", 0)
        val wallAge = clock.millis() - checked
        val age = if (json.optInt("premiumCheckedBoot", -2) == bootCount()) {
            SystemClock.elapsedRealtime() - json.optLong("premiumCheckedElapsed", 0)
        } else wallAge
        if (!verifier.configured || checked == 0L || wallAge < 0 || age !in 0..cacheTtlMillis) return@read false
        val receipt = json.optString("premiumReceipt", "")
        val signature = json.optString("premiumSignature", "")
        try { validPurchase(Purchase(receipt, signature)) } catch (_: Exception) { false }
    }
    private fun bootCount() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    private fun checkBilling(result: BillingResult) {
        check(result.responseCode == BillingClient.BillingResponseCode.OK) { "Play response ${result.responseCode}" }
    }
}

private fun deriveInstallationId(context: Context): String {
    val androidId = try { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "" } catch (_: Exception) { "" }
    val digest = MessageDigest.getInstance("SHA-256").digest("$androidId:${context.packageName}".toByteArray(Charsets.UTF_8))
    val hex = digest.joinToString("") { "%02x".format(it) }.uppercase().take(12)
    return hex.chunked(4).joinToString("-")
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/billing/PurchaseSignatureVerifier.kt

```kotlin
package com.semmyx.ledger.data.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Play's purchase signature algorithm is SHA1withRSA (a protocol requirement,
 * not the PIN KDF). No entitlement is granted when the key is missing / invalid.
 * For high-value products, server verification + RTDN must augment client checks.
 */
class PurchaseSignatureVerifier(base64PublicKey: String) {
    private val publicKey = try {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64PublicKey.trim())))
    } catch (_: Exception) { null }
    val configured: Boolean get() = publicKey != null
    fun verify(json: String, signature: String): Boolean {
        val key = publicKey ?: return false
        return try {
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(json.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature))
            }
        } catch (_: Exception) { false }
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/local/BankEntities.kt

```kotlin
package com.semmyx.ledger.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(@PrimaryKey val id: String, val name: String, val colorHex: String)
@Entity(tableName = "bank_inbox", indices = [Index(value = ["eventKey"], unique = true)])
data class BankInboxEntity(@PrimaryKey val id: String, val eventKey: String, val bankId: String,
    val amountMinor: Long, val currency: String, val type: String, val merchant: String, val cardLast4: String?,
    val eventAtMillis: Long, val receivedAtMillis: Long, val timeFromMessage: Boolean,
    val transfer: Boolean, val category: String, val origin: String, val status: String,
    val entryId: String? = null)
data class SourceSpendRow(val sourceType: String, val bankId: String?, val totalMinor: Long)
data class DetailedCategoryRow(val category: String, val customCategoryId: String?, val customName: String?, val customColor: String?, val totalMinor: Long)
@Dao
interface BankDao {
    @Query("SELECT * FROM custom_categories ORDER BY name") fun categories(): Flow<List<CustomCategoryEntity>>
    @Insert suspend fun addCategory(category: CustomCategoryEntity)
    @Query("SELECT * FROM bank_inbox WHERE status IN ('REVIEW','DUPLICATE_REVIEW') ORDER BY receivedAtMillis DESC LIMIT 100") fun inbox(): Flow<List<BankInboxEntity>>
    @Query("SELECT * FROM bank_inbox WHERE id=:id") suspend fun inboxItem(id: String): BankInboxEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInbox(item: BankInboxEntity): Long
    @Query("UPDATE bank_inbox SET status=:status,entryId=:entryId WHERE id=:id") suspend fun status(id: String, status: String, entryId: String?)
    @Query("""SELECT COUNT(*) FROM bank_inbox WHERE bankId=:bank AND amountMinor=:amount
        AND currency=:currency AND type=:type AND ABS(eventAtMillis-:time)<=90000
        AND status IN ('IMPORTED','REVIEW','DUPLICATE_REVIEW')
        AND ((cardLast4 IS NOT NULL AND cardLast4=:card) OR (merchant<>'' AND LOWER(merchant)=LOWER(:merchant)))""")
    suspend fun similar(bank: String, amount: Long, currency: String, type: String, time: Long, card: String?, merchant: String): Int
    @Query("""SELECT sourceType,bankId,COALESCE(SUM(amountMinor),0) AS totalMinor FROM entries
        WHERE deletedAtMillis IS NULL AND type='EXPENSE' AND currency=:currency AND epochDay BETWEEN :from AND :through
        GROUP BY sourceType,bankId ORDER BY totalMinor DESC""")
    fun sources(currency: String, from: Long, through: Long): Flow<List<SourceSpendRow>>
    @Query("""SELECT e.category,e.customCategoryId,c.name AS customName,c.colorHex AS customColor,SUM(e.amountMinor) AS totalMinor
        FROM entries e LEFT JOIN custom_categories c ON e.customCategoryId=c.id
        WHERE e.deletedAtMillis IS NULL AND e.type='EXPENSE' AND e.currency=:currency AND e.epochDay BETWEEN :from AND :through
        AND (:source IS NULL OR e.sourceType=:source) AND (:bank IS NULL OR e.bankId=:bank)
        GROUP BY e.category,e.customCategoryId,c.name,c.colorHex ORDER BY totalMinor DESC""")
    fun detailCategories(currency: String, from: Long, through: Long, source: String?, bank: String?): Flow<List<DetailedCategoryRow>>
    @Query("""SELECT * FROM entries WHERE deletedAtMillis IS NULL AND currency=:currency
        AND epochDay BETWEEN :from AND :through AND (:source IS NULL OR sourceType=:source) AND (:bank IS NULL OR bankId=:bank)
        ORDER BY COALESCE(eventAtMillis,createdAtMillis) DESC LIMIT 100""")
    fun recent(currency: String, from: Long, through: Long, source: String?, bank: String?): Flow<List<EntryEntity>>
    @Query("SELECT * FROM entries WHERE deletedAtMillis IS NULL ORDER BY epochDay DESC,createdAtMillis DESC") suspend fun exportEntries(): List<EntryEntity>
    @Query("DELETE FROM bank_inbox") suspend fun clearInbox()
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/local/Entities.kt

```kotlin
package com.semmyx.ledger.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "entries", indices = [Index(value = ["currency", "deletedAtMillis", "epochDay", "createdAtMillis"])])
data class EntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amountMinor: Long,
    val currency: String,
    val type: String,
    val category: String,
    val epochDay: Long,
    val note: String,
    val searchText: String,
    val createdAtMillis: Long,
    val deletedAtMillis: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "'UNKNOWN'") val sourceType: String = "UNKNOWN",
    val bankId: String? = null,
    val cardLast4: String? = null,
    val eventAtMillis: Long? = null,
    val customCategoryId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'MANUAL'") val origin: String = "MANUAL",
)

data class BalanceRow(val incomeMinor: Long, val expenseMinor: Long, val entryCount: Int)
data class CategoryTotalRow(val category: String, val incomeMinor: Long, val expenseMinor: Long, val count: Int, val incomeCount: Int, val expenseCount: Int)
data class ActivityRow(val epochDay: Long, val incomeMinor: Long, val expenseMinor: Long)

@Entity(tableName = "rates")
data class RateEntity(
    @PrimaryKey val currency: String,
    val uahPerUnit: Double,
    val epochDay: Long,
    val fetchedAtMillis: Long,
)
```

## data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt

```kotlin
package com.semmyx.ledger.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    // Bound LIKE patterns escape wildcard characters; no query-string SQL interpolation.
    @Query("""SELECT * FROM entries WHERE deletedAtMillis IS NULL AND currency = :currency
        AND (:type IS NULL OR type = :type)
        AND (:category IS NULL OR category = :category)
        AND (:from IS NULL OR epochDay >= :from) AND (:through IS NULL OR epochDay <= :through)
        AND searchText LIKE :pattern ESCAPE '\'
        ORDER BY epochDay DESC, createdAtMillis DESC, id DESC LIMIT :limit""")
    fun observeEntries(currency: String, pattern: String, type: String?, limit: Int, category: String? = null, from: Long? = null, through: Long? = null): Flow<List<EntryEntity>>

    @Query("""SELECT COALESCE(SUM(CASE WHEN type='INCOME' THEN amountMinor ELSE 0 END),0) AS incomeMinor,
        COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amountMinor ELSE 0 END),0) AS expenseMinor,
        COUNT(*) AS entryCount FROM entries WHERE currency=:currency AND deletedAtMillis IS NULL""")
    fun observeBalance(currency: String): Flow<BalanceRow>

    @Query("""SELECT epochDay, SUM(CASE WHEN type='INCOME' THEN amountMinor ELSE 0 END) AS incomeMinor,
        SUM(CASE WHEN type='EXPENSE' THEN amountMinor ELSE 0 END) AS expenseMinor
        FROM entries WHERE currency=:currency AND deletedAtMillis IS NULL
        AND epochDay BETWEEN :from AND :through GROUP BY epochDay ORDER BY epochDay""")
    fun observeActivity(currency: String, from: Long, through: Long): Flow<List<ActivityRow>>

    @Query("""SELECT category, COALESCE(SUM(CASE WHEN type='INCOME' THEN amountMinor ELSE 0 END),0) AS incomeMinor,
        COALESCE(SUM(CASE WHEN type='EXPENSE' THEN amountMinor ELSE 0 END),0) AS expenseMinor, COUNT(*) AS count,
        SUM(CASE WHEN type='INCOME' THEN 1 ELSE 0 END) AS incomeCount,
        SUM(CASE WHEN type='EXPENSE' THEN 1 ELSE 0 END) AS expenseCount
        FROM entries WHERE currency=:currency AND deletedAtMillis IS NULL AND epochDay BETWEEN :from AND :through
        GROUP BY category ORDER BY expenseMinor DESC""")
    fun observeCategoryTotals(currency: String, from: Long, through: Long): Flow<List<CategoryTotalRow>>

    @Query("""SELECT COALESCE(SUM(amountMinor),0) FROM entries WHERE deletedAtMillis IS NULL
        AND currency=:currency AND type=:type AND epochDay BETWEEN :from AND :through
        AND (:category IS NULL OR category=:category)""")
    suspend fun statistics(currency: String, from: Long, through: Long, type: String, category: String?): Long

    @Query("SELECT * FROM entries WHERE id=:id AND deletedAtMillis IS NULL")
    suspend fun find(id: String): EntryEntity?
    @Query("SELECT * FROM entries WHERE id=:id")
    suspend fun findIncludingDeleted(id: String): EntryEntity?
    @Insert suspend fun insert(entry: EntryEntity)
    @Update suspend fun update(entry: EntryEntity): Int
    @Transaction
    suspend fun save(entry: EntryEntity) {
        val existing = findIncludingDeleted(entry.id)
        if (existing == null) insert(entry)
        else {
            check(existing.deletedAtMillis == null) { "Cannot edit a deleted entry" }
            check(update(entry.copy(createdAtMillis = existing.createdAtMillis)) == 1)
        }
    }
    @Query("UPDATE entries SET deletedAtMillis=:now WHERE id=:id AND deletedAtMillis IS NULL")
    suspend fun delete(id: String, now: Long): Int
    @Query("UPDATE entries SET deletedAtMillis=NULL WHERE id=:id AND deletedAtMillis=:stamp")
    suspend fun undo(id: String, stamp: Long): Int
    @Query("DELETE FROM entries WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :before")
    suspend fun purge(before: Long)
}

@Dao
interface RateDao {
    @Query("SELECT * FROM rates ORDER BY currency") fun observe(): Flow<List<RateEntity>>
    @Upsert suspend fun upsert(rates: List<RateEntity>)
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt

```kotlin
package com.semmyx.ledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [EntryEntity::class, RateEntity::class, CustomCategoryEntity::class, BankInboxEntity::class], version = 2, exportSchema = true)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledgerDao(): LedgerDao
    abstract fun rateDao(): RateDao
    abstract fun bankDao(): BankDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE entries ADD COLUMN bankId TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN cardLast4 TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN eventAtMillis INTEGER")
                db.execSQL("ALTER TABLE entries ADD COLUMN customCategoryId TEXT")
                db.execSQL("ALTER TABLE entries ADD COLUMN origin TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("CREATE TABLE IF NOT EXISTS custom_categories (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,colorHex TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS bank_inbox (id TEXT NOT NULL PRIMARY KEY,eventKey TEXT NOT NULL,bankId TEXT NOT NULL,amountMinor INTEGER NOT NULL,currency TEXT NOT NULL,type TEXT NOT NULL,merchant TEXT NOT NULL,cardLast4 TEXT,eventAtMillis INTEGER NOT NULL,receivedAtMillis INTEGER NOT NULL,timeFromMessage INTEGER NOT NULL,transfer INTEGER NOT NULL,category TEXT NOT NULL,origin TEXT NOT NULL,status TEXT NOT NULL,entryId TEXT)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bank_inbox_eventKey ON bank_inbox(eventKey)")
            }
        }
        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(
            context.applicationContext, LedgerDatabase::class.java, "ledger.db"
        ).addMigrations(MIGRATION_1_2).setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build()
        // v1 -> v2 preserves all ledger rows. Every future schema must register explicit migrations;
        // deliberately no fallbackToDestructiveMigration that could erase financial records.
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/remote/RateApi.kt

```kotlin
package com.semmyx.ledger.data.remote

import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/** Public National Bank of Ukraine reference rates. No personal data is sent. */
interface RateApi {
    @GET("NBUStatService/v1/statdirectory/exchange?json")
    suspend fun latest(): List<RateDto>

    companion object {
        fun create(): RateApi {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true).build()
            return Retrofit.Builder().baseUrl("https://bank.gov.ua/")
                .client(client).addConverterFactory(GsonConverterFactory.create())
                .build().create(RateApi::class.java)
        }
    }
}

data class RateDto(
    @SerializedName("cc") val currency: String?,
    @SerializedName("rate") val rate: Double?,
    @SerializedName("exchangedate") val exchangeDate: String?,
)
```

## data/src/main/kotlin/com/semmyx/ledger/data/repository/BankImportRepository.kt

```kotlin
package com.semmyx.ledger.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.semmyx.ledger.data.local.*
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Imports and acknowledgements are atomic with the existing ledger. No HTTP, raw text or PAN storage. */
class BankImportRepository(private val db: LedgerDatabase, context: Context) : BankRepository {
    private val config = context.applicationContext.getSharedPreferences("bank_import", Context.MODE_PRIVATE)
    private val diagnosticState = MutableStateFlow(ImportDiagnostics())
    override val diagnostics = diagnosticState.asStateFlow()
    fun connected(value: Boolean) { diagnosticState.update { it.copy(connected = value) } }
    fun note(code: String, packageName: String = "", notification: Boolean = false, bankMessage: Boolean = false) {
        diagnosticState.update { old -> old.copy(lastCode = code, lastPackage = packageName.ifBlank { old.lastPackage },
            lastAtMillis = System.currentTimeMillis(), notificationCount = old.notificationCount + if (notification) 1 else 0,
            bankMessageCount = old.bankMessageCount + if (bankMessage) 1 else 0) }
    }
    override fun configuredPackage(bankId: String): String = config.getString("package_" + bankId, null)
        ?: Banks.all.firstOrNull { it.id == bankId }?.packages?.firstOrNull().orEmpty()
    override fun setPackage(bankId: String, packageName: String) {
        require(Banks.all.any { it.id == bankId })
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")))
        config.edit().putString("package_" + bankId, packageName.trim()).apply()
    }
    fun bankForPackage(packageName: String): Bank? = Banks.all.firstOrNull { config.getString("package_" + it.id, null) == packageName }
        ?: Banks.byPackage(packageName)
    override suspend fun importText(bankId: String, text: String): Boolean {
        val bank = Banks.all.firstOrNull { it.id == bankId } ?: return false
        if (BankParser.parse(bank, text, System.currentTimeMillis()) == null) { note("UNRECOGNIZED"); return false }
        receive(bank, text, "MANUAL_TEXT:" + bankId + ":" + UUID.randomUUID(), System.currentTimeMillis(), "MANUAL_TEXT", manual = true)
        return true
    }
    private val dao = db.bankDao()
    override fun categories() = dao.categories().map { rows -> rows.map { CustomBankCategory(it.id, it.name, it.colorHex) } }
    override fun reviews() = dao.inbox().map { rows -> rows.map { ImportReview(it.id, it.bankId, it.amountMinor,
        LedgerCurrency.valueOf(it.currency), EntryType.valueOf(it.type), it.merchant, it.cardLast4, it.eventAtMillis,
        it.timeFromMessage, it.transfer, it.status) } }
    override fun sources(currency: LedgerCurrency, from: LocalDate, through: LocalDate) =
        dao.sources(currency.name, from.toEpochDay(), through.toEpochDay()).map { rows -> rows.map { BankSourceSpend(it.sourceType, it.bankId, it.totalMinor) } }
    override fun categorySpending(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?) =
        dao.detailCategories(currency.name, from.toEpochDay(), through.toEpochDay(), source, bank).map { rows -> rows.map {
            BankCategorySpend(Category.valueOf(it.category), it.customCategoryId, it.customName, it.customColor, it.totalMinor) } }
    override fun recent(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?) =
        dao.recent(currency.name, from.toEpochDay(), through.toEpochDay(), source, bank).map { rows -> rows.map { it.toDomain() } }
    override suspend fun exportEntries() = dao.exportEntries().map { it.toDomain() }
    override suspend fun clearHistory() { dao.clearInbox() }
    override fun budget(currency: LedgerCurrency): Long = config.getLong("budget_" + currency.name, 0L)
    override fun setBudget(currency: LedgerCurrency, minor: Long) { require(minor >= 0); config.edit().putLong("budget_" + currency.name, minor).apply() }
    override var enabled: Boolean
        get() = config.getBoolean("enabled", false)
        set(value) { config.edit().putBoolean("enabled", value).apply() }
    override var autoImport: Boolean
        get() = config.getBoolean("auto", false)
        set(value) { config.edit().putBoolean("auto", value).apply() }
    suspend fun receive(bank: Bank, text: String, eventKey: String, at: Long, origin: String, manual: Boolean = false) {
        if (!enabled && !manual) { note("DISABLED"); return }
        val parsed = BankParser.parse(bank, text, at) ?: run { note("UNRECOGNIZED"); return }
        var result = "QUEUED"
        db.withTransaction {
            val duplicate = dao.similar(parsed.bankId, parsed.amountMinor, parsed.currency.name, parsed.type.name,
                parsed.happenedAtMillis, parsed.cardLast4, parsed.merchant) > 0
            val status = if (duplicate) "DUPLICATE_REVIEW" else "REVIEW"
            val item = BankInboxEntity(UUID.randomUUID().toString(), digest(eventKey), parsed.bankId, parsed.amountMinor,
                parsed.currency.name, parsed.type.name, parsed.merchant, parsed.cardLast4, parsed.happenedAtMillis, at,
                parsed.timeFromMessage, parsed.transfer, parsed.category.name, origin, status)
            if (dao.insertInbox(item) == -1L) { result = "REDELIVERED"; return@withTransaction }
            if (duplicate) result = "DUPLICATE_REVIEW"
            if (!manual && autoImport && parsed.confident && !duplicate) { acceptInside(item, parsed.type); result = "AUTO_IMPORTED" }
        }
        note(result)
    }
    override suspend fun accept(id: String, type: EntryType) { db.withTransaction {
        val item = dao.inboxItem(id) ?: return@withTransaction
        if (item.status !in listOf("REVIEW", "DUPLICATE_REVIEW")) return@withTransaction
        acceptInside(item, type)
    } }
    private suspend fun acceptInside(item: BankInboxEntity, type: EntryType) {
        val id = "bank-" + item.id
        val date = Instant.ofEpochMilli(item.eventAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val title = item.merchant.ifBlank { Banks.name(item.bankId) }.take(60)
        val entity = EntryEntity(id, title, item.amountMinor, item.currency, type.name, item.category, date.toEpochDay(),
            if (item.transfer) "Confirmed external transfer" else "", title.lowercase(Locale.ROOT), item.receivedAtMillis,
            sourceType = "CARD", bankId = item.bankId, cardLast4 = item.cardLast4, eventAtMillis = item.eventAtMillis, origin = item.origin)
        db.ledgerDao().insert(entity)
        dao.status(item.id, "IMPORTED", id)
    }
    override suspend fun ignore(id: String) { db.withTransaction {
        val item = dao.inboxItem(id) ?: return@withTransaction
        if (item.status in listOf("REVIEW", "DUPLICATE_REVIEW")) dao.status(id, "IGNORED", null)
    } }
    override suspend fun createCategory(name: String, hex: String) {
        require(name.trim().length in 1..40)
        val color = ColorPreferences.normalizeHex(hex) ?: error("Invalid color")
        dao.addCategory(CustomCategoryEntity(UUID.randomUUID().toString(), name.trim(), color))
    }
    companion object {
        fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/repository/DataStorePreferences.kt

```kotlin
package com.semmyx.ledger.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.semmyx.ledger.domain.AppLanguage
import com.semmyx.ledger.domain.LedgerCurrency
import com.semmyx.ledger.domain.PreferencesRepository
import com.semmyx.ledger.domain.ThemeMode
import com.semmyx.ledger.domain.UserPreferences
import java.util.Locale
import org.json.JSONObject
import com.semmyx.ledger.domain.ColorPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class DataStorePreferences(context: Context, scope: CoroutineScope) : PreferencesRepository {
    private val defaultLanguage = if (Locale.getDefault().language == "uk") AppLanguage.UKRAINIAN else AppLanguage.ENGLISH
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(scope.coroutineContext + Dispatchers.IO),
        produceFile = { context.applicationContext.preferencesDataStoreFile("preferences") },
    )
    private object Keys {
        val name = stringPreferencesKey("display_name")
        val currency = stringPreferencesKey("currency")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val onboarded = booleanPreferencesKey("onboarded")
        val biometrics = booleanPreferencesKey("biometrics")
        val colors = stringPreferencesKey("color_overrides")
        val refreshRate = intPreferencesKey("refresh_rate_hz")
        val visualRevision = intPreferencesKey("visual_revision")
        val skin = stringPreferencesKey("mascot_skin")
        val animations = booleanPreferencesKey("mascot_animations")
        val premiumAssistant = booleanPreferencesKey("premium_assistant")
    }
    init {
        scope.launch {
            store.edit { values ->
                if ((values[Keys.visualRevision] ?: 0) < 4) {
                    values[Keys.theme] = ThemeMode.DARK.name
                    values[Keys.animations] = true
                    values[Keys.visualRevision] = 4
                }
            }
        }
    }
    override val preferences = store.data.map { p ->
        UserPreferences(
            displayName = p[Keys.name].orEmpty(),
            currency = enumOrDefault(p[Keys.currency], LedgerCurrency.UAH),
            theme = enumOrDefault(p[Keys.theme], ThemeMode.DARK),
            language = enumOrDefault(p[Keys.language], defaultLanguage),
            onboardingComplete = p[Keys.onboarded] ?: false,
            biometricsEnabled = p[Keys.biometrics] ?: false,
            premiumAssistantEnabled = p[Keys.premiumAssistant] ?: false,
            mascotSkin = p[Keys.skin] ?: "gray",
            mascotAnimations = p[Keys.animations] ?: true,
            refreshRateHz = p[Keys.refreshRate] ?: 0,
            colorOverrides = decodeColors(p[Keys.colors]),
        )
    }
    override suspend fun setName(name: String) { require(name.trim().length <= 40); store.edit { it[Keys.name] = name.trim() } }
    override suspend fun setCurrency(currency: LedgerCurrency) { store.edit { it[Keys.currency] = currency.name } }
    override suspend fun setTheme(theme: ThemeMode) { store.edit { it[Keys.theme] = theme.name } }
    override suspend fun setLanguage(language: AppLanguage) { store.edit { it[Keys.language] = language.name } }
    override suspend fun completeOnboarding() { store.edit { it[Keys.onboarded] = true } }
    override suspend fun setBiometrics(enabled: Boolean) { store.edit { it[Keys.biometrics] = enabled } }
    override suspend fun setColor(key: String, hex: String?) {
        require(ColorPreferences.validKey(key))
        val normalized = hex?.let { ColorPreferences.normalizeHex(it) ?: error("Invalid RGB color") }
        store.edit { prefs ->
            val values = decodeColors(prefs[Keys.colors]).toMutableMap()
            if (normalized == null) values.remove(key) else values[key] = normalized
            prefs[Keys.colors] = JSONObject(values as Map<*, *>).toString()
        }
    }
    override suspend fun resetColors() { store.edit { it.remove(Keys.colors) } }
    override suspend fun setRefreshRate(hz: Int) { require(hz in listOf(0, 60, 90, 120)); store.edit { it[Keys.refreshRate] = hz } }
    override suspend fun setMascotSkin(value: String) { require(value in listOf("gray", "sky")); store.edit { it[Keys.skin] = value } }
    override suspend fun setMascotAnimations(enabled: Boolean) { store.edit { it[Keys.animations] = enabled } }
    override suspend fun setPremiumAssistant(enabled: Boolean) { store.edit { it[Keys.premiumAssistant] = enabled } }
}

private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

private fun decodeColors(text: String?): Map<String, String> {
    if (text.isNullOrBlank()) return emptyMap()
    return try {
        val json = JSONObject(text)
        buildMap { json.keys().forEach { key ->
            if (ColorPreferences.validKey(key)) ColorPreferences.normalizeHex(json.optString(key))?.let { put(key, it) }
        } }
    } catch (_: Exception) { emptyMap() }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/repository/OfflineRateRepository.kt

```kotlin
package com.semmyx.ledger.data.repository

import com.semmyx.ledger.data.local.RateDao
import com.semmyx.ledger.data.local.RateEntity
import com.semmyx.ledger.data.remote.RateApi
import com.semmyx.ledger.domain.*
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineRateRepository(private val dao: RateDao, private val api: RateApi, private val clock: Clock) : RateRepository {
    private val refreshLock = Mutex()
    override val rates = dao.observe().map { rows -> rows.map {
        ExchangeRate(LedgerCurrency.valueOf(it.currency), it.uahPerUnit, LocalDate.ofEpochDay(it.epochDay), it.fetchedAtMillis)
    } }
    override suspend fun refresh() = refreshLock.withLock {
        val fetchedAt = clock.millis()
        val allowed = LedgerCurrency.entries.map { it.name }.toSet() - "UAH"
        val rows = api.latest().mapNotNull { dto ->
            val code = dto.currency?.takeIf { it in allowed } ?: return@mapNotNull null
            val value = dto.rate?.takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            val date = try { LocalDate.parse(dto.exchangeDate, DateTimeFormatter.ofPattern("dd.MM.yyyy")) }
                catch (_: Exception) { return@mapNotNull null }
            RateEntity(code, value, date.toEpochDay(), fetchedAt)
        }
        // Invalid or partial payloads must not destroy a known-good offline cache.
        if (rows.map { it.currency }.toSet() != allowed) throw IOException("Incomplete rate response")
        dao.upsert(rows)
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/repository/RoomEntryRepository.kt

```kotlin
package com.semmyx.ledger.data.repository

import com.semmyx.ledger.data.local.EntryEntity
import com.semmyx.ledger.data.local.LedgerDao
import com.semmyx.ledger.domain.*
import java.text.Normalizer
import java.util.Locale
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.map

class RoomEntryRepository(private val dao: LedgerDao, private val clock: Clock) : EntryRepository {
    override fun observeEntries(filter: EntryFilter) = dao.observeEntries(
        filter.currency.name, likePattern(filter.query), filter.type?.name, filter.limit.coerceAtLeast(1),
        filter.category?.name, filter.from?.toEpochDay(), filter.through?.toEpochDay()
    ).map { rows -> rows.map { it.toDomain() } }
    override fun observeBalance(currency: LedgerCurrency) = dao.observeBalance(currency.name)
        .map { Balance(it.incomeMinor, it.expenseMinor, it.entryCount) }
    override fun observeActivity(currency: LedgerCurrency, from: LocalDate, through: LocalDate) =
        dao.observeActivity(currency.name, from.toEpochDay(), through.toEpochDay())
            .map { rows -> rows.map { DailyActivity(LocalDate.ofEpochDay(it.epochDay), it.incomeMinor, it.expenseMinor) } }
    override fun observeCategoryTotals(currency: LedgerCurrency, from: LocalDate, through: LocalDate) =
        dao.observeCategoryTotals(currency.name, from.toEpochDay(), through.toEpochDay()).map { rows ->
            rows.map { CategoryTotal(Category.valueOf(it.category), it.incomeMinor, it.expenseMinor, it.count, it.incomeCount, it.expenseCount) }
        }
    override suspend fun statistics(currency: LedgerCurrency, from: LocalDate, through: LocalDate, type: EntryType, category: Category?) =
        dao.statistics(currency.name, from.toEpochDay(), through.toEpochDay(), type.name, category?.name)
    override suspend fun find(id: String): LedgerEntry? = dao.find(id)?.toDomain()
    override suspend fun save(entry: LedgerEntry) = dao.save(entry.toEntity(clock.millis()))
    override suspend fun delete(id: String): DeletionToken? {
        val now = clock.millis()
        return if (dao.delete(id, now) == 1) DeletionToken(id, now) else null
    }
    override suspend fun undo(token: DeletionToken) = dao.undo(token.id, token.deletedAtMillis) == 1
    override suspend fun purgeDeletedBefore(epochMillis: Long) = dao.purge(epochMillis)

    companion object {
        fun likePattern(query: String): String = "%" + normalizeSearch(query.trim())
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}

internal fun EntryEntity.toDomain() = LedgerEntry(id, title, amountMinor, LedgerCurrency.valueOf(currency),
    EntryType.valueOf(type), Category.valueOf(category), LocalDate.ofEpochDay(epochDay), note, sourceType, bankId, cardLast4, eventAtMillis, customCategoryId, origin)
private fun LedgerEntry.toEntity(created: Long) = EntryEntity(id, title, amountMinor, currency.name, type.name, category.name,
    date.toEpochDay(), note, normalizeSearch(title + " " + note), created, sourceType = sourceType, bankId = bankId, cardLast4 = cardLast4,
    eventAtMillis = eventAtMillis, customCategoryId = customCategoryId, origin = origin)
private fun normalizeSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
```

## data/src/main/kotlin/com/semmyx/ledger/data/security/BiometricKeys.kt

```kotlin
package com.semmyx.ledger.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Separate per-use, strong-biometric key. Enrollment changes invalidate it.
 * Only an already unlocked Settings screen may provision / replace this key.
 */
class BiometricKeys(context: Context) {
    private val alias = "${context.packageName}.biometric.v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun provision() {
        val keyStore = store()
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(spec.build())
            generateKey()
        }
    }
    fun remove() { store().deleteEntry(alias) }
    fun authenticationCipher(): Cipher {
        val key = store().getKey(alias, null) as? SecretKey ?: error("Biometric key unavailable")
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreSecurityRepository.kt

```kotlin
package com.semmyx.ledger.data.security

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.semmyx.ledger.domain.PinPolicy
import com.semmyx.ledger.domain.PinResult
import com.semmyx.ledger.domain.SecurityRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.json.JSONObject

/** PIN plaintext is never persisted. A slow salted verifier is additionally Keystore-encrypted. */
class KeystoreSecurityRepository(
    context: Context,
    private val vault: KeystoreVault,
    private val clock: Clock,
) : SecurityRepository {
    private val resolver = context.applicationContext.contentResolver
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private fun bootCount() = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1)

    override suspend fun hasPin(): Boolean = vault.read { json ->
        val salt = json.has("pinSalt")
        val hash = json.has("pinHash")
        check(salt == hash) { "Incomplete PIN verifier" }
        salt
    }

    override suspend fun createPin(pin: CharArray) {
        try {
            require(PinPolicy.valid(pin)) { "PIN must contain six ASCII digits" }
            vault.update { json ->
                check(!json.has("pinHash")) { "PIN already configured" }
                val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val hash = derive(pin, salt)
                try {
                    json.put("pinSalt", encoder.encodeToString(salt))
                        .put("pinHash", encoder.encodeToString(hash))
                        .put("pinIterations", PinPolicy.PBKDF2_ITERATIONS)
                    resetFailures(json)
                } finally { hash.fill(0); salt.fill(0) }
            }
        } finally { pin.fill('\u0000') }
    }

    override suspend fun verifyPin(pin: CharArray): PinResult {
        try {
            require(PinPolicy.valid(pin))
            return vault.update { json ->
                check(json.has("pinHash")) { "PIN has not been configured" }
                val remaining = remainingSeconds(json)
                if (remaining > 0) return@update PinResult.Locked(remaining)
                val salt = decoder.decode(json.getString("pinSalt"))
                val expected = decoder.decode(json.getString("pinHash"))
                val iterations = json.getInt("pinIterations")
                check(iterations == PinPolicy.PBKDF2_ITERATIONS) { "Unsupported PIN parameters" }
                val actual = derive(pin, salt)
                val matches = try { MessageDigest.isEqual(expected, actual) }
                    finally { actual.fill(0); expected.fill(0); salt.fill(0) }
                if (matches) {
                    resetFailures(json)
                    PinResult.Success
                } else {
                    val failures = (json.optInt("failures", 0) + 1).coerceAtMost(1000)
                    val delayMillis = PinPolicy.delaySeconds(failures) * 1000
                    json.put("failures", failures).put("delayMillis", delayMillis)
                        .put("failedAtWall", clock.millis()).put("failedAtElapsed", SystemClock.elapsedRealtime())
                        .put("failedAtBoot", bootCount())
                    if (delayMillis > 0) PinResult.Locked(delayMillis / 1000) else PinResult.Incorrect
                }
            }
        } finally { pin.fill('\u0000') }
    }

    override suspend fun lockoutSeconds(): Long = vault.read(::remainingSeconds)

    private fun remainingSeconds(json: JSONObject): Long {
        val delay = json.optLong("delayMillis", 0).coerceIn(0, 3_600_000)
        if (delay == 0L) return 0
        val elapsed = if (json.optInt("failedAtBoot", -2) == bootCount()) {
            SystemClock.elapsedRealtime() - json.optLong("failedAtElapsed", 0)
        } else {
            // Across reboot use wall time. A backward clock change never shortens a delay.
            clock.millis() - json.optLong("failedAtWall", clock.millis())
        }
        return ((delay - elapsed.coerceAtLeast(0)).coerceAtLeast(0) + 999) / 1000
    }
    private fun resetFailures(json: JSONObject) {
        json.put("failures", 0).put("delayMillis", 0)
            .remove("failedAtWall")
        json.remove("failedAtElapsed")
        json.remove("failedAtBoot")
    }
    private fun derive(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, PinPolicy.PBKDF2_ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreVault.kt

```kotlin
package com.semmyx.ledger.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AES-256-GCM envelope encryption. This vault contains authentication verifiers,
 * lockout state and signed Play receipts, NOT the Room database. AAD binds the
 * ciphertext to this installation's package. Android backup is disabled.
 * Corruption / key loss is intentionally fatal to the operation, never a reset.
 */
class KeystoreVault(context: Context) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "security.vault"))
    private val lock = Mutex()
    private val alias = "${app.packageName}.vault.v1"
    private val aad = "$alias:1".toByteArray(Charsets.UTF_8)
    private val keyStore: KeyStore get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun key(mayCreate: Boolean): SecretKey {
        val store = keyStore
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(mayCreate) { "Existing vault key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    private fun readUnlocked(): JSONObject {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return JSONObject()
        val bytes = file.openRead().use { input ->
            // Authentication data is bounded, preventing memory exhaustion on a damaged file.
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= 1_048_576) { "Vault exceeds size limit" }
                output.write(buffer, 0, count)
            }
            output.toByteArray().also { check(it.size >= 30) { "Invalid vault size" } }
        }
        val buffer = ByteBuffer.wrap(bytes)
        check(buffer.get().toInt() == 1) { "Unsupported vault version" }
        val iv = ByteArray(12).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(mayCreate = false), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        val plaintext = cipher.doFinal(ciphertext)
        return try { JSONObject(plaintext.toString(Charsets.UTF_8)) } finally { plaintext.fill(0) }
    }

    private fun writeUnlocked(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(mayCreate = !file.baseFile.exists()))
        cipher.updateAAD(aad)
        val plaintext = value.toString().toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        val bytes = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size)
            .put(1.toByte()).put(cipher.iv).put(encrypted).array()
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    suspend fun <T> read(block: (JSONObject) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock { block(readUnlocked()) }
    }
    /** Serial read-modify-write prevents PIN and billing updates from clobbering each other. */
    suspend fun <T> update(block: (JSONObject) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val value = readUnlocked()
            val result = block(value)
            writeUnlocked(value)
            result
        }
    }
}
```

## data/src/play/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt

```kotlin
package com.semmyx.ledger.data.billing

import android.content.Context
import com.android.billingclient.api.*

internal fun createVersionedBillingClient(context: Context, listener: PurchasesUpdatedListener): BillingClient =
    BillingClient.newBuilder(context).setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()

internal fun BillingClient.queryVersionedProducts(params: QueryProductDetailsParams,
    callback: (BillingResult, List<ProductDetails>) -> Unit) {
    queryProductDetailsAsync(params) { result, response -> callback(result, response.productDetailsList) }
}
internal fun versionedPrice(details: ProductDetails): String? = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
internal fun versionedProductParams(details: ProductDetails): BillingFlowParams.ProductDetailsParams {
    val offer = requireNotNull(details.oneTimePurchaseOfferDetailsList?.firstOrNull())
    return BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(offer.offerToken).build()
}
```

## data/src/test/kotlin/com/semmyx/ledger/data/PurchaseSignatureVerifierTest.kt

```kotlin
package com.semmyx.ledger.data

import com.semmyx.ledger.data.billing.PurchaseSignatureVerifier
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class PurchaseSignatureVerifierTest {
    @Test fun acceptsCorrectSignatureAndRejectsTampering() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val payload = "{\"productId\":\"ledger_plus\"}"
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private); update(payload.toByteArray()); Base64.getEncoder().encodeToString(sign())
        }
        val verifier = PurchaseSignatureVerifier(Base64.getEncoder().encodeToString(keys.public.encoded))
        assertTrue(verifier.configured)
        assertTrue(verifier.verify(payload, signature))
        assertFalse(verifier.verify(payload + " ", signature))
        assertFalse(verifier.verify(payload, "invalid"))
    }
    @Test fun missingLicenseKeyFailsClosed() {
        val verifier = PurchaseSignatureVerifier("")
        assertFalse(verifier.configured)
        assertFalse(verifier.verify("{}", ""))
    }
}
```

## data/src/test/kotlin/com/semmyx/ledger/data/SearchPatternTest.kt

```kotlin
package com.semmyx.ledger.data

import com.semmyx.ledger.data.repository.RoomEntryRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPatternTest {
    @Test fun wildcardsAreLiteralsAndUkrainianIsCaseFolded() {
        assertEquals("%кава%", RoomEntryRepository.likePattern(" КАВА "))
        assertEquals("%50\\%\\_off%", RoomEntryRepository.likePattern("50%_off"))
    }
}
```

## data/src/test/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifierTest.kt

```kotlin
package com.semmyx.ledger.data.billing

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperSubscriptionVerifierTest {
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    /** Mirrors tools/issue_activation_code.py's wire format: "v1|installationId|productId|issuedAt|expiresAt". */
    private fun token(installationId: String, productId: String, expiresAtMillis: Long?, issuedAtMillis: Long = 1L): String {
        val payload = listOf("v1", installationId, productId, issuedAtMillis.toString(), expiresAtMillis?.toString() ?: "")
            .joinToString("|").toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private); update(payload); sign()
        }
        val encoder = Base64.getUrlEncoder()
        return "${encoder.encodeToString(payload)}.${encoder.encodeToString(signature)}"
    }

    @Test
    fun acceptsAValidUnexpiredCodeForTheRightInstallation() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        assertTrue(verifier.configured)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        val result = verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000)
        assertEquals(10_000L, result?.expiresAtMillis)
        assertEquals("AAAA-BBBB-CCCC", result?.installationId)
        assertEquals("ledger_plus", result?.productId)
    }

    @Test
    fun acceptsACodeWithNoExpiryAtAll() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = null)
        val result = verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 999_999_999)
        assertNull(result?.expiresAtMillis)
        assertEquals("AAAA-BBBB-CCCC", result?.installationId)
    }

    @Test
    fun rejectsAnExpiredCode() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 1_000)
        assertNull(verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun rejectsACodeIssuedForADifferentInstallation() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        assertNull(verifier.verify(code, "ZZZZ-ZZZZ-ZZZZ", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun rejectsACodeIssuedForADifferentProduct() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        assertNull(verifier.verify(code, "AAAA-BBBB-CCCC", "other_product", nowMillis = 5_000))
    }

    @Test
    fun rejectsATamperedPayload() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        val parts = code.split(".")
        val forgedPayload = Base64.getUrlEncoder().encodeToString(
            listOf("v1", "AAAA-BBBB-CCCC", "ledger_plus", "0", "99999999999").joinToString("|").toByteArray(Charsets.UTF_8),
        )
        assertNull(verifier.verify("$forgedPayload.${parts[1]}", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun rejectsAMalformedCodeWithoutCrashing() {
        val verifier = DeveloperSubscriptionVerifier(publicKeyBase64)
        assertNull(verifier.verify("not-a-real-code", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
        assertNull(verifier.verify("", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
        assertNull(verifier.verify(".", "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }

    @Test
    fun unconfiguredVerifierRejectsEverything() {
        val verifier = DeveloperSubscriptionVerifier("")
        assertFalse(verifier.configured)
        val code = token("AAAA-BBBB-CCCC", "ledger_plus", expiresAtMillis = 10_000)
        assertNull(verifier.verify(code, "AAAA-BBBB-CCCC", "ledger_plus", nowMillis = 5_000))
    }
}
```

## domain/build.gradle.kts

```kotlin
plugins { id("org.jetbrains.kotlin.jvm") }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/Analytics.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.LocalDate

data class CategoryTotal(val category: Category, val incomeMinor: Long, val expenseMinor: Long, val count: Int, val incomeCount: Int = 0, val expenseCount: Int = 0)

/** Zero is the all-time sentinel; calendar windows never start before supported entries. */
object FinanceWindow {
    val EARLIEST: LocalDate = LocalDate.of(2000, 1, 1)
    fun from(days: Int, today: LocalDate): LocalDate = if (days == 0) EARLIEST else today.minusDays(days.toLong() - 1)
}

object TrialPolicy {
    fun validDays(days: Int) = days == 7 || days == 14
    fun active(start: Long, end: Long, now: Long, lastSeen: Long): Boolean =
        start > 0 && end > start && now >= start && now >= lastSeen && now < end
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/CinematicAnalytics.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.LocalDate
import java.time.YearMonth
import java.math.BigDecimal
import java.math.RoundingMode

data class CashBucket(val from: LocalDate, val through: LocalDate, val incomeMinor: Long, val expenseMinor: Long)
data class CategorySlice(val category: Category, val minor: Long, val count: Int, val basisPoints: Int)

object AnalyticsMath {
    /** Monetary values stay Long. Rounded display percentages use largest remainder: total is 100.00%. */
    fun slices(totals: List<CategoryTotal>, type: EntryType): List<CategorySlice> {
        val nonzero = totals.map { total ->
            Triple(total.category, if (type == EntryType.EXPENSE) total.expenseMinor else total.incomeMinor,
                if (type == EntryType.EXPENSE) total.expenseCount else total.incomeCount)
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        val total = nonzero.fold(0L) { acc, row -> Math.addExact(acc, row.second) }
        if (total == 0L) return emptyList()
        val exact = nonzero.map { BigDecimal.valueOf(it.second).multiply(BigDecimal(10000))
            .divide(BigDecimal.valueOf(total), 20, RoundingMode.DOWN) }
        val rounded = exact.map { it.toInt() }.toMutableList()
        val remainder = 10000 - rounded.sum()
        exact.indices.sortedByDescending { exact[it].subtract(BigDecimal(rounded[it])) }.take(remainder)
            .forEach { rounded[it]++ }
        return nonzero.mapIndexed { i, row -> CategorySlice(row.first, row.second, row.third, rounded[i]) }
    }
    /** Screenshot structure: calendar-week fragments inside a chosen month, including zero-valued buckets. */
    fun buckets(activity: List<DailyActivity>, month: YearMonth?, through: LocalDate): List<CashBucket> {
        if (month == null) return activity.groupBy { YearMonth.from(it.date) }.toSortedMap().map { (m, rows) ->
            CashBucket(m.atDay(1), minOf(m.atEndOfMonth(), through), rows.sumOf { it.incomeMinor }, rows.sumOf { it.expenseMinor })
        }
        if (through.isBefore(month.atDay(1))) return emptyList()
        val result = mutableListOf<CashBucket>()
        var start = month.atDay(1)
        while (!start.isAfter(through)) {
            val end = minOf(start.plusDays(7L - start.dayOfWeek.value), through)
            val rows = activity.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            result += CashBucket(start, end, rows.sumOf { it.incomeMinor }, rows.sumOf { it.expenseMinor })
            start = end.plusDays(1)
        }
        return result
    }
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/ColorPreferences.kt

```kotlin
package com.semmyx.ledger.domain

object ColorPreferences {
    val roles = linkedMapOf(
        "accent" to "#6FAEFF", "background" to "#000000", "surface" to "#1C201E",
        "text" to "#F3F5F7", "muted" to "#A3ABB5", "income" to "#48D597", "expense" to "#FF647C",
        "chartTop" to "#FF9AAC", "chartBottom" to "#E74765", "grid" to "#343B45", "icons" to "#6FAEFF", "error" to "#FF647C"
    )
    fun categoryKey(category: Category, type: EntryType): String = "category:${type.name}:${category.name}"
    fun validKey(key: String): Boolean = key in roles || Category.entries.any { category ->
        EntryType.entries.any { key == categoryKey(category, it) }
    }
    fun normalizeHex(value: String): String? {
        val cleaned = value.trim().removePrefix("#")
        return if (Regex("[0-9a-fA-F]{6}").matches(cleaned)) "#" + cleaned.uppercase() else null
    }
    fun defaultHex(key: String): String {
        roles[key]?.let { return it }
        val income = key.startsWith("category:INCOME:")
        val category = Category.entries.firstOrNull { key.endsWith(":" + it.name) } ?: return "#6FAEFF"
        val colors = if (income) listOf("#48D597", "#55BF8C", "#6BC9A5", "#37C58E", "#7DD9AB", "#19B950", "#5CAC85", "#35CE75", "#72D4A6", "#7ACC73")
            else listOf("#EF5277", "#FC7969", "#D45368", "#F78D9F", "#E54775", "#C96268", "#AB6976", "#D96570", "#F37591", "#B75668")
        return colors[category.ordinal]
    }
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/Models.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.LocalDate

/** All supported currencies have exactly two fraction digits. Never store money as Double. */
enum class LedgerCurrency { UAH, USD, EUR, GBP, PLN }
enum class EntryType { INCOME, EXPENSE }
enum class Category { FOOD, TRANSPORT, HOME, HEALTH, LEISURE, SALARY, OTHER, TRANSFER, DINING, CASHBACK }
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class AppLanguage(val tag: String) { ENGLISH("en"), UKRAINIAN("uk") }

data class UserPreferences(
    val displayName: String = "",
    val currency: LedgerCurrency = LedgerCurrency.UAH,
    val theme: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val onboardingComplete: Boolean = false,
    val biometricsEnabled: Boolean = false,
    // Gated separately from entitlement: a premium user can still hide the mascot.
    val premiumAssistantEnabled: Boolean = false,
    val mascotSkin: String = "gray",
    val mascotAnimations: Boolean = true,
    val refreshRateHz: Int = 0,
    val colorOverrides: Map<String, String> = emptyMap(),
)

data class LedgerEntry(
    val id: String,
    val title: String,
    val amountMinor: Long,
    val currency: LedgerCurrency,
    val type: EntryType,
    val category: Category,
    val date: LocalDate,
    val note: String = "",
    val sourceType: String = "UNKNOWN",
    val bankId: String? = null,
    val cardLast4: String? = null,
    val eventAtMillis: Long? = null,
    val customCategoryId: String? = null,
    val origin: String = "MANUAL",
)

data class EntryFilter(
    val currency: LedgerCurrency,
    val query: String = "",
    val type: EntryType? = null,
    val limit: Int = 50,
    val category: Category? = null,
    val from: LocalDate? = null,
    val through: LocalDate? = null,
)

data class Balance(val incomeMinor: Long = 0, val expenseMinor: Long = 0, val entryCount: Int = 0) {
    val netMinor: Long get() = Math.subtractExact(incomeMinor, expenseMinor)
}

data class DailyActivity(val date: LocalDate, val incomeMinor: Long, val expenseMinor: Long)
data class DeletionToken(val id: String, val deletedAtMillis: Long)
/** Informational NBU rate, not a transaction conversion. */
data class ExchangeRate(val currency: LedgerCurrency, val uahPerUnit: Double, val date: LocalDate, val fetchedAtMillis: Long)

enum class ValidationIssue { TITLE, AMOUNT, NOTE, DATE }
class EntryValidationException(val issue: ValidationIssue) : IllegalArgumentException(issue.name)
sealed interface PinResult {
    data object Success : PinResult
    data object Incorrect : PinResult
    data class Locked(val remainingSeconds: Long) : PinResult
}

enum class BillingIssue { UNAVAILABLE, CONFIGURATION, NETWORK, PENDING, VERIFICATION, ACKNOWLEDGEMENT, ACTIVATION_CODE }

/** Where the currently active entitlement (if any) came from. */
enum class PremiumSource { GOOGLE_PLAY, OFFICIAL_DEVELOPER, TRIAL }

data class PremiumState(
    // True once a Google Play billing public key is baked into this build.
    val configured: Boolean = false,
    val entitled: Boolean = false,
    val formattedPrice: String? = null,
    val busy: Boolean = false,
    val issue: BillingIssue? = null,
    val source: PremiumSource? = null,
    // Only set for a developer-issued activation code; Google Play grants do not expire here.
    val expiresAtMillis: Long? = null,
    // True once the developer's activation public key is baked into this build.
    val developerPurchaseEnabled: Boolean = false,
    // Stable per-installation id the developer binds an activation code to.
    val developerInstallationId: String = "",
    val trialAvailable: Boolean = false,
)
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/Repositories.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

interface EntryRepository {
    fun observeEntries(filter: EntryFilter): Flow<List<LedgerEntry>>
    fun observeBalance(currency: LedgerCurrency): Flow<Balance>
    fun observeActivity(currency: LedgerCurrency, from: LocalDate, through: LocalDate): Flow<List<DailyActivity>>
    fun observeCategoryTotals(currency: LedgerCurrency, from: LocalDate, through: LocalDate): Flow<List<CategoryTotal>> =
        observeEntries(EntryFilter(currency, limit = Int.MAX_VALUE)).map { rows ->
            rows.filter { !it.date.isBefore(from) && !it.date.isAfter(through) }.groupBy { it.category }.map { (category, values) ->
                CategoryTotal(category, values.filter { it.type == EntryType.INCOME }.sumOf { it.amountMinor },
                    values.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountMinor }, values.size,
                    values.count { it.type == EntryType.INCOME }, values.count { it.type == EntryType.EXPENSE })
            }
        }
    suspend fun statistics(currency: LedgerCurrency, from: LocalDate, through: LocalDate, type: EntryType, category: Category?): Long =
        observeCategoryTotals(currency, from, through).first().filter { category == null || it.category == category }
            .sumOf { if (type == EntryType.EXPENSE) it.expenseMinor else it.incomeMinor }
    suspend fun find(id: String): LedgerEntry?
    suspend fun save(entry: LedgerEntry)
    suspend fun delete(id: String): DeletionToken?
    suspend fun undo(token: DeletionToken): Boolean
    suspend fun purgeDeletedBefore(epochMillis: Long)
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setName(name: String)
    suspend fun setCurrency(currency: LedgerCurrency)
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setLanguage(language: AppLanguage)
    suspend fun completeOnboarding()
    suspend fun setBiometrics(enabled: Boolean)
    suspend fun setPremiumAssistant(enabled: Boolean)
    suspend fun setMascotSkin(value: String) { }
    suspend fun setMascotAnimations(enabled: Boolean) { }
    suspend fun setRefreshRate(hz: Int) { }
    suspend fun setColor(key: String, hex: String?) { }
    suspend fun resetColors() { }
}

interface SecurityRepository {
    suspend fun hasPin(): Boolean
    suspend fun createPin(pin: CharArray)
    suspend fun verifyPin(pin: CharArray): PinResult
    suspend fun lockoutSeconds(): Long
}

interface RateRepository {
    val rates: Flow<List<ExchangeRate>>
    suspend fun refresh()
}

/**
 * The Activity needed for a Play purchase is deliberately NOT part of the domain.
 *
 * Two independent entitlement sources exist for the same non-consumable
 * product: Google Play, and a developer-issued offline activation code (for
 * testing before/alongside a Play listing, or for direct manual sales, e.g.
 * over Telegram). Either one alone can grant `entitled`.
 */
interface PremiumRepository {
    val state: StateFlow<PremiumState>
    suspend fun refresh()
    /** Verifies and, if valid, stores a developer-issued activation code. Returns true if it granted access. */
    suspend fun activateDeveloperToken(token: String): Boolean
    /** Stable id to hand the developer so they can bind a code to this installation. */
    suspend fun developerInstallationId(): String
    suspend fun startTrial(days: Int): Boolean = false
    fun clearIssue()
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/UseCases.kt

```kotlin
package com.semmyx.ledger.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

object Money {
    const val MAX_ENTRY_MINOR = 9_999_999_999L
    /** Accept comma OR dot decimals, not ambiguous grouping or rounding. */
    fun parseMinor(input: String): Long? {
        val normalized = input.trim().replace(',', '.')
        if (!Regex("[0-9]{1,8}(\\.[0-9]{1,2})?").matches(normalized)) return null
        return try {
            BigDecimal(normalized).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact().takeIf { it in 1..MAX_ENTRY_MINOR }
        } catch (_: ArithmeticException) { null }
    }
    fun editable(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
}

class SaveEntry(private val repository: EntryRepository, private val clock: Clock) {
    suspend operator fun invoke(entry: LedgerEntry) {
        val cleaned = entry.copy(title = entry.title.trim(), note = entry.note.trim())
        if (cleaned.title.isEmpty() || cleaned.title.length > 60) throw EntryValidationException(ValidationIssue.TITLE)
        if (cleaned.amountMinor !in 1..Money.MAX_ENTRY_MINOR) throw EntryValidationException(ValidationIssue.AMOUNT)
        if (cleaned.note.length > 240) throw EntryValidationException(ValidationIssue.NOTE)
        if (cleaned.date.isAfter(LocalDate.now(clock)) || cleaned.date.year < 2000) throw EntryValidationException(ValidationIssue.DATE)
        repository.save(cleaned)
    }
}

data class DashboardSnapshot(
    val entries: List<LedgerEntry> = emptyList(),
    val balance: Balance = Balance(),
    val activity: List<DailyActivity> = emptyList(),
)

class ObserveDashboard(private val repository: EntryRepository) {
    operator fun invoke(filter: EntryFilter, from: LocalDate, through: LocalDate): Flow<DashboardSnapshot> =
        combine(repository.observeEntries(filter), repository.observeBalance(filter.currency),
            repository.observeActivity(filter.currency, from, through)) { entries, balance, activity ->
            val byDate = activity.associateBy { it.date }
            val days = if (java.time.temporal.ChronoUnit.DAYS.between(from, through) > 90) activity else generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(through) }
                .map { byDate[it] ?: DailyActivity(it, 0, 0) }.toList()
            DashboardSnapshot(entries, balance, days)
        }
}

/** Persisted escalating delay limits brute force; the repository seals its state. */
object PinPolicy {
    const val LENGTH = 6
    const val PBKDF2_ITERATIONS = 210_000
    fun valid(pin: CharArray): Boolean = pin.size == LENGTH && pin.all { it in '0'..'9' }
    fun delaySeconds(failures: Int): Long = if (failures < 5) 0L else
        (30L * (1L shl (failures - 5).coerceAtMost(7))).coerceAtMost(3600L)
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankParser.kt

```kotlin
package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class PaymentSource { UNKNOWN, CASH, CARD }
data class Bank(val id: String, val name: String, val packages: Set<String>, val senders: Set<String>)
object Banks {
    val all = listOf(
        Bank("privat", "ПриватБанк", setOf("com.pb", "com.privatbank.ap24"), setOf("privatbank", "privat", "privat24", "3700")),
        Bank("mono", "monobank", setOf("com.ftband.mono"), setOf("monobank", "mono")),
        Bank("sense", "Sense Bank", setOf("ua.alfabank.mobile.android", "ua.sensebank.mobile.android"), setOf("sensebank", "sense", "alfabank")),
        Bank("abank", "А-Банк", setOf("ua.com.abank", "ua.com.abank.android"), setOf("abank", "a-bank", "а-банк")),
        Bank("oschad", "Ощадбанк", setOf("ua.oschadbank.online"), setOf("oschadbank", "oschad", "ощадбанк")),
        Bank("pumb", "ПУМБ", setOf("com.fuib.android.spot.online"), setOf("pumb", "fuib", "пумб")),
        Bank("revolut", "Revolut", setOf("com.revolut.revolut"), setOf("revolut")),
        Bank("wise", "Wise", setOf("com.transferwise.android"), setOf("wise", "transferwise")),
        Bank("n26", "N26", setOf("de.number26.android"), setOf("n26"))
    )
    fun byPackage(value: String) = all.firstOrNull { value in it.packages }
    fun bySender(value: String) = all.firstOrNull { value.trim().lowercase(Locale.ROOT) in it.senders }
    fun name(id: String?) = all.firstOrNull { it.id == id }?.name ?: id.orEmpty()
}
data class ParsedBankOperation(val bankId: String, val amountMinor: Long, val currency: LedgerCurrency,
    val type: EntryType, val merchant: String, val cardLast4: String?, val happenedAtMillis: Long,
    val timeFromMessage: Boolean, val transfer: Boolean, val confident: Boolean, val category: Category)

/** Pure offline parser. OTP/ads/refusals are ignored; ambiguous texts never become automatic expenses. */
object BankParser {
    private val amount = Regex("([+-]?[0-9]{1,3}(?:[ \\u00A0\\u202F][0-9]{3})+(?:[.,][0-9]{1,2})?|[+-]?[0-9]+(?:[.,][0-9]{1,2})?)\\s*(UAH|USD|EUR|грн\\.?|гривень|гривні|₴|\\$|€)", RegexOption.IGNORE_CASE)
    private val amountPrefix = Regex("(UAH|USD|EUR|\\$|€|₴)\\s*([+-]?[0-9]+(?:[.,][0-9]{1,2})?)", RegexOption.IGNORE_CASE)
    private val expense = Regex("(?:списан[оі]?|витрат[аи]|оплат[аи]|покупк[аи]|купівл[яі]|purchase|paid|payment|spent|debit(?:ed)?)", RegexOption.IGNORE_CASE)
    private val income = Regex("(?:зарахован[оі]?|поповнення|пополнение|надходження|зачислен[оа]?|received|credited|top[ -]?up|refund|повернення)", RegexOption.IGNORE_CASE)
    private val transfer = Regex("(?:переказ|перевод|transfer|p2p)", RegexOption.IGNORE_CASE)
    private val otp = Regex("(?:одноразов|one[ -]?time|verification code|код\\s+(?:входу|підтвердження|подтверждения)|otp|пароль|password)", RegexOption.IGNORE_CASE)
    private val declined = Regex("(?:відхилен|отклонен|declined|failed|невдал|недостатньо|insufficient)", RegexOption.IGNORE_CASE)
    private val card = Regex("(?:карт[аикоуі]*|card|[＊*•]{2,})\\s*[:№#*•-]*\\s*([0-9]{4})(?![0-9])", RegexOption.IGNORE_CASE)
    private val merchant = Regex("(?:merchant|магазин|мерчант|торговець|at|у|в)\\s*[:=-]?\\s+([^;\\n]+)", RegexOption.IGNORE_CASE)
    private val time = Regex("([0-9]{2}[./][0-9]{2}[./][0-9]{4})[ ,T]+([0-9]{2}:[0-9]{2}(?::[0-9]{2})?)")
    fun parse(bank: Bank, text: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): ParsedBankOperation? {
        if (text.length > 8000 || otp.containsMatchIn(text) || declined.containsMatchIn(text)) return null
        val matches = amount.findAll(text).filter { found ->
            val prefix = text.substring(maxOf(0, found.range.first - 35), found.range.first)
            !Regex("(?:баланс|залишок|остаток|balance|available|ліміт|limit)\\s*[:=-]?\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix)
        }.toList()
        val prefixes = amountPrefix.findAll(text).filter { found ->
            val prefix = text.substring(maxOf(0, found.range.first - 35), found.range.first)
            !Regex("(?:баланс|залишок|остаток|balance|available|ліміт|limit)\\s*[:=-]?\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix)
        }.toList()
        if (matches.isEmpty() && prefixes.isEmpty()) return null
        val debit = expense.containsMatchIn(text); val credit = income.containsMatchIn(text); val moving = transfer.containsMatchIn(text)
        if (!debit && !credit && !moving && !text.contains(Regex("[+-]\\s*[0-9]"))) return null
        val selected = matches.firstOrNull()
        val rawAmount = (selected?.groupValues?.get(1) ?: prefixes.first().groupValues[2]).replace(Regex("[ \\u00A0\\u202F]"), "").replace(',', '.')
        val money = try { BigDecimal(rawAmount).abs().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() } catch (_: Exception) { return null }
        if (money <= 0 || money > Money.MAX_ENTRY_MINOR) return null
        val cur = (selected?.groupValues?.get(2) ?: prefixes.first().groupValues[1]).uppercase(Locale.ROOT)
        val currency = when (cur) { "USD", "$" -> LedgerCurrency.USD; "EUR", "€" -> LedgerCurrency.EUR; else -> LedgerCurrency.UAH }
        val type = if (credit && !debit || rawAmount.startsWith("+")) EntryType.INCOME else EntryType.EXPENSE
        var shop = merchant.find(text)?.groupValues?.getOrNull(1)?.let {
            it.replace(Regex("(?:баланс|залишок|balance|карт[аикоуі]*|card|[0-9]{2}[./][0-9]{2}[./][0-9]{4}).*$", RegexOption.IGNORE_CASE), "").trim().take(60)
        }.orEmpty()
        if (shop.isBlank()) {
            val excluded = Regex("(?:баланс|залишок|остаток|balance|available|карт[аикоуі]*|card|[0-9]{2}:[0-9]{2})", RegexOption.IGNORE_CASE)
            shop = text.lines().map { line -> amount.replace(amountPrefix.replace(line, ""), "").trim(' ', ':', '-', '+') }
                .firstOrNull { line -> line.length in 2..60 && !line.equals(bank.name, ignoreCase = true) && !excluded.containsMatchIn(line) &&
                    !expense.matches(line) && !income.matches(line) && !transfer.matches(line) && !line.all { it.isDigit() || it.isWhitespace() } }.orEmpty()
        }
        val timestamp = time.find(text)?.let { match ->
            try {
                val date = LocalDate.parse(match.groupValues[1].replace('/', '.'), DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT))
                val clock = LocalTime.parse(match.groupValues[2])
                date.atTime(clock).atZone(zone).toInstant().toEpochMilli().takeIf { it <= receivedAt + 300_000L && it >= receivedAt - 366L * 86400000L }
            } catch (_: Exception) { null }
        }
        return ParsedBankOperation(bank.id, money, currency, type, shop, card.find(text)?.groupValues?.getOrNull(1),
            timestamp ?: receivedAt, timestamp != null, moving, matches.size + prefixes.size == 1 && debit != credit && !moving && shop.isNotBlank() && (merchant.containsMatchIn(text) || categorize(shop) != Category.OTHER), categorize(shop))
    }
    fun categorize(merchant: String): Category {
        val value = merchant.lowercase(Locale.ROOT)
        return when {
            listOf("атб", "atb", "сільпо", "silpo", "ашан", "auchan", "novus", "metro").any(value::contains) -> Category.FOOD
            listOf("wog", "okko", "ugo", "uber", "bolt", "uklon").any(value::contains) -> Category.TRANSPORT
            listOf("steam", "megogo", "netflix", "spotify", "cinema").any(value::contains) -> Category.LEISURE
            listOf("аптек", "pharmacy", "clinic").any(value::contains) -> Category.HEALTH
            listOf("restaurant", "кафе", "cafe", "mcdonald", "kfc").any(value::contains) -> Category.DINING
            else -> Category.OTHER
        }
    }
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankRepository.kt

```kotlin
package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

data class CustomBankCategory(val id: String, val name: String, val colorHex: String)
data class BankSourceSpend(val sourceType: String, val bankId: String?, val totalMinor: Long)
data class BankCategorySpend(val category: Category, val customCategoryId: String?, val customName: String?, val colorHex: String?, val totalMinor: Long)
data class ImportReview(val id: String, val bankId: String, val amountMinor: Long, val currency: LedgerCurrency,
    val type: EntryType, val merchant: String, val cardLast4: String?, val eventAtMillis: Long, val timeFromMessage: Boolean,
    val transfer: Boolean, val status: String)
data class ImportDiagnostics(val connected: Boolean = false, val notificationCount: Int = 0, val bankMessageCount: Int = 0,
    val lastCode: String = "WAITING", val lastPackage: String = "", val lastAtMillis: Long = 0)
interface BankRepository {
    val diagnostics: Flow<ImportDiagnostics>
    fun configuredPackage(bankId: String): String
    fun setPackage(bankId: String, packageName: String)
    suspend fun importText(bankId: String, text: String): Boolean
    var enabled: Boolean
    var autoImport: Boolean
    fun categories(): Flow<List<CustomBankCategory>>
    fun reviews(): Flow<List<ImportReview>>
    fun sources(currency: LedgerCurrency, from: LocalDate, through: LocalDate): Flow<List<BankSourceSpend>>
    fun categorySpending(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?): Flow<List<BankCategorySpend>>
    fun recent(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?): Flow<List<LedgerEntry>>
    suspend fun accept(id: String, type: EntryType)
    suspend fun ignore(id: String)
    suspend fun createCategory(name: String, hex: String)
    suspend fun clearHistory()
    suspend fun exportEntries(): List<LedgerEntry>
    fun budget(currency: LedgerCurrency): Long
    fun setBudget(currency: LedgerCurrency, minor: Long)
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/BankParserTest.kt

```kotlin
package com.semmyx.ledger.domain

import com.semmyx.ledger.domain.bank.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class BankParserTest {
    private val bank = Banks.all.first { it.id == "mono" }
    private val at = Instant.parse("2026-10-07T20:00:00Z").toEpochMilli()
    @Test fun debitExtractsAmountShopCardAndDate() {
        val parsed = BankParser.parse(bank, "Оплата 1 200,50 грн у АТБ; картка *1234; 07.10.2026 22:59", at, ZoneId.of("Europe/Kyiv"))!!
        assertEquals(120050L, parsed.amountMinor)
        assertEquals(EntryType.EXPENSE, parsed.type)
        assertEquals("1234", parsed.cardLast4)
        assertEquals(Category.FOOD, parsed.category)
        assertTrue(parsed.confident)
        assertTrue(parsed.timeFromMessage)
    }
    @Test fun ignoresBalanceOtpAndDeclined() {
        assertNull(BankParser.parse(bank, "Баланс 1200 грн", at))
        assertNull(BankParser.parse(bank, "Код підтвердження оплати 100 грн 123456", at))
        assertNull(BankParser.parse(bank, "Payment declined 10 EUR at Steam", at))
    }
    @Test fun doesNotChooseBalanceAsPurchase() {
        assertEquals(10000L, BankParser.parse(bank, "Оплата 100 грн у АТБ; баланс 900 грн", at)!!.amountMinor)
    }
    @Test fun transfersRequireReview() { assertFalse(BankParser.parse(bank, "Переказ 100 грн", at)!!.confident) }
    @Test fun currencyPrefixAndIncome() {
        assertEquals(1250L, BankParser.parse(bank, "Payment \$12.50 at Steam", at)!!.amountMinor)
        assertEquals(LedgerCurrency.USD, BankParser.parse(bank, "Payment \$12.50 at Steam", at)!!.currency)
        assertEquals(EntryType.INCOME, BankParser.parse(bank, "Зараховано 2000 грн", at)!!.type)
    }
    @Test fun receiptTimeIsNotInventedBankTime() {
        val value = BankParser.parse(bank, "Оплата 100 грн у АТБ", at)!!
        assertEquals(at, value.happenedAtMillis); assertFalse(value.timeFromMessage)
    }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/CinematicAnalyticsTest.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class CinematicAnalyticsTest {
    @Test fun zeroHasNoInventedSlice() {
        assertTrue(AnalyticsMath.slices(emptyList(), EntryType.EXPENSE).isEmpty())
        assertTrue(AnalyticsMath.slices(listOf(CategoryTotal(Category.FOOD, 0, 0, 0)), EntryType.INCOME).isEmpty())
    }
    @Test fun oneCategoryIsExactlyOneHundredPercent() {
        val slice = AnalyticsMath.slices(listOf(CategoryTotal(Category.SALARY, 104300, 0, 13, 13, 0)), EntryType.INCOME).single()
        assertEquals(10000, slice.basisPoints)
        assertEquals(13, slice.count)
    }
    @Test fun thirdsAddToOneHundredWithoutFloatingPointDrift() {
        val values = listOf(Category.FOOD, Category.HOME, Category.HEALTH).map { CategoryTotal(it, 0, 100, 1, 0, 1) }
        val slices = AnalyticsMath.slices(values, EntryType.EXPENSE)
        assertEquals(10000, slices.sumOf { it.basisPoints })
        assertEquals(listOf(3334, 3333, 3333), slices.map { it.basisPoints })
    }
    @Test fun countIsSpecificToTransactionType() {
        val total = CategoryTotal(Category.OTHER, 50000, 70000, 15, 13, 2)
        assertEquals(13, AnalyticsMath.slices(listOf(total), EntryType.INCOME).single().count)
        assertEquals(2, AnalyticsMath.slices(listOf(total), EntryType.EXPENSE).single().count)
    }
    @Test fun august2026MatchesReferenceWeekFragments() {
        val buckets = AnalyticsMath.buckets(emptyList(), YearMonth.of(2026, 8), LocalDate.of(2026, 8, 31))
        assertEquals(listOf(1 to 2, 3 to 9, 10 to 16, 17 to 23, 24 to 30, 31 to 31),
            buckets.map { it.from.dayOfMonth to it.through.dayOfMonth })
        assertTrue(buckets.all { it.expenseMinor == 0L })
    }
    @Test fun leapMonthNeverLeaksIntoMarch() {
        val buckets = AnalyticsMath.buckets(emptyList(), YearMonth.of(2024, 2), LocalDate.of(2024, 2, 29))
        assertEquals(LocalDate.of(2024, 2, 29), buckets.last().through)
    }
    @Test fun weekTotalsEqualSourceTotals() {
        val rows = listOf(DailyActivity(LocalDate.of(2026, 8, 1), 100, 200), DailyActivity(LocalDate.of(2026, 8, 8), 400, 600))
        val buckets = AnalyticsMath.buckets(rows, YearMonth.of(2026, 8), LocalDate.of(2026, 8, 31))
        assertEquals(800L, buckets.sumOf { it.expenseMinor })
        assertEquals(500L, buckets.sumOf { it.incomeMinor })
    }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/ColorPreferencesTest.kt

```kotlin
package com.semmyx.ledger.domain

import org.junit.Assert.*
import org.junit.Test

class ColorPreferencesTest {
    @Test fun normalizeValidHex() {
        assertEquals("#6FAEFF", ColorPreferences.normalizeHex(" 6faeff "))
        assertEquals("#000000", ColorPreferences.normalizeHex("#000000"))
        assertEquals("#FFFFFF", ColorPreferences.normalizeHex("ffffff"))
    }
    @Test fun rejectMalformedOrTransparentColors() {
        listOf("", "#fff", "#GG0011", "#FF001122", "red", "#12 3456").forEach { assertNull(ColorPreferences.normalizeHex(it)) }
    }
    @Test fun brandAndFinancialColorsAreIndependent() {
        assertEquals("#6FAEFF", ColorPreferences.defaultHex("accent"))
        assertEquals("#48D597", ColorPreferences.defaultHex("income"))
        assertEquals("#FF647C", ColorPreferences.defaultHex("expense"))
    }
    @Test fun eachCategoryHasSeparateExpenseAndIncomeKeys() {
        Category.entries.forEach { category ->
            val expense = ColorPreferences.categoryKey(category, EntryType.EXPENSE)
            val income = ColorPreferences.categoryKey(category, EntryType.INCOME)
            assertNotEquals(expense, income)
            listOf(expense, income).forEach { key ->
                assertTrue(ColorPreferences.validKey(key))
                assertNotNull(ColorPreferences.normalizeHex(ColorPreferences.defaultHex(key)))
            }
        }
        assertFalse(ColorPreferences.validKey("category:EXPENSE:UNKNOWN"))
        assertFalse(ColorPreferences.validKey("arbitrary"))
    }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/LedgerUseCasesTest.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LedgerUseCasesTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC)
    private val date = LocalDate.of(2026, 9, 8)
    private fun entry(id: String = "one", currency: LedgerCurrency = LedgerCurrency.UAH, type: EntryType = EntryType.EXPENSE) =
        LedgerEntry(id, "  Coffee  ", 1250, currency, type, Category.FOOD, date, " note ")

    @Test fun saveTrimsTextAndUpdateDoesNotDuplicate() = runTest {
        val repo = MemoryEntryRepository()
        val save = SaveEntry(repo, clock)
        save(entry())
        assertEquals("Coffee", repo.find("one")?.title)
        save(entry().copy(amountMinor = 2500))
        assertEquals(1, repo.observeBalance(LedgerCurrency.UAH).first().entryCount)
        assertEquals(2500L, repo.find("one")?.amountMinor)
    }
    @Test fun futureDatesAreRejected() = runTest {
        try { SaveEntry(MemoryEntryRepository(), clock)(entry().copy(date = date.plusDays(1))); fail("Expected validation") }
        catch (error: EntryValidationException) { assertEquals(ValidationIssue.DATE, error.issue) }
    }
    @Test fun currenciesNeverMixAndChartsZeroFillMissingDays() = runTest {
        val repo = MemoryEntryRepository()
        repo.save(entry("uah"))
        repo.save(entry("usd", LedgerCurrency.USD, EntryType.INCOME).copy(amountMinor = 999999))
        val snapshot = ObserveDashboard(repo)(EntryFilter(LedgerCurrency.UAH), date.minusDays(6), date).first()
        assertEquals(7, snapshot.activity.size)
        assertEquals(0L, snapshot.balance.incomeMinor)
        assertEquals(-1250L, snapshot.balance.netMinor)
        assertEquals(0L, snapshot.activity.first().expenseMinor)
        assertEquals(1250L, snapshot.activity.last().expenseMinor)
    }
    @Test fun searchDoesNotChangeOverallTotals() = runTest {
        val repo = MemoryEntryRepository()
        repo.save(entry())
        val snapshot = ObserveDashboard(repo)(EntryFilter(LedgerCurrency.UAH, "missing"), date, date).first()
        assertTrue(snapshot.entries.isEmpty())
        assertEquals(1250L, snapshot.balance.expenseMinor)
    }
    @Test fun deleteAndUndoAreIdempotent() = runTest {
        val repo = MemoryEntryRepository()
        repo.save(entry())
        val token = requireNotNull(repo.delete("one"))
        assertNull(repo.find("one"))
        assertNull(repo.delete("one"))
        assertTrue(repo.undo(token))
        assertFalse(repo.undo(token))
        assertNotNull(repo.find("one"))
    }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/MemoryEntryRepository.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Executable fake with real filtering, totals and undo semantics; no unimplemented test methods. */
class MemoryEntryRepository : EntryRepository {
    private val items = MutableStateFlow<Map<String, LedgerEntry>>(emptyMap())
    private val deleted = mutableMapOf<String, Pair<DeletionToken, LedgerEntry>>()
    private var sequence = 0L
    override fun observeEntries(filter: EntryFilter) = items.map { rows -> rows.values.filter {
        it.currency == filter.currency && (filter.type == null || it.type == filter.type) &&
            (filter.category == null || it.category == filter.category) &&
            (filter.from == null || !it.date.isBefore(filter.from)) &&
            (filter.through == null || !it.date.isAfter(filter.through)) &&
            (it.title.contains(filter.query, true) || it.note.contains(filter.query, true))
    }.sortedByDescending { it.date }.take(filter.limit) }
    override fun observeBalance(currency: LedgerCurrency) = items.map { rows ->
        val selected = rows.values.filter { it.currency == currency }
        Balance(selected.filter { it.type == EntryType.INCOME }.sumOf { it.amountMinor },
            selected.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountMinor }, selected.size)
    }
    override fun observeActivity(currency: LedgerCurrency, from: LocalDate, through: LocalDate) = items.map { rows ->
        rows.values.filter { it.currency == currency && !it.date.isBefore(from) && !it.date.isAfter(through) }
            .groupBy { it.date }.map { (date, entries) -> DailyActivity(date,
                entries.filter { it.type == EntryType.INCOME }.sumOf { it.amountMinor },
                entries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountMinor }) }.sortedBy { it.date }
    }
    override suspend fun find(id: String) = items.value[id]
    override suspend fun save(entry: LedgerEntry) { items.value = items.value + (entry.id to entry) }
    override suspend fun delete(id: String): DeletionToken? {
        val entry = items.value[id] ?: return null
        val token = DeletionToken(id, ++sequence)
        deleted[id] = token to entry
        items.value = items.value - id
        return token
    }
    override suspend fun undo(token: DeletionToken): Boolean {
        val saved = deleted[token.id]?.takeIf { it.first == token } ?: return false
        deleted.remove(token.id)
        items.value = items.value + (token.id to saved.second)
        return true
    }
    override suspend fun purgeDeletedBefore(epochMillis: Long) { deleted.entries.removeAll { it.value.first.deletedAtMillis < epochMillis } }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/MoneyAndPolicyTest.kt

```kotlin
package com.semmyx.ledger.domain

import org.junit.Assert.*
import org.junit.Test

class MoneyAndPolicyTest {
    @Test fun decimalParsingNeverUsesBinaryFloatingPoint() {
        assertEquals(10L, Money.parseMinor("0.10"))
        assertEquals(1250L, Money.parseMinor("12,50"))
        assertEquals(100L, Money.parseMinor("1"))
        assertEquals(Money.MAX_ENTRY_MINOR, Money.parseMinor("99999999.99"))
    }
    @Test fun invalidAndAmbiguousAmountsAreRejected() {
        listOf("0", "-1", "1.001", "1,234.56", "1e3", "NaN", "", "100000000.00", "١٢").forEach { assertNull(it, Money.parseMinor(it)) }
    }
    @Test fun formatterRoundTripsMinorUnits() {
        for (minor in listOf(1L, 99L, 100L, 101L, Money.MAX_ENTRY_MINOR)) assertEquals(minor, Money.parseMinor(Money.editable(minor)))
    }
    @Test fun pinRequiresSixAsciiDigits() {
        assertTrue(PinPolicy.valid("123456".toCharArray()))
        assertFalse(PinPolicy.valid("12345".toCharArray()))
        assertFalse(PinPolicy.valid("１２３４５６".toCharArray()))
    }
    @Test fun delaysEscalateAndHaveAnUpperBound() {
        assertEquals(0L, PinPolicy.delaySeconds(4))
        assertEquals(30L, PinPolicy.delaySeconds(5))
        assertEquals(60L, PinPolicy.delaySeconds(6))
        assertEquals(3600L, PinPolicy.delaySeconds(1000))
    }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/UpgradePolicyTest.kt

```kotlin
package com.semmyx.ledger.domain

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class UpgradePolicyTest {
    @Test fun trialDurationsAndBoundary() {
        assertTrue(TrialPolicy.validDays(7)); assertTrue(TrialPolicy.validDays(14))
        assertFalse(TrialPolicy.validDays(30)); assertFalse(TrialPolicy.validDays(0))
        assertTrue(TrialPolicy.active(100, 200, 199, 150))
        assertFalse(TrialPolicy.active(100, 200, 200, 150))
        assertFalse(TrialPolicy.active(100, 200, 149, 150))
        assertFalse(TrialPolicy.active(0, 200, 100, 100))
    }
    @Test fun categoryDetailsAndSumAreNotLimitedToFirstFifty() = runTest {
        val repository = MemoryEntryRepository()
        val day = LocalDate.of(2026, 10, 4)
        repeat(125) { id -> repository.save(LedgerEntry("$id", "Food $id", 100,
            LedgerCurrency.UAH, EntryType.EXPENSE, Category.FOOD, day)) }
        repository.save(LedgerEntry("other", "Taxi", 9000, LedgerCurrency.UAH, EntryType.EXPENSE, Category.TRANSPORT, day))
        repository.save(LedgerEntry("old", "Food last month", 7000, LedgerCurrency.UAH, EntryType.EXPENSE, Category.FOOD, day.minusMonths(1)))
        val filter = EntryFilter(LedgerCurrency.UAH, limit = 51, category = Category.FOOD, from = day.withDayOfMonth(1), through = day)
        assertEquals(51, repository.observeEntries(filter).first().size)
        assertEquals(125, repository.observeEntries(filter.copy(limit = 150)).first().size)
        assertEquals(12500L, repository.statistics(LedgerCurrency.UAH, day.withDayOfMonth(1), day, EntryType.EXPENSE, Category.FOOD))
        assertEquals(19500L, repository.statistics(LedgerCurrency.UAH, FinanceWindow.EARLIEST, day, EntryType.EXPENSE, Category.FOOD))
    }
}
```

## gradle.properties

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
# The Android Gradle Plugin warns (and on some versions/paths can fail) when the
# project directory contains non-ASCII characters (e.g. a Cyrillic folder name
# like "\u041a\u043e\u0448\u0435\u043b\u0435\u043a"). This project is expected to live under such a path,
# so the check is explicitly disabled here. See http://b.android.com/95744.
android.overridePathCheck=true
# Public key for the "buy directly from the developer" activation-code path (Ledger Plus).
# This is only the PUBLIC key -- safe to keep here. The matching PRIVATE key lives at
# tools/developer_private_key.pem, is git-ignored, and must never be shared with anyone;
# only you (the developer) use it, with tools/issue_activation_code.py, to mint codes.
DEVELOPER_SUBSCRIPTION_PUBLIC_KEY=MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAsOk68sCOTVl/4WCNZuvCidKZy4b3zz+ZjB4TsJOs07fRk/IolFxIOC1vrL55ncwzLeQlRpmWWKLkRIwEV0tbOu56+3NHuHF8Jcfq07VOyXplWT7kxzd+bTjmcZsXhO3J7z4kJtbYY3X3Fo465/8RuXHSQLyFkrHjS0FepclHDgnaZB/7qz6diLtLbOk7so4rScuIl7W362Vz71bDp/OD6UkDW6W+niGlQQKhKNJI7XnBA2AVl1W/4ZFY4v+7kNyztRcIzazJlbnrVjX9kLZiC9Sbk7+R+ZXssWXNvu7JytHTb+A8rWjkqu7GWvspJmvieWoj1dGB5PL/k+17s8BtyvDm7CNiG7lhKn0Lq7x/oMRDW5hu2HnYtmd8DqJjks2peUTsewkUGL3UrnDPgDnIXud1YJuyI0SWZN/P2RqTo40sMHPjGYd4esUk6R5/ATgKfSf01kPN2oabZzeC7rYpecP0QLCM0lDVMVGpxVaiegDxsKajGLyy67XVx29V72ShAgMBAAE=
```

## gradle/bootstrap.ps1

```powershell
$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$jar = Join-Path $PSScriptRoot 'wrapper\gradle-wrapper.jar'
$expected = '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046'
if (!(Test-Path $jar)) {
    $temporary = "$jar.$PID.part"
    try {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar' -OutFile $temporary -TimeoutSec 120
        if ((Get-FileHash $temporary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Gradle wrapper checksum mismatch.' }
        Move-Item -Force $temporary $jar
    } finally {
        if (Test-Path $temporary) { Remove-Item $temporary }
    }
}
if ((Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Untrusted Gradle wrapper. Refusing to run.' }
```

## gradle/wrapper/gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

## gradlew

```bash
#!/bin/sh
# Fetch only the official Gradle wrapper, verify it, then delegate to Gradle.
# The distribution is independently checked by gradle-wrapper.properties.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
EXPECTED=2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046
checksum() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d ' ' -f 1
    else shasum -a 256 "$1" | cut -d ' ' -f 1; fi
}
if [ ! -f "$JAR" ]; then
    TEMP="$JAR.$$.part"
    trap 'rm -f "$TEMP"' EXIT HUP INT TERM
    URL=https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --proto '=https' --tlsv1.2 --connect-timeout 15 --max-time 120 "$URL" -o "$TEMP"
    else
        wget --https-only --timeout=120 "$URL" -O "$TEMP"
    fi
    [ "$(checksum "$TEMP")" = "$EXPECTED" ] || { echo 'Gradle wrapper checksum mismatch.' >&2; exit 1; }
    mv "$TEMP" "$JAR"
fi
[ "$(checksum "$JAR")" = "$EXPECTED" ] || { echo 'Untrusted Gradle wrapper. Refusing to run.' >&2; exit 1; }
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; fi
exec "$JAVA" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
```

## gradlew.bat

```batch
@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0gradle\bootstrap.ps1"
if errorlevel 1 exit /b 1
set "JAVA_EXE=java.exe"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
```

## settings.gradle.kts

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "TGLedger"
include(":app", ":domain", ":data")
```

## tools/audit_project.py

```python
#!/usr/bin/env python3
"""Offline structural/resource/SQLite audit. This is NOT an Android compiler or a device test."""
from pathlib import Path
import json
import re
import sqlite3
import sys
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
checks = []
failures = []

def check(condition, label):
    (checks if condition else failures).append(label)


def load_strings(folder):
    values = {}
    items = [item for file in sorted((ROOT / f'app/src/main/res/{folder}').glob('*.xml')) for item in ET.parse(file).getroot()]
    for item in items:
        if item.tag != 'string':
            continue
        check(item.attrib['name'] not in values, f'No duplicate string: {folder}/{item.attrib["name"]}')
        values[item.attrib['name']] = ''.join(item.itertext())
    return values


def code_only(source):
    """Lex strings/comments for balanced delimiters, without claiming full Kotlin parsing."""
    # Kotlin string-template bodies are deliberately not type-checked by this audit.
    pattern = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*[\s\S]*?\*/|//[^\n]*'
    return re.sub(pattern, lambda match: '\n' * match.group(0).count('\n'), source)


for file in ROOT.rglob('*.xml'):
    if 'build' in file.parts:
        continue
    try:
        ET.parse(file)
        checks.append(f'Well-formed XML: {file.relative_to(ROOT)}')
    except ET.ParseError as error:
        failures.append(f'Malformed XML: {file.relative_to(ROOT)}: {error}')

en = load_strings('values')
uk = load_strings('values-uk')
# Explicit non-translatable branding resources inherit from default values in Android.
for file in (ROOT/'app/src/main/res/values').glob('*.xml'):
    for item in ET.parse(file).getroot():
        if item.tag == 'string' and item.get('translatable') == 'false':
            uk.setdefault(item.get('name'), ''.join(item.itertext()))
check(en.keys() == uk.keys(), 'English/Ukrainian key parity')
for key in en.keys() & uk.keys():
    check(sorted(re.findall(r'%\d+\$[a-z]', en[key])) == sorted(re.findall(r'%\d+\$[a-z]', uk[key])), f'Format-argument parity: {key}')
    check(bool(en[key].strip()) and bool(uk[key].strip()), f'Nonempty translations: {key}')

kotlin = list(ROOT.rglob('*.kt'))
for file in kotlin:
    source = file.read_text()
    path = str(file.relative_to(ROOT))
    check('\0' not in source, f'No NUL bytes: {path}')
    check(not re.search(r'\b(TODO|FIXME|NotImplementedError)\b', source), f'No implementation markers: {path}')
    if '/src/main/' in path:
        for name in re.findall(r'R\.string\.(\w+)', source):
            check(name in en, f'Resolved app string: {name}')
    if path.startswith('domain/src/main/'):
        check(not re.search(r'import (android\.|androidx\.|com\.semmyx\.ledger\.(data|presentation))', source), f'Pure domain boundary: {path}')
    if '/presentation/' in path and path.endswith('ViewModel.kt'):
        check('com.semmyx.ledger.data.' not in source, f'Domain-only ViewModel dependencies: {path}')
    stack = []
    valid = True
    for character in code_only(source):
        if character in '({[':
            stack.append(character)
        elif character in ')}]':
            if not stack or stack.pop() != {')':'(', '}':'{', ']':'['}[character]:
                valid = False
                break
    check(valid and not stack, f'Lexically balanced Kotlin delimiters: {path}')

# Execute the exact @Query bodies in SQLite, not a manually rewritten query.
dao = (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt').read_text()
query_map = {}
for match in re.finditer(r'@Query\(("""[\s\S]*?"""|"(?:\\.|[^"\\])*")\)\s*(?:suspend\s+)?fun\s+(\w+)', dao):
    quoted, name = match.groups()
    query_map[name] = quoted[3:-3] if quoted.startswith('"""') else json.loads(quoted)

entities = (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/local/Entities.kt').read_text()
body = re.search(r'data class EntryEntity\(([\s\S]*?)\n\)', entities).group(1)
columns = re.findall(r'val (\w+): (\w+)(\?)?', body)
types = {'String':'TEXT','Long':'INTEGER','Int':'INTEGER','Double':'REAL'}
connection = sqlite3.connect(':memory:')
connection.row_factory = sqlite3.Row
connection.execute('CREATE TABLE entries (' + ','.join('"'+name+'" '+types[kind]+(' PRIMARY KEY' if name=='id' else '') for name, kind, nullable in columns) + ')')

def insert(id, title, minor, currency='UAH', kind='EXPENSE', day=20704, created=1):
    values = dict(id=id, title=title, amountMinor=minor, currency=currency, type=kind, category='FOOD', epochDay=day,
                  note='', searchText=unicodedata.normalize('NFKC', title).lower(), createdAtMillis=created, deletedAtMillis=None, sourceType='UNKNOWN', bankId=None, cardLast4=None, eventAtMillis=None, customCategoryId=None, origin='MANUAL')
    connection.execute('INSERT INTO entries ('+','.join('"'+name+'"' for name,_,_ in columns)+') VALUES ('+','.join('?' for _ in columns)+')', [values[name] for name,_,_ in columns])


def query(name, **params):
    if name == 'observeEntries':
        params = {'category': None, 'from': None, 'through': None, **params}
    return connection.execute(query_map[name], params)

insert('coffee', 'КАВА 50%', 1250, created=3)
insert('income', 'Salary', 100000, kind='INCOME', created=2)
insert('usd', 'Other currency', 999999999, currency='USD')
balance = dict(query('observeBalance', currency='UAH').fetchone())
check(balance == {'incomeMinor':100000,'expenseMinor':1250,'entryCount':2}, 'Actual SQL: sums keep currencies separate')
check(len(query('observeEntries', currency='UAH', type=None, pattern='%кава%', limit=50).fetchall()) == 1, 'Actual SQL: normalized Ukrainian search')
check(len(query('observeEntries', currency='UAH', type=None, pattern='%50\\%%', limit=50).fetchall()) == 1, 'Actual SQL: literal percent search')
check(len(query('observeEntries', currency='UAH', type=None, pattern="%' OR 1=1 --%", limit=50).fetchall()) == 0, 'Actual SQL: bound input cannot change query structure')
check(len(query('observeEntries', currency='UAH', type='INCOME', pattern='%', limit=50).fetchall()) == 1, 'Actual SQL: type filter')
check(query('observeEntries', currency='UAH', type=None, pattern='%', limit=1).fetchone()['id'] == 'coffee', 'Actual SQL: stable creation ordering and limit')
check(query('delete', id='coffee', now=1234).rowcount == 1, 'Actual SQL: first delete succeeds')
check(query('delete', id='coffee', now=5678).rowcount == 0, 'Actual SQL: repeated delete is idempotent')
check(query('find', id='coffee').fetchone() is None, 'Actual SQL: deleted entries are hidden')
check(query('undo', id='coffee', stamp=9999).rowcount == 0, 'Actual SQL: stale undo token is rejected')
check(query('undo', id='coffee', stamp=1234).rowcount == 1, 'Actual SQL: exact undo token restores entry')
query('delete', id='coffee', now=1234)
query('purge', before=1235)
check(query('findIncludingDeleted', id='coffee').fetchone() is None, 'Actual SQL: old tombstone is purged')
activity = query('observeActivity', currency='UAH', **{'from':20703,'through':20705}).fetchall()
check(len(activity)==1 and activity[0]['incomeMinor']==100000 and activity[0]['expenseMinor']==0, 'Actual SQL: date-window aggregation excludes deletions')
connection.close()

app_manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
application = app_manifest.find('application')
check(application.get(android+'allowBackup') == 'false', 'Android cloud backup disabled')
check(application.get(android+'usesCleartextTraffic') == 'false', 'Cleartext traffic disabled')
support_activity = next(a for a in application.findall('activity') if 'SupportWebActivity' in a.get(android+'name',''))
check(support_activity.get(android+'exported') == 'false', 'Support WebView is non-exported')
check('private fun normalizeSearch' in (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/repository/RoomEntryRepository.kt').read_text(), 'Unicode normalization implemented in repository')
check('com.android.billingclient:billing-ktx:7.1.1' in (ROOT/'app/build.gradle.kts').read_text(), 'Requested v7 flavor included')
check('com.android.billingclient:billing-ktx:8.0.0' in (ROOT/'app/build.gradle.kts').read_text(), 'Current-submission v8 flavor included')
check('targetSdk = 36' in (ROOT/'app/build.gradle.kts').read_text(), 'Android 16 target configured')
check('fallbackToDestructiveMigration(' not in (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt').read_text(), 'No destructive migration fallback')

result = {
    'kind':'offline-structural-resource-and-sql-audit',
    'passed_assertions':len(checks),
    'failures':failures,
    'kotlin_source_files':len(kotlin),
    'localized_string_pairs':len(en),
    'compiled_android':False,
    'executed_android_or_junit_tests':False,
    'checks':checks,
}
output = ROOT/'verification/source-audit.json'
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n')
print(json.dumps({key:value for key,value in result.items() if key!='checks'}, ensure_ascii=False, indent=2))
sys.exit(1 if failures else 0)
```

## tools/issue_activation_code.py

```python
#!/usr/bin/env python3
"""Developer-side tool for the Ledger Plus "buy from the developer" path.

This never runs inside the app and never ships in the APK. Keep the private
key file it creates OFF source control and OFF any device but your own.

Requires the 'cryptography' package (pure Python + prebuilt wheels, no
separate OpenSSL install needed on any OS, including Windows):
    pip install cryptography

Typical manual flow (matches selling Ledger Plus over Telegram):
  1. One-time setup:
       pip install cryptography
       python tools/issue_activation_code.py generate-keys
     This writes developer_private_key.pem (KEEP SECRET, keep local) and
     prints a base64 public key. Put that value in your OWN
     ~/.gradle/gradle.properties (never commit it) as:
       DEVELOPER_SUBSCRIPTION_PUBLIC_KEY=<the printed base64 value>
     Rebuild the app once. From then on the app can verify codes you issue.

  2. Every sale:
       - Buyer messages you on Telegram asking for Ledger Plus.
       - You reply with the price (100 UAH) and your card number.
       - Buyer pays and sends a screenshot / confirmation.
       - Buyer opens Settings -> Ledger Plus -> "Buy from developer" card,
         copies their Installation ID, and sends it to you on Telegram.
       - You check the money actually arrived, then run:
           python tools/issue_activation_code.py issue \\
             --installation-id XXXX-XXXX-XXXX --days 30
       - You paste the printed code back to the buyer on Telegram.
       - Buyer pastes the code into the app and taps Activate. Ledger Plus
         turns on immediately and works fully offline until it expires.

See docs/DEVELOPER_ACTIVATION_CODES.md for the full explanation, including
how this same token format lets you automate step 2 later with a Telegram
bot without ever updating the app.
"""
from __future__ import annotations

import argparse
import base64
import sys
import time
from pathlib import Path

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding, rsa
except ImportError:
    print("Missing dependency. Install it once with:\n    pip install cryptography", file=sys.stderr)
    sys.exit(1)

HERE = Path(__file__).resolve().parent
DEFAULT_PRIVATE_KEY = HERE / "developer_private_key.pem"
DEFAULT_PUBLIC_DER = HERE / "developer_public_key.der"
DEFAULT_PRODUCT_ID = "ledger_plus"


def generate_keys(private_key_path: Path, public_der_path: Path) -> None:
    if private_key_path.exists():
        raise SystemExit(
            f"{private_key_path} already exists. Delete it yourself first if you really want a new "
            "key pair -- doing so invalidates every code you issued with the old one."
        )
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,
        encryption_algorithm=serialization.NoEncryption(),
    )
    private_key_path.write_bytes(private_pem)
    try:
        private_key_path.chmod(0o600)
    except (OSError, NotImplementedError):
        pass  # Permission bits are not meaningful on every OS/filesystem (e.g. some Windows drives); not fatal.
    public_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_der_path.write_bytes(public_der)
    encoded = base64.b64encode(public_der).decode("ascii")
    print("Keys generated.\n")
    print(f"Private key (keep this SECRET, keep it only on your computer): {private_key_path}")
    print(f"Public key DER (safe to keep, this is what gets embedded):     {public_der_path}\n")
    print("Put this in your OWN ~/.gradle/gradle.properties (never commit it), then rebuild:")
    print(f"DEVELOPER_SUBSCRIPTION_PUBLIC_KEY={encoded}")


def issue_token(private_key_path: Path, installation_id: str, product_id: str, days: float) -> str:
    if not private_key_path.exists():
        raise SystemExit(f"{private_key_path} not found. Run 'generate-keys' first.")
    private_key = serialization.load_pem_private_key(private_key_path.read_bytes(), password=None)
    now_millis = int(time.time() * 1000)
    # Kotlin's DeveloperSubscriptionVerifier parses a pipe-delimited payload, NOT JSON:
    #   "v1|<installationId>|<productId>|<issuedAtMillis>|<expiresAtMillisOrEmpty>"
    # An empty final field (days <= 0) means the grant never expires.
    expires_at = now_millis + int(days * 24 * 60 * 60 * 1000) if days > 0 else None
    clean_installation_id = installation_id.strip().upper()
    fields = ["v1", clean_installation_id, product_id, str(now_millis), str(expires_at) if expires_at is not None else ""]
    payload_bytes = "|".join(fields).encode("utf-8")
    # SHA256withRSA in Kotlin == RSASSA-PKCS1-v1_5 with SHA-256 here; same algorithm, no OpenSSL needed.
    signature_bytes = private_key.sign(payload_bytes, padding.PKCS1v15(), hashes.SHA256())
    payload_b64 = base64.urlsafe_b64encode(payload_bytes).decode("ascii")
    signature_b64 = base64.urlsafe_b64encode(signature_bytes).decode("ascii")
    token = f"{payload_b64}.{signature_b64}"
    validity = "it never expires" if expires_at is None else f"valid until {time.strftime('%Y-%m-%d %H:%M UTC', time.gmtime(expires_at / 1000))}"
    print(f"Activation code (send this to the buyer, {validity}):\n")
    print(token)
    return token


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    keys_cmd = sub.add_parser("generate-keys", help="One-time: create the developer signing key pair.")
    keys_cmd.add_argument("--private-key", type=Path, default=DEFAULT_PRIVATE_KEY)
    keys_cmd.add_argument("--public-der", type=Path, default=DEFAULT_PUBLIC_DER)

    issue_cmd = sub.add_parser("issue", help="Issue one activation code for a confirmed sale.")
    issue_cmd.add_argument("--installation-id", required=True, help="The buyer's Installation ID from Settings.")
    issue_cmd.add_argument("--product-id", default=DEFAULT_PRODUCT_ID)
    issue_cmd.add_argument("--days", type=float, default=30, help="Subscription length in days (default: 30). Use 0 for a code that never expires.")
    issue_cmd.add_argument("--private-key", type=Path, default=DEFAULT_PRIVATE_KEY)

    args = parser.parse_args()
    if args.command == "generate-keys":
        generate_keys(args.private_key, args.public_der)
    elif args.command == "issue":
        issue_token(args.private_key, args.installation_id, args.product_id, args.days)


if __name__ == "__main__":
    try:
        main()
    except RuntimeError as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
```

## tools/verify_bank_regex.py

```python
#!/usr/bin/env python3
"""Executes the actual Kotlin string-literal regexes on Java's engine; not Kotlin/Android compilation."""
from pathlib import Path
import re,json,subprocess,tempfile
R=Path(__file__).resolve().parents[1]
s=(R/'domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankParser.kt').read_text()
q=r'"(?:\\.|[^"\\])*"'
patterns={name:json.loads(literal) for name,literal in re.findall(r'private val (\w+) = Regex\(('+q+')',s)}
java='''import java.util.regex.*;
public class BankRegexSmoke {
 static int checks=0;
 static Pattern p(String source) { return Pattern.compile(source,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE); }
 static Pattern amount=p(AMOUNT),prefix=p(PREFIX),card=p(CARD),merchant=p(MERCHANT),otp=p(OTP),declined=p(DECLINED),transfer=p(TRANSFER),income=p(INCOME);
 static void check(boolean test,String label) { if(!test) throw new AssertionError(label); checks++; }
 static void money(String text,String expected,String currency) {
  Matcher m=amount.matcher(text); String value=null,cur=null;
  if(m.find()) { value=m.group(1); cur=m.group(2); }
  else { m=prefix.matcher(text); if(m.find()) { value=m.group(2); cur=m.group(1); } }
  if(value!=null)value=value.replaceAll("[ \\u00A0\\u202F]","").replace(',','.');
  check(java.util.Objects.equals(expected,value) && java.util.Objects.equals(currency,cur),"money fixture: "+text);
 }
 public static void main(String[] args) {
  money("Оплата 1 200,50 грн у АТБ; картка *1234","1200.50","грн");
  money("Purchase 12.50 USD at Steam","12.50","USD");
  money("Payment $12.50 at Steam","12.50","$");
  money("Зараховано 2000 UAH","2000","UAH");
  money("Оплата 1\\u00A0000,25 грн","1000.25","грн");
  money("Payment €12.50 at Megogo","12.50","€");
  Matcher c=card.matcher("картка *1234"); check(c.find()&&c.group(1).equals("1234"),"card suffix");
  Matcher shop=merchant.matcher("Оплата 100 грн у АТБ; баланс 900 грн");check(shop.find()&&shop.group(1).equals("АТБ"),"merchant boundary");
  check(otp.matcher("Код підтвердження оплати 100 грн").find(),"OTP ignored");
  check(declined.matcher("Payment declined 10 EUR").find(),"declined ignored");
  check(transfer.matcher("Переказ 100 грн").find(),"transfer marked for review");
  check(income.matcher("Зараховано 2000 грн").find(),"income detection");
  System.out.println("Bank source-regex JVM fixtures passed: "+checks);
 }
}'''
for javaName,kotlinName in [('AMOUNT','amount'),('PREFIX','amountPrefix'),('CARD','card'),('MERCHANT','merchant'),('OTP','otp'),('DECLINED','declined'),('TRANSFER','transfer'),('INCOME','income')]:
 java=java.replace('p('+javaName+')','p('+json.dumps(patterns[kotlinName],ensure_ascii=False)+')')
with tempfile.TemporaryDirectory() as folder:
 p=Path(folder)/'BankRegexSmoke.java';p.write_text(java)
 run=subprocess.run(['java',str(p)],capture_output=True,text=True,timeout=60)
 if run.returncode: raise RuntimeError(run.stdout+run.stderr)
 print(run.stdout.strip())
(R/'verification/bank-regex-jvm.json').write_text(json.dumps({'kind':'actual-source-regex-on-JVM','passed_fixtures':12,'compiled_kotlin':False,'executed_android':False},indent=2))
```

## tools/verify_bank_sql.py

```python
#!/usr/bin/env python3
from pathlib import Path
import json,re,sqlite3
R=Path(__file__).resolve().parents[1]
source=(R/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt').read_text()
sql=[json.loads(x) for x in re.findall(r'db.execSQL\(("(?:\\.|[^"\\])*")\)',source)]
c=sqlite3.connect(':memory:');c.row_factory=sqlite3.Row
c.execute('CREATE TABLE entries(id TEXT NOT NULL PRIMARY KEY,title TEXT NOT NULL,amountMinor INTEGER NOT NULL,currency TEXT NOT NULL,type TEXT NOT NULL,category TEXT NOT NULL,epochDay INTEGER NOT NULL,note TEXT NOT NULL,searchText TEXT NOT NULL,createdAtMillis INTEGER NOT NULL,deletedAtMillis INTEGER)')
c.execute("INSERT INTO entries VALUES('old','Legacy expense',100,'UAH','EXPENSE','FOOD',20733,'','legacy',1,NULL)")
for statement in sql:c.execute(statement)
checks=[]
def check(test,label):
 assert test,label
 checks.append(label)
old=c.execute("SELECT * FROM entries WHERE id='old'").fetchone()
check(old['sourceType']=='UNKNOWN' and old['amountMinor']==100,'Migration preserves entries and does not invent cash/card attribution')
check(len(c.execute('PRAGMA table_info(entries)').fetchall())==17,'All six metadata columns migrated')
s=(R/'data/src/main/kotlin/com/semmyx/ledger/data/local/BankEntities.kt').read_text()
queries={}
for quoted,name in re.findall(r'@Query\(("""[\s\S]*?"""|"(?:\\.|[^"\\])*")\)\s*(?:suspend\s+)?fun\s+(\w+)',s):queries[name]=quoted[3:-3] if quoted.startswith('"""') else json.loads(quoted)
def insert(id,amount,source,bank,currency='UAH',kind='EXPENSE',category='FOOD',custom=None,deleted=None):
 c.execute('INSERT INTO entries(id,title,amountMinor,currency,type,category,epochDay,note,searchText,createdAtMillis,deletedAtMillis,sourceType,bankId,customCategoryId) VALUES(?,?,?,?,?,?,20733,\'\',\'\',1,?,?,?,?)',(id,id,amount,currency,kind,category,deleted,source,bank,custom))
insert('cash',200,'CASH',None);insert('mono',300,'CARD','mono');insert('privat',400,'CARD','privat')
insert('dollars',99999,'CARD','mono',currency='USD');insert('income',99999,'CARD','mono',kind='INCOME');insert('deleted',99999,'CARD','mono',deleted=1)
p={'currency':'UAH','from':20730,'through':20734,'source':None,'bank':None}
rows=c.execute(queries['sources'],p).fetchall()
check(sum(r['totalMinor'] for r in rows)==1000,'Source totals exclude income, deleted and other currencies')
check(next(r['totalMinor'] for r in rows if r['bankId']=='mono')==300,'Mono is isolated from Privat and cash')
check(sum(r['totalMinor'] for r in c.execute(queries['detailCategories'],{**p,'source':'CARD','bank':'privat'}))==400,'Source category filter uses chosen bank')
c.execute("INSERT INTO custom_categories VALUES('c1','Pet care','#6FAEFF')")
insert('custom',500,'CASH',None,category='OTHER',custom='c1')
cat=c.execute(queries['detailCategories'],p).fetchall()
check(any(r['customName']=='Pet care' and r['totalMinor']==500 for r in cat),'Custom categories retain their own identity')
fields=['id','eventKey','bankId','amountMinor','currency','type','merchant','cardLast4','eventAtMillis','receivedAtMillis','timeFromMessage','transfer','category','origin','status']
vals=['a','hash','mono',300,'UAH','EXPENSE','ATB','1234',100000,100000,0,0,'FOOD','PUSH','REVIEW']
q='INSERT OR IGNORE INTO bank_inbox('+','.join(fields)+') VALUES('+','.join('?'*len(fields))+')'
c.execute(q,vals);c.execute(q,['b']+vals[1:])
check(c.execute('SELECT COUNT(*) FROM bank_inbox').fetchone()[0]==1,'Unique fingerprint prevents repeated delivery imports')
check(c.execute(queries['similar'],dict(bank='mono',amount=300,currency='UAH',type='EXPENSE',time=120000,card='1234',merchant='ATB')).fetchone()[0]==1,'Near duplicates are detected for review')
check(c.execute(queries['similar'],dict(bank='privat',amount=300,currency='UAH',type='EXPENSE',time=120000,card='1234',merchant='ATB')).fetchone()[0]==0,'Different banks do not deduplicate each other')
c.execute(queries['clearInbox']);check(c.execute('SELECT COUNT(*) FROM entries').fetchone()[0]==8,'Clearing review history preserves ledger entries')
result={'kind':'exact-migration-and-Room-bank-SQL-in-SQLite','passed':len(checks),'checks':checks,'compiled_android':False}
(R/'verification/bank-sql.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps(result,ensure_ascii=False,indent=2))
```

## tools/verify_navigation_recovery.py

```python
#!/usr/bin/env python3
"""Source regression guards for the reported crash/navigation defect; not an Android test."""
from pathlib import Path
import json
R=Path(__file__).resolve().parents[1]
A=R/'app/src/main/kotlin/com/semmyx/ledger'
root=(A/'presentation/AppRoot.kt').read_text()
analytics=(A/'presentation/analytics/AnalyticsScreen.kt').read_text()
bank=(A/'presentation/bank/BankScreen.kt').read_text()
repo=(R/'data/src/main/kotlin/com/semmyx/ledger/data/repository/BankImportRepository.kt').read_text()
checks=[]
def check(condition,label):
 assert condition,label
 checks.append(label)
check('CompositionLocalProvider(LocalActivityResultRegistryOwner provides host, LocalOnBackPressedDispatcherOwner provides host)' in root,'Explicit real Activity owners are provided above localized content')
check(root.index('LocalActivityResultRegistryOwner provides host') < root.index('LocalizedContent(state.preferences.language)'),'Registry owner is installed before the localized Context replacement')
check('TextButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Icon(Icons.Default.History' in analytics,'History button no longer invokes dashboard back action')
check('composable("history?month={month}"' in root and 'HistoryScreen(historyModel' in root,'History has an independent navigation destination')
check('onHistory = { month -> nav.navigate(' in root,'Analytics selected month is passed to history')
check('fun openSettings(action: String' in bank and 'Intent.FLAG_ACTIVITY_NEW_TASK' in bank,'System settings launched safely from localized non-Activity Context')
check('repository.importText(bankId, body)' in bank and 'preview = remember(bankId, body)' in bank,'Local text recovery has a preview and explicit submit action')
check('if (!enabled && !manual)' in repo and 'if (!manual && autoImport' in repo,'Manual recovery works without automation and never bypasses confirmation')
check('model.diagnostics.collectAsStateWithLifecycle()' in bank,'Live import diagnostics are visible in the banking screen')
check('requestRebind' in bank and 'repository.configuredPackage(bankId)' in bank,'Reconnect and bank package configuration are provided')
result={'kind':'source-regression-guards','passed':len(checks),'checks':checks,'compiled_android':False,'device_crash_fixed_verified':False}
(R/'verification/navigation-recovery.json').write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps(result,ensure_ascii=False,indent=2))
```

## tools/verify_upgrade_sql.py

```python
#!/usr/bin/env python3
"""Executes the exact Room @Query SQL in SQLite. Not an Android or Kotlin test runner."""
from pathlib import Path
import re, json, sqlite3, datetime
R=Path(__file__).resolve().parents[1]
source=(R/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt').read_text()
queries={}
for quoted,name in re.findall(r'@Query\(("""[\s\S]*?"""|"(?:\\.|[^"\\])*")\)\s*(?:suspend\s+)?fun\s+(\w+)',source):
    queries[name]=quoted[3:-3] if quoted.startswith('"""') else json.loads(quoted)
c=sqlite3.connect(':memory:'); c.row_factory=sqlite3.Row
c.execute('CREATE TABLE entries(id TEXT PRIMARY KEY,title TEXT,amountMinor INTEGER,currency TEXT,type TEXT,category TEXT,epochDay INTEGER,note TEXT,searchText TEXT,createdAtMillis INTEGER,deletedAtMillis INTEGER)')
def day(value): return (datetime.date.fromisoformat(value)-datetime.date(1970,1,1)).days
def insert(id,amount=100,category='FOOD',date='2026-10-04',currency='UAH',kind='EXPENSE',deleted=None):
    c.execute('INSERT INTO entries VALUES(?,?,?,?,?,?,?,?,?,?,?)',(id,id,amount,currency,kind,category,day(date),'detail',id,1,deleted))
def q(name,**params): return c.execute(queries[name],params)
checks=[]
def check(value,label):
    if not value: raise AssertionError(label)
    checks.append(label)
for i in range(125): insert(f'food-{i}')
insert('transport',9000,'TRANSPORT')
insert('last-month',7000,date='2026-09-20')
insert('year-old',3000,date='2025-01-01')
insert('other-currency',999999,currency='USD')
insert('deleted',999999,deleted=1)
insert('salary',1000000,'SALARY',kind='INCOME')
insert('future',999999,date='2026-10-10')
params=dict(currency='UAH',type='EXPENSE',category='FOOD',**{'from':day('2026-10-01'),'through':day('2026-10-04')})
check(q('statistics',**params).fetchone()[0]==12500,'Calendar month: exact food sum across more than 50 records')
check(q('statistics',**{**params,'category':None}).fetchone()[0]==21500,'Calendar month: all expense categories')
check(q('statistics',**{**params,'type':'INCOME','category':None}).fetchone()[0]==1000000,'Income excludes expenses')
check(q('statistics',**{**params,'from':day('2000-01-01')}).fetchone()[0]==22500,'All-time includes earlier years and excludes deleted/future/currency')
check(q('statistics',**{**params,'category':'HEALTH'}).fetchone()[0]==0,'Empty category returns zero')
filter=dict(currency='UAH',type=None,pattern='%',category='FOOD',limit=51,**{'from':day('2026-10-01'),'through':day('2026-10-04')})
check(len(q('observeEntries',**filter).fetchall())==51,'Category pagination initial window plus sentinel')
rows=q('observeEntries',**{**filter,'limit':150}).fetchall()
check(len(rows)==125 and all(row['category']=='FOOD' for row in rows),'Load more obtains every category transaction')
check(all(row['note']=='detail' for row in rows),'Transaction details retained')
weekly=q('observeEntries',**{**filter,'limit':200,'from':day('2026-09-28')}).fetchall()
check(len(weekly)==125,'Seven-day details share exact seven-day bounds')
totals=q('observeCategoryTotals',currency='UAH',**{'from':day('2026-10-01'),'through':day('2026-10-04')}).fetchall()
food=next(row for row in totals if row['category']=='FOOD')
check(food['count']==125 and food['expenseMinor']==12500,'Category header uses full dataset rather than current page')
q('delete',id='food-0',now=1234)
check(q('statistics',**params).fetchone()[0]==12400,'Assistant statistics exclude newly deleted entries')
q('undo',id='food-0',stamp=1234)
check(q('statistics',**params).fetchone()[0]==12500,'Undo restores aggregate')
insert('food-income',100,'FOOD',kind='INCOME')
typed=q('observeCategoryTotals',currency='UAH',**{'from':day('2026-10-01'),'through':day('2026-10-04')}).fetchall()
food=next(row for row in typed if row['category']=='FOOD')
check(food['incomeCount']==1 and food['expenseCount']==125,'Income/expense counts remain separate inside one category')
check(food['count']==126,'Combined category detail count remains backwards compatible')
result={'kind':'exact-Room-SQL-in-SQLite','passed':len(checks),'checks':checks,'compiled_android':False,'executed_junit':False}
(R/'verification/upgrade-sql.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
```

## tools/verify_voice_regex.py

```python
#!/usr/bin/env python3
"""Run the exact source monetary regex patterns on the JVM. This does NOT compile Kotlin."""
from pathlib import Path
import re, json, tempfile, subprocess
R=Path(__file__).resolve().parents[1]
s=(R/'app/src/main/kotlin/com/semmyx/ledger/voice/VoiceEntryParser.kt').read_text()
quoted=r'"(?:\\.|[^"\\])*"'
stem_block=s.split('private val CURRENCY_STEMS = listOf(',1)[1].split('\n    )',1)[0]
stems=[json.loads(x) for x in re.findall(quoted,stem_block)]
amount_block=s.split('private val AMOUNT_WITH_CURRENCY = Regex(',1)[1].split('RegexOption',1)[0]
parts=[json.loads(x) for x in re.findall(quoted,amount_block)]
amount=parts[0]+'|'.join(stems)+parts[-1]
bare=json.loads(re.search(r'private val BARE_NUMBER = Regex\(('+quoted+r')\)',s).group(1))
java='''import java.util.regex.*;
public class RegexSmoke {
    static int checks=0;
    static final Pattern money=Pattern.compile(MONEY,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    static final Pattern bare=Pattern.compile(BARE);
    static void test(String text,String expected) {
        Matcher m=money.matcher(text);
        String found=null;
        if(m.find()) found=m.groupCount()>=1?m.group(1):m.group();
        else { m=bare.matcher(text); while(m.find()) found=m.groupCount()>=1?m.group(1):m.group(); }
        if(found!=null) found=found.replace(" ","").replace("\\u00A0","");
        if(!java.util.Objects.equals(found,expected)) throw new AssertionError(text+" -> "+found);
        checks++;
    }
    public static void main(String[] args) {
        test("привет я купил 1 кг томатов за 100 грн","100");
        test("я получил зарплату 1 000 000 гривен","1000000");
        test("купив томати за 100","100");
        test("отримав зарплату 2000,50","2000,50");
        test("купив за 1\\u00A0000,25 гривень","1000,25");
        test("привіт",null);
        test("bought food for 12.50 USD","12.50");
        System.out.println("JVM monetary-regex checks passed: "+checks);
    }
}'''.replace('MONEY',json.dumps(amount,ensure_ascii=True)).replace('BARE',json.dumps(bare,ensure_ascii=True))
with tempfile.TemporaryDirectory() as folder:
    path=Path(folder)/'RegexSmoke.java';path.write_text(java)
    result=subprocess.run(['java','--source','17',str(path)],text=True,capture_output=True,timeout=30)
    if result.returncode: raise SystemExit(result.stdout+result.stderr)
    print(result.stdout.strip())
    (R/'verification/voice-regex-jvm.json').write_text(json.dumps({'kind':'exact-source-regex-on-JVM','passed':7,'compiled_kotlin':False,'compiled_android':False,'output':result.stdout.strip()},indent=2)+'\n')
```

