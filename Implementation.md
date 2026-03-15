# ExtraTube — Implementation Guide

## Pre-flight Checklist

Before writing any code, verify your environment:

- [ ] **Android Studio** Hedgehog (2023.1.1) or newer installed
- [ ] **JDK 17** set as the project JDK in Android Studio → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK
- [ ] **Android SDK API 35** installed via SDK Manager
- [ ] **Kotlin 2.x** plugin installed (bundled with recent Android Studio)
- [ ] **Git** initialized: `git init`, `README.md` created, `.gitignore` added
- [ ] **GitHub repo** created; push initial empty commit
- [ ] `.gitignore` covers: `local.properties`, `*.jks`, `*.keystore`, `build/`, `.gradle/`, `.idea/`

---

## Build Phases Overview

| Phase | Name | Estimated Effort | Validation |
|---|---|---|---|
| 1 | Project Scaffold | 1h | `./gradlew assembleDebug` succeeds; app launches |
| 2 | Theme + Navigation Scaffold | 1h | Dark BG visible; 2-screen navigation works |
| 3 | Domain Layer | 2h | Unit tests pass with mock repositories |
| 4 | NewPipe Init + OkHttpDownloader | 2h | Debug log shows real search result |
| 5 | Data Layer: Search Repository | 3h | `SearchVideosUseCase` with real network returns results |
| 6 | Search UI | 4h | Shimmer → results; infinite scroll; error + Retry |
| 7 | Data Layer: Stream Repository | 3h | Use case returns valid DASH stream URLs |
| 8 | Player UI + ExoPlayer + MergingMediaSource | 4h | Tap result → DASH video plays with audio |
| 9 | Background Audio Service | 3h | Background audio persists; notification controls work |
| 10 | Error Handling + Edge Cases | 2h | Airplane mode → graceful error; StrictMode clean |
| 11 | Polish + Performance | 2h | No jank; profiler shows main thread free during search |
| 12 | Release Preparation | 1h | Signed release APK works end-to-end |

**Total: ~28 hours of focused implementation**

---

## Phase 1 — Project Scaffold

**Goal:** Compilable Android project with all dependencies wired.

### Steps

1. **Create new project** in Android Studio:
   - Template: Empty Activity
   - Language: Kotlin
   - Package name: `com.extratube`
   - Minimum SDK: API 26 (Android 8.0)
   - Build configuration language: Kotlin DSL

2. **Replace `gradle/libs.versions.toml`** with the complete version catalog from `Design.md §4`.

3. **Replace `settings.gradle.kts`** with the spec from `Design.md §4` — includes scoped JitPack for `com.github.TeamNewPipe` only.

4. **Replace `gradle.properties`** with the spec from `Design.md §4` — includes `enableJetifier=false`, `enableR8.fullMode=true`, `configuration-cache=true`.

5. **Update `app/build.gradle.kts`**:
   ```kotlin
   plugins {
       alias(libs.plugins.android.application)
       alias(libs.plugins.kotlin.android)
       alias(libs.plugins.kotlin.kapt)
       alias(libs.plugins.hilt.android)
       alias(libs.plugins.kotlin.parcelize)
   }
   ```
   Add the full `android {}` block (namespace, compileSdk 35, defaultConfig, buildTypes, buildFeatures, compileOptions, kotlinOptions, packaging excludes) from `Design.md §4`.

6. **Add all dependencies** from the version catalog to `app/build.gradle.kts`:
   ```kotlin
   dependencies {
       val composeBom = platform(libs.androidx.compose.bom)
       implementation(composeBom)
       // ... all compose, lifecycle, navigation, hilt, media3, newpipe, okhttp, coil, coroutines
       kapt(libs.hilt.android.compiler)
       // ... testing deps
   }
   kapt { correctErrorTypes = true }
   ```

7. **Create `ExtraTubeApplication.kt`**:
   ```kotlin
   @HiltAndroidApp
   class ExtraTubeApplication : Application()
   ```

8. **Update `AndroidManifest.xml`** with:
   - `android:name=".ExtraTubeApplication"` on `<application>`
   - `android:hardwareAccelerated="true"` on `<application>`
   - All 4 permissions (INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS)
   - Full `<activity>` spec for `MainActivity` (configChanges, windowSoftInputMode)
   - Full `<service>` spec for `PlaybackService` (exported, foregroundServiceType, intent-filter)

9. **Update `MainActivity.kt`**:
   ```kotlin
   @AndroidEntryPoint
   class MainActivity : ComponentActivity() {
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           setContent { Text("Hello ExtraTube") } // stub
       }
   }
   ```

10. **Run:** `./gradlew assembleDebug`

### Validation
- Build succeeds with zero errors and zero warnings (except expected Compose experimental opt-in)
- APK installs and launches on device/emulator showing "Hello ExtraTube" text on dark background (set `Theme.ExtraTube` as NoActionBar theme)

---

## Phase 2 — Theme + Navigation Scaffold

**Goal:** Forced dark theme established; two-screen navigation working.

### Steps

1. **Create `res/values/themes.xml`**:
   ```xml
   <style name="Theme.ExtraTube" parent="Theme.Material3.DayNight.NoActionBar" />
   ```
   This is a thin shell — actual theming is fully in Compose.

2. **Create `presentation/theme/Color.kt`** — dark palette only:
   ```kotlin
   val DarkBackground = Color(0xFF0F0F0F)
   val DarkSurface = Color(0xFF1C1C1C)
   val DarkPrimary = Color(0xFFFF0000)        // YouTube red accent
   val DarkOnPrimary = Color(0xFFFFFFFF)
   val DarkOnBackground = Color(0xFFE8E8E8)
   val DarkOnSurface = Color(0xFFCCCCCC)
   val DarkError = Color(0xFFCF6679)
   ```

