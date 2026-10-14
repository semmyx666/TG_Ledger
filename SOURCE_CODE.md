# TG Ledger 3.3.3 — complete file-by-file source

166 implementation, configuration and test files, without truncation.

Generated from tracked files. Local keys and build outputs are excluded.

## .github/workflows/android.yml

```yaml
name: Android validation
on:
  push:
    branches: [main]
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
        with:
          packages: ''
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
        with:
          name: debug-apks
          path: app/build/outputs/apk/*/debug/*.apk
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
        with:
          packages: ''
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
          script: bash tools/run_device_tests.sh
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: device-reports-api-${{ matrix.api }}
          path: |
            **/build/reports/androidTests/
            verification/device-screenshots/
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
        versionCode = 17
        versionName = "3.3.3"
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
    implementation(project(":data"))
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    androidTestImplementation(libs.androidx.espresso.core) // Used only by the composition root / platform adapters.
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
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

## app/src/androidTest/kotlin/com/semmyx/ledger/BankBackgroundImportTest.kt

```kotlin
package com.semmyx.ledger

import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.semmyx.ledger.bank.BankNotificationListener
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
    private fun listenerAccess(enabled: Boolean) {
        shell("cmd notification ${if (enabled) "allow_listener" else "disallow_listener"} $listener")
        // Android 8 does not implement these cmd notification subcommands. Use the same
        // secure setting that its notification-access Settings screen writes, via shell.
        val existing = shell("settings get secure enabled_notification_listeners").trim()
            .takeUnless { it == "null" }.orEmpty().split(':').filter { it.isNotBlank() }.toMutableSet()
        if (enabled) existing.add(listener) else existing.remove(listener)
        if (existing.isEmpty()) shell("settings delete secure enabled_notification_listeners")
        else shell("settings put secure enabled_notification_listeners ${existing.joinToString(":")}")
        if (enabled) android.service.notification.NotificationListenerService.requestRebind(android.content.ComponentName(app,BankNotificationListener::class.java))
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
        listenerAccess(true)
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
        listenerAccess(false)
        assertTrue("Listener did not disconnect", waitFor { !repo.diagnostics.value.connected })
        val at = System.currentTimeMillis() - 120_000
        post(2, 33445, at)
        Thread.sleep(800)
        assertFalse(entries().any { it.amountMinor == 33445L })
        listenerAccess(true)
        assertTrue("Active notification not replayed: ${repo.diagnostics.value.lastCode}", waitFor { entries().any { it.amountMinor == 33445L } })
        assertEquals(at, entries().first { it.amountMinor == 33445L }.eventAtMillis)
    }
    @Test fun equalAmountSeparatePaymentIsRetainedForReviewInsteadOfDropped() {
        val at = System.currentTimeMillis()
        post(10, 77889, at)
        assertTrue(waitFor { entries().any { it.amountMinor == 77889L } })
        post(11, 77889, at + 1000)
        assertTrue(waitFor { runBlocking { repo.reviews().first().any { it.amountMinor == 77889L && it.status == "DUPLICATE_REVIEW" } } })
        assertEquals(1, entries().count { it.amountMinor == 77889L })
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
```

## app/src/androidTest/kotlin/com/semmyx/ledger/BankScreenLayoutTest.kt

```kotlin
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
            val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            val backOwner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
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
        NativeScreenshots.save(compose.onRoot().captureToImage(),"bank-320dp-font140-uk")
        compose.onNodeWithTag("bankSetup").performClick()
        compose.onNodeWithTag("bankList").performScrollToNode(hasText("Push банку (рекомендовано)"))
        compose.onNodeWithText("Push банку (рекомендовано)").performScrollTo().assertIsDisplayed()
    }
}
```

## app/src/androidTest/kotlin/com/semmyx/ledger/CompactLayoutTest.kt

```kotlin
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
    @Test fun longBankCategoryDoesNotBreakIntoLetterFragments() {
        val label = "Супермаркети та продукти"
        compose.setContent { LocalizedContent(AppLanguage.UKRAINIAN) { LedgerTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.4f)) {
                Column(Modifier.width(320.dp).padding(16.dp)) {
                    com.semmyx.ledger.bank.BankAmountRow(label,"778,89 ₴", androidx.compose.ui.graphics.Color.Magenta, labelTag="longCategory")
                }
            }
        } } }
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("longCategory").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(layout.hasVisualOverflow)
            for (line in 0 until layout.lineCount-1) {
                val boundary = layout.getLineEnd(line,visibleEnd=true)
                assertTrue("Category word split into fragments at $boundary", boundary >= label.length || label[boundary].isWhitespace())
            }
        }
        screenshot("category-320dp-font140-uk")
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
}
```

## app/src/androidTest/kotlin/com/semmyx/ledger/NativeScreenshots.kt

```kotlin
package com.semmyx.ledger

