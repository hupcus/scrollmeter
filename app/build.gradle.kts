import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing comes only from environment variables (pattern from DETECT), so no keystore or
// password ever lives in the repository. Without SIGNING_KEYSTORE_PATH the release build stays
// unsigned and still succeeds (CI, local gates). A path that is set but wrong, or a missing
// password, fails the build: a release silently left unsigned is worse than a loud error.
// tools/build_release.sh fills the variables from the macOS Keychain.
val signingKeystore: File? = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
    ?.takeIf { it.isNotBlank() }
    ?.let { path ->
        File(path).also { require(it.isFile) { "SIGNING_KEYSTORE_PATH does not point to a file" } }
    }

fun signingEnv(name: String): String =
    requireNotNull(providers.environmentVariable(name).orNull?.takeIf { it.isNotEmpty() }) {
        "$name must be set when SIGNING_KEYSTORE_PATH is"
    }

android {
    namespace = "com.scrollmeter.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scrollmeter.app"
        // scrollDeltaX/Y exist from API 28 (ADR-002), but Android 9 delivers a scroll-only service the
        // events of the "active" window alone, which it hardly ever updates for such a service (ADR-033).
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = signingKeystore
                storePassword = signingEnv("SIGNING_STORE_PASSWORD")
                keyAlias = signingEnv("SIGNING_KEY_ALIAS")
                keyPassword = signingEnv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Czech is the default (values/, D17), English the one translation (values-en/, ADR-032). The
    // generated locale config lets Android 13+ pick the app's language per app; the filter drops the
    // libraries' other translations, so a German phone does not get German dialogs in a Czech app.
    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("cs", "en")
    }

    testOptions {
        unitTests {
            // Robolectric (AccessibilityEvent parser test) needs the merged manifest and resources.
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // D14: bottom navigation with type-safe (@Serializable) routes, from Phase 5.
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Calibration and (from Phase 6) settings — DataStore Preferences, pinned in libs.versions.toml.
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