3. **Create `presentation/theme/Type.kt`** — Material3 typography with system fonts.

4. **Create `presentation/theme/Shape.kt`** — `Shapes(small=4dp, medium=8dp, large=16dp)`.

5. **Create `presentation/theme/Theme.kt`**:
   ```kotlin
   @Composable
   fun ExtraTubeTheme(content: @Composable () -> Unit) {
       // FORCED dark — no isSystemInDarkTheme() check, no light theme
       val colorScheme = darkColorScheme(
           primary = DarkPrimary,
           background = DarkBackground,
           surface = DarkSurface,
           onBackground = DarkOnBackground,
           onSurface = DarkOnSurface,
           error = DarkError
       )
       MaterialTheme(
           colorScheme = colorScheme,
           typography = ExtraTubeTypography,
           shapes = ExtraTubeShapes,
           content = content
       )
   }
   ```

6. **Create `presentation/navigation/Screen.kt`**:
   ```kotlin
   sealed class Screen(val route: String) {
       object Search : Screen("search")
       object Player : Screen("player/{videoId}") {
           fun createRoute(videoId: String) = "player/$videoId"
       }
   }
   ```

7. **Create stub composables:**
   - `SearchScreen.kt`: `@Composable fun SearchScreen(onVideoClick: (String) -> Unit) { Box { Text("Search") } }`
   - `PlayerScreen.kt`: `@Composable fun PlayerScreen(videoId: String, onBack: () -> Unit) { Box { Text("Player: $videoId") } }`

8. **Create `presentation/navigation/NavGraph.kt`**:
   ```kotlin
   @Composable
   fun NavGraph(navController: NavHostController = rememberNavController()) {
       NavHost(navController = navController, startDestination = Screen.Search.route) {
           composable(Screen.Search.route) {
               SearchScreen(onVideoClick = { id ->
                   navController.navigate(Screen.Player.createRoute(id))
               })
           }
           composable(
               Screen.Player.route,
               arguments = listOf(navArgument("videoId") { type = NavType.StringType })
           ) { backStackEntry ->
               PlayerScreen(
                   videoId = backStackEntry.arguments?.getString("videoId") ?: return@composable,
                   onBack = { navController.popBackStack() }
               )
           }
       }
   }
   ```

9. **Update `MainActivity.kt`**:
   ```kotlin
   @AndroidEntryPoint
   class MainActivity : ComponentActivity() {
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           WindowCompat.setDecorFitsSystemWindows(window, false)
           setContent { ExtraTubeTheme { NavGraph() } }
       }
   }
   ```

### Validation
- App launches with black background (dark theme enforced)
- Temporary button on SearchScreen navigates to PlayerScreen with a test videoId
- Back navigation returns to SearchScreen

---

## Phase 3 — Domain Layer

**Goal:** All domain models, repository interfaces, and use cases created and unit-tested.

### Steps

1. **Create `domain/model/Resource.kt`**:
   ```kotlin
   sealed class Resource<out T> {
       object Loading : Resource<Nothing>()
       data class Success<T>(val data: T) : Resource<T>()
       data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
   }

   inline fun <T> Resource<T>.onSuccess(block: (T) -> Unit): Resource<T> {
       if (this is Resource.Success) block(data)
       return this
   }

   inline fun <T> Resource<T>.onError(block: (String, Throwable?) -> Unit): Resource<T> {
       if (this is Resource.Error) block(message, cause)
       return this
   }
   ```

2. **Create `domain/model/SearchResult.kt`**:
   ```kotlin
   data class SearchResult(
       val videoId: String,
       val title: String,
       val thumbnailUrl: String,
       val channelName: String,
       val viewCount: Long,
       val duration: String    // formatted as "MM:SS" or "HH:MM:SS"
   )
   ```

3. **Create `domain/model/StreamInfo.kt`**:
   > ⚠️ Named `StreamInfo`, NOT `VideoStream`. The name `VideoStream` collides with
   > `org.schabi.newpipe.extractor.stream.VideoStream` causing import ambiguity.
   ```kotlin
   data class StreamInfo(
       val videoId: String,
       val videoUrl: String,
       val audioUrl: String,
       val qualityLabel: String,   // e.g. "1080p"
       val title: String,
       val channelName: String,
       val thumbnailUrl: String,
       val viewCount: Long,
       val uploadDate: String
   )
   ```

4. **Create `domain/repository/SearchRepository.kt`**:
   ```kotlin
   interface SearchRepository {
       suspend fun search(query: String): Pair<Resource<List<SearchResult>>, Any?>
       suspend fun getNextPage(page: Any): Pair<Resource<List<SearchResult>>, Any?>
   }
   ```
   Note: `page` is typed as `Any?` in the interface to avoid importing NewPipe's `Page` class into the domain layer. The data layer casts appropriately.

5. **Create `domain/repository/StreamRepository.kt`**:
   ```kotlin
   interface StreamRepository {
       suspend fun getStreams(videoId: String): Resource<StreamInfo>
   }
   ```

6. **Create `domain/usecase/SearchVideosUseCase.kt`**:
   ```kotlin
   class SearchVideosUseCase @Inject constructor(
       private val repository: SearchRepository
   ) {
       suspend fun execute(query: String): Pair<Resource<List<SearchResult>>, Any?> {
           if (query.isBlank()) return Pair(Resource.Error("Query cannot be blank"), null)
           return repository.search(query)
       }
   }
   ```

