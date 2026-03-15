# ExtraTube — System Design Document

## 1. Architecture Overview

ExtraTube uses **MVVM + Clean Architecture** with strict unidirectional dependency flow. The three layers have enforced boundaries: the UI layer never imports from the data layer; all cross-layer communication goes through domain interfaces.

```
┌──────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                        │
│                                                              │
│   Composables  ←──→  ViewModels  ←──→  UI State (sealed)    │
│   (SearchScreen, PlayerScreen, ErrorScreen, NavGraph)        │
│                                                              │
│   State: collectAsStateWithLifecycle() — never collectAsState │
└──────────────────────────────┬───────────────────────────────┘
                               │ calls UseCases (domain interfaces)
┌──────────────────────────────▼───────────────────────────────┐
│                      DOMAIN LAYER                             │
│                                                              │
│   UseCases  ←──→  Repository Interfaces  ←──→  Models       │
│   (SearchVideosUseCase, GetVideoStreamsUseCase, ...)          │
│                                                              │
│   Pure Kotlin — zero Android framework imports               │
└──────────────────────────────┬───────────────────────────────┘
                               │ implements interfaces (Hilt @Binds)
┌──────────────────────────────▼───────────────────────────────┐
│                       DATA LAYER                              │
│                                                              │
│   RepositoryImpl  ←──→  NewPipeService  ←──→  OkHttp        │
│   (SearchRepositoryImpl, StreamRepositoryImpl)               │
│                                                              │
│   All extractor calls: withContext(Dispatchers.IO)           │
└──────────────────────────────────────────────────────────────┘
```

**Why MVVM + Clean Architecture?**
- ViewModels survive configuration changes (screen rotation), preserving ExoPlayer state
- Repository interfaces allow unit testing without network calls (fake implementations)
- Domain layer remains portable — no Android dependencies means the business logic is testable on JVM
- Hilt enforces singleton scoping for `PlayerManager`, preventing duplicate ExoPlayer instances

---

## 2. Complete Project File Tree

