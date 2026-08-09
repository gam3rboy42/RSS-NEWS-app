package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object NotificationHelper {

    private const val CHANNEL_ID = "rss_article_notifications"
    private const val CHANNEL_NAME = "RSS Article Suggestions"
    private const val CHANNEL_DESC = "Periodic notifications for recommended and fresh articles"

    private const val PODCAST_CHANNEL_ID = "podcast_episode_notifications"
    private const val PODCAST_CHANNEL_NAME = "Preferred Podcast Episode Alerts"
    private const val PODCAST_CHANNEL_DESC = "Notifications when a new episode of a preferred podcast is released"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val newsChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
            }

            val podcastChannel = NotificationChannel(
                PODCAST_CHANNEL_ID,
                PODCAST_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = PODCAST_CHANNEL_DESC
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(newsChannel)
            notificationManager.createNotificationChannel(podcastChannel)
        }
    }

    suspend fun showArticleNotification(
        context: Context,
        article: ArticleEntity,
        reasonText: String? = null
    ) {
        createNotificationChannel(context)

        // Fetch image bitmap if present
        val imageBitmap: Bitmap? = if (!article.imageUrl.isNull_or_blank()) {
            downloadBitmap(article.imageUrl!!)
        } else null

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_ARTICLE_ID", article.id)
            putExtra("EXTRA_ARTICLE_URL", article.link)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            article.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Subtitle logic showing feed title + author if present
        val sourceInfo = buildString {
            append(article.feedTitle.ifBlank { "RSS Feed" })
            if (article.author.isNotBlank()) {
                append(" • By ").append(article.author)
            }
        }

        val contentTitle = if (article.isLiked || article.isPreferredSource) {
            "★ LIKED SOURCE: ${article.title}"
        } else {
            article.title
        }

        val contentText = reasonText ?: sourceInfo

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(sourceInfo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // If image is present (especially for liked sources or rich articles), attach picture
        if (imageBitmap != null) {
            builder.setLargeIcon(imageBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(contentText)
            )
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(article.id.hashCode(), builder.build())
    }

    suspend fun showPodcastNotification(
        context: Context,
        article: ArticleEntity,
        reasonText: String? = null
    ) {
        createNotificationChannel(context)

        val imageBitmap: Bitmap? = if (!article.imageUrl.isNull_or_blank()) {
            downloadBitmap(article.imageUrl!!)
        } else null

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_ARTICLE_ID", article.id)
            putExtra("EXTRA_ARTICLE_URL", article.link)
            putExtra("EXTRA_IS_PODCAST", true)
        }

        val notifId = article.id.hashCode() + 500000

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sourceInfo = buildString {
            append(article.feedTitle.ifBlank { "Preferred Podcast" })
            if (article.author.isNotBlank()) {
                append(" • ").append(article.author)
            }
        }

        val contentTitle = "🎧 NEW PODCAST: ${article.title}"
        val contentText = reasonText ?: "New episode released from ${article.feedTitle.ifBlank { "preferred podcast" }}"

        val builder = NotificationCompat.Builder(context, PODCAST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSubText(sourceInfo)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (imageBitmap != null) {
            builder.setLargeIcon(imageBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(imageBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(contentText)
            )
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(notifId, builder.build())
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
}