7. **Create `domain/usecase/GetNextPageUseCase.kt`**:
   ```kotlin
   class GetNextPageUseCase @Inject constructor(
       private val repository: SearchRepository
   ) {
       suspend fun execute(page: Any): Pair<Resource<List<SearchResult>>, Any?> =
           repository.getNextPage(page)
   }
   ```

8. **Create `domain/usecase/GetVideoStreamsUseCase.kt`**:
   ```kotlin
   class GetVideoStreamsUseCase @Inject constructor(
       private val repository: StreamRepository
   ) {
       suspend fun execute(videoId: String): Resource<StreamInfo> =
           repository.getStreams(videoId)
   }
   ```

9. **Write unit tests** in `test/`:
   - `SearchVideosUseCaseTest`: mock `SearchRepository`; test blank query → Error, valid query → delegates
   - `SearchRepositoryTest`: mock `NewPipeService`; test success + exception paths

### Validation
- `./gradlew test` → all unit tests green

---

## Phase 4 — NewPipeExtractor Initialization + OkHttpDownloader

**Goal:** NewPipe initialized and able to make real network calls.

### Steps

1. **Create `data/remote/OkHttpDownloader.kt`**:

   NewPipe's `Downloader` abstract class has one abstract method:
   `execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response`

   The implementation must be **synchronous** (NewPipeExtractor is not coroutine-aware):

   ```kotlin
   class OkHttpDownloader @Inject constructor(
       private val okHttpClient: OkHttpClient
   ) : Downloader() {

       @Throws(IOException::class, ReCaptchaException::class)
       override fun execute(request: Request): Response {
           val requestBuilder = okhttp3.Request.Builder().url(request.url())

           // Copy NewPipe headers to OkHttp headers
           request.headers().forEach { (key, values) ->
               values.forEach { value -> requestBuilder.addHeader(key, value) }
           }

           val httpMethod = request.httpMethod()
           val body = request.dataToSend()

           when (httpMethod) {
               "GET" -> requestBuilder.get()
               "POST" -> requestBuilder.post(
                   (body ?: ByteArray(0)).toRequestBody(null)
               )
               "HEAD" -> requestBuilder.head()
               else -> requestBuilder.method(httpMethod, body?.toRequestBody(null))
           }

           val okHttpResponse = okHttpClient.newCall(requestBuilder.build()).execute()

           if (okHttpResponse.code == 429) throw ReCaptchaException("reCaptcha challenge", request.url())

           val responseBody = okHttpResponse.body?.string() ?: ""
           val responseHeaders = okHttpResponse.headers.toMultimap()

           return Response(okHttpResponse.code, okHttpResponse.message, responseHeaders, responseBody, request.url())
       }
   }
   ```

