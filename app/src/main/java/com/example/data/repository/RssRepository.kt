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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.data.model.FeedCategoryAutoTagger
import kotlinx.coroutines.flow.first
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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val feeds: Flow<List<FeedEntity>> = rssDao.getAllFeeds()
    val rawArticles: Flow<List<ArticleEntity>> = rssDao.getAllArticles()
    val bookmarkedArticles: Flow<List<ArticleEntity>> = rssDao.getBookmarkedArticles()
    val offlineArticles: Flow<List<ArticleEntity>> = rssDao.getOfflineArticles()
    val readArticles: Flow<List<ArticleEntity>> = rssDao.getReadArticles()

    private val stopWords = setOf(
        "a", "an", "the", "in", "on", "at", "to", "for", "of", "with", "by", "from",
        "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
        "it", "its", "this", "that", "these", "those", "how", "what", "why", "who",
        "new", "says", "report", "first", "after", "over", "about", "more", "will",
        "has", "have", "had", "can", "could", "should", "would", "may", "might"
    )

    suspend fun initializeDefaultsIfNeeded() {
        withContext(Dispatchers.IO) {
            val existingCount = rssDao.getAllFeedsList().size
            if (existingCount == 0) {
                val defaultFeeds = DefaultFeedCatalog.toDefaultEntities()
                rssDao.insertDefaultFeeds(defaultFeeds)
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

    suspend fun refreshFeeds(): SyncState = withContext(Dispatchers.IO) {
        // Ensure default feeds are inserted if DB is completely empty
        initializeDefaultsIfNeeded()

        try {
            val enabledFeeds = rssDao.getEnabledFeeds()
            if (enabledFeeds.isEmpty()) {
                return@withContext SyncState.Success(0)
            }

            var totalArticlesFetched = 0
            val newArticles = mutableListOf<ArticleEntity>()

            for (feed in enabledFeeds) {
                try {
                    val request = Request.Builder()
                        .url(feed.url)
                        .header("User-Agent", "Mozilla/5.0 (Android; Nothing RSS News Reader)")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        val parsed = RssXmlParser.parse(
                            inputStream = response.body!!.byteStream(),
                            feedUrl = feed.url,
                            defaultCategory = feed.category,
                            defaultFeedTitle = feed.title,
                            isPreferredSource = feed.isPreferred
                        )

                        val processed = DealDetector.processArticles(parsed.articles)
                        newArticles.addAll(processed)
                        totalArticlesFetched += processed.size
                    }
                } catch (e: Exception) {
                    Log.e("RssRepository", "Error fetching feed ${feed.url}: ${e.localizedMessage}")
                }
            }

            if (newArticles.isNotEmpty()) {
                rssDao.insertArticles(newArticles)
            }

            // Generate topic recommendations from liked stories if online
            generateDiscoveredRecommendationsInternal()

            SyncState.Success(totalArticlesFetched)
        } catch (e: Exception) {
            SyncState.Error(e.localizedMessage ?: "Failed to update feeds")
        }
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
            }.take(5)

            if (candidateFeeds.isEmpty()) return

            val recommendedArticles = mutableListOf<ArticleEntity>()

            for (candidate in candidateFeeds) {
                try {
                    val request = Request.Builder()
                        .url(candidate.url)
                        .header("User-Agent", "Mozilla/5.0 (Android; Nothing RSS News Reader)")
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        val parsed = RssXmlParser.parse(
                            inputStream = response.body!!.byteStream(),
                            feedUrl = candidate.url,
                            defaultCategory = candidate.category,
                            defaultFeedTitle = candidate.title,
                            isPreferredSource = false
                        )

                        // Filter candidate articles that match liked authors or keywords
                        val matching = parsed.articles.filter { article ->
                            val authorMatch = likedAuthors.isNotEmpty() && article.author.isNotBlank() &&
                                    likedAuthors.any { la -> article.author.lowercase().contains(la) }
                            val artText = (article.title + " " + article.description).lowercase()
                            val keywordMatch = likedKeywords.any { kw -> artText.contains(kw) }
                            authorMatch || keywordMatch
                        }.take(3).map { it.copy(isDiscoveredRecommendation = true) }

                        recommendedArticles.addAll(matching)
                    }
                } catch (e: Exception) {
                    Log.e("RssRepository", "Recommendation fetch warning for ${candidate.url}: ${e.localizedMessage}")
                }
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
            else -> rawArticles
        }

        return combine(baseArticlesFlow, feeds) { articles, allFeeds ->
            fun normalizeUrl(url: String) = url.trim().trimEnd('/').lowercase()

            val preferredUrlMap = allFeeds.associate { normalizeUrl(it.url) to it.isPreferred }
            val feedCategoryMap = allFeeds.associate { normalizeUrl(it.url) to it.category }
            val enabledUrlSet = allFeeds.filter { it.isEnabled }.map { normalizeUrl(it.url) }.toSet()

            // Filter & update preferred source flags and categories
            val filteredArticles = articles.map { article ->
                val normUrl = normalizeUrl(article.feedUrl)
                val isPref = preferredUrlMap[normUrl] ?: article.isPreferredSource
                val feedCat = feedCategoryMap[normUrl] ?: article.category
                var updated = article
                if (article.isPreferredSource != isPref) {
                    updated = updated.copy(isPreferredSource = isPref)
                }
                if (!feedCat.isNullOrBlank() && updated.category != feedCat) {
                    updated = updated.copy(category = feedCat)
                }
                updated
            }.filter { article ->
                val normUrl = normalizeUrl(article.feedUrl)
                // Must belong to an enabled feed, or allFeeds is empty, OR be a discovered recommendation
                val isFeedEnabledOrRecommendation = allFeeds.isEmpty() || enabledUrlSet.contains(normUrl) || article.isDiscoveredRecommendation
                // Category filter
                val matchesCategory = categoryFilter == "ALL" || article.category.equals(categoryFilter, ignoreCase = true)
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

    suspend fun toggleBookmark(articleId: String, currentStatus: Boolean) {
        withContext(Dispatchers.IO) {
            rssDao.updateBookmarkStatus(articleId, !currentStatus)
        }
    }

    suspend fun toggleOfflineStatus(articleId: String, currentSaved: Boolean) {
        withContext(Dispatchers.IO) {
            rssDao.updateOfflineStatus(articleId, !currentSaved)
        }
    }

    suspend fun toggleLikedStatus(articleId: String, currentLiked: Boolean) {
        withContext(Dispatchers.IO) {
            rssDao.updateLikedStatus(articleId, !currentLiked)
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

    suspend fun toggleFeedPreferred(feedUrl: String, currentPreferred: Boolean) {
        withContext(Dispatchers.IO) {
            val cleanUrl = feedUrl.trim()
            val altUrl = if (cleanUrl.endsWith("/")) cleanUrl.dropLast(1) else "$cleanUrl/"
            val existing = rssDao.getFeedByUrl(cleanUrl) ?: rssDao.getFeedByUrl(altUrl)

            if (existing != null) {
                rssDao.updateFeedPreferred(existing.url, !currentPreferred)
            } else {
                val catalogItem = DefaultFeedCatalog.curatedFeeds.find {
                    it.url.equals(cleanUrl, ignoreCase = true) || it.url.equals(altUrl, ignoreCase = true)
                }
                val newFeed = FeedEntity(
                    url = cleanUrl,
                    title = catalogItem?.title ?: "Feed",
                    category = catalogItem?.category ?: "GENERAL",
                    description = catalogItem?.description ?: "",
                    isPreferred = !currentPreferred,
                    isEnabled = true,
                    isCustom = false
                )
                rssDao.insertFeed(newFeed)
            }
        }
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
        newCategory: String
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
            rssDao.updateFeedDetails(oldFeed.url, formattedTitle, formattedCategory)
            FeedCategoryAutoTagger.recordUserTagging(context, oldFeed.url, formattedTitle, formattedCategory)
            true
        } else {
            // URL changed: remove old feed, insert new feed entity, migrate articles
            rssDao.deleteFeedByUrl(oldFeed.url)
            val updatedFeed = oldFeed.copy(
                url = formattedUrl,
                title = formattedTitle,
                category = formattedCategory,
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
            rssDao.getArticleById(id)
        }
    }

    suspend fun getArticleByLink(link: String): ArticleEntity? {
        return withContext(Dispatchers.IO) {
            rssDao.getArticleByLink(link)
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

    suspend fun importFeedsFromJson(jsonString: String): com.example.util.JsonImportResult = withContext(Dispatchers.IO) {
        val (feeds, result) = com.example.util.JsonMigrationManager.parseJsonBackup(context, jsonString)
        var newCount = 0
        for (feed in feeds) {
            val existing = rssDao.getFeedByUrl(feed.url)
            if (existing == null) {
                rssDao.insertFeed(feed)
                newCount++
            } else {
                // Update existing feed category/tags if imported feed has custom tags
                if (existing.category.isBlank() || existing.category == "GENERAL" || existing.category == "UNCATEGORIZED") {
                    val updated = existing.copy(
                        category = feed.category,
                        isPreferred = existing.isPreferred || feed.isPreferred
                    )
                    rssDao.insertFeed(updated)
                }
            }
        }
        result.copy(importedFeedsCount = newCount)
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
}