```
extra-tube/
├── settings.gradle.kts                        # repo + JitPack config
├── build.gradle.kts                           # project-level (plugin versions only)
├── gradle.properties                          # JVM args, R8, config cache flags
├── .gitignore                                 # covers local.properties, *.jks, build/, .gradle/
├── local.properties                           # GITIGNORED — Android SDK path
├── gradle/
│   ├── libs.versions.toml                     # single source of truth for all versions
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties          # Gradle 8.9+
└── app/
    ├── build.gradle.kts                       # app-level — plugins, android{}, dependencies{}
    ├── proguard-rules.pro                     # NewPipe, Jackson, Rhino, OkHttp, Hilt, Media3 rules
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/extratube/
        │   │   ├── ExtraTubeApplication.kt         # @HiltAndroidApp; NewPipe.init(); StrictMode (debug)
        │   │   ├── MainActivity.kt                 # @AndroidEntryPoint; ComponentActivity; edge-to-edge
        │   │   │
        │   │   ├── di/
        │   │   │   ├── AppModule.kt                # OkHttpClient, OkHttpDownloader, PlayerManager, NewPipe.init
        │   │   │   └── RepositoryModule.kt         # @Binds SearchRepository←Impl, StreamRepository←Impl
        │   │   │
        │   │   ├── data/
        │   │   │   ├── remote/
        │   │   │   │   ├── OkHttpDownloader.kt     # implements NewPipe Downloader (synchronous OkHttp)
        │   │   │   │   └── NewPipeService.kt       # all extractor calls; wraps in withContext(IO)
        │   │   │   ├── repository/
        │   │   │   │   ├── SearchRepositoryImpl.kt # implements SearchRepository; delegates to NewPipeService
        │   │   │   │   └── StreamRepositoryImpl.kt # implements StreamRepository; delegates to NewPipeService
        │   │   │   └── mapper/
        │   │   │       └── VideoMapper.kt          # StreamInfoItem → SearchResult; seconds → MM:SS
        │   │   │
        │   │   ├── domain/
        │   │   │   ├── model/
        │   │   │   │   ├── Resource.kt             # sealed: Loading / Success<T> / Error
        │   │   │   │   ├── SearchResult.kt         # videoId, title, thumbnailUrl, channelName, viewCount, duration
        │   │   │   │   └── StreamInfo.kt           # videoUrl, audioUrl, qualityLabel, title, channelName, thumbnailUrl
        │   │   │   ├── repository/
        │   │   │   │   ├── SearchRepository.kt     # interface — search() + getNextPage()
        │   │   │   │   └── StreamRepository.kt     # interface — getStreams(videoId)
        │   │   │   └── usecase/
        │   │   │       ├── SearchVideosUseCase.kt  # validates query; delegates; wraps errors
        │   │   │       ├── GetNextPageUseCase.kt   # delegates getNextPage; appends results
        │   │   │       └── GetVideoStreamsUseCase.kt # fetches DASH streams; selects best quality
        │   │   │
        │   │   ├── presentation/
        │   │   │   ├── theme/
        │   │   │   │   ├── Color.kt                # dark palette tokens (surface, background, primary, …)
        │   │   │   │   ├── Theme.kt                # ExtraTubeTheme — darkColorScheme() ALWAYS, no system check
        │   │   │   │   ├── Type.kt                 # Material3 typography scale
        │   │   │   │   └── Shape.kt                # corner shapes (small=4dp, medium=8dp, large=16dp)
        │   │   │   ├── navigation/
        │   │   │   │   ├── NavGraph.kt             # NavHost: "search" (start), "player/{videoId}"
        │   │   │   │   └── Screen.kt               # sealed class Screen(val route: String)
        │   │   │   ├── components/
        │   │   │   │   └── ErrorScreen.kt          # reusable: icon + message + Retry button
        │   │   │   ├── search/
        │   │   │   │   ├── SearchScreen.kt         # Scaffold + TopAppBar(SearchBar) + LazyColumn
        │   │   │   │   ├── SearchViewModel.kt      # @HiltViewModel; debounce pipeline; pagination
        │   │   │   │   ├── SearchUiState.kt        # sealed: Idle / Loading / Success / Error
        │   │   │   │   └── components/
        │   │   │   │       ├── SearchBar.kt        # TextField with leading icon + trailing clear
        │   │   │   │       ├── SearchResultItem.kt # AsyncImage + title + channel + viewCount
        │   │   │   │       └── ShimmerResultItem.kt # pure Compose animation; no library
        │   │   │   └── player/
        │   │   │       ├── PlayerScreen.kt         # Scaffold + ExoPlayerView + metadata + controls
        │   │   │       ├── PlayerViewModel.kt      # @HiltViewModel; loadStreams(); MergingMediaSource
        │   │   │       ├── PlayerUiState.kt        # sealed: Loading / Ready / Error
        │   │   │       └── components/
        │   │   │           ├── ExoPlayerView.kt    # AndroidView(StyledPlayerView); NO release in onDispose
        │   │   │           ├── PlayerControls.kt   # play/pause, seek bar, 10s skip, fullscreen
        │   │   │           └── RelatedVideosList.kt # (stub in v1; populated in v2)
        │   │   │
        │   │   └── service/
        │   │       └── PlaybackService.kt          # @AndroidEntryPoint; MediaSessionService; NO startForeground()
        │   │
        │   └── res/
        │       ├── drawable/
        │       │   ├── placeholder_thumbnail.xml   # solid Color(0xFF2A2A2A) shape — Coil placeholder
        │       │   └── error_thumbnail.xml         # broken image icon drawable — Coil error
        │       ├── mipmap-mdpi/ic_launcher.png
        │       ├── mipmap-hdpi/ic_launcher.png
        │       ├── mipmap-xhdpi/ic_launcher.png
        │       ├── mipmap-xxhdpi/ic_launcher.png
        │       ├── mipmap-xxxhdpi/ic_launcher.png
        │       ├── mipmap-anydpi-v26/
        │       │   └── ic_launcher.xml             # adaptive icon (foreground + background layers)
        │       ├── values/
        │       │   ├── strings.xml                 # app_name + all user-facing error/UI strings
        │       │   └── themes.xml                  # Theme.ExtraTube: parent="Theme.Material3.DayNight.NoActionBar"
        │       └── xml/
        │           ├── backup_rules.xml            # android:allowBackup="false" companion
        │           ├── data_extraction_rules.xml   # API 31+ backup exclusion rules
        │           └── network_security_config.xml # HTTPS-only; no cleartext HTTP
        │
        ├── test/
        │   └── java/com/extratube/
        │       ├── domain/usecase/
        │       │   └── SearchVideosUseCaseTest.kt  # JUnit + MockK; fake SearchRepository
        │       ├── data/repository/
        │       │   └── SearchRepositoryTest.kt     # JUnit + MockK; fake NewPipeService
        │       └── presentation/search/
        │           └── SearchViewModelTest.kt      # Turbine + MockK + StandardTestDispatcher
        │
        └── androidTest/
            └── java/com/extratube/
                └── SearchScreenTest.kt             # createComposeRule(); fake ViewModel
```

---

## 3. AndroidManifest.xml — Complete Specification

