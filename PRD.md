# ExtraTube — Product Requirements Document

## 1. Executive Summary

ExtraTube is an open-source, privacy-first Android YouTube client that bypasses the official YouTube Data API entirely. It uses the **NewPipeExtractor** library to scrape and parse YouTube content, extracting direct DASH audio and video streams for custom playback via **AndroidX Media3 (ExoPlayer)**. The app provides YouTube search, video playback, and background audio — with no Google account required, no telemetry, no ads, and no API quotas.

Built on **Jetpack Compose + Material Design 3**, it also serves as a clean reference implementation of modern Android architecture (MVVM + Clean Architecture + Hilt + Coroutines) for the open-source community.

---

## 2. Problem Statement

| Problem | Impact |
|---|---|
| The official YouTube Android app collects telemetry, serves ads, and increasingly requires sign-in to access basic features | Privacy-conscious users have no viable official alternative |
| The YouTube Data API enforces strict quotas (10,000 units/day) and requires API keys that expose developer identity | Independent apps cannot build reliably on top of the official API |
| Existing open-source clients (NewPipe, LibreTube) are feature-complete but architecturally complex for new contributors | Developers learning modern Android have no clean, minimal reference implementation |
| Most Compose + Media3 tutorials use stub data or simple progressive streams, not real DASH audio/video merging | No practical reference exists for the technically complex DASH stream merging pattern |

---

## 3. Target Users

### Primary: Privacy-Conscious Android Users
- Want to watch YouTube content without Google tracking, ads, or account requirements
- Comfortable sideloading APKs (not Google Play users)
- Use devices running Android 8.0+

### Secondary: Android Developers
- Learning Jetpack Compose, MVVM, Clean Architecture, Hilt, and/or Media3
- Want a real-world, non-trivial reference project to study or extend
- May contribute features, bug fixes, or ports

### Tertiary: Open-Source Contributors
- Fork the project for their own YouTube-adjacent apps
- Submit PRs for new features (downloads, playlists, etc.) after v1

---

## 4. User Stories (MoSCoW)

### Must Have

| ID | User Story | Acceptance Criteria |
|---|---|---|
| US-01 | As a user, I want to search YouTube by keyword so I can find videos without a Google account | Search bar accepts input; results appear within 5s on Wi-Fi |
| US-02 | As a user, I want to see a list of search results with thumbnail, title, channel name, and view count | Each result card shows 16:9 thumbnail (Coil-loaded), 2-line title, channel name, formatted view count |
| US-03 | As a user, I want to tap a video result and have it play with merged DASH audio and video | Player screen opens; ExoPlayer plays merged MergingMediaSource within 3s of tap |
| US-04 | As a user, I want playback to continue in the background when I switch apps or lock the screen | MediaSessionService keeps audio playing; notification visible with controls |
| US-05 | As a user, I want a shimmer loading indicator while content loads, not a spinner | Shimmer animation shown in result list and player screen during loading states |
| US-06 | As a user, I want a clear error message and Retry button when something fails | Error screen shown with `Icons.Rounded.ErrorOutline`, user-readable message, and FilledTonalButton("Retry") |

### Should Have

| ID | User Story | Acceptance Criteria |
|---|---|---|
| US-07 | As a user, I want to scroll down to load more search results automatically (infinite scroll) | Next page loads when user scrolls to bottom; no "Load More" button needed |
| US-08 | As a user, I want the search bar to always be visible so I can search at any time | Search bar rendered in TopAppBar; never collapses on scroll |

### Could Have (Post-v1)

| ID | User Story |
|---|---|
| US-09 | As a user, I want to see video quality options so I can choose between bandwidth and quality |
| US-10 | As a user, I want to view related videos below the player |
| US-11 | As a user, I want to see subtitles/closed captions during playback |
| US-12 | As a user, I want Picture-in-Picture mode so I can watch while using other apps |

### Won't Have (v1)

- User accounts / Google sign-in
- Subscriptions or channel feeds
- Watch history (persistent)
- Download to local storage
- Comment viewing or posting
- Playlist support
- YouTube Shorts
- Live stream playback
- Trending / recommended homepage
- Localization (English only)
- Tablet / foldable adaptive layouts

---

## 5. Functional Requirements

### FR-1: Global Search

