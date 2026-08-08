package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.parser.RssXmlParser
import com.example.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RssBackgroundWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
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

            for (feed in enabledFeeds) {
                try {
                    val request = Request.Builder().url(feed.url).build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body
                        if (body != null) {
                            val parsedFeed = RssXmlParser.parse(
                                inputStream = body.byteStream(),
                                feedUrl = feed.url,
                                defaultCategory = feed.category,
                                defaultFeedTitle = feed.title,
                                isPreferredSource = feed.isPreferred
                            )

                            for (article in parsedFeed.articles) {
                                val exists = rssDao.getArticleById(article.id) != null
                                if (!exists) {
                                    rssDao.insertArticles(listOf(article))

                                    val authorName = article.author.lowercase()
                                    val isLikedAuthor = article.author.isNotBlank() && likedAuthors.any { la -> authorName.contains(la) }

                                    // Priority 1: From liked author
                                    if (isLikedAuthor) {
                                        topSuggestedArticle = article
                                        suggestionReason = "New story by author you liked: ${article.author}"
                                    }
                                    // Priority 2: From preferred source
                                    else if (feed.isPreferred && topSuggestedArticle == null) {
                                        topSuggestedArticle = article
                                        suggestionReason = "New update from preferred feed: ${feed.title}"
                                    }
                                    // Priority 3: General fresh article
                                    else if (topSuggestedArticle == null) {
                                        topSuggestedArticle = article
                                        suggestionReason = "Fresh story from ${feed.title}"
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Continue to next feed
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