### `<manifest>` package declaration
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
```

### Permissions (all required — with reasons)

```xml
<!-- NewPipeExtractor makes all HTTP calls via OkHttp on IO dispatcher -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- Required API 28+ for any app starting a foreground service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<!-- Required API 34+: without this, starting a service with foregroundServiceType="mediaPlayback"
     throws IllegalArgumentException at runtime -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- Required API 33+: must also request at runtime via ActivityCompat.requestPermissions()
     before starting PlaybackService; without user grant, the notification is silently
     suppressed and the system may kill the foreground service as an invisible ANR -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### `<application>` attributes

```xml
<application
    android:name=".ExtraTubeApplication"
    android:allowBackup="false"
    android:hardwareAccelerated="true"
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:label="@string/app_name"
    android:theme="@style/Theme.ExtraTube"
    android:networkSecurityConfig="@xml/network_security_config"
    android:supportsRtl="true">
```

**Attribute rationale:**

| Attribute | Why |
|---|---|
| `android:name=".ExtraTubeApplication"` | Hilt requires `@HiltAndroidApp` on a custom `Application` subclass |
| `android:allowBackup="false"` | Privacy: prevents Android Backup from storing stale extraction state or session data |
| `android:hardwareAccelerated="true"` | ExoPlayer's `SurfaceView`/`TextureView` requires hardware acceleration; software fallback produces unplayable frame rates |
| `android:theme="@style/Theme.ExtraTube"` | Must extend `NoActionBar` variant; Compose renders its own top-level surface and a system `ActionBar` causes double-rendering artifacts |
| `android:networkSecurityConfig` | Enforces HTTPS-only policy; YouTube and all CDNs use HTTPS |

### `<activity>` — MainActivity

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden"
    android:windowSoftInputMode="adjustResize">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

| Attribute | Why |
|---|---|
| `android:exported="true"` | Android 12+ enforces explicit `exported` on any activity with `MAIN`/`LAUNCHER` filter; omission is a build error |
| `android:configChanges="orientation\|screenSize\|..."` | Prevents Activity recreation on rotation/keyboard events; Compose handles layout via `LocalConfiguration.current`; without this, rotation destroys ExoPlayer and resets navigation back stack |
| `android:windowSoftInputMode="adjustResize"` | Window resizes when soft keyboard appears, keeping search bar visible; `adjustPan` shifts the whole window which looks broken in Compose full-screen layouts |

### `<service>` — PlaybackService

```xml
<service
    android:name=".service.PlaybackService"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService" />
    </intent-filter>
</service>
```

| Attribute | Why |
|---|---|
| `android:exported="true"` | Media3 `MediaSessionService` must be exported so system UI components (lock screen controls, notification shade, Android Auto) can bind to it |
| `android:foregroundServiceType="mediaPlayback"` | Android 10+ requires this for the notification to survive screen-off during audio playback; API 34+ also requires the matching `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission |
| `MediaSessionService` intent-filter | System uses this to discover the service for media button routing (volume keys, Bluetooth) |

---

## 4. Gradle Configuration

### `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    // Security: prevents individual modules from declaring their own repositories
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                // IMPORTANT: scope JitPack to NewPipe only.
                // Without this, Gradle queries JitPack for EVERY unresolved dependency,
                // adding 2-5 minutes to cold builds.
                includeGroup("com.github.TeamNewPipe")
            }
        }
    }
}

rootProject.name = "ExtraTube"
include(":app")
```

### `gradle.properties`

```properties
# Gradle daemon JVM heap. Compose + Hilt kapt is memory-intensive.
# 4GB minimum; 6GB recommended for faster kapt rounds.
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8

android.useAndroidX=true

# Jetifier rewrites libraries to use AndroidX. DISABLED because:
# (1) Compose has zero Support Library dependencies
# (2) NewPipeExtractor and OkHttp are already AndroidX-compatible
# (3) Enabling it adds 10-15 seconds to every clean build for zero benefit
android.enableJetifier=false

kotlin.code.style=official

# R8 full mode: ~10-15% smaller APK via aggressive dead code elimination.
# REQUIRES comprehensive proguard-rules.pro (Jackson, Rhino, NewPipe, Hilt).
android.enableR8.fullMode=true

# Configuration cache: serializes task graph after first build, saving 2-5s on incremental builds.
org.gradle.configuration-cache=true

org.gradle.parallel=true
kotlin.incremental=true
kotlin.daemon.jvm.options=-Xmx2048m
```

### `gradle/libs.versions.toml` — Complete Version Catalog

```toml
[versions]
agp = "8.7.0"
kotlin = "2.1.0"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycleRuntimeKtx = "2.8.7"
navigationCompose = "2.8.5"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
media3 = "1.5.0"
newpipeExtractor = "v0.24.2"
okhttp = "4.12.0"
coil = "2.7.0"
coroutines = "1.9.0"
junit = "4.13.2"
junitExt = "1.2.1"
mockk = "1.13.12"
turbine = "1.1.0"

