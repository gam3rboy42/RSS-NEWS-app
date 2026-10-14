package com.example.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.FeedEntity
import com.example.data.local.RssDao
import com.example.data.model.DealDetector
import com.example.data.model.DefaultFeedCatalog
import com.example.data.model.DislikeAnalysisResult
import com.example.data.model.DislikeAnalyzer
import com.example.data.model.StoryCluster
import com.example.data.model.StoryClusterer
import com.example.data.parser.RssXmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.data.model.FeedCategoryAutoTagger
import com.example.data.model.NpuTensorCategorizer
import java.util.concurrent.TimeUnit

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val fetchedCount: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class RssRepository(
    private val context: Context,
    private val rssDao: RssDao
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 6
        })
        .cache(try {
            Cache(context.cacheDir.resolve("rss_http_cache"), 10L * 1024L * 1024L)
        } catch (_: Exception) {
            null
        })
        .followRedirects(true)
        .build()

    val feeds: Flow<List<FeedEntity>> = rssDao.getAllFeeds()
    val rawArticles: Flow<List<ArticleEntity>> = rssDao.getAllArticles().map { list -> list.map { com.example.util.HtmlSanitizer.sanitizeArticle(it) } }
    val newsArticles: Flow<List<ArticleEntity>> = rssDao.getNewsArticles().map { list -> list.map { com.example.util.HtmlSanitizer.sanitizeArticle(it) } }
    val podcastArticles: Flow<List<ArticleEntity>> = rssDao.getPodcastArticles().map { list -> list.map { com.example.util.HtmlSanitizer.sanitizeArticle(it) } }
    val bookmarkedArticles: Flow<List<ArticleEntity>> = rssDao.getBookmarkedArticles().map { list -> list.map { com.example.util.HtmlSanitizer.sanitizeArticle(it) } }
    val offlineArticles: Flow<List<ArticleEntity>> = rssDao.getOfflineArticles().map { list -> list.map { com.example.util.HtmlSanitizer.sanitizeArticle(it) } }
    val readArticles: Flow<List<ArticleEntity>> = rssDao.getReadArticles().map { list -> list.map { com.example.util.HtmlSanitizer.sanitizeArticle(it) } }

    private val cachePrefs = context.getSharedPreferences("rss_cache_prefs", Context.MODE_PRIVATE)

    fun getLastSyncTimestamp(): Long {
        return cachePrefs.getLong("last_sync_timestamp", 0L)
    }

    fun updateLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        cachePrefs.edit().putLong("last_sync_timestamp", timestamp).apply()
    }

    suspend fun isCacheValid(cacheValidityMinutes: Long): Boolean {
        val count = getArticleCount()
        if (count == 0) return false
        val lastSync = getLastSyncTimestamp()
        if (lastSync == 0L) return false
        val elapsedMs = System.currentTimeMillis() - lastSync
        val validityMs = cacheValidityMinutes * 60 * 1000L
        return elapsedMs < validityMs
    }

    private val stopWords = setOf(
        "a", "an", "the", "in", "on", "at", "to", "for", "of", "with", "by", "from",
        "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
        "it", "its", "this", "that", "these", "those", "how", "what", "why", "who",
        "new", "says", "report", "first", "after", "over", "about", "more", "will",
        "has", "have", "had", "can", "could", "should", "would", "may", "might"
    )

    suspend fun initializeDefaultsIfNeeded() {
        withContext(Dispatchers.IO) {
            val existingFeeds = rssDao.getAllFeedsList()
            if (existingFeeds.isEmpty()) {
                val defaultFeeds = DefaultFeedCatalog.toDefaultEntities()
                rssDao.insertDefaultFeeds(defaultFeeds)
            } else if (existingFeeds.none { it.category.equals("PODCASTS", ignoreCase = true) }) {
                val podcastDefaults = DefaultFeedCatalog.curatedPodcastFeeds.map { item ->
                    FeedEntity(
                        url = item.url,
                        title = item.title,
                        category = item.category,
                        description = item.description,
                        isPreferred = item.isDefaultPreferred,
                        isEnabled = true,
                        isCustom = false
                    )
                }
                rssDao.insertDefaultFeeds(podcastDefaults)
            }
            // Clean up any previously misclassified news articles from legacy podcast tables
            rssDao.cleanupMistaggedPodcastArticles()

            // Sanitize any existing stored articles with raw HTML entities
            try {
                val dirty = rssDao.getAllArticlesList().filter {
                    it.title.contains("&#") || it.title.contains("&amp;") || it.title.contains("&quot;") ||
                    it.description.contains("&#") || it.description.contains("&amp;") || it.description.contains("&quot;") ||
                    it.content.contains("&#") || it.content.contains("&amp;") || it.content.contains("&quot;")
                }
                if (dirty.isNotEmpty()) {
                    val cleaned = dirty.map { com.example.util.HtmlSanitizer.sanitizeArticle(it) }
                    rssDao.insertArticles(cleaned)
                }
            } catch (e: Exception) {
                Log.e("RssRepository", "Error cleaning HTML entities from existing articles: ${e.localizedMessage}")
            }
        }
    }

    fun isDeviceOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            if (capabilities != null) {
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
            return cm.activeNetwork != null
        }
        return true
    }

    private data class SingleFeedFetchResult(
        val articles: List<ArticleEntity>,
        val updatedIconUrl: Pair<String, String>? = null
    )

    private fun fetchSingleFeed(feed: FeedEntity): SingleFeedFetchResult {
        try {
            val request = Request.Builder()
                .url(feed.url)
                .header("User-Agent", "Mozilla/5.0 (Android; Nothing RSS News Reader)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful && resp.body != null) {
                    val parsed = RssXmlParser.parse(
                        inputStream = resp.body!!.byteStream(),
                        feedUrl = feed.url,
                        defaultCategory = feed.category,
                        defaultFeedTitle = feed.title,
                        isPreferredSource = feed.isPreferred
                    )

                    var updatedIcon: Pair<String, String>? = null
                    var currentFeedIcon = feed.iconUrl
                    if (parsed.iconUrl.isNotBlank() && currentFeedIcon.isBlank()) {
                        currentFeedIcon = parsed.iconUrl
                        updatedIcon = Pair(feed.url, currentFeedIcon)
                    }

                    val processed = DealDetector.processArticles(parsed.articles).map { article ->
                        if (article.imageUrl.isNullOrBlank() && currentFeedIcon.isNotBlank()) {
                            article.copy(imageUrl = currentFeedIcon)
                        } else {
                            article
                        }
                    }
                    return SingleFeedFetchResult(processed, updatedIcon)
                }
            }
        } catch (e: Exception) {
            Log.e("RssRepository", "Error fetching feed ${feed.url}: ${e.localizedMessage}")
        }
        return SingleFeedFetchResult(emptyList())
    }

    private suspend fun refreshFeedsInternal(feedsToRefresh: List<FeedEntity>): SyncState = withContext(Dispatchers.IO) {
        if (feedsToRefresh.isEmpty()) {
            return@withContext SyncState.Success(0)
        }

        try {
            // Fetch feeds concurrently with max 6 parallel network calls
            val semaphore = Semaphore(6)
            val feedResults = coroutineScope {
                feedsToRefresh.map { feed ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            fetchSingleFeed(feed)
                        }
                    }
                }.awaitAll()
            }

            val newArticles = feedResults.flatMap { it.articles }
            val iconUpdates = feedResults.mapNotNull { it.updatedIconUrl }

            for ((feedUrl, iconUrl) in iconUpdates) {
                rssDao.updateFeedIconUrl(feedUrl, iconUrl)
            }

            // Auto-classify feeds with generic or missing categories via NPU on RSS pull
            for (feed in feedsToRefresh) {
                if (feed.category.equals("GENERAL", ignoreCase = true) || 
                    feed.category.equals("UNCATEGORIZED", ignoreCase = true) || 
                    feed.category.isBlank()) {
                    val sampleTitles = newArticles.filter { it.feedUrl.equals(feed.url, ignoreCase = true) }.map { it.title }
                    val classification = com.example.data.model.NpuTensorCategorizer.classifyFeed(
                        feedTitle = feed.title,
                        feedDescription = feed.description,
                        feedUrl = feed.url,
                        sampleArticleTitles = sampleTitles
                    )
                    if (classification.category.isNotBlank() && classification.category != "GENERAL") {
                        rssDao.updateFeedCategory(feed.url, classification.category)
                    }
                }
            }

            // Perform NPU neural filtering & categorization on the RSS pull before persisting to database
            val currentFeeds = rssDao.getAllFeedsList()
            val feedCatMap = currentFeeds.associate { it.url.trim().lowercase() to it.category }
            val npuProcessedArticles = com.example.data.model.NpuTensorCategorizer.processArticlesOnTheFly(
                articles = newArticles,
                feedCategoryMap = feedCatMap,
                enableReclassification = true
            )

            // Check if newly pulled articles are clearly not full in RSS (stubs, summaries, truncated)
            // and download their full webpage so they can be viewed in full
            val enrichedArticles = if (isDeviceOnline() && npuProcessedArticles.isNotEmpty()) {
                val incompleteArticles = npuProcessedArticles.filter { art ->
                    !art.isPodcast && !art.isVideoPodcast &&
                    !art.category.equals("PODCASTS", ignoreCase = true) &&
                    art.link.isNotBlank() &&
                    com.example.util.WebpageArticleExtractor.isArticleIncomplete(art)
                }

                if (incompleteArticles.isNotEmpty()) {
                    val webpageSemaphore = Semaphore(4)
                    val fullContentMap = coroutineScope {
                        incompleteArticles.take(15).map { art ->
                            async(Dispatchers.IO) {
                                webpageSemaphore.withPermit {
                                    val extracted = com.example.util.WebpageArticleExtractor.extractFromWebpage(art.link, okHttpClient)
                                    if (extracted != null && extracted.fullText.length > art.content.length) {
                                        art.id to extracted
                                    } else {
                                        null
                                    }
                                }
                            }
                        }.awaitAll().filterNotNull().toMap()
                    }

                    npuProcessedArticles.map { art ->
                        val extracted = fullContentMap[art.id]
                        if (extracted != null) {
                            art.copy(
                                content = extracted.fullText,
                                imageUrl = if (art.imageUrl.isNullOrBlank()) extracted.leadImageUrl else art.imageUrl
                            )
                        } else {
                            art
                        }
                    }
                } else {
                    npuProcessedArticles
                }
            } else {
                npuProcessedArticles
            }

            if (enrichedArticles.isNotEmpty()) {
                rssDao.insertArticles(enrichedArticles)

                // Auto-download preferred podcast episodes on Wi-Fi
                if (com.example.util.PodcastCacheManager.getAutoDownloadWifi(context) &&
                    com.example.util.PodcastCacheManager.isWifiConnected(context)) {
                    val preferredPodcasts = enrichedArticles.filter { art ->
                        (art.isPodcast || art.isVideoPodcast || art.mediaType == "AUDIO" || art.mediaType == "VIDEO" || art.category.equals("PODCASTS", ignoreCase = true)) &&
                        art.isPreferredSource
                    }
                    for (pod in preferredPodcasts) {
                        com.example.util.PodcastCacheManager.autoDownloadIfWifi(context, pod, rssDao)
                    }
                }
            }

            // Generate topic recommendations from liked stories if online
            generateDiscoveredRecommendationsInternal()

            updateLastSyncTimestamp()

            SyncState.Success(newArticles.size)
        } catch (e: Exception) {
            SyncState.Error(e.localizedMessage ?: "Failed to update feeds")
        }
    }

    suspend fun refreshFeeds(): SyncState = withContext(Dispatchers.IO) {
        initializeDefaultsIfNeeded()
        val enabledFeeds = rssDao.getEnabledFeeds()
        refreshFeedsInternal(enabledFeeds)
    }

    suspend fun refreshNewsFeedsOnly(): SyncState = withContext(Dispatchers.IO) {
        initializeDefaultsIfNeeded()
        val newsFeeds = rssDao.getEnabledNewsFeeds()
        refreshFeedsInternal(newsFeeds)
    }

    suspend fun refreshPodcastFeedsOnly(): SyncState = withContext(Dispatchers.IO) {
        initializeDefaultsIfNeeded()
        val podcastFeeds = rssDao.getEnabledPodcastFeeds()
        refreshFeedsInternal(podcastFeeds)
    }

    private suspend fun generateDiscoveredRecommendationsInternal() {
        try {
            val liked = rssDao.getLikedArticles()
            if (liked.isEmpty()) return

            // Extract liked authors
            val likedAuthors = liked.map { it.author.trim() }.filter { it.isNotBlank() && it.length >= 3 }.map { it.lowercase() }.toSet()

            // 1. Check local articles in Room database from the same author
            for (author in likedAuthors) {
                val authorArticles = rssDao.getArticlesByAuthor(author)
                for (art in authorArticles) {
                    if (!art.isDiscoveredRecommendation && !art.isLiked) {
                        rssDao.markAsDiscoveredRecommendation(art.id)
                    }
                }
            }

            if (!isDeviceOnline()) return

            // 2. Extract liked categories and keywords
            val likedCategories = liked.map { it.category.uppercase() }.toSet()
            val likedKeywords = liked.flatMap { article ->
                val text = (article.title + " " + article.description).lowercase()
                text.split(Regex("[^a-zA-Z0-9]+"))
                    .filter { word -> word.length >= 4 && !stopWords.contains(word) }
            }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(20).map { it.key }.toSet()

            // 3. Find candidate feeds from DefaultFeedCatalog
            val enabledFeeds = rssDao.getEnabledFeeds()
            val activeFeedUrls = enabledFeeds.map { it.url }.toSet()

            val candidateFeeds = DefaultFeedCatalog.curatedFeeds.filter { catalogItem ->
                !activeFeedUrls.contains(catalogItem.url) &&
                        (likedCategories.contains(catalogItem.category.uppercase()) || likedCategories.contains("ALL"))
            }.take(3)

            if (candidateFeeds.isEmpty()) return

            val semaphore = Semaphore(3)
            val recommendedArticles = coroutineScope {
                candidateFeeds.map { candidate ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val request = Request.Builder()
                                    .url(candidate.url)
                                    .header("User-Agent", "Mozilla/5.0 (Android; Nothing RSS News Reader)")
                                    .build()

                                val response = okHttpClient.newCall(request).execute()
                                response.use { resp ->
                                    if (resp.isSuccessful && resp.body != null) {
                                        val parsed = RssXmlParser.parse(
                                            inputStream = resp.body!!.byteStream(),
                                            feedUrl = candidate.url,
                                            defaultCategory = candidate.category,
                                            defaultFeedTitle = candidate.title,
                                            isPreferredSource = false
                                        )

                                        parsed.articles.filter { article ->
                                            val authorMatch = likedAuthors.isNotEmpty() && article.author.isNotBlank() &&
                                                    likedAuthors.any { la -> article.author.lowercase().contains(la) }
                                            val artText = (article.title + " " + article.description).lowercase()
                                            val keywordMatch = likedKeywords.any { kw -> artText.contains(kw) }
                                            authorMatch || keywordMatch
                                        }.take(3).map { it.copy(isDiscoveredRecommendation = true) }
                                    } else emptyList()
                                }
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }
                }.awaitAll().flatten()
            }

            if (recommendedArticles.isNotEmpty()) {
                val processed = DealDetector.processArticles(recommendedArticles)
                rssDao.insertArticles(processed)
            }
        } catch (e: Exception) {
            Log.e("RssRepository", "Error generating recommendations: ${e.localizedMessage}")
        }
    }

    fun getClusteredFeedStream(
        categoryFilter: String,
        timeRangeFilter: com.example.data.model.TimeRangeFilter,
        hideDeals: Boolean,
        onlyPreferredFeeds: Boolean,
        onlyBookmarks: Boolean,
        onlyOffline: Boolean,
        onlyRecommendations: Boolean,
        onlyReadHistory: Boolean,
        randomSeed: Long
    ): Flow<List<StoryCluster>> {
        val baseArticlesFlow = when {
            onlyOffline -> offlineArticles
            onlyBookmarks -> bookmarkedArticles
            onlyReadHistory -> readArticles
            else -> newsArticles
        }

        return combine(baseArticlesFlow, feeds) { articles, allFeeds ->
            fun normalizeUrl(url: String) = url.trim().trimEnd('/').lowercase()

            val feedPreferredMap = allFeeds.associate { 
                normalizeUrl(it.url) to Pair(it.isPreferred, it.preferredScope) 
            }
            val feedCategoryMap = allFeeds.associate { normalizeUrl(it.url) to it.category }
            val enabledUrlSet = allFeeds.filter { it.isEnabled }.map { normalizeUrl(it.url) }.toSet()

            // Articles are already filtered and categorized by NPU on the RSS pull before being persisted in Room.
            // On app open and flow emission, we consume the pre-categorized articles directly from Room.
            val filteredArticles = articles.map { article ->
                val normUrl = normalizeUrl(article.feedUrl)
                val (feedIsPref, feedPrefScope) = feedPreferredMap[normUrl] ?: Pair(article.isPreferredSource, FeedEntity.SCOPE_ALL)
                val feedCat = feedCategoryMap[normUrl] ?: article.category

                // Context-aware preferred source evaluation:
                // - In "ALL" stream: only feeds preferred for ALL feeds are considered preferred.
                // - In specific category streams: all feeds preferred for that category (CATEGORY or ALL) are considered preferred!
                val isPrefInCurrentContext = when {
                    !feedIsPref -> false
                    categoryFilter == "ALL" -> feedPrefScope.equals(FeedEntity.SCOPE_ALL, ignoreCase = true) || feedPrefScope.isBlank()
                    else -> true
                }

                var updated = article
                if (article.isPreferredSource != isPrefInCurrentContext) {
                    updated = updated.copy(isPreferredSource = isPrefInCurrentContext)
                }
                // If the story wasn't specifically reclassified by NPU and parent feed has non-generic category, inherit feed category
                if (!article.isNpuCategorized && !feedCat.isNullOrBlank() && feedCat != "GENERAL" && updated.category != feedCat) {
                    updated = updated.copy(category = feedCat)
                }
                updated
            }.filter { article ->
                val normUrl = normalizeUrl(article.feedUrl)
                // Must belong to an enabled feed, or allFeeds is empty, OR be a discovered recommendation
                val isFeedEnabledOrRecommendation = allFeeds.isEmpty() || enabledUrlSet.contains(normUrl) || article.isDiscoveredRecommendation
                val isPodcastArticle = article.isPodcast ||
                        article.isVideoPodcast ||
                        article.category.equals("PODCASTS", ignoreCase = true) ||
                        article.mediaType == "AUDIO" ||
                        article.mediaType == "VIDEO"

                // Category filter: separate podcasts from regular news stream
                val matchesCategory = when {
                    onlyBookmarks || onlyOffline || onlyReadHistory -> true
                    categoryFilter == "ALL" -> !isPodcastArticle
                    categoryFilter.equals("PODCASTS", ignoreCase = true) -> isPodcastArticle
                    else -> article.category.equals(categoryFilter, ignoreCase = true) && !isPodcastArticle
                }
                // Hide deals filter
                val matchesDeals = !hideDeals || !article.isDeal
                // Preferred filter
                val matchesPreferred = !onlyPreferredFeeds || article.isPreferredSource
                // Recommendations filter
                val matchesRecommendations = !onlyRecommendations || article.isDiscoveredRecommendation

                // Dislike, Muted, and Archive filters
                val notArchived = !article.isArchived || onlyReadHistory
                val notExplicitlyDisliked = !article.isDisliked
                val mutedKws = getMutedKeywords()
                val mutedAuts = getMutedAuthors()
                val titleAndDescLower = (article.title + " " + article.description).lowercase()
                val matchesMutedKeyword = mutedKws.any { kw -> kw.isNotBlank() && titleAndDescLower.contains(kw.lowercase()) }
                val matchesMutedAuthor = article.author.isNotBlank() && mutedAuts.any { aut -> aut.isNotBlank() && article.author.contains(aut, ignoreCase = true) }

                isFeedEnabledOrRecommendation && matchesCategory && matchesDeals && matchesPreferred && matchesRecommendations && notArchived && notExplicitlyDisliked && !matchesMutedKeyword && !matchesMutedAuthor
            }

            // Cluster identical stories with time range and semi-random ordering
            StoryClusterer.clusterArticles(
                articles = filteredArticles,
                timeRangeFilter = timeRangeFilter,
                randomSeed = randomSeed
            )
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Dedicated Podcast stream directly streaming podcast audio/video episodes.
     * Completely decoupled from the news feed clustering and sorting pipeline.
     */
    fun getPodcastStream(): Flow<List<StoryCluster>> {
        return combine(podcastArticles, feeds) { episodes, allFeeds ->
            fun normalizeUrl(url: String) = url.trim().trimEnd('/').lowercase()

            val feedCategoryMap = allFeeds.associate { 
                normalizeUrl(it.url) to it.category.uppercase().trim()
            }
            val feedPreferredMap = allFeeds.associate { 
                normalizeUrl(it.url) to it.isPreferred 
            }
            val enabledUrlSet = allFeeds.filter { it.isEnabled }.map { normalizeUrl(it.url) }.toSet()

            fun isPullableAudio(art: ArticleEntity): Boolean {
                if (art.mediaType.equals("AUDIO", ignoreCase = true)) return true
                val mUrl = art.mediaUrl?.lowercase() ?: ""
                if (mUrl.contains(".mp3") || mUrl.contains(".m4a") || mUrl.contains(".ogg") ||
                    mUrl.contains(".wav") || mUrl.contains(".aac") || mUrl.contains(".flac") ||
                    mUrl.contains(".opus") || mUrl.contains("/audio") || mUrl.contains("audio/")) {
                    return true
                }
                val lUrl = art.link.lowercase()
                if (lUrl.endsWith(".mp3") || lUrl.endsWith(".m4a") || lUrl.contains(".mp3?") || lUrl.contains(".m4a?")) {
                    return true
                }
                return false
            }

            val filtered = episodes.filter { ep ->
                val normUrl = normalizeUrl(ep.feedUrl)
                val isFeedEnabled = allFeeds.isEmpty() || enabledUrlSet.contains(normUrl) || ep.isDiscoveredRecommendation
                val notArchived = !ep.isArchived
                val notDisliked = !ep.isDisliked

                // Strict Podcast Eligibility:
                // Nothing that is not in the podcast category folder can be in the podcast page
                // unless an audio file is able to be pulled.
                val parentFeedCat = feedCategoryMap[normUrl] ?: ""
                val isInPodcastFolder = ep.category.equals("PODCASTS", ignoreCase = true) ||
                        parentFeedCat == "PODCASTS"
                val audioPullable = isPullableAudio(ep)

                val allowedOnPodcastPage = isInPodcastFolder || audioPullable

                isFeedEnabled && notArchived && notDisliked && allowedOnPodcastPage
            }

            filtered.map { ep ->
                val normUrl = normalizeUrl(ep.feedUrl)
                val isPref = feedPreferredMap[normUrl] ?: ep.isPreferredSource
                val updatedEp = if (ep.isPreferredSource != isPref) ep.copy(isPreferredSource = isPref) else ep

                StoryCluster(
                    clusterId = "podcast_${ep.id.hashCode()}",
                    primaryTitle = updatedEp.title,
                    category = "PODCASTS",
                    subcategory = updatedEp.subcategory.ifBlank { "GENERAL" },
                    isDeal = false,
                    isBookmarked = updatedEp.isBookmarked,
                    latestTimestamp = updatedEp.pubDateTimestamp,
                    articles = listOf(updatedEp)
                )
            }.sortedWith(
                compareByDescending<StoryCluster> { it.articles.any { art -> art.isPreferredSource } }
                    .thenByDescending { it.latestTimestamp }
            )
        }.flowOn(Dispatchers.Default)
    }

    suspend fun toggleBookmark(articleId: String, currentStatus: Boolean) {
        withContext(Dispatchers.IO) {
            rssDao.updateBookmarkStatus(articleId, !currentStatus)
        }
    }

    suspend fun toggleOfflineStatus(articleId: String, currentSaved: Boolean) {
        withContext(Dispatchers.IO) {
            val newStatus = !currentSaved
            rssDao.updateOfflineStatus(articleId, newStatus)
            if (newStatus && isDeviceOnline()) {
                val article = rssDao.getArticleById(articleId)
                if (article != null && com.example.util.WebpageArticleExtractor.isArticleIncomplete(article)) {
                    val extracted = com.example.util.WebpageArticleExtractor.extractFromWebpage(article.link, okHttpClient)
                    if (extracted != null && extracted.fullText.length > article.content.length) {
                        rssDao.updateArticleFullContent(
                            id = article.id,
                            content = extracted.fullText,
                            imageUrl = if (article.imageUrl.isNullOrBlank()) extracted.leadImageUrl else null
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchAndSaveFullArticleWebpage(articleId: String): ArticleEntity? = withContext(Dispatchers.IO) {
        val current = rssDao.getArticleById(articleId) ?: return@withContext null
        if (current.link.isBlank()) return@withContext current

        val extracted = com.example.util.WebpageArticleExtractor.extractFromWebpage(current.link, okHttpClient)
        if (extracted != null && extracted.fullText.length > current.content.length) {
            val updated = current.copy(
                content = extracted.fullText,
                imageUrl = if (current.imageUrl.isNullOrBlank()) extracted.leadImageUrl else current.imageUrl
            )
            rssDao.updateArticleFullContent(
                id = current.id,
                content = extracted.fullText,
                imageUrl = if (current.imageUrl.isNullOrBlank()) extracted.leadImageUrl else null
            )
            return@withContext com.example.util.HtmlSanitizer.sanitizeArticle(updated)
        }
        return@withContext current
    }

    suspend fun toggleLikedStatus(articleId: String, currentLiked: Boolean) {
        withContext(Dispatchers.IO) {
            rssDao.updateLikedStatus(articleId, !currentLiked)
        }
    }

    suspend fun flagArticleNotPodcast(article: ArticleEntity, reclassifyEntireFeed: Boolean = false) {
        withContext(Dispatchers.IO) {
            val npuResult = com.example.data.model.NpuTensorCategorizer.classifyStory(
                title = article.title,
                description = article.description,
                content = article.content,
                feedCategory = "GENERAL"
            )
            val targetCategory = if (npuResult.category.isNotBlank() && !npuResult.category.equals("PODCASTS", ignoreCase = true)) {
                npuResult.category.uppercase().trim()
            } else {
                "TECH"
            }
            val targetSubcat = npuResult.subcategory.ifBlank { "GENERAL" }

            rssDao.flagArticleNotPodcast(article.id, targetCategory, targetSubcat)

            if (reclassifyEntireFeed) {
                rssDao.updateFeedCategory(article.feedUrl, targetCategory)
                rssDao.flagFeedArticlesNotPodcast(article.feedUrl, targetCategory)
            }
        }
    }

    suspend fun archiveCluster(cluster: StoryCluster) {
        withContext(Dispatchers.IO) {
            val hash = cluster.primaryArticle.storyClusterHash
            if (hash.isNotBlank()) {
                rssDao.updateClusterArchivedStatus(hash, true)
            }
            cluster.articles.forEach { article ->
                rssDao.updateArchivedStatus(article.id, true)
            }
        }
    }

    suspend fun unarchiveCluster(cluster: StoryCluster) {
        withContext(Dispatchers.IO) {
            val hash = cluster.primaryArticle.storyClusterHash
            if (hash.isNotBlank()) {
                rssDao.updateClusterArchivedStatus(hash, false)
            }
            cluster.articles.forEach { article ->
                rssDao.updateArchivedStatus(article.id, false)
            }
        }
    }

    suspend fun decoupleArticle(articleId: String) {
        withContext(Dispatchers.IO) {
            rssDao.updateDecoupledStatus(articleId, true)
        }
    }

    suspend fun decoupleCluster(cluster: StoryCluster) {
        withContext(Dispatchers.IO) {
            val ids = cluster.articles.map { it.id }
            rssDao.updateDecoupledStatusForIds(ids, true)
        }
    }

    private val dislikePrefs = context.getSharedPreferences("nothing_dislikes_prefs", Context.MODE_PRIVATE)

    fun getMutedKeywords(): Set<String> {
        return dislikePrefs.getStringSet("muted_keywords", emptySet()) ?: emptySet()
    }

    fun getMutedAuthors(): Set<String> {
        return dislikePrefs.getStringSet("muted_authors", emptySet()) ?: emptySet()
    }

    fun getMutedSubcategories(): Set<String> {
        return dislikePrefs.getStringSet("muted_subcategories", emptySet()) ?: emptySet()
    }

    fun unmuteKeyword(keyword: String) {
        val current = getMutedKeywords().toMutableSet()
        current.remove(keyword)
        dislikePrefs.edit().putStringSet("muted_keywords", current).apply()
    }

    fun unmuteAuthor(author: String) {
        val current = getMutedAuthors().toMutableSet()
        current.remove(author)
        dislikePrefs.edit().putStringSet("muted_authors", current).apply()
    }

    fun unmuteSubcategory(subcat: String) {
        val current = getMutedSubcategories().toMutableSet()
        current.remove(subcat)
        dislikePrefs.edit().putStringSet("muted_subcategories", current).apply()
    }

    fun muteKeywords(keywords: Set<String>) {
        if (keywords.isEmpty()) return
        val current = getMutedKeywords().toMutableSet()
        current.addAll(keywords)
        dislikePrefs.edit().putStringSet("muted_keywords", current).apply()
    }

    fun muteAuthor(author: String) {
        if (author.isBlank()) return
        val current = getMutedAuthors().toMutableSet()
        current.add(author)
        dislikePrefs.edit().putStringSet("muted_authors", current).apply()
    }

    fun muteSubcategory(subcat: String) {
        if (subcat.isBlank() || subcat == "GENERAL") return
        val current = getMutedSubcategories().toMutableSet()
        current.add(subcat)
        dislikePrefs.edit().putStringSet("muted_subcategories", current).apply()
    }

    suspend fun muteFeed(feedUrl: String) {
        if (feedUrl.isBlank()) return
        toggleFeedEnabled(feedUrl, true)
    }

    suspend fun toggleDislikedStatus(articleId: String, currentDisliked: Boolean): DislikeAnalysisResult? {
        return withContext(Dispatchers.IO) {
            val newStatus = !currentDisliked
            rssDao.updateDislikedStatus(articleId, newStatus)

            if (newStatus) {
                val article = rssDao.getArticleById(articleId)
                if (article != null) {
                    return@withContext DislikeAnalyzer.analyzeDislike(article)
                }
            }
            null
        }
    }

    suspend fun markArticleRead(articleId: String) {
        withContext(Dispatchers.IO) {
            rssDao.markArticleAsRead(articleId)
        }
    }

    suspend fun clearReadHistory() {
        withContext(Dispatchers.IO) {
            rssDao.clearReadHistory()
        }
    }

    suspend fun getSampleArticleForNotification(): ArticleEntity? {
        return withContext(Dispatchers.IO) {
            val likedList = rssDao.getLikedArticles()
            likedList.firstOrNull() ?: rssDao.getLatestArticle()
        }
    }

    suspend fun getSamplePodcastForNotification(): ArticleEntity? {
        return withContext(Dispatchers.IO) {
            val allPodcasts = rssDao.getAllArticlesList().filter {
                it.isPodcast || it.isVideoPodcast || it.mediaType == "AUDIO" || it.mediaType == "VIDEO" || it.category.equals("PODCASTS", ignoreCase = true)
            }
            allPodcasts.firstOrNull { it.isPreferredSource } ?: allPodcasts.firstOrNull()
        }
    }

    suspend fun toggleFeedPreferred(feedUrl: String, currentPreferred: Boolean) {
        cycleFeedPreferred(feedUrl)
    }

    suspend fun setFeedPreferredWithScope(
        feedUrl: String,
        isPreferred: Boolean,
        preferredScope: String = FeedEntity.SCOPE_ALL
    ) = withContext(Dispatchers.IO) {
        val cleanUrl = feedUrl.trim()
        val altUrl = if (cleanUrl.endsWith("/")) cleanUrl.dropLast(1) else "$cleanUrl/"
        val existing = rssDao.getFeedByUrl(cleanUrl) ?: rssDao.getFeedByUrl(altUrl)

        if (existing != null) {
            rssDao.updateFeedPreferredWithScope(existing.url, isPreferred, preferredScope)
        } else {
            val catalogItem = DefaultFeedCatalog.curatedFeeds.find {
                it.url.equals(cleanUrl, ignoreCase = true) || it.url.equals(altUrl, ignoreCase = true)
            }
            val newFeed = FeedEntity(
                url = cleanUrl,
                title = catalogItem?.title ?: "Feed",
                category = catalogItem?.category ?: "GENERAL",
                description = catalogItem?.description ?: "",
                isPreferred = isPreferred,
                preferredScope = preferredScope,
                isEnabled = true,
                isCustom = false
            )
            rssDao.insertFeed(newFeed)
        }
    }

    /**
     * Cycles through preferred states:
     * 1. OFF -> 2. CATEGORY_ONLY (★ Category) -> 3. ALL_FEEDS (★ All) -> 4. OFF
     */
    suspend fun cycleFeedPreferred(feedUrl: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUrl = feedUrl.trim()
        val altUrl = if (cleanUrl.endsWith("/")) cleanUrl.dropLast(1) else "$cleanUrl/"
        val existing = rssDao.getFeedByUrl(cleanUrl) ?: rssDao.getFeedByUrl(altUrl)

        if (existing == null) {
            val catalogItem = DefaultFeedCatalog.curatedFeeds.find {
                it.url.equals(cleanUrl, ignoreCase = true) || it.url.equals(altUrl, ignoreCase = true)
            }
            val newFeed = FeedEntity(
                url = cleanUrl,
                title = catalogItem?.title ?: "Feed",
                category = catalogItem?.category ?: "GENERAL",
                description = catalogItem?.description ?: "",
                isPreferred = true,
                preferredScope = FeedEntity.SCOPE_CATEGORY,
                isEnabled = true,
                isCustom = false
            )
            rssDao.insertFeed(newFeed)
            Pair(true, FeedEntity.SCOPE_CATEGORY)
        } else {
            val nextState = when {
                !existing.isPreferred -> Pair(true, FeedEntity.SCOPE_CATEGORY)
                existing.preferredScope.equals(FeedEntity.SCOPE_CATEGORY, ignoreCase = true) -> Pair(true, FeedEntity.SCOPE_ALL)
                else -> Pair(false, FeedEntity.SCOPE_ALL)
            }
            rssDao.updateFeedPreferredWithScope(existing.url, nextState.first, nextState.second)
            nextState
        }
    }

    suspend fun runNpuReclassificationOnAllFeedsAndStories(): NpuTensorCategorizer.BatchClassificationSummary = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val allFeedsList = rssDao.getAllFeedsList()
        val allArticlesList = rssDao.getAllArticlesList()

        var feedsUpdated = 0
        var storiesUpdated = 0
        val updatedFeedCatMap = mutableMapOf<String, String>()

        // 1. Classify All Feeds with NPU Engine
        for (feed in allFeedsList) {
            val isPodcastFeed = feed.category.equals("PODCASTS", ignoreCase = true)
            if (isPodcastFeed) {
                updatedFeedCatMap[feed.url.trim().lowercase()] = "PODCASTS"
                continue
            }

            val feedArticles = allArticlesList.filter { it.feedUrl.equals(feed.url, ignoreCase = true) }
            val feedClassification = com.example.data.model.NpuTensorCategorizer.classifyFeed(
                feedTitle = feed.title,
                feedDescription = feed.description,
                feedUrl = feed.url,
                sampleArticleTitles = feedArticles.map { it.title }
            )

            val newFeedCat = feedClassification.category
            if (newFeedCat.isNotBlank() && newFeedCat != "GENERAL") {
                if (newFeedCat != feed.category) {
                    rssDao.updateFeedCategory(feed.url, newFeedCat)
                    feedsUpdated++
                }
                updatedFeedCatMap[feed.url.trim().lowercase()] = newFeedCat
            } else {
                updatedFeedCatMap[feed.url.trim().lowercase()] = feed.category
            }
        }

        // 2. Classify All Stories with NPU Engine
        for (article in allArticlesList) {
            val isPodcastItem = article.isPodcast || article.isVideoPodcast ||
                    article.mediaType == "AUDIO" || article.mediaType == "VIDEO" ||
                    article.category.equals("PODCASTS", ignoreCase = true)
            if (isPodcastItem) continue

            val feedCat = updatedFeedCatMap[article.feedUrl.trim().lowercase()] ?: article.category
            val result = com.example.data.model.NpuTensorCategorizer.classifyStory(
                title = article.title,
                description = article.description,
                content = article.content,
                feedCategory = feedCat,
                feedUrl = article.feedUrl
            )

            val finalCat = if (result.category.isNotBlank() && result.category != "GENERAL") result.category else feedCat
            val finalSubcat = result.subcategory

            var changed = false
            if (finalCat.isNotBlank() && finalCat != article.category) {
                rssDao.updateArticleCategoryById(article.id, finalCat, false)
                changed = true
            }
            if (finalSubcat.isNotBlank() && finalSubcat != article.subcategory) {
                rssDao.updateArticleSubcategoryById(article.id, finalSubcat)
                changed = true
            }
            if (changed) {
                storiesUpdated++
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        NpuTensorCategorizer.BatchClassificationSummary(
            feedsClassified = feedsUpdated,
            storiesClassified = storiesUpdated,
            hardwareEngine = NpuTensorCategorizer.hardwareInfo.acceleratorType,
            executionTimeMs = elapsed
        )
    }

    suspend fun runNpuReclassificationOnAllArticles(): Int = withContext(Dispatchers.IO) {
        val summary = runNpuReclassificationOnAllFeedsAndStories()
        summary.storiesClassified + summary.feedsClassified
    }

    suspend fun teachAndLabelArticle(
        articleId: String,
        newCategory: String,
        newSubcategory: String = "",
        updateSourceFeed: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val article = rssDao.getArticleById(articleId) ?: return@withContext false
        val prevCategory = article.category

        // 1. Train on-device NPU tensor model
        NpuTensorCategorizer.recordUserLabel(
            context = context,
            title = article.title,
            description = article.description,
            content = article.content,
            feedUrl = article.feedUrl,
            newCategory = newCategory,
            newSubcategory = newSubcategory,
            previousCategory = prevCategory
        )

        // Also record in AutoTagger for backward compatibility
        FeedCategoryAutoTagger.recordUserTagging(context, article.feedUrl, article.title, newCategory)

        // 2. Update the article in Room
        val finalSubcat = if (newSubcategory.isNotBlank()) newSubcategory else com.example.data.model.SubcategoryAnalyzer.analyze(article.title, article.description, newCategory)
        rssDao.updateArticleCategoryAndSubcategoryById(articleId, newCategory.uppercase().trim(), finalSubcat)

        // 3. If user requested to apply to source feed as well
        if (updateSourceFeed && article.feedUrl.isNotBlank()) {
            rssDao.updateFeedCategory(article.feedUrl, newCategory.uppercase().trim())
            val feed = rssDao.getFeedByUrl(article.feedUrl)
            if (feed != null) {
                NpuTensorCategorizer.recordUserFeedLabel(
                    context = context,
                    feedUrl = feed.url,
                    feedTitle = feed.title,
                    feedDescription = feed.description,
                    newCategory = newCategory
                )
            }
        }
        true
    }

    suspend fun teachAndLabelFeed(
        feedUrl: String,
        newCategory: String
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanCat = newCategory.uppercase().trim()
        val feed = rssDao.getFeedByUrl(feedUrl) ?: return@withContext false

        // 1. Train on-device NPU
        NpuTensorCategorizer.recordUserFeedLabel(
            context = context,
            feedUrl = feed.url,
            feedTitle = feed.title,
            feedDescription = feed.description,
            newCategory = cleanCat
        )
        FeedCategoryAutoTagger.recordUserTagging(context, feed.url, feed.title, cleanCat)

        // 2. Update Feed in DB
        rssDao.updateFeedCategory(feedUrl, cleanCat)

        // 3. Update articles from this feed
        rssDao.updateArticlesCategoryByFeedUrl(feedUrl, cleanCat, cleanCat == "PODCASTS")
        true
    }

    fun getNpuModelLearningStats(): NpuTensorCategorizer.ModelLearningStats {
        return NpuTensorCategorizer.getLearnedModelStats(context)
    }

    fun resetNpuLearnedWeights() {
        NpuTensorCategorizer.clearLearnedWeights(context)
    }

    suspend fun autoTagAllFeedsOnDevice(): Int = withContext(Dispatchers.IO) {
        val allFeedsList = rssDao.getAllFeedsList()
        val allArticlesList = rssDao.getAllArticlesList()

        val autoTaggedMap = FeedCategoryAutoTagger.autoTagAllFeeds(context, allFeedsList, allArticlesList)
        var updatedCount = 0

        for (feed in allFeedsList) {
            val detected = autoTaggedMap[feed.url]
            if (!detected.isNullOrBlank() && detected != feed.category) {
                rssDao.updateFeedCategory(feed.url, detected)
                updatedCount++
            }
        }
        updatedCount
    }

    suspend fun detectCategoryForFeed(feedUrl: String, feedTitle: String, feedDescription: String = ""): String = withContext(Dispatchers.IO) {
        val allArticlesList = rssDao.getAllArticlesList().filter { it.feedUrl.equals(feedUrl, ignoreCase = true) }
        FeedCategoryAutoTagger.detectCategory(
            context = context,
            feedUrl = feedUrl,
            feedTitle = feedTitle,
            feedDescription = feedDescription,
            articleTitles = allArticlesList.map { it.title }
        )
    }

    suspend fun updateFeedCategory(feedUrl: String, newCategory: String) {
        withContext(Dispatchers.IO) {
            val cleanUrl = feedUrl.trim()
            val altUrl = if (cleanUrl.endsWith("/")) cleanUrl.dropLast(1) else "$cleanUrl/"
            val formatted = newCategory.uppercase().trim().ifBlank { "UNCATEGORIZED" }

            val existing = rssDao.getFeedByUrl(cleanUrl) ?: rssDao.getFeedByUrl(altUrl)
            if (existing != null) {
                rssDao.updateFeedCategory(existing.url, formatted)
                rssDao.updateArticlesCategoryByFeedUrl(existing.url, formatted, formatted == "PODCASTS")
                rssDao.cleanupMistaggedPodcastArticles()
                FeedCategoryAutoTagger.recordUserTagging(context, existing.url, existing.title, formatted)
            } else {
                val catalogItem = DefaultFeedCatalog.curatedFeeds.find {
                    it.url.equals(cleanUrl, ignoreCase = true) || it.url.equals(altUrl, ignoreCase = true)
                }
                val newFeed = FeedEntity(
                    url = cleanUrl,
                    title = catalogItem?.title ?: "Feed",
                    category = formatted,
                    description = catalogItem?.description ?: "",
                    isPreferred = false,
                    isEnabled = true,
                    isCustom = false
                )
                rssDao.insertFeed(newFeed)
                FeedCategoryAutoTagger.recordUserTagging(context, cleanUrl, catalogItem?.title ?: "Feed", formatted)
            }
        }
    }

    suspend fun updateFeedDetails(
        oldFeed: FeedEntity,
        newTitle: String,
        newUrl: String,
        newCategory: String,
        newIsPreferred: Boolean = oldFeed.isPreferred,
        newPreferredScope: String = oldFeed.preferredScope
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = newUrl.trim()
        val formattedUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else {
            cleanUrl
        }
        val formattedCategory = newCategory.uppercase().trim().ifBlank { "UNCATEGORIZED" }
        val formattedTitle = newTitle.trim().ifBlank { oldFeed.title }

        if (oldFeed.url == formattedUrl) {
            rssDao.updateFeedDetailsWithScope(oldFeed.url, formattedTitle, formattedCategory, newIsPreferred, newPreferredScope)
            FeedCategoryAutoTagger.recordUserTagging(context, oldFeed.url, formattedTitle, formattedCategory)
            true
        } else {
            // URL changed: remove old feed, insert new feed entity, migrate articles
            rssDao.deleteFeedByUrl(oldFeed.url)
            val updatedFeed = oldFeed.copy(
                url = formattedUrl,
                title = formattedTitle,
                category = formattedCategory,
                isPreferred = newIsPreferred,
                preferredScope = newPreferredScope,
                lastUpdated = System.currentTimeMillis()
            )
            rssDao.insertFeed(updatedFeed)
            rssDao.updateArticlesFeedUrl(oldFeed.url, formattedUrl)
            FeedCategoryAutoTagger.recordUserTagging(context, formattedUrl, formattedTitle, formattedCategory)

            // Try fetching from new URL
            try {
                val request = Request.Builder()
                    .url(formattedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful && response.body != null) {
                    val parsed = RssXmlParser.parse(
                        inputStream = response.body!!.byteStream(),
                        feedUrl = formattedUrl,
                        defaultCategory = formattedCategory,
                        defaultFeedTitle = formattedTitle,
                        isPreferredSource = oldFeed.isPreferred
                    )
                    if (parsed.articles.isNotEmpty()) {
                        val processed = DealDetector.processArticles(parsed.articles)
                        rssDao.insertArticles(processed)
                    }
                }
            } catch (e: Exception) {
                Log.e("RssRepository", "Error fetching updated feed URL $formattedUrl: ${e.localizedMessage}")
            }
            true
        }
    }

    suspend fun toggleFeedEnabled(feedUrl: String, currentEnabled: Boolean) {
        withContext(Dispatchers.IO) {
            val cleanUrl = feedUrl.trim()
            val altUrl = if (cleanUrl.endsWith("/")) cleanUrl.dropLast(1) else "$cleanUrl/"
            val existing = rssDao.getFeedByUrl(cleanUrl) ?: rssDao.getFeedByUrl(altUrl)

            if (existing != null) {
                rssDao.updateFeedEnabled(existing.url, !currentEnabled)
            } else {
                val catalogItem = DefaultFeedCatalog.curatedFeeds.find {
                    it.url.equals(cleanUrl, ignoreCase = true) || it.url.equals(altUrl, ignoreCase = true)
                }
                val newFeed = FeedEntity(
                    url = cleanUrl,
                    title = catalogItem?.title ?: "Feed",
                    category = catalogItem?.category ?: "GENERAL",
                    description = catalogItem?.description ?: "",
                    isPreferred = false,
                    isEnabled = !currentEnabled,
                    isCustom = false
                )
                rssDao.insertFeed(newFeed)
            }
        }
    }

    suspend fun addCustomFeed(url: String, title: String, category: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val formattedUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else {
            cleanUrl
        }

        var userCategoryInput = category.uppercase().trim()
        var feedTitle = title.ifBlank { "Custom Feed" }
        var feedDescription = "Custom RSS Feed"
        var parsedArticles = emptyList<ArticleEntity>()

        try {
            val request = Request.Builder()
                .url(formattedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val parsed = RssXmlParser.parse(
                    inputStream = response.body!!.byteStream(),
                    feedUrl = formattedUrl,
                    defaultCategory = userCategoryInput.ifBlank { "CUSTOM" },
                    defaultFeedTitle = feedTitle,
                    isPreferredSource = true
                )
                if (parsed.title.isNotBlank() && parsed.title != "Feed") {
                    feedTitle = parsed.title
                }
                if (parsed.description.isNotBlank()) {
                    feedDescription = parsed.description
                }
                parsedArticles = parsed.articles
            }
        } catch (e: Exception) {
            Log.e("RssRepository", "Network or XML parse warning when adding feed $formattedUrl: ${e.localizedMessage}")
        }

        // Auto-detect category if user left blank or chose AUTO or GENERAL
        val finalCategory = if (userCategoryInput.isBlank() || userCategoryInput == "AUTO" || userCategoryInput == "CUSTOM" || userCategoryInput == "GENERAL") {
            FeedCategoryAutoTagger.detectCategory(
                context = context,
                feedUrl = formattedUrl,
                feedTitle = feedTitle,
                feedDescription = feedDescription,
                articleTitles = parsedArticles.map { it.title }
            )
        } else {
            userCategoryInput
        }

        FeedCategoryAutoTagger.recordUserTagging(context, formattedUrl, feedTitle, finalCategory)

        val newFeed = FeedEntity(
            url = formattedUrl,
            title = feedTitle,
            category = finalCategory,
            description = feedDescription,
            isPreferred = true,
            isEnabled = true,
            isCustom = true
        )

        rssDao.insertFeed(newFeed)

        if (parsedArticles.isNotEmpty()) {
            val processed = DealDetector.processArticles(parsedArticles)
            rssDao.insertArticles(processed)
        }

        true
    }

    suspend fun deleteFeed(feedUrl: String) {
        withContext(Dispatchers.IO) {
            val cleanUrl = feedUrl.trim()
            val altUrl = if (cleanUrl.endsWith("/")) cleanUrl.dropLast(1) else "$cleanUrl/"
            rssDao.deleteFeedByUrl(cleanUrl)
            rssDao.deleteFeedByUrl(altUrl)
        }
    }

    suspend fun getArticleById(id: String): ArticleEntity? {
        return withContext(Dispatchers.IO) {
            rssDao.getArticleById(id)?.let { com.example.util.HtmlSanitizer.sanitizeArticle(it) }
        }
    }

    suspend fun getArticleByLink(link: String): ArticleEntity? {
        return withContext(Dispatchers.IO) {
            rssDao.getArticleByLink(link)?.let { com.example.util.HtmlSanitizer.sanitizeArticle(it) }
        }
    }

    suspend fun getArticleCount(): Int {
        return withContext(Dispatchers.IO) {
            rssDao.getArticleCount()
        }
    }

    suspend fun exportOpml(): String = withContext(Dispatchers.IO) {
        val feeds = rssDao.getAllFeedsList()
        com.example.util.OpmlManager.exportToOpml(feeds)
    }

    suspend fun exportFeedsToJson(): String = withContext(Dispatchers.IO) {
        val feeds = rssDao.getAllFeedsList()
        com.example.util.JsonMigrationManager.exportFeedsToJson(context, feeds)
    }

    suspend fun importFeedsFromJson(
        jsonString: String,
        removeFeedsNotInJson: Boolean = true
    ): com.example.util.JsonImportResult = withContext(Dispatchers.IO) {
        val (feeds, result) = com.example.util.JsonMigrationManager.parseJsonBackup(context, jsonString)
        
        fun normalizeUrl(u: String): String {
            return u.trim().lowercase().removeSuffix("/")
        }

        var removedCount = 0
        if (removeFeedsNotInJson && feeds.isNotEmpty()) {
            val incomingUrls = feeds.map { normalizeUrl(it.url) }.toSet()
            val existingFeeds = rssDao.getAllFeedsList()
            for (existing in existingFeeds) {
                val existingNorm = normalizeUrl(existing.url)
                if (existingNorm !in incomingUrls) {
                    rssDao.deleteFeedByUrl(existing.url)
                    val altUrl = if (existing.url.endsWith("/")) existing.url.dropLast(1) else "${existing.url}/"
                    rssDao.deleteFeedByUrl(altUrl)
                    rssDao.deleteNonSavedArticlesByFeedUrl(existing.url)
                    rssDao.deleteNonSavedArticlesByFeedUrl(altUrl)
                    removedCount++
                }
            }
        }

        var newCount = 0
        for (feed in feeds) {
            val existing = rssDao.getFeedByUrl(feed.url)
                ?: rssDao.getFeedByUrl(if (feed.url.endsWith("/")) feed.url.dropLast(1) else "${feed.url}/")
            if (existing == null) {
                rssDao.insertFeed(feed)
                newCount++
            } else {
                // Update existing feed details from JSON
                val updated = existing.copy(
                    title = if (feed.title.isNotBlank() && feed.title != "Feed") feed.title else existing.title,
                    category = if (feed.category.isNotBlank() && feed.category != "GENERAL" && feed.category != "UNCATEGORIZED") feed.category else existing.category,
                    description = if (feed.description.isNotBlank()) feed.description else existing.description,
                    iconUrl = if (feed.iconUrl.isNotBlank()) feed.iconUrl else existing.iconUrl,
                    isPreferred = feed.isPreferred,
                    isEnabled = feed.isEnabled,
                    isCustom = feed.isCustom
                )
                rssDao.insertFeed(updated)
            }
        }
        result.copy(importedFeedsCount = newCount, removedFeedsCount = removedCount)
    }

    suspend fun importOpmlXml(xmlContent: String): Int = withContext(Dispatchers.IO) {
        val items = com.example.util.OpmlManager.parseOpml(xmlContent)
        var importedCount = 0
        for (item in items) {
            val cleanUrl = item.xmlUrl.trim()
            if (cleanUrl.isBlank()) continue
            val formattedUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                "https://$cleanUrl"
            } else {
                cleanUrl
            }
            val existing = rssDao.getFeedByUrl(formattedUrl)
            if (existing == null) {
                val feedEntity = FeedEntity(
                    url = formattedUrl,
                    title = item.title.ifBlank { "Feed" },
                    category = item.category.uppercase().ifBlank { "UNCATEGORIZED" },
                    isPreferred = false,
                    isEnabled = true,
                    isCustom = true
                )
                rssDao.insertFeed(feedEntity)
                importedCount++
            }
        }
        importedCount
    }

    suspend fun getAllFeedsList(): List<FeedEntity> = withContext(Dispatchers.IO) {
        rssDao.getAllFeedsList()
    }

    suspend fun updateFeedIconUrl(feedUrl: String, iconUrl: String) = withContext(Dispatchers.IO) {
        rssDao.updateFeedIconUrl(feedUrl, iconUrl)
    }

    suspend fun updateArticleImageUrl(articleId: String, imageUrl: String?) = withContext(Dispatchers.IO) {
        rssDao.updateArticleImageUrl(articleId, imageUrl)
    }

    suspend fun searchArtworkCandidates(query: String): List<com.example.util.PodcastArtworkCandidate> = withContext(Dispatchers.IO) {
        com.example.util.PodcastMetadataGrabber.searchArtworkCandidates(query)
    }

    suspend fun autoGrabPodcastArtwork(feedTitle: String, episodeTitle: String = "", feedUrl: String = ""): String? = withContext(Dispatchers.IO) {
        com.example.util.PodcastMetadataGrabber.autoGrabBestThumbnail(feedTitle, episodeTitle, feedUrl)
    }
}
