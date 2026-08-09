package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.parser.RssXmlParser
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RssBackgroundWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
        .followRedirects(true)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(appContext)
            val rssDao = db.rssDao()

            val enabledFeeds = rssDao.getEnabledFeeds()
            if (enabledFeeds.isEmpty()) return@withContext Result.success()

            val likedArticles = rssDao.getLikedArticles()
            val likedAuthors = likedArticles
                .map { it.author.trim() }
                .filter { authorName -> authorName.isNotBlank() && authorName.length >= 3 }
                .map { authorName -> authorName.lowercase() }
                .toSet()

            var topSuggestedArticle: ArticleEntity? = null
            var suggestionReason: String? = null

            // Fetch background feeds concurrently with max 4 parallel connections
            val semaphore = Semaphore(4)
            val parsedResults = coroutineScope {
                enabledFeeds.map { feed ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val request = Request.Builder().url(feed.url).build()
                                val response = httpClient.newCall(request).execute()
                                response.use { resp ->
                                    if (resp.isSuccessful && resp.body != null) {
                                        val parsedFeed = RssXmlParser.parse(
                                            inputStream = resp.body!!.byteStream(),
                                            feedUrl = feed.url,
                                            defaultCategory = feed.category,
                                            defaultFeedTitle = feed.title,
                                            isPreferredSource = feed.isPreferred
                                        )
                                        Pair(feed, parsedFeed.articles)
                                    } else null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            for ((feed, articles) in parsedResults) {
                for (article in articles) {
                    val exists = rssDao.getArticleById(article.id) != null
                    if (!exists) {
                        rssDao.insertArticles(listOf(article))

                        val isPodcastItem = article.isPodcast || article.isVideoPodcast ||
                                article.mediaType == "AUDIO" || article.mediaType == "VIDEO" ||
                                article.category.equals("PODCASTS", ignoreCase = true)

                        if (isPodcastItem && (feed.isPreferred || article.isPreferredSource)) {
                            var isAutoDownloaded = false
                            if (com.example.util.PodcastCacheManager.getAutoDownloadWifi(appContext) &&
                                com.example.util.PodcastCacheManager.isWifiConnected(appContext)) {
                                val downloadedFile = com.example.util.PodcastCacheManager.downloadPodcastAudio(appContext, article)
                                if (downloadedFile != null) {
                                    rssDao.updateOfflineStatus(article.id, true)
                                    com.example.util.PodcastCacheManager.autoPruneIfNecessary(appContext, rssDao)
                                    isAutoDownloaded = true
                                }
                            }

                            val reason = if (isAutoDownloaded) {
                                "New episode auto-downloaded on Wi-Fi: ${feed.title.ifBlank { article.feedTitle }}"
                            } else {
                                "New episode on preferred podcast: ${feed.title.ifBlank { article.feedTitle }}"
                            }

                            NotificationHelper.showPodcastNotification(
                                context = appContext,
                                article = article,
                                reasonText = reason
                            )
                        } else if (!isPodcastItem) {
                            val authorName = article.author.lowercase()
                            val isLikedAuthor = article.author.isNotBlank() && likedAuthors.any { la -> authorName.contains(la) }

                            if (isLikedAuthor) {
                                topSuggestedArticle = article
                                suggestionReason = "New story by author you liked: ${article.author}"
                            } else if (feed.isPreferred && topSuggestedArticle == null) {
                                topSuggestedArticle = article
                                suggestionReason = "New update from preferred feed: ${feed.title}"
                            } else if (topSuggestedArticle == null) {
                                topSuggestedArticle = article
                                suggestionReason = "Fresh story from ${feed.title}"
                            }
                        }
                    }
                }
            }

            // Post notification if a suggested article was found
            topSuggestedArticle?.let { article ->
                NotificationHelper.showArticleNotification(
                    context = appContext,
                    article = article,
                    reasonText = suggestionReason
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