[libraries]
# Compose BOM — all compose-* libraries inherit version from this single entry
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

# Activity
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }

# Lifecycle
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# Hilt
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-android-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Media3 (ExoPlayer)
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
androidx-media3-exoplayer-dash = { group = "androidx.media3", name = "media3-exoplayer-dash", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }

# NewPipeExtractor — JitPack (not on Maven Central)
newpipe-extractor = { group = "com.github.TeamNewPipe", name = "NewPipeExtractor", version.ref = "newpipeExtractor" }

# OkHttp
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# Coil
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }

# Coroutines
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }

# Testing
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-kapt = { id = "org.jetbrains.kotlin.kapt", version.ref = "kotlin" }
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
# kotlin-parcelize is a COMPILER PLUGIN — declared here, not in dependencies{}
kotlin-parcelize = { id = "org.jetbrains.kotlin.plugin.parcelize", version.ref = "kotlin" }
```

### `app/build.gradle.kts` — Key Sections

**Plugins block:**
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)        // Hilt annotation processing
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.parcelize)   // compiler plugin — enables @Parcelize
}
```

**`android {}` block:**
```kotlin
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
            applicationIdSuffix = ".debug"   // installs alongside release on same device
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
        buildConfig = true    // enables BuildConfig.DEBUG for conditional logging
    }

    // With Kotlin 2.x, the Compose compiler is BUNDLED with the Kotlin compiler.
    // Do NOT set kotlinCompilerExtensionVersion here — it will cause a build error.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    packaging {
        resources {
            // NewPipeExtractor bundles Jackson; Rhino JS engine bundles its own
            // META-INF files. These duplicates cause APK packaging failures.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "mozilla/public-suffix-list.txt"
        }
    }
}

kapt {
    // Required for Hilt: allows kapt to resolve types it cannot see at
    // annotation processing time (Hilt-generated classes referencing app classes).
    // Without this, expect cryptic "cannot find symbol" kapt errors.
    correctErrorTypes = true
}
```

### `proguard-rules.pro` — Complete Rules

```proguard
# =======================================================
# NewPipeExtractor
# =======================================================
# NewPipeExtractor uses reflection to discover and instantiate extractor classes.
# R8 full mode strips all unused classes — these rules prevent that.
-keep class org.schabi.newpipe.extractor.** { *; }
-keepnames class org.schabi.newpipe.extractor.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.services.** { *; }

# =======================================================
# Jackson (NewPipeExtractor JSON parsing)
# =======================================================
# Jackson uses reflection to read/write fields and call getters/setters.
# R8 strips private fields; Jackson then throws UnrecognizedPropertyException.
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* *;
}
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonCreator class * { *; }
-keep class com.fasterxml.jackson.databind.** { *; }

# =======================================================
# Rhino JavaScript Engine
# =======================================================
# NewPipeExtractor uses Mozilla Rhino to execute the YouTube player's
# JavaScript signature decryption function at runtime.
# Without these rules, R8 strips Rhino and stream extraction silently fails.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**

# =======================================================
# OkHttp + Okio
# =======================================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# =======================================================
# Kotlin Coroutines
# =======================================================
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# =======================================================
# Hilt (safety net — Hilt's own consumer rules cover most cases)
# =======================================================
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# =======================================================
# AndroidX Media3 / ExoPlayer
# =======================================================
# PlaybackService must not be renamed (referenced by name in Manifest)
-keep class androidx.media3.** { *; }
# Legacy ExoPlayer package still referenced in some Media3 1.x internals
-keep class com.google.android.exoplayer2.** { *; }

# =======================================================
# General Android / Kotlin Rules
# =======================================================
# Preserve stack traces in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata (required for reflection on data classes)
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable (used by @Parcelize)
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
```

---

## 5. Data Flow Diagrams

### Search Flow

```
User types in SearchBar
  │
  ▼
SearchViewModel.onQueryChange(query: String)
  │  sets _query.value = query
  │
  ▼
_query: MutableStateFlow<String>
  │  .debounce(300L)           ← waits 300ms of inactivity
  │  .distinctUntilChanged()   ← skips duplicate queries
  │  .filter { it.length >= 2 } ← minimum query length
  │  .flatMapLatest { ... }    ← cancels in-flight request on new query
  │
  ▼
SearchViewModel.performSearch(query)
  │  emits SearchUiState.Loading
  │
  ▼
SearchVideosUseCase.execute(query)
  │  validates non-blank
  │
  ▼
SearchRepositoryImpl.search(query)
  │
  ▼
NewPipeService.search(query)           [withContext(Dispatchers.IO)]
  │  YouTube.getSearchExtractor(query)
  │  extractor.fetchPage()
  │
  ▼
OkHttpDownloader.execute(request)      [synchronous OkHttp call]
  │  HTTP GET to YouTube search endpoint
  │
  ▼
HTML response parsed by NewPipeExtractor
  │  returns InfoItemsPage<StreamInfoItem>
  │
  ▼
VideoMapper.toSearchResult(StreamInfoItem)
  │  extracts: videoId from URL, title, thumbnailUrl, channelName
  │  formats: viewCount (Long → "1.2M"), duration (seconds → "MM:SS")
  │
  ▼
Resource.Success(List<SearchResult>) + Page? (nextPage token)
  │
  ▼
_uiState.value = SearchUiState.Success(results, canLoadMore)
  │
  ▼
SearchScreen recomposition (collectAsStateWithLifecycle)
  │  shimmer items replaced by SearchResultItem cards
  ▼
LazyColumn rendered
```

