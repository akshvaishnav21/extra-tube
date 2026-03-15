# ExtraTube — AI Implementation Prompts

Each prompt below is **self-contained**. Copy-paste a single prompt to implement one file or feature. Each prompt states exactly what already exists, what to create, and the precise requirements — no cross-referencing needed.

---

## Prompt 1 — Resource Sealed Class

**File to create:** `app/src/main/java/com/extratube/domain/model/Resource.kt`

```
Create the file: app/src/main/java/com/extratube/domain/model/Resource.kt

Package: com.extratube.domain.model

Requirements:
- Pure Kotlin file — ZERO Android framework imports (no Context, no Log, nothing from android.*)
- sealed class Resource<out T> with three subclasses:
    object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
- Add two inline extension functions on Resource<T>:
    inline fun <T> Resource<T>.onSuccess(block: (T) -> Unit): Resource<T>
    inline fun <T> Resource<T>.onError(block: (String, Throwable?) -> Unit): Resource<T>
  Each function executes the block if the sealed type matches, then returns `this` for chaining.
- No other classes or files needed.
```

---

## Prompt 2 — OkHttpDownloader

**File to create:** `app/src/main/java/com/extratube/data/remote/OkHttpDownloader.kt`

```
Create the file: app/src/main/java/com/extratube/data/remote/OkHttpDownloader.kt

Package: com.extratube.data.remote

Context:
- NewPipeExtractor requires a custom HTTP client implementation that extends its abstract Downloader class.
- The Downloader class is: org.schabi.newpipe.extractor.downloader.Downloader
- NewPipe's request/response types:
    org.schabi.newpipe.extractor.downloader.Request  (has .url(), .headers(), .httpMethod(), .dataToSend())
    org.schabi.newpipe.extractor.downloader.Response (constructor: code, message, headers, body, latestUrl)
- NewPipe's exception: org.schabi.newpipe.extractor.exceptions.ReCaptchaException
- OkHttp 4.x is available (com.squareup.okhttp3:okhttp:4.12.0)
- This class is a Hilt @Singleton injected via AppModule

Requirements:
- class OkHttpDownloader @Inject constructor(private val okHttpClient: OkHttpClient) : Downloader()
- Override: execute(request: Request): Response  — SYNCHRONOUS (NewPipeExtractor is not coroutine-aware)
- Map NewPipe Request.headers() (Map<String, List<String>>) to OkHttp request headers
- Handle HTTP methods: GET (requestBuilder.get()), POST (requestBuilder.post(body)), HEAD (requestBuilder.head()),
  other methods via requestBuilder.method(method, body)
- body from request.dataToSend() may be null — use ByteArray(0).toRequestBody(null) as fallback for POST
- Execute synchronously: okHttpClient.newCall(okHttpRequest).execute()
- If response code == 429: throw ReCaptchaException("reCaptcha challenge", request.url())
- Map OkHttp response back to NewPipe Response:
    code = okHttpResponse.code
    message = okHttpResponse.message
    headers = okHttpResponse.headers.toMultimap()
    body = okHttpResponse.body?.string() ?: ""
    latestUrl = request.url()
- On IOException: let it propagate (NewPipeExtractor catches IOException)
- Add @Throws(IOException::class, ReCaptchaException::class) annotation to execute()
```

---

## Prompt 3 — NewPipeService (Search)

**File to create:** `app/src/main/java/com/extratube/data/remote/NewPipeService.kt`

