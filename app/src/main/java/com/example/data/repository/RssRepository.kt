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

    suspend fun initializeDefaultsIfNeeded() {
        withContext(Dispatchers.IO) {
            val defaultFeeds = DefaultFeedCatalog.toDefaultEntities()
            rssDao.insertDefaultFeeds(defaultFeeds)
        }
    }

    fun isDeviceOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            if (capabilities != null) {
                return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return false
    }

    suspend fun refreshFeeds(): SyncState = withContext(Dispatchers.IO) {
        if (!isDeviceOnline()) {
            return@withContext SyncState.Error("NO INTERNET CONNECTION — OFFLINE READING MODE ACTIVE")
        }

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

            SyncState.Success(totalArticlesFetched)
        } catch (e: Exception) {
            SyncState.Error(e.localizedMessage ?: "Failed to update feeds")
        }
    }

    fun getClusteredFeedStream(
        categoryFilter: String,
        timeRangeFilter: com.example.data.model.TimeRangeFilter,
        hideDeals: Boolean,
        onlyPreferredFeeds: Boolean,
        onlyBookmarks: Boolean,
        randomSeed: Long
    ): Flow<List<StoryCluster>> {
        val baseArticlesFlow = if (onlyBookmarks) bookmarkedArticles else rawArticles

        return combine(baseArticlesFlow, feeds) { articles, allFeeds ->
            val preferredUrlMap = allFeeds.associate { it.url to it.isPreferred }
            val feedCategoryMap = allFeeds.associate { it.url to it.category }
            val enabledUrlSet = allFeeds.filter { it.isEnabled }.map { it.url }.toSet()

            // Filter & update preferred source flags and categories
            val filteredArticles = articles.map { article ->
                val isPref = preferredUrlMap[article.feedUrl] ?: article.isPreferredSource
                val feedCat = feedCategoryMap[article.feedUrl] ?: article.category
                var updated = article
                if (article.isPreferredSource != isPref) {
                    updated = updated.copy(isPreferredSource = isPref)
                }
                if (!feedCat.isNullOrBlank() && updated.category != feedCat) {
                    updated = updated.copy(category = feedCat)
                }
                updated
            }.filter { article ->
                // Must belong to an enabled feed
                val isFeedEnabled = enabledUrlSet.contains(article.feedUrl)
                // Category filter
                val matchesCategory = categoryFilter == "ALL" || article.category.equals(categoryFilter, ignoreCase = true)
                // Hide deals filter
                val matchesDeals = !hideDeals || !article.isDeal
                // Preferred filter
                val matchesPreferred = !onlyPreferredFeeds || article.isPreferredSource

                isFeedEnabled && matchesCategory && matchesDeals && matchesPreferred
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

    suspend fun markArticleRead(articleId: String) {
        withContext(Dispatchers.IO) {
            rssDao.markArticleAsRead(articleId)
        }
    }

    suspend fun toggleFeedPreferred(feedUrl: String, currentPreferred: Boolean) {
        withContext(Dispatchers.IO) {
            rssDao.updateFeedPreferred(feedUrl, !currentPreferred)
        }
    }

    suspend fun updateFeedCategory(feedUrl: String, newCategory: String) {
        withContext(Dispatchers.IO) {
            val formatted = newCategory.uppercase().trim().ifBlank { "UNCATEGORIZED" }
            rssDao.updateFeedCategory(feedUrl, formatted)
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
            rssDao.updateFeedEnabled(feedUrl, !currentEnabled)
        }
    }

    suspend fun addCustomFeed(url: String, title: String, category: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val formattedUrl = if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            "https://$cleanUrl"
        } else {
            cleanUrl
        }

        val formattedCategory = category.uppercase().trim().ifBlank { "CUSTOM" }
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
                    defaultCategory = formattedCategory,
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

        val newFeed = FeedEntity(
            url = formattedUrl,
            title = feedTitle,
            category = formattedCategory,
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
            rssDao.deleteFeedByUrl(feedUrl)
        }
    }

    suspend fun getArticleById(id: String): ArticleEntity? {
        return withContext(Dispatchers.IO) {
            rssDao.getArticleById(id)
        }
    }
}