### Playback Flow

```
User taps SearchResultItem(videoId: String)
  │
  ▼
NavController.navigate("player/$videoId")
  │
  ▼
PlayerScreen launched
  │  PlayerViewModel.loadStreams(videoId) called in LaunchedEffect
  │
  ▼
_uiState.value = PlayerUiState.Loading
  │  shimmer overlay shown on black background
  │
  ▼
GetVideoStreamsUseCase.execute(videoId)        [viewModelScope + IO dispatcher]
  │
  ▼
StreamRepositoryImpl.getStreams(videoId)
  │
  ▼
NewPipeService.extractStreams(videoId)         [withContext(Dispatchers.IO)]
  │  url = "https://www.youtube.com/watch?v=$videoId"
  │  YouTube.getStreamExtractor(url)
  │  extractor.fetchPage()
  │
  ▼
OkHttpDownloader.execute(request)              [synchronous OkHttp call]
  │
  ▼
Stream list parsed:
  │  videoOnlyStreams: filter for MPEG_4 or WEBM, sort by height DESC, pick ≤1080p
  │  audioStreams: filter for OPUS or AAC DASH, sort by averageBitrate DESC, pick first
  │
  ▼
StreamInfo(videoUrl, audioUrl, qualityLabel, title, channelName, thumbnailUrl)
  │
  ▼
Resource.Success(streamInfo) returned
  │
  ▼ [switch to Main dispatcher]
PlayerManager.configureAndPlay(streamInfo)
  │  val dataSourceFactory = DefaultHttpDataSource.Factory()
  │  val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
  │      .createMediaSource(MediaItem.fromUri(streamInfo.videoUrl))
  │  val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
  │      .createMediaSource(MediaItem.fromUri(streamInfo.audioUrl))
  │  exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
  │  exoPlayer.prepare()
  │  exoPlayer.playWhenReady = true
  │
  ▼
_uiState.value = PlayerUiState.Ready(streamInfo)
  │
  ▼
ExoPlayerView AndroidView renders StyledPlayerView
  │  video frames begin rendering via hardware decoder
  ▼
Video plays with merged DASH audio+video
```

### Background Service Flow

```
ExoPlayer playing in PlayerScreen
  │
  ▼
PlaybackService (MediaSessionService) — already running as foreground service
  │  bound to PlayerManager.exoPlayer (same singleton instance)
  │
  ▼
MediaSession — connected to exoPlayer
  │  Media3 DefaultMediaNotificationProvider creates/updates notification
  │
  ▼
System Notification:
  ┌─────────────────────────────────────────┐
  │ [thumbnail] ExtraTube                   │
  │             Video Title                 │
  │             Channel Name                │
  │  [◀◀] [⏸] [▶▶]              [✕]       │
  └─────────────────────────────────────────┘
  │
  ├── User presses Home → app backgrounded
  │     PlaybackService keeps running (foreground service cannot be killed)
  │     Audio continues via ExoPlayer
  │
  ├── User locks screen
  │     Lock screen shows media controls (system-provided)
  │     Same MediaSession commands
  │
  ├── User plugs in Bluetooth headphones
  │     System routes audio to Bluetooth output (audio focus handled automatically)
  │
  └── User presses notification Pause
        MediaSession.onPause() → exoPlayer.pause()
        Notification updates to show Play button
```

---

## 6. Key Classes and Responsibilities