```
Create the file: app/src/main/java/com/extratube/data/remote/NewPipeService.kt

Package: com.extratube.data.remote

Context — files that already exist:
- OkHttpDownloader: com.extratube.data.remote.OkHttpDownloader (implements NewPipe Downloader)
- Resource<T>: com.extratube.domain.model.Resource (sealed: Loading/Success/Error)
- SearchResult: com.extratube.domain.model.SearchResult
    data class SearchResult(videoId, title, thumbnailUrl, channelName, viewCount: Long, duration: String)
- VideoMapper: com.extratube.data.mapper.VideoMapper (object with toSearchResult(StreamInfoItem))
- NewPipe.init() has already been called — do not call it here.
- Hilt @Singleton scope

NewPipeExtractor classes to use:
- org.schabi.newpipe.extractor.NewPipe (already initialized)
- org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeSearchExtractor
- org.schabi.newpipe.extractor.services.ServiceList (ServiceList.YouTube)
- org.schabi.newpipe.extractor.stream.StreamInfoItem
- org.schabi.newpipe.extractor.Page

Requirements — implement these two suspend functions:

1. suspend fun search(query: String): Pair<Resource<List<SearchResult>>, Any?>
   - Get extractor: ServiceList.YouTube.getSearchExtractor(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), null)
   - Call extractor.fetchPage() — this is BLOCKING; wrap entire function body in withContext(Dispatchers.IO)
   - Get page: extractor.initialPage
   - Filter page.items for StreamInfoItem instances only
   - Map each via VideoMapper.toSearchResult(item)
   - On success: return Pair(Resource.Success(resultList), Pair(extractor, page.nextPage))
     The opaque Any? nextPage token is Pair<YoutubeSearchExtractor, Page?> — callers cast it back
   - On any Exception: return Pair(Resource.Error(mapException(e), e), null)

2. suspend fun getNextPage(token: Any): Pair<Resource<List<SearchResult>>, Any?>
   - Cast token: val (extractor, page) = token as Pair<*, *>
   - Cast each: extractor as SearchExtractor, page as Page
   - Call extractor.getPage(page) in withContext(Dispatchers.IO)
   - Same mapping and return pattern as search()

3. private fun mapException(e: Exception): String
   - ExtractionException → "Could not load results. YouTube may have changed."
   - IOException → "Check your internet connection and try again."
   - ContentNotAvailableException → "This content is not available in your region."
   - else → "Something went wrong. Please retry."

Class constructor:
   @Singleton
   class NewPipeService @Inject constructor(
       private val downloader: OkHttpDownloader,
       @Suppress("UNUSED_PARAMETER") newPipeInit: Boolean   // dependency ensures NewPipe.init() ran first
   )
```

---

## Prompt 4 — SearchViewModel

**File to create:** `app/src/main/java/com/extratube/presentation/search/SearchViewModel.kt`

```
Create the file: app/src/main/java/com/extratube/presentation/search/SearchViewModel.kt

Package: com.extratube.presentation.search

Context — files that already exist:
- SearchUiState (same package): sealed class with Idle/Loading/Success(results, canLoadMore)/Error(message)
- SearchResult: com.extratube.domain.model.SearchResult
- Resource<T>: com.extratube.domain.model.Resource
- SearchVideosUseCase: com.extratube.domain.usecase.SearchVideosUseCase
    fun execute(query: String): Pair<Resource<List<SearchResult>>, Any?>
- GetNextPageUseCase: com.extratube.domain.usecase.GetNextPageUseCase
    fun execute(page: Any): Pair<Resource<List<SearchResult>>, Any?>

Requirements:
- @HiltViewModel class SearchViewModel @Inject constructor(
      private val searchUseCase: SearchVideosUseCase,
      private val nextPageUseCase: GetNextPageUseCase
  ) : ViewModel()

- Internal state:
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    private val _query = MutableStateFlow("")
    private val accumulatedResults = mutableListOf<SearchResult>()
    private var nextPageToken: Any? = null

- init block: set up Flow pipeline:
    _query
        .debounce(300L)
        .distinctUntilChanged()
        .filter { it.length >= 2 }
        .flatMapLatest { query -> performSearch(query) }
        .launchIn(viewModelScope)

- fun onQueryChange(query: String):
    _query.value = query
    if (query.isBlank()) { accumulatedResults.clear(); _uiState.value = SearchUiState.Idle }

- private fun performSearch(query: String): Flow<Unit> = flow { ... }:
    Emit SearchUiState.Loading
    Clear accumulatedResults
    Call searchUseCase.execute(query) (it is a suspend fun — call inside coroutine context)
    Store nextPageToken
    On Success: addAll to accumulatedResults, emit Success(accumulatedResults.toList(), canLoadMore = nextPage != null)
    On Error: emit SearchUiState.Error(message)
    emit(Unit) at end of flow

- fun loadNextPage():
    val page = nextPageToken ?: return
    if current state is not Success, return
    viewModelScope.launch:
        Call nextPageUseCase.execute(page)
        Store new nextPageToken
        On Success: addAll, emit updated Success state
        On Error: keep existing results (do not replace with error state on pagination failure)

- fun retry():
    Store current _query.value
    Set _query.value = ""
    Set _query.value = stored value
    (This forces distinctUntilChanged to re-emit the same query)

IMPORTANT: flowOf/flatMapLatest require kotlinx.coroutines.flow imports.
All StateFlow emissions happen on Main (viewModelScope is Main-confined).
The use case calls are suspend funs; wrap in withContext(Dispatchers.IO) inside the flow builder
since performSearch runs in a flow collected on viewModelScope (Main).
```