import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Copy out before Gradle uninstalls the test app and deletes its external files. */
object NativeScreenshots {
    fun save(bitmap: ImageBitmap, name: String) {
        val app = ApplicationProvider.getApplicationContext<LedgerApplication>()
        val file = File(app.getExternalFilesDir(null),"screenshots/$name.png").also { it.parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        shell("mkdir -p /data/local/tmp/tg-ledger-screenshots")
        shell("cp ${file.absolutePath} /data/local/tmp/tg-ledger-screenshots/$name.png")
    }
}
```

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools">
    <uses-feature android:name="android.hardware.telephony" android:required="false" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.USE_BIOMETRIC" />
    <uses-permission android:name="com.android.vending.BILLING" />
    <!-- Only requested at runtime when a TG Plus subscriber taps the voice assistant button. -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <!-- Required on Android 13+ (API 33+) to post background import notifications. -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <!-- Bank SMS import mode (user-selected on the Banks screen). -->
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />

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
        <activity android:name=".support.SupportWebActivity" android:exported="false" />
        <activity android:name=".MainActivity" android:exported="true"
            android:windowSoftInputMode="adjustResize"
            android:theme="@style/Theme.Ledger.Starting">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <receiver android:name=".bank.BankSmsReceiver" android:exported="true" android:permission="android.permission.BROADCAST_SMS">
            <intent-filter android:priority="999"><action android:name="android.provider.Telephony.SMS_RECEIVED" /></intent-filter>
        </receiver>
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
    val bankImport = com.semmyx.ledger.data.bank.BankImportRepository(database, app, vault)
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
import com.semmyx.ledger.bank.BankImportWorker
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
        com.semmyx.ledger.platform.OwnershipSeal.register()
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
        // Bank import must keep running when the app is closed.
        BankImportWorker.schedule(this)
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
import com.semmyx.ledger.navigation.AppRoot
import com.semmyx.ledger.navigation.AppViewModel
import com.semmyx.ledger.common.LedgerViewModelFactory
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

## app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsCharts.kt

```kotlin
package com.semmyx.ledger.analytics

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
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
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

## app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsScreen.kt

```kotlin
package com.semmyx.ledger.analytics

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
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
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

## app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsViewModel.kt

```kotlin
package com.semmyx.ledger.analytics

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

## app/src/main/kotlin/com/semmyx/ledger/auth/AuthViewModel.kt

```kotlin
package com.semmyx.ledger.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.PinPolicy
import com.semmyx.ledger.domain.PinResult
import com.semmyx.ledger.domain.SecurityRepository
import com.semmyx.ledger.common.guarded
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

## app/src/main/kotlin/com/semmyx/ledger/auth/AuthenticationScreen.kt

```kotlin
package com.semmyx.ledger.auth

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
        Row(Modifier.padding(vertical = 8.dp).testTag("pinDots").clearAndSetSemantics { contentDescription = description },
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

## app/src/main/kotlin/com/semmyx/ledger/bank/BankAmountRow.kt

```kotlin
package com.semmyx.ledger.bank

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Never let a right-hand amount squeeze the bank/category name into a few letters. */
@Composable
fun BankAmountRow(label: String, amount: String, color: Color? = null,
    onClick: (() -> Unit)? = null, labelTag: String = "") {
    val labelStyle = MaterialTheme.typography.bodyMedium
    val amountStyle = MaterialTheme.typography.titleSmall
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).padding(vertical = 4.dp)) {
        val available = with(LocalDensity.current) { maxWidth.toPx() }
        val desired = measurer.measure(label, style = labelStyle, softWrap = false).size.width +
            measurer.measure(amount, style = amountStyle, softWrap = false).size.width +
            with(LocalDensity.current) { (if (color == null) 12.dp else 44.dp).toPx() }
        @Composable fun Label(modifier: Modifier) {
            Text(label, modifier.testTag(labelTag), style = labelStyle)
        }
        @Composable fun Dot() { color?.let { Box(Modifier.size(18.dp).background(it,CircleShape)) } }
        if (desired > available) Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(); Label(Modifier.weight(1f))
            }
            Text(amount, modifier = Modifier.fillMaxWidth(), style = amountStyle)
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(); Label(Modifier.weight(1f)); Text(amount, style = amountStyle)
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/bank/BankImportWorker.kt

```kotlin
package com.semmyx.ledger.bank

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.semmyx.ledger.LedgerApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Keeps bank import alive without the app being open:
 *  - re-binds the notification listener if the system dropped it (its start-up replays pending notifications),
 *  - API mode: pulls new operations from monobank; SMS mode: imports bank SMS received while the app was closed.
 * Runs every 15 minutes, after reboot, and once whenever the app process starts.
 */
class BankImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val repository = (applicationContext as LedgerApplication).container.bankImport
            if (!repository.enabled) return Result.success()
            when (repository.importMode) {
                "API" -> { repository.syncMonobank(); repository.syncPrivat() }
                "SMS" -> BankSmsCatchUp.run(applicationContext, repository)
                else -> if (applicationContext.packageName in NotificationManagerCompat.getEnabledListenerPackages(applicationContext)) {
                    ensureListenerBound(applicationContext, repository)
                }
            }
            Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.retry()
        }
    }

    companion object {
        /**
         * The system sometimes leaves a notification listener unbound after an app update, a crash or aggressive
         * battery management. First ask politely; if it is still not connected, toggle the component, which makes
         * Android bind it again (and it then replays the notifications still in the shade).
         */
        suspend fun ensureListenerBound(context: Context, repository: com.semmyx.ledger.data.bank.BankImportRepository) {
            if (repository.diagnostics.value.connected && BankNotificationListener.replayIfConnected()) return
            val component = ComponentName(context, BankNotificationListener::class.java)
            runCatching { NotificationListenerService.requestRebind(component) }
            kotlinx.coroutines.delay(3_000)
            if (repository.diagnostics.value.connected) return
            val pm = context.packageManager
            runCatching {
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(component, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            }
        }

        /** Runs one import pass right now (after switching mode, connecting, or pressing "Sync now"). */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("bank-import-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BankImportWorker>().build())
        }

        fun schedule(context: Context) {
            val work = WorkManager.getInstance(context)
            work.enqueueUniquePeriodicWork("bank-import-keepalive", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BankImportWorker>(15, TimeUnit.MINUTES).build())
            work.enqueueUniqueWork("bank-import-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<BankImportWorker>().build())
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationDecoder.kt

```kotlin
package com.semmyx.ledger.bank

import android.app.Notification
import androidx.core.app.NotificationCompat
import com.semmyx.ledger.domain.bank.Bank
import com.semmyx.ledger.domain.bank.BankParser

/** No network, no raw message persistence. Handles plain, big, inbox and messaging styles. */
object BankNotificationDecoder {
    data class Message(val text: String, val at: Long)

    fun messages(notification: Notification, bank: Bank, postedAt: Long): List<Message> {
        val extras = notification.extras ?: return emptyList()
        // Some apps re-post an old notification. Preserve its original event time when valid.
        val at = notification.`when`.takeIf { it in 1..(postedAt + 300_000) } ?: postedAt
        val title = (extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE))?.toString()?.trim().orEmpty()
        val messaging = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)?.messages }.getOrNull().orEmpty()
        if (messaging.isNotEmpty()) return messaging.mapNotNull { message ->
            val text = message.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) null else Message("$title\n$text", message.timestamp.takeIf { it in 1..(postedAt + 300_000) } ?: at)
        }.distinct()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
            .map { it.toString().trim() }.filter { it.isNotEmpty() }
        // Inbox style can contain several independent payments. Do not merge their amounts.
        val payments = lines.map { Message("$title\n$it", at) }
            .filter { BankParser.parse(bank, it.text, it.at) != null }
        if (payments.isNotEmpty()) return payments.distinct()
        val parts = linkedSetOf<String>()
        fun add(value: CharSequence?) { value?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(parts::add) }
        add(title)
        add(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        add(extras.getCharSequence(Notification.EXTRA_TEXT))
        lines.forEach(::add)
        add(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        if (parts.size < 2) add(notification.tickerText)
        val text = parts.filter { part -> parts.none { other -> other.length > part.length && other.contains(part) } }
            .joinToString("\n")
        return if (text.isBlank()) emptyList() else listOf(Message(text, at))
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationListener.kt

```kotlin
package com.semmyx.ledger.bank

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.semmyx.ledger.LedgerApplication
import kotlinx.coroutines.*

/**
 * Reads bank / wallet notifications in the background, with no Activity needed.
 *
 * Imports are written straight to the database by [com.semmyx.ledger.data.bank.BankImportRepository];
 * the same notification delivered twice (live + replay) is ignored by its event key, and one card tap
 * reported by two apps (e.g. Google Pay + PrivatBank) is collapsed by cross-source deduplication.
 *
 * Catch-up: every time the system (re)binds this service we replay the notifications that are still
 * in the shade, so anything that arrived while the service was dead, the phone rebooted or the app
 * process was killed is still recorded. Force-stop requires opening the app again; dismissed
 * notifications are not recoverable through this Android API.
 */
class BankNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun repository() = (application as LedgerApplication).container.bankImport

    override fun onListenerConnected() {
        current = java.lang.ref.WeakReference(this)
        try {
            repository().connected(true)
            replayActive()
        } catch (e: Exception) {
            android.util.Log.w("BankImport", "Catch-up failed: ${e.javaClass.simpleName}")
        }
    }

    override fun onListenerDisconnected() {
        runCatching { repository().connected(false) }
        // Ask for an immediate rebind instead of waiting for the OS back-off timer.
        runCatching { requestRebind(ComponentName(this, BankNotificationListener::class.java)) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try { handle(sbn) } catch (e: Exception) {
            // A failure here must never crash the process: that would stop all background import.
            android.util.Log.w("BankImport", "Notification handling failed: ${e.javaClass.simpleName}")
        }
    }

    private fun handle(sbn: StatusBarNotification) {
        // Our own "imported" confirmations must never be re-read as bank messages.
        if (sbn.packageName == packageName) return
        val repository = repository()
        if (repository.importMode != "NOTIFY") return
        val bank = repository.bankForPackage(sbn.packageName)
        repository.note(
            if (bank == null) "UNSUPPORTED_APP" else "BANK_RECEIVED",
            sbn.packageName, notification = true, bankMessage = bank != null,
        )
        bank ?: return

        val notification = sbn.notification
        // Group summaries carry no transaction data.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) { repository.note("GROUP_SUMMARY"); return }

        val messages = BankNotificationDecoder.messages(notification, bank, sbn.postTime)
        if (messages.isEmpty()) { repository.note("EMPTY_TEXT"); return }
        if (!repository.enabled) { repository.note("DISABLED"); return }
        scope.launch {
            // Finish a started database import if Android tears down the listener mid-write.
            withContext(NonCancellable) {
                messages.forEach { message ->
                    try {
                        repository.receive(bank, message.text,
                            eventKey = "PUSH:${sbn.packageName}:${sbn.key}:${message.at}:${message.text}",
                            at = message.at, origin = "PUSH")
                    } catch (_: Exception) {
                        repository.note("ERROR")
                        android.util.Log.w("BankImport", "Background import failed; no message contents logged")
                    }
                }
            }
        }
    }

    private fun replayActive() {
        runCatching { activeNotifications?.sortedBy { it.postTime }?.forEach(::handle) }
            .onFailure { repository().note("ERROR") }
    }

    companion object {
        @Volatile private var current = java.lang.ref.WeakReference<BankNotificationListener>(null)
        /** Also used when switching into push mode while the listener is already bound. */
        fun replayIfConnected(): Boolean {
            val listener = current.get() ?: return false
            listener.replayActive()
            return true
        }
    }

    override fun onDestroy() {
        if (current.get() === this) current.clear()
        runCatching { repository().connected(false) }
        scope.cancel()
        super.onDestroy()
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/bank/BankScreen.kt

```kotlin
package com.semmyx.ledger.bank

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.BuildConfig
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import com.semmyx.ledger.common.*
import com.semmyx.ledger.theme.LocalAppearance
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BankScreen(model: BankViewModel, repository: com.semmyx.ledger.data.bank.BankImportRepository, onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, onPremium: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val diagnostics by model.diagnostics.collectAsStateWithLifecycle()
    val reviews by model.reviews.collectAsStateWithLifecycle()
    val custom by model.customCategories.collectAsStateWithLifecycle()
    val enabled by model.enabled.collectAsStateWithLifecycle()
    val automatic by model.auto.collectAsStateWithLifecycle()
    val failed by model.error.collectAsStateWithLifecycle()
    val revision by model.revision.collectAsStateWithLifecycle()
    val mode by model.mode.collectAsStateWithLifecycle()
    val mono by model.mono.collectAsStateWithLifecycle()
    val privat by model.privat.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalAppearance.current
    var notificationAccess by remember { mutableStateOf(false) }
    var confirmationAccess by remember { mutableStateOf(false) }
    var smsAccess by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    var packageDialog by remember { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf(false) }
    var privacy by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var privatToken by remember { mutableStateOf("") }
    var connectingPrivat by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var smsScan by remember { mutableStateOf<BankSmsCatchUp.Result?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var setupExpanded by remember { mutableStateOf(false) }
    
    fun openSettings(action: String, data: Uri? = null) {
        try { context.startActivity(Intent(action, data).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { exportFailed = true }
    }
    val confirmationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { confirmationAccess = it }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        smsAccess = result[Manifest.permission.RECEIVE_SMS] == true
        if (smsAccess) BankImportWorker.schedule(context)
    }
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
        confirmationAccess = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        smsAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (repository.importMode == "NOTIFY") BankImportWorker.runNow(context)
        onPauseOrDispose { }
    }
    val currency = state.preferences.currency
    val language = state.preferences.language
    val total = state.categories.sumOf { it.totalMinor }
    val budget = remember(currency, revision) { model.budget(currency) }
    var budgetText by remember(currency, budget) { mutableStateOf(if (budget > 0) Money.editable(budget) else "") }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.bank_dashboard)) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
    }, actions = { IconButton(onClick = onAdd) { Icon(Icons.Default.Add, stringResource(R.string.add_entry)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("bankList"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (failed || exportFailed || state.failed) item { Text(stringResource(R.string.storage_error), color = MaterialTheme.colorScheme.error) }
            item {
                OutlinedButton(onClick = { setupExpanded = !setupExpanded }, modifier = Modifier.fillMaxWidth().testTag("bankSetup")) {
                    Icon(if (setupExpanded) Icons.Default.ExpandLess else Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bank_setup))
                }
            }
            if (setupExpanded) {
            item { ContentCard {
                Text(stringResource(R.string.bank_import_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.bank_consent), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.bank_enabled), Modifier.weight(1f)); Switch(enabled, model::enabled) }
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.bank_auto), Modifier.weight(1f)); Switch(automatic, model::auto, enabled = enabled) }
                Text(stringResource(R.string.import_mode_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NOTIFY" to R.string.import_mode_notify, "API" to R.string.import_mode_api, "SMS" to R.string.import_mode_sms).forEach { (value, title) ->
                        FilterChip(selected = mode == value, onClick = { model.mode(value); BankImportWorker.runNow(context) }, label = { Text(stringResource(title)) })
                    }
                }
                when (mode) {
                    "API" -> {
                        Text(stringResource(R.string.api_howto), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { openSettings(Intent.ACTION_VIEW, Uri.parse("https://api.monobank.ua/")) }) { Text(stringResource(R.string.api_open_site)) }
                        if (!mono.connected) {
                            OutlinedTextField(token, { token = it.trim().take(200) }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(stringResource(R.string.api_token_label)) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                isError = mono.error == "BAD_TOKEN")
                            Button(enabled = token.length >= 20 && !connecting, onClick = {
                                connecting = true
                                scope.launch {
                                    if (repository.connectMonobank(token)) { token = ""; BankImportWorker.runNow(context) }
                                    connecting = false
                                }
                            }) { Text(stringResource(if (connecting) R.string.api_connecting else R.string.api_connect)) }
                        } else {
                            Text(stringResource(R.string.api_connected, mono.accounts.coerceAtLeast(1)), color = MaterialTheme.colorScheme.secondary)
                            if (mono.lastSyncMillis > 0) Text(stringResource(R.string.api_last_sync,
                                Instant.ofEpochMilli(mono.lastSyncMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))) + " · " +
                                stringResource(R.string.api_imported_last, mono.lastImported), style = MaterialTheme.typography.bodySmall)
                            if (mono.syncing) Text(stringResource(R.string.api_syncing), style = MaterialTheme.typography.bodySmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(enabled = !mono.syncing, onClick = { BankImportWorker.runNow(context) }) { Text(stringResource(R.string.api_sync_now)) }
                                TextButton(onClick = { scope.launch { repository.disconnectMonobank() } }) { Text(stringResource(R.string.api_disconnect)) }
                            }
                        }
                        mono.error?.let { code ->
                            Text(stringResource(when (code) {
                                "BAD_TOKEN" -> R.string.api_error_bad_token; "RATE_LIMIT" -> R.string.api_error_rate
                                "NO_ACCOUNTS" -> R.string.api_error_no_accounts; else -> R.string.api_error_network
                            }), color = MaterialTheme.colorScheme.error)
                        }
                        HorizontalDivider()
                        Text(stringResource(R.string.privat_api_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.privat_api_howto), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { openSettings(Intent.ACTION_VIEW, Uri.parse("https://privatbank.ua/business/intehratsiya")) }) { Text(stringResource(R.string.privat_api_site)) }
                        if (!privat.connected) {
                            OutlinedTextField(privatToken, { privatToken = it.trim().take(2048) }, Modifier.fillMaxWidth(), singleLine = true,
                                label = { Text(stringResource(R.string.privat_api_token)) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            Button(enabled = privatToken.length >= 20 && !connectingPrivat, onClick = {
                                connectingPrivat = true
                                scope.launch {
                                    try { if (repository.connectPrivat(privatToken)) { privatToken = ""; BankImportWorker.runNow(context) } }
                                    finally { connectingPrivat = false }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (connectingPrivat) R.string.api_connecting else R.string.api_connect)) }
                        } else {
                            Text(stringResource(R.string.privat_api_connected))
                            if (privat.lastSyncMillis > 0) Text(stringResource(R.string.api_last_sync,
                                Instant.ofEpochMilli(privat.lastSyncMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled = !privat.syncing, onClick = { BankImportWorker.runNow(context) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.api_sync_now)) }
                            TextButton(onClick = { scope.launch { repository.disconnectPrivat() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.api_disconnect)) }
                        }
                        privat.error?.let { Text(stringResource(R.string.privat_api_error), color = MaterialTheme.colorScheme.error) }
                        Text(stringResource(R.string.other_api_note), style = MaterialTheme.typography.bodySmall)
                    }
                    "SMS" -> {
                        Text(stringResource(R.string.sms_howto), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(if (smsAccess) R.string.bank_sms_yes else R.string.bank_sms_no))
                        if (!smsAccess) Button(onClick = { smsPermission.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }) { Text(stringResource(R.string.bank_sms_permission)) }
                        OutlinedButton(enabled = smsAccess && !scanning, onClick = {
                            scanning = true
                            scope.launch { smsScan = withContext(Dispatchers.IO) { BankSmsCatchUp.run(context, repository, 30) }; scanning = false }
                        }) { Text(stringResource(if (scanning) R.string.sms_scanning else R.string.sms_scan)) }
                        smsScan?.let { r ->
                            Text(if (!r.permission) stringResource(R.string.bank_sms_no)
                                else stringResource(R.string.sms_scan_result, r.bankMessages, r.imported, r.queued), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.sms_restricted_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text(stringResource(R.string.push_howto), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(if (notificationAccess) R.string.bank_access_yes else R.string.bank_access_no))
                        OutlinedButton(onClick = { openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }) { Text(stringResource(R.string.bank_notification_permission)) }
                    }
                }
                TextButton(onClick = { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)) }) { Text(stringResource(R.string.bank_app_permissions)) }
                TextButton(onClick = { openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) { Text(stringResource(R.string.bank_battery)) }
                if (!confirmationAccess && android.os.Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { confirmationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_confirm_permission)) }
                else Text(stringResource(R.string.import_confirm_enabled), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { privacy = true }) { Text(stringResource(R.string.bank_privacy)) }
            } }
            item { ContentCard {
                Text(stringResource(R.string.import_diagnostics), style = MaterialTheme.typography.titleMedium)
                if (mode == "NOTIFY") {
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
                }
                Button(onClick = { pasteDialog = true }) { Text(stringResource(R.string.import_paste)) }
                Text(stringResource(R.string.import_queue_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAY" to R.string.bank_day, "WEEK" to R.string.bank_week, "MONTH" to R.string.bank_month, "ALL" to R.string.all_time).forEach { (period, title) ->
                    FilterChip(modifier = Modifier.testTag("bankPeriod:$period"), selected = state.filter.period == period, onClick = { if (period == "DAY" || state.premium) model.period(period) else onPremium() }, label = {
                        Text(stringResource(title) + if (period != "DAY" && !state.premium) " · Plus" else "")
                    })
                }
            } }
            item { ContentCard {
                Text(stringResource(R.string.expenses), color = palette.type(EntryType.EXPENSE))
                Text(formatMoney(total, currency, language), style = MaterialTheme.typography.headlineMedium, color = palette.type(EntryType.EXPENSE))
                FilterChip(selected = state.filter.source == null, onClick = { model.source(null) }, label = { Text(stringResource(R.string.all)) })
                state.sources.forEach { source ->
                    val name = when (source.sourceType) { "CASH" -> stringResource(R.string.bank_cash); "CARD" -> Banks.name(source.bankId); else -> stringResource(R.string.bank_unknown) }
                    BankAmountRow(name, formatMoney(source.totalMinor, currency, language),
                        onClick = { model.source(source.sourceType, source.bankId) })
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
                    BankAmountRow(cat.customName ?: stringResource(cat.category.label()),
                        formatMoney(cat.totalMinor, currency, language), color, labelTag = "bankCategoryLabel:${cat.category.name}")
                    LinearProgressIndicator(progress = { if (total == 0L) 0f else (cat.totalMinor.toDouble() / total).toFloat() }, modifier = Modifier.fillMaxWidth(), color = color)
                }
                TextButton(onClick = { categoryDialog = true }) { Text(stringResource(R.string.bank_new_category)) }
                custom.forEach { Text(it.name, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } }
            if (reviews.isNotEmpty()) item { Text(stringResource(R.string.bank_review_count, reviews.size), style = MaterialTheme.typography.titleLarge) }
            items(reviews, key = { "review-" + it.id }) { item -> ContentCard {
                // Bank name + merchant — can be long, allow 2 lines
                Text(
                    Banks.name(item.bankId) + " · " + item.merchant.ifBlank { "—" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    formatMoney(item.amountMinor, item.currency, language),
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.type(item.type),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Instant.ofEpochMilli(item.eventAtMillis).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss", Locale.forLanguageTag(language.tag))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.cardLast4?.let { Text("•••• $it", style = MaterialTheme.typography.bodySmall) }
                if (!item.timeFromMessage) Text(stringResource(R.string.bank_time_received), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (item.transfer) Text(stringResource(R.string.bank_transfer_review), style = MaterialTheme.typography.bodySmall)
                // Duplicate warnings
                when (item.status) {
                    "DUPLICATE_REVIEW" -> Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.bank_duplicate), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    "CROSS_DUPLICATE_REVIEW" -> Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SwapHoriz, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Text("Можливий дублікат з іншого банку/гаманця", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
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
    "QUEUED", "REVIEW", "CROSS_DUPLICATE_REVIEW" -> R.string.import_queued
    "DUPLICATE_REVIEW" -> R.string.bank_duplicate
    "REDELIVERED" -> R.string.import_repeated
    "ERROR" -> R.string.storage_error
    else -> R.string.import_waiting
}
```

## app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsCatchUp.kt

```kotlin
package com.semmyx.ledger.bank

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.semmyx.ledger.data.bank.BankImportRepository
import com.semmyx.ledger.domain.bank.Banks
import java.util.concurrent.TimeUnit

/**
 * Reads bank SMS that arrived while the app was closed (needs READ_SMS).
 * Without the permission it does nothing. Messages already recorded are skipped by event key.
 */
object BankSmsCatchUp {
    private const val FIRST_RUN_DAYS = 30L
    private const val OVERLAP_MINUTES = 10L

    data class Result(val permission: Boolean, val bankMessages: Int = 0, val imported: Int = 0, val queued: Int = 0)

    /** [days] != null rescans that many days of the inbox regardless of the last scan (manual "Scan now"). */
    suspend fun run(context: Context, repository: BankImportRepository, days: Long? = null): Result {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return Result(false)
        var bankMessages = 0; var imported = 0; var queued = 0
        val prefs = context.getSharedPreferences("bank_import", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val since = if (days != null) now - TimeUnit.DAYS.toMillis(days)
            else prefs.getLong("sms_scan_at", now - TimeUnit.DAYS.toMillis(FIRST_RUN_DAYS)) - TimeUnit.MINUTES.toMillis(OVERLAP_MINUTES)
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val sender = cursor.getString(0).orEmpty()
                val bank = Banks.bySender(sender) ?: continue
                val text = cursor.getString(1).orEmpty().take(8001)
                if (text.isBlank()) continue
                val sent = cursor.getLong(3)
                val at = if (sent > 0) sent else cursor.getLong(2)
                // Same event key as BankSmsReceiver so a live message and its catch-up copy are one record.
                bankMessages++
                when (repository.receive(bank, text, "SMS:$sender:$at:$text", at, "SMS")) {
                    "AUTO_IMPORTED" -> imported++
                    "REVIEW", "DUPLICATE_REVIEW", "CROSS_DUPLICATE_REVIEW" -> queued++
                }
            }
        }
        if (days == null) prefs.edit().putLong("sms_scan_at", now).apply()
        return Result(true, bankMessages, imported, queued)
    }
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

/** SMS import mode: reads each new bank SMS as it arrives (historical inbox is read by [BankSmsCatchUp]). */
class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val sender = messages.first().originatingAddress.orEmpty()
        val bank = Banks.bySender(sender) ?: return
        val repository = (context.applicationContext as LedgerApplication).container.bankImport
        if (!repository.enabled || repository.importMode != "SMS") return
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

## app/src/main/kotlin/com/semmyx/ledger/bank/BankViewModel.kt

```kotlin
package com.semmyx.ledger.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class BankFilter(val period: String = "DAY", val source: String? = null, val bank: String? = null)
data class BankDashboardState(val preferences: UserPreferences = UserPreferences(), val filter: BankFilter = BankFilter(),
    val sources: List<BankSourceSpend> = emptyList(), val categories: List<BankCategorySpend> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(), val premium: Boolean = false, val failed: Boolean = false)
@OptIn(ExperimentalCoroutinesApi::class)
class BankViewModel(private val repo: BankRepository, preferences: PreferencesRepository, premium: PremiumRepository, private val clock: Clock) : ViewModel() {
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
    val mode = MutableStateFlow(repo.importMode)
    val privat = repo.privatStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonobankStatus())
    val mono = repo.monobankStatus.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonobankStatus())
    fun mode(value: String) { repo.importMode = value; mode.value = value }
    val auto = MutableStateFlow(repo.autoImport)
    val error = MutableStateFlow(false)
    val revision = MutableStateFlow(0)
    val state = combine(preferences.preferences, premium.state, filter, today) { prefs, access, selected, date ->
        Request(prefs, access.entitled, selected.copy(period = BankPeriodAccess.effective(selected.period, access.entitled)), date)
    }.flatMapLatest { (prefs, entitled, selected, through) ->
        val from = when (selected.period) { "DAY" -> through; "WEEK" -> through.minusDays(6); "ALL" -> LocalDate.of(2000, 1, 1); else -> through.minusDays(29) }
        combine(repo.sources(prefs.currency, from, through), repo.categorySpending(prefs.currency, from, through, selected.source, selected.bank),
            repo.recent(prefs.currency, from, through, selected.source, selected.bank)) { sources, cats, entries -> BankDashboardState(prefs, selected, sources, cats, entries, premium = entitled) }
    }.catch { emit(BankDashboardState(failed = true)) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BankDashboardState())
    fun period(value: String) { if (value in BankPeriodAccess.periods) filter.update { it.copy(period = value) } }
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
    private data class Request(val prefs: UserPreferences, val entitled: Boolean, val selected: BankFilter, val through: LocalDate)
    private fun task(block: suspend () -> Unit) { viewModelScope.launch { try { block(); error.value = false }
        catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { error.value = true } } }
}
```

## app/src/main/kotlin/com/semmyx/ledger/cat/CatAnimation.kt

```kotlin
package com.semmyx.ledger.cat

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
    else CatMeshRenderer(if (motionEnabled) pose else CatPose(pleased = if (balanceMinor > 0) 1f else 0f), modifier, skin = skin)
}
```

## app/src/main/kotlin/com/semmyx/ledger/cat/CatAssistantCard.kt

```kotlin
package com.semmyx.ledger.cat

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.AppLanguage

@Composable
fun CatAssistantCard(language: AppLanguage, listening: Boolean, lastReply: String?, onTalk: () -> Unit,
    modifier: Modifier = Modifier, skin: String = "gray", animations: Boolean = true,
    onText: (String) -> Unit = {}, balanceMinor: Long = 0, locked: Boolean = false) {
    var text by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val label = stringResource(R.string.voice_assistant_button)
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                @Composable fun Cat(modifier: Modifier) {
                    LiveCatHost(balanceMinor, listening, skin, modifier
                        .clickable(enabled = !listening, onClickLabel = label, role = Role.Button, onClick = onTalk)
                        .semantics { contentDescription = label }.testTag("voiceAssistantButton"), motionEnabled = animations)
                }
                @Composable fun Caption(modifier: Modifier) {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.mascot_card_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text(if (locked) stringResource(R.string.cat_locked_hint) else if (listening) stringResource(R.string.voice_assistant_listening) else lastReply ?: stringResource(R.string.cat_tap_hint),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!locked) TextButton(onClick = { typing = !typing }) { Text(stringResource(R.string.assistant_write_short)) }
                    }
                }
                if (LocalDensity.current.fontScale > 1.5f || maxWidth < 250.dp) Column {
                    Cat(Modifier.fillMaxWidth().height(104.dp))
                    Caption(Modifier.fillMaxWidth())
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Cat(Modifier.size(104.dp))
                    Caption(Modifier.weight(1f))
                }
            }
            AnimatedVisibility(typing && !locked, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
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

## app/src/main/kotlin/com/semmyx/ledger/cat/CatFaceMesh.kt

```kotlin
package com.semmyx.ledger.cat

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

## app/src/main/kotlin/com/semmyx/ledger/cat/CatMeshRenderer.kt

```kotlin
package com.semmyx.ledger.cat

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
fun CatMeshRenderer(pose: CatPose, modifier: Modifier, skin: String = "gray") {
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

## app/src/main/kotlin/com/semmyx/ledger/common/CommonComponents.kt

```kotlin
package com.semmyx.ledger.common

import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.theme.LocalAppearance
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

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

fun formatMoney(minor: Long, currency: LedgerCurrency, language: AppLanguage): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag(language.tag)).apply {
        this.currency = Currency.getInstance(currency.name)
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(BigDecimal.valueOf(minor, 2))

fun formatDate(date: LocalDate, language: AppLanguage): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.tag)))

/** Used for the developer-activation expiry date, which is a real instant rather than a calendar day. */
fun formatDateTime(epochMillis: Long, language: AppLanguage): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.forLanguageTag(language.tag)))

// ---------------------------------------------------------------------------
// Category / message labels
// ---------------------------------------------------------------------------

@StringRes fun Category.label(): Int = when (this) {
    Category.FOOD      -> R.string.category_food
    Category.TRANSPORT -> R.string.category_transport
    Category.HOME      -> R.string.category_home
    Category.HEALTH    -> R.string.category_health
    Category.LEISURE   -> R.string.category_leisure
    Category.SALARY    -> R.string.category_salary
    Category.OTHER     -> R.string.category_other
    Category.TRANSFER  -> R.string.category_transfer
    Category.DINING    -> R.string.category_dining
    Category.CASHBACK  -> R.string.category_cashback
}

fun Category.icon(): ImageVector = when (this) {
    Category.FOOD      -> Icons.Default.ShoppingCart
    Category.TRANSPORT -> Icons.Default.DirectionsBus
    Category.HOME      -> Icons.Default.Home
    Category.HEALTH    -> Icons.Default.FavoriteBorder
    Category.LEISURE   -> Icons.Default.LocalActivity
    Category.SALARY    -> Icons.Default.WorkOutline
    Category.OTHER     -> Icons.Default.ReceiptLong
    Category.TRANSFER  -> Icons.Default.DoubleArrow
    Category.DINING    -> Icons.Default.Restaurant
    Category.CASHBACK  -> Icons.Default.CurrencyExchange
}

@StringRes fun UiMessage.label(): Int = when (this) {
    UiMessage.STORAGE              -> R.string.storage_error
    UiMessage.SAVED                -> R.string.saved
    UiMessage.DELETED              -> R.string.entry_deleted
    UiMessage.UNDO_EXPIRED         -> R.string.undo_expired
    UiMessage.RATE_OFFLINE         -> R.string.rate_offline
    UiMessage.BIOMETRIC_UNAVAILABLE -> R.string.biometric_unavailable
    UiMessage.NAME_INVALID         -> R.string.name_invalid
    UiMessage.NOT_FOUND            -> R.string.entry_not_found
    UiMessage.PREMIUM_ACTIVATED    -> R.string.premium_activated
    UiMessage.ACTIVATION_INVALID   -> R.string.activation_invalid
}

@StringRes fun BillingIssue.label(): Int = when (this) {
    BillingIssue.UNAVAILABLE    -> R.string.billing_unavailable
    BillingIssue.CONFIGURATION  -> R.string.billing_configuration
    BillingIssue.NETWORK        -> R.string.billing_network
    BillingIssue.PENDING        -> R.string.billing_pending
    BillingIssue.VERIFICATION   -> R.string.billing_verification
    BillingIssue.ACKNOWLEDGEMENT -> R.string.billing_acknowledgement
    BillingIssue.ACTIVATION_CODE -> R.string.billing_activation_code
}

// ---------------------------------------------------------------------------
// Brand mark
// ---------------------------------------------------------------------------

@Composable
fun LedgerMark(modifier: Modifier = Modifier, color: Color = LocalAppearance.current.color("icons")) {
    Canvas(modifier.size(40.dp)) {
        val w = size.width / 5f
        listOf(0.42f, 0.68f, 1f).forEachIndexed { index, height ->
            drawRoundRect(
                color,
                Offset(index * w * 1.6f, size.height * (1 - height)),
                Size(w, size.height * height),
                CornerRadius(w * .2f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Navigation bar
// ---------------------------------------------------------------------------

@Composable
fun LedgerBottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        R.string.overview, R.string.settings, R.string.help,
        R.string.analytics_title, R.string.bank_dashboard,
    )
    val icons = listOf(
        Icons.Default.SpaceDashboard, Icons.Default.Tune, Icons.Default.HelpOutline,
        Icons.Default.BarChart, Icons.Default.AccountBalance,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            NavigationBarItem(
                selected = isSelected,
                onClick  = { onSelect(index) },
                icon = {
                    Icon(
                        icons[index], null,
                        tint = LocalAppearance.current.color("icons")
                            .copy(alpha = if (isSelected) 1f else .55f),
                    )
                },
                label = {
                    Text(
                        stringResource(label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Content card  — used everywhere for grouped sections
// ---------------------------------------------------------------------------

/**
 * Premium card with a subtle gradient tint on the top-left corner.
 * Text inside never overflows: Columns use fillMaxWidth; callers must
 * supply maxLines / overflow on individual Text composables as needed.
 */
@Composable
fun ContentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape  = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Empty / loading states
// ---------------------------------------------------------------------------

@Composable
fun EmptyState(
    title: String,
    description: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                Icons.Default.ReceiptLong, null,
                Modifier.padding(20.dp).size(36.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            title,
            style     = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines  = 3,
            overflow  = TextOverflow.Ellipsis,
        )
        Text(
            description,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style     = MaterialTheme.typography.bodyMedium,
            maxLines  = 5,
            overflow  = TextOverflow.Ellipsis,
        )
        FilledTonalButton(
            onClick   = onAction,
            modifier  = Modifier.heightIn(min = 48.dp),
        ) { Text(action, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/common/ViewModelSupport.kt

```kotlin
package com.semmyx.ledger.common

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

## app/src/main/kotlin/com/semmyx/ledger/dashboard/ActivityChart.kt

```kotlin
package com.semmyx.ledger.dashboard

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
import com.semmyx.ledger.common.ContentCard
import com.semmyx.ledger.common.formatMoney
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

## app/src/main/kotlin/com/semmyx/ledger/dashboard/CategoryDetails.kt

```kotlin
package com.semmyx.ledger.dashboard

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
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
import com.semmyx.ledger.analytics.*

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

## app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardScreen.kt

```kotlin
package com.semmyx.ledger.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
import com.semmyx.ledger.cat.CatAssistantCard
import com.semmyx.ledger.cat.trackCatTouch
import android.widget.Toast
import com.semmyx.ledger.voice.OptionalVoiceAssistant
import com.semmyx.ledger.voice.VoiceCommand
import com.semmyx.ledger.voice.VoiceEntryParser
import com.semmyx.ledger.voice.VoiceSmallTalk
import java.time.LocalDate
import com.semmyx.ledger.voice.VoiceFinanceQuery
import com.semmyx.ledger.voice.SpokenMoneyFormatter
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(state: DashboardState, snackbar: SnackbarHostState, host: FragmentActivity, onNavigate: (Int) -> Unit,
    onAdd: () -> Unit, onEdit: (String) -> Unit, onDelete: (String) -> Unit, onSearch: (String) -> Unit,
    onFilter: (EntryType?) -> Unit, onDays: (Int) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit,
    onVoiceEntry: (com.semmyx.ledger.voice.VoiceEntryDraft, String) -> Unit,
    speechRecognizerLauncher: ActivityResultLauncher<Intent>, micPermissionLauncher: ActivityResultLauncher<String>,
    setOnSpeechResult: ((Intent?) -> Unit) -> Unit, setOnMicPermissionResult: ((Boolean) -> Unit) -> Unit,
    onCategory: (Category?) -> Unit, onMoreCategory: () -> Unit,
    onStatistics: (VoiceFinanceQuery.Statistics, (Long) -> Unit) -> Unit) {
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
        var pendingVoice by remember { mutableStateOf<com.semmyx.ledger.voice.VoiceEntryDraft?>(null) }
        val finishVoiceEntry: (com.semmyx.ledger.voice.VoiceEntryDraft, String) -> Unit = { ready, source ->
            pendingVoice = null
            onVoiceEntry(ready, source)
            val language = state.preferences.language
            val typeLabel = voiceContext.getString(if (ready.type == EntryType.INCOME) R.string.income else R.string.expense)
            val reply = voiceContext.getString(R.string.voice_entry_added, typeLabel,
                formatMoney(ready.amountMinor, state.preferences.currency, language), ready.title, voiceContext.getString(ready.category.label()))
            lastReply = reply
            ensureAssistant().speak(reply, language)
        }
        pendingVoice?.let { ready ->
            AlertDialog(onDismissRequest = { pendingVoice = null }, title = { Text(stringResource(R.string.voice_ask_source)) },
                text = { Text(ready.title + " · " + formatMoney(ready.amountMinor, state.preferences.currency, state.preferences.language)) },
                confirmButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { finishVoiceEntry(ready, "CARD") }, modifier = Modifier.testTag("voiceSourceCard")) { Text(stringResource(R.string.bank_card)) }
                    Button(onClick = { finishVoiceEntry(ready, "CASH") }, modifier = Modifier.testTag("voiceSourceCash")) { Text(stringResource(R.string.bank_cash)) }
                } },
                dismissButton = { TextButton(onClick = { pendingVoice = null }) { Text(stringResource(R.string.cancel)) } })
        }
        val processInput: (String?) -> Unit = { heard ->
                listening = false
                val assistant = ensureAssistant()
                val language = state.preferences.language
                val query = heard?.let { VoiceFinanceQuery.parse(it) }
                val draft = if (query == null) heard?.let { VoiceEntryParser.parse(it) } else null
                when {
                    query == VoiceFinanceQuery.Balance -> {
                        val reply = voiceContext.getString(R.string.voice_reply_balance,
                            SpokenMoneyFormatter.format(state.snapshot.balance.netMinor, state.preferences.currency, language))
                        lastReply = reply; assistant.speak(reply, language)
                    }
                    query is VoiceFinanceQuery.Statistics -> onStatistics(query) { amount ->
                        val period = voiceContext.getString(when (query.period) {
                            VoiceFinanceQuery.Period.TODAY -> R.string.period_today
                            VoiceFinanceQuery.Period.WEEK -> R.string.last_7_days
                            VoiceFinanceQuery.Period.MONTH -> R.string.period_month
                            VoiceFinanceQuery.Period.YEAR -> R.string.period_year
                            VoiceFinanceQuery.Period.ALL -> R.string.all_time
                        })
                        val category = query.category?.let { voiceContext.getString(it.label()) } ?: voiceContext.getString(R.string.all_categories)
                        val kind = voiceContext.getString(if (query.type == EntryType.EXPENSE) R.string.expenses else R.string.income)
                        val reply = voiceContext.getString(R.string.voice_statistics, kind, period, category,
                            SpokenMoneyFormatter.format(amount, state.preferences.currency, language))
                        lastReply = reply; assistant.speak(reply, language)
                    }
                    draft != null -> {
                        val title = draft.title.ifBlank { voiceContext.getString(draft.category.label()) }
                        val ready = draft.copy(title = title)
                        val said = heard?.let { VoiceEntryParser.detectSource(it) }
                        if (said != null) finishVoiceEntry(ready, said)
                        else {
                            // The cat always needs to know how it was paid before it writes the entry.
                            pendingVoice = ready
                            val ask = voiceContext.getString(R.string.voice_ask_source)
                            lastReply = ask
                            assistant.speak(ask, language)
                        }
                    }
                    heard != null -> when (OptionalVoiceAssistant.classify(heard)) {
                        VoiceCommand.ShowBalance -> {
                            val reply = voiceContext.getString(R.string.voice_reply_balance,
                                SpokenMoneyFormatter.format(state.snapshot.balance.netMinor, state.preferences.currency, language))
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
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item(key = "greeting", contentType = "heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (state.preferences.displayName.isBlank()) stringResource(R.string.dashboard_heading)
                            else stringResource(R.string.greeting, state.preferences.displayName), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.dashboard_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "balance", contentType = "summary") { CompactBalanceSummary(state) }
                // The cat is always on the home screen; without Premium it is shown locked and opens the Premium card.
                if (!state.premium || state.preferences.premiumAssistantEnabled) {
                    item(key = "assistant", contentType = "assistant") {
                        CatAssistantCard(state.preferences.language, listening, lastReply,
                            onTalk = if (state.premium) onVoice else ({ onNavigate(1) }),
                            skin = state.preferences.mascotSkin, animations = state.preferences.mascotAnimations,
                            onText = { processInput(it) }, balanceMinor = state.snapshot.balance.netMinor, locked = !state.premium)
                    }
                }

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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** One compact card instead of a tall balance card and two separate totals. */
@Composable
fun CompactBalanceSummary(state: DashboardState) {
    ContentCard(Modifier.testTag("balanceSummary")) {
        val balance = state.snapshot.balance
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.3f &&
                listOf(balance.netMinor, balance.incomeMinor, balance.expenseMinor).all {
                    formatMoney(it, state.preferences.currency, state.preferences.language).length <= 14
                }
            @Composable fun Metric(label: Int, amount: Long, color: Color, tag: String, modifier: Modifier = Modifier) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (compact) Modifier.heightIn(min = with(LocalDensity.current) { 32.sp.toDp() }) else Modifier)
                    BasicText(formatMoney(amount, state.preferences.currency, state.preferences.language),
                        style = MaterialTheme.typography.titleMedium.copy(color = color),
                        maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp, stepSize = 1.sp),
                        modifier = Modifier.fillMaxWidth().testTag(tag))
                }
            }
            if (compact) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(R.string.total_balance, balance.netMinor, MaterialTheme.colorScheme.primary, "totalBalance", Modifier.weight(1f))
                Metric(R.string.income, balance.incomeMinor, MaterialTheme.colorScheme.secondary, "totalIncome", Modifier.weight(1f))
                Metric(R.string.expenses, balance.expenseMinor, MaterialTheme.colorScheme.tertiary, "totalExpenses", Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(R.string.total_balance, balance.netMinor, MaterialTheme.colorScheme.primary, "totalBalance")
                // Each metric gets the entire line at large system font sizes.
                Metric(R.string.income, balance.incomeMinor, MaterialTheme.colorScheme.secondary, "totalIncome")
                Metric(R.string.expenses, balance.expenseMinor, MaterialTheme.colorScheme.tertiary, "totalExpenses")
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LedgerEntry, language: AppLanguage, onEdit: () -> Unit, onDelete: () -> Unit) {
    val categoryTint = LocalAppearance.current.category(entry.category, entry.type == EntryType.INCOME)
    val isIncome = entry.type == EntryType.INCOME
    Card(
        modifier  = Modifier.fillMaxWidth().testTag("entry:${entry.id}"),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Category icon in a soft pill — fixed size, never squishes
            Surface(shape = MaterialTheme.shapes.medium, color = categoryTint.copy(alpha = .14f)) {
                Icon(entry.category.icon(), null, Modifier.padding(11.dp).size(22.dp), tint = categoryTint)
            }
            // Title + date — weight(1f) ensures it never pushes the amount off-screen
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${stringResource(entry.category.label())} · ${formatDate(entry.date, language)}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Amount + delete icon — right-aligned, amounts never wrap
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(
                        if (isIncome) R.string.positive_amount else R.string.negative_amount,
                        formatMoney(entry.amountMinor, entry.currency, language),
                    ),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = if (isIncome) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete_named_entry, entry.title),
                    modifier = Modifier.size(16.dp).clickable(onClick = onDelete),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
                )
            }
        }
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardViewModel.kt

```kotlin
package com.semmyx.ledger.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.UiMessage
import com.semmyx.ledger.common.guarded
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
    fun addVoiceEntry(draft: VoiceEntryDraft, sourceType: String = "UNKNOWN") {
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
                sourceType = sourceType,
            ))
        }
    }
    suspend fun answerStatistics(request: com.semmyx.ledger.voice.VoiceFinanceQuery.Statistics): Long {
        val now = LocalDate.now(clock)
        return entries.statistics(state.value.preferences.currency, request.from(now), now, request.type, request.category)
    }
    private data class DashboardRequest(val prefs: UserPreferences, val premium: Boolean, val spec: QuerySpec, val today: LocalDate)
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

## app/src/main/kotlin/com/semmyx/ledger/editor/EntryScreen.kt

```kotlin
package com.semmyx.ledger.editor

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.R
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.*
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
        else {
            val fallbackTitle = customCategories.firstOrNull { it.id == state.customCategoryId }?.name ?: stringResource(state.category.label())
            val amountFocus = remember { FocusRequester() }
            LaunchedEffect(state.loading) { if (!state.editing) runCatching { amountFocus.requestFocus() } }
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Form: type, amount, optional title, category, date, save. Empty title falls back to the category name.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.tertiary), selected = state.type == EntryType.EXPENSE, onClick = { model.type(EntryType.EXPENSE) }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.expense)) })
                    FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.secondary), selected = state.type == EntryType.INCOME, onClick = { model.type(EntryType.INCOME) }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.income)) })
                }
                OutlinedTextField(value = state.amount, onValueChange = model::amount, label = { Text(stringResource(R.string.amount)) },
                    suffix = { Text(state.currency.name) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = state.validation == ValidationIssue.AMOUNT, enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocus).testTag("entryAmount"),
                    supportingText = { if (state.validation == ValidationIssue.AMOUNT) Text(stringResource(R.string.amount_invalid)) })
                Text(stringResource(R.string.pay_source_title), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.sourceType == "CARD", onClick = { model.source("CARD") }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.bank_card)) }, modifier = Modifier.testTag("sourceCard"))
                    FilterChip(selected = state.sourceType == "CASH", onClick = { model.source("CASH") }, enabled = !state.busy,
                        label = { Text(stringResource(R.string.bank_cash)) }, modifier = Modifier.testTag("sourceCash"))
                }
                if (state.sourceMissing) Text(stringResource(R.string.pay_source_required), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(value = state.title, onValueChange = model::title, label = { Text(stringResource(R.string.entry_title)) }, singleLine = true,
                    placeholder = { Text(fallbackTitle) }, supportingText = { Text(stringResource(R.string.title_optional_hint)) },
                    isError = state.validation == ValidationIssue.TITLE, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("entryTitle"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { category ->
                        FilterChip(colors = FilterChipDefaults.filterChipColors(selectedContainerColor = LocalAppearance.current.category(category, state.type == EntryType.INCOME).copy(alpha = .18f), selectedLabelColor = LocalAppearance.current.category(category, state.type == EntryType.INCOME)), selected = state.customCategoryId == null && state.category == category, onClick = { model.category(category) }, enabled = !state.busy,
                            label = { Text(stringResource(category.label())) })
                    }
                    customCategories.forEach { category ->
                        FilterChip(selected = state.customCategoryId == category.id, onClick = { model.customCategory(category.id) },
                            label = { Text(category.name) })
                    }
                }
                TextButton(onClick = { dateDialog = true }, enabled = !state.busy) {
                    Icon(Icons.Default.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(formatDate(state.date, language))
                }
                if (state.validation == ValidationIssue.DATE) Text(stringResource(R.string.date_invalid), color = MaterialTheme.colorScheme.error)
                state.error?.let { Text(stringResource(it.label()), color = MaterialTheme.colorScheme.error) }
                Button(onClick = { model.save(fallbackTitle) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("saveEntry")) {
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.save_entry))
                }
            }
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

## app/src/main/kotlin/com/semmyx/ledger/editor/EntryViewModel.kt

```kotlin
package com.semmyx.ledger.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.UiMessage
import com.semmyx.ledger.common.guarded
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
    /** "CASH" or "CARD" once chosen; new entries cannot be saved until the user picks one. */
    val sourceType: String = "UNKNOWN",
    val sourceMissing: Boolean = false,
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
    fun source(value: String) {
        require(value in listOf("UNKNOWN", "CASH", "CARD"))
        mutableState.update { it.copy(sourceType = value, sourceMissing = false, cardLast4 = if (value == "CARD") it.cardLast4 else null, bankId = if (value == "CARD") it.bankId else null) }
    }
    fun bank(id: String) { mutableState.update { it.copy(bankId = id, sourceType = "CARD") } }
    fun date(value: LocalDate) { mutableState.update { it.copy(date = value, eventAtMillis = value.atStartOfDay(clock.zone).toInstant().toEpochMilli(), validation = null) } }
    fun note(value: String) { mutableState.update { it.copy(note = value.take(240), validation = null) } }
    fun save(fallbackTitle: String = "") {
        if (state.value.busy || state.value.loading || state.value.loadFailed) return
        val form = state.value
        val minor = Money.parseMinor(form.amount)
        if (minor == null) { mutableState.update { it.copy(validation = ValidationIssue.AMOUNT) }; return }
        if (!form.editing && form.sourceType != "CASH" && form.sourceType != "CARD") { mutableState.update { it.copy(sourceMissing = true) }; return }
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                saveEntry(LedgerEntry(id, form.title.ifBlank { fallbackTitle.take(60) }, minor, form.currency, form.type, form.category, form.date, form.note, form.sourceType, form.bankId, form.cardLast4,
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

## app/src/main/kotlin/com/semmyx/ledger/history/HistoryScreen.kt

```kotlin
package com.semmyx.ledger.history

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
import com.semmyx.ledger.common.*
import com.semmyx.ledger.theme.LocalAppearance

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

## app/src/main/kotlin/com/semmyx/ledger/history/HistoryViewModel.kt

```kotlin
package com.semmyx.ledger.history

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

## app/src/main/kotlin/com/semmyx/ledger/navigation/AppRoot.kt

```kotlin
package com.semmyx.ledger.navigation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import com.semmyx.ledger.history.*
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
import com.semmyx.ledger.support.SupportNavigator
import com.semmyx.ledger.auth.*
import com.semmyx.ledger.common.*
import com.semmyx.ledger.dashboard.*
import com.semmyx.ledger.analytics.*
import com.semmyx.ledger.bank.*
import com.semmyx.ledger.editor.*
import com.semmyx.ledger.onboarding.OnboardingScreen
import com.semmyx.ledger.settings.*
import com.semmyx.ledger.support.*
import com.semmyx.ledger.theme.*

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
        // Home is the only screen with the bottom bar. Every other tab opens as its own full screen
        // with a close (X) button; closing, or pressing Back, always returns to Home.
        if (index == 0) nav.popBackStack("dashboard", inclusive = false)
        else nav.navigate(routes[index]) {
            popUpTo("dashboard")
            launchSingleTop = true
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
        composable("help") { HelpScreen(onClose = { onNavigate(0) }, onSupport = onSupport) }
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
                BankViewModel(container.bankImport, container.preferences, container.premium, container.clock)
            })
            BankScreen(bankModel, container.bankImport, onBack = { onNavigate(0) }, onAdd = { nav.navigate("entry/new") },
                onEdit = { nav.navigate("entry/$it") }, onPremium = { onNavigate(1) })
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

## app/src/main/kotlin/com/semmyx/ledger/navigation/AppViewModel.kt

```kotlin
package com.semmyx.ledger.navigation

import androidx.lifecycle.ViewModel
import com.semmyx.ledger.domain.PreferencesRepository
import com.semmyx.ledger.domain.SecurityRepository
import com.semmyx.ledger.domain.UserPreferences
import com.semmyx.ledger.common.guarded
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

## app/src/main/kotlin/com/semmyx/ledger/onboarding/OnboardingScreen.kt

```kotlin
package com.semmyx.ledger.onboarding

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
import com.semmyx.ledger.common.LedgerMark
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

## app/src/main/kotlin/com/semmyx/ledger/platform/OwnershipSeal.kt

```kotlin
package com.semmyx.ledger.platform

/**
 * Copyright (c) 2026 Semmyx (semmyx666). All rights reserved. See LICENSE.
 *
 * Ownership seal: SHA-256("TG Ledger|semmyx666|semmyxofficial666@gmail.com|2026"), first 32 hex chars.
 * It is published in docs/OWNERSHIP.md and is present in every build (APK/AAB), so a copy of the app can be
 * traced back to this project even after it is renamed or re-signed.
 */
object OwnershipSeal {
    const val OWNER = "Semmyx (semmyx666)"
    const val ID = "11b3e1bb7fc072422cf2d25d258c65c8"

    /** Keeps the seal referenced so that shrinking/obfuscation can never strip it from release builds. */
    fun register() { System.setProperty("com.semmyx.ledger.seal", "$OWNER|$ID") }
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

## app/src/main/kotlin/com/semmyx/ledger/settings/ColorSettings.kt

```kotlin
package com.semmyx.ledger.settings

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
import com.semmyx.ledger.common.*
import com.semmyx.ledger.theme.LocalAppearance
import com.semmyx.ledger.theme.readableOn
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

## app/src/main/kotlin/com/semmyx/ledger/settings/RestorePurchasesAction.kt

```kotlin
package com.semmyx.ledger.settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.semmyx.ledger.R

@Composable
fun RestorePurchasesAction(enabled: Boolean, onClick: () -> Unit) {
    // A dedicated full-width line, never the squeezed last child of a button row.
    TextButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag("restorePurchases")) {
        Text(stringResource(R.string.restore_purchases), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}
```

## app/src/main/kotlin/com/semmyx/ledger/settings/SettingsScreen.kt

```kotlin
package com.semmyx.ledger.settings

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
import com.semmyx.ledger.common.*
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
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = {
        IconButton(onClick = { onNavigate(0) }) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
    }, actions = {
        IconButton(onClick = onLock) { Icon(Icons.Default.Lock, stringResource(R.string.lock_now)) }
    }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
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
                    OutlinedButton(onClick = { model.startTrial(TrialPolicy.DAYS) }) { Text(stringResource(R.string.trial_14)) }
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
                RestorePurchasesAction(enabled = !state.premium.busy, onClick = model::restorePurchases)
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

## app/src/main/kotlin/com/semmyx/ledger/settings/SettingsViewModel.kt

```kotlin
package com.semmyx.ledger.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.common.UiMessage
import com.semmyx.ledger.common.guarded
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

## app/src/main/kotlin/com/semmyx/ledger/support/HelpScreen.kt

```kotlin
package com.semmyx.ledger.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.BuildConfig
import com.semmyx.ledger.R
import com.semmyx.ledger.common.ContentCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onClose: () -> Unit, onSupport: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.help)) }, navigationIcon = {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
    }) }) { padding ->
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
                Text(stringResource(R.string.help_banks_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.help_banks_body))
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

## app/src/main/kotlin/com/semmyx/ledger/support/SupportNavigator.kt

```kotlin
package com.semmyx.ledger.support

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

## app/src/main/kotlin/com/semmyx/ledger/support/SupportSheet.kt

```kotlin
package com.semmyx.ledger.support

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
import com.semmyx.ledger.common.ContentCard

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

## app/src/main/kotlin/com/semmyx/ledger/support/SupportWebActivity.kt

```kotlin
package com.semmyx.ledger.support

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
import com.semmyx.ledger.theme.LedgerTheme
import com.semmyx.ledger.theme.LocalizedContent

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

## app/src/main/kotlin/com/semmyx/ledger/theme/AppearancePalette.kt

```kotlin
package com.semmyx.ledger.theme

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

## app/src/main/kotlin/com/semmyx/ledger/theme/Theme.kt

```kotlin
package com.semmyx.ledger.theme

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

// ---------------------------------------------------------------------------
// Premium color palette
// Light: warm white surfaces + deep indigo primary + emerald income
// Dark : pure OLED black + soft periwinkle + vivid emerald — no grey wash
// ---------------------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary            = Color(0xFF3D5AFE),   // Indigo A400 — vibrant but not garish
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFE8EAFF),
    onPrimaryContainer = Color(0xFF001480),

    secondary            = Color(0xFF00875A),   // Emerald — income
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFD7F5E8),
    onSecondaryContainer = Color(0xFF00391F),

    tertiary            = Color(0xFFFF5252),    // Crimson — expense
    onTertiary          = Color.White,
    tertiaryContainer   = Color(0xFFFFDEDE),
    onTertiaryContainer = Color(0xFF7F0000),

    background   = Color(0xFFF5F6FF),           // Slightly blue-tinted white
    onBackground = Color(0xFF0D0E1A),

    surface        = Color(0xFFFFFFFF),
    onSurface      = Color(0xFF0D0E1A),
    surfaceVariant = Color(0xFFEDEEFF),
    onSurfaceVariant = Color(0xFF4A4C6B),

    outline        = Color(0xFF9496B4),
    outlineVariant = Color(0xFFD8D9F0),

    error   = Color(0xFFB00020),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary            = Color(0xFF8EA7FF),   // Soft periwinkle on pure black
    onPrimary          = Color(0xFF001480),
    primaryContainer   = Color(0xFF1A237E),
    onPrimaryContainer = Color(0xFFCDD5FF),

    secondary            = Color(0xFF2ECC71),   // Bright emerald
    onSecondary          = Color(0xFF00391F),
    secondaryContainer   = Color(0xFF00512E),
    onSecondaryContainer = Color(0xFFA7F3C6),

    tertiary            = Color(0xFFFF6E6E),    // Bright red
    onTertiary          = Color(0xFF7F0000),
    tertiaryContainer   = Color(0xFF5C0000),
    onTertiaryContainer = Color(0xFFFFCDD2),

    background   = Color(0xFF000000),           // Pure OLED
    onBackground = Color(0xFFF0F0FF),

    surface        = Color(0xFF111218),         // Very dark blue-grey
    onSurface      = Color(0xFFF0F0FF),
    surfaceVariant = Color(0xFF1C1E30),
    onSurfaceVariant = Color(0xFFB0B3D8),

    outline        = Color(0xFF5C5E80),
    outlineVariant = Color(0xFF2A2C46),

    error   = Color(0xFFFF6680),
    onError = Color(0xFF4A0010),
)

// ---------------------------------------------------------------------------
// Typography — slightly larger line-heights prevent text clipping
// ---------------------------------------------------------------------------

private val LedgerTypography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,     fontSize = 45.sp, lineHeight = 52.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 42.sp),
    headlineMedium= TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 38.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp),
    bodySmall     = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelLarge    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

// ---------------------------------------------------------------------------
// Premium shape scale — softer corners everywhere
// ---------------------------------------------------------------------------

private val LedgerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ---------------------------------------------------------------------------
// Theme entry point
// ---------------------------------------------------------------------------

@Composable
fun LedgerTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    overrides: Map<String, String> = emptyMap(),
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
    }
    val palette = remember(overrides, dark) {
        AppearancePalette(
            overrides,
            if (dark) emptyMap()
            else mapOf(
                "background" to "#F5F6FF",
                "surface"    to "#FFFFFF",
                "text"       to "#0D0E1A",
                "muted"      to "#4A4C6B",
            ),
        )
    }
    val base   = if (dark) DarkColors else LightColors
    val accent = palette.color("accent")
    val scheme = base.copy(
        primary              = accent,
        onPrimary            = readableOn(accent),
        primaryContainer     = accent.copy(alpha = .18f),
        onPrimaryContainer   = accent,
        secondary            = palette.color("income"),
        onSecondary          = readableOn(palette.color("income")),
        secondaryContainer   = palette.color("income").copy(alpha = .18f),
        onSecondaryContainer = palette.color("income"),
        surfaceTint          = accent,
        error                = palette.color("error"),
        onError              = readableOn(palette.color("error")),
        tertiaryContainer    = palette.color("expense").copy(alpha = .18f),
        onTertiaryContainer  = palette.color("expense"),
        tertiary             = palette.color("expense"),
        onTertiary           = readableOn(palette.color("expense")),
        background           = if (dark || overrides.containsKey("background")) palette.color("background") else base.background,
        surface              = if (dark || overrides.containsKey("surface"))    palette.color("surface")    else base.surface,
        onBackground         = if (dark || overrides.containsKey("text"))       palette.color("text")       else base.onBackground,
        onSurface            = if (dark || overrides.containsKey("text"))       palette.color("text")       else base.onSurface,
        surfaceVariant       = if (dark || overrides.containsKey("surface"))    palette.color("surface")    else base.surfaceVariant,
        onSurfaceVariant     = if (dark || overrides.containsKey("muted"))      palette.color("muted")      else base.onSurfaceVariant,
        outline              = palette.color("grid"),
        outlineVariant       = palette.color("grid"),
    )
    CompositionLocalProvider(LocalAppearance provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography  = LedgerTypography,
            shapes      = LedgerShapes,
            content     = content,
        )
    }
}

// ---------------------------------------------------------------------------
// Localization
// ---------------------------------------------------------------------------

/**
 * Compose-scoped configuration avoids Activity.recreate() and a process restart.
 * DataStore is the single source of truth. LocalContext + LocalConfiguration
 * invalidate stringResource, date formatting, dialogs and accessibility labels.
 * Both supported languages are LTR. No global Locale.setDefault mutation.
 */
@Composable
fun LocalizedContent(language: AppLanguage, content: @Composable () -> Unit) {
    val base     = LocalContext.current
    val original = LocalConfiguration.current
    val config   = remember(original, language) {
        Configuration(original).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    }
    val localizedContext = remember(base, config) { base.createConfigurationContext(config) }
    CompositionLocalProvider(
        LocalContext          provides localizedContext,
        LocalConfiguration   provides config,
        LocalLayoutDirection  provides LayoutDirection.Ltr,
        content               = content,
    )
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
        private val ADD_EN = listOf("add entry", "new entry", "add expense", "add income", "new expense", "new income")
        private val ADD_UK = listOf("додати запис", "новий запис", "додати витрату", "додати доход")
    }
}

sealed interface VoiceCommand {
    data object ShowBalance : VoiceCommand
    data object ShowSpentToday : VoiceCommand
    data object OpenAddEntry : VoiceCommand
}
```

## app/src/main/kotlin/com/semmyx/ledger/voice/SpokenMoneyFormatter.kt

```kotlin
package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Explicit words for minus and currency: TTS must never depend on pronunciation of '-' or ₴. */
object SpokenMoneyFormatter {
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

    private val CARD_WORDS = Regex("карт|card|безнал|безготівк", RegexOption.IGNORE_CASE)
    private val CASH_WORDS = Regex("готівк|наличн|налом|cash", RegexOption.IGNORE_CASE)

    /** "CARD" / "CASH" when the sentence already says how it was paid; null when the cat must ask. */
    fun detectSource(heard: String): String? {
        val card = CARD_WORDS.containsMatchIn(heard)
        val cash = CASH_WORDS.containsMatchIn(heard)
        return when { card && !cash -> "CARD"; cash && !card -> "CASH"; else -> null }
    }

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

## app/src/main/kotlin/com/semmyx/ledger/voice/VoiceFinanceQuery.kt

```kotlin
package com.semmyx.ledger.voice

import com.semmyx.ledger.domain.*
import java.time.LocalDate

/** Structured intent resolver. Queries are resolved BEFORE monetary entries, never saved as purchases. */
sealed interface VoiceFinanceQuery {
    data object Balance : VoiceFinanceQuery
    enum class Period { TODAY, WEEK, MONTH, YEAR, ALL }
    data class Statistics(val period: Period, val type: EntryType, val category: Category?) : VoiceFinanceQuery {
        fun from(today: LocalDate): LocalDate = when (period) {
            Period.TODAY -> today
            Period.WEEK -> today.minusDays(6)
            Period.MONTH -> today.withDayOfMonth(1)
            Period.YEAR -> today.withDayOfYear(1)
            Period.ALL -> FinanceWindow.EARLIEST
        }
    }
    companion object {
        fun parse(heard: String): VoiceFinanceQuery? {
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

## app/src/main/res/drawable/ic_launcher_background.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#3DDC84"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#00000000"
        android:pathData="M9,0L9,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,0L19,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,0L29,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,0L39,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,0L49,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,0L59,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,0L69,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,0L79,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M89,0L89,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M99,0L99,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,9L108,9"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,19L108,19"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,29L108,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,39L108,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,49L108,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,59L108,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,69L108,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,79L108,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,89L108,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,99L108,99"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,29L89,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,39L89,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,49L89,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,59L89,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,69L89,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,79L89,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,19L29,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,19L39,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,19L49,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,19L59,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,19L69,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,19L79,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
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
    <string name="close">Закрити</string>
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
    <string name="title_optional_hint">Необов’язково — якщо порожньо, буде назва категорії</string>
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
    <string name="premium_feature_trends">Розширені графіки та статистика банків: 7 днів, 30 днів і весь час</string>
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
    <string name="trial_explanation">Один безкоштовний пробний період — 14 днів. Без платіжних даних, списань і автоматичного продовження. Після завершення основний облік лишається безкоштовним.</string>
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
    <string name="bank_consent">Push-сповіщення банків розпізнаються на телефоні. Один раз надайте доступ до сповіщень. Однозначні платежі зберігаються автоматично; сумнівні — у черзі перевірки. SMS — окремий необов’язковий спосіб.</string>
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
    <string name="bank_month">Останні 30 днів</string>
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
    <string name="bank_budget">Ліміт витрат за 30 днів</string>
    <string name="bank_budget_progress">Витрати за 30 днів: %1$s / %2$s</string>
    <string name="bank_export">Експортувати всі операції у CSV</string>
    <string name="bank_clear_history">Очистити історію розпізнавання</string>
    <string name="bank_clear_warning">Чергу підтвердження та відбитки дублікатів буде видалено. Збережені операції залишаться. Після очищення старі сповіщення можуть бути розпізнані знову.</string>
    <string name="bank_app_permissions">Дозволи застосунку Android</string>
    <string name="bank_battery">Дозволити роботу у фоні (батарея)</string>
    <string name="bank_budget_disable">Вимкнути ліміт витрат</string>
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
    <string name="import_waiting">Очікуємо push банку. Перепідключення читає сповіщення, що залишилися у шторці.</string>
    <string name="import_mode_title">Спосіб імпорту</string>
    <string name="import_mode_api">API банку</string>
    <string name="import_mode_sms">SMS</string>
    <string name="import_mode_notify">Push банку (рекомендовано)</string>
    <string name="api_howto">1) Відкрийте api.monobank.ua і увійдіть через застосунок monobank. 2) Скопіюйте особистий токен. 3) Вставте його сюди. Застосунок лише читає виписку (read-only) і перевіряє нові платежі приблизно кожні 15 хвилин, навіть коли закритий. Токен зберігається на телефоні в зашифованому вигляді.</string>
    <string name="api_open_site">Відкрити api.monobank.ua</string>
    <string name="api_token_label">API-токен monobank</string>
    <string name="api_connect">Підключити</string>
    <string name="api_connecting">Підключення…</string>
    <string name="api_connected">Підключено · рахунків: %1$d</string>
    <string name="api_last_sync">Остання синхронізація: %1$s</string>
    <string name="api_imported_last">нових: %1$d</string>
    <string name="api_syncing">Синхронізація…</string>
    <string name="api_sync_now">Синхронізувати зараз</string>
    <string name="api_disconnect">Відключити</string>
    <string name="api_error_bad_token">monobank не прийняв токен. Скопіюйте його ще раз з api.monobank.ua.</string>
    <string name="api_error_rate">monobank дозволяє один запит на хвилину. Зачекайте хвилину й спробуйте знову.</string>
    <string name="api_error_network">Не вдалося зʼєднатися з monobank. Перевірте інтернет.</string>
    <string name="api_error_no_accounts">За цим токеном рахунків не знайдено.</string>
    <string name="sms_howto">Застосунок читає SMS від банків (PRIVATBANK, monobank, SENSE, Ощад, ПУМБ…) одразу після надходження й сам додає платежі. Інші SMS ігноруються й не зберігаються.</string>
    <string name="sms_scan">Сканувати SMS за 30 днів</string>
    <string name="sms_scanning">Сканування…</string>
    <string name="sms_scan_result">Банківських SMS знайдено: %1$d · додано: %2$d · потребують підтвердження: %3$d</string>
    <string name="sms_restricted_tip">Якщо Android блокує доступ до SMS: Налаштування → Застосунки → TG Ledger → ⋮ → Дозволити обмежені налаштування, потім знову дозвольте SMS.</string>
    <string name="pay_source_title">Чим оплачено</string>
    <string name="pay_source_required">Оберіть: картка чи готівка</string>
    <string name="voice_ask_source">Картка чи готівка?</string>
    <string name="cat_locked_hint">Кіт-помічник входить у Premium. Торкніться, щоб розблокувати (14 днів безкоштовно).</string>
    <string name="bank_setup">Налаштування імпорту</string>
    <string name="push_howto">Увімкніть доступ до сповіщень, автоімпорт і роботу у фоні. Новий push банку читається без відкритого екрана. Повторне підключення також читає сповіщення, що ще є у шторці, навіть отримані кілька хвилин тому. Android не надає цьому застосунку історію вже прибраних сповіщень. Після «Примусово зупинити» відкрийте TG Ledger знову. На деяких телефонах спочатку потрібен дозвіл «Обмежені налаштування».</string>
    <string name="privat_api_title">ПриватБанк · Бізнес / ФОП</string>
    <string name="privat_api_howto">Для рахунків Приват24 для бізнесу / ФОП, не для звичайних особистих карток Приват24. Інтеграція (Автоклієнт) → API: створіть токен з доступом лише до балансів і транзакцій. Застосунок тільки читає виписку. Токен шифрується на телефоні. Перша синхронізація — за 30 днів, наступні — приблизно кожні 15 хвилин.</string>
    <string name="privat_api_site">Інструкція API ПриватБанку</string>
    <string name="privat_api_token">Токен Автоклієнта</string>
    <string name="privat_api_connected">Приват24 для бізнесу підключено</string>
    <string name="privat_api_error">Не вдалося прочитати виписку. Перевірте інтернет, токен Автоклієнта та дозвіл читання транзакцій. Токен має бути з Приват24 для бізнесу.</string>
    <string name="other_api_note">Інші банки: універсального API-ключа немає. Open Banking потребує підтримуваної банком згоди та авторизованого постачальника. Для особистого Приват24 та інших банків оберіть push. Якщо застосунку немає у списку, додайте package у «Застосунки банків» → «Інший банк». Не вводьте тут пароль банку, PIN чи CVV.</string>
    <string name="help_banks_title">Банки та автоматичний імпорт</string>
    <string name="help_banks_body">Банки показують витрати за картками, готівкою та банками. Безкоштовно — сьогодні. TG Plus або активний пробний період — 7 днів, 30 днів і весь час. Період змінює статистику, а не збирання операцій.\n\nРекомендовано: Push банку → доступ до сповіщень → увімкнений автоімпорт. Розпізнавання локальне, без SMS і API-токена. Однозначні платежі записуються до журналу, коли екран застосунку закритий. Підтримуються звичайні, розгорнуті сповіщення, списки та повідомлення. OTP, відхилені платежі та лише баланс не додаються. Сумнівні суми, перекази та можливі дублікати потребують перевірки.\n\nПерепідключення / відкриття застосунку читає сповіщення банків, що ще залишилися у шторці. Прибрані повідомлення недоступні. «Примусово зупинити» вимикає фонову роботу до нового відкриття застосунку. Обмеження батареї можуть зупиняти або затримувати імпорт.\n\nAPI: особистий токен monobank та токен Автоклієнта Приват24 для бізнесу. Автоклієнт — для бізнесу / ФОП, не для звичайних особистих карток. API-синхронізація періодична, не миттєва. Інші банки потребують окремої авторизованої інтеграції Open Banking; за наявності використовуйте push. Відсутній застосунок можна додати як «Інший банк».\n\nДодаткові підтвердження потребують дозволу Android на сповіщення; він не впливає на збереження операцій. Імпорт не є підтвердженою банком випискою: звіряйте журнал із банком і перевіряйте чергу.</string>
    <string name="import_confirm_permission">Дозволити сповіщення про збережені платежі</string>
    <string name="import_confirm_enabled">Сповіщення про збережені платежі ввімкнено</string>
    <string name="assistant_write_short">Написати</string>
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

## app/src/main/res/values/colors.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
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
    <string name="close">Close</string>
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
    <string name="title_optional_hint">Optional — the category name is used if empty</string>
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
    <string name="premium_feature_trends">Extended trends and bank statistics: 7 days, 30 days and all time</string>
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
    <string name="trial_explanation">One free 14-day trial. No payment details, charges, or automatic renewal. After it ends, core tracking remains free.</string>
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
    <string name="bank_consent">Bank push notifications are parsed on this phone. Enable notification access once. Unambiguous payments are saved automatically; uncertain messages wait for review. SMS is optional, not required for push.</string>
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
    <string name="bank_month">Last 30 days</string>
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
    <string name="bank_budget">30-day spending limit</string>
    <string name="bank_budget_progress">Spending over 30 days: %1$s / %2$s</string>
    <string name="bank_export">Export all transactions to CSV</string>
    <string name="bank_clear_history">Clear recognition history</string>
    <string name="bank_clear_warning">Pending reviews and duplicate fingerprints will be deleted. Saved transactions are kept. Old notifications could be recognized again after clearing.</string>
    <string name="bank_app_permissions">Android app permissions</string>
    <string name="bank_battery">Allow background work (battery)</string>
    <string name="bank_budget_disable">Disable spending limit</string>
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
    <string name="import_waiting">Waiting for bank push. Reconnect scans notifications still in the shade.</string>
    <string name="import_mode_title">Import method</string>
    <string name="import_mode_api">Bank API</string>
    <string name="import_mode_sms">SMS</string>
    <string name="import_mode_notify">Bank push (recommended)</string>
    <string name="api_howto">1) Open api.monobank.ua and sign in with the monobank app. 2) Copy your personal token. 3) Paste it here. The app only reads your statement (read-only) and checks for new payments every ~15 minutes, even when closed. The token is stored encrypted on this phone.</string>
    <string name="api_open_site">Open api.monobank.ua</string>
    <string name="api_token_label">monobank API token</string>
    <string name="api_connect">Connect</string>
    <string name="api_connecting">Connecting…</string>
    <string name="api_connected">Connected · accounts: %1$d</string>
    <string name="api_last_sync">Last sync: %1$s</string>
    <string name="api_imported_last">new: %1$d</string>
    <string name="api_syncing">Syncing…</string>
    <string name="api_sync_now">Sync now</string>
    <string name="api_disconnect">Disconnect</string>
    <string name="api_error_bad_token">monobank rejected the token. Copy it again from api.monobank.ua.</string>
    <string name="api_error_rate">monobank allows one request per minute. Wait a minute and try again.</string>
    <string name="api_error_network">Could not reach monobank. Check the internet connection.</string>
    <string name="api_error_no_accounts">No accounts found for this token.</string>
    <string name="sms_howto">The app reads SMS from banks (PRIVATBANK, monobank, SENSE, Oschad, PUMB…) as they arrive and imports payments automatically. Other SMS are ignored and never stored.</string>
    <string name="sms_scan">Scan SMS from the last 30 days</string>
    <string name="sms_scanning">Scanning…</string>
    <string name="sms_scan_result">Bank SMS found: %1$d · added: %2$d · need confirmation: %3$d</string>
    <string name="sms_restricted_tip">If Android blocks SMS access: Settings → Apps → TG Ledger → ⋮ → Allow restricted settings, then allow SMS again.</string>
    <string name="pay_source_title">Paid with</string>
    <string name="pay_source_required">Choose card or cash</string>
    <string name="voice_ask_source">Card or cash?</string>
    <string name="cat_locked_hint">The cat assistant is part of Premium. Tap to unlock (14-day trial is free).</string>
    <string name="bank_setup">Import settings</string>
    <string name="push_howto">Enable access to notifications, turn on auto-import and allow background activity. New bank push is read without an open screen. Reconnect also reads bank notifications still visible in the shade, including recent ones. Android does not expose dismissed notification history to this app. After Force stop, open TG Ledger again. Some phones require Allow restricted settings before notification access.</string>
    <string name="privat_api_title">PrivatBank · Business / sole proprietor</string>
    <string name="privat_api_howto">For Privat24 Business AutoClient accounts, not ordinary retail Privat24 cards. In Privat24 Business → Integration (AutoClient) → API, create a token restricted to balances and transactions. Only statements are requested. The token is encrypted on this phone. First sync imports 30 days; later syncs run approximately every 15 minutes.</string>
    <string name="privat_api_site">PrivatBank API instructions</string>
    <string name="privat_api_token">AutoClient token</string>
    <string name="privat_api_connected">Privat24 Business connected</string>
    <string name="privat_api_error">Could not read the statement. Check internet, AutoClient token and its read permissions. This token must belong to Privat24 Business.</string>
    <string name="other_api_note">Other banks: there is no universal API key. Open Banking needs a bank-supported consent flow and an authorised provider. For retail Privat24 and other banks, use push. Add the bank app package under App mapping → Other bank if it is missing. Never enter your bank password, PIN or CVV here.</string>
    <string name="help_banks_title">Banks and automatic import</string>
    <string name="help_banks_body">Banks shows card, cash and bank spending. Free access: today. TG Plus or an active trial: 7 days, 30 days and all time. A period changes statistics, not whether operations are captured.\n\nRecommended: Bank push → grant notification access → keep auto-import enabled. Recognition runs locally, without SMS or an API token, and writes clear payments into the ledger while its screen is closed. It supports plain, expanded, inbox and messaging notifications. OTPs, declines and balance-only messages are not operations. Ambiguous messages, transfers and possible duplicates need review.\n\nReconnect / opening the app reads bank notifications still in the notification shade. Dismissed messages cannot be recovered through notification access. Force-stop disables background work until you open the app again. Battery restrictions can delay or stop it.\n\nAPI: monobank personal token and Privat24 Business AutoClient token are supported. PrivatBank AutoClient is for business/sole proprietor accounts, not ordinary retail cards. API sync is periodic, not instant. Other banks need a bank-specific authorised Open Banking integration; use push where available. A missing bank app can be mapped under Other bank.\n\nOptional confirmations need Android notification permission. They do not affect saving. Import is not a certified bank statement: reconcile against your bank and check the review queue.</string>
    <string name="import_confirm_permission">Allow saved-payment notifications</string>
    <string name="import_confirm_enabled">Saved-payment notifications enabled</string>
    <string name="assistant_write_short">Write</string>
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