| Class | Package | Layer | Responsibility |
|---|---|---|---|
| `Resource<T>` | `domain.model` | Domain | Sealed: `Loading` / `Success(data)` / `Error(message, cause?)`. Universal async state wrapper. |
| `SearchResult` | `domain.model` | Domain | `videoId`, `title`, `thumbnailUrl`, `channelName`, `viewCount: Long`, `duration: String` |
| `StreamInfo` | `domain.model` | Domain | `videoUrl`, `audioUrl`, `qualityLabel`, `title`, `channelName`, `thumbnailUrl`. Named `StreamInfo` (not `VideoStream`) to avoid clash with `org.schabi.newpipe.extractor.stream.VideoStream` |
| `SearchRepository` | `domain.repository` | Domain | Interface: `suspend fun search(query): Pair<Resource<List<SearchResult>>, Page?>` + `getNextPage` |
| `StreamRepository` | `domain.repository` | Domain | Interface: `suspend fun getStreams(videoId: String): Resource<StreamInfo>` |
| `SearchVideosUseCase` | `domain.usecase` | Domain | Validates query length; delegates to `SearchRepository`; maps exceptions to `Resource.Error` |
| `GetVideoStreamsUseCase` | `domain.usecase` | Domain | Delegates to `StreamRepository`; handles exception → `Resource.Error` mapping |
| `OkHttpDownloader` | `data.remote` | Data | Implements NewPipe's `Downloader` abstract class synchronously via `OkHttpClient.execute()` |
| `NewPipeService` | `data.remote` | Data | All `YoutubeSearchExtractor` and `YoutubeStreamExtractor` calls, wrapped in `withContext(IO)` |
| `SearchRepositoryImpl` | `data.repository` | Data | Implements `SearchRepository`; delegates to `NewPipeService`; stores extractor instance for pagination |
| `StreamRepositoryImpl` | `data.repository` | Data | Implements `StreamRepository`; delegates to `NewPipeService` |
| `VideoMapper` | `data.mapper` | Data | Maps `StreamInfoItem` → `SearchResult`: formats view count, converts seconds to MM:SS |
| `SearchViewModel` | `presentation.search` | Presentation | `StateFlow<SearchUiState>`; debounce+`flatMapLatest` pipeline; accumulated result list for pagination |
| `PlayerViewModel` | `presentation.player` | Presentation | `StateFlow<PlayerUiState>`; calls `GetVideoStreamsUseCase`; switches to Main before ExoPlayer calls |
| `PlayerManager` | `di` (Hilt `@Singleton`) | Service | Holds the single `ExoPlayer` instance shared between `PlayerViewModel` and `PlaybackService` |
| `ExoPlayerView` | `presentation.player.components` | Presentation | `AndroidView` wrapping `StyledPlayerView`; `useController = false`; `DisposableEffect` does NOT release |
| `PlaybackService` | `service` | Service | `@AndroidEntryPoint MediaSessionService`; creates `MediaSession`; does NOT call `startForeground()` |

---

## 7. Concurrency Model

```
Thread/Dispatcher          Used For
─────────────────────────────────────────────────────────────────────
Main (UI thread)           Compose recomposition, StateFlow.emit(),
                           ALL ExoPlayer API calls (ExoPlayer is
                           NOT thread-safe; must be called on Main)

Dispatchers.IO             All NewPipeService methods:
                           - YoutubeSearchExtractor.fetchPage()
                           - YoutubeStreamExtractor.fetchPage()
                           - OkHttpDownloader.execute() (synchronous)
                           Each extraction call creates a NEW extractor
                           instance (NewPipeExtractor is NOT thread-safe
                           across concurrent uses of the same instance)

viewModelScope             All ViewModel coroutine launching; tied to
                           ViewModel lifecycle; auto-cancelled on clear

lifecycleScope             PlaybackService coroutines

PROHIBITED: GlobalScope    Never used anywhere in the codebase
```

**Flow operators used:**

| Operator | Location | Purpose |
|---|---|---|
| `debounce(300L)` | `SearchViewModel.init` | Prevents firing a search on every keystroke |
| `distinctUntilChanged()` | `SearchViewModel.init` | Skips duplicate queries (e.g., user deletes and re-types same letter) |
| `filter { it.length >= 2 }` | `SearchViewModel.init` | Minimum query length |
| `flatMapLatest { }` | `SearchViewModel.init` | **Cancels in-flight search** when user types a new query; prevents result ordering issues |
| `catch { }` | Repository/UseCase flows | Maps all exceptions to `Resource.Error`; prevents uncaught exception crashing the Flow |

**Thread switching pattern in PlayerViewModel:**
```kotlin
viewModelScope.launch {
    _uiState.value = PlayerUiState.Loading
    val result = withContext(Dispatchers.IO) {
        getVideoStreamsUseCase.execute(videoId)   // runs on IO
    }
    // Back on Main after withContext returns:
    when (result) {
        is Resource.Success -> {
            playerManager.configureAndPlay(result.data)  // ExoPlayer on Main ✓
            _uiState.value = PlayerUiState.Ready(result.data)
        }
        is Resource.Error -> _uiState.value = PlayerUiState.Error(result.message)
        is Resource.Loading -> Unit
    }
}
```

---

## 8. State Management

### `SearchUiState`

