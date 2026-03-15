# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**ExtraTube** is a privacy-first, open-source Android YouTube client. It bypasses the official YouTube Data API entirely, using **NewPipeExtractor** to scrape and parse YouTube content, extracting direct DASH audio and video streams for playback via **AndroidX Media3 (ExoPlayer)**.

Key design documents:
- `PRD.md` — Product requirements, user stories, success metrics
- `Design.md` — Architecture, module structure, Gradle config, data flow diagrams, ProGuard rules
- `Implementation.md` — 12-phase step-by-step build plan with code patterns
- `prompt.md` — 11 self-contained AI prompts, one per major component

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run lint
./gradlew lint

# Clean
./gradlew clean
```

On Windows use `gradlew.bat` instead of `./gradlew`.

## Architecture

### Tech Stack
- **Language:** Kotlin 2.1
- **UI:** Jetpack Compose + Material Design 3 (forced dark theme always)
- **Architecture:** MVVM + Clean Architecture (Presentation → Domain → Data)
- **DI:** Dagger Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`)
- **Concurrency:** Kotlin Coroutines + `StateFlow`/`SharedFlow`
- **Media:** AndroidX Media3 1.5 (ExoPlayer + `MediaSessionService`)
- **Extraction:** NewPipeExtractor v0.24.2 (via JitPack)
- **Networking:** OkHttp 4.12 (via `OkHttpDownloader` implementing NewPipe's `Downloader`)
- **Images:** Coil 2.7 (`AsyncImage` with crossfade + placeholder)

### Package Structure

```
com.extratube/
├── ExtraTubeApplication.kt     @HiltAndroidApp; StrictMode (debug)
├── MainActivity.kt             @AndroidEntryPoint; single Activity
├── di/
│   ├── AppModule.kt            OkHttpClient, OkHttpDownloader, PlayerManager, NewPipe.init()
│   └── RepositoryModule.kt     @Binds interface → impl
├── data/
│   ├── remote/
│   │   ├── OkHttpDownloader.kt implements NewPipe Downloader (synchronous)
│   │   └── NewPipeService.kt   all extractor calls; withContext(Dispatchers.IO)
│   ├── repository/
│   │   ├── SearchRepositoryImpl.kt
│   │   └── StreamRepositoryImpl.kt
│   └── mapper/
│       └── VideoMapper.kt      StreamInfoItem → SearchResult
├── domain/
│   ├── model/
│   │   ├── Resource.kt         sealed: Loading / Success<T> / Error
│   │   ├── SearchResult.kt     videoId, title, thumbnailUrl, channelName, viewCount, duration
│   │   └── StreamInfo.kt       videoUrl, audioUrl, qualityLabel, title, channelName, thumbnailUrl
│   ├── repository/
│   │   ├── SearchRepository.kt (interface)
│   │   └── StreamRepository.kt (interface)
│   └── usecase/
│       ├── SearchVideosUseCase.kt
│       ├── GetNextPageUseCase.kt
│       └── GetVideoStreamsUseCase.kt
├── presentation/
│   ├── theme/                  Color, Theme (forced dark), Type, Shape
│   ├── navigation/             NavGraph, Screen
│   ├── components/             ErrorScreen (reusable)
│   ├── search/                 SearchScreen, SearchViewModel, SearchUiState, components/
│   └── player/                 PlayerScreen, PlayerViewModel, PlayerUiState, components/
└── service/
    └── PlaybackService.kt      @AndroidEntryPoint MediaSessionService
```

### Data Flow
```
User action
  → ViewModel (StateFlow<UiState>)
    → UseCase
      → Repository interface
        → RepositoryImpl → NewPipeService [withContext(IO)]
          → NewPipeExtractor → OkHttpDownloader → OkHttp (network)
```

## Critical Design Rules

1. **Thread safety — IO dispatcher**: All `NewPipeExtractor` calls wrapped in `withContext(Dispatchers.IO)`. NewPipeExtractor is synchronous and blocking. Zero network/IO on the Main thread. `StrictMode` enforces this in debug builds.

2. **Thread safety — ExoPlayer on Main**: All `ExoPlayer` API calls must happen on the **Main dispatcher**. After `withContext(IO)` returns, execution is back on Main — call ExoPlayer there.

3. **`ProgressiveMediaSource` not `DashMediaSource`**: NewPipeExtractor returns **direct stream file URLs** (`googlevideo.com` MP4/WEBM), NOT DASH manifest URLs. Using `DashMediaSource` crashes with a parser error.

4. **`StreamInfo` not `VideoStream`**: The domain model is named `StreamInfo` to avoid import conflict with `org.schabi.newpipe.extractor.stream.VideoStream`.

5. **`PlayerManager` singleton**: ExoPlayer lives in a Hilt `@Singleton` `PlayerManager`. Never create a second ExoPlayer instance. Both `PlayerViewModel` and `PlaybackService` inject the same `PlayerManager`.

6. **`DisposableEffect` must NOT release ExoPlayer**: `ExoPlayerView` composable has an intentionally empty `onDispose`. Compose recomposition (e.g., rotation) triggers `onDispose` — releasing ExoPlayer there destroys active playback. `PlayerViewModel.onCleared()` is the only correct release point.

7. **No `startForeground()` in `PlaybackService`**: Media3 `MediaSessionService` auto-manages foreground state. Manual `startForeground()` races with Media3 internals and causes `RemoteServiceException` on Samsung/Xiaomi.

8. **`collectAsStateWithLifecycle()` always**: Never use `collectAsState()` — it continues collecting when the app is backgrounded. Always use `collectAsStateWithLifecycle()` from `lifecycle-runtime-compose`.

9. **Forced dark theme**: `ExtraTubeTheme` always calls `darkColorScheme()`. No `isSystemInDarkTheme()` check. No light theme.

10. **`Resource<T>` universally**: All async results are wrapped in `Resource.Loading` / `Resource.Success` / `Resource.Error`. Repositories never throw — they catch all exceptions and return `Resource.Error`.

## Dependency Notes

- **NewPipeExtractor** is via JitPack: `com.github.TeamNewPipe.NewPipeExtractor:extractor:v0.24.2`. JitPack is scoped via `includeGroupByRegex("com\\.github\\.TeamNewPipe.*")` in `settings.gradle.kts`.
- **Hilt kapt**: `kapt { correctErrorTypes = true }` is required in `app/build.gradle.kts`. Without it, `@HiltViewModel` produces cryptic "cannot find symbol" errors.
- **Kotlin 2.x + Compose**: Do NOT set `kotlinCompilerExtensionVersion` in `composeOptions {}`. With Kotlin 2.x the Compose compiler is bundled — setting the version causes a build conflict.
- **Packaging excludes**: `app/build.gradle.kts` excludes Jackson/Rhino META-INF duplicates. Do not remove these — NewPipeExtractor bundles Jackson and Rhino which conflict at APK packaging.

## ProGuard / R8

`proguard-rules.pro` has rules for: NewPipeExtractor (reflection), Jackson (annotations), Rhino JS engine (YouTube signature decryption), OkHttp, Hilt, Media3/ExoPlayer. **Do not remove Rhino rules** — without them, stream URL extraction silently fails in release builds.

## Testing Approach

- Unit tests in `app/src/test/` use **MockK** + **Turbine** (for `StateFlow` testing) + `kotlinx-coroutines-test`
- Compose UI tests in `app/src/androidTest/` use `createComposeRule()`
- No mocking of `NewPipeExtractor` internals — use fake `SearchRepository`/`StreamRepository` implementations
- Do not write integration tests that call real YouTube endpoints in CI

## Known Limitations

- NewPipeExtractor may break when YouTube changes its page structure — update `newpipeExtractor` version in `libs.versions.toml`
- DASH stream URLs expire (~6 hours after extraction) — no refresh logic in v1
- Age-restricted videos fail extraction silently → `Resource.Error`
- Live streams not supported in v1