---

## Prompt 5 — ShimmerResultItem

**File to create:** `app/src/main/java/com/extratube/presentation/search/components/ShimmerResultItem.kt`

```
Create the file: app/src/main/java/com/extratube/presentation/search/components/ShimmerResultItem.kt

Package: com.extratube.presentation.search.components

Context:
- Jetpack Compose (latest BOM 2024.12.01), Material 3, forced dark theme
- NO external shimmer library — implement with pure Compose animation APIs only
- This composable shows a skeleton placeholder that looks like a SearchResultItem layout

Requirements:
- @Composable fun ShimmerResultItem()

Animation:
- val transition = rememberInfiniteTransition(label = "shimmer")
- val translateAnim by transition.animateFloat(
      initialValue = 0f,
      targetValue = 1000f,
      animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart
      ),
      label = "shimmer_translate"
  )
- val brush = Brush.linearGradient(
      colors = listOf(Color(0xFF2A2A2A), Color(0xFF4A4A4A), Color(0xFF2A2A2A)),
      start = Offset(translateAnim - 300f, 0f),
      end = Offset(translateAnim, 0f)
  )

Layout (matches SearchResultItem proportions):
- Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
  - Left side: Box, width=120.dp, aspectRatio=16f/9f, clip(RoundedCornerShape(4.dp)), background(brush)
  - Spacer(width=12.dp)
  - Right side: Column(modifier = Modifier.weight(1f))
      - Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))  // title line 1
      - Spacer(height=6.dp)
      - Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))  // title line 2
      - Spacer(height=8.dp)
      - Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))  // channel line

- Add @Preview(showBackground=true, backgroundColor=0xFF0F0F0F) annotation
```

---

## Prompt 6 — ExoPlayerView (AndroidView Wrapper)

**File to create:** `app/src/main/java/com/extratube/presentation/player/components/ExoPlayerView.kt`

```
Create the file: app/src/main/java/com/extratube/presentation/player/components/ExoPlayerView.kt

Package: com.extratube.presentation.player.components

Context:
- AndroidX Media3 1.5.0 (media3-exoplayer, media3-ui)
- ExoPlayer instance is passed as a parameter — it is NOT created inside this composable
- The ExoPlayer lifecycle is managed by PlayerManager (@Singleton) and released in PlayerViewModel.onCleared()
- DO NOT create a new ExoPlayer inside this composable
- DO NOT release the ExoPlayer in DisposableEffect.onDispose()

Requirements:
- @Composable fun ExoPlayerView(exoPlayer: ExoPlayer, modifier: Modifier = Modifier)

Implementation:
- AndroidView(
      factory = { context ->
          StyledPlayerView(context).apply {
              player = exoPlayer
              useController = false          // overlay custom Compose controls instead
              resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
              setShowBuffering(StyledPlayerView.SHOW_BUFFERING_WHEN_PLAYING)
          }
      },
      update = { view -> view.player = exoPlayer },   // reconnect if exoPlayer reference changes
      modifier = modifier
          .fillMaxWidth()
          .aspectRatio(16f / 9f)
          .background(Color.Black)
  )

- DisposableEffect(exoPlayer) {
      onDispose {
          // INTENTIONALLY EMPTY.
          // Reason: Compose calls onDispose on every recomposition that removes this composable
          // (e.g., screen rotation with configChanges handled, or navigation). Releasing ExoPlayer
          // here would destroy active playback. PlayerViewModel.onCleared() is the correct
          // and only ExoPlayer release point.
      }
  }

- Add a comment block explaining the empty onDispose rule for future maintainers.
- AspectRatioFrameLayout import: androidx.media3.ui.AspectRatioFrameLayout
- StyledPlayerView import: androidx.media3.ui.StyledPlayerView
```

---

## Prompt 7 — MergingMediaSource in PlayerViewModel