```kotlin
sealed class SearchUiState {
    // Initial state: empty screen, search prompt visible
    object Idle : SearchUiState()

    // Shimmer items shown; triggered when query changes
    object Loading : SearchUiState()

    // Result list shown; canLoadMore = false when no nextPage token
    data class Success(
        val results: List<SearchResult>,
        val canLoadMore: Boolean
    ) : SearchUiState()

    // ErrorScreen shown with Retry button
    data class Error(val message: String) : SearchUiState()
}
```

**Pagination note:** The accumulated results list is stored in `SearchViewModel` (not in `SearchUiState.Success`) so that appending next-page results does not require copying the entire state. The `YoutubeSearchExtractor` instance must be stored in `SearchRepositoryImpl` between `search()` and `getNextPage()` calls — `getPage(nextPage)` requires the same extractor instance.

### `PlayerUiState`

```kotlin
sealed class PlayerUiState {
    // Shimmer overlay on black while extracting streams
    object Loading : PlayerUiState()

    // ExoPlayer configured and playing
    data class Ready(val stream: StreamInfo) : PlayerUiState()

    // ErrorScreen with Retry
    data class Error(val message: String) : PlayerUiState()
}
```

---

## 9. Android-Specific Compose Patterns

### Edge-to-Edge Display

```kotlin
// In MainActivity.onCreate():
WindowCompat.setDecorFitsSystemWindows(window, false)
enableEdgeToEdge()   // AndroidX Activity 1.8+

// In Scaffold content:
Modifier.systemBarsPadding()      // accounts for status bar + navigation bar
Modifier.navigationBarsPadding()  // gesture nav bar safe zone
```

### Orientation Lock in PlayerScreen

```kotlin
@Composable
fun PlayerScreen(videoId: String, onBack: () -> Unit) {
    val activity = LocalContext.current as Activity

    // Lock landscape when entering player; restore on back navigation
    DisposableEffect(Unit) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    // ...
}
```

### Back Handler for Fullscreen Exit

```kotlin
var isFullscreen by remember { mutableStateOf(false) }

BackHandler(enabled = isFullscreen) {
    isFullscreen = false
    // restore system UI bars via WindowInsetsController
}
```

### Lifecycle-Aware State Collection

```kotlin
// ALWAYS use collectAsStateWithLifecycle (from lifecycle-runtime-compose)
// NEVER use collectAsState() — it continues collecting when app is backgrounded,
// wasting CPU/battery and causing unnecessary recomposition when screen is off.
val uiState by viewModel.uiState.collectAsStateWithLifecycle()
```

### ExoPlayer isPlaying → Compose State

```kotlin
// In PlayerManager:
private val _isPlaying = MutableStateFlow(false)
val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

init {
    exoPlayer.addListener(object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }
        override fun onPlayerError(error: PlaybackException) {
            // emit error to PlayerViewModel via shared StateFlow
        }
    })
}
```

### Coil AsyncImage with Shimmer Placeholder

```kotlin
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(searchResult.thumbnailUrl)
        .crossfade(true)
        .build(),
    placeholder = painterResource(R.drawable.placeholder_thumbnail),
    error = painterResource(R.drawable.error_thumbnail),
    contentDescription = searchResult.title,
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .width(120.dp)
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(4.dp))
)
```

### ExoPlayerView — Critical DisposableEffect Rule

```kotlin
@Composable
fun ExoPlayerView(exoPlayer: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            StyledPlayerView(context).apply {
                player = exoPlayer
                useController = false          // custom Compose controls overlaid
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    )
    // ⚠️ INTENTIONALLY EMPTY onDispose.
    // DO NOT call exoPlayer.release() here.
    // Compose recomposition (e.g., on screen rotation) will call onDispose and
    // destroy ExoPlayer mid-playback. PlayerViewModel.onCleared() is the correct
    // and only release point.
    DisposableEffect(exoPlayer) {
        onDispose { /* intentionally empty */ }
    }
}
```

---

## 10. PlayerManager Singleton

`PlayerManager` is a Hilt `@Singleton` that holds the single `ExoPlayer` instance. This is the key architectural decision that makes both `PlayerViewModel` and `PlaybackService` work with the same player without passing it through Intents.

