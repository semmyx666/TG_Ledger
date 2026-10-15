import java.util.Properties
import java.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec

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
// Per-project setup is authoritative, so stale ~/.gradle values cannot silently override it.
val activationFile = rootProject.file("activation-public.properties")
val activationProperties = Properties().apply {
    if (activationFile.exists()) activationFile.inputStream().use { load(it) }
}
val developerKey = if (activationFile.exists()) activationProperties.getProperty("DEVELOPER_SUBSCRIPTION_PUBLIC_KEY", "").trim()
    else providers.gradleProperty("DEVELOPER_SUBSCRIPTION_PUBLIC_KEY").orElse("").get().trim()
val developerKeyId = if (developerKey.isBlank()) "" else try {
    val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(developerKey))) as RSAPublicKey
    check(key.modulus.bitLength() >= 2048)
    MessageDigest.getInstance("SHA-256").digest(key.encoded).take(8).joinToString("") { "%02X".format(it) }
} catch (error: Exception) { throw GradleException("Invalid activation public key. Run START_ACTIVATION.cmd to configure this project.", error) }
if (providers.gradleProperty("REQUIRE_DEVELOPER_ACTIVATION").orElse("false").get().toBoolean() && developerKeyId.isEmpty()) {
    throw GradleException("Activation is not configured. Run START_ACTIVATION.cmd, import/create your key, then build again.")
}
tasks.register("activationDoctor") {
    doLast { println("Developer activation Key ID: " + developerKeyId.ifEmpty { "NOT CONFIGURED" }) }
}
val legacyApproved = providers.gradleProperty("LEGACY_BILLING_EXTENSION_APPROVED").orElse("false")

android {
    namespace = "com.semmyx.ledger"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.semmyx.ledger"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "4.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["activationKeyId"] = developerKeyId
        buildConfigField("String", "PLAY_LICENSE_KEY", quoted(playKey.get()))
        buildConfigField("String", "PREMIUM_PRODUCT_ID", quoted(product.get()))
        buildConfigField("String", "DEVELOPER_SUBSCRIPTION_PUBLIC_KEY", quoted(developerKey))
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