**File to create:** `app/src/main/java/com/extratube/presentation/player/PlayerViewModel.kt`

```
Create the file: app/src/main/java/com/extratube/presentation/player/PlayerViewModel.kt

Package: com.extratube.presentation.player

Context — files that already exist:
- PlayerUiState (same package): sealed class with Loading/Ready(stream)/Error(message)
- StreamInfo: com.extratube.domain.model.StreamInfo
    data class StreamInfo(videoId, videoUrl, audioUrl, qualityLabel, title, channelName, thumbnailUrl, viewCount, uploadDate)
- Resource<T>: com.extratube.domain.model.Resource
- GetVideoStreamsUseCase: com.extratube.domain.usecase.GetVideoStreamsUseCase
    suspend fun execute(videoId: String): Resource<StreamInfo>
- PlayerManager: com.extratube.PlayerManager (or com.extratube.di.PlayerManager)
    val exoPlayer: ExoPlayer (lazy @Singleton)
    val isPlaying: StateFlow<Boolean>
    fun configureAndPlay(streamInfo: StreamInfo)

Requirements:
- @HiltViewModel class PlayerViewModel @Inject constructor(
      private val getStreamsUseCase: GetVideoStreamsUseCase,
      private val playerManager: PlayerManager
  ) : ViewModel()

- private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
  val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
  val isPlaying: StateFlow<Boolean> = playerManager.isPlaying

- fun loadStreams(videoId: String):
    viewModelScope.launch {
        _uiState.value = PlayerUiState.Loading
        val result = withContext(Dispatchers.IO) {
            getStreamsUseCase.execute(videoId)
        }
        // After withContext returns, execution is back on Main dispatcher
        when (result) {
            is Resource.Success -> {
                playerManager.configureAndPlay(result.data)  // ExoPlayer on Main ✓
                _uiState.value = PlayerUiState.Ready(result.data)
            }
            is Resource.Error -> _uiState.value = PlayerUiState.Error(result.message)
            is Resource.Loading -> Unit
        }
    }

- fun togglePlayPause():
    with(playerManager.exoPlayer) { if (isPlaying) pause() else play() }

- fun seekTo(positionMs: Long): playerManager.exoPlayer.seekTo(positionMs)
- fun skipForward(): playerManager.exoPlayer.seekForward()
- fun skipBackward(): playerManager.exoPlayer.seekBack()

- override fun onCleared():
    super.onCleared()
    // Do NOT release playerManager.exoPlayer here.
    // PlayerManager is a @Singleton — releasing here would break PlaybackService
    // which holds the same ExoPlayer instance for background audio.

PlayerManager.configureAndPlay() implementation (document in comments — already implemented):
    val dataSourceFactory = DefaultHttpDataSource.Factory()
    // ⚠️ IMPORTANT: Use ProgressiveMediaSource, NOT DashMediaSource.
    // NewPipeExtractor returns DIRECT stream file URLs (MP4/WEBM from googlevideo.com),
    // NOT DASH manifest (.mpd) URLs. DashMediaSource expects manifests and will fail.
    val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(streamInfo.videoUrl))
    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
        .createMediaSource(MediaItem.fromUri(streamInfo.audioUrl))
    exoPlayer.setMediaSource(MergingMediaSource(videoSource, audioSource))
    exoPlayer.prepare()
    exoPlayer.playWhenReady = true
```

---

## Prompt 8 — PlaybackService (MediaSessionService)

**File to create:** `app/src/main/java/com/extratube/service/PlaybackService.kt`