## app/src/main/res/xml/backup_rules.xml

```xml
<?xml version="1.0" encoding="utf-8"?><!--
   Sample backup rules file; uncomment and customize as necessary.
   See https://developer.android.com/guide/topics/data/autobackup
   for details.
   Note: This file is ignored for devices older than API 31
   See https://developer.android.com/about/versions/12/backup-restore
-->
<full-backup-content>
    <!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>
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

## app/src/test/java/com/example/myapplication/ExampleUnitTest.kt

```kotlin
package com.example.myapplication

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
```

## app/src/test/kotlin/com/semmyx/ledger/BankViewModelAccessTest.kt

```kotlin
package com.semmyx.ledger
import androidx.lifecycle.ViewModelStore
import com.semmyx.ledger.bank.BankViewModel
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.lang.reflect.Proxy
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BankViewModelAccessTest {
    @get:Rule val main = MainDispatcherRule()
    @Test fun freeQueriesOnlyTodayAndExpiredPremiumCannotKeepThirtyDays() = runTest {
        val now = LocalDate.of(2026,10,14)
        var from: LocalDate? = null
        val repo = Proxy.newProxyInstance(BankRepository::class.java.classLoader,arrayOf(BankRepository::class.java)) { _, method, args ->
            when (method.name) {
                "getDiagnostics" -> MutableStateFlow(ImportDiagnostics())
                "getMonobankStatus", "getPrivatStatus" -> MutableStateFlow(MonobankStatus())
                "getEnabled", "getAutoImport" -> true
                "getImportMode" -> "NOTIFY"
                "sources" -> { from = args!![1] as LocalDate; flowOf(emptyList<BankSourceSpend>()) }
                "categorySpending" -> flowOf(emptyList<BankCategorySpend>())
                "recent" -> flowOf(emptyList<LedgerEntry>())
                "categories" -> flowOf(emptyList<CustomBankCategory>())
                "reviews" -> flowOf(emptyList<ImportReview>())
                else -> error("Unexpected method ${method.name}")
            }
        } as BankRepository
        val preferences = Proxy.newProxyInstance(PreferencesRepository::class.java.classLoader,arrayOf(PreferencesRepository::class.java)) { _, _, _ -> MutableStateFlow(UserPreferences()) } as PreferencesRepository
        val access = MutableStateFlow(PremiumState())
        val premium = object : PremiumRepository {
            override val state = access
            override suspend fun refresh() {}
            override suspend fun activateDeveloperToken(token: String) = false
            override suspend fun developerInstallationId() = "test"
            override fun clearIssue() {}
        }
        val model = BankViewModel(repo,preferences,premium,Clock.fixed(now.atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC))
        val store = ViewModelStore().apply { put("bank",model) }
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        try {
            model.period("MONTH"); runCurrent()
            assertEquals("DAY",model.state.value.filter.period); assertEquals(now,from)
            access.value = PremiumState(entitled=true, source=PremiumSource.TRIAL); runCurrent()
            assertEquals(now.minusDays(29),from); assertEquals("MONTH",model.state.value.filter.period)
            access.value = PremiumState(entitled=false); runCurrent()
            assertEquals("DAY",model.state.value.filter.period); assertEquals(now,from)
            model.period("ALL"); runCurrent(); assertEquals(now,from)
        } finally { collector.cancel(); store.clear() }
    }
}
```

## app/src/test/kotlin/com/semmyx/ledger/MainDispatcherRule.kt

```kotlin
package com.semmyx.ledger

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

## app/src/test/kotlin/com/semmyx/ledger/ViewModelTest.kt

```kotlin
package com.semmyx.ledger

import androidx.lifecycle.ViewModelStore
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.auth.AuthViewModel
import com.semmyx.ledger.navigation.AppViewModel
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

## app/src/test/kotlin/com/semmyx/ledger/cat/CatFaceMeshTest.kt

```kotlin
package com.semmyx.ledger.cat

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
        val query = VoiceFinanceQuery.parse("Скай витрати за місяць") as VoiceFinanceQuery.Statistics
        assertEquals(EntryType.EXPENSE, query.type)
        assertEquals(LocalDate.of(2026, 10, 1), query.from(today))
        assertNull(query.category)
    }
    @Test fun monthlyFoodQuery() {
        val query = VoiceFinanceQuery.parse("Скай витрати за місяць за продукти") as VoiceFinanceQuery.Statistics
        assertEquals(Category.FOOD, query.category)
        assertEquals(VoiceFinanceQuery.Period.MONTH, query.period)
    }
    @Test fun russianAndEnglishQueries() {
        assertEquals(Category.TRANSPORT, (VoiceFinanceQuery.parse("расходы за месяц на транспорт") as VoiceFinanceQuery.Statistics).category)
        assertEquals(Category.FOOD, (VoiceFinanceQuery.parse("expenses this month for food") as VoiceFinanceQuery.Statistics).category)
    }
    @Test fun periodsAndIncome() {
        assertEquals(today.minusDays(6), (VoiceFinanceQuery.parse("витрати за тиждень") as VoiceFinanceQuery.Statistics).from(today))
        assertEquals(FinanceWindow.EARLIEST, (VoiceFinanceQuery.parse("витрати за весь час") as VoiceFinanceQuery.Statistics).from(today))
        assertEquals(EntryType.INCOME, (VoiceFinanceQuery.parse("дохід за місяць") as VoiceFinanceQuery.Statistics).type)
        assertEquals(today.withDayOfYear(1), (VoiceFinanceQuery.parse("витрати за рік") as VoiceFinanceQuery.Statistics).from(today))
    }
    @Test fun aPurchaseIsNotAStatisticsQuestion() {
        assertNull(VoiceFinanceQuery.parse("я витратив 100 грн сьогодні"))
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
        assertEquals("мінус 2000 гривень", SpokenMoneyFormatter.format(-200000, LedgerCurrency.UAH, AppLanguage.UKRAINIAN))
        assertEquals("minus 2000 hryvnias", SpokenMoneyFormatter.format(-200000, LedgerCurrency.UAH, AppLanguage.ENGLISH))
        assertFalse(SpokenMoneyFormatter.format(200000, LedgerCurrency.UAH, AppLanguage.UKRAINIAN).contains("мінус"))
        assertTrue(SpokenMoneyFormatter.format(Long.MIN_VALUE, LedgerCurrency.UAH, AppLanguage.ENGLISH).startsWith("minus "))
    }
    @Test fun zeroIsNotDebt() {
        assertEquals("0 гривень", SpokenMoneyFormatter.format(0, LedgerCurrency.UAH, AppLanguage.UKRAINIAN))
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

## app/src/test/kotlin/com/semmyx/ledger/voice/VoiceSourceTest.kt

```kotlin
package com.semmyx.ledger.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceSourceTest {
    @Test fun sourceIsDetectedOnlyWhenSpoken() {
        assertEquals("CARD", VoiceEntryParser.detectSource("купив каву за 50 карткою"))
        assertEquals("CASH", VoiceEntryParser.detectSource("bought coffee for 5 cash"))
        assertEquals("CASH", VoiceEntryParser.detectSource("витратив 100 готівкою"))
        assertNull(VoiceEntryParser.detectSource("купив каву за 50"))
        assertNull(VoiceEntryParser.detectSource("карткою або готівкою 50"))
    }
}
```

## bank-fixture/build.gradle.kts

```kotlin
plugins { id("com.android.application") }
android {
    namespace = "com.semmyx.ledger.bankfixture"
    compileSdk = 36
    defaultConfig { applicationId = "com.semmyx.ledger.bankfixture"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1" }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
```

## bank-fixture/src/main/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application android:label="QA Bank (test only)" android:allowBackup="false" android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <receiver android:name=".FixtureReceiver" android:exported="true" />
    </application>
</manifest>
```

## bank-fixture/src/main/java/com/semmyx/ledger/bankfixture/FixtureReceiver.java

```java
package com.semmyx.ledger.bankfixture;
import android.app.*;
import android.content.*;

/** CI-only second app. Never bundled into TG Ledger or included in its release APK. */
public class FixtureReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (intent.getBooleanExtra("cancel", false)) { nm.cancelAll(); return; }
        nm.createNotificationChannel(new NotificationChannel("qa_bank", "QA Bank payments", NotificationManager.IMPORTANCE_DEFAULT));
        int id = intent.getIntExtra("id", 1);
        int cents = intent.getIntExtra("cents", 12550);
        long at = intent.getLongExtra("at", System.currentTimeMillis());
        String text = "Ви витратили " + (cents / 100) + "," + String.format(java.util.Locale.ROOT, "%02d", cents % 100) + " грн у Сільпо; Залишок: 10000 грн";
        Notification.Builder b = new Notification.Builder(context, "qa_bank")
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Сільпо")
            .setContentText(text).setWhen(at).setShowWhen(true);
        String style = intent.getStringExtra("style");
        if ("big".equals(style)) b.setStyle(new Notification.BigTextStyle().bigText(text));
        if ("inbox".equals(style)) b.setStyle(new Notification.InboxStyle().addLine(text)
            .addLine("Оплата 11,22 грн у АТБ; Баланс: 9000 грн"));
        if ("messaging".equals(style)) b.setStyle(new Notification.MessagingStyle("Bank")
            .addMessage(text, at, "Сільпо"));
        nm.notify("qa_" + id, id, b.build());
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

## data/schemas/com.semmyx.ledger.data.local.LedgerDatabase/2.json

```json
{
  "formatVersion": 1,
  "database": {
    "version": 2,
    "identityHash": "26836e1c65598d48f9b1e51268027d2f",
    "entities": [
      {
        "tableName": "entries",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, `searchText` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `deletedAtMillis` INTEGER, `sourceType` TEXT NOT NULL DEFAULT 'UNKNOWN', `bankId` TEXT, `cardLast4` TEXT, `eventAtMillis` INTEGER, `customCategoryId` TEXT, `origin` TEXT NOT NULL DEFAULT 'MANUAL', PRIMARY KEY(`id`))",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "title",
            "columnName": "title",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "amountMinor",
            "columnName": "amountMinor",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "currency",
            "columnName": "currency",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "type",
            "columnName": "type",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "category",
            "columnName": "category",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "epochDay",
            "columnName": "epochDay",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "note",
            "columnName": "note",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "searchText",
            "columnName": "searchText",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "createdAtMillis",
            "columnName": "createdAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "deletedAtMillis",
            "columnName": "deletedAtMillis",
            "affinity": "INTEGER"
          },
          {
            "fieldPath": "sourceType",
            "columnName": "sourceType",
            "affinity": "TEXT",
            "notNull": true,
            "defaultValue": "'UNKNOWN'"
          },
          {
            "fieldPath": "bankId",
            "columnName": "bankId",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "cardLast4",
            "columnName": "cardLast4",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "eventAtMillis",
            "columnName": "eventAtMillis",
            "affinity": "INTEGER"
          },
          {
            "fieldPath": "customCategoryId",
            "columnName": "customCategoryId",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "origin",
            "columnName": "origin",
            "affinity": "TEXT",
            "notNull": true,
            "defaultValue": "'MANUAL'"
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "id"
          ]
        },
        "indices": [
          {
            "name": "index_entries_currency_deletedAtMillis_epochDay_createdAtMillis",
            "unique": false,
            "columnNames": [
              "currency",
              "deletedAtMillis",
              "epochDay",
              "createdAtMillis"
            ],
            "orders": [],
            "createSql": "CREATE INDEX IF NOT EXISTS `index_entries_currency_deletedAtMillis_epochDay_createdAtMillis` ON `${TABLE_NAME}` (`currency`, `deletedAtMillis`, `epochDay`, `createdAtMillis`)"
          }
        ]
      },
      {
        "tableName": "rates",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`currency` TEXT NOT NULL, `uahPerUnit` REAL NOT NULL, `epochDay` INTEGER NOT NULL, `fetchedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`currency`))",
        "fields": [
          {
            "fieldPath": "currency",
            "columnName": "currency",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "uahPerUnit",
            "columnName": "uahPerUnit",
            "affinity": "REAL",
            "notNull": true
          },
          {
            "fieldPath": "epochDay",
            "columnName": "epochDay",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "fetchedAtMillis",
            "columnName": "fetchedAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "currency"
          ]
        }
      },
      {
        "tableName": "custom_categories",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "name",
            "columnName": "name",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "colorHex",
            "columnName": "colorHex",
            "affinity": "TEXT",
            "notNull": true
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "id"
          ]
        }
      },
      {
        "tableName": "bank_inbox",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, `eventKey` TEXT NOT NULL, `bankId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `type` TEXT NOT NULL, `merchant` TEXT NOT NULL, `cardLast4` TEXT, `eventAtMillis` INTEGER NOT NULL, `receivedAtMillis` INTEGER NOT NULL, `timeFromMessage` INTEGER NOT NULL, `transfer` INTEGER NOT NULL, `category` TEXT NOT NULL, `origin` TEXT NOT NULL, `status` TEXT NOT NULL, `entryId` TEXT, PRIMARY KEY(`id`))",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "eventKey",
            "columnName": "eventKey",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "bankId",
            "columnName": "bankId",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "amountMinor",
            "columnName": "amountMinor",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "currency",
            "columnName": "currency",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "type",
            "columnName": "type",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "merchant",
            "columnName": "merchant",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "cardLast4",
            "columnName": "cardLast4",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "eventAtMillis",
            "columnName": "eventAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "receivedAtMillis",
            "columnName": "receivedAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "timeFromMessage",
            "columnName": "timeFromMessage",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "transfer",
            "columnName": "transfer",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "category",
            "columnName": "category",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "origin",
            "columnName": "origin",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "status",
            "columnName": "status",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "entryId",
            "columnName": "entryId",
            "affinity": "TEXT"
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "id"
          ]
        },
        "indices": [
          {
            "name": "index_bank_inbox_eventKey",
            "unique": true,
            "columnNames": [
              "eventKey"
            ],
            "orders": [],
            "createSql": "CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_inbox_eventKey` ON `${TABLE_NAME}` (`eventKey`)"
          }
        ]
      }
    ],
    "setupQueries": [
      "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
      "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '26836e1c65598d48f9b1e51268027d2f')"
    ]
  }
}
```

## data/schemas/com.semmyx.ledger.data.local.LedgerDatabase/3.json

```json
{
  "formatVersion": 1,
  "database": {
    "version": 3,
    "identityHash": "d2c7b95ecb8ce12dbbd2f6fb3e5066fd",
    "entities": [
      {
        "tableName": "entries",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `type` TEXT NOT NULL, `category` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, `searchText` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `deletedAtMillis` INTEGER, `sourceType` TEXT NOT NULL DEFAULT 'UNKNOWN', `bankId` TEXT, `cardLast4` TEXT, `eventAtMillis` INTEGER, `customCategoryId` TEXT, `origin` TEXT NOT NULL DEFAULT 'MANUAL', PRIMARY KEY(`id`))",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "title",
            "columnName": "title",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "amountMinor",
            "columnName": "amountMinor",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "currency",
            "columnName": "currency",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "type",
            "columnName": "type",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "category",
            "columnName": "category",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "epochDay",
            "columnName": "epochDay",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "note",
            "columnName": "note",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "searchText",
            "columnName": "searchText",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "createdAtMillis",
            "columnName": "createdAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "deletedAtMillis",
            "columnName": "deletedAtMillis",
            "affinity": "INTEGER"
          },
          {
            "fieldPath": "sourceType",
            "columnName": "sourceType",
            "affinity": "TEXT",
            "notNull": true,
            "defaultValue": "'UNKNOWN'"
          },
          {
            "fieldPath": "bankId",
            "columnName": "bankId",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "cardLast4",
            "columnName": "cardLast4",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "eventAtMillis",
            "columnName": "eventAtMillis",
            "affinity": "INTEGER"
          },
          {
            "fieldPath": "customCategoryId",
            "columnName": "customCategoryId",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "origin",
            "columnName": "origin",
            "affinity": "TEXT",
            "notNull": true,
            "defaultValue": "'MANUAL'"
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "id"
          ]
        },
        "indices": [
          {
            "name": "index_entries_currency_deletedAtMillis_epochDay_createdAtMillis",
            "unique": false,
            "columnNames": [
              "currency",
              "deletedAtMillis",
              "epochDay",
              "createdAtMillis"
            ],
            "orders": [],
            "createSql": "CREATE INDEX IF NOT EXISTS `index_entries_currency_deletedAtMillis_epochDay_createdAtMillis` ON `${TABLE_NAME}` (`currency`, `deletedAtMillis`, `epochDay`, `createdAtMillis`)"
          }
        ]
      },
      {
        "tableName": "rates",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`currency` TEXT NOT NULL, `uahPerUnit` REAL NOT NULL, `epochDay` INTEGER NOT NULL, `fetchedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`currency`))",
        "fields": [
          {
            "fieldPath": "currency",
            "columnName": "currency",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "uahPerUnit",
            "columnName": "uahPerUnit",
            "affinity": "REAL",
            "notNull": true
          },
          {
            "fieldPath": "epochDay",
            "columnName": "epochDay",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "fetchedAtMillis",
            "columnName": "fetchedAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "currency"
          ]
        }
      },
      {
        "tableName": "custom_categories",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "name",
            "columnName": "name",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "colorHex",
            "columnName": "colorHex",
            "affinity": "TEXT",
            "notNull": true
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "id"
          ]
        }
      },
      {
        "tableName": "bank_inbox",
        "createSql": "CREATE TABLE IF NOT EXISTS `${TABLE_NAME}` (`id` TEXT NOT NULL, `eventKey` TEXT NOT NULL, `bankId` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currency` TEXT NOT NULL, `type` TEXT NOT NULL, `merchant` TEXT NOT NULL, `cardLast4` TEXT, `eventAtMillis` INTEGER NOT NULL, `receivedAtMillis` INTEGER NOT NULL, `timeFromMessage` INTEGER NOT NULL, `transfer` INTEGER NOT NULL, `category` TEXT NOT NULL, `origin` TEXT NOT NULL, `status` TEXT NOT NULL, `entryId` TEXT, `groupKey` TEXT, PRIMARY KEY(`id`))",
        "fields": [
          {
            "fieldPath": "id",
            "columnName": "id",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "eventKey",
            "columnName": "eventKey",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "bankId",
            "columnName": "bankId",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "amountMinor",
            "columnName": "amountMinor",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "currency",
            "columnName": "currency",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "type",
            "columnName": "type",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "merchant",
            "columnName": "merchant",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "cardLast4",
            "columnName": "cardLast4",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "eventAtMillis",
            "columnName": "eventAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "receivedAtMillis",
            "columnName": "receivedAtMillis",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "timeFromMessage",
            "columnName": "timeFromMessage",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "transfer",
            "columnName": "transfer",
            "affinity": "INTEGER",
            "notNull": true
          },
          {
            "fieldPath": "category",
            "columnName": "category",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "origin",
            "columnName": "origin",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "status",
            "columnName": "status",
            "affinity": "TEXT",
            "notNull": true
          },
          {
            "fieldPath": "entryId",
            "columnName": "entryId",
            "affinity": "TEXT"
          },
          {
            "fieldPath": "groupKey",
            "columnName": "groupKey",
            "affinity": "TEXT"
          }
        ],
        "primaryKey": {
          "autoGenerate": false,
          "columnNames": [
            "id"
          ]
        },
        "indices": [
          {
            "name": "index_bank_inbox_eventKey",
            "unique": true,
            "columnNames": [
              "eventKey"
            ],
            "orders": [],
            "createSql": "CREATE UNIQUE INDEX IF NOT EXISTS `index_bank_inbox_eventKey` ON `${TABLE_NAME}` (`eventKey`)"
          },
          {
            "name": "index_bank_inbox_groupKey",
            "unique": false,
            "columnNames": [
              "groupKey"
            ],
            "orders": [],
            "createSql": "CREATE INDEX IF NOT EXISTS `index_bank_inbox_groupKey` ON `${TABLE_NAME}` (`groupKey`)"
          }
        ]
      }
    ],
    "setupQueries": [
      "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
      "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd2c7b95ecb8ce12dbbd2f6fb3e5066fd')"
    ]
  }
}
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

## data/src/main/kotlin/com/semmyx/ledger/data/bank/BankEntities.kt

```kotlin
package com.semmyx.ledger.data.bank

import androidx.room.*
import com.semmyx.ledger.data.local.EntryEntity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "custom_categories")
data class CustomCategoryEntity(@PrimaryKey val id: String, val name: String, val colorHex: String)

/**
 * [groupKey] — SHA-256(amountMinor + currency + type + 5-minute epoch bucket).
 * Two rows with the same groupKey represent the same physical card tap arriving
 * from different source apps (e.g. wallet widget + issuing bank). The second row
 * is flagged CROSS_DUPLICATE_REVIEW instead of REVIEW.
 */
@Entity(
    tableName = "bank_inbox",
    indices = [
        Index(value = ["eventKey"], unique = true),
        Index(value = ["groupKey"]),
    ],
)
data class BankInboxEntity(
    @PrimaryKey val id: String,
    val eventKey: String,
    val bankId: String,
    val amountMinor: Long,
    val currency: String,
    val type: String,
    val merchant: String,
    val cardLast4: String?,
    val eventAtMillis: Long,
    val receivedAtMillis: Long,
    val timeFromMessage: Boolean,
    val transfer: Boolean,
    val category: String,
    val origin: String,
    val status: String,
    val entryId: String? = null,
    /** nullable so existing rows (DB v2) are compatible without migration breakage. */
    val groupKey: String? = null,
)

data class SourceSpendRow(val sourceType: String, val bankId: String?, val totalMinor: Long)
data class DetailedCategoryRow(
    val category: String,
    val customCategoryId: String?,
    val customName: String?,
    val customColor: String?,
    val totalMinor: Long,
)

@Dao
interface BankDao {
    @Query("SELECT * FROM custom_categories ORDER BY name")
    fun categories(): Flow<List<CustomCategoryEntity>>

    @Insert
    suspend fun addCategory(category: CustomCategoryEntity)

    @Query(
        "SELECT * FROM bank_inbox WHERE status IN ('REVIEW','DUPLICATE_REVIEW','CROSS_DUPLICATE_REVIEW')" +
            " ORDER BY receivedAtMillis DESC LIMIT 100",
    )
    fun inbox(): Flow<List<BankInboxEntity>>

    @Query("SELECT * FROM bank_inbox WHERE id=:id")
    suspend fun inboxItem(id: String): BankInboxEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInbox(item: BankInboxEntity): Long

    @Query("UPDATE bank_inbox SET status=:status,entryId=:entryId WHERE id=:id")
    suspend fun status(id: String, status: String, entryId: String?)

    /**
     * Same-bank duplicate: same card OR same merchant, within ±90 s.
     * Guards against push re-delivery from the same issuing bank.
     */
    @Query(
        """SELECT COUNT(*) FROM bank_inbox WHERE bankId=:bank AND amountMinor=:amount
        AND currency=:currency AND type=:type AND ABS(eventAtMillis-:time)<=90000
        AND status IN ('IMPORTED','REVIEW','DUPLICATE_REVIEW','CROSS_DUPLICATE_REVIEW')
        AND ((cardLast4 IS NOT NULL AND cardLast4=:card) OR (merchant<>'' AND LOWER(merchant)=LOWER(:merchant)))""",
    )
    suspend fun similar(
        bank: String, amount: Long, currency: String, type: String,
        time: Long, card: String?, merchant: String,
    ): Int

    /**
     * Cross-source duplicate: same amount + currency + type from **any** bank or wallet app
     * within a ±180 s sliding window.
     *
     * Catches the common double-fire pattern:
     *   1. Google Pay / gаманець fires first.
     *   2. Issuing bank (PrivatBank, mono…) fires 0–120 s later.
     * Without this check both events would be recorded as separate ledger entries.
     */
    @Query(
        """SELECT COUNT(*) FROM bank_inbox WHERE amountMinor=:amount
        AND currency=:currency AND type=:type AND ABS(eventAtMillis-:time)<=180000
        AND status IN ('IMPORTED','REVIEW','DUPLICATE_REVIEW','CROSS_DUPLICATE_REVIEW')""",
    )
    suspend fun similarCrossSource(amount: Long, currency: String, type: String, time: Long): Int

    /** A wallet-app row for the same amount/time exists (wallet + issuing bank announce one tap). */
    @Query(
        """SELECT COUNT(*) FROM bank_inbox WHERE bankId='wallet' AND amountMinor=:amount
        AND currency=:currency AND type=:type AND ABS(eventAtMillis-:time)<=180000
        AND status IN ('IMPORTED','REVIEW','DUPLICATE_REVIEW','CROSS_DUPLICATE_REVIEW')""",
    )
    suspend fun walletTwin(amount: Long, currency: String, type: String, time: Long): Int

    @Query(
        """SELECT sourceType,bankId,COALESCE(SUM(amountMinor),0) AS totalMinor FROM entries
        WHERE deletedAtMillis IS NULL AND type='EXPENSE' AND currency=:currency AND epochDay BETWEEN :from AND :through
        GROUP BY sourceType,bankId ORDER BY totalMinor DESC""",
    )
    fun sources(currency: String, from: Long, through: Long): Flow<List<SourceSpendRow>>

    @Query(
        """SELECT e.category,e.customCategoryId,c.name AS customName,c.colorHex AS customColor,SUM(e.amountMinor) AS totalMinor
        FROM entries e LEFT JOIN custom_categories c ON e.customCategoryId=c.id
        WHERE e.deletedAtMillis IS NULL AND e.type='EXPENSE' AND e.currency=:currency AND e.epochDay BETWEEN :from AND :through
        AND (:source IS NULL OR e.sourceType=:source) AND (:bank IS NULL OR e.bankId=:bank)
        GROUP BY e.category,e.customCategoryId,c.name,c.colorHex ORDER BY totalMinor DESC""",
    )
    fun detailCategories(
        currency: String, from: Long, through: Long, source: String?, bank: String?,
    ): Flow<List<DetailedCategoryRow>>

    @Query(
        """SELECT * FROM entries WHERE deletedAtMillis IS NULL AND currency=:currency
        AND epochDay BETWEEN :from AND :through AND (:source IS NULL OR sourceType=:source) AND (:bank IS NULL OR bankId=:bank)
        ORDER BY COALESCE(eventAtMillis,createdAtMillis) DESC LIMIT 100""",
    )
    fun recent(currency: String, from: Long, through: Long, source: String?, bank: String?): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE deletedAtMillis IS NULL ORDER BY epochDay DESC,createdAtMillis DESC")
    suspend fun exportEntries(): List<EntryEntity>

    @Query("DELETE FROM bank_inbox")
    suspend fun clearInbox()
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/bank/BankImportRepository.kt

```kotlin
package com.semmyx.ledger.data.bank

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.withTransaction
import com.semmyx.ledger.data.local.*
import com.semmyx.ledger.data.repository.toDomain
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.security.MessageDigest
import java.time.*
import java.util.UUID
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Imports and acknowledgements are atomic with the existing ledger.
 * Push parsing is offline. API clients only read statements; no raw text or PAN storage.
 *
 * ## Cross-source deduplication
 *
 * A single physical card payment typically generates **two** push notifications:
 *   1. The wallet/payment app (Google Pay, Apple Pay) fires almost instantly.
 *   2. The issuing bank (PrivatBank, mono…) fires 30–120 s later.
 *
 * Both carry the same amount / currency / type.  Without cross-source dedup the
 * ledger would record UAH 100 twice.
 *
 * Strategy (two-tier, both run inside the same Room transaction):
 *   - Tier 1 [same-bank, ±90 s]: prevents re-delivery of the exact same push.
 *   - Tier 2 [cross-bank, ±180 s]: matches any inbox row with identical
 *     (amount, currency, type) regardless of which bank sent it.
 *     If a cross-source match exists the new row status = CROSS_DUPLICATE_REVIEW
 *     so the user can still inspect and accept/reject it.  If auto-import is on,
 *     cross-duplicates are silently ignored to avoid double-counting.
 */