| Requirement | Detail |
|---|---|
| FR-1.1 Persistent search bar | Rendered in `TopAppBar`; visible on all screens; never collapses |
| FR-1.2 Debounced input | Search fires after 300ms of inactivity; uses `Flow.debounce(300L)` |
| FR-1.3 Minimum query length | Query must be ≥ 2 characters before firing |
| FR-1.4 Result list | `LazyColumn` of `SearchResultItem` cards: 16:9 `AsyncImage` thumbnail (Coil), title (max 2 lines), channel name, formatted view count (e.g. "1.2M views") |
| FR-1.5 Infinite scroll | When user reaches bottom of list, `GetNextPageUseCase` is called; results appended |
| FR-1.6 Empty state | Illustration + "No results found" text when search returns empty list |
| FR-1.7 Error state | `ErrorScreen` composable with message + Retry button |
| FR-1.8 Idle state | Default state before first search: prompt text "Search YouTube..." |
| FR-1.9 Cancel in-flight | Typing a new query cancels any in-flight extraction (`flatMapLatest`) |

### FR-2: Video Playback

| Requirement | Detail |
|---|---|
| FR-2.1 Navigation | Tapping a `SearchResultItem` navigates to `PlayerScreen` via Compose Navigation, passing `videoId` |
| FR-2.2 Stream extraction | `StreamExtractor` (NewPipeExtractor) fetches all available video-only and audio-only DASH streams |
| FR-2.3 Quality selection | Highest video-only stream ≤ 1080p selected; highest audio-only stream selected |
| FR-2.4 Stream merging | `MergingMediaSource(videoSource, audioSource)` used with `ProgressiveMediaSource.Factory` (NOT `DashMediaSource`) |
| FR-2.5 Player controls | Play/Pause toggle, seek bar with current/total time, 10-second forward/backward skip, fullscreen toggle |
| FR-2.6 Landscape lock | Player screen locks orientation to landscape; restores on back navigation |
| FR-2.7 Video metadata | Title, channel name, view count, publish date displayed below player |
| FR-2.8 Loading state | Shimmer overlay on black background while streams are being extracted |
| FR-2.9 Error state | `ErrorScreen` with "Could not load video" message + Retry |

### FR-3: Background Audio Service

| Requirement | Detail |
|---|---|
| FR-3.1 Service type | `MediaSessionService` (AndroidX Media3); keeps `ExoPlayer` playing when app is backgrounded |
| FR-3.2 Foreground service | Runs as foreground service; system cannot kill it during active playback |
| FR-3.3 Media notification | Shows: video thumbnail, title, channel name, Play/Pause action, Stop/Close action |
| FR-3.4 System integration | Responds to hardware volume keys, Bluetooth media buttons, lock screen controls, Android Auto |
| FR-3.5 Notification permission | Requests `POST_NOTIFICATIONS` at runtime on API 33+ before starting service |
| FR-3.6 Auto-notification | Media3 `DefaultMediaNotificationProvider` handles notification creation automatically; no `startForeground()` call in app code |

---

## 6. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Performance** | Cold start to interactive search bar: < 2 seconds on mid-range device (Snapdragon 665 class) |
| **Performance** | Search first result render: < 3 seconds on Wi-Fi (network + extractor latency is bottleneck) |
| **Performance** | Playback start (first frame): < 3 seconds after tapping a result |
| **Memory** | Peak memory during playback: < 150MB |
| **Thread safety** | Zero IO operations on Main thread; all `NewPipeExtractor` calls in `withContext(Dispatchers.IO)` |
| **Reliability** | No ANR (Application Not Responding) under any user interaction |
| **Reliability** | No crash on network unavailability; graceful error state shown |
| **Reliability** | No startup network calls; network only triggered by explicit user action |
| **Stability** | `StrictMode` passes with zero violations in debug builds |
| **Binary** | Single-module Android APK; no split APKs required |

---

## 7. Platform Requirements