```
Create the file: app/src/main/java/com/extratube/service/PlaybackService.kt

Package: com.extratube.service

Context:
- AndroidX Media3 1.5.0 (media3-session)
- PlayerManager is a Hilt @Singleton holding the ExoPlayer instance
- @AndroidEntryPoint is required for Hilt field injection in a Service

Requirements:
- @AndroidEntryPoint
  class PlaybackService : MediaSessionService()

- @Inject lateinit var playerManager: PlayerManager
- private var mediaSession: MediaSession? = null

- override fun onCreate():
    super.onCreate()
    mediaSession = MediaSession.Builder(this, playerManager.exoPlayer).build()

- override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    mediaSession

- override fun onDestroy():
    mediaSession?.release()   // release MediaSession — NOT exoPlayer (owned by PlayerManager @Singleton)
    mediaSession = null
    super.onDestroy()

CRITICAL RULES:
1. Do NOT call startForeground() anywhere in this class.
   Media3 MediaSessionService manages foreground service lifecycle automatically via
   DefaultMediaNotificationProvider. Manual startForeground() calls conflict with
   Media3's internal state machine and cause RemoteServiceException on some OEM devices.

2. Do NOT call exoPlayer.release() in onDestroy().
   PlayerManager is a @Singleton — the ExoPlayer instance is shared with PlayerViewModel.
   Releasing it here would break playback for any active PlayerScreen.
   ExoPlayer is released only when the application process exits.

AndroidManifest.xml entry required (do not generate XML, just document):
    <service
        android:name=".service.PlaybackService"
        android:exported="true"
        android:foregroundServiceType="mediaPlayback">
        <intent-filter>
            <action android:name="androidx.media3.session.MediaSessionService" />
        </intent-filter>
    </service>
```

---

## Prompt 9 — Hilt AppModule

**File to create:** `app/src/main/java/com/extratube/di/AppModule.kt`

```
Create the file: app/src/main/java/com/extratube/di/AppModule.kt

Package: com.extratube.di

Context — files that already exist:
- OkHttpDownloader: com.extratube.data.remote.OkHttpDownloader
- PlayerManager: com.extratube.PlayerManager (or wherever it lives)
- BuildConfig.DEBUG is available (buildConfig = true in build.gradle.kts)
- NewPipe import: org.schabi.newpipe.extractor.NewPipe

Requirements:
- @Module @InstallIn(SingletonComponent::class) object AppModule

1. @Provides @Singleton fun provideOkHttpClient(): OkHttpClient
   - connectTimeout(30, TimeUnit.SECONDS)
   - readTimeout(30, TimeUnit.SECONDS)
   - writeTimeout(30, TimeUnit.SECONDS)
   - NO response cache (stale DASH stream URLs cause playback failures)
   - In debug only: add HttpLoggingInterceptor with Level.HEADERS
   Return: OkHttpClient.Builder().apply { ... }.build()

2. @Provides @Singleton fun provideOkHttpDownloader(client: OkHttpClient): OkHttpDownloader
   = OkHttpDownloader(client)

3. @Provides @Singleton fun initNewPipe(downloader: OkHttpDownloader): Boolean
   - NewPipe.init(downloader)
   - Return true (sentinel value: returning Boolean forces Hilt to instantiate this provider
     eagerly when something depends on Boolean, ensuring NewPipe.init() runs before any extractor call)
   - Wrap in try/catch — if init fails, log in debug and return false (graceful degradation)

4. @Provides @Singleton fun providePlayerManager(@ApplicationContext context: Context): PlayerManager
   = PlayerManager(context)

Note on initNewPipe:
NewPipeService constructor takes Boolean as parameter (@Suppress("UNUSED_PARAMETER") newPipeInit: Boolean).
This creates a Hilt dependency: NewPipeService → Boolean → initNewPipe().
Hilt instantiates initNewPipe() before NewPipeService, guaranteeing NewPipe.init() runs first.
This is the standard "side-effect initialization" pattern for Hilt singletons.
```

---

## Prompt 10 — NavGraph + Full App Wiring

**Files to create:**
- `app/src/main/java/com/extratube/presentation/navigation/NavGraph.kt`
- Update `MainActivity.kt` (existing file)