class BankImportRepository(
    private val db: LedgerDatabase, context: Context,
    private val vault: com.semmyx.ledger.data.security.KeystoreVault? = null,
    private val monobank: MonobankClient = MonobankClient(),
    private val privat: PrivatBankClient = PrivatBankClient(),
) : BankRepository {

    private val ctx = context.applicationContext
    private val config = ctx.getSharedPreferences("bank_import", Context.MODE_PRIVATE)
    private val diagnosticState = MutableStateFlow(ImportDiagnostics())
    override val diagnostics = diagnosticState.asStateFlow()

    fun connected(value: Boolean) { diagnosticState.update { it.copy(connected = value) } }

    fun note(code: String, packageName: String = "", notification: Boolean = false, bankMessage: Boolean = false) {
        diagnosticState.update { old ->
            old.copy(
                lastCode = code,
                lastPackage = packageName.ifBlank { old.lastPackage },
                lastAtMillis = System.currentTimeMillis(),
                notificationCount = old.notificationCount + if (notification) 1 else 0,
                bankMessageCount = old.bankMessageCount + if (bankMessage) 1 else 0,
            )
        }
    }

    override fun configuredPackage(bankId: String): String =
        config.getString("package_$bankId", null)
            ?: Banks.all.firstOrNull { it.id == bankId }?.packages?.firstOrNull().orEmpty()

    override fun setPackage(bankId: String, packageName: String) {
        require(Banks.all.any { it.id == bankId })
        require(packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")))
        config.edit().putString("package_$bankId", packageName.trim()).apply()
    }

    fun bankForPackage(packageName: String): Bank? =
        Banks.all.firstOrNull { config.getString("package_${it.id}", null) == packageName }
            ?: Banks.byPackage(packageName)

    override suspend fun importText(bankId: String, text: String): Boolean {
        val bank = Banks.all.firstOrNull { it.id == bankId } ?: return false
        if (BankParser.parse(bank, text, System.currentTimeMillis()) == null) { note("UNRECOGNIZED"); return false }
        receive(bank, text, "MANUAL_TEXT:$bankId:${UUID.randomUUID()}", System.currentTimeMillis(), "MANUAL_TEXT", manual = true)
        return true
    }

    private val dao = db.bankDao()

    override fun categories() = dao.categories().map { rows -> rows.map { CustomBankCategory(it.id, it.name, it.colorHex) } }

    override fun reviews() = dao.inbox().map { rows ->
        rows.map {
            ImportReview(
                it.id, it.bankId, it.amountMinor, LedgerCurrency.valueOf(it.currency),
                EntryType.valueOf(it.type), it.merchant, it.cardLast4, it.eventAtMillis,
                it.timeFromMessage, it.transfer, it.status,
            )
        }
    }

    override fun sources(currency: LedgerCurrency, from: LocalDate, through: LocalDate) =
        dao.sources(currency.name, from.toEpochDay(), through.toEpochDay()).map { rows ->
            rows.map { BankSourceSpend(it.sourceType, it.bankId, it.totalMinor) }
        }

    override fun categorySpending(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?) =
        dao.detailCategories(currency.name, from.toEpochDay(), through.toEpochDay(), source, bank).map { rows ->
            rows.map { BankCategorySpend(Category.valueOf(it.category), it.customCategoryId, it.customName, it.customColor, it.totalMinor) }
        }

    override fun recent(currency: LedgerCurrency, from: LocalDate, through: LocalDate, source: String?, bank: String?) =
        dao.recent(currency.name, from.toEpochDay(), through.toEpochDay(), source, bank).map { rows -> rows.map { it.toDomain() } }

    override suspend fun exportEntries() = dao.exportEntries().map { it.toDomain() }
    override suspend fun clearHistory() { dao.clearInbox() }
    override fun budget(currency: LedgerCurrency): Long = config.getLong("budget_${currency.name}", 0L)
    override fun setBudget(currency: LedgerCurrency, minor: Long) { require(minor >= 0); config.edit().putLong("budget_${currency.name}", minor).apply() }

    override var enabled: Boolean
        get() = config.getBoolean("enabled", true)
        set(value) { config.edit().putBoolean("enabled", value).apply() }

    override var autoImport: Boolean
        get() = config.getBoolean("auto", true)
        set(value) { config.edit().putBoolean("auto", value).apply() }

    override var importMode: String
        get() = config.getString("mode", "NOTIFY") ?: "NOTIFY"
        set(value) { require(value in listOf("API", "SMS", "NOTIFY")); config.edit().putString("mode", value).apply() }

    // -------------------------------------------------------------------------
    // monobank personal API (token mode)
    // -------------------------------------------------------------------------

    private val monoState = MutableStateFlow(MonobankStatus(
        connected = config.getString("mono_accounts", null) != null,
        lastSyncMillis = config.getLong("mono_last_sync", 0L),
    ))
    override val monobankStatus = monoState.asStateFlow()
    private val syncLock = kotlinx.coroutines.sync.Mutex()

    private fun savedAccounts(): List<MonobankClient.Account> =
        config.getString("mono_accounts", null)?.split(';')?.mapNotNull { row ->
            val f = row.split('|')
            if (f.size < 4) null else MonobankClient.Account(f[0], runCatching { LedgerCurrency.valueOf(f[1]) }.getOrNull() ?: return@mapNotNull null, f[2], f[3].ifBlank { null })
        }.orEmpty()

    private fun saveAccounts(accounts: List<MonobankClient.Account>) {
        config.edit().putString("mono_accounts", accounts.joinToString(";") { "${it.id}|${it.currency.name}|${it.type}|${it.cardLast4.orEmpty()}" }).apply()
    }

    /** Validates the token with monobank, stores it encrypted (Android Keystore) and remembers the accounts. */
    override suspend fun connectMonobank(token: String): Boolean = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.length !in 20..200 || !clean.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            monoState.update { it.copy(error = "BAD_TOKEN") }; return@withContext false
        }
        try {
            val accounts = monobank.accounts(clean)
            if (accounts.isEmpty()) { monoState.update { it.copy(error = "NO_ACCOUNTS") }; return@withContext false }
            val store = vault ?: error("Secure storage unavailable")
            store.update { it.put("monoToken", clean); Unit }
            saveAccounts(accounts)
            config.edit().remove("mono_last_sync").putString("mode", "API").apply()
            monoState.value = MonobankStatus(connected = true, accounts = accounts.size)
            true
        } catch (e: MonobankClient.ApiException) {
            monoState.update { it.copy(error = if (e.invalidToken) "BAD_TOKEN" else if (e.rateLimited) "RATE_LIMIT" else "NETWORK") }; false
        } catch (e: java.io.IOException) {
            monoState.update { it.copy(error = "NETWORK") }; false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            monoState.update { it.copy(error = "NETWORK") }; false
        }
    }

    override suspend fun disconnectMonobank() = withContext(Dispatchers.IO) {
        runCatching { vault?.update { it.remove("monoToken"); Unit } }
        config.edit().remove("mono_accounts").remove("mono_last_sync").apply()
        monoState.value = MonobankStatus()
    }

    /**
     * Pulls new operations for every saved account. monobank allows one statement request per account
     * per 60 s, so accounts are fetched one minute apart. Safe to call repeatedly: operations are keyed by id.
     */
    override suspend fun syncMonobank(): Int = withContext(Dispatchers.IO) {
        if (!syncLock.tryLock()) return@withContext 0
        try {
            val token = vault?.read { it.optString("monoToken", "") }.orEmpty()
            val accounts = savedAccounts()
            if (token.isBlank() || accounts.isEmpty()) { monoState.update { it.copy(connected = false) }; return@withContext 0 }
            monoState.update { it.copy(syncing = true, error = null) }
            val nowSec = System.currentTimeMillis() / 1000
            val last = config.getLong("mono_last_sync", 0L) / 1000
            val from = if (last > 0) maxOf(last - 3600, nowSec - 31L * 86400) else nowSec - 30L * 86400
            var imported = 0
            accounts.take(4).forEachIndexed { index, account ->
                if (index > 0) kotlinx.coroutines.delay(61_000)
                val rows = monobank.statement(token, account.id, from, nowSec)
                rows.sortedBy { it.timeSeconds }.forEach { row ->
                    val op = MonobankMapper.map(row, account.currency, account.cardLast4) ?: return@forEach
                    val result = store(op, "MONO:${row.id}", row.timeSeconds * 1000, "API", authoritative = true)
                    if (result == "AUTO_IMPORTED") imported++
                }
            }
            val now = System.currentTimeMillis()
            config.edit().putLong("mono_last_sync", now).apply()
            monoState.update { it.copy(connected = true, syncing = false, lastSyncMillis = now, lastImported = imported, error = null, accounts = accounts.size) }
            imported
        } catch (e: MonobankClient.ApiException) {
            monoState.update { it.copy(syncing = false, error = if (e.invalidToken) "BAD_TOKEN" else if (e.rateLimited) "RATE_LIMIT" else "NETWORK") }
            0
        } catch (e: java.io.IOException) {
            monoState.update { it.copy(syncing = false, error = "NETWORK") }; 0
        } catch (e: Exception) {
            if (e is CancellationException) { monoState.update { it.copy(syncing = false) }; throw e }
            monoState.update { it.copy(syncing = false, error = "NETWORK") }; 0
        } finally { syncLock.unlock() }
    }

    // -------------------------------------------------------------------------
    // Core receive path
    // -------------------------------------------------------------------------

    /** Parses a bank text (push/SMS/pasted) and stores it. Returns the outcome code. */
    suspend fun receive(
        bank: Bank, text: String, eventKey: String, at: Long,
        origin: String, manual: Boolean = false,
    ): String {
        if (!enabled && !manual) { note("DISABLED"); return "DISABLED" }
        val parsed = BankParser.parse(bank, text, at) ?: run { note("UNRECOGNIZED"); return "UNRECOGNIZED" }
        return store(parsed, eventKey, at, origin, manual)
    }

    /**
     * Stores an already parsed operation. [authoritative] sources (the bank's own API, unique id per
     * operation) skip the heuristic duplicate checks: the event key alone guarantees "once".
     */
    suspend fun store(
        parsed: ParsedBankOperation, eventKey: String, at: Long, origin: String,
        manual: Boolean = false, authoritative: Boolean = false,
    ): String {
        if (!enabled && !manual) { note("DISABLED"); return "DISABLED" }
        var result = "QUEUED"
        db.withTransaction {
            // --- Tier 1: same-bank re-delivery guard ---
            val sameBankDup = !authoritative && dao.similar(
                parsed.bankId, parsed.amountMinor, parsed.currency.name, parsed.type.name,
                parsed.happenedAtMillis, parsed.cardLast4, parsed.merchant,
            ) > 0

            // --- Tier 2: cross-source dedup (wallet + bank for the same tap) ---
            // Only applies when the same-bank check didn't already flag it.
            val crossSourceDup = !authoritative && !sameBankDup && dao.similarCrossSource(
                parsed.amountMinor, parsed.currency.name, parsed.type.name, parsed.happenedAtMillis,
            ) > 0

            // With auto-import on, repeats are dropped silently: a bank re-posting the same push, or a wallet
            // app and the issuing bank announcing the same tap. Otherwise they wait in the review queue.
            val walletInvolved = Banks.WALLET_ID == parsed.bankId || crossSourceDup && dao.walletTwin(
                parsed.amountMinor, parsed.currency.name, parsed.type.name, parsed.happenedAtMillis) > 0
            val silentDrop = !manual && autoImport && crossSourceDup && walletInvolved
            val status = when {
                silentDrop -> "DUPLICATE_IGNORED"
                sameBankDup   -> "DUPLICATE_REVIEW"
                crossSourceDup -> "CROSS_DUPLICATE_REVIEW"
                else          -> "REVIEW"
            }

            // groupKey: SHA-256(amount|currency|type|5-min bucket) — links twin events
            val bucket = parsed.happenedAtMillis / 300_000L   // 5-minute windows
            val groupKey = digest("${parsed.amountMinor}|${parsed.currency}|${parsed.type}|$bucket")

            val item = BankInboxEntity(
                id = UUID.randomUUID().toString(),
                eventKey = digest(eventKey),
                bankId = parsed.bankId,
                amountMinor = parsed.amountMinor,
                currency = parsed.currency.name,
                type = parsed.type.name,
                merchant = parsed.merchant,
                cardLast4 = parsed.cardLast4,
                eventAtMillis = parsed.happenedAtMillis,
                receivedAtMillis = at,
                timeFromMessage = parsed.timeFromMessage,
                transfer = parsed.transfer,
                category = parsed.category.name,
                origin = origin,
                status = status,
                groupKey = groupKey,
            )

            if (dao.insertInbox(item) == -1L) { result = "REDELIVERED"; return@withTransaction }
            result = status

            // Auto-import: only for confident single-source entries.
            // Cross-source duplicates are NEVER auto-imported — the user must review.
            if (!manual && autoImport && parsed.confident && !parsed.transfer && status == "REVIEW") {
                acceptInside(item, parsed.type)
                result = "AUTO_IMPORTED"
                
            }
        }
        // Notify only AFTER commit. A denied notification permission must never undo the entry.
        if (result == "AUTO_IMPORTED" && System.currentTimeMillis() - parsed.happenedAtMillis in 0 until 15 * 60_000L) postImportNotification(parsed)
        note(result)
        return result
    }

    private val privatState = MutableStateFlow(MonobankStatus(
        connected = config.getBoolean("privat_connected", false),
        lastSyncMillis = config.getLong("privat_last_sync", 0L)))
    override val privatStatus = privatState.asStateFlow()
    private val privatLock = kotlinx.coroutines.sync.Mutex()

    override suspend fun connectPrivat(token: String): Boolean = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.length !in 20..2048 || clean.any { it.code !in 33..126 }) {
            privatState.update { it.copy(error = "BAD_TOKEN") }; return@withContext false
        }
        try {
            val today = LocalDate.now(ZoneId.of("Europe/Kyiv"))
            // Validate access before storing credentials. No payment endpoints are called.
            privat.statement(clean, today, today)
            val store = vault ?: error("Secure storage unavailable")
            store.update { it.put("privatToken", clean); Unit }
            config.edit().putBoolean("privat_connected", true).remove("privat_last_sync").putString("mode", "API").apply()
            privatState.value = MonobankStatus(connected = true)
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { privatState.update { it.copy(error = privatError(e)) }; false }
    }

    override suspend fun disconnectPrivat() = withContext(Dispatchers.IO) {
        vault?.update { it.remove("privatToken"); Unit }
        config.edit().remove("privat_connected").remove("privat_last_sync").apply()
        privatState.value = MonobankStatus()
    }

    override suspend fun syncPrivat(): Int = withContext(Dispatchers.IO) {
        if (!privatLock.tryLock()) return@withContext 0
        try {
            if (!enabled || !config.getBoolean("privat_connected", false)) return@withContext 0
            val token = vault?.read { it.optString("privatToken", "") }.orEmpty()
            if (token.isBlank()) { privatState.update { it.copy(connected = false) }; return@withContext 0 }
            privatState.update { it.copy(syncing = true, error = null) }
            val now = System.currentTimeMillis()
            val zone = ZoneId.of("Europe/Kyiv")
            val through = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val last = config.getLong("privat_last_sync", 0L)
            val from = if (last > 0) Instant.ofEpochMilli(last).atZone(zone).toLocalDate().minusDays(1).coerceAtLeast(through.minusDays(29)) else through.minusDays(29)
            var imported = 0
            // Short bounded windows also accommodate banks with restrictive statement date limits.
            var begin = from
            while (!begin.isAfter(through)) {
                val end = begin.plusDays(6).coerceAtMost(through)
                privat.statement(token, begin, end).forEach { row ->
                    val key = "PRIVAT:${row.key}"
                    if (store(row.parsed, key, row.parsed.happenedAtMillis, "API", authoritative = true) == "AUTO_IMPORTED") imported++
                }
                begin = end.plusDays(1)
            }
            config.edit().putLong("privat_last_sync", now).apply()
            privatState.update { it.copy(connected = true, syncing = false, lastSyncMillis = now, lastImported = imported, error = null) }
            imported
        } catch (e: CancellationException) { privatState.update { it.copy(syncing = false) }; throw e }
        catch (e: Exception) { privatState.update { it.copy(syncing = false, error = privatError(e)) }; 0 }
        finally { privatLock.unlock() }
    }

    private fun privatError(e: Exception): String = when ((e as? PrivatBankClient.ApiException)?.code) {
        401, 403 -> "BAD_TOKEN"; 429 -> "RATE_LIMIT"; else -> "NETWORK"
    }

    // -------------------------------------------------------------------------
    // Accept / ignore
    // -------------------------------------------------------------------------

    override suspend fun accept(id: String, type: EntryType) {
        db.withTransaction {
            val item = dao.inboxItem(id) ?: return@withTransaction
            if (item.status !in listOf("REVIEW", "DUPLICATE_REVIEW", "CROSS_DUPLICATE_REVIEW")) return@withTransaction
            acceptInside(item, type)
        }
    }

    private suspend fun acceptInside(item: BankInboxEntity, type: EntryType) {
        val id = "bank-${item.id}"
        val date = Instant.ofEpochMilli(item.eventAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val title = item.merchant.ifBlank { Banks.name(item.bankId) }.take(60)
        val entity = EntryEntity(
            id, title, item.amountMinor, item.currency, type.name, item.category,
            date.toEpochDay(),
            "",
            title.lowercase(Locale.ROOT),
            item.receivedAtMillis,
            sourceType = "CARD",
            bankId = item.bankId,
            cardLast4 = item.cardLast4,
            eventAtMillis = item.eventAtMillis,
            origin = item.origin,
        )
        db.ledgerDao().insert(entity)
        dao.status(item.id, "IMPORTED", id)
    }

    override suspend fun ignore(id: String) {
        db.withTransaction {
            val item = dao.inboxItem(id) ?: return@withTransaction
            if (item.status in listOf("REVIEW", "DUPLICATE_REVIEW", "CROSS_DUPLICATE_REVIEW"))
                dao.status(id, "IGNORED", null)
        }
    }

    override suspend fun createCategory(name: String, hex: String) {
        require(name.trim().length in 1..40)
        val color = ColorPreferences.normalizeHex(hex) ?: error("Invalid color")
        dao.addCategory(CustomCategoryEntity(UUID.randomUUID().toString(), name.trim(), color))
    }

    // -------------------------------------------------------------------------
    // Background notification
    // -------------------------------------------------------------------------

    /**
     * Posts a silent notification so the user knows something was auto-imported
     * while the app was in the background.  The channel is created lazily; on
     * Android < O the call is a no-op.
     */
    private fun postImportNotification(parsed: ParsedBankOperation) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channelId = "ledger_import_v2"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Saved bank payments", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Background bank import events"
                    setShowBadge(false)
                },
            )
        }
        val amountText = Money.editable(parsed.amountMinor)
        val title = "${Banks.name(parsed.bankId)}: ${parsed.merchant.ifBlank { Banks.name(parsed.bankId) }}"
        val body = "${if (parsed.type == EntryType.EXPENSE) "−" else "+"} $amountText ${parsed.currency.name}"
        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle(title)
            .setContentText(body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(android.app.PendingIntent.getActivity(ctx, 0,
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: android.content.Intent(),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Use a stable ID derived from the amount+time so rapid arrivals don't stack
        val notifId = (parsed.amountMinor xor parsed.happenedAtMillis).toInt()
        try { nm.notify(notifId, notification) } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    companion object {
        fun digest(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/bank/MonobankClient.kt

```kotlin
package com.semmyx.ledger.data.bank

import com.google.gson.JsonParser
import com.semmyx.ledger.domain.LedgerCurrency
import com.semmyx.ledger.domain.bank.MonobankMapper
import com.semmyx.ledger.domain.bank.MonobankStatementItem
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Minimal client for monobank's personal API (https://api.monobank.ua/docs/). Read-only; the token is
 * sent only to this host in the `X-Token` header. Blocking calls: run on a background dispatcher.
 */
class MonobankClient(
    private val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://api.monobank.ua",
) {
    data class Account(val id: String, val currency: LedgerCurrency, val type: String, val cardLast4: String?)

    class ApiException(val httpCode: Int, message: String) : IOException(message) {
        val invalidToken get() = httpCode == 403 || httpCode == 401
        val rateLimited get() = httpCode == 429
    }

    private fun get(path: String, token: String): String {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + path).header("X-Token", token).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.code, "monobank HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    fun accounts(token: String): List<Account> {
        val root = JsonParser.parseString(get("/personal/client-info", token)).asJsonObject
        return root.getAsJsonArray("accounts")?.mapNotNull { element ->
            val a = element.asJsonObject
            val currency = MonobankMapper.currency(a.get("currencyCode")?.asInt ?: return@mapNotNull null) ?: return@mapNotNull null
            val type = a.get("type")?.asString.orEmpty()
            if (type == "eAid") return@mapNotNull null // state-aid wallets are not spending accounts
            val mask = a.getAsJsonArray("maskedPan")?.firstOrNull()?.asString
            Account(a.get("id").asString, currency, type, mask?.takeLast(4)?.takeIf { it.all(Char::isDigit) })
        }.orEmpty()
    }

    fun statement(token: String, accountId: String, fromSeconds: Long, toSeconds: Long): List<MonobankStatementItem> {
        val array = JsonParser.parseString(get("/personal/statement/$accountId/$fromSeconds/$toSeconds", token)).asJsonArray
        return array.mapNotNull { element ->
            val o = element.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            MonobankStatementItem(
                id = id,
                timeSeconds = o.get("time")?.asLong ?: return@mapNotNull null,
                description = o.get("description")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                mcc = o.get("mcc")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                amount = o.get("amount")?.asLong ?: return@mapNotNull null,
                comment = o.get("comment")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            )
        }
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/bank/PrivatBankClient.kt

```kotlin
package com.semmyx.ledger.data.bank

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.semmyx.ledger.domain.*
import com.semmyx.ledger.domain.bank.*
import java.io.IOException
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Privat24 Business AutoClient. GET statements only; not the API for retail Privat24 cards. */
class PrivatBankClient(
    private val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(45, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://acp.privatbank.ua",
) {
    data class Operation(val key: String, val parsed: ParsedBankOperation)
    class ApiException(val code: Int) : IOException("PrivatBank request failed ($code)")

    fun statement(token: String, from: LocalDate, through: LocalDate): List<Operation> {
        require(!through.isBefore(from))
        val result = mutableListOf<Operation>()
        var cursor: String? = null
        val cursors = mutableSetOf<String>()
        // Pagination is bounded; a broken/repeated cursor cannot loop forever or silently truncate.
        repeat(100) {
            val url = (baseUrl.trimEnd('/') + "/api/statements/transactions").toHttpUrl().newBuilder()
                .addQueryParameter("startDate", from.format(DateTimeFormatter.ofPattern("dd-MM-uuuu")))
                .addQueryParameter("endDate", through.format(DateTimeFormatter.ofPattern("dd-MM-uuuu")))
                .addQueryParameter("limit", "100")
            cursor?.let { url.addQueryParameter("followId", it) }
            val request = Request.Builder().url(url.build()).get().header("token", token)
                .header("User-Agent", "TG-Ledger/3.3.3")
                .header("Content-Type", "application/json;charset=utf8").header("Accept", "application/json").build()
            val root = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ApiException(response.code)
                JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            }
            if (root.string("status") != "SUCCESS") throw ApiException(422)
            root.getAsJsonArray("transactions")?.forEach { row -> map(row.asJsonObject)?.let(result::add) }
            val more = root.string("exist_next_page").equals("true", ignoreCase = true)
            if (!more) return result.distinctBy { it.key }
            cursor = root.string("next_page_id").takeIf { it.isNotBlank() } ?: throw ApiException(422)
            if (!cursors.add(cursor!!)) throw ApiException(422)
        }
        throw ApiException(422)
    }

    companion object {
        private fun JsonObject.string(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
        fun map(row: JsonObject): Operation? {
            if (row.string("PR_PR") != "r" || row.string("FL_REAL") != "r") return null
            val type = when (row.string("TRANTYPE")) { "D" -> EntryType.EXPENSE; "C" -> EntryType.INCOME; else -> return null }
            val currency = runCatching { LedgerCurrency.valueOf(row.string("CCY")) }.getOrNull() ?: return null
            val minor = runCatching { BigDecimal(row.string("SUM")).abs().movePointRight(2).longValueExact() }.getOrNull() ?: return null
            if (minor <= 0 || minor > Money.MAX_ENTRY_MINOR) return null
            val ref = row.string("REF"); val refn = row.string("REFN"); val account = row.string("AUT_MY_ACC")
            if (ref.isBlank() || refn.isBlank() || account.isBlank()) return null
            val timestamp = runCatching {
                LocalDateTime.parse(row.string("DATE_TIME_DAT_OD_TIM_P"),
                    DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss").withResolverStyle(ResolverStyle.STRICT))
                    .atZone(ZoneId.of("Europe/Kyiv")).toInstant().toEpochMilli()
            }.getOrNull() ?: return null
            val merchant = row.string("AUT_CNTR_NAM").take(60).ifBlank { "ПриватБанк" }
            val purpose = row.string("OSND")
            val transfer = Regex("між власними|власн.*рахунк|между своими|own accounts", RegexOption.IGNORE_CASE).containsMatchIn(purpose)
            val category = if (transfer) Category.TRANSFER else BankParser.categorize(merchant)
            return Operation("$account|$ref|$refn", ParsedBankOperation("privat", minor, currency, type,
                merchant, null, timestamp, true, transfer, !transfer, category))
        }
    }
}
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
        scope.launch {
            refreshLocalEntitlements()
            // The single 14-day free trial starts by itself on first launch: no button, no card, no renewal.
            if (trial.value.available) startTrial(TrialPolicy.DAYS)
        }
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
import com.semmyx.ledger.data.bank.BankDao
import com.semmyx.ledger.data.bank.BankInboxEntity
import com.semmyx.ledger.data.bank.CustomCategoryEntity

@Database(
    entities = [EntryEntity::class, RateEntity::class, CustomCategoryEntity::class, BankInboxEntity::class],
    version = 3,
    exportSchema = true,
)
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

        /**
         * v2 → v3: adds [groupKey] column and its index to bank_inbox.
         * groupKey links wallet-app + issuing-bank notifications for the same card tap
         * to support cross-source deduplication. NULL for existing rows is safe.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bank_inbox ADD COLUMN groupKey TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_inbox_groupKey ON bank_inbox(groupKey)")
            }
        }

        fun create(context: Context): LedgerDatabase = Room.databaseBuilder(
            context.applicationContext, LedgerDatabase::class.java, "ledger.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .build()
        // No fallbackToDestructiveMigration — financial records must never be erased automatically.
    }
}
```

## data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerEntities.kt

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
            premiumAssistantEnabled = p[Keys.premiumAssistant] ?: true,
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
    return BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(requireNotNull(offer.offerToken)).build()
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

## data/src/test/kotlin/com/semmyx/ledger/data/bank/MonobankClientTest.kt

```kotlin
package com.semmyx.ledger.data.bank

import com.semmyx.ledger.domain.LedgerCurrency
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MonobankClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: MonobankClient
    @Before fun setUp() { server = MockWebServer().also { it.start() }; client = MonobankClient(baseUrl = server.url("/").toString()) }
    @After fun tearDown() { server.shutdown() }

    @Test fun accountsAreParsedAndTokenIsSentAsHeader() {
        server.enqueue(MockResponse().setBody("""{"clientId":"c","name":"N","accounts":[
            {"id":"acc1","currencyCode":980,"type":"black","maskedPan":["537541******1234"],"balance":1000},
            {"id":"acc2","currencyCode":840,"type":"white","maskedPan":[],"balance":5},
            {"id":"acc3","currencyCode":980,"type":"eAid","maskedPan":[]},
            {"id":"acc4","currencyCode":392,"type":"black","maskedPan":[]}]}"""))
        val accounts = client.accounts("secret-token")
        assertEquals(listOf("acc1", "acc2"), accounts.map { it.id })
        assertEquals("1234", accounts[0].cardLast4); assertNull(accounts[1].cardLast4)
        assertEquals(LedgerCurrency.USD, accounts[1].currency)
        val request = server.takeRequest()
        assertEquals("/personal/client-info", request.path); assertEquals("secret-token", request.getHeader("X-Token"))
    }

    @Test fun statementRowsAreParsed() {
        server.enqueue(MockResponse().setBody("""[{"id":"s1","time":1700000000,"description":"Сільпо","mcc":5411,"amount":-12550,"balance":1,"currencyCode":980},
            {"id":"s2","time":1700000100,"description":null,"mcc":4829,"amount":50000}]"""))
        val rows = client.statement("t", "acc1", 1699000000, 1700000200)
        assertEquals(2, rows.size)
        assertEquals(-12550L, rows[0].amount); assertEquals("Сільпо", rows[0].description); assertEquals(5411, rows[0].mcc)
        assertEquals("", rows[1].description)
        assertEquals("/personal/statement/acc1/1699000000/1700000200", server.takeRequest().path)
    }

    @Test fun errorsCarryTheHttpCode() {
        server.enqueue(MockResponse().setResponseCode(403))
        val bad = try { client.accounts("x"); null } catch (e: MonobankClient.ApiException) { e }
        assertTrue(bad!!.invalidToken)
        server.enqueue(MockResponse().setResponseCode(429))
        val slow = try { client.statement("x", "a", 1, 2); null } catch (e: MonobankClient.ApiException) { e }
        assertTrue(slow!!.rateLimited)
    }
}
```

## data/src/test/kotlin/com/semmyx/ledger/data/bank/PrivatBankClientTest.kt

```kotlin
package com.semmyx.ledger.data.bank
import com.google.gson.JsonParser
import com.semmyx.ledger.domain.*
import okhttp3.mockwebserver.*
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*

class PrivatBankClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: PrivatBankClient
    private val row = """{"AUT_MY_ACC":"UA111111111111111111111111111","AUT_CNTR_NAM":"Сільпо","CCY":"UAH","FL_REAL":"r","PR_PR":"r","SUM":"125.50","REF":"ref1","REFN":"1","TRANTYPE":"D","DATE_TIME_DAT_OD_TIM_P":"14.10.2026 12:01:02","OSND":"Оплата"}"""
    @Before fun before() { server = MockWebServer().also { it.start() }; client = PrivatBankClient(baseUrl = server.url("/").toString()) }
    @After fun after() { server.shutdown() }
    @Test fun readOnlyRequestPaginationAndExactMoney() {
        server.enqueue(MockResponse().setBody("""{"status":"SUCCESS","exist_next_page":true,"next_page_id":"cursor1","transactions":[$row]}"""))
        server.enqueue(MockResponse().setBody("""{"status":"SUCCESS","exist_next_page":false,"transactions":[$row]}"""))
        val rows = client.statement("test-token", LocalDate.of(2026,10,1), LocalDate.of(2026,10,7))
        assertEquals(1, rows.size); assertEquals(12550L, rows[0].parsed.amountMinor)
        assertEquals(EntryType.EXPENSE, rows[0].parsed.type)
        assertEquals(Category.FOOD, rows[0].parsed.category)
        val first = server.takeRequest()
        assertEquals("GET", first.method); assertEquals("test-token", first.getHeader("token"))
        assertEquals("01-10-2026", first.requestUrl!!.queryParameter("startDate"))
        assertEquals("cursor1", server.takeRequest().requestUrl!!.queryParameter("followId"))
    }
    @Test fun onlyBookedRowsAndSupportedCurrenciesAreImported() {
        fun map(text: String) = PrivatBankClient.map(JsonParser.parseString(text).asJsonObject)
        assertNull(map(row.replace("\"PR_PR\":\"r\"", "\"PR_PR\":\"p\"")))
        assertNull(map(row.replace("\"FL_REAL\":\"r\"", "\"FL_REAL\":\"i\"")))
        assertNull(map(row.replace("\"UAH\"", "\"JPY\"")))
        assertNull(map(row.replace("125.50", "0")))
        assertEquals(EntryType.INCOME, map(row.replace("\"D\"", "\"C\""))!!.parsed.type)
        assertNull(map(row.replace("14.10.2026", "31.02.2026")))
    }
    @Test fun repeatedOrMissingCursorFailsRatherThanSilentlyLosingHistory() {
        repeat(2) { server.enqueue(MockResponse().setBody("""{"status":"SUCCESS","exist_next_page":true,"next_page_id":"same","transactions":[]}""")) }
        try { client.statement("test", LocalDate.now(), LocalDate.now()); fail() }
        catch (e: PrivatBankClient.ApiException) { assertEquals(422, e.code) }
    }
    @Test fun badCredentialsAreReported() {
        server.enqueue(MockResponse().setResponseCode(403))
        try { client.statement("test", LocalDate.now(), LocalDate.now()); fail() }
        catch (e: PrivatBankClient.ApiException) { assertEquals(403, e.code) }
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
    /** One trial only: 14 days. */
    const val DAYS = 14
    fun validDays(days: Int) = days == DAYS
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
    val premiumAssistantEnabled: Boolean = true,
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
import java.time.format.ResolverStyle
import java.util.Locale

enum class PaymentSource { UNKNOWN, CASH, CARD }

/**
 * [packages] are exact Android package names; [packageHints] are fragments that identify the same app
 * when the vendor ships it under a different id (regional builds, beta channels).
 */
data class Bank(
    val id: String, val name: String, val packages: Set<String>, val senders: Set<String>,
    val packageHints: Set<String> = emptySet(),
)

object Banks {
    const val WALLET_ID = "wallet"
    val all = listOf(
        Bank("privat", "ПриватБанк", setOf("ua.privatbank.ap24", "com.privatbank.ap24", "com.pb"), setOf("privatbank", "privat", "privat24", "3700"), setOf("privatbank", "ap24")),
        Bank("mono", "monobank", setOf("com.ftband.mono"), setOf("monobank", "mono"), setOf("ftband", "monobank")),
        Bank("sense", "Sense Bank", setOf("ua.alfabank.mobile.android", "ua.sensebank.mobile.android"), setOf("sensebank", "sense", "alfabank"), setOf("alfabank", "sensebank")),
        Bank("abank", "А-Банк", setOf("ua.com.abank", "ua.com.abank.android"), setOf("abank", "a-bank", "а-банк"), setOf("abank")),
        Bank("oschad", "Ощадбанк", setOf("ua.oschadbank.online"), setOf("oschadbank", "oschad", "ощадбанк"), setOf("oschad")),
        Bank("pumb", "ПУМБ", setOf("com.fuib.android.spot.online"), setOf("pumb", "fuib", "пумб"), setOf("fuib", "pumb")),
        Bank("revolut", "Revolut", setOf("com.revolut.revolut"), setOf("revolut"), setOf("revolut")),
        Bank("wise", "Wise", setOf("com.transferwise.android"), setOf("wise", "transferwise"), setOf("transferwise")),
        Bank("n26", "N26", setOf("de.number26.android"), setOf("n26"), setOf("number26")),
        // Wallet apps announce the same tap as the issuing bank; the pair is collapsed by cross-source deduplication.
        Bank("other", "Інший банк / Other bank", emptySet(), emptySet()),
        Bank(WALLET_ID, "Google Wallet", setOf("com.google.android.apps.walletnfcrel", "com.samsung.android.spay"), emptySet(), setOf("walletnfcrel")),
    )
    fun byPackage(value: String): Bank? =
        all.firstOrNull { value in it.packages } ?: all.firstOrNull { bank -> bank.packageHints.any { value.contains(it, ignoreCase = true) } }
    /** SMS sender ids vary ("PRIVATBANK", "Privat24", "monobank", "SENSE BANK"): exact match, else a name fragment (4+ chars). */
    fun bySender(value: String): Bank? {
        val sender = value.trim().lowercase(Locale.ROOT)
        if (sender.isEmpty()) return null
        return all.firstOrNull { sender in it.senders }
            ?: all.firstOrNull { bank -> bank.senders.any { it.length >= 4 && sender.contains(it) } }
    }
    fun name(id: String?) = all.firstOrNull { it.id == id }?.name ?: id.orEmpty()
}

data class ParsedBankOperation(val bankId: String, val amountMinor: Long, val currency: LedgerCurrency,
    val type: EntryType, val merchant: String, val cardLast4: String?, val happenedAtMillis: Long,
    val timeFromMessage: Boolean, val transfer: Boolean, val confident: Boolean, val category: Category)

/**
 * Pure offline parser for bank push / SMS texts (Ukrainian, Russian, English and transliterated).
 *
 * A message becomes an operation only when it carries an amount **and** a clear direction (sign or
 * keyword). OTP codes, declines, ads, balance-only texts and conversion amounts are ignored.
 * `confident` means: single amount, known direction -> safe to save without asking.
 */
object BankParser {
    private const val NUM = "(?:[0-9]{1,3}(?:[ .,][0-9]{3})+(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)"
    private const val WORD_CUR = "(?:UAH|USD|EUR|GBP|PLN|грн\\.?|гривень|гривні|гривня|zł|zl)(?![\\p{L}])"
    private const val SYMBOL_CUR = "[₴$€£]"
    private val suffixAmount = Regex("([+-])?\\s*($NUM)\\s*($SYMBOL_CUR|$WORD_CUR)", RegexOption.IGNORE_CASE)
    private val prefixAmount = Regex("(?<![\\p{L}])($SYMBOL_CUR|UAH|USD|EUR|GBP|PLN)\\s*([+-])?\\s*($NUM)", RegexOption.IGNORE_CASE)

    private val nonTransactionAmount = Regex(
        "(?:баланс|бал\\.?|залишок|остаток|доступн\\w*|available|balance|bal\\.?|ліміт|лимит|limit|кредитн\\w*|кешбек\\w*|кэшбэк\\w*|cashback|комісі\\w*|комиссия|fee|commission|до сплати|мін\\w*\\s+платіж|минимальный платеж)\\s*[:=-]?\\s*$",
        RegexOption.IGNORE_CASE)
    private val refund = Regex("(?:повернен|возврат|refund|reversal|скасуванн|відміна оплати)", RegexOption.IGNORE_CASE)
    private val expense = Regex(
        "(?:списан|потрат|потрач|сплачен|розрахува|знят[оті]|зняття|снят|витрат|видатк|оплат|покупк|купівл|куплен|платіж|платеж|purchase|paid|payment|spent|debit|withdraw|charged|відправлен|sent|spysan|spisan|snyat|znyat|oplat|splat|pokupk|kupivl|platizh)",
        RegexOption.IGNORE_CASE)
    private val income = Regex(
        "(?:зарахован|зарахув|поповнен|пополнен|надходжен|зачислен|отримано|получен|received|credited|top[ -]?up|deposit|salary|зарплат|zarahov|zarakhov|zarahuv|zarakhuv|zachisl|popovnen|popolnen|nadkhodzh|nadhodzh)",
        RegexOption.IGNORE_CASE)
    private val transfer = Regex("(?:переказ|перевод|transfer|p2p|perekaz|perevod)", RegexOption.IGNORE_CASE)
    private val transferIn = Regex("(?:від\\s|от\\s|from\\s|отримано|получен|received|зарахован|зачислен)", RegexOption.IGNORE_CASE)
    private val transferOut = Regex("(?:\\sна\\s|\\sto\\s|відправлен|отправлен|sent|списан|знят)", RegexOption.IGNORE_CASE)
    private val otp = Regex("(?:одноразов|one[ -]?time|verification code|код\\s+(?:входу|підтвердження|подтверждения|безпеки)|otp|пароль|password|cvv)", RegexOption.IGNORE_CASE)
    private val declined = Regex("(?:відхилен|отклонен|declined|failed|невдал|недостатньо|insufficient|не вдалос|заблоковано)", RegexOption.IGNORE_CASE)
    private val promo = Regex("(?:кредит до|кредит на|до \\d[\\d ]*\\s*(?:грн|₴)\\s+(?:на|під|без)|акці[яї]|знижк|пропозиці|промо|promo|розіграш|виграй|вигра|реклам|оформи|отримай)", RegexOption.IGNORE_CASE)
    private val card = Regex("(?:карт[аикоуі]*|card|[＊*•·]{1,})\\s*[:№#*•·.-]*\\s*([0-9]{4})(?![0-9])", RegexOption.IGNORE_CASE)
    private val label = Regex("(?:merchant|магазин|мерчант|торговець|торгова точка|місце|место|place|details|description|опис|описание|призначення|назначение)\\s*[:=]\\s*([^;\\n]+)", RegexOption.IGNORE_CASE)
    private val preposition = Regex("(?<![\\p{L}\\p{N}])(?:at|у|в|in)\\s+([^;\\n,]+)", RegexOption.IGNORE_CASE)
    private val dateTime = Regex("([0-9]{2}[./][0-9]{2}[./][0-9]{4})[ ,T]+([0-9]{2}:[0-9]{2}(?::[0-9]{2})?)")
    private val clockOnly = Regex("(?<![0-9:.])([0-9]{1,2}:[0-9]{2})(?![0-9:])")
    private val trailer = Regex(
        "\\s*(?:[;|•·]|(?:баланс|залишок|остаток|бал\\.|balance|bal\\.|карт[аикоуі]*|card|рахунок|доступно)(?![\\p{L}\\p{N}])|[0-9]{2}[./][0-9]{2}[./][0-9]{2,4}|[0-9]{1,2}:[0-9]{2}|[＊*•]{1,}\\s*[0-9]{4}).*$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val genericTitle = Regex(
        "(?:оплата|покупка|купівля|списання|списано|зарахування|зараховано|поповнення|переказ|платіж|транзакці\\w*|нова транзакція|операці\\w*|витрата|надходження|payment|purchase|transaction|new transaction|transfer|debit|credit|card payment|оплата карткою|apple pay|google pay|google wallet|samsung pay|карт[аиок]*|card|\\p{P}|\\s)+",
        RegexOption.IGNORE_CASE)
    private val emoji = Regex("[\\p{So}\\p{Cs}\\u200d\\uFE0F]+")

    private data class Hit(val range: IntRange, val minor: Long, val currency: LedgerCurrency, val sign: Char?)

    private fun normalize(text: String) = text
        .replace(Regex("[\\u2212\\u2012\\u2013\\u2014\\u2796\\uFE63\\uFF0D]"), "-")
        .replace(Regex("[\\u00A0\\u202F\\u2009\\u2007\\u200B]"), " ")
        .replace("\r", "")

    private fun currencyOf(token: String): LedgerCurrency? = when (token.trim().trimEnd('.').uppercase(Locale.ROOT)) {
        "USD", "$" -> LedgerCurrency.USD
        "EUR", "€" -> LedgerCurrency.EUR
        "GBP", "£" -> LedgerCurrency.GBP
        "PLN", "ZŁ", "ZL" -> LedgerCurrency.PLN
        "UAH", "₴", "ГРН", "ГРИВЕНЬ", "ГРИВНІ", "ГРИВНЯ" -> LedgerCurrency.UAH
        else -> null
    }

    /** "1 200,50" "1,200.50" "1200.5" "1.200,50" -> minor units, or null. */
    internal fun minorOf(raw: String): Long? {
        var value = raw.replace(" ", "")
        val lastComma = value.lastIndexOf(','); val lastDot = value.lastIndexOf('.')
        value = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) value.replace(".", "").replace(',', '.') else value.replace(",", "")
            lastComma >= 0 || lastDot >= 0 -> {
                val sep = if (lastComma >= 0) ',' else '.'
                val parts = value.split(sep)
                if (parts.size > 2) value.replace(sep.toString(), "")
                else if (parts[1].length == 3 && parts[0].length in 1..3 && parts[0] != "0") parts[0] + parts[1]
                else parts[0] + "." + parts[1]
            }
            else -> value
        }
        return try { BigDecimal(value).abs().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() } catch (_: Exception) {
            try { BigDecimal(value).abs().setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact() } catch (_: Exception) { null }
        }
    }

    private fun hits(text: String): List<Hit> {
        val found = mutableListOf<Hit>()
        suffixAmount.findAll(text).forEach { m ->
            val minor = minorOf(m.groupValues[2]) ?: return@forEach
            val currency = currencyOf(m.groupValues[3]) ?: return@forEach
            found += Hit(m.range, minor, currency, m.groupValues[1].firstOrNull())
        }
        prefixAmount.findAll(text).forEach { m ->
            if (found.any { it.range.first <= m.range.last && m.range.first <= it.range.last }) return@forEach
            val minor = minorOf(m.groupValues[3]) ?: return@forEach
            val currency = currencyOf(m.groupValues[1]) ?: return@forEach
            found += Hit(m.range, minor, currency, m.groupValues[2].firstOrNull())
        }
        return found.sortedBy { it.range.first }.map { hit ->
            // The sign may sit just before the currency token ("$-12.50") or before a gap ("- 100").
            if (hit.sign != null) hit else {
                val before = text.substring(maxOf(0, hit.range.first - 2), hit.range.first).trimEnd()
                hit.copy(sign = before.lastOrNull()?.takeIf { it == '-' || it == '+' })
            }
        }
    }

    fun parse(bank: Bank, rawText: String, receivedAt: Long, zone: ZoneId = ZoneId.systemDefault()): ParsedBankOperation? {
        if (rawText.length > 8000) return null
        val text = normalize(rawText)
        if (otp.containsMatchIn(text) || declined.containsMatchIn(text)) return null

        val primary = hits(text).filter { hit ->
            val prefix = text.substring(maxOf(0, hit.range.first - 28), hit.range.first)
            !nonTransactionAmount.containsMatchIn(prefix)
        }
        val first = primary.firstOrNull() ?: return null
        // Other amounts in the same currency are ambiguous; other currencies are conversions of the same payment.
        val ambiguous = primary.drop(1).any { it.currency == first.currency && it.minor != first.minor }
        if (first.minor <= 0 || first.minor > Money.MAX_ENTRY_MINOR) return null

        val isRefund = refund.containsMatchIn(text)
        val debit = expense.containsMatchIn(text) && !isRefund
        val credit = income.containsMatchIn(text) || isRefund
        val moving = transfer.containsMatchIn(text)
        val signed = first.sign
        if (!debit && !credit && !moving && signed == null) return null
        if (promo.containsMatchIn(text) && signed == null && !debit && !credit) return null

        var directionKnown = true
        val type: EntryType = when {
            signed == '+' -> EntryType.INCOME
            signed == '-' -> EntryType.EXPENSE
            isRefund -> EntryType.INCOME
            credit && !debit -> EntryType.INCOME
            debit && !credit -> EntryType.EXPENSE
            debit && credit -> { directionKnown = false; if ((income.find(text)?.range?.first ?: Int.MAX_VALUE) < (expense.find(text)?.range?.first ?: Int.MAX_VALUE)) EntryType.INCOME else EntryType.EXPENSE }
            moving && transferIn.containsMatchIn(text) && !transferOut.containsMatchIn(text) -> EntryType.INCOME
            moving && transferOut.containsMatchIn(text) && !transferIn.containsMatchIn(text) -> EntryType.EXPENSE
            else -> { directionKnown = false; EntryType.EXPENSE }
        }

        val shop = merchant(bank, text, first)
        val timestamp = timestamp(text, receivedAt, zone)
        val category = when {
            moving -> Category.TRANSFER
            type == EntryType.INCOME && Regex("зарплат|salary", RegexOption.IGNORE_CASE).containsMatchIn(text) -> Category.SALARY
            else -> categorize(shop)
        }
        return ParsedBankOperation(
            bank.id, first.minor, first.currency, type, shop, card.find(text)?.groupValues?.getOrNull(1),
            timestamp ?: receivedAt, timestamp != null, moving, directionKnown && !ambiguous, category,
        )
    }

    private fun timestamp(text: String, receivedAt: Long, zone: ZoneId): Long? {
        dateTime.find(text)?.let { match ->
            try {
                val date = LocalDate.parse(match.groupValues[1].replace('/', '.'), DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT))
                val clock = LocalTime.parse(match.groupValues[2])
                return date.atTime(clock).atZone(zone).toInstant().toEpochMilli().takeIf { it <= receivedAt + 300_000L && it >= receivedAt - 366L * 86400000L }
            } catch (_: Exception) { return null }
        }
        // "HH:mm" alone: take today's date if that moment is not in the future (otherwise yesterday).
        clockOnly.find(text)?.let { match ->
            try {
                val clock = LocalTime.parse(match.groupValues[1].padStart(5, '0'))
                val today = Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
                var moment = today.atTime(clock).atZone(zone).toInstant().toEpochMilli()
                if (moment > receivedAt + 300_000L) moment -= 86_400_000L
                return moment.takeIf { receivedAt - moment <= 6 * 3_600_000L }
            } catch (_: Exception) { return null }
        }
        return null
    }

    private fun clean(value: String): String {
        var out = value.replace(emoji, " ")
        out = out.replace(trailer, "")
        out = out.trim(' ', ':', '-', '+', '.', ',', '"', '\'', '«', '»', '(', ')', '|')
        return out.replace(Regex("\\s{2,}"), " ").take(60)
    }

    private fun merchant(bank: Bank, text: String, first: Hit): String {
        label.find(text)?.groupValues?.getOrNull(1)?.let(::clean)?.takeIf { it.length >= 2 }?.let { return it }
        val badStart = Regex("^(?:карт|card|рахунок|счет|account|[0-9]|[＊*•])", RegexOption.IGNORE_CASE)
        val after = text.substring(minOf(text.length, first.range.last + 1))
        preposition.findAll(after).map { clean(it.groupValues[1]) }
            .firstOrNull { it.length >= 2 && !badStart.containsMatchIn(it) && !genericTitle.matches(it) }?.let { return it }
        preposition.findAll(text).map { clean(it.groupValues[1]) }
            .firstOrNull { it.length >= 2 && !badStart.containsMatchIn(it) && !genericTitle.matches(it) }?.let { return it }
        // Many apps put the shop into the notification title (first line) and the amount into the body.
        val noAmounts = suffixAmount.replace(prefixAmount.replace(text, ""), "")
        val keywords = Regex("\\p{L}*(?:${expense.pattern}|${income.pattern}|${transfer.pattern})\\p{L}*", RegexOption.IGNORE_CASE)
        return noAmounts.lines().asSequence()
            .map { line -> clean(line.replace(card, " ").replace(keywords, " ")) }
            .firstOrNull { line ->
                line.length in 2..60 && !line.equals(bank.name, ignoreCase = true) && !genericTitle.matches(line) &&
                    !nonTransactionAmount.containsMatchIn(line) && !line.all { it.isDigit() || it.isWhitespace() || it in ".,:*-" } &&
                    !Regex("^(?:баланс|залишок|остаток|balance|bal)", RegexOption.IGNORE_CASE).containsMatchIn(line)
            }.orEmpty()
    }

    fun categorize(merchant: String): Category {
        val value = merchant.lowercase(Locale.ROOT)
        return when {
            listOf("атб", "atb", "сільпо", "silpo", "ашан", "auchan", "novus", "метро", "metro", "varus", "варус", "фора", "fora", "lidl", "biedronka", "продукт", "супермаркет", "grocery", "market", "маркет", "ева ", "eva ").any(value::contains) -> Category.FOOD
            listOf("wog", "okko", "ugo", "uber", "bolt", "uklon", "укрзалізниц", "ukrzaliznytsia", "азс", "shell", "socar", "паливо", "taxi", "таксі").any(value::contains) -> Category.TRANSPORT
            listOf("steam", "megogo", "netflix", "spotify", "cinema", "кіно", "youtube", "playstation", "xbox", "multiplex").any(value::contains) -> Category.LEISURE
            listOf("аптек", "pharmacy", "clinic", "клінік", "подорожник", "анц", "лікарн").any(value::contains) -> Category.HEALTH
            listOf("restaurant", "ресторан", "кафе", "cafe", "mcdonald", "kfc", "pizza", "піц", "sushi", "суші", "coffee", "кав", "starbucks", "burger", "бургер").any(value::contains) -> Category.DINING
            listOf("епіцентр", "epicentr", "jysk", "ikea", "комунал", "жкг", "kyivstar", "київстар", "vodafone", "lifecell", "інтернет").any(value::contains) -> Category.HOME
            else -> Category.OTHER
        }
    }
}
```

## domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccess.kt

```kotlin
package com.semmyx.ledger.domain.bank

/** Enforced before any bank statistics query, including after entitlement expires. */
object BankPeriodAccess {
    val periods = setOf("DAY", "WEEK", "MONTH", "ALL")
    fun effective(requested: String, premium: Boolean): String =
        if (requested in periods && (requested == "DAY" || premium)) requested else "DAY"
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
/** [error] is a code: BAD_TOKEN, RATE_LIMIT, NETWORK, NO_ACCOUNTS (or null). */
data class MonobankStatus(val connected: Boolean = false, val syncing: Boolean = false, val lastSyncMillis: Long = 0,
    val lastImported: Int = 0, val accounts: Int = 0, val error: String? = null)
interface BankRepository {
    /** "API" (monobank token), "SMS" or "NOTIFY" (bank app notifications). */
    var importMode: String
    val privatStatus: Flow<MonobankStatus>
    suspend fun connectPrivat(token: String): Boolean
    suspend fun disconnectPrivat()
    suspend fun syncPrivat(): Int
    val monobankStatus: Flow<MonobankStatus>
    suspend fun connectMonobank(token: String): Boolean
    suspend fun disconnectMonobank()
    suspend fun syncMonobank(): Int
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

## domain/src/main/kotlin/com/semmyx/ledger/domain/bank/MonobankMapper.kt

```kotlin
package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*

/** One row of monobank's personal `statement` API ([amount] is signed minor units of the account currency). */
data class MonobankStatementItem(
    val id: String, val timeSeconds: Long, val description: String, val mcc: Int,
    val amount: Long, val comment: String = "",
)

/** Pure mapping of monobank API rows to ledger operations. No network, no Android. */
object MonobankMapper {
    fun currency(code: Int): LedgerCurrency? = when (code) {
        980 -> LedgerCurrency.UAH; 840 -> LedgerCurrency.USD; 978 -> LedgerCurrency.EUR
        826 -> LedgerCurrency.GBP; 985 -> LedgerCurrency.PLN; else -> null
    }

    fun map(item: MonobankStatementItem, accountCurrency: LedgerCurrency, cardLast4: String?): ParsedBankOperation? {
        if (item.id.isBlank() || item.amount == 0L) return null
        val minor = Math.abs(item.amount)
        if (minor > Money.MAX_ENTRY_MINOR) return null
        val description = item.description.replace(Regex("\\s+"), " ").trim().take(60)
        val transfer = item.mcc == 4829 || description.startsWith("Від:", true) || description.startsWith("До:", true) ||
            description.startsWith("From:", true) || description.startsWith("To:", true)
        val type = if (item.amount > 0) EntryType.INCOME else EntryType.EXPENSE
        val category = when {
            transfer -> Category.TRANSFER
            else -> byMcc(item.mcc) ?: BankParser.categorize(description)
        }
        return ParsedBankOperation(
            bankId = "mono", amountMinor = minor, currency = accountCurrency, type = type, merchant = description,
            cardLast4 = cardLast4, happenedAtMillis = item.timeSeconds * 1000L, timeFromMessage = true,
            transfer = transfer, confident = true, category = category,
        )
    }

    /** Merchant category codes (ISO 18245) -> app categories; unknown codes fall back to name matching. */
    fun byMcc(mcc: Int): Category? = when (mcc) {
        5411, 5422, 5441, 5451, 5462, 5499, 5300, 5311 -> Category.FOOD
        5811, 5812, 5813, 5814 -> Category.DINING
        4111, 4112, 4121, 4131, 4784, 5541, 5542, 7523, 7512 -> Category.TRANSPORT
        5912, 5122, 8011, 8021, 8031, 8041, 8042, 8043, 8049, 8050, 8062, 8071, 8099 -> Category.HEALTH
        7832, 7841, 7922, 7929, 7932, 7933, 7941, 7991, 7992, 7993, 7994, 7995, 7996, 7997, 7998, 7999, 5815, 5816, 5817, 5818, 5735 -> Category.LEISURE
        4900, 4814, 4899, 5200, 5211, 5251, 5712, 5713, 5719, 7349 -> Category.HOME
        else -> null
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
        assertTrue(TrialPolicy.validDays(14)); assertEquals(14, TrialPolicy.DAYS)
        assertFalse(TrialPolicy.validDays(7)); assertFalse(TrialPolicy.validDays(30)); assertFalse(TrialPolicy.validDays(0))
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

## domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankParserTest.kt

```kotlin
package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.Category
import com.semmyx.ledger.domain.EntryType
import com.semmyx.ledger.domain.LedgerCurrency
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

    private fun parse(text: String, bankId: String = "mono") = BankParser.parse(Banks.all.first { it.id == bankId }, text, at, ZoneId.of("Europe/Kyiv"))

    // ---- Realistic push formats: title on the first line, body below (as the listener builds them) ----
    @Test fun monoUnicodeMinusWithMerchantInTitle() {
        val p = parse("Сільпо\n−1 234,56 ₴ · Залишок 10 000,00 ₴")!!
        assertEquals(123456L, p.amountMinor); assertEquals(EntryType.EXPENSE, p.type)
        assertEquals("Сільпо", p.merchant); assertEquals(Category.FOOD, p.category); assertTrue(p.confident)
    }
    @Test fun monoIncomePlusSign() {
        val p = parse("Іван П.\n+500,00 ₴ Баланс 1 500,00 ₴")!!
        assertEquals(50000L, p.amountMinor); assertEquals(EntryType.INCOME, p.type); assertTrue(p.confident)
    }
    @Test fun privatPurchaseWithGluedCurrencyAndBalance() {
        val p = parse("Оплата 150.50UAH Картка *4455 WOG Баланс 2000.00UAH", "privat")!!
        assertEquals(15050L, p.amountMinor); assertEquals("4455", p.cardLast4); assertEquals(EntryType.EXPENSE, p.type)
        assertEquals(Category.TRANSPORT, p.category); assertTrue(p.confident)
    }
    @Test fun transliteratedPrivatSms() {
        val p = parse("Spysannya 99.00UAH. Karta *1111 Netflix. Bal. 500.00UAH", "privat")!!
        assertEquals(9900L, p.amountMinor); assertEquals(EntryType.EXPENSE, p.type)
    }
    @Test fun incomeKeywordTransliterated() {
        assertEquals(EntryType.INCOME, parse("Zarakhuvannya 2000.00UAH na kartku *1234", "privat")!!.type)
    }
    @Test fun titleIsGenericSoMerchantComesFromBody() {
        val p = parse("Оплата\n320 грн, магазин: Аптека Доброго Дня")!!
        assertEquals(32000L, p.amountMinor); assertEquals(Category.HEALTH, p.category)
    }
    @Test fun merchantMissingStillImportsWithBlankMerchant() {
        val p = parse("Списано 77,00 грн")!!
        assertEquals(7700L, p.amountMinor); assertTrue(p.confident); assertEquals("", p.merchant)
    }
    @Test fun conversionAmountIsNotSecondPurchase() {
        val p = parse("Оплата 10.00 USD (412,50 грн) у Steam")!!
        assertEquals(1000L, p.amountMinor); assertEquals(LedgerCurrency.USD, p.currency); assertTrue(p.confident)
    }
    @Test fun twoDifferentAmountsAreNotConfident() {
        assertFalse(parse("Оплата 100 грн та 250 грн у АТБ")!!.confident)
    }
    @Test fun refundIsIncome() { assertEquals(EntryType.INCOME, parse("Повернення оплати 250 грн АТБ")!!.type) }
    @Test fun transferDirections() {
        assertEquals(EntryType.INCOME, parse("Переказ від Олена К. 300 грн")!!.type)
        assertEquals(EntryType.EXPENSE, parse("Переказ на картку 300 грн")!!.type)
        assertTrue(parse("Переказ на картку 300 грн")!!.transfer)
    }
    @Test fun adsAndCashbackOnlyAreIgnored() {
        assertNull(parse("Кредит до 50 000 грн за акцією!"))
        assertNull(parse("Кешбек 15 грн нараховано"))
        assertNull(parse("Ваш ліміт 20 000 грн"))
        assertNull(parse("Hello! Your card is ready"))
    }
    @Test fun thousandSeparators() {
        assertEquals(120050L, parse("Оплата 1,200.50 грн")!!.amountMinor)
        assertEquals(120050L, parse("Оплата 1.200,50 грн")!!.amountMinor)
        assertEquals(1200000L, parse("Оплата 12 000 грн")!!.amountMinor)
    }
    @Test fun clockOnlyUsesTodayNotFuture() {
        val p = parse("Оплата 10 грн Кава 22:50")!!
        assertTrue(p.timeFromMessage)
        assertTrue(p.happenedAtMillis <= at + 300_000L)
    }
    @Test fun walletAndBankPackagesAreRecognised() {
        assertEquals("privat", Banks.byPackage("ua.privatbank.ap24")?.id)
        assertEquals("mono", Banks.byPackage("com.ftband.mono")?.id)
        assertEquals("wallet", Banks.byPackage("com.google.android.apps.walletnfcrel")?.id)
        assertEquals("sense", Banks.byPackage("ua.alfabank.mobile.android.beta")?.id)
        assertNull(Banks.byPackage("org.telegram.messenger"))
        assertNull(Banks.byPackage("com.whatsapp"))
    }

    @Test fun senderMatchingIsLenient() {
        assertEquals("privat", Banks.bySender("PRIVATBANK")?.id)
        assertEquals("privat", Banks.bySender("Privat24")?.id)
        assertEquals("mono", Banks.bySender("monobank")?.id)
        assertEquals("sense", Banks.bySender("SENSE BANK")?.id)
        assertNull(Banks.bySender("+380501234567"))
        assertNull(Banks.bySender("Mom"))
    }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccessTest.kt

```kotlin
package com.semmyx.ledger.domain.bank
import org.junit.Assert.*
import org.junit.Test
class BankPeriodAccessTest {
    @Test fun freeOnlyTodayAndNoAllTimeBypass() {
        BankPeriodAccess.periods.forEach { assertEquals("DAY", BankPeriodAccess.effective(it, false)) }
    }
    @Test fun premiumAndTrialCanUseEveryPeriod() {
        BankPeriodAccess.periods.forEach { assertEquals(it, BankPeriodAccess.effective(it, true)) }
    }
    @Test fun expiredAccessFallsBackAndInvalidPeriodIsRejected() {
        assertEquals("DAY", BankPeriodAccess.effective("MONTH", false))
        assertEquals("DAY", BankPeriodAccess.effective("INVALID", true))
    }
}
```

## domain/src/test/kotlin/com/semmyx/ledger/domain/bank/MonobankMapperTest.kt

```kotlin
package com.semmyx.ledger.domain.bank

import com.semmyx.ledger.domain.*
import org.junit.Assert.*
import org.junit.Test

class MonobankMapperTest {
    @Test fun expenseUsesMccAndSignedAmount() {
        val op = MonobankMapper.map(MonobankStatementItem("a1", 1_700_000_000, "Сільпо", 5411, -12550), LedgerCurrency.UAH, "1234")!!
        assertEquals(12550L, op.amountMinor); assertEquals(EntryType.EXPENSE, op.type)
        assertEquals(Category.FOOD, op.category); assertEquals("1234", op.cardLast4)
        assertEquals(1_700_000_000_000L, op.happenedAtMillis); assertTrue(op.confident)
    }
    @Test fun incomeAndTransfer() {
        val op = MonobankMapper.map(MonobankStatementItem("a2", 1, "Від: Іван П.", 4829, 50000), LedgerCurrency.UAH, null)!!
        assertEquals(EntryType.INCOME, op.type); assertTrue(op.transfer); assertEquals(Category.TRANSFER, op.category)
    }
    @Test fun unknownMccFallsBackToName() {
        assertEquals(Category.TRANSPORT, MonobankMapper.map(MonobankStatementItem("a3", 1, "WOG", 0, -100), LedgerCurrency.UAH, null)!!.category)
    }
    @Test fun zeroAndBlankAreSkipped() {
        assertNull(MonobankMapper.map(MonobankStatementItem("a4", 1, "x", 0, 0), LedgerCurrency.UAH, null))
        assertNull(MonobankMapper.map(MonobankStatementItem("", 1, "x", 0, -5), LedgerCurrency.UAH, null))
    }
    @Test fun currencyCodes() {
        assertEquals(LedgerCurrency.UAH, MonobankMapper.currency(980)); assertEquals(LedgerCurrency.USD, MonobankMapper.currency(840))
        assertNull(MonobankMapper.currency(392))
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

# Enabled parallel sync for Gradle 9.4+
org.gradle.tooling.parallel=true
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

## gradle/gradle-daemon-jvm.properties

```properties
#This file is generated by updateDaemonJvm
toolchainUrl.FREE_BSD.AARCH64=https\://api.foojay.io/disco/v3.0/ids/cf726b4a1c84b50457225f9bba6d7650/redirect
toolchainUrl.FREE_BSD.X86_64=https\://api.foojay.io/disco/v3.0/ids/fa1e318c287360478e3c83a9a3ef1007/redirect
toolchainUrl.LINUX.AARCH64=https\://api.foojay.io/disco/v3.0/ids/cf726b4a1c84b50457225f9bba6d7650/redirect
toolchainUrl.LINUX.X86_64=https\://api.foojay.io/disco/v3.0/ids/fa1e318c287360478e3c83a9a3ef1007/redirect
toolchainUrl.MAC_OS.AARCH64=https\://api.foojay.io/disco/v3.0/ids/c2dd35c9d0aaf0ba6ad0791320f99dfc/redirect
toolchainUrl.MAC_OS.X86_64=https\://api.foojay.io/disco/v3.0/ids/e5810bd7fd1f8a586644409d395a7e55/redirect
toolchainUrl.UNIX.AARCH64=https\://api.foojay.io/disco/v3.0/ids/cf726b4a1c84b50457225f9bba6d7650/redirect
toolchainUrl.UNIX.X86_64=https\://api.foojay.io/disco/v3.0/ids/fa1e318c287360478e3c83a9a3ef1007/redirect
toolchainUrl.WINDOWS.AARCH64=https\://api.foojay.io/disco/v3.0/ids/7b3c4877c0749019e6805bb61e421497/redirect
toolchainUrl.WINDOWS.X86_64=https\://api.foojay.io/disco/v3.0/ids/d76df094a9cbbabd3b08251f9e61444a/redirect
toolchainVersion=25
```

## gradle/libs.versions.toml

```toml
[versions]
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
appcompat = "1.6.1"
material = "1.10.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }

[plugins]
```

## gradle/wrapper/gradle-wrapper.properties

```properties
#Fri Oct 09 23:39:28 EEST 2026
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.0-bin.zip
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
include(":bank-fixture")
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
    if path.startswith('app/src/main/') and path.endswith('ViewModel.kt'):
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

entities = (ROOT/'data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerEntities.kt').read_text()
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

## tools/export_sources.py

```python
#!/usr/bin/env python3
"""Regenerate the source book, project tree and SHA-256 manifest from tracked files.
Never includes .git, ignored local credentials, build outputs or developer private keys.
Run after staging new files; repeat after changing the release verification report.
"""
from pathlib import Path
import hashlib
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
tracked = sorted(set(subprocess.check_output(['git', 'ls-files'], cwd=ROOT, text=True).splitlines()))
version = re.search(r'versionName = "([^"]+)"', (ROOT / 'app/build.gradle.kts').read_text())[1]
langs = {'.kt':'kotlin', '.kts':'kotlin', '.java':'java', '.py':'python', '.xml':'xml',
         '.yml':'yaml', '.yaml':'yaml', '.properties':'properties', '.ps1':'powershell',
         '.bat':'batch', '.toml':'toml', '.json':'json'}
source = [p for p in tracked if Path(p).suffix in langs or p == 'gradlew']
book = [f'# TG Ledger {version} — complete file-by-file source', '',
        f'{len(source)} implementation, configuration and test files, without truncation.', '',
        'Generated from tracked files. Local keys and build outputs are excluded.', '']
for name in source:
    text = (ROOT / name).read_text().rstrip('\n')
    # Fences must be longer than any backtick run inside the original file.
    fence = '`' * max(3, max((len(m[0]) + 1 for m in re.finditer(r'`+', text)), default=3))
    book.extend([f'## {name}', '', fence + langs.get(Path(name).suffix, 'bash'), text, fence, ''])
(ROOT / 'SOURCE_CODE.md').write_text('\n'.join(book))
files = sorted(set(tracked) | {'SOURCE_CODE.md', 'PROJECT_TREE.txt', 'MANIFEST.sha256'})
(ROOT / 'PROJECT_TREE.txt').write_text(f'TG_Ledger-{version}/\n' + '\n'.join('  '+p for p in files)+'\n')
(ROOT / 'MANIFEST.sha256').write_text(''.join(
    f'{hashlib.sha256((ROOT/name).read_bytes()).hexdigest()}  {name}\n'
    for name in files if name != 'MANIFEST.sha256'))
print(f'Source book: {len(source)} files; manifest: {len(files)-1} files')
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
s=(R/'data/src/main/kotlin/com/semmyx/ledger/data/bank/BankEntities.kt').read_text()
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
root=(A/'navigation/AppRoot.kt').read_text()
analytics=(A/'analytics/AnalyticsScreen.kt').read_text()
bank=(A/'bank/BankScreen.kt').read_text()
repo=(R/'data/src/main/kotlin/com/semmyx/ledger/data/bank/BankImportRepository.kt').read_text()
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

## verification/bank-regex-jvm.json

```json
{
  "kind": "actual-source-regex-on-JVM",
  "passed_fixtures": 12,
  "compiled_kotlin": false,
  "executed_android": false
}
```

## verification/bank-sql.json

```json
{
  "kind": "exact-migration-and-Room-bank-SQL-in-SQLite",
  "passed": 10,
  "checks": [
    "Migration preserves entries and does not invent cash/card attribution",
    "All six metadata columns migrated",
    "Source totals exclude income, deleted and other currencies",
    "Mono is isolated from Privat and cash",
    "Source category filter uses chosen bank",
    "Custom categories retain their own identity",
    "Unique fingerprint prevents repeated delivery imports",
    "Near duplicates are detected for review",
    "Different banks do not deduplicate each other",
    "Clearing review history preserves ledger entries"
  ],
  "compiled_android": false
}
```

## verification/cat-skin-assets.json

```json
{
  "dimensions": [
    1024,
    1024
  ],
  "changed_pixels": 440799,
  "eye_nose_pixels_preserved": true,
  "dark_background_preserved": true,
  "android_render_verified": false
}
```

## verification/navigation-recovery.json

```json
{
  "kind": "source-regression-guards",
  "passed": 10,
  "checks": [
    "Explicit real Activity owners are provided above localized content",
    "Registry owner is installed before the localized Context replacement",
    "History button no longer invokes dashboard back action",
    "History has an independent navigation destination",
    "Analytics selected month is passed to history",
    "System settings launched safely from localized non-Activity Context",
    "Local text recovery has a preview and explicit submit action",
    "Manual recovery works without automation and never bypasses confirmation",
    "Live import diagnostics are visible in the banking screen",
    "Reconnect and bank package configuration are provided"
  ],
  "compiled_android": false,
  "device_crash_fixed_verified": false
}
```

## verification/release-3.3.3.json

```json
{
  "version": "3.3.3",
  "version_code": 17,
  "tested_code_commit": "56f60e981437e389e992d2901b3cf7adbd63cb36",
  "ci_url": "https://github.com/semmyx666/TG_Ledger/actions/runs/38578995362",
  "jobs": [
    {
      "name": "instrumented (36)",
      "conclusion": "success",
      "started_at": "2026-10-14T14:16:12Z",
      "completed_at": "2026-10-14T14:24:14Z"
    },
    {
      "name": "instrumented (26)",
      "conclusion": "success",
      "started_at": "2026-10-14T14:16:12Z",
      "completed_at": "2026-10-14T14:23:49Z"
    },
    {
      "name": "build",
      "conclusion": "success",
      "started_at": "2026-10-14T14:16:12Z",
      "completed_at": "2026-10-14T14:25:18Z"
    }
  ],
  "unit_executions": {
    "domain": 53,
    "app_play": 25,
    "data_play": 18,
    "data_legacy_v7": 18
  },
  "instrumented": {
    "api26": {
      "app": 14,
      "data": 6,
      "failed": 0,
      "skipped": 0
    },
    "api36": {
      "app": 14,
      "data": 6,
      "failed": 0,
      "skipped": 0
    }
  },
  "native_widths_dp": [
    288,
    320,
    360
  ],
  "font_scales": [
    1.0,
    1.4,
    1.7
  ],
  "live_bank_accounts_tested": false,
  "physical_user_phone_tested": false,
  "packaging_changes_after_tests": "Documentation, generated source book, manifest and test artifacts only; application code unchanged."
}
```

## verification/source-audit.json

```json
{
  "kind": "offline-structural-resource-and-sql-audit",
  "passed_assertions": 2581,
  "failures": [],
  "kotlin_source_files": 135,
  "localized_string_pairs": 397,
  "compiled_android": false,
  "executed_android_or_junit_tests": false,
  "checks": [
    "Well-formed XML: bank-fixture/src/main/AndroidManifest.xml",
    "Well-formed XML: data/src/main/AndroidManifest.xml",
    "Well-formed XML: app/src/main/AndroidManifest.xml",
    "Well-formed XML: app/src/main/res/drawable/brand_sky.xml",
    "Well-formed XML: app/src/main/res/drawable/ic_launcher_background.xml",
    "Well-formed XML: app/src/main/res/drawable/ic_launcher_foreground.xml",
    "Well-formed XML: app/src/main/res/drawable/ic_splash.xml",
    "Well-formed XML: app/src/main/res/drawable/mascot_gray.xml",
    "Well-formed XML: app/src/main/res/drawable/mascot_sky.xml",
    "Well-formed XML: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "Well-formed XML: app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "Well-formed XML: app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml",
    "Well-formed XML: app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml",
    "Well-formed XML: app/src/main/res/values-night/themes.xml",
    "Well-formed XML: app/src/main/res/values-uk/strings.xml",
    "Well-formed XML: app/src/main/res/values/branding.xml",
    "Well-formed XML: app/src/main/res/values/colors.xml",
    "Well-formed XML: app/src/main/res/values/strings.xml",
    "Well-formed XML: app/src/main/res/values/themes.xml",
    "Well-formed XML: app/src/main/res/xml/backup_rules.xml",
    "Well-formed XML: app/src/main/res/xml/data_extraction_rules.xml",
    "Well-formed XML: app/src/main/res/xml/network_security_config.xml",
    "No duplicate string: values/brand_author_signature",
    "No duplicate string: values/brand_copyright",
    "No duplicate string: values/app_name",
    "No duplicate string: values/overview",
    "No duplicate string: values/settings",
    "No duplicate string: values/help",
    "No duplicate string: values/back",
    "No duplicate string: values/close",
    "No duplicate string: values/retry",
    "No duplicate string: values/next",
    "No duplicate string: values/skip",
    "No duplicate string: values/get_started",
    "No duplicate string: values/page_of",
    "No duplicate string: values/onboard_title_one",
    "No duplicate string: values/onboard_body_one",
    "No duplicate string: values/onboard_title_two",
    "No duplicate string: values/onboard_body_two",
    "No duplicate string: values/onboard_title_three",
    "No duplicate string: values/onboard_body_three",
    "No duplicate string: values/unlock_title",
    "No duplicate string: values/confirm_pin",
    "No duplicate string: values/create_pin",
    "No duplicate string: values/pin_setup_subtitle",
    "No duplicate string: values/pin_unlock_subtitle",
    "No duplicate string: values/pin_digit_count",
    "No duplicate string: values/pin_locked",
    "No duplicate string: values/pin_incorrect",
    "No duplicate string: values/pin_mismatch",
    "No duplicate string: values/pin_recovery_notice",
    "No duplicate string: values/erase_digit",
    "No duplicate string: values/start_over",
    "No duplicate string: values/need_help",
    "No duplicate string: values/security_error",
    "No duplicate string: values/biometric_failed",
    "No duplicate string: values/unlock_biometric",
    "No duplicate string: values/biometric_title",
    "No duplicate string: values/biometric_subtitle",
    "No duplicate string: values/use_pin",
    "No duplicate string: values/load_error_title",
    "No duplicate string: values/storage_error",
    "No duplicate string: values/saved",
    "No duplicate string: values/entry_deleted",
    "No duplicate string: values/undo",
    "No duplicate string: values/undo_expired",
    "No duplicate string: values/dashboard_heading",
    "No duplicate string: values/greeting",
    "No duplicate string: values/dashboard_subtitle",
    "No duplicate string: values/total_balance",
    "No duplicate string: values/currency_scope",
    "No duplicate string: values/income",
    "No duplicate string: values/expenses",
    "No duplicate string: values/expense",
    "No duplicate string: values/cash_flow",
    "No duplicate string: values/last_7_days",
    "No duplicate string: values/last_30_days",
    "No duplicate string: values/last_30_days_plus",
    "No duplicate string: values/chart_description",
    "No duplicate string: values/chart_empty",
    "No duplicate string: values/legend_income",
    "No duplicate string: values/legend_expense",
    "No duplicate string: values/activity",
    "No duplicate string: values/search_entries",
    "No duplicate string: values/clear_search",
    "No duplicate string: values/all",
    "No duplicate string: values/search_scope",
    "No duplicate string: values/no_matches",
    "No duplicate string: values/no_matches_body",
    "No duplicate string: values/clear_filters",
    "No duplicate string: values/empty_ledger_title",
    "No duplicate string: values/empty_ledger_body",
    "No duplicate string: values/add_first_entry",
    "No duplicate string: values/add_entry",
    "No duplicate string: values/edit_entry",
    "No duplicate string: values/delete_entry",
    "No duplicate string: values/delete_named_entry",
    "No duplicate string: values/load_more",
    "No duplicate string: values/positive_amount",
    "No duplicate string: values/negative_amount",
    "No duplicate string: values/category_food",
    "No duplicate string: values/category_transport",
    "No duplicate string: values/category_home",
    "No duplicate string: values/category_health",
    "No duplicate string: values/category_leisure",
    "No duplicate string: values/category_salary",
    "No duplicate string: values/category_other",
    "No duplicate string: values/entry_currency_notice",
    "No duplicate string: values/amount",
    "No duplicate string: values/amount_invalid",
    "No duplicate string: values/title_optional_hint",
    "No duplicate string: values/entry_title",
    "No duplicate string: values/title_invalid",
    "No duplicate string: values/title_hint",
    "No duplicate string: values/category",
    "No duplicate string: values/date_invalid",
    "No duplicate string: values/note_optional",
    "No duplicate string: values/character_count",
    "No duplicate string: values/save_entry",
    "No duplicate string: values/entry_not_found",
    "No duplicate string: values/done",
    "No duplicate string: values/cancel",
    "No duplicate string: values/discard_title",
    "No duplicate string: values/discard_body",
    "No duplicate string: values/discard",
    "No duplicate string: values/keep_editing",
    "No duplicate string: values/profile",
    "No duplicate string: values/local_profile",
    "No duplicate string: values/display_name",
    "No duplicate string: values/save_profile",
    "No duplicate string: values/name_invalid",
    "No duplicate string: values/language",
    "No duplicate string: values/language_instant",
    "No duplicate string: values/language_en",
    "No duplicate string: values/language_uk",
    "No duplicate string: values/appearance",
    "No duplicate string: values/theme_system",
    "No duplicate string: values/theme_light",
    "No duplicate string: values/theme_dark",
    "No duplicate string: values/default_currency",
    "No duplicate string: values/currency_explanation",
    "No duplicate string: values/security",
    "No duplicate string: values/biometric_unlock",
    "No duplicate string: values/biometric_explanation",
    "No duplicate string: values/biometric_unavailable",
    "No duplicate string: values/auto_lock_explanation",
    "No duplicate string: values/lock_now",
    "No duplicate string: values/premium_name",
    "No duplicate string: values/premium_body",
    "No duplicate string: values/premium_active",
    "No duplicate string: values/premium_offline_notice",
    "No duplicate string: values/premium_unlock",
    "No duplicate string: values/premium_buy",
    "No duplicate string: values/restore_purchases",
    "No duplicate string: values/billing_configuration",
    "No duplicate string: values/billing_unavailable",
    "No duplicate string: values/billing_network",
    "No duplicate string: values/billing_pending",
    "No duplicate string: values/billing_verification",
    "No duplicate string: values/billing_acknowledgement",
    "No duplicate string: values/reference_rates",
    "No duplicate string: values/rates_disclaimer",
    "No duplicate string: values/rates_empty",
    "No duplicate string: values/rate_value",
    "No duplicate string: values/rates_as_of",
    "No duplicate string: values/refresh_rates",
    "No duplicate string: values/refreshing",
    "No duplicate string: values/rate_offline",
    "No duplicate string: values/support",
    "No duplicate string: values/support_intro",
    "No duplicate string: values/contact_telegram",
    "No duplicate string: values/diagnostics_show_button",
    "No duplicate string: values/diagnostics_none",
    "No duplicate string: values/diagnostics_title",
    "No duplicate string: values/diagnostics_hint",
    "No duplicate string: values/diagnostics_copy",
    "No duplicate string: values/diagnostics_copied",
    "No duplicate string: values/diagnostics_clear",
    "No duplicate string: values/diagnostics_close",
    "No duplicate string: values/here_to_help",
    "No duplicate string: values/lead_developer",
    "No duplicate string: values/support_handle",
    "No duplicate string: values/copy_handle",
    "No duplicate string: values/handle_copied",
    "No duplicate string: values/support_privacy",
    "No duplicate string: values/telegram_support",
    "No duplicate string: values/support_network_error",
    "No duplicate string: values/help_heading",
    "No duplicate string: values/help_intro",
    "No duplicate string: values/help_entries_title",
    "No duplicate string: values/help_entries_body",
    "No duplicate string: values/help_privacy_title",
    "No duplicate string: values/help_privacy_body",
    "No duplicate string: values/help_security_title",
    "No duplicate string: values/help_security_body",
    "No duplicate string: values/help_premium_title",
    "No duplicate string: values/help_premium_body",
    "No duplicate string: values/app_version",
    "No duplicate string: values/premium_feature_trends",
    "No duplicate string: values/premium_feature_mascot",
    "No duplicate string: values/premium_feature_support",
    "No duplicate string: values/premium_source_play",
    "No duplicate string: values/premium_source_developer",
    "No duplicate string: values/premium_expires",
    "No duplicate string: values/premium_assistant_toggle",
    "No duplicate string: values/premium_assistant_toggle_hint",
    "No duplicate string: values/developer_purchase_title",
    "No duplicate string: values/developer_purchase_body",
    "No duplicate string: values/developer_purchase_banner",
    "No duplicate string: values/developer_purchase_note",
    "No duplicate string: values/developer_purchase_button",
    "No duplicate string: values/installation_id_label",
    "No duplicate string: values/installation_id_copy",
    "No duplicate string: values/installation_id_copied",
    "No duplicate string: values/activation_code_label",
    "No duplicate string: values/activation_code_hint",
    "No duplicate string: values/activation_code_button",
    "No duplicate string: values/billing_activation_code",
    "No duplicate string: values/premium_activated",
    "No duplicate string: values/activation_invalid",
    "No duplicate string: values/mascot_card_title",
    "No duplicate string: values/mascot_hear_again",
    "No duplicate string: values/mascot_premium_note",
    "No duplicate string: values/voice_assistant_button",
    "No duplicate string: values/voice_assistant_hint",
    "No duplicate string: values/voice_assistant_listening",
    "No duplicate string: values/voice_entry_added",
    "No duplicate string: values/voice_reply_balance",
    "No duplicate string: values/voice_reply_spent_today",
    "No duplicate string: values/voice_reply_opening_entry",
    "No duplicate string: values/voice_reply_not_understood",
    "No duplicate string: values/voice_smalltalk_greeting",
    "No duplicate string: values/voice_smalltalk_how_are_you",
    "No duplicate string: values/voice_smalltalk_thanks",
    "No duplicate string: values/voice_smalltalk_who_are_you",
    "No duplicate string: values/voice_smalltalk_bye",
    "No duplicate string: values/voice_smalltalk_compliment",
    "No duplicate string: values/voice_smalltalk_joke",
    "No duplicate string: values/voice_permission_denied",
    "No duplicate string: values/voice_recognizer_unavailable",
    "No duplicate string: values/all_time",
    "No duplicate string: values/categories",
    "No duplicate string: values/all_categories",
    "No duplicate string: values/category_transaction_count",
    "No duplicate string: values/category_empty",
    "No duplicate string: values/period_today",
    "No duplicate string: values/period_month",
    "No duplicate string: values/period_year",
    "No duplicate string: values/voice_statistics",
    "No duplicate string: values/assistant_text_hint",
    "No duplicate string: values/assistant_send",
    "No duplicate string: values/premium_source_trial",
    "No duplicate string: values/premium_local_notice",
    "No duplicate string: values/trial_explanation",
    "No duplicate string: values/trial_14",
    "No duplicate string: values/mascot_style",
    "No duplicate string: values/mascot_gray",
    "No duplicate string: values/mascot_sky",
    "No duplicate string: values/mascot_animations",
    "No duplicate string: values/brand_intro_title",
    "No duplicate string: values/brand_intro_body",
    "No duplicate string: values/brand_design_label",
    "No duplicate string: values/analytics_title",
    "No duplicate string: values/cinematic_history",
    "No duplicate string: values/cinematic_period",
    "No duplicate string: values/cinematic_apply",
    "No duplicate string: values/cinematic_more",
    "No duplicate string: values/cinematic_expenses_period",
    "No duplicate string: values/cinematic_comparison",
    "No duplicate string: values/cinematic_more_than_previous",
    "No duplicate string: values/cinematic_less_than_previous",
    "No duplicate string: values/cinematic_week_average",
    "No duplicate string: values/cinematic_month_average",
    "No duplicate string: values/cinematic_expense_categories",
    "No duplicate string: values/cinematic_income_categories",
    "No duplicate string: values/cinematic_transactions",
    "No duplicate string: values/cinematic_tooltip",
    "No duplicate string: values/cinematic_chart_accessibility",
    "No duplicate string: values/cinematic_more_buckets",
    "No duplicate string: values/cat_asset_status",
    "No duplicate string: values/fps_title",
    "No duplicate string: values/fps_explanation",
    "No duplicate string: values/fps_auto",
    "No duplicate string: values/fps_value",
    "No duplicate string: values/fps_supported",
    "No duplicate string: values/fps_fallback",
    "No duplicate string: values/cat_tap_hint",
    "No duplicate string: values/category_transfer",
    "No duplicate string: values/category_dining",
    "No duplicate string: values/category_cashback",
    "No duplicate string: values/reference_total_for",
    "No duplicate string: values/total_all_time",
    "No duplicate string: values/total_thirty_days",
    "No duplicate string: values/total_seven_days",
    "No duplicate string: values/colors_title",
    "No duplicate string: values/colors_explanation",
    "No duplicate string: values/colors_edit",
    "No duplicate string: values/colors_collapse",
    "No duplicate string: values/colors_categories",
    "No duplicate string: values/colors_reset_all",
    "No duplicate string: values/color_accent",
    "No duplicate string: values/color_background",
    "No duplicate string: values/color_surface",
    "No duplicate string: values/color_text",
    "No duplicate string: values/color_muted",
    "No duplicate string: values/color_chart_top",
    "No duplicate string: values/color_chart_bottom",
    "No duplicate string: values/color_grid",
    "No duplicate string: values/color_icons",
    "No duplicate string: values/color_hue",
    "No duplicate string: values/color_saturation",
    "No duplicate string: values/color_brightness",
    "No duplicate string: values/color_apply",
    "No duplicate string: values/color_reset",
    "No duplicate string: values/color_invalid",
    "No duplicate string: values/color_contrast_warning",
    "No duplicate string: values/color_error",
    "No duplicate string: values/bank_dashboard",
    "No duplicate string: values/bank_import_title",
    "No duplicate string: values/bank_consent",
    "No duplicate string: values/bank_enabled",
    "No duplicate string: values/bank_auto",
    "No duplicate string: values/bank_access_yes",
    "No duplicate string: values/bank_access_no",
    "No duplicate string: values/bank_notification_permission",
    "No duplicate string: values/bank_sms_yes",
    "No duplicate string: values/bank_sms_no",
    "No duplicate string: values/bank_sms_permission",
    "No duplicate string: values/bank_play_sms",
    "No duplicate string: values/bank_privacy",
    "No duplicate string: values/bank_privacy_body",
    "No duplicate string: values/bank_day",
    "No duplicate string: values/bank_week",
    "No duplicate string: values/bank_month",
    "No duplicate string: values/bank_cash",
    "No duplicate string: values/bank_card",
    "No duplicate string: values/bank_unknown",
    "No duplicate string: values/bank_source",
    "No duplicate string: values/bank_category_stats",
    "No duplicate string: values/bank_new_category",
    "No duplicate string: values/bank_review_count",
    "No duplicate string: values/bank_time_received",
    "No duplicate string: values/bank_duplicate",
    "No duplicate string: values/bank_transfer_review",
    "No duplicate string: values/bank_ignore",
    "No duplicate string: values/bank_recent",
    "No duplicate string: values/bank_limit_recent",
    "No duplicate string: values/bank_budget",
    "No duplicate string: values/bank_budget_progress",
    "No duplicate string: values/bank_export",
    "No duplicate string: values/bank_clear_history",
    "No duplicate string: values/bank_clear_warning",
    "No duplicate string: values/bank_app_permissions",
    "No duplicate string: values/bank_battery",
    "No duplicate string: values/bank_budget_disable",
    "No duplicate string: values/history_search",
    "No duplicate string: values/import_diagnostics",
    "No duplicate string: values/import_connected",
    "No duplicate string: values/import_disconnected",
    "No duplicate string: values/import_counts",
    "No duplicate string: values/import_reconnect",
    "No duplicate string: values/import_package_settings",
    "No duplicate string: values/import_package_warning",
    "No duplicate string: values/import_paste",
    "No duplicate string: values/import_text",
    "No duplicate string: values/import_manual_privacy",
    "No duplicate string: values/import_to_queue",
    "No duplicate string: values/import_queue_note",
    "No duplicate string: values/import_disabled",
    "No duplicate string: values/import_unsupported",
    "No duplicate string: values/import_empty",
    "No duplicate string: values/import_group",
    "No duplicate string: values/import_unrecognized",
    "No duplicate string: values/import_auto_done",
    "No duplicate string: values/import_queued",
    "No duplicate string: values/import_repeated",
    "No duplicate string: values/import_waiting",
    "No duplicate string: values/import_mode_title",
    "No duplicate string: values/import_mode_api",
    "No duplicate string: values/import_mode_sms",
    "No duplicate string: values/import_mode_notify",
    "No duplicate string: values/api_howto",
    "No duplicate string: values/api_open_site",
    "No duplicate string: values/api_token_label",
    "No duplicate string: values/api_connect",
    "No duplicate string: values/api_connecting",
    "No duplicate string: values/api_connected",
    "No duplicate string: values/api_last_sync",
    "No duplicate string: values/api_imported_last",
    "No duplicate string: values/api_syncing",
    "No duplicate string: values/api_sync_now",
    "No duplicate string: values/api_disconnect",
    "No duplicate string: values/api_error_bad_token",
    "No duplicate string: values/api_error_rate",
    "No duplicate string: values/api_error_network",
    "No duplicate string: values/api_error_no_accounts",
    "No duplicate string: values/sms_howto",
    "No duplicate string: values/sms_scan",
    "No duplicate string: values/sms_scanning",
    "No duplicate string: values/sms_scan_result",
    "No duplicate string: values/sms_restricted_tip",
    "No duplicate string: values/pay_source_title",
    "No duplicate string: values/pay_source_required",
    "No duplicate string: values/voice_ask_source",
    "No duplicate string: values/cat_locked_hint",
    "No duplicate string: values/bank_setup",
    "No duplicate string: values/push_howto",
    "No duplicate string: values/privat_api_title",
    "No duplicate string: values/privat_api_howto",
    "No duplicate string: values/privat_api_site",
    "No duplicate string: values/privat_api_token",
    "No duplicate string: values/privat_api_connected",
    "No duplicate string: values/privat_api_error",
    "No duplicate string: values/other_api_note",
    "No duplicate string: values/help_banks_title",
    "No duplicate string: values/help_banks_body",
    "No duplicate string: values/import_confirm_permission",
    "No duplicate string: values/import_confirm_enabled",
    "No duplicate string: values/assistant_write_short",
    "No duplicate string: values-uk/app_name",
    "No duplicate string: values-uk/overview",
    "No duplicate string: values-uk/settings",
    "No duplicate string: values-uk/help",
    "No duplicate string: values-uk/back",
    "No duplicate string: values-uk/close",
    "No duplicate string: values-uk/retry",
    "No duplicate string: values-uk/next",
    "No duplicate string: values-uk/skip",
    "No duplicate string: values-uk/get_started",
    "No duplicate string: values-uk/page_of",
    "No duplicate string: values-uk/onboard_title_one",
    "No duplicate string: values-uk/onboard_body_one",
    "No duplicate string: values-uk/onboard_title_two",
    "No duplicate string: values-uk/onboard_body_two",
    "No duplicate string: values-uk/onboard_title_three",
    "No duplicate string: values-uk/onboard_body_three",
    "No duplicate string: values-uk/unlock_title",
    "No duplicate string: values-uk/confirm_pin",
    "No duplicate string: values-uk/create_pin",
    "No duplicate string: values-uk/pin_setup_subtitle",
    "No duplicate string: values-uk/pin_unlock_subtitle",
    "No duplicate string: values-uk/pin_digit_count",
    "No duplicate string: values-uk/pin_locked",
    "No duplicate string: values-uk/pin_incorrect",
    "No duplicate string: values-uk/pin_mismatch",
    "No duplicate string: values-uk/pin_recovery_notice",
    "No duplicate string: values-uk/erase_digit",
    "No duplicate string: values-uk/start_over",
    "No duplicate string: values-uk/need_help",
    "No duplicate string: values-uk/security_error",
    "No duplicate string: values-uk/biometric_failed",
    "No duplicate string: values-uk/unlock_biometric",
    "No duplicate string: values-uk/biometric_title",
    "No duplicate string: values-uk/biometric_subtitle",
    "No duplicate string: values-uk/use_pin",
    "No duplicate string: values-uk/load_error_title",
    "No duplicate string: values-uk/storage_error",
    "No duplicate string: values-uk/saved",
    "No duplicate string: values-uk/entry_deleted",
    "No duplicate string: values-uk/undo",
    "No duplicate string: values-uk/undo_expired",
    "No duplicate string: values-uk/dashboard_heading",
    "No duplicate string: values-uk/greeting",
    "No duplicate string: values-uk/dashboard_subtitle",
    "No duplicate string: values-uk/total_balance",
    "No duplicate string: values-uk/currency_scope",
    "No duplicate string: values-uk/income",
    "No duplicate string: values-uk/expenses",
    "No duplicate string: values-uk/expense",
    "No duplicate string: values-uk/cash_flow",
    "No duplicate string: values-uk/last_7_days",
    "No duplicate string: values-uk/last_30_days",
    "No duplicate string: values-uk/last_30_days_plus",
    "No duplicate string: values-uk/chart_description",
    "No duplicate string: values-uk/chart_empty",
    "No duplicate string: values-uk/legend_income",
    "No duplicate string: values-uk/legend_expense",
    "No duplicate string: values-uk/activity",
    "No duplicate string: values-uk/search_entries",
    "No duplicate string: values-uk/clear_search",
    "No duplicate string: values-uk/all",
    "No duplicate string: values-uk/search_scope",
    "No duplicate string: values-uk/no_matches",
    "No duplicate string: values-uk/no_matches_body",
    "No duplicate string: values-uk/clear_filters",
    "No duplicate string: values-uk/empty_ledger_title",
    "No duplicate string: values-uk/empty_ledger_body",
    "No duplicate string: values-uk/add_first_entry",
    "No duplicate string: values-uk/add_entry",
    "No duplicate string: values-uk/edit_entry",
    "No duplicate string: values-uk/delete_entry",
    "No duplicate string: values-uk/delete_named_entry",
    "No duplicate string: values-uk/load_more",
    "No duplicate string: values-uk/positive_amount",
    "No duplicate string: values-uk/negative_amount",
    "No duplicate string: values-uk/category_food",
    "No duplicate string: values-uk/category_transport",
    "No duplicate string: values-uk/category_home",
    "No duplicate string: values-uk/category_health",
    "No duplicate string: values-uk/category_leisure",
    "No duplicate string: values-uk/category_salary",
    "No duplicate string: values-uk/category_other",
    "No duplicate string: values-uk/entry_currency_notice",
    "No duplicate string: values-uk/amount",
    "No duplicate string: values-uk/amount_invalid",
    "No duplicate string: values-uk/title_optional_hint",
    "No duplicate string: values-uk/entry_title",
    "No duplicate string: values-uk/title_invalid",
    "No duplicate string: values-uk/title_hint",
    "No duplicate string: values-uk/category",
    "No duplicate string: values-uk/date_invalid",
    "No duplicate string: values-uk/note_optional",
    "No duplicate string: values-uk/character_count",
    "No duplicate string: values-uk/save_entry",
    "No duplicate string: values-uk/entry_not_found",
    "No duplicate string: values-uk/done",
    "No duplicate string: values-uk/cancel",
    "No duplicate string: values-uk/discard_title",
    "No duplicate string: values-uk/discard_body",
    "No duplicate string: values-uk/discard",
    "No duplicate string: values-uk/keep_editing",
    "No duplicate string: values-uk/profile",
    "No duplicate string: values-uk/local_profile",
    "No duplicate string: values-uk/display_name",
    "No duplicate string: values-uk/save_profile",
    "No duplicate string: values-uk/name_invalid",
    "No duplicate string: values-uk/language",
    "No duplicate string: values-uk/language_instant",
    "No duplicate string: values-uk/language_en",
    "No duplicate string: values-uk/language_uk",
    "No duplicate string: values-uk/appearance",
    "No duplicate string: values-uk/theme_system",
    "No duplicate string: values-uk/theme_light",
    "No duplicate string: values-uk/theme_dark",
    "No duplicate string: values-uk/default_currency",
    "No duplicate string: values-uk/currency_explanation",
    "No duplicate string: values-uk/security",
    "No duplicate string: values-uk/biometric_unlock",
    "No duplicate string: values-uk/biometric_explanation",
    "No duplicate string: values-uk/biometric_unavailable",
    "No duplicate string: values-uk/auto_lock_explanation",
    "No duplicate string: values-uk/lock_now",
    "No duplicate string: values-uk/premium_name",
    "No duplicate string: values-uk/premium_body",
    "No duplicate string: values-uk/premium_active",
    "No duplicate string: values-uk/premium_offline_notice",
    "No duplicate string: values-uk/premium_unlock",
    "No duplicate string: values-uk/premium_buy",
    "No duplicate string: values-uk/restore_purchases",
    "No duplicate string: values-uk/billing_configuration",
    "No duplicate string: values-uk/billing_unavailable",
    "No duplicate string: values-uk/billing_network",
    "No duplicate string: values-uk/billing_pending",
    "No duplicate string: values-uk/billing_verification",
    "No duplicate string: values-uk/billing_acknowledgement",
    "No duplicate string: values-uk/reference_rates",
    "No duplicate string: values-uk/rates_disclaimer",
    "No duplicate string: values-uk/rates_empty",
    "No duplicate string: values-uk/rate_value",
    "No duplicate string: values-uk/rates_as_of",
    "No duplicate string: values-uk/refresh_rates",
    "No duplicate string: values-uk/refreshing",
    "No duplicate string: values-uk/rate_offline",
    "No duplicate string: values-uk/support",
    "No duplicate string: values-uk/support_intro",
    "No duplicate string: values-uk/contact_telegram",
    "No duplicate string: values-uk/diagnostics_show_button",
    "No duplicate string: values-uk/diagnostics_none",
    "No duplicate string: values-uk/diagnostics_title",
    "No duplicate string: values-uk/diagnostics_hint",
    "No duplicate string: values-uk/diagnostics_copy",
    "No duplicate string: values-uk/diagnostics_copied",
    "No duplicate string: values-uk/diagnostics_clear",
    "No duplicate string: values-uk/diagnostics_close",
    "No duplicate string: values-uk/here_to_help",
    "No duplicate string: values-uk/lead_developer",
    "No duplicate string: values-uk/support_handle",
    "No duplicate string: values-uk/copy_handle",
    "No duplicate string: values-uk/handle_copied",
    "No duplicate string: values-uk/support_privacy",
    "No duplicate string: values-uk/telegram_support",
    "No duplicate string: values-uk/support_network_error",
    "No duplicate string: values-uk/help_heading",
    "No duplicate string: values-uk/help_intro",
    "No duplicate string: values-uk/help_entries_title",
    "No duplicate string: values-uk/help_entries_body",
    "No duplicate string: values-uk/help_privacy_title",
    "No duplicate string: values-uk/help_privacy_body",
    "No duplicate string: values-uk/help_security_title",
    "No duplicate string: values-uk/help_security_body",
    "No duplicate string: values-uk/help_premium_title",
    "No duplicate string: values-uk/help_premium_body",
    "No duplicate string: values-uk/app_version",
    "No duplicate string: values-uk/premium_feature_trends",
    "No duplicate string: values-uk/premium_feature_mascot",
    "No duplicate string: values-uk/premium_feature_support",
    "No duplicate string: values-uk/premium_source_play",
    "No duplicate string: values-uk/premium_source_developer",
    "No duplicate string: values-uk/premium_expires",
    "No duplicate string: values-uk/premium_assistant_toggle",
    "No duplicate string: values-uk/premium_assistant_toggle_hint",
    "No duplicate string: values-uk/developer_purchase_title",
    "No duplicate string: values-uk/developer_purchase_body",
    "No duplicate string: values-uk/developer_purchase_banner",
    "No duplicate string: values-uk/developer_purchase_note",
    "No duplicate string: values-uk/developer_purchase_button",
    "No duplicate string: values-uk/installation_id_label",
    "No duplicate string: values-uk/installation_id_copy",
    "No duplicate string: values-uk/installation_id_copied",
    "No duplicate string: values-uk/activation_code_label",
    "No duplicate string: values-uk/activation_code_hint",
    "No duplicate string: values-uk/activation_code_button",
    "No duplicate string: values-uk/billing_activation_code",
    "No duplicate string: values-uk/premium_activated",
    "No duplicate string: values-uk/activation_invalid",
    "No duplicate string: values-uk/mascot_card_title",
    "No duplicate string: values-uk/mascot_hear_again",
    "No duplicate string: values-uk/mascot_premium_note",
    "No duplicate string: values-uk/voice_assistant_button",
    "No duplicate string: values-uk/voice_assistant_hint",
    "No duplicate string: values-uk/voice_assistant_listening",
    "No duplicate string: values-uk/voice_entry_added",
    "No duplicate string: values-uk/voice_reply_balance",
    "No duplicate string: values-uk/voice_reply_spent_today",
    "No duplicate string: values-uk/voice_reply_opening_entry",
    "No duplicate string: values-uk/voice_reply_not_understood",
    "No duplicate string: values-uk/voice_smalltalk_greeting",
    "No duplicate string: values-uk/voice_smalltalk_how_are_you",
    "No duplicate string: values-uk/voice_smalltalk_thanks",
    "No duplicate string: values-uk/voice_smalltalk_who_are_you",
    "No duplicate string: values-uk/voice_smalltalk_bye",
    "No duplicate string: values-uk/voice_smalltalk_compliment",
    "No duplicate string: values-uk/voice_smalltalk_joke",
    "No duplicate string: values-uk/voice_permission_denied",
    "No duplicate string: values-uk/voice_recognizer_unavailable",
    "No duplicate string: values-uk/all_time",
    "No duplicate string: values-uk/categories",
    "No duplicate string: values-uk/all_categories",
    "No duplicate string: values-uk/category_transaction_count",
    "No duplicate string: values-uk/category_empty",
    "No duplicate string: values-uk/period_today",
    "No duplicate string: values-uk/period_month",
    "No duplicate string: values-uk/period_year",
    "No duplicate string: values-uk/voice_statistics",
    "No duplicate string: values-uk/assistant_text_hint",
    "No duplicate string: values-uk/assistant_send",
    "No duplicate string: values-uk/premium_source_trial",
    "No duplicate string: values-uk/premium_local_notice",
    "No duplicate string: values-uk/trial_explanation",
    "No duplicate string: values-uk/trial_14",
    "No duplicate string: values-uk/mascot_style",
    "No duplicate string: values-uk/mascot_gray",
    "No duplicate string: values-uk/mascot_sky",
    "No duplicate string: values-uk/mascot_animations",
    "No duplicate string: values-uk/brand_intro_title",
    "No duplicate string: values-uk/brand_intro_body",
    "No duplicate string: values-uk/brand_design_label",
    "No duplicate string: values-uk/analytics_title",
    "No duplicate string: values-uk/cinematic_history",
    "No duplicate string: values-uk/cinematic_period",
    "No duplicate string: values-uk/cinematic_apply",
    "No duplicate string: values-uk/cinematic_more",
    "No duplicate string: values-uk/cinematic_expenses_period",
    "No duplicate string: values-uk/cinematic_comparison",
    "No duplicate string: values-uk/cinematic_more_than_previous",
    "No duplicate string: values-uk/cinematic_less_than_previous",
    "No duplicate string: values-uk/cinematic_week_average",
    "No duplicate string: values-uk/cinematic_month_average",
    "No duplicate string: values-uk/cinematic_expense_categories",
    "No duplicate string: values-uk/cinematic_income_categories",
    "No duplicate string: values-uk/cinematic_transactions",
    "No duplicate string: values-uk/cinematic_tooltip",
    "No duplicate string: values-uk/cinematic_chart_accessibility",
    "No duplicate string: values-uk/cinematic_more_buckets",
    "No duplicate string: values-uk/cat_asset_status",
    "No duplicate string: values-uk/fps_title",
    "No duplicate string: values-uk/fps_explanation",
    "No duplicate string: values-uk/fps_auto",
    "No duplicate string: values-uk/fps_value",
    "No duplicate string: values-uk/fps_supported",
    "No duplicate string: values-uk/fps_fallback",
    "No duplicate string: values-uk/cat_tap_hint",
    "No duplicate string: values-uk/category_transfer",
    "No duplicate string: values-uk/category_dining",
    "No duplicate string: values-uk/category_cashback",
    "No duplicate string: values-uk/reference_total_for",
    "No duplicate string: values-uk/total_all_time",
    "No duplicate string: values-uk/total_thirty_days",
    "No duplicate string: values-uk/total_seven_days",
    "No duplicate string: values-uk/colors_title",
    "No duplicate string: values-uk/colors_explanation",
    "No duplicate string: values-uk/colors_edit",
    "No duplicate string: values-uk/colors_collapse",
    "No duplicate string: values-uk/colors_categories",
    "No duplicate string: values-uk/colors_reset_all",
    "No duplicate string: values-uk/color_accent",
    "No duplicate string: values-uk/color_background",
    "No duplicate string: values-uk/color_surface",
    "No duplicate string: values-uk/color_text",
    "No duplicate string: values-uk/color_muted",
    "No duplicate string: values-uk/color_chart_top",
    "No duplicate string: values-uk/color_chart_bottom",
    "No duplicate string: values-uk/color_grid",
    "No duplicate string: values-uk/color_icons",
    "No duplicate string: values-uk/color_hue",
    "No duplicate string: values-uk/color_saturation",
    "No duplicate string: values-uk/color_brightness",
    "No duplicate string: values-uk/color_apply",
    "No duplicate string: values-uk/color_reset",
    "No duplicate string: values-uk/color_invalid",
    "No duplicate string: values-uk/color_contrast_warning",
    "No duplicate string: values-uk/color_error",
    "No duplicate string: values-uk/bank_dashboard",
    "No duplicate string: values-uk/bank_import_title",
    "No duplicate string: values-uk/bank_consent",
    "No duplicate string: values-uk/bank_enabled",
    "No duplicate string: values-uk/bank_auto",
    "No duplicate string: values-uk/bank_access_yes",
    "No duplicate string: values-uk/bank_access_no",
    "No duplicate string: values-uk/bank_notification_permission",
    "No duplicate string: values-uk/bank_sms_yes",
    "No duplicate string: values-uk/bank_sms_no",
    "No duplicate string: values-uk/bank_sms_permission",
    "No duplicate string: values-uk/bank_play_sms",
    "No duplicate string: values-uk/bank_privacy",
    "No duplicate string: values-uk/bank_privacy_body",
    "No duplicate string: values-uk/bank_day",
    "No duplicate string: values-uk/bank_week",
    "No duplicate string: values-uk/bank_month",
    "No duplicate string: values-uk/bank_cash",
    "No duplicate string: values-uk/bank_card",
    "No duplicate string: values-uk/bank_unknown",
    "No duplicate string: values-uk/bank_source",
    "No duplicate string: values-uk/bank_category_stats",
    "No duplicate string: values-uk/bank_new_category",
    "No duplicate string: values-uk/bank_review_count",
    "No duplicate string: values-uk/bank_time_received",
    "No duplicate string: values-uk/bank_duplicate",
    "No duplicate string: values-uk/bank_transfer_review",
    "No duplicate string: values-uk/bank_ignore",
    "No duplicate string: values-uk/bank_recent",
    "No duplicate string: values-uk/bank_limit_recent",
    "No duplicate string: values-uk/bank_budget",
    "No duplicate string: values-uk/bank_budget_progress",
    "No duplicate string: values-uk/bank_export",
    "No duplicate string: values-uk/bank_clear_history",
    "No duplicate string: values-uk/bank_clear_warning",
    "No duplicate string: values-uk/bank_app_permissions",
    "No duplicate string: values-uk/bank_battery",
    "No duplicate string: values-uk/bank_budget_disable",
    "No duplicate string: values-uk/history_search",
    "No duplicate string: values-uk/import_diagnostics",
    "No duplicate string: values-uk/import_connected",
    "No duplicate string: values-uk/import_disconnected",
    "No duplicate string: values-uk/import_counts",
    "No duplicate string: values-uk/import_reconnect",
    "No duplicate string: values-uk/import_package_settings",
    "No duplicate string: values-uk/import_package_warning",
    "No duplicate string: values-uk/import_paste",
    "No duplicate string: values-uk/import_text",
    "No duplicate string: values-uk/import_manual_privacy",
    "No duplicate string: values-uk/import_to_queue",
    "No duplicate string: values-uk/import_queue_note",
    "No duplicate string: values-uk/import_disabled",
    "No duplicate string: values-uk/import_unsupported",
    "No duplicate string: values-uk/import_empty",
    "No duplicate string: values-uk/import_group",
    "No duplicate string: values-uk/import_unrecognized",
    "No duplicate string: values-uk/import_auto_done",
    "No duplicate string: values-uk/import_queued",
    "No duplicate string: values-uk/import_repeated",
    "No duplicate string: values-uk/import_waiting",
    "No duplicate string: values-uk/import_mode_title",
    "No duplicate string: values-uk/import_mode_api",
    "No duplicate string: values-uk/import_mode_sms",
    "No duplicate string: values-uk/import_mode_notify",
    "No duplicate string: values-uk/api_howto",
    "No duplicate string: values-uk/api_open_site",
    "No duplicate string: values-uk/api_token_label",
    "No duplicate string: values-uk/api_connect",
    "No duplicate string: values-uk/api_connecting",
    "No duplicate string: values-uk/api_connected",
    "No duplicate string: values-uk/api_last_sync",
    "No duplicate string: values-uk/api_imported_last",
    "No duplicate string: values-uk/api_syncing",
    "No duplicate string: values-uk/api_sync_now",
    "No duplicate string: values-uk/api_disconnect",
    "No duplicate string: values-uk/api_error_bad_token",
    "No duplicate string: values-uk/api_error_rate",
    "No duplicate string: values-uk/api_error_network",
    "No duplicate string: values-uk/api_error_no_accounts",
    "No duplicate string: values-uk/sms_howto",
    "No duplicate string: values-uk/sms_scan",
    "No duplicate string: values-uk/sms_scanning",
    "No duplicate string: values-uk/sms_scan_result",
    "No duplicate string: values-uk/sms_restricted_tip",
    "No duplicate string: values-uk/pay_source_title",
    "No duplicate string: values-uk/pay_source_required",
    "No duplicate string: values-uk/voice_ask_source",
    "No duplicate string: values-uk/cat_locked_hint",
    "No duplicate string: values-uk/bank_setup",
    "No duplicate string: values-uk/push_howto",
    "No duplicate string: values-uk/privat_api_title",
    "No duplicate string: values-uk/privat_api_howto",
    "No duplicate string: values-uk/privat_api_site",
    "No duplicate string: values-uk/privat_api_token",
    "No duplicate string: values-uk/privat_api_connected",
    "No duplicate string: values-uk/privat_api_error",
    "No duplicate string: values-uk/other_api_note",
    "No duplicate string: values-uk/help_banks_title",
    "No duplicate string: values-uk/help_banks_body",
    "No duplicate string: values-uk/import_confirm_permission",
    "No duplicate string: values-uk/import_confirm_enabled",
    "No duplicate string: values-uk/assistant_write_short",
    "English/Ukrainian key parity",
    "Format-argument parity: all_time",
    "Nonempty translations: all_time",
    "Format-argument parity: undo_expired",
    "Nonempty translations: undo_expired",
    "Format-argument parity: total_seven_days",
    "Nonempty translations: total_seven_days",
    "Format-argument parity: add_entry",
    "Nonempty translations: add_entry",
    "Format-argument parity: api_connecting",
    "Nonempty translations: api_connecting",
    "Format-argument parity: categories",
    "Nonempty translations: categories",
    "Format-argument parity: import_connected",
    "Nonempty translations: import_connected",
    "Format-argument parity: bank_privacy",
    "Nonempty translations: bank_privacy",
    "Format-argument parity: display_name",
    "Nonempty translations: display_name",
    "Format-argument parity: sms_scan_result",
    "Nonempty translations: sms_scan_result",
    "Format-argument parity: voice_permission_denied",
    "Nonempty translations: voice_permission_denied",
    "Format-argument parity: name_invalid",
    "Nonempty translations: name_invalid",
    "Format-argument parity: delete_named_entry",
    "Nonempty translations: delete_named_entry",
    "Format-argument parity: theme_dark",
    "Nonempty translations: theme_dark",
    "Format-argument parity: api_sync_now",
    "Nonempty translations: api_sync_now",
    "Format-argument parity: voice_assistant_listening",
    "Nonempty translations: voice_assistant_listening",
    "Format-argument parity: import_reconnect",
    "Nonempty translations: import_reconnect",
    "Format-argument parity: activation_invalid",
    "Nonempty translations: activation_invalid",
    "Format-argument parity: discard",
    "Nonempty translations: discard",
    "Format-argument parity: bank_budget",
    "Nonempty translations: bank_budget",
    "Format-argument parity: diagnostics_title",
    "Nonempty translations: diagnostics_title",
    "Format-argument parity: bank_source",
    "Nonempty translations: bank_source",
    "Format-argument parity: color_background",
    "Nonempty translations: color_background",
    "Format-argument parity: import_repeated",
    "Nonempty translations: import_repeated",
    "Format-argument parity: cinematic_week_average",
    "Nonempty translations: cinematic_week_average",
    "Format-argument parity: voice_smalltalk_thanks",
    "Nonempty translations: voice_smalltalk_thanks",
    "Format-argument parity: api_error_network",
    "Nonempty translations: api_error_network",
    "Format-argument parity: bank_app_permissions",
    "Nonempty translations: bank_app_permissions",
    "Format-argument parity: back",
    "Nonempty translations: back",
    "Format-argument parity: bank_duplicate",
    "Nonempty translations: bank_duplicate",
    "Format-argument parity: installation_id_label",
    "Nonempty translations: installation_id_label",
    "Format-argument parity: profile",
    "Nonempty translations: profile",
    "Format-argument parity: help_banks_title",
    "Nonempty translations: help_banks_title",
    "Format-argument parity: colors_edit",
    "Nonempty translations: colors_edit",
    "Format-argument parity: onboard_body_two",
    "Nonempty translations: onboard_body_two",
    "Format-argument parity: voice_statistics",
    "Nonempty translations: voice_statistics",
    "Format-argument parity: add_first_entry",
    "Nonempty translations: add_first_entry",
    "Format-argument parity: mascot_premium_note",
    "Nonempty translations: mascot_premium_note",
    "Format-argument parity: color_chart_top",
    "Nonempty translations: color_chart_top",
    "Format-argument parity: search_entries",
    "Nonempty translations: search_entries",
    "Format-argument parity: voice_smalltalk_bye",
    "Nonempty translations: voice_smalltalk_bye",
    "Format-argument parity: category_home",
    "Nonempty translations: category_home",
    "Format-argument parity: fps_fallback",
    "Nonempty translations: fps_fallback",
    "Format-argument parity: edit_entry",
    "Nonempty translations: edit_entry",
    "Format-argument parity: language_en",
    "Nonempty translations: language_en",
    "Format-argument parity: diagnostics_copy",
    "Nonempty translations: diagnostics_copy",
    "Format-argument parity: legend_income",
    "Nonempty translations: legend_income",
    "Format-argument parity: api_disconnect",
    "Nonempty translations: api_disconnect",
    "Format-argument parity: import_package_warning",
    "Nonempty translations: import_package_warning",
    "Format-argument parity: onboard_title_one",
    "Nonempty translations: onboard_title_one",
    "Format-argument parity: dashboard_subtitle",
    "Nonempty translations: dashboard_subtitle",
    "Format-argument parity: refresh_rates",
    "Nonempty translations: refresh_rates",
    "Format-argument parity: premium_unlock",
    "Nonempty translations: premium_unlock",
    "Format-argument parity: bank_clear_warning",
    "Nonempty translations: bank_clear_warning",
    "Format-argument parity: billing_network",
    "Nonempty translations: billing_network",
    "Format-argument parity: color_text",
    "Nonempty translations: color_text",
    "Format-argument parity: local_profile",
    "Nonempty translations: local_profile",
    "Format-argument parity: bank_budget_disable",
    "Nonempty translations: bank_budget_disable",
    "Format-argument parity: colors_categories",
    "Nonempty translations: colors_categories",
    "Format-argument parity: retry",
    "Nonempty translations: retry",
    "Format-argument parity: voice_assistant_hint",
    "Nonempty translations: voice_assistant_hint",
    "Format-argument parity: create_pin",
    "Nonempty translations: create_pin",
    "Format-argument parity: sms_howto",
    "Nonempty translations: sms_howto",
    "Format-argument parity: bank_ignore",
    "Nonempty translations: bank_ignore",
    "Format-argument parity: developer_purchase_note",
    "Nonempty translations: developer_purchase_note",
    "Format-argument parity: note_optional",
    "Nonempty translations: note_optional",
    "Format-argument parity: need_help",
    "Nonempty translations: need_help",
    "Format-argument parity: category_cashback",
    "Nonempty translations: category_cashback",
    "Format-argument parity: billing_configuration",
    "Nonempty translations: billing_configuration",
    "Format-argument parity: privat_api_error",
    "Nonempty translations: privat_api_error",
    "Format-argument parity: language_instant",
    "Nonempty translations: language_instant",
    "Format-argument parity: mascot_card_title",
    "Nonempty translations: mascot_card_title",
    "Format-argument parity: category_transport",
    "Nonempty translations: category_transport",
    "Format-argument parity: empty_ledger_body",
    "Nonempty translations: empty_ledger_body",
    "Format-argument parity: color_brightness",
    "Nonempty translations: color_brightness",
    "Format-argument parity: activation_code_hint",
    "Nonempty translations: activation_code_hint",
    "Format-argument parity: all_categories",
    "Nonempty translations: all_categories",
    "Format-argument parity: premium_feature_support",
    "Nonempty translations: premium_feature_support",
    "Format-argument parity: sms_restricted_tip",
    "Nonempty translations: sms_restricted_tip",
    "Format-argument parity: api_open_site",
    "Nonempty translations: api_open_site",
    "Format-argument parity: premium_buy",
    "Nonempty translations: premium_buy",
    "Format-argument parity: color_icons",
    "Nonempty translations: color_icons",
    "Format-argument parity: color_grid",
    "Nonempty translations: color_grid",
    "Format-argument parity: erase_digit",
    "Nonempty translations: erase_digit",
    "Format-argument parity: discard_body",
    "Nonempty translations: discard_body",
    "Format-argument parity: privat_api_title",
    "Nonempty translations: privat_api_title",
    "Format-argument parity: biometric_failed",
    "Nonempty translations: biometric_failed",
    "Format-argument parity: total_all_time",
    "Nonempty translations: total_all_time",
    "Format-argument parity: cinematic_tooltip",
    "Nonempty translations: cinematic_tooltip",
    "Format-argument parity: category_dining",
    "Nonempty translations: category_dining",
    "Format-argument parity: premium_activated",
    "Nonempty translations: premium_activated",
    "Format-argument parity: import_queued",
    "Nonempty translations: import_queued",
    "Format-argument parity: last_30_days_plus",
    "Nonempty translations: last_30_days_plus",
    "Format-argument parity: dashboard_heading",
    "Nonempty translations: dashboard_heading",
    "Format-argument parity: entry_deleted",
    "Nonempty translations: entry_deleted",
    "Format-argument parity: other_api_note",
    "Nonempty translations: other_api_note",
    "Format-argument parity: premium_assistant_toggle",
    "Nonempty translations: premium_assistant_toggle",
    "Format-argument parity: cinematic_more_than_previous",
    "Nonempty translations: cinematic_more_than_previous",
    "Format-argument parity: entry_currency_notice",
    "Nonempty translations: entry_currency_notice",
    "Format-argument parity: diagnostics_copied",
    "Nonempty translations: diagnostics_copied",
    "Format-argument parity: premium_name",
    "Nonempty translations: premium_name",
    "Format-argument parity: restore_purchases",
    "Nonempty translations: restore_purchases",
    "Format-argument parity: import_mode_title",
    "Nonempty translations: import_mode_title",
    "Format-argument parity: import_diagnostics",
    "Nonempty translations: import_diagnostics",
    "Format-argument parity: cinematic_transactions",
    "Nonempty translations: cinematic_transactions",
    "Format-argument parity: color_error",
    "Nonempty translations: color_error",
    "Format-argument parity: bank_export",
    "Nonempty translations: bank_export",
    "Format-argument parity: period_year",
    "Nonempty translations: period_year",
    "Format-argument parity: last_30_days",
    "Nonempty translations: last_30_days",
    "Format-argument parity: colors_reset_all",
    "Nonempty translations: colors_reset_all",
    "Format-argument parity: chart_description",
    "Nonempty translations: chart_description",
    "Format-argument parity: colors_title",
    "Nonempty translations: colors_title",
    "Format-argument parity: help",
    "Nonempty translations: help",
    "Format-argument parity: bank_day",
    "Nonempty translations: bank_day",
    "Format-argument parity: import_manual_privacy",
    "Nonempty translations: import_manual_privacy",
    "Format-argument parity: color_contrast_warning",
    "Nonempty translations: color_contrast_warning",
    "Format-argument parity: import_group",
    "Nonempty translations: import_group",
    "Format-argument parity: voice_reply_not_understood",
    "Nonempty translations: voice_reply_not_understood",
    "Format-argument parity: sms_scanning",
    "Nonempty translations: sms_scanning",
    "Format-argument parity: settings",
    "Nonempty translations: settings",
    "Format-argument parity: activation_code_button",
    "Nonempty translations: activation_code_button",
    "Format-argument parity: cat_locked_hint",
    "Nonempty translations: cat_locked_hint",
    "Format-argument parity: import_to_queue",
    "Nonempty translations: import_to_queue",
    "Format-argument parity: import_counts",
    "Nonempty translations: import_counts",
    "Format-argument parity: cinematic_income_categories",
    "Nonempty translations: cinematic_income_categories",
    "Format-argument parity: color_reset",
    "Nonempty translations: color_reset",
    "Format-argument parity: billing_acknowledgement",
    "Nonempty translations: billing_acknowledgement",
    "Format-argument parity: api_howto",
    "Nonempty translations: api_howto",
    "Format-argument parity: onboard_body_one",
    "Nonempty translations: onboard_body_one",
    "Format-argument parity: colors_explanation",
    "Nonempty translations: colors_explanation",
    "Format-argument parity: category_salary",
    "Nonempty translations: category_salary",
    "Format-argument parity: voice_reply_opening_entry",
    "Nonempty translations: voice_reply_opening_entry",
    "Format-argument parity: app_name",
    "Nonempty translations: app_name",
    "Format-argument parity: import_disconnected",
    "Nonempty translations: import_disconnected",
    "Format-argument parity: import_queue_note",
    "Nonempty translations: import_queue_note",
    "Format-argument parity: category_empty",
    "Nonempty translations: category_empty",
    "Format-argument parity: here_to_help",
    "Nonempty translations: here_to_help",
    "Format-argument parity: help_premium_title",
    "Nonempty translations: help_premium_title",
    "Format-argument parity: api_error_no_accounts",
    "Nonempty translations: api_error_no_accounts",
    "Format-argument parity: bank_auto",
    "Nonempty translations: bank_auto",
    "Format-argument parity: bank_consent",
    "Nonempty translations: bank_consent",
    "Format-argument parity: saved",
    "Nonempty translations: saved",
    "Format-argument parity: voice_smalltalk_joke",
    "Nonempty translations: voice_smalltalk_joke",
    "Format-argument parity: color_invalid",
    "Nonempty translations: color_invalid",
    "Format-argument parity: premium_feature_trends",
    "Nonempty translations: premium_feature_trends",
    "Format-argument parity: cinematic_expense_categories",
    "Nonempty translations: cinematic_expense_categories",
    "Format-argument parity: period_month",
    "Nonempty translations: period_month",
    "Format-argument parity: discard_title",
    "Nonempty translations: discard_title",
    "Format-argument parity: import_waiting",
    "Nonempty translations: import_waiting",
    "Format-argument parity: expense",
    "Nonempty translations: expense",
    "Format-argument parity: sms_scan",
    "Nonempty translations: sms_scan",
    "Format-argument parity: income",
    "Nonempty translations: income",
    "Format-argument parity: language_uk",
    "Nonempty translations: language_uk",
    "Format-argument parity: diagnostics_none",
    "Nonempty translations: diagnostics_none",
    "Format-argument parity: api_last_sync",
    "Nonempty translations: api_last_sync",
    "Format-argument parity: title_hint",
    "Nonempty translations: title_hint",
    "Format-argument parity: installation_id_copy",
    "Nonempty translations: installation_id_copy",
    "Format-argument parity: voice_recognizer_unavailable",
    "Nonempty translations: voice_recognizer_unavailable",
    "Format-argument parity: bank_cash",
    "Nonempty translations: bank_cash",
    "Format-argument parity: bank_setup",
    "Nonempty translations: bank_setup",
    "Format-argument parity: pin_unlock_subtitle",
    "Nonempty translations: pin_unlock_subtitle",
    "Format-argument parity: privat_api_connected",
    "Nonempty translations: privat_api_connected",
    "Format-argument parity: api_error_bad_token",
    "Nonempty translations: api_error_bad_token",
    "Format-argument parity: expenses",
    "Nonempty translations: expenses",
    "Format-argument parity: api_token_label",
    "Nonempty translations: api_token_label",
    "Format-argument parity: rates_disclaimer",
    "Nonempty translations: rates_disclaimer",
    "Format-argument parity: copy_handle",
    "Nonempty translations: copy_handle",
    "Format-argument parity: bank_review_count",
    "Nonempty translations: bank_review_count",
    "Format-argument parity: pin_setup_subtitle",
    "Nonempty translations: pin_setup_subtitle",
    "Format-argument parity: bank_access_no",
    "Nonempty translations: bank_access_no",
    "Format-argument parity: entry_not_found",
    "Nonempty translations: entry_not_found",
    "Format-argument parity: support",
    "Nonempty translations: support",
    "Format-argument parity: bank_sms_no",
    "Nonempty translations: bank_sms_no",
    "Format-argument parity: fps_value",
    "Nonempty translations: fps_value",
    "Format-argument parity: bank_transfer_review",
    "Nonempty translations: bank_transfer_review",
    "Format-argument parity: lock_now",
    "Nonempty translations: lock_now",
    "Format-argument parity: category_food",
    "Nonempty translations: category_food",
    "Format-argument parity: voice_smalltalk_who_are_you",
    "Nonempty translations: voice_smalltalk_who_are_you",
    "Format-argument parity: bank_import_title",
    "Nonempty translations: bank_import_title",
    "Format-argument parity: confirm_pin",
    "Nonempty translations: confirm_pin",
    "Format-argument parity: undo",
    "Nonempty translations: undo",
    "Format-argument parity: greeting",
    "Nonempty translations: greeting",
    "Format-argument parity: developer_purchase_body",
    "Nonempty translations: developer_purchase_body",
    "Format-argument parity: refreshing",
    "Nonempty translations: refreshing",
    "Format-argument parity: premium_local_notice",
    "Nonempty translations: premium_local_notice",
    "Format-argument parity: help_privacy_title",
    "Nonempty translations: help_privacy_title",
    "Format-argument parity: pin_recovery_notice",
    "Nonempty translations: pin_recovery_notice",
    "Format-argument parity: help_entries_title",
    "Nonempty translations: help_entries_title",
    "Format-argument parity: assistant_send",
    "Nonempty translations: assistant_send",
    "Format-argument parity: premium_source_play",
    "Nonempty translations: premium_source_play",
    "Format-argument parity: premium_source_trial",
    "Nonempty translations: premium_source_trial",
    "Format-argument parity: history_search",
    "Nonempty translations: history_search",
    "Format-argument parity: privat_api_token",
    "Nonempty translations: privat_api_token",
    "Format-argument parity: fps_supported",
    "Nonempty translations: fps_supported",
    "Format-argument parity: voice_assistant_button",
    "Nonempty translations: voice_assistant_button",
    "Format-argument parity: diagnostics_clear",
    "Nonempty translations: diagnostics_clear",
    "Format-argument parity: pay_source_title",
    "Nonempty translations: pay_source_title",
    "Format-argument parity: telegram_support",
    "Nonempty translations: telegram_support",
    "Format-argument parity: voice_reply_spent_today",
    "Nonempty translations: voice_reply_spent_today",
    "Format-argument parity: positive_amount",
    "Nonempty translations: positive_amount",
    "Format-argument parity: color_accent",
    "Nonempty translations: color_accent",
    "Format-argument parity: save_entry",
    "Nonempty translations: save_entry",
    "Format-argument parity: diagnostics_hint",
    "Nonempty translations: diagnostics_hint",
    "Format-argument parity: load_error_title",
    "Nonempty translations: load_error_title",
    "Format-argument parity: cat_asset_status",
    "Nonempty translations: cat_asset_status",
    "Format-argument parity: clear_search",
    "Nonempty translations: clear_search",
    "Format-argument parity: support_intro",
    "Nonempty translations: support_intro",
    "Format-argument parity: diagnostics_close",
    "Nonempty translations: diagnostics_close",
    "Format-argument parity: developer_purchase_title",
    "Nonempty translations: developer_purchase_title",
    "Format-argument parity: color_saturation",
    "Nonempty translations: color_saturation",
    "Format-argument parity: rate_offline",
    "Nonempty translations: rate_offline",
    "Format-argument parity: import_mode_api",
    "Nonempty translations: import_mode_api",
    "Format-argument parity: page_of",
    "Nonempty translations: page_of",
    "Format-argument parity: mascot_hear_again",
    "Nonempty translations: mascot_hear_again",
    "Format-argument parity: lead_developer",
    "Nonempty translations: lead_developer",
    "Format-argument parity: legend_expense",
    "Nonempty translations: legend_expense",
    "Format-argument parity: reference_rates",
    "Nonempty translations: reference_rates",
    "Format-argument parity: bank_card",
    "Nonempty translations: bank_card",
    "Format-argument parity: privat_api_site",
    "Nonempty translations: privat_api_site",
    "Format-argument parity: category_transaction_count",
    "Nonempty translations: category_transaction_count",
    "Format-argument parity: app_version",
    "Nonempty translations: app_version",
    "Format-argument parity: next",
    "Nonempty translations: next",
    "Format-argument parity: help_intro",
    "Nonempty translations: help_intro",
    "Format-argument parity: cat_tap_hint",
    "Nonempty translations: cat_tap_hint",
    "Format-argument parity: bank_week",
    "Nonempty translations: bank_week",
    "Format-argument parity: activity",
    "Nonempty translations: activity",
    "Format-argument parity: currency_scope",
    "Nonempty translations: currency_scope",
    "Format-argument parity: amount",
    "Nonempty translations: amount",
    "Format-argument parity: brand_design_label",
    "Nonempty translations: brand_design_label",
    "Format-argument parity: cinematic_chart_accessibility",
    "Nonempty translations: cinematic_chart_accessibility",
    "Format-argument parity: currency_explanation",
    "Nonempty translations: currency_explanation",
    "Format-argument parity: help_heading",
    "Nonempty translations: help_heading",
    "Format-argument parity: bank_unknown",
    "Nonempty translations: bank_unknown",
    "Format-argument parity: onboard_body_three",
    "Nonempty translations: onboard_body_three",
    "Format-argument parity: voice_smalltalk_how_are_you",
    "Nonempty translations: voice_smalltalk_how_are_you",
    "Format-argument parity: support_privacy",
    "Nonempty translations: support_privacy",
    "Format-argument parity: title_invalid",
    "Nonempty translations: title_invalid",
    "Format-argument parity: import_unrecognized",
    "Nonempty translations: import_unrecognized",
    "Format-argument parity: category",
    "Nonempty translations: category",
    "Format-argument parity: bank_new_category",
    "Nonempty translations: bank_new_category",
    "Format-argument parity: rate_value",
    "Nonempty translations: rate_value",
    "Format-argument parity: developer_purchase_banner",
    "Nonempty translations: developer_purchase_banner",
    "Format-argument parity: import_auto_done",
    "Nonempty translations: import_auto_done",
    "Format-argument parity: keep_editing",
    "Nonempty translations: keep_editing",
    "Format-argument parity: period_today",
    "Nonempty translations: period_today",
    "Format-argument parity: trial_14",
    "Nonempty translations: trial_14",
    "Format-argument parity: premium_assistant_toggle_hint",
    "Nonempty translations: premium_assistant_toggle_hint",
    "Format-argument parity: trial_explanation",
    "Nonempty translations: trial_explanation",
    "Format-argument parity: delete_entry",
    "Nonempty translations: delete_entry",
    "Format-argument parity: mascot_sky",
    "Nonempty translations: mascot_sky",
    "Format-argument parity: api_syncing",
    "Nonempty translations: api_syncing",
    "Format-argument parity: last_7_days",
    "Nonempty translations: last_7_days",
    "Format-argument parity: get_started",
    "Nonempty translations: get_started",
    "Format-argument parity: biometric_unavailable",
    "Nonempty translations: biometric_unavailable",
    "Format-argument parity: support_network_error",
    "Nonempty translations: support_network_error",
    "Format-argument parity: cinematic_more_buckets",
    "Nonempty translations: cinematic_more_buckets",
    "Format-argument parity: help_privacy_body",
    "Nonempty translations: help_privacy_body",
    "Format-argument parity: import_confirm_permission",
    "Nonempty translations: import_confirm_permission",
    "Format-argument parity: no_matches_body",
    "Nonempty translations: no_matches_body",
    "Format-argument parity: bank_play_sms",
    "Nonempty translations: bank_play_sms",
    "Format-argument parity: negative_amount",
    "Nonempty translations: negative_amount",
    "Format-argument parity: bank_clear_history",
    "Nonempty translations: bank_clear_history",
    "Format-argument parity: cinematic_comparison",
    "Nonempty translations: cinematic_comparison",
    "Format-argument parity: biometric_unlock",
    "Nonempty translations: biometric_unlock",
    "Format-argument parity: language",
    "Nonempty translations: language",
    "Format-argument parity: fps_title",
    "Nonempty translations: fps_title",
    "Format-argument parity: bank_budget_progress",
    "Nonempty translations: bank_budget_progress",
    "Format-argument parity: help_security_body",
    "Nonempty translations: help_security_body",
    "Format-argument parity: billing_verification",
    "Nonempty translations: billing_verification",
    "Format-argument parity: done",
    "Nonempty translations: done",
    "Format-argument parity: unlock_title",
    "Nonempty translations: unlock_title",
    "Format-argument parity: premium_body",
    "Nonempty translations: premium_body",
    "Format-argument parity: chart_empty",
    "Nonempty translations: chart_empty",
    "Format-argument parity: premium_active",
    "Nonempty translations: premium_active",
    "Format-argument parity: api_connected",
    "Nonempty translations: api_connected",
    "Format-argument parity: contact_telegram",
    "Nonempty translations: contact_telegram",
    "Format-argument parity: load_more",
    "Nonempty translations: load_more",
    "Format-argument parity: default_currency",
    "Nonempty translations: default_currency",
    "Format-argument parity: fps_explanation",
    "Nonempty translations: fps_explanation",
    "Format-argument parity: fps_auto",
    "Nonempty translations: fps_auto",
    "Format-argument parity: biometric_title",
    "Nonempty translations: biometric_title",
    "Format-argument parity: assistant_text_hint",
    "Nonempty translations: assistant_text_hint",
    "Format-argument parity: voice_entry_added",
    "Nonempty translations: voice_entry_added",
    "Format-argument parity: storage_error",
    "Nonempty translations: storage_error",
    "Format-argument parity: date_invalid",
    "Nonempty translations: date_invalid",
    "Format-argument parity: cash_flow",
    "Nonempty translations: cash_flow",
    "Format-argument parity: category_health",
    "Nonempty translations: category_health",
    "Format-argument parity: bank_enabled",
    "Nonempty translations: bank_enabled",
    "Format-argument parity: import_package_settings",
    "Nonempty translations: import_package_settings",
    "Format-argument parity: support_handle",
    "Nonempty translations: support_handle",
    "Format-argument parity: biometric_subtitle",
    "Nonempty translations: biometric_subtitle",
    "Format-argument parity: assistant_write_short",
    "Nonempty translations: assistant_write_short",
    "Format-argument parity: bank_recent",
    "Nonempty translations: bank_recent",
    "Format-argument parity: colors_collapse",
    "Nonempty translations: colors_collapse",
    "Format-argument parity: amount_invalid",
    "Nonempty translations: amount_invalid",
    "Format-argument parity: title_optional_hint",
    "Nonempty translations: title_optional_hint",
    "Format-argument parity: all",
    "Nonempty translations: all",
    "Format-argument parity: developer_purchase_button",
    "Nonempty translations: developer_purchase_button",
    "Format-argument parity: billing_activation_code",
    "Nonempty translations: billing_activation_code",
    "Format-argument parity: category_leisure",
    "Nonempty translations: category_leisure",
    "Format-argument parity: theme_light",
    "Nonempty translations: theme_light",
    "Format-argument parity: analytics_title",
    "Nonempty translations: analytics_title",
    "Format-argument parity: close",
    "Nonempty translations: close",
    "Format-argument parity: empty_ledger_title",
    "Nonempty translations: empty_ledger_title",
    "Format-argument parity: appearance",
    "Nonempty translations: appearance",
    "Format-argument parity: cancel",
    "Nonempty translations: cancel",
    "Format-argument parity: import_disabled",
    "Nonempty translations: import_disabled",
    "Format-argument parity: cinematic_history",
    "Nonempty translations: cinematic_history",
    "Format-argument parity: import_paste",
    "Nonempty translations: import_paste",
    "Format-argument parity: bank_month",
    "Nonempty translations: bank_month",
    "Format-argument parity: security",
    "Nonempty translations: security",
    "Format-argument parity: pin_mismatch",
    "Nonempty translations: pin_mismatch",
    "Format-argument parity: import_text",
    "Nonempty translations: import_text",
    "Format-argument parity: pin_digit_count",
    "Nonempty translations: pin_digit_count",
    "Format-argument parity: no_matches",
    "Nonempty translations: no_matches",
    "Format-argument parity: diagnostics_show_button",
    "Nonempty translations: diagnostics_show_button",
    "Format-argument parity: clear_filters",
    "Nonempty translations: clear_filters",
    "Format-argument parity: cinematic_month_average",
    "Nonempty translations: cinematic_month_average",
    "Format-argument parity: installation_id_copied",
    "Nonempty translations: installation_id_copied",
    "Format-argument parity: pay_source_required",
    "Nonempty translations: pay_source_required",
    "Format-argument parity: overview",
    "Nonempty translations: overview",
    "Format-argument parity: bank_access_yes",
    "Nonempty translations: bank_access_yes",
    "Format-argument parity: import_mode_notify",
    "Nonempty translations: import_mode_notify",
    "Format-argument parity: help_premium_body",
    "Nonempty translations: help_premium_body",
    "Format-argument parity: category_other",
    "Nonempty translations: category_other",
    "Format-argument parity: use_pin",
    "Nonempty translations: use_pin",
    "Format-argument parity: mascot_animations",
    "Nonempty translations: mascot_animations",
    "Format-argument parity: color_hue",
    "Nonempty translations: color_hue",
    "Format-argument parity: pin_incorrect",
    "Nonempty translations: pin_incorrect",
    "Format-argument parity: brand_copyright",
    "Nonempty translations: brand_copyright",
    "Format-argument parity: total_balance",
    "Nonempty translations: total_balance",
    "Format-argument parity: cinematic_apply",
    "Nonempty translations: cinematic_apply",
    "Format-argument parity: character_count",
    "Nonempty translations: character_count",
    "Format-argument parity: import_empty",
    "Nonempty translations: import_empty",
    "Format-argument parity: cinematic_expenses_period",
    "Nonempty translations: cinematic_expenses_period",
    "Format-argument parity: voice_smalltalk_greeting",
    "Nonempty translations: voice_smalltalk_greeting",
    "Format-argument parity: premium_feature_mascot",
    "Nonempty translations: premium_feature_mascot",
    "Format-argument parity: cinematic_less_than_previous",
    "Nonempty translations: cinematic_less_than_previous",
    "Format-argument parity: onboard_title_two",
    "Nonempty translations: onboard_title_two",
    "Format-argument parity: brand_intro_body",
    "Nonempty translations: brand_intro_body",
    "Format-argument parity: color_surface",
    "Nonempty translations: color_surface",
    "Format-argument parity: premium_source_developer",
    "Nonempty translations: premium_source_developer",
    "Format-argument parity: voice_smalltalk_compliment",
    "Nonempty translations: voice_smalltalk_compliment",
    "Format-argument parity: rates_empty",
    "Nonempty translations: rates_empty",
    "Format-argument parity: import_confirm_enabled",
    "Nonempty translations: import_confirm_enabled",
    "Format-argument parity: help_security_title",
    "Nonempty translations: help_security_title",
    "Format-argument parity: api_imported_last",
    "Nonempty translations: api_imported_last",
    "Format-argument parity: push_howto",
    "Nonempty translations: push_howto",
    "Format-argument parity: billing_unavailable",
    "Nonempty translations: billing_unavailable",
    "Format-argument parity: bank_battery",
    "Nonempty translations: bank_battery",
    "Format-argument parity: entry_title",
    "Nonempty translations: entry_title",
    "Format-argument parity: cinematic_more",
    "Nonempty translations: cinematic_more",
    "Format-argument parity: bank_time_received",
    "Nonempty translations: bank_time_received",
    "Format-argument parity: help_banks_body",
    "Nonempty translations: help_banks_body",
    "Format-argument parity: color_muted",
    "Nonempty translations: color_muted",
    "Format-argument parity: security_error",
    "Nonempty translations: security_error",
    "Format-argument parity: bank_sms_permission",
    "Nonempty translations: bank_sms_permission",
    "Format-argument parity: privat_api_howto",
    "Nonempty translations: privat_api_howto",
    "Format-argument parity: search_scope",
    "Nonempty translations: search_scope",
    "Format-argument parity: bank_dashboard",
    "Nonempty translations: bank_dashboard",
    "Format-argument parity: premium_expires",
    "Nonempty translations: premium_expires",
    "Format-argument parity: bank_category_stats",
    "Nonempty translations: bank_category_stats",
    "Format-argument parity: bank_sms_yes",
    "Nonempty translations: bank_sms_yes",
    "Format-argument parity: handle_copied",
    "Nonempty translations: handle_copied",
    "Format-argument parity: total_thirty_days",
    "Nonempty translations: total_thirty_days",
    "Format-argument parity: voice_ask_source",
    "Nonempty translations: voice_ask_source",
    "Format-argument parity: api_error_rate",
    "Nonempty translations: api_error_rate",
    "Format-argument parity: mascot_gray",
    "Nonempty translations: mascot_gray",
    "Format-argument parity: category_transfer",
    "Nonempty translations: category_transfer",
    "Format-argument parity: import_unsupported",
    "Nonempty translations: import_unsupported",
    "Format-argument parity: start_over",
    "Nonempty translations: start_over",
    "Format-argument parity: activation_code_label",
    "Nonempty translations: activation_code_label",
    "Format-argument parity: save_profile",
    "Nonempty translations: save_profile",
    "Format-argument parity: skip",
    "Nonempty translations: skip",
    "Format-argument parity: color_apply",
    "Nonempty translations: color_apply",
    "Format-argument parity: unlock_biometric",
    "Nonempty translations: unlock_biometric",
    "Format-argument parity: voice_reply_balance",
    "Nonempty translations: voice_reply_balance",
    "Format-argument parity: biometric_explanation",
    "Nonempty translations: biometric_explanation",
    "Format-argument parity: billing_pending",
    "Nonempty translations: billing_pending",
    "Format-argument parity: cinematic_period",
    "Nonempty translations: cinematic_period",
    "Format-argument parity: bank_notification_permission",
    "Nonempty translations: bank_notification_permission",
    "Format-argument parity: theme_system",
    "Nonempty translations: theme_system",
    "Format-argument parity: mascot_style",
    "Nonempty translations: mascot_style",
    "Format-argument parity: auto_lock_explanation",
    "Nonempty translations: auto_lock_explanation",
    "Format-argument parity: brand_author_signature",
    "Nonempty translations: brand_author_signature",
    "Format-argument parity: api_connect",
    "Nonempty translations: api_connect",
    "Format-argument parity: help_entries_body",
    "Nonempty translations: help_entries_body",
    "Format-argument parity: premium_offline_notice",
    "Nonempty translations: premium_offline_notice",
    "Format-argument parity: brand_intro_title",
    "Nonempty translations: brand_intro_title",
    "Format-argument parity: color_chart_bottom",
    "Nonempty translations: color_chart_bottom",
    "Format-argument parity: bank_privacy_body",
    "Nonempty translations: bank_privacy_body",
    "Format-argument parity: onboard_title_three",
    "Nonempty translations: onboard_title_three",
    "Format-argument parity: rates_as_of",
    "Nonempty translations: rates_as_of",
    "Format-argument parity: reference_total_for",
    "Nonempty translations: reference_total_for",
    "Format-argument parity: import_mode_sms",
    "Nonempty translations: import_mode_sms",
    "Format-argument parity: pin_locked",
    "Nonempty translations: pin_locked",
    "Format-argument parity: bank_limit_recent",
    "Nonempty translations: bank_limit_recent",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/CinematicAnalyticsTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/CinematicAnalyticsTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/CinematicAnalyticsTest.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/ColorPreferencesTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/ColorPreferencesTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/ColorPreferencesTest.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/LedgerUseCasesTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/LedgerUseCasesTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/LedgerUseCasesTest.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/MemoryEntryRepository.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/MemoryEntryRepository.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/MemoryEntryRepository.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/MoneyAndPolicyTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/MoneyAndPolicyTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/MoneyAndPolicyTest.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/UpgradePolicyTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/UpgradePolicyTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/UpgradePolicyTest.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankParserTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankParserTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankParserTest.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/MonobankMapperTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/MonobankMapperTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/MonobankMapperTest.kt",
    "No NUL bytes: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccessTest.kt",
    "No implementation markers: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccessTest.kt",
    "Lexically balanced Kotlin delimiters: domain/src/test/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccessTest.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/Analytics.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/Analytics.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/Analytics.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/Analytics.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/ColorPreferences.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/ColorPreferences.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/ColorPreferences.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/ColorPreferences.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/Models.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/Models.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/Models.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/Models.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/UseCases.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/UseCases.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/UseCases.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/UseCases.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/CinematicAnalytics.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/CinematicAnalytics.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/CinematicAnalytics.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/CinematicAnalytics.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/Repositories.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/Repositories.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/Repositories.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/Repositories.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankParser.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankParser.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankParser.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankParser.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankRepository.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankRepository.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankRepository.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankRepository.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/MonobankMapper.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/MonobankMapper.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/MonobankMapper.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/MonobankMapper.kt",
    "No NUL bytes: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccess.kt",
    "No implementation markers: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccess.kt",
    "Pure domain boundary: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccess.kt",
    "Lexically balanced Kotlin delimiters: domain/src/main/kotlin/com/semmyx/ledger/domain/bank/BankPeriodAccess.kt",
    "No NUL bytes: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No implementation markers: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No NUL bytes: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playRelease/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No implementation markers: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No NUL bytes: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/legacyV7Debug/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No NUL bytes: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No implementation markers: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No NUL bytes: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No implementation markers: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/kspCaches/playDebug/backups/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playRelease/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No implementation markers: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/legacyV7Debug/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/LedgerDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/RateDao_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/local/LedgerDatabase_Impl.kt",
    "No NUL bytes: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No implementation markers: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "Lexically balanced Kotlin delimiters: data/build/generated/ksp/playDebug/kotlin/com/semmyx/ledger/data/bank/BankDao_Impl.kt",
    "No NUL bytes: data/src/test/kotlin/com/semmyx/ledger/data/PurchaseSignatureVerifierTest.kt",
    "No implementation markers: data/src/test/kotlin/com/semmyx/ledger/data/PurchaseSignatureVerifierTest.kt",
    "Lexically balanced Kotlin delimiters: data/src/test/kotlin/com/semmyx/ledger/data/PurchaseSignatureVerifierTest.kt",
    "No NUL bytes: data/src/test/kotlin/com/semmyx/ledger/data/SearchPatternTest.kt",
    "No implementation markers: data/src/test/kotlin/com/semmyx/ledger/data/SearchPatternTest.kt",
    "Lexically balanced Kotlin delimiters: data/src/test/kotlin/com/semmyx/ledger/data/SearchPatternTest.kt",
    "No NUL bytes: data/src/test/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifierTest.kt",
    "No implementation markers: data/src/test/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifierTest.kt",
    "Lexically balanced Kotlin delimiters: data/src/test/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifierTest.kt",
    "No NUL bytes: data/src/test/kotlin/com/semmyx/ledger/data/bank/MonobankClientTest.kt",
    "No implementation markers: data/src/test/kotlin/com/semmyx/ledger/data/bank/MonobankClientTest.kt",
    "Lexically balanced Kotlin delimiters: data/src/test/kotlin/com/semmyx/ledger/data/bank/MonobankClientTest.kt",
    "No NUL bytes: data/src/test/kotlin/com/semmyx/ledger/data/bank/PrivatBankClientTest.kt",
    "No implementation markers: data/src/test/kotlin/com/semmyx/ledger/data/bank/PrivatBankClientTest.kt",
    "Lexically balanced Kotlin delimiters: data/src/test/kotlin/com/semmyx/ledger/data/bank/PrivatBankClientTest.kt",
    "No NUL bytes: data/src/play/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt",
    "No implementation markers: data/src/play/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt",
    "Lexically balanced Kotlin delimiters: data/src/play/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifier.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifier.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/billing/DeveloperSubscriptionVerifier.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/billing/PurchaseSignatureVerifier.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/billing/PurchaseSignatureVerifier.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/billing/PurchaseSignatureVerifier.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/billing/PlayPremiumRepository.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/billing/PlayPremiumRepository.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/billing/PlayPremiumRepository.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDao.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerDatabase.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerEntities.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerEntities.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/local/LedgerEntities.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/remote/RateApi.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/remote/RateApi.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/remote/RateApi.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/repository/DataStorePreferences.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/repository/DataStorePreferences.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/repository/DataStorePreferences.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/repository/OfflineRateRepository.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/repository/OfflineRateRepository.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/repository/OfflineRateRepository.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/repository/RoomEntryRepository.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/repository/RoomEntryRepository.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/repository/RoomEntryRepository.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/security/BiometricKeys.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/security/BiometricKeys.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/security/BiometricKeys.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreSecurityRepository.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreSecurityRepository.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreSecurityRepository.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreVault.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreVault.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/security/KeystoreVault.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/bank/BankEntities.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/bank/BankEntities.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/bank/BankEntities.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/bank/BankImportRepository.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/bank/BankImportRepository.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/bank/BankImportRepository.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/bank/MonobankClient.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/bank/MonobankClient.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/bank/MonobankClient.kt",
    "No NUL bytes: data/src/main/kotlin/com/semmyx/ledger/data/bank/PrivatBankClient.kt",
    "No implementation markers: data/src/main/kotlin/com/semmyx/ledger/data/bank/PrivatBankClient.kt",
    "Lexically balanced Kotlin delimiters: data/src/main/kotlin/com/semmyx/ledger/data/bank/PrivatBankClient.kt",
    "No NUL bytes: data/src/legacyV7/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt",
    "No implementation markers: data/src/legacyV7/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt",
    "Lexically balanced Kotlin delimiters: data/src/legacyV7/kotlin/com/semmyx/ledger/data/billing/BillingVersionAdapter.kt",
    "No NUL bytes: data/src/androidTest/kotlin/com/semmyx/ledger/data/KeystoreSecurityTest.kt",
    "No implementation markers: data/src/androidTest/kotlin/com/semmyx/ledger/data/KeystoreSecurityTest.kt",
    "Lexically balanced Kotlin delimiters: data/src/androidTest/kotlin/com/semmyx/ledger/data/KeystoreSecurityTest.kt",
    "No NUL bytes: data/src/androidTest/kotlin/com/semmyx/ledger/data/RoomRepositoryTest.kt",
    "No implementation markers: data/src/androidTest/kotlin/com/semmyx/ledger/data/RoomRepositoryTest.kt",
    "Lexically balanced Kotlin delimiters: data/src/androidTest/kotlin/com/semmyx/ledger/data/RoomRepositoryTest.kt",
    "No NUL bytes: app/src/test/kotlin/com/semmyx/ledger/MainDispatcherRule.kt",
    "No implementation markers: app/src/test/kotlin/com/semmyx/ledger/MainDispatcherRule.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/kotlin/com/semmyx/ledger/MainDispatcherRule.kt",
    "No NUL bytes: app/src/test/kotlin/com/semmyx/ledger/ViewModelTest.kt",
    "No implementation markers: app/src/test/kotlin/com/semmyx/ledger/ViewModelTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/kotlin/com/semmyx/ledger/ViewModelTest.kt",
    "No NUL bytes: app/src/test/kotlin/com/semmyx/ledger/BankViewModelAccessTest.kt",
    "No implementation markers: app/src/test/kotlin/com/semmyx/ledger/BankViewModelAccessTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/kotlin/com/semmyx/ledger/BankViewModelAccessTest.kt",
    "No NUL bytes: app/src/test/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistantParserTest.kt",
    "No implementation markers: app/src/test/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistantParserTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistantParserTest.kt",
    "No NUL bytes: app/src/test/kotlin/com/semmyx/ledger/voice/FinanceUpgradeTest.kt",
    "No implementation markers: app/src/test/kotlin/com/semmyx/ledger/voice/FinanceUpgradeTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/kotlin/com/semmyx/ledger/voice/FinanceUpgradeTest.kt",
    "No NUL bytes: app/src/test/kotlin/com/semmyx/ledger/voice/VoiceSourceTest.kt",
    "No implementation markers: app/src/test/kotlin/com/semmyx/ledger/voice/VoiceSourceTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/kotlin/com/semmyx/ledger/voice/VoiceSourceTest.kt",
    "No NUL bytes: app/src/test/kotlin/com/semmyx/ledger/cat/CatFaceMeshTest.kt",
    "No implementation markers: app/src/test/kotlin/com/semmyx/ledger/cat/CatFaceMeshTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/kotlin/com/semmyx/ledger/cat/CatFaceMeshTest.kt",
    "No NUL bytes: app/src/test/java/com/example/myapplication/ExampleUnitTest.kt",
    "No implementation markers: app/src/test/java/com/example/myapplication/ExampleUnitTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/test/java/com/example/myapplication/ExampleUnitTest.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/LedgerApplication.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/LedgerApplication.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/LedgerApplication.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/AppContainer.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/AppContainer.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/AppContainer.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/MainActivity.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/MainActivity.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/MainActivity.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsReceiver.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsReceiver.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsReceiver.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationListener.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationListener.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationListener.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankScreen.kt",
    "Resolved app string: bank_dashboard",
    "Resolved app string: close",
    "Resolved app string: add_entry",
    "Resolved app string: storage_error",
    "Resolved app string: bank_setup",
    "Resolved app string: bank_import_title",
    "Resolved app string: bank_consent",
    "Resolved app string: bank_enabled",
    "Resolved app string: bank_auto",
    "Resolved app string: import_mode_title",
    "Resolved app string: import_mode_notify",
    "Resolved app string: import_mode_api",
    "Resolved app string: import_mode_sms",
    "Resolved app string: api_howto",
    "Resolved app string: api_open_site",
    "Resolved app string: api_token_label",
    "Resolved app string: api_connecting",
    "Resolved app string: api_connect",
    "Resolved app string: api_connected",
    "Resolved app string: api_last_sync",
    "Resolved app string: api_imported_last",
    "Resolved app string: api_syncing",
    "Resolved app string: api_sync_now",
    "Resolved app string: api_disconnect",
    "Resolved app string: api_error_bad_token",
    "Resolved app string: api_error_rate",
    "Resolved app string: api_error_no_accounts",
    "Resolved app string: api_error_network",
    "Resolved app string: privat_api_title",
    "Resolved app string: privat_api_howto",
    "Resolved app string: privat_api_site",
    "Resolved app string: privat_api_token",
    "Resolved app string: api_connecting",
    "Resolved app string: api_connect",
    "Resolved app string: privat_api_connected",
    "Resolved app string: api_last_sync",
    "Resolved app string: api_sync_now",
    "Resolved app string: api_disconnect",
    "Resolved app string: privat_api_error",
    "Resolved app string: other_api_note",
    "Resolved app string: sms_howto",
    "Resolved app string: bank_sms_yes",
    "Resolved app string: bank_sms_no",
    "Resolved app string: bank_sms_permission",
    "Resolved app string: sms_scanning",
    "Resolved app string: sms_scan",
    "Resolved app string: bank_sms_no",
    "Resolved app string: sms_scan_result",
    "Resolved app string: sms_restricted_tip",
    "Resolved app string: push_howto",
    "Resolved app string: bank_access_yes",
    "Resolved app string: bank_access_no",
    "Resolved app string: bank_notification_permission",
    "Resolved app string: bank_app_permissions",
    "Resolved app string: bank_battery",
    "Resolved app string: import_confirm_permission",
    "Resolved app string: import_confirm_enabled",
    "Resolved app string: bank_privacy",
    "Resolved app string: import_diagnostics",
    "Resolved app string: import_connected",
    "Resolved app string: import_disconnected",
    "Resolved app string: import_counts",
    "Resolved app string: import_reconnect",
    "Resolved app string: import_package_settings",
    "Resolved app string: import_paste",
    "Resolved app string: import_queue_note",
    "Resolved app string: bank_day",
    "Resolved app string: bank_week",
    "Resolved app string: bank_month",
    "Resolved app string: all_time",
    "Resolved app string: expenses",
    "Resolved app string: all",
    "Resolved app string: bank_cash",
    "Resolved app string: bank_unknown",
    "Resolved app string: bank_budget_progress",
    "Resolved app string: bank_category_stats",
    "Resolved app string: bank_new_category",
    "Resolved app string: bank_review_count",
    "Resolved app string: bank_time_received",
    "Resolved app string: bank_transfer_review",
    "Resolved app string: bank_duplicate",
    "Resolved app string: expenses",
    "Resolved app string: income",
    "Resolved app string: bank_ignore",
    "Resolved app string: bank_recent",
    "Resolved app string: bank_cash",
    "Resolved app string: bank_limit_recent",
    "Resolved app string: bank_budget",
    "Resolved app string: save_entry",
    "Resolved app string: bank_budget_disable",
    "Resolved app string: bank_export",
    "Resolved app string: bank_clear_history",
    "Resolved app string: import_paste",
    "Resolved app string: import_text",
    "Resolved app string: income",
    "Resolved app string: expenses",
    "Resolved app string: import_unrecognized",
    "Resolved app string: import_manual_privacy",
    "Resolved app string: import_to_queue",
    "Resolved app string: cancel",
    "Resolved app string: import_package_settings",
    "Resolved app string: import_package_warning",
    "Resolved app string: save_entry",
    "Resolved app string: cancel",
    "Resolved app string: bank_new_category",
    "Resolved app string: category",
    "Resolved app string: save_entry",
    "Resolved app string: cancel",
    "Resolved app string: bank_clear_history",
    "Resolved app string: bank_clear_warning",
    "Resolved app string: bank_clear_history",
    "Resolved app string: cancel",
    "Resolved app string: bank_privacy",
    "Resolved app string: bank_privacy_body",
    "Resolved app string: done",
    "Resolved app string: import_disabled",
    "Resolved app string: import_unsupported",
    "Resolved app string: import_empty",
    "Resolved app string: import_group",
    "Resolved app string: import_unrecognized",
    "Resolved app string: import_auto_done",
    "Resolved app string: import_queued",
    "Resolved app string: bank_duplicate",
    "Resolved app string: import_repeated",
    "Resolved app string: storage_error",
    "Resolved app string: import_waiting",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/bank/BankViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsCatchUp.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsCatchUp.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankSmsCatchUp.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankImportWorker.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankImportWorker.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankImportWorker.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationDecoder.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationDecoder.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankNotificationDecoder.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/bank/BankAmountRow.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/bank/BankAmountRow.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/bank/BankAmountRow.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/diagnostics/CrashLogger.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/diagnostics/CrashLogger.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/diagnostics/CrashLogger.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/platform/BiometricUnlocker.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/platform/BiometricUnlocker.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/platform/BiometricUnlocker.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/platform/HighRefreshRate.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/platform/HighRefreshRate.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/platform/HighRefreshRate.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/platform/RateRefreshWorker.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/platform/RateRefreshWorker.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/platform/RateRefreshWorker.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/platform/OwnershipSeal.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/platform/OwnershipSeal.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/platform/OwnershipSeal.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceEntryParser.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceEntryParser.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceEntryParser.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceSmallTalk.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceSmallTalk.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceSmallTalk.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistant.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistant.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/voice/OptionalVoiceAssistant.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceFinanceQuery.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceFinanceQuery.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/voice/VoiceFinanceQuery.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/voice/SpokenMoneyFormatter.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/voice/SpokenMoneyFormatter.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/voice/SpokenMoneyFormatter.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/support/SupportNavigator.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/support/SupportNavigator.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/support/SupportNavigator.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/support/SupportWebActivity.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/support/SupportWebActivity.kt",
    "Resolved app string: telegram_support",
    "Resolved app string: back",
    "Resolved app string: support_network_error",
    "Resolved app string: retry",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/support/SupportWebActivity.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/support/HelpScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/support/HelpScreen.kt",
    "Resolved app string: help",
    "Resolved app string: close",
    "Resolved app string: help_heading",
    "Resolved app string: help_intro",
    "Resolved app string: contact_telegram",
    "Resolved app string: help_entries_title",
    "Resolved app string: help_entries_body",
    "Resolved app string: help_banks_title",
    "Resolved app string: help_banks_body",
    "Resolved app string: help_privacy_title",
    "Resolved app string: help_privacy_body",
    "Resolved app string: help_security_title",
    "Resolved app string: help_security_body",
    "Resolved app string: help_premium_title",
    "Resolved app string: help_premium_body",
    "Resolved app string: app_version",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/support/HelpScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/support/SupportSheet.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/support/SupportSheet.kt",
    "Resolved app string: here_to_help",
    "Resolved app string: support_intro",
    "Resolved app string: lead_developer",
    "Resolved app string: support_handle",
    "Resolved app string: support",
    "Resolved app string: copy_handle",
    "Resolved app string: handle_copied",
    "Resolved app string: contact_telegram",
    "Resolved app string: support_privacy",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/support/SupportSheet.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/navigation/AppRoot.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/navigation/AppRoot.kt",
    "Resolved app string: load_error_title",
    "Resolved app string: security_error",
    "Resolved app string: retry",
    "Resolved app string: need_help",
    "Resolved app string: biometric_title",
    "Resolved app string: biometric_subtitle",
    "Resolved app string: use_pin",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/navigation/AppRoot.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/navigation/AppViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/navigation/AppViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/navigation/AppViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/navigation/AppViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsScreen.kt",
    "Resolved app string: all_time",
    "Resolved app string: cinematic_period",
    "Resolved app string: all_time",
    "Resolved app string: cinematic_apply",
    "Resolved app string: back",
    "Resolved app string: back",
    "Resolved app string: cinematic_history",
    "Resolved app string: analytics_title",
    "Resolved app string: load_error_title",
    "Resolved app string: storage_error",
    "Resolved app string: retry",
    "Resolved app string: cinematic_expenses_period",
    "Resolved app string: cinematic_comparison",
    "Resolved app string: cinematic_more_than_previous",
    "Resolved app string: cinematic_less_than_previous",
    "Resolved app string: cinematic_month_average",
    "Resolved app string: cinematic_week_average",
    "Resolved app string: cinematic_expense_categories",
    "Resolved app string: cinematic_income_categories",
    "Resolved app string: cinematic_more",
    "Resolved app string: category_empty",
    "Resolved app string: back",
    "Resolved app string: expenses",
    "Resolved app string: income",
    "Resolved app string: reference_total_for",
    "Resolved app string: cinematic_expense_categories",
    "Resolved app string: cinematic_income_categories",
    "Resolved app string: all_categories",
    "Resolved app string: cinematic_transactions",
    "Resolved app string: category_empty",
    "Resolved app string: load_more",
    "Resolved app string: category_transaction_count",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsCharts.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsCharts.kt",
    "Resolved app string: cinematic_chart_accessibility",
    "Resolved app string: cinematic_tooltip",
    "Resolved app string: cinematic_more_buckets",
    "Resolved app string: cinematic_income_categories",
    "Resolved app string: cinematic_expense_categories",
    "Resolved app string: category_empty",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/analytics/AnalyticsCharts.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/auth/AuthViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/auth/AuthViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/auth/AuthViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/auth/AuthViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/auth/AuthenticationScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/auth/AuthenticationScreen.kt",
    "Resolved app string: unlock_title",
    "Resolved app string: confirm_pin",
    "Resolved app string: create_pin",
    "Resolved app string: pin_digit_count",
    "Resolved app string: pin_setup_subtitle",
    "Resolved app string: pin_unlock_subtitle",
    "Resolved app string: pin_locked",
    "Resolved app string: pin_incorrect",
    "Resolved app string: pin_mismatch",
    "Resolved app string: security_error",
    "Resolved app string: biometric_failed",
    "Resolved app string: unlock_biometric",
    "Resolved app string: erase_digit",
    "Resolved app string: start_over",
    "Resolved app string: need_help",
    "Resolved app string: pin_recovery_notice",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/auth/AuthenticationScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/common/CommonComponents.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/common/CommonComponents.kt",
    "Resolved app string: category_food",
    "Resolved app string: category_transport",
    "Resolved app string: category_home",
    "Resolved app string: category_health",
    "Resolved app string: category_leisure",
    "Resolved app string: category_salary",
    "Resolved app string: category_other",
    "Resolved app string: category_transfer",
    "Resolved app string: category_dining",
    "Resolved app string: category_cashback",
    "Resolved app string: storage_error",
    "Resolved app string: saved",
    "Resolved app string: entry_deleted",
    "Resolved app string: undo_expired",
    "Resolved app string: rate_offline",
    "Resolved app string: biometric_unavailable",
    "Resolved app string: name_invalid",
    "Resolved app string: entry_not_found",
    "Resolved app string: premium_activated",
    "Resolved app string: activation_invalid",
    "Resolved app string: billing_unavailable",
    "Resolved app string: billing_configuration",
    "Resolved app string: billing_network",
    "Resolved app string: billing_pending",
    "Resolved app string: billing_verification",
    "Resolved app string: billing_acknowledgement",
    "Resolved app string: billing_activation_code",
    "Resolved app string: overview",
    "Resolved app string: settings",
    "Resolved app string: help",
    "Resolved app string: analytics_title",
    "Resolved app string: bank_dashboard",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/common/CommonComponents.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/common/ViewModelSupport.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/common/ViewModelSupport.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/common/ViewModelSupport.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/dashboard/ActivityChart.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/dashboard/ActivityChart.kt",
    "Resolved app string: all_time",
    "Resolved app string: chart_description",
    "Resolved app string: cash_flow",
    "Resolved app string: last_7_days",
    "Resolved app string: last_30_days",
    "Resolved app string: last_30_days_plus",
    "Resolved app string: all_time",
    "Resolved app string: chart_empty",
    "Resolved app string: legend_income",
    "Resolved app string: legend_expense",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/dashboard/ActivityChart.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/dashboard/CategoryDetails.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/dashboard/CategoryDetails.kt",
    "Resolved app string: cinematic_expense_categories",
    "Resolved app string: cinematic_income_categories",
    "Resolved app string: cinematic_more",
    "Resolved app string: category_empty",
    "Resolved app string: all_time",
    "Resolved app string: last_30_days",
    "Resolved app string: last_7_days",
    "Resolved app string: expenses",
    "Resolved app string: income",
    "Resolved app string: back",
    "Resolved app string: last_7_days",
    "Resolved app string: last_30_days",
    "Resolved app string: all_time",
    "Resolved app string: expenses",
    "Resolved app string: income",
    "Resolved app string: total_all_time",
    "Resolved app string: total_thirty_days",
    "Resolved app string: total_seven_days",
    "Resolved app string: cinematic_expense_categories",
    "Resolved app string: cinematic_income_categories",
    "Resolved app string: category_transaction_count",
    "Resolved app string: category_transaction_count",
    "Resolved app string: load_more",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/dashboard/CategoryDetails.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardScreen.kt",
    "Resolved app string: entry_deleted",
    "Resolved app string: undo",
    "Resolved app string: storage_error",
    "Resolved app string: app_name",
    "Resolved app string: add_entry",
    "Resolved app string: voice_recognizer_unavailable",
    "Resolved app string: voice_recognizer_unavailable",
    "Resolved app string: voice_recognizer_unavailable",
    "Resolved app string: voice_recognizer_unavailable",
    "Resolved app string: income",
    "Resolved app string: expense",
    "Resolved app string: voice_entry_added",
    "Resolved app string: voice_ask_source",
    "Resolved app string: bank_card",
    "Resolved app string: bank_cash",
    "Resolved app string: cancel",
    "Resolved app string: voice_reply_balance",
    "Resolved app string: period_today",
    "Resolved app string: last_7_days",
    "Resolved app string: period_month",
    "Resolved app string: period_year",
    "Resolved app string: all_time",
    "Resolved app string: all_categories",
    "Resolved app string: expenses",
    "Resolved app string: income",
    "Resolved app string: voice_statistics",
    "Resolved app string: voice_ask_source",
    "Resolved app string: voice_reply_balance",
    "Resolved app string: voice_reply_spent_today",
    "Resolved app string: voice_reply_opening_entry",
    "Resolved app string: voice_reply_not_understood",
    "Resolved app string: voice_reply_not_understood",
    "Resolved app string: voice_permission_denied",
    "Resolved app string: voice_recognizer_unavailable",
    "Resolved app string: load_error_title",
    "Resolved app string: storage_error",
    "Resolved app string: retry",
    "Resolved app string: dashboard_heading",
    "Resolved app string: greeting",
    "Resolved app string: dashboard_subtitle",
    "Resolved app string: activity",
    "Resolved app string: search_entries",
    "Resolved app string: clear_search",
    "Resolved app string: all",
    "Resolved app string: income",
    "Resolved app string: expenses",
    "Resolved app string: search_scope",
    "Resolved app string: no_matches",
    "Resolved app string: empty_ledger_title",
    "Resolved app string: no_matches_body",
    "Resolved app string: empty_ledger_body",
    "Resolved app string: clear_filters",
    "Resolved app string: add_first_entry",
    "Resolved app string: delete_entry",
    "Resolved app string: load_more",
    "Resolved app string: voice_smalltalk_greeting",
    "Resolved app string: voice_smalltalk_how_are_you",
    "Resolved app string: voice_smalltalk_thanks",
    "Resolved app string: voice_smalltalk_who_are_you",
    "Resolved app string: voice_smalltalk_bye",
    "Resolved app string: voice_smalltalk_compliment",
    "Resolved app string: voice_smalltalk_joke",
    "Resolved app string: total_balance",
    "Resolved app string: income",
    "Resolved app string: expenses",
    "Resolved app string: total_balance",
    "Resolved app string: income",
    "Resolved app string: expenses",
    "Resolved app string: positive_amount",
    "Resolved app string: negative_amount",
    "Resolved app string: delete_named_entry",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/dashboard/DashboardViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/editor/EntryScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/editor/EntryScreen.kt",
    "Resolved app string: edit_entry",
    "Resolved app string: add_entry",
    "Resolved app string: back",
    "Resolved app string: load_error_title",
    "Resolved app string: storage_error",
    "Resolved app string: back",
    "Resolved app string: expense",
    "Resolved app string: income",
    "Resolved app string: amount",
    "Resolved app string: amount_invalid",
    "Resolved app string: pay_source_title",
    "Resolved app string: bank_card",
    "Resolved app string: bank_cash",
    "Resolved app string: pay_source_required",
    "Resolved app string: entry_title",
    "Resolved app string: title_optional_hint",
    "Resolved app string: date_invalid",
    "Resolved app string: save_entry",
    "Resolved app string: discard_title",
    "Resolved app string: discard_body",
    "Resolved app string: discard",
    "Resolved app string: keep_editing",
    "Resolved app string: done",
    "Resolved app string: cancel",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/editor/EntryScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/editor/EntryViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/editor/EntryViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/editor/EntryViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/editor/EntryViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/history/HistoryScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/history/HistoryScreen.kt",
    "Resolved app string: cinematic_history",
    "Resolved app string: back",
    "Resolved app string: all_time",
    "Resolved app string: history_search",
    "Resolved app string: all",
    "Resolved app string: income",
    "Resolved app string: expenses",
    "Resolved app string: storage_error",
    "Resolved app string: category_empty",
    "Resolved app string: bank_cash",
    "Resolved app string: bank_unknown",
    "Resolved app string: load_more",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/history/HistoryScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/history/HistoryViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/history/HistoryViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/history/HistoryViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/history/HistoryViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/onboarding/OnboardingScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/onboarding/OnboardingScreen.kt",
    "Resolved app string: brand_intro_title",
    "Resolved app string: onboard_title_one",
    "Resolved app string: onboard_title_two",
    "Resolved app string: onboard_title_three",
    "Resolved app string: brand_intro_body",
    "Resolved app string: onboard_body_one",
    "Resolved app string: onboard_body_two",
    "Resolved app string: onboard_body_three",
    "Resolved app string: app_name",
    "Resolved app string: skip",
    "Resolved app string: brand_author_signature",
    "Resolved app string: brand_design_label",
    "Resolved app string: brand_copyright",
    "Resolved app string: page_of",
    "Resolved app string: get_started",
    "Resolved app string: next",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/onboarding/OnboardingScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/cat/CatFaceMesh.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/cat/CatFaceMesh.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/cat/CatFaceMesh.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/cat/CatAnimation.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/cat/CatAnimation.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/cat/CatAnimation.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/cat/CatMeshRenderer.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/cat/CatMeshRenderer.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/cat/CatMeshRenderer.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/cat/CatAssistantCard.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/cat/CatAssistantCard.kt",
    "Resolved app string: voice_assistant_button",
    "Resolved app string: mascot_card_title",
    "Resolved app string: cat_locked_hint",
    "Resolved app string: voice_assistant_listening",
    "Resolved app string: cat_tap_hint",
    "Resolved app string: assistant_write_short",
    "Resolved app string: assistant_text_hint",
    "Resolved app string: assistant_send",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/cat/CatAssistantCard.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/settings/ColorSettings.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/settings/ColorSettings.kt",
    "Resolved app string: colors_title",
    "Resolved app string: colors_explanation",
    "Resolved app string: colors_collapse",
    "Resolved app string: colors_edit",
    "Resolved app string: colors_categories",
    "Resolved app string: income",
    "Resolved app string: expenses",
    "Resolved app string: colors_reset_all",
    "Resolved app string: color_hue",
    "Resolved app string: color_saturation",
    "Resolved app string: color_brightness",
    "Resolved app string: color_invalid",
    "Resolved app string: color_contrast_warning",
    "Resolved app string: color_reset",
    "Resolved app string: color_apply",
    "Resolved app string: cancel",
    "Resolved app string: color_accent",
    "Resolved app string: color_background",
    "Resolved app string: color_surface",
    "Resolved app string: color_text",
    "Resolved app string: color_muted",
    "Resolved app string: income",
    "Resolved app string: expenses",
    "Resolved app string: color_chart_top",
    "Resolved app string: color_chart_bottom",
    "Resolved app string: color_error",
    "Resolved app string: color_grid",
    "Resolved app string: color_icons",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/settings/ColorSettings.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/settings/SettingsScreen.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/settings/SettingsScreen.kt",
    "Resolved app string: load_error_title",
    "Resolved app string: storage_error",
    "Resolved app string: back",
    "Resolved app string: settings",
    "Resolved app string: close",
    "Resolved app string: lock_now",
    "Resolved app string: profile",
    "Resolved app string: local_profile",
    "Resolved app string: display_name",
    "Resolved app string: save_profile",
    "Resolved app string: language",
    "Resolved app string: language_instant",
    "Resolved app string: language_en",
    "Resolved app string: language_uk",
    "Resolved app string: bank_import_title",
    "Resolved app string: bank_consent",
    "Resolved app string: bank_dashboard",
    "Resolved app string: appearance",
    "Resolved app string: theme_system",
    "Resolved app string: theme_light",
    "Resolved app string: theme_dark",
    "Resolved app string: fps_title",
    "Resolved app string: fps_explanation",
    "Resolved app string: fps_auto",
    "Resolved app string: fps_value",
    "Resolved app string: fps_supported",
    "Resolved app string: fps_fallback",
    "Resolved app string: default_currency",
    "Resolved app string: currency_explanation",
    "Resolved app string: security",
    "Resolved app string: biometric_unlock",
    "Resolved app string: biometric_explanation",
    "Resolved app string: biometric_unavailable",
    "Resolved app string: auto_lock_explanation",
    "Resolved app string: lock_now",
    "Resolved app string: premium_name",
    "Resolved app string: premium_body",
    "Resolved app string: premium_feature_trends",
    "Resolved app string: premium_feature_mascot",
    "Resolved app string: premium_feature_support",
    "Resolved app string: premium_active",
    "Resolved app string: premium_source_developer",
    "Resolved app string: premium_source_trial",
    "Resolved app string: premium_source_play",
    "Resolved app string: premium_expires",
    "Resolved app string: premium_offline_notice",
    "Resolved app string: premium_local_notice",
    "Resolved app string: premium_assistant_toggle",
    "Resolved app string: premium_assistant_toggle_hint",
    "Resolved app string: premium_unlock",
    "Resolved app string: premium_buy",
    "Resolved app string: billing_configuration",
    "Resolved app string: trial_explanation",
    "Resolved app string: trial_14",
    "Resolved app string: mascot_style",
    "Resolved app string: mascot_gray",
    "Resolved app string: mascot_sky",
    "Resolved app string: mascot_animations",
    "Resolved app string: developer_purchase_title",
    "Resolved app string: developer_purchase_body",
    "Resolved app string: developer_purchase_banner",
    "Resolved app string: developer_purchase_note",
    "Resolved app string: developer_purchase_button",
    "Resolved app string: installation_id_label",
    "Resolved app string: installation_id_copied",
    "Resolved app string: installation_id_copy",
    "Resolved app string: activation_code_label",
    "Resolved app string: activation_code_hint",
    "Resolved app string: activation_code_button",
    "Resolved app string: reference_rates",
    "Resolved app string: rates_disclaimer",
    "Resolved app string: rates_empty",
    "Resolved app string: rate_value",
    "Resolved app string: rates_as_of",
    "Resolved app string: refreshing",
    "Resolved app string: refresh_rates",
    "Resolved app string: support",
    "Resolved app string: support_intro",
    "Resolved app string: contact_telegram",
    "Resolved app string: diagnostics_none",
    "Resolved app string: diagnostics_show_button",
    "Resolved app string: diagnostics_title",
    "Resolved app string: diagnostics_hint",
    "Resolved app string: diagnostics_copied",
    "Resolved app string: diagnostics_copy",
    "Resolved app string: diagnostics_clear",
    "Resolved app string: diagnostics_close",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/settings/SettingsScreen.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/settings/SettingsViewModel.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/settings/SettingsViewModel.kt",
    "Domain-only ViewModel dependencies: app/src/main/kotlin/com/semmyx/ledger/settings/SettingsViewModel.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/settings/SettingsViewModel.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/settings/RestorePurchasesAction.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/settings/RestorePurchasesAction.kt",
    "Resolved app string: restore_purchases",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/settings/RestorePurchasesAction.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/theme/AppearancePalette.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/theme/AppearancePalette.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/theme/AppearancePalette.kt",
    "No NUL bytes: app/src/main/kotlin/com/semmyx/ledger/theme/Theme.kt",
    "No implementation markers: app/src/main/kotlin/com/semmyx/ledger/theme/Theme.kt",
    "Lexically balanced Kotlin delimiters: app/src/main/kotlin/com/semmyx/ledger/theme/Theme.kt",
    "No NUL bytes: app/src/androidTest/kotlin/com/semmyx/ledger/ComposeScreensTest.kt",
    "No implementation markers: app/src/androidTest/kotlin/com/semmyx/ledger/ComposeScreensTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/androidTest/kotlin/com/semmyx/ledger/ComposeScreensTest.kt",
    "No NUL bytes: app/src/androidTest/kotlin/com/semmyx/ledger/BankBackgroundImportTest.kt",
    "No implementation markers: app/src/androidTest/kotlin/com/semmyx/ledger/BankBackgroundImportTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/androidTest/kotlin/com/semmyx/ledger/BankBackgroundImportTest.kt",
    "No NUL bytes: app/src/androidTest/kotlin/com/semmyx/ledger/CompactLayoutTest.kt",
    "No implementation markers: app/src/androidTest/kotlin/com/semmyx/ledger/CompactLayoutTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/androidTest/kotlin/com/semmyx/ledger/CompactLayoutTest.kt",
    "No NUL bytes: app/src/androidTest/kotlin/com/semmyx/ledger/BankScreenLayoutTest.kt",
    "No implementation markers: app/src/androidTest/kotlin/com/semmyx/ledger/BankScreenLayoutTest.kt",
    "Lexically balanced Kotlin delimiters: app/src/androidTest/kotlin/com/semmyx/ledger/BankScreenLayoutTest.kt",
    "No NUL bytes: app/src/androidTest/kotlin/com/semmyx/ledger/NativeScreenshots.kt",
    "No implementation markers: app/src/androidTest/kotlin/com/semmyx/ledger/NativeScreenshots.kt",
    "Lexically balanced Kotlin delimiters: app/src/androidTest/kotlin/com/semmyx/ledger/NativeScreenshots.kt",
    "Actual SQL: sums keep currencies separate",
    "Actual SQL: normalized Ukrainian search",
    "Actual SQL: literal percent search",
    "Actual SQL: bound input cannot change query structure",
    "Actual SQL: type filter",
    "Actual SQL: stable creation ordering and limit",
    "Actual SQL: first delete succeeds",
    "Actual SQL: repeated delete is idempotent",
    "Actual SQL: deleted entries are hidden",
    "Actual SQL: stale undo token is rejected",
    "Actual SQL: exact undo token restores entry",
    "Actual SQL: old tombstone is purged",
    "Actual SQL: date-window aggregation excludes deletions",
    "Android cloud backup disabled",
    "Cleartext traffic disabled",
    "Support WebView is non-exported",
    "Unicode normalization implemented in repository",
    "Requested v7 flavor included",
    "Current-submission v8 flavor included",
    "Android 16 target configured",
    "No destructive migration fallback"
  ]
}
```

## verification/upgrade-sql.json

```json
{
  "kind": "exact-Room-SQL-in-SQLite",
  "passed": 14,
  "checks": [
    "Calendar month: exact food sum across more than 50 records",
    "Calendar month: all expense categories",
    "Income excludes expenses",
    "All-time includes earlier years and excludes deleted/future/currency",
    "Empty category returns zero",
    "Category pagination initial window plus sentinel",
    "Load more obtains every category transaction",
    "Transaction details retained",
    "Seven-day details share exact seven-day bounds",
    "Category header uses full dataset rather than current page",
    "Assistant statistics exclude newly deleted entries",
    "Undo restores aggregate",
    "Income/expense counts remain separate inside one category",
    "Combined category detail count remains backwards compatible"
  ],
  "compiled_android": false,
  "executed_junit": false
}
```

## verification/voice-regex-jvm.json

```json
{
  "kind": "exact-source-regex-on-JVM",
  "passed": 7,
  "compiled_kotlin": false,
  "compiled_android": false,
  "output": "JVM monetary-regex checks passed: 7"
}
```
