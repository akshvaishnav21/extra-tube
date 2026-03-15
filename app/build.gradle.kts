import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)       // Hilt annotation processing
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.parcelize)  // compiler plugin — enables @Parcelize
    alias(libs.plugins.kotlin.compose)    // required in Kotlin 2.0+ when compose = true
}

android {
    namespace = "com.extratube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.extratube"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        // Enables BuildConfig.DEBUG for conditional logging and StrictMode
        buildConfig = true
    }

    // With Kotlin 2.x the Compose compiler is BUNDLED with the Kotlin compiler plugin.
    // Do NOT set kotlinCompilerExtensionVersion here — it causes a build error with Kotlin 2.x.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }

    packaging {
        resources {
            // NewPipeExtractor bundles Jackson and Rhino (JS engine) which include
            // duplicate META-INF files. These exclusions prevent APK packaging failures.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/INDEX.LIST"
            // Rhino JS engine
            excludes += "mozilla/public-suffix-list.txt"
        }
    }
}

// Required for Hilt: allows kapt to resolve types it cannot see at annotation
// processing time (Hilt-generated classes referencing app classes).
// Without this, @HiltViewModel and @AndroidEntryPoint produce cryptic kapt errors.
kapt {
    correctErrorTypes = true
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────────────────
    // Pins all compose-* library versions consistently via a single BOM import.
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── Activity + ViewModel ─────────────────────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // lifecycle-runtime-compose provides collectAsStateWithLifecycle()
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation(libs.androidx.navigation.compose)

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    // hiltViewModel() helper for Compose
    implementation(libs.androidx.hilt.navigation.compose)

    // ── Media3 (ExoPlayer) ───────────────────────────────────────────────────
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)   // future live stream support
    implementation(libs.androidx.media3.exoplayer.dash)  // DASH stream support
    implementation(libs.androidx.media3.ui)              // StyledPlayerView
    implementation(libs.androidx.media3.session)         // MediaSession + MediaSessionService

    // ── NewPipeExtractor (JitPack) ────────────────────────────────────────────
    implementation(libs.newpipe.extractor)

    // ── OkHttp ───────────────────────────────────────────────────────────────
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // ── Coil ─────────────────────────────────────────────────────────────────
    implementation(libs.coil.compose)

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // ── Unit Tests ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // ── Instrumented Tests ───────────────────────────────────────────────────
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