```
Implement NavGraph and wire up MainActivity.

File 1: app/src/main/java/com/extratube/presentation/navigation/NavGraph.kt
Package: com.extratube.presentation.navigation

Context — files that already exist:
- Screen: com.extratube.presentation.navigation.Screen (sealed class with Search and Player routes)
- SearchScreen: @Composable fun SearchScreen(onVideoClick: (String) -> Unit)
- PlayerScreen: @Composable fun PlayerScreen(videoId: String, onBack: () -> Unit)
- ExtraTubeTheme: com.extratube.presentation.theme.ExtraTubeTheme

Requirements for NavGraph.kt:
- @Composable fun NavGraph(navController: NavHostController = rememberNavController())
- NavHost(navController = navController, startDestination = Screen.Search.route) {
      composable(Screen.Search.route) {
          SearchScreen(onVideoClick = { videoId ->
              navController.navigate(Screen.Player.createRoute(videoId))
          })
      }
      composable(
          route = Screen.Player.route,   // "player/{videoId}"
          arguments = listOf(navArgument("videoId") { type = NavType.StringType })
      ) { backStackEntry ->
          val videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable
          PlayerScreen(
              videoId = videoId,
              onBack = { navController.popBackStack() }
          )
      }
  }

File 2: Update app/src/main/java/com/extratube/MainActivity.kt
- @AndroidEntryPoint
  class MainActivity : ComponentActivity()
- onCreate():
    1. super.onCreate(savedInstanceState)
    2. Request POST_NOTIFICATIONS permission on API 33+:
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
         }
    3. WindowCompat.setDecorFitsSystemWindows(window, false)
    4. setContent { ExtraTubeTheme { NavGraph() } }

ExtraTubeTheme rule: ALWAYS uses darkColorScheme(). No isSystemInDarkTheme() check.
No light theme. This is a design constraint enforced here forever.
```

---

## Prompt 11 — ErrorScreen

**File to create:** `app/src/main/java/com/extratube/presentation/components/ErrorScreen.kt`

```
Create the file: app/src/main/java/com/extratube/presentation/components/ErrorScreen.kt

Package: com.extratube.presentation.components

Context:
- Jetpack Compose, Material 3, forced dark theme
- Reusable component — used in both SearchScreen and PlayerScreen
- No ViewModel dependency — pure stateless composable

Requirements:
- @Composable fun ErrorScreen(
      message: String,
      onRetry: () -> Unit,
      modifier: Modifier = Modifier
  )

Layout:
- Column(
      modifier = modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
  )
  - Icon(
        imageVector = Icons.Rounded.ErrorOutline,
        contentDescription = null,
        modifier = Modifier.size(64.dp),
        tint = MaterialTheme.colorScheme.error
    )
  - Spacer(Modifier.height(16.dp))
  - Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
  - Spacer(Modifier.height(24.dp))
  - FilledTonalButton(onClick = onRetry) { Text("Retry") }

Usage examples (document in KDoc):
  // In SearchScreen:
  SearchUiState.Error -> ErrorScreen(message = state.message, onRetry = { viewModel.retry() })

  // In PlayerScreen:
  PlayerUiState.Error -> ErrorScreen(message = state.message, onRetry = { viewModel.loadStreams(videoId) })

- Add @Preview annotation showing the error screen with message "Check your internet connection and try again."
```

---

## Appendix A — Key Decision Log

| Decision | What | Why |
|---|---|---|
| `StreamInfo` not `VideoStream` | Domain model renamed | `VideoStream` collides with `org.schabi.newpipe.extractor.stream.VideoStream` — same class name causes ambiguous import errors throughout the data layer |
| `PlayerManager` as Hilt `@Singleton` | Architecture | ExoPlayer must be the same instance in both `PlayerViewModel` and `PlaybackService`; no viable way to pass ExoPlayer via Intent; Singleton is the only correct approach |
| Extractor instance stored in Repository for pagination | Data layer | `YoutubeSearchExtractor.getPage(nextPage: Page)` requires calling the **same extractor instance** that fetched the first page; creating a new extractor for each page throws `IllegalStateException` |
| `ProgressiveMediaSource` NOT `DashMediaSource` | Playback | NewPipeExtractor returns **direct MP4/WEBM file URLs** from `googlevideo.com`, not DASH `.mpd` manifest URLs. `DashMediaSource` parses manifests; feeding it a direct file URL causes a parser crash |
| `flatMapLatest` for search query | ViewModel | Cancels the in-flight IO coroutine when the user types a new character; without this, rapidly typed queries produce results out of order (older queries resolving after newer ones) |
| `DisposableEffect` onDispose is empty in ExoPlayerView | Composable | Compose calls `onDispose` on every recomposition that removes the composable (rotation, navigation). Releasing ExoPlayer in `onDispose` destroys mid-playback. `PlayerViewModel.onCleared()` is the authoritative lifecycle owner |
| `collectAsStateWithLifecycle()` not `collectAsState()` | All Composables | `collectAsStateWithLifecycle()` stops Flow collection when the lifecycle drops to STOPPED (app backgrounded). `collectAsState()` continues collecting on background threads, wasting battery and causing invisible recomposition with screen off |
| No `startForeground()` in PlaybackService | Service | Media3 `MediaSessionService` internally calls `startForeground()` through `DefaultMediaNotificationProvider`. A manual call races with Media3's internal state, causing `RemoteServiceException` (app crash) on Samsung OneUI and MIUI |
| `android.enableJetifier=false` | Build config | Jetpack Compose has zero Support Library dependencies. Jetifier is a build-time rewriter; enabling it for a Compose project adds 10-15 seconds to every clean build for zero functional benefit |
| JitPack scoped with `content { includeGroup("com.github.TeamNewPipe") }` | Build config | Without scope restriction, Gradle queries JitPack for every single dependency it cannot resolve from Google/MavenCentral. This adds 2-5 minutes to cold Gradle sync on first project open |
| `kapt { correctErrorTypes = true }` | Build config | Required for Hilt to resolve types that reference Hilt-generated classes during annotation processing. Without this setting, `@HiltViewModel` and `@AndroidEntryPoint` classes produce cryptic "cannot find symbol" kapt errors |

