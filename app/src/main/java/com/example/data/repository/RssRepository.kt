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
            val count = rssDao.getArticleCount()
            // If feeds table is empty or first run, seed default feeds
            val defaultFeeds = DefaultFeedCatalog.toDefaultEntities()
            rssDao.insertFeeds(defaultFeeds)
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
        hideDeals: Boolean,
        onlyPreferredFeeds: Boolean,
        onlyBookmarks: Boolean
    ): Flow<List<StoryCluster>> {
        val baseArticlesFlow = if (onlyBookmarks) bookmarkedArticles else rawArticles

        return combine(baseArticlesFlow, feeds) { articles, allFeeds ->
            val preferredUrlMap = allFeeds.associate { it.url to it.isPreferred }

            // Filter & update preferred source flags
            val filteredArticles = articles.map { article ->
                val isPref = preferredUrlMap[article.feedUrl] ?: article.isPreferredSource
                if (article.isPreferredSource != isPref) {
                    article.copy(isPreferredSource = isPref)
                } else {
                    article
                }
            }.filter { article ->
                // Category filter
                val matchesCategory = categoryFilter == "ALL" || article.category.equals(categoryFilter, ignoreCase = true)
                // Hide deals filter
                val matchesDeals = !hideDeals || !article.isDeal
                // Preferred filter
                val matchesPreferred = !onlyPreferredFeeds || article.isPreferredSource

                matchesCategory && matchesDeals && matchesPreferred
            }

            // Cluster identical stories
            StoryClusterer.clusterArticles(filteredArticles)
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

    suspend fun toggleFeedEnabled(feedUrl: String, currentEnabled: Boolean) {
        withContext(Dispatchers.IO) {
            rssDao.updateFeedEnabled(feedUrl, !currentEnabled)
        }
    }

    suspend fun addCustomFeed(url: String, title: String, category: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Nothing RSS News Reader)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val parsed = RssXmlParser.parse(
                    inputStream = response.body!!.byteStream(),
                    feedUrl = url,
                    defaultCategory = category,
                    defaultFeedTitle = title,
                    isPreferredSource = true
                )

                val newFeed = FeedEntity(
                    url = url,
                    title = parsed.title.ifBlank { title },
                    category = category,
                    description = parsed.description.ifBlank { "Custom RSS Feed" },
                    isPreferred = true,
                    isEnabled = true,
                    isCustom = true
                )

                rssDao.insertFeed(newFeed)

                if (parsed.articles.isNotEmpty()) {
                    val processed = DealDetector.processArticles(parsed.articles)
                    rssDao.insertArticles(processed)
                }

                true
            } else false
        } catch (e: Exception) {
            Log.e("RssRepository", "Failed adding custom feed: ${e.localizedMessage}")
            false
        }
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