```kotlin
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Lazy initialization: ExoPlayer.Builder().build() must be called on the Main
    // thread. Hilt constructs @Singleton instances lazily, which happens on Main.
    val exoPlayer: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setHandleAudioBecomingNoisy(true)          // auto-pause on headphone unplug
            .setAudioAttributes(AudioAttributes.DEFAULT, true)  // audio focus management
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs  = */ 15_000,
                        /* maxBufferMs  = */ 50_000,
                        /* bufferForPlaybackMs = */ 2_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 5_000
                    )
                    .build()
            )
            .build()
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Called from PlayerViewModel on Main dispatcher (after withContext(IO) returns)
    fun configureAndPlay(streamInfo: StreamInfo) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()

        // Use ProgressiveMediaSource — NOT DashMediaSource.
        // NewPipeExtractor returns direct HTTPS stream file URLs (MP4/WEBM),
        // NOT DASH manifest (.mpd) URLs. DashMediaSource expects manifests.
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamInfo.videoUrl))
        val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamInfo.audioUrl))

        exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun release() {
        exoPlayer.release()
    }
}
```

---

## 11. Security Design

| Concern | Approach |
|---|---|
| API keys | None. Zero YouTube API keys in codebase. NewPipeExtractor is pure HTML scraping via OkHttp. |
| WebView | Not used. All extraction runs in NewPipeExtractor on Kotlin/JVM, not a browser. Zero WebView attack surface. |
| Permissions | Minimal: `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`. No `READ_EXTERNAL_STORAGE`, no camera, no location. |
| Network | `network_security_config.xml` enforces HTTPS-only. No `cleartextTrafficPermitted`. All YouTube domains use HTTPS. |
| Backup | `android:allowBackup="false"`. No user data backed up to Google servers. |
| Deep links | None in v1. Zero intent redirection attack surface. |
| Analytics / Crash | No analytics SDK. No crash reporting SDK. Zero telemetry. |
| Dependency pinning | NewPipeExtractor version pinned in `libs.versions.toml`. No floating `+` versions anywhere. |
| OkHttp | 30s connect/read/write timeouts. No response cache (stale DASH URLs are a security/correctness risk). |
| ProGuard | Release builds use R8 full mode with comprehensive rules. Stack traces preserved via `-keepattributes`. |

---

## 12. Error Handling Strategy

All repository and service methods return `Resource<T>` — they never throw. ViewModels never crash the app on extraction failure.

### Exception → User Message Mapping

| Exception Type | User-Facing Message | Notes |
|---|---|---|
| `ExtractionException` | "Could not load video. YouTube may have changed." | NewPipe parsing failure; common after YouTube updates |
| `IOException` | "Check your internet connection and try again." | Network unavailable; OkHttp timeout |
| `ContentNotAvailableException` | "This video is not available in your region." | Geo-blocked content |
| `AgeRestrictedException` | "This video requires age verification." | NewPipe cannot extract without sign-in |
| `VideoPlaylistException` | "Playlists are not supported yet." | Future feature; graceful message |
| Any other `Exception` | "Something went wrong. Please retry." | Catch-all; always shows Retry |

### StrictMode (Debug Only)

```kotlin
// In ExtraTubeApplication.onCreate():
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectNetwork()       // catches any IO on Main thread
            .detectDiskReads()
            .detectDiskWrites()
            .penaltyLog()
            .penaltyDeath()        // crash immediately in debug — do not hide violations
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedSqlLiteObjects()
            .detectLeakedClosableObjects()
            .penaltyLog()
            .build()
    )
}
```

---

## 13. Hilt Dependency Graph

```
SingletonComponent (app lifetime)
│
├── OkHttpClient  (@Singleton, AppModule)
│     └── 30s timeouts, logging interceptor (debug only), no cache
│
├── OkHttpDownloader  (@Singleton, AppModule)
│     └── depends on: OkHttpClient
│
├── NewPipe.init()  (@Singleton sentinel, AppModule)
│     └── depends on: OkHttpDownloader
│     └── side-effect: NewPipe.init(downloader) called once
│
├── PlayerManager  (@Singleton, AppModule)
│     └── depends on: @ApplicationContext
│     └── holds: ExoPlayer (lazy, Main thread)
│
├── SearchRepositoryImpl  (@Singleton, RepositoryModule @Binds)
│     └── bound to: SearchRepository interface
│     └── depends on: NewPipeService
│
└── StreamRepositoryImpl  (@Singleton, RepositoryModule @Binds)
      └── bound to: StreamRepository interface
      └── depends on: NewPipeService

ViewModelComponent (ViewModel lifetime)
│
├── SearchViewModel  (@HiltViewModel)
│     └── depends on: SearchVideosUseCase, GetNextPageUseCase
│
└── PlayerViewModel  (@HiltViewModel)
      └── depends on: GetVideoStreamsUseCase, PlayerManager

ServiceComponent (Service lifetime)
│
└── PlaybackService  (@AndroidEntryPoint)
      └── depends on: PlayerManager
```

**Dependency direction (no circular deps):**
```
Composables → ViewModels → UseCases → Repository interfaces ← RepositoryImpl → NewPipeService → OkHttp
                                                                                     ↑
                                                                               (also depends on)
                                                                              NewPipe.init singleton
```