2. **Create `di/AppModule.kt`**:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   object AppModule {

       @Provides @Singleton
       fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
           .connectTimeout(30, TimeUnit.SECONDS)
           .readTimeout(30, TimeUnit.SECONDS)
           .writeTimeout(30, TimeUnit.SECONDS)
           // No cache: stale DASH stream URLs cause playback failures
           .apply {
               if (BuildConfig.DEBUG) {
                   addInterceptor(HttpLoggingInterceptor().apply {
                       level = HttpLoggingInterceptor.Level.HEADERS
                   })
               }
           }
           .build()

       @Provides @Singleton
       fun provideOkHttpDownloader(client: OkHttpClient): OkHttpDownloader =
           OkHttpDownloader(client)

       // Sentinel return value (Boolean) forces Hilt to instantiate this eagerly.
       // NewPipe.init() MUST complete before any extractor call.
       @Provides @Singleton
       fun initNewPipe(downloader: OkHttpDownloader): Boolean {
           NewPipe.init(downloader)
           return true
       }

       @Provides @Singleton
       fun providePlayerManager(@ApplicationContext context: Context): PlayerManager =
           PlayerManager(context)
   }
   ```

3. **Update `ExtraTubeApplication.kt`**:
   ```kotlin
   @HiltAndroidApp
   class ExtraTubeApplication : Application() {
       override fun onCreate() {
           super.onCreate()
           if (BuildConfig.DEBUG) {
               StrictMode.setThreadPolicy(
                   StrictMode.ThreadPolicy.Builder()
                       .detectNetwork().detectDiskReads().detectDiskWrites()
                       .penaltyLog().penaltyDeath().build()
               )
           }
       }
   }
   ```
   Note: `NewPipe.init()` is called by Hilt's `initNewPipe` provider, which is instantiated when any class that depends on the `Boolean` sentinel is first created. Alternatively, call `NewPipe.init(OkHttpDownloader(OkHttpClient()))` here directly before Hilt runs — whichever ensures it runs before any extractor call.

4. **Add `proguard-rules.pro`** from `Design.md §4` — particularly the NewPipe, Jackson, and Rhino rules.

5. **Manual integration test (debug only — NOT a CI test):**
   In a temporary `DebugActivity` or via breakpoint in Application:
   ```kotlin
   GlobalScope.launch(Dispatchers.IO) {
       val extractor = YouTube.getSearchExtractor("kotlin android")
       extractor.fetchPage()
       val item = extractor.initialPage.items.firstOrNull()
       Log.d("TEST", "First result: ${item?.name}")
   }
   ```
   Verify log output shows a real video title.

### Validation
- Debug log shows `First result: <some YouTube video title>` without crash
- `StrictMode` does not fire during this test

---

## Phase 5 — Data Layer: Search Repository

**Goal:** Full search pipeline from query to `List<SearchResult>` working.

### Steps

1. **Create `data/mapper/VideoMapper.kt`**:
   ```kotlin
   object VideoMapper {

       fun toSearchResult(item: StreamInfoItem): SearchResult {
           val videoId = extractVideoId(item.url)
           val thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: ""
           val durationStr = formatDuration(item.duration)
           return SearchResult(
               videoId = videoId,
               title = item.name,
               thumbnailUrl = thumbnailUrl,
               channelName = item.uploaderName ?: "Unknown",
               viewCount = item.viewCount,
               duration = durationStr
           )
       }

       private fun extractVideoId(url: String): String {
           // YouTube URLs: https://www.youtube.com/watch?v=XXXXXXXXXXX
           return Uri.parse(url).getQueryParameter("v") ?: url
       }

       private fun formatDuration(seconds: Long): String {
           if (seconds < 0) return "LIVE"
           val h = seconds / 3600
           val m = (seconds % 3600) / 60
           val s = seconds % 60
           return if (h > 0) "%d:%02d:%02d".format(h, m, s)
           else "%d:%02d".format(m, s)
       }

       fun Long.toCompactViewCount(): String = when {
           this >= 1_000_000_000 -> "%.1fB views".format(this / 1_000_000_000.0)
           this >= 1_000_000 -> "%.1fM views".format(this / 1_000_000.0)
           this >= 1_000 -> "%.1fK views".format(this / 1_000.0)
           this < 0 -> ""
           else -> "$this views"
       }
   }
   ```

2. **Create `data/remote/NewPipeService.kt`** (search portion):
   ```kotlin
   @Singleton
   class NewPipeService @Inject constructor(
       private val downloader: OkHttpDownloader,
       @Suppress("UNUSED_PARAMETER") newPipeInit: Boolean  // forces NewPipe.init() to run first
   ) {

       suspend fun search(query: String): Pair<Resource<List<SearchResult>>, Any?> =
           withContext(Dispatchers.IO) {
               try {
                   val extractor = YouTube.getSearchExtractor(
                       query,
                       listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                       null
                   )
                   extractor.fetchPage()
                   val page = extractor.initialPage
                   val results = page.items
                       .filterIsInstance<StreamInfoItem>()
                       .map { VideoMapper.toSearchResult(it) }
                   Pair(Resource.Success(results), Pair(extractor, page.nextPage))
               } catch (e: Exception) {
                   val msg = mapException(e)
                   Pair(Resource.Error(msg, e), null)
               }
           }

       suspend fun getNextPage(
           extractorAndPage: Pair<*, *>
       ): Pair<Resource<List<SearchResult>>, Any?> =
           withContext(Dispatchers.IO) {
               try {
                   @Suppress("UNCHECKED_CAST")
                   val extractor = extractorAndPage.first as SearchExtractor
                   val page = extractorAndPage.second as Page
                   val infoPage = extractor.getPage(page)
                   val results = infoPage.items
                       .filterIsInstance<StreamInfoItem>()
                       .map { VideoMapper.toSearchResult(it) }
                   Pair(Resource.Success(results), Pair(extractor, infoPage.nextPage))
               } catch (e: Exception) {
                   Pair(Resource.Error(mapException(e), e), null)
               }
           }

       private fun mapException(e: Exception): String = when (e) {
           is ExtractionException -> "Could not load results. YouTube may have changed."
           is IOException -> "Check your internet connection and try again."
           is ContentNotAvailableException -> "This content is not available in your region."
           else -> "Something went wrong. Please retry."
       }
   }
   ```

3. **Create `data/repository/SearchRepositoryImpl.kt`**:
   ```kotlin
   @Singleton
   class SearchRepositoryImpl @Inject constructor(
       private val service: NewPipeService
   ) : SearchRepository {

       override suspend fun search(query: String) = service.search(query)
       override suspend fun getNextPage(page: Any) =
           service.getNextPage(page as Pair<*, *>)
   }
   ```

4. **Create `di/RepositoryModule.kt`**:
   ```kotlin
   @Module
   @InstallIn(SingletonComponent::class)
   abstract class RepositoryModule {
       @Binds @Singleton
       abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

       @Binds @Singleton
       abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository
   }
   ```

### Validation
- Integration test: call `SearchVideosUseCase.execute("android compose")` → receives non-empty `List<SearchResult>` with real video titles and thumbnail URLs

---

## Phase 6 — Search UI

**Goal:** Complete search experience: shimmer → results → infinite scroll → error handling.

### Steps

1. **Create `presentation/search/SearchUiState.kt`**:
   ```kotlin
   sealed class SearchUiState {
       object Idle : SearchUiState()
       object Loading : SearchUiState()
       data class Success(
           val results: List<SearchResult>,
           val canLoadMore: Boolean
       ) : SearchUiState()
       data class Error(val message: String) : SearchUiState()
   }
   ```

2. **Create `presentation/search/SearchViewModel.kt`**:
   ```kotlin
   @HiltViewModel
   class SearchViewModel @Inject constructor(
       private val searchUseCase: SearchVideosUseCase,
       private val nextPageUseCase: GetNextPageUseCase
   ) : ViewModel() {

       private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
       val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

       private val _query = MutableStateFlow("")
       private var accumulatedResults = mutableListOf<SearchResult>()
       private var nextPageToken: Any? = null

       init {
           _query
               .debounce(300L)
               .distinctUntilChanged()
               .filter { it.length >= 2 }
               .flatMapLatest { query -> performSearch(query) }
               .launchIn(viewModelScope)
       }

       fun onQueryChange(query: String) {
           _query.value = query
           if (query.isBlank()) {
               accumulatedResults.clear()
               _uiState.value = SearchUiState.Idle
           }
       }

       private fun performSearch(query: String): Flow<Unit> = flow {
           _uiState.value = SearchUiState.Loading
           accumulatedResults.clear()
           val (resource, nextPage) = searchUseCase.execute(query)
           nextPageToken = nextPage
           when (resource) {
               is Resource.Success -> {
                   accumulatedResults.addAll(resource.data)
                   _uiState.value = SearchUiState.Success(
                       results = accumulatedResults.toList(),
                       canLoadMore = nextPage != null
                   )
               }
               is Resource.Error -> _uiState.value = SearchUiState.Error(resource.message)
               is Resource.Loading -> Unit
           }
           emit(Unit)
       }

       fun loadNextPage() {
           val page = nextPageToken ?: return
           if (_uiState.value !is SearchUiState.Success) return
           viewModelScope.launch {
               val (resource, nextPage) = nextPageUseCase.execute(page)
               nextPageToken = nextPage
               when (resource) {
                   is Resource.Success -> {
                       accumulatedResults.addAll(resource.data)
                       _uiState.value = SearchUiState.Success(
                           results = accumulatedResults.toList(),
                           canLoadMore = nextPage != null
                       )
                   }
                   is Resource.Error -> { /* show snackbar or keep existing results */ }
                   is Resource.Loading -> Unit
               }
           }
       }

       fun retry() {
           val currentQuery = _query.value
           if (currentQuery.isNotBlank()) {
               _query.value = "" // force re-emit
               _query.value = currentQuery
           }
       }
   }
   ```

3. **Create `presentation/search/components/ShimmerResultItem.kt`** (no external library):
   ```kotlin
   @Composable
   fun ShimmerResultItem() {
       val shimmerColors = listOf(
           Color(0xFF2A2A2A),
           Color(0xFF4A4A4A),
           Color(0xFF2A2A2A)
       )
       val transition = rememberInfiniteTransition(label = "shimmer")
       val translateAnim by transition.animateFloat(
           initialValue = 0f,
           targetValue = 1000f,
           animationSpec = infiniteRepeatable(
               animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
               repeatMode = RepeatMode.Restart
           ),
           label = "shimmer_translate"
       )
       val brush = Brush.linearGradient(
           colors = shimmerColors,
           start = Offset(translateAnim - 300f, 0f),
           end = Offset(translateAnim, 0f)
       )

       Row(
           modifier = Modifier
               .fillMaxWidth()
               .padding(horizontal = 16.dp, vertical = 8.dp)
       ) {
           // Thumbnail placeholder
           Box(
               modifier = Modifier
                   .width(120.dp)
                   .aspectRatio(16f / 9f)
                   .clip(RoundedCornerShape(4.dp))
                   .background(brush)
           )
           Spacer(Modifier.width(12.dp))
           Column(modifier = Modifier.weight(1f)) {
               // Title placeholder (2 lines)
               Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
               Spacer(Modifier.height(6.dp))
               Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
               Spacer(Modifier.height(8.dp))
               // Channel + view count placeholder
               Box(Modifier.fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
           }
       }
   }
   ```

4. **Create `presentation/search/components/SearchResultItem.kt`**:
   ```kotlin
   @Composable
   fun SearchResultItem(
       result: SearchResult,
       onClick: () -> Unit
   ) {
       Row(
           modifier = Modifier
               .fillMaxWidth()
               .clickable(onClick = onClick)
               .padding(horizontal = 16.dp, vertical = 8.dp)
       ) {
           AsyncImage(
               model = ImageRequest.Builder(LocalContext.current)
                   .data(result.thumbnailUrl)
                   .crossfade(true)
                   .build(),
               placeholder = painterResource(R.drawable.placeholder_thumbnail),
               error = painterResource(R.drawable.error_thumbnail),
               contentDescription = result.title,
               contentScale = ContentScale.Crop,
               modifier = Modifier
                   .width(120.dp)
                   .aspectRatio(16f / 9f)
                   .clip(RoundedCornerShape(4.dp))
           )
           Spacer(Modifier.width(12.dp))
           Column(modifier = Modifier.weight(1f)) {
               Text(
                   text = result.title,
                   style = MaterialTheme.typography.bodyMedium,
                   maxLines = 2,
                   overflow = TextOverflow.Ellipsis
               )
               Spacer(Modifier.height(4.dp))
               Text(
                   text = result.channelName,
                   style = MaterialTheme.typography.bodySmall,
                   color = MaterialTheme.colorScheme.onSurfaceVariant
               )
               Text(
                   text = "${result.viewCount.toCompactViewCount()} • ${result.duration}",
                   style = MaterialTheme.typography.bodySmall,
                   color = MaterialTheme.colorScheme.onSurfaceVariant
               )
           }
       }
   }
   ```

5. **Create `presentation/search/SearchScreen.kt`**:
   - `Scaffold` with `TopAppBar` containing `SearchBar`
   - Content: `LazyColumn` keyed on `result.videoId` (prevents recomposition on scroll)
   - When `Loading`: show 6× `ShimmerResultItem`
   - When `Success`: show items; add `LazyListState` scroll listener to call `viewModel.loadNextPage()` when last visible item is near bottom
   - When `Error`: show `ErrorScreen(message, onRetry = { viewModel.retry() })`
   - When `Idle`: show centered hint text

6. **Create `presentation/components/ErrorScreen.kt`**:
   ```kotlin
   @Composable
   fun ErrorScreen(
       message: String,
       onRetry: () -> Unit,
       modifier: Modifier = Modifier
   ) {
       Column(
           modifier = modifier.fillMaxSize().padding(32.dp),
           horizontalAlignment = Alignment.CenterHorizontally,
           verticalArrangement = Arrangement.Center
       ) {
           Icon(
               imageVector = Icons.Rounded.ErrorOutline,
               contentDescription = null,
               modifier = Modifier.size(64.dp),
               tint = MaterialTheme.colorScheme.error
           )
           Spacer(Modifier.height(16.dp))
           Text(
               text = message,
               style = MaterialTheme.typography.bodyLarge,
               textAlign = TextAlign.Center,
               color = MaterialTheme.colorScheme.onSurface
           )
           Spacer(Modifier.height(24.dp))
           FilledTonalButton(onClick = onRetry) { Text("Retry") }
       }
   }
   ```

### Validation
- Search query → shimmer → results list displayed with real thumbnails
- Scroll to bottom → next page loads and appends
- Airplane mode → error screen with Retry
- Tapping a result calls `onVideoClick(videoId)` and navigates to stub PlayerScreen

---

## Phase 7 — Data Layer: Stream Repository

**Goal:** Extract DASH stream URLs for a given video ID.

### Steps

1. **Add `extractStreams` to `data/remote/NewPipeService.kt`**:
   ```kotlin
   suspend fun extractStreams(videoId: String): Resource<StreamInfo> =
       withContext(Dispatchers.IO) {
           try {
               val url = "https://www.youtube.com/watch?v=$videoId"
               val extractor = YouTube.getStreamExtractor(url) as YoutubeStreamExtractor
               extractor.fetchPage()

               // Video-only DASH streams (no audio)
               val videoStream = extractor.videoOnlyStreams
                   .filter { it.format == MediaFormat.MPEG_4 || it.format == MediaFormat.WEBM }
                   .filter { it.height <= 1080 }
                   .maxByOrNull { it.height }
                   ?: return@withContext Resource.Error("No suitable video stream found")

               // Audio-only DASH streams
               val audioStream = extractor.audioStreams
                   .filter { it.format == MediaFormat.M4A || it.format == MediaFormat.WEBMA }
                   .maxByOrNull { it.averageBitrate }
                   ?: return@withContext Resource.Error("No suitable audio stream found")

               Resource.Success(
                   StreamInfo(
                       videoId = videoId,
                       videoUrl = videoStream.content,   // .content is the direct URL
                       audioUrl = audioStream.content,
                       qualityLabel = "${videoStream.height}p",
                       title = extractor.name,
                       channelName = extractor.uploaderName ?: "Unknown",
                       thumbnailUrl = extractor.thumbnails.firstOrNull()?.url ?: "",
                       viewCount = extractor.viewCount,
                       uploadDate = extractor.uploadDate?.date()?.toString() ?: ""
                   )
               )
           } catch (e: Exception) {
               Resource.Error(mapException(e), e)
           }
       }
   ```

2. **Create `data/repository/StreamRepositoryImpl.kt`**:
   ```kotlin
   @Singleton
   class StreamRepositoryImpl @Inject constructor(
       private val service: NewPipeService
   ) : StreamRepository {
       override suspend fun getStreams(videoId: String) = service.extractStreams(videoId)
   }
   ```

3. **`RepositoryModule.kt`** already has `@Binds` for `StreamRepository` (added in Phase 5).

### Critical Note: `ProgressiveMediaSource` vs `DashMediaSource`

`NewPipeExtractor` returns **direct stream file URLs** (e.g., `https://r1---sn-xxx.googlevideo.com/videoplayback?...`) — these are MP4 or WEBM files served directly, NOT DASH manifest (`.mpd`) URLs.

Use `ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory())` — **not** `DashMediaSource.Factory()`. `DashMediaSource` expects an `.mpd` manifest and will fail with a parsing error on direct file URLs.

### Validation
- `GetVideoStreamsUseCase.execute("dQw4w9WgXcQ")` returns `Resource.Success(StreamInfo)` with non-empty `videoUrl` and `audioUrl`
- URLs are valid HTTPS `googlevideo.com` addresses

---

## Phase 8 — Player UI + ExoPlayer + MergingMediaSource

**Goal:** Tapping a search result plays merged DASH video with audio.

### Steps

1. **Create `PlayerManager.kt`** — see `Design.md §10` for full implementation. Key points:
   - `@Singleton` class with `@ApplicationContext` constructor parameter
   - `exoPlayer` is a `by lazy` property — initialized on first access (on Main thread)
   - `setHandleAudioBecomingNoisy(true)` — auto-pauses on headphone unplug
   - `setAudioAttributes(AudioAttributes.DEFAULT, true)` — requests audio focus
   - `DefaultLoadControl.Builder()` with `minBufferMs=15_000, maxBufferMs=50_000`
   - `configureAndPlay(streamInfo: StreamInfo)` uses `ProgressiveMediaSource` + `MergingMediaSource`

2. **Create `presentation/player/PlayerUiState.kt`**:
   ```kotlin
   sealed class PlayerUiState {
       object Loading : PlayerUiState()
       data class Ready(val stream: StreamInfo) : PlayerUiState()
       data class Error(val message: String) : PlayerUiState()
   }
   ```

3. **Create `presentation/player/PlayerViewModel.kt`**:
   ```kotlin
   @HiltViewModel
   class PlayerViewModel @Inject constructor(
       private val getStreamsUseCase: GetVideoStreamsUseCase,
       private val playerManager: PlayerManager
   ) : ViewModel() {

       private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
       val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

       val isPlaying: StateFlow<Boolean> = playerManager.isPlaying

       fun loadStreams(videoId: String) {
           viewModelScope.launch {
               _uiState.value = PlayerUiState.Loading
               val result = withContext(Dispatchers.IO) {
                   getStreamsUseCase.execute(videoId)
               }
               // Back on Main dispatcher after withContext returns
               when (result) {
                   is Resource.Success -> {
                       playerManager.configureAndPlay(result.data)  // ExoPlayer on Main ✓
                       _uiState.value = PlayerUiState.Ready(result.data)
                   }
                   is Resource.Error ->
                       _uiState.value = PlayerUiState.Error(result.message)
                   is Resource.Loading -> Unit
               }
           }
       }

       fun togglePlayPause() {
           if (playerManager.exoPlayer.isPlaying) playerManager.exoPlayer.pause()
           else playerManager.exoPlayer.play()
       }

       fun seekTo(positionMs: Long) = playerManager.exoPlayer.seekTo(positionMs)

       fun skipForward() = playerManager.exoPlayer.seekForward()

       fun skipBackward() = playerManager.exoPlayer.seekBack()

       override fun onCleared() {
           super.onCleared()
           // PlayerManager singleton keeps ExoPlayer alive for PlaybackService.
           // Do NOT call playerManager.release() here in v1.
           // Release only when the app process exits.
       }
   }
   ```

4. **Create `presentation/player/components/ExoPlayerView.kt`** — see `Design.md §9` for exact implementation. Key rules:
   - `AndroidView` factory creates `StyledPlayerView` with `useController = false`
   - `RESIZE_MODE_FIT` set on the view
   - `DisposableEffect` `onDispose` is **intentionally empty** — ViewModel owns lifecycle

5. **Create `presentation/player/components/PlayerControls.kt`**:
   ```kotlin
   @Composable
   fun PlayerControls(
       isPlaying: Boolean,
       currentPosition: Long,
       duration: Long,
       onPlayPause: () -> Unit,
       onSeek: (Long) -> Unit,
       onSkipForward: () -> Unit,
       onSkipBackward: () -> Unit,
       modifier: Modifier = Modifier
   ) {
       Column(modifier = modifier.padding(16.dp)) {
           Slider(
               value = if (duration > 0) currentPosition / duration.toFloat() else 0f,
               onValueChange = { fraction -> onSeek((fraction * duration).toLong()) }
           )
           Row(
               modifier = Modifier.fillMaxWidth(),
               horizontalArrangement = Arrangement.SpaceEvenly,
               verticalAlignment = Alignment.CenterVertically
           ) {
               IconButton(onClick = onSkipBackward) {
                   Icon(Icons.Filled.Replay10, contentDescription = "Skip back 10s")
               }
               IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                   Icon(
                       imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                       contentDescription = if (isPlaying) "Pause" else "Play",
                       modifier = Modifier.size(40.dp)
                   )
               }
               IconButton(onClick = onSkipForward) {
                   Icon(Icons.Filled.Forward10, contentDescription = "Skip forward 10s")
               }
           }
       }
   }
   ```

6. **Create `presentation/player/PlayerScreen.kt`**:
   - `LaunchedEffect(videoId) { viewModel.loadStreams(videoId) }`
   - Orientation lock via `DisposableEffect` (see `Design.md §9`)
   - When `Loading`: black Box + centered `CircularProgressIndicator` (or shimmer overlay)
   - When `Ready`: `ExoPlayerView` + `PlayerControls` + metadata text
   - When `Error`: `ErrorScreen(message, onRetry = { viewModel.loadStreams(videoId) })`
   - Poll `exoPlayer.currentPosition` via `LaunchedEffect` with `while(true) { delay(500); update }` for seek bar

### Validation
- Tap search result → PlayerScreen opens → streams extracted → `MergingMediaSource` configured → ExoPlayer plays video with synchronized audio
- Seek bar updates in real time
- Play/Pause/Skip buttons work

---

## Phase 9 — Background Audio Service

**Goal:** Playback continues when app is backgrounded; media notification with controls.

### Steps

1. **Create `service/PlaybackService.kt`**:
   ```kotlin
   @AndroidEntryPoint
   class PlaybackService : MediaSessionService() {

       @Inject lateinit var playerManager: PlayerManager

       private var mediaSession: MediaSession? = null

       override fun onCreate() {
           super.onCreate()
           mediaSession = MediaSession.Builder(this, playerManager.exoPlayer)
               .build()
       }

       override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
           mediaSession

       override fun onDestroy() {
           mediaSession?.run {
               // Release MediaSession but NOT ExoPlayer (PlayerManager @Singleton owns it)
               release()
           }
           mediaSession = null
           super.onDestroy()
       }
   }
   ```

   > ⚠️ Do NOT call `startForeground()` manually.
   > Media3 `MediaSessionService` handles foreground service management automatically through
   > `DefaultMediaNotificationProvider`. Manual `startForeground()` calls conflict with
   > Media3's internal lifecycle and cause `RemoteServiceException` on certain OEM devices.

2. **Verify `AndroidManifest.xml`** has the `<service>` declaration exactly as specified in `Design.md §3`.

3. **Request `POST_NOTIFICATIONS` in `MainActivity.kt`**:
   ```kotlin
   override fun onCreate(savedInstanceState: Bundle?) {
       super.onCreate(savedInstanceState)
       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
           ActivityCompat.requestPermissions(
               this,
               arrayOf(Manifest.permission.POST_NOTIFICATIONS),
               /* requestCode = */ 1001
           )
       }
       // ... setContent { ... }
   }
   ```

4. **Start the service from `PlayerViewModel`** when playback begins:
   ```kotlin
   // After playerManager.configureAndPlay(streamInfo):
   val serviceIntent = Intent(application, PlaybackService::class.java)
   application.startForegroundService(serviceIntent)
   ```
   Or use `MediaController` for a fully bound approach (recommended in v2).

### Validation
- Start playback → press Home → audio continues in background
- Notification appears with title, channel, Play/Pause, Stop
- Pressing Pause in notification pauses ExoPlayer
- Bluetooth media buttons pause/resume playback
- Lock screen shows media controls

---

## Phase 10 — Error Handling + Edge Cases

**Goal:** All failure paths handled gracefully; no StrictMode violations.

### Steps

1. **Offline detection:** Add network check before extraction:
   ```kotlin
   fun isNetworkAvailable(context: Context): Boolean {
       val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
       return cm.activeNetwork?.let { network ->
           cm.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
       } ?: false
   }
   ```
   In `NewPipeService`, check before extractor calls → return `Resource.Error("No internet connection")` immediately.

2. **Verify all Compose screens** use `collectAsStateWithLifecycle()`:
   ```kotlin
   // ✓ Correct:
   val uiState by viewModel.uiState.collectAsStateWithLifecycle()
   // ✗ Wrong (continues collecting in background):
   val uiState by viewModel.uiState.collectAsState()
   ```

3. **Verify LazyColumn keys** are set:
   ```kotlin
   items(results, key = { it.videoId }) { result ->
       SearchResultItem(result, onClick = { onVideoClick(result.videoId) })
   }
   ```

4. **Test all error states manually:**
   - [ ] Airplane mode → search → ErrorScreen shown with "Check your internet connection"
   - [ ] Airplane mode → player → ErrorScreen shown
   - [ ] Invalid videoId → player → ErrorScreen shown
   - [ ] Retry button → works correctly for both screens
   - [ ] 5 rapid query changes → only last result shown (flatMapLatest verified)

5. **Verify StrictMode passes** — run in debug mode, check Logcat for `StrictMode` tags. Expected: zero violations.

6. **Verify `res/xml/network_security_config.xml`**:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <network-security-config>
       <base-config cleartextTrafficPermitted="false">
           <trust-anchors>
               <certificates src="system" />
           </trust-anchors>
       </base-config>
   </network-security-config>
   ```

---

## Phase 11 — Polish + Performance

**Goal:** Smooth scrolling, no jank, no memory leaks.

### Steps

1. **LazyColumn `key` parameter** — verify all `items(...)` calls have `key = { it.videoId }`. Prevents full recomposition on scroll.

2. **Coil crossfade** — verify all `AsyncImage` calls have `.crossfade(true)` in `ImageRequest`.

3. **View count formatter** — verify `Long.toCompactViewCount()` extension in `VideoMapper.kt` is used everywhere (no raw Long display).

4. **`ExoPlayer.addListener`** — verify `Player.Listener` is removed in `onCleared()` or `PlayerManager.release()` to prevent listener leaks.

5. **`remember { exoPlayer }` pattern** — verify `ExoPlayerView` does not create a new ExoPlayer on recomposition; it receives the instance from `PlayerManager` via `PlayerViewModel`.

6. **Profile with Android Studio Profiler:**
   - CPU: Main thread should be idle during search (extractor runs on IO)
   - Memory: stable during scroll in `LazyColumn` (no growing heap)
   - Memory: no leak after navigating from PlayerScreen back to SearchScreen

7. **Test on real device (not emulator)** for video playback — hardware codecs are required for smooth DASH playback.

---

## Phase 12 — Release Preparation

**Goal:** Signed APK ready for distribution; ProGuard rules verified.

### Steps

1. **Verify `proguard-rules.pro`** from `Design.md §4` is complete — especially:
   - NewPipeExtractor keep rules
   - Jackson annotation rules
   - Rhino JS engine rules (critical for YouTube signature decryption)
   - Media3 service rules

2. **Build a release APK:**
   ```bash
   ./gradlew assembleRelease
   ```
   Install on device and test end-to-end: search → results → tap → playback → background audio → notification.

3. **Create a signing config** in `app/build.gradle.kts`:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
           storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
           keyAlias = System.getenv("KEY_ALIAS") ?: ""
           keyPassword = System.getenv("KEY_PASSWORD") ?: ""
       }
   }
   buildTypes {
       release {
           signingConfig = signingConfigs.getByName("release")
           // ...
       }
   }
   ```

4. **Write `README.md`:**
   - Project description + screenshots
   - Build instructions: `./gradlew assembleDebug`
   - Known limitations (NewPipe extractor breakage, DASH URL expiry)
   - F-Droid submission metadata (optional)

5. **Create GitHub Release** with:
   - Tag: `v1.0.0`
   - Release notes
   - Attached signed APK

### Validation
- Release APK: search works, playback works, background audio works
- No crashes on common error paths (offline, invalid video)
- APK size < 15MB (Compose + NewPipeExtractor baseline is ~12-13MB stripped)