| Requirement | Detail | Reason |
|---|---|---|
| `minSdk = 26` (Android 8.0 Oreo) | Hard minimum | `ForegroundServiceType.MEDIA_PLAYBACK`, modern audio focus APIs (`AudioFocusRequest`), and `NotificationChannel` (mandatory API 26+) all require this baseline |
| `targetSdk = 35` (Android 15) | Target | Required for new Google Play submissions in 2025; enables modern window insets, predictive back gesture |
| `POST_NOTIFICATIONS` runtime permission | Required API 33+ | Must be requested via `ActivityCompat.requestPermissions` before starting `PlaybackService`; without it, the notification is silently suppressed on API 33+ and the system may kill the foreground service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` manifest permission | Required API 34+ | Without this, starting a foreground service with `foregroundServiceType="mediaPlayback"` throws `IllegalArgumentException` at runtime on API 34+ |
| `android:hardwareAccelerated="true"` | Application-level attribute | Mandatory for ExoPlayer's `SurfaceView`/`TextureView` hardware video decoding pipeline; without it, video falls back to software rendering at unplayable frame rates on most devices |
| Device architecture | arm64-v8a, armeabi-v7a, x86_64 | Standard Android device coverage; no custom ABI splits in v1 |

---

## 8. Design Constraints

| Constraint | Specification |
|---|---|
| **Theme** | Forced dark theme via `darkColorScheme()` in `ExtraTubeTheme`; no light theme; no system theme toggle in v1 |
| **Design system** | Material Design 3 components throughout; no custom widget library |
| **Loading UI** | Shimmer placeholder animation for all loading states (result list, player); no `CircularProgressIndicator` |
| **Async state** | `sealed class Resource<out T>` used universally: `Loading`, `Success(data: T)`, `Error(message, cause?)` |
| **API keys** | Zero hardcoded API keys in codebase; NewPipeExtractor uses HTML scraping only |
| **Compose version** | Jetpack Compose (latest stable BOM); no XML layouts |
| **State collection** | `collectAsStateWithLifecycle()` used everywhere (not `collectAsState()`) to prevent background collection |

---

## 9. Success Metrics

| Metric | Target | Measurement Method |
|---|---|---|
| Search latency (P95) | < 5 seconds on Wi-Fi | Manual testing on reference device (Pixel 6) |
| Playback start latency (P95) | < 3 seconds | Stopwatch from tap to first video frame |
| ANR rate | 0 reports in first 30 days | User-reported GitHub issues |
| Background audio | Works on 5 OEM skins | Manual test: Samsung OneUI, Pixel, Xiaomi MIUI, OnePlus OxygenOS, Oppo ColorOS |
| StrictMode | Zero violations in debug build | `StrictMode.setThreadPolicy` + `setVmPolicy` enabled in `Application.onCreate()` |
| IO-on-main | Zero violations | StrictMode `ThreadPolicy.Builder().detectNetwork()` |
| GitHub traction | 50+ stars within 60 days of open-source release | GitHub Insights |
| Build reproducibility | `./gradlew assembleRelease` succeeds on clean checkout with JDK 17 | CI pipeline |

---

## 10. Out of Scope (v1)

The following are explicitly excluded from v1 and documented for future roadmap planning:

- **Video downloads** — requires `WorkManager` + storage permissions + download notification
- **User authentication** — no Google sign-in, no account management
- **Comments** — requires additional NewPipeExtractor call; separate domain model
- **Trending / Recommendations** — requires `KioskInfo` from NewPipeExtractor; no homepage in v1
- **YouTube Shorts** — different aspect ratio + different stream format; out of scope
- **Live stream playback** — requires HLS manifest handling (`HlsMediaSource`); different architecture
- **Playlists** — requires `PlaylistInfo` extraction; separate UI flow
- **Localization** — English only in v1; `strings.xml` used (enables future localization)
- **Tablet / Foldable layouts** — single-pane layout only; no `WindowSizeClass` adaptive logic
- **Offline caching** — no `Room` database for content caching in v1
- **Accessibility** — basic `contentDescription` on images only; full a11y audit is post-v1
- **Google Play distribution** — APK sideload / F-Droid only; no Play Store submission in v1

---

## 11. Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| NewPipeExtractor breaks when YouTube changes page structure | High (historical: several times/year) | High | Pin extractor version in `libs.versions.toml`; monitor [TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) for releases; document upgrade path |
| DASH stream URLs expire (~6 hours after extraction) | Medium | Medium | Document as known limitation in v1; add URL refresh logic in v2 |
| YouTube rate-limits / IP-blocks repeated scraping | Medium | Medium | No mitigation in v1; document as known risk; no retry storms (single request per search) |
| ProGuard/R8 strips reflection-dependent NewPipeExtractor classes in release build | High (without rules) | High | Comprehensive `proguard-rules.pro` with keep rules for NewPipeExtractor, Jackson, and Rhino JS engine |
| `POST_NOTIFICATIONS` denial causes invisible foreground service | Medium (users may deny) | Medium | Graceful degradation: service still runs; show in-app banner explaining why notification is missing |
| ExoPlayer `MergingMediaSource` incompatibility with certain stream formats | Low | High | Fallback: if video-only stream URL fails, attempt progressive (muxed) stream if available |
| No Google Play distribution limits initial reach | Certain | Low | Distribute via GitHub Releases + F-Droid; document sideload instructions prominently in README |