---

## Appendix B — Known Limitations (v1)

| Limitation | Impact | Future Fix |
|---|---|---|
| NewPipeExtractor may break when YouTube changes its page structure | Search or playback stops working until extractor is updated | Monitor `TeamNewPipe/NewPipeExtractor` releases; update `newpipeExtractor` version in `libs.versions.toml` |
| DASH stream URLs are signed and expire (~6 hours after extraction) | Videos opened more than 6 hours after tapping will fail | v2: re-extract stream URLs when `ExoPlayer` reports `ERROR_CODE_IO_BAD_HTTP_STATUS` on an expired URL |
| Age-restricted videos cannot be extracted without a logged-in session | Attempting to load an age-restricted video returns `Resource.Error` | v2: investigate cookie-based extraction (no account required for some regions) |
| Live streams are not supported | Live videos show an error screen | v2: detect `StreamType.LIVE_STREAM` in `NewPipeService`; use `HlsMediaSource` with HLS manifest |
| No result caching | Every search triggers a full network + scraping round-trip | v2: Room database for caching recent searches with TTL |
| Search does not include channels or playlists | Only `StreamInfoItem` results shown | v2: add `ChannelInfoItem` and `PlaylistInfoItem` filter options |
| Video quality is not user-configurable | Always picks ≤1080p video-only + highest audio bitrate | v2: expose quality selector in player settings sheet |

---

## Appendix C — Future Work Roadmap

### Architecture Evolution
- Split into feature modules: `:feature:search`, `:feature:player`, `:core:network`, `:core:domain`, `:core:ui`
- Migrate from `kapt` to `ksp` (Kotlin Symbol Processing) — 2x faster annotation processing

### New Features
| Feature | Key Libraries | Architectural Change |
|---|---|---|
| Watch history | `androidx.room:room-runtime` | New `:core:database` module; `HistoryRepository` |
| Video downloads | `androidx.work:work-runtime-ktx` | `DownloadWorker`; storage permissions |
| Picture-in-Picture | `android.app.PictureInPictureParams` | `PlayerScreen` `onUserLeaveHint` hook |
| Subtitles / CC | NewPipe `SubtitlesStream` | Pass subtitle URL to `ExoPlayer.addMediaItem` with `MimeTypes.APPLICATION_TTML` |
| Trending / Home | NewPipe `KioskInfo` | New `HomeRepository`; `KioskScreen` composable |
| Channel page | NewPipe `ChannelInfo` | `ChannelScreen` + `ChannelViewModel` |
| Playlist support | NewPipe `PlaylistInfo` | `PlaylistScreen`; ExoPlayer playlist management |
| Search filters | NewPipe `SearchQueryHandler` filters | Additional filter chips in `SearchScreen` |
| User-configurable quality | `StreamInfo.qualityLabel` list | `QualityBottomSheet` Compose component |
| Chromecast | `media3-cast` | `CastPlayer` as alternate `PlayerManager` backend |

### Testing Expansion
- Screenshot testing with `Roborazzi` (Compose screenshot diffing)
- Integration tests using a fake `OkHttpDownloader` with pre-recorded responses
- `ui-automator` end-to-end test: search → tap → playback confirmed by `ExoPlayer.isPlaying`
