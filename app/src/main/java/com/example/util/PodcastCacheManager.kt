package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.data.local.ArticleEntity
import com.example.data.local.RssDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class PodcastStorageStats(
    val totalBytes: Long,
    val totalFiles: Int,
    val listenedFilesCount: Int,
    val listenedBytes: Long,
    val maxStorageMb: Int,
    val autoPruneListened: Boolean,
    val pruneAgeDays: Int
) {
    val totalMb: Float get() = totalBytes / (1024f * 1024f)
    val listenedMb: Float get() = listenedBytes / (1024f * 1024f)
    val usagePercentage: Float get() = if (maxStorageMb <= 0) 0f else ((totalMb / maxStorageMb) * 100f).coerceIn(0f, 100f)
}

data class PruneResult(
    val filesPruned: Int,
    val bytesReclaimed: Long
)

object PodcastCacheManager {

    private const val TAG = "PodcastCacheManager"
    private const val PREFS_NAME = "podcast_cache_prefs"
    private const val KEY_MAX_STORAGE_MB = "max_storage_mb"
    private const val KEY_AUTO_PRUNE_LISTENED = "auto_prune_listened"
    private const val KEY_PRUNE_AGE_DAYS = "prune_age_days"
    private const val KEY_AUTO_DOWNLOAD_WIFI = "auto_download_wifi"

    private const val DEFAULT_MAX_STORAGE_MB = 250
    private const val DEFAULT_AUTO_PRUNE_LISTENED = true
    private const val DEFAULT_PRUNE_AGE_DAYS = 14
    private const val DEFAULT_AUTO_DOWNLOAD_WIFI = true

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "podcast_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun hashId(id: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(id.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            id.replace("[^a-zA-Z0-9]".toRegex(), "_")
        }
    }

    fun isAudioFileValid(file: File): Boolean {
        if (!file.exists() || file.length() < 50 * 1024) return false
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durStr?.toLongOrNull() ?: 0L
            durationMs > 0L
        } catch (e: Exception) {
            Log.e(TAG, "Audio validation check failed for ${file.name}: ${e.message}")
            false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    fun getCachedFile(context: Context, articleId: String): File? {
        val fileName = "${hashId(articleId)}.mp3"
        val file = File(getCacheDir(context), fileName)
        return if (file.exists() && isAudioFileValid(file)) file else null
    }

    fun isArticleCached(context: Context, articleId: String): Boolean {
        return getCachedFile(context, articleId) != null
    }

    fun getMaxStorageMb(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MAX_STORAGE_MB, DEFAULT_MAX_STORAGE_MB)
    }

    fun setMaxStorageMb(context: Context, mb: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_MAX_STORAGE_MB, mb).apply()
    }

    fun getAutoPruneListened(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_PRUNE_LISTENED, DEFAULT_AUTO_PRUNE_LISTENED)
    }

    fun setAutoPruneListened(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_PRUNE_LISTENED, enabled).apply()
    }

    fun getPruneAgeDays(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_PRUNE_AGE_DAYS, DEFAULT_PRUNE_AGE_DAYS)
    }

    fun setPruneAgeDays(context: Context, days: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_PRUNE_AGE_DAYS, days).apply()
    }

    fun getAutoDownloadWifi(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_DOWNLOAD_WIFI, DEFAULT_AUTO_DOWNLOAD_WIFI)
    }

    fun setAutoDownloadWifi(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_WIFI, enabled).apply()
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val netInfo = cm.activeNetworkInfo
            @Suppress("DEPRECATION")
            return netInfo != null && netInfo.isConnected && netInfo.type == ConnectivityManager.TYPE_WIFI
        }
    }

    suspend fun autoDownloadIfWifi(
        context: Context,
        article: ArticleEntity,
        rssDao: RssDao
    ): File? = withContext(Dispatchers.IO) {
        if (!getAutoDownloadWifi(context)) return@withContext null
        if (!isWifiConnected(context)) return@withContext null

        val isPod = article.isPodcast || article.isVideoPodcast ||
                article.mediaType == "AUDIO" || article.mediaType == "VIDEO" ||
                article.category.equals("PODCASTS", ignoreCase = true)

        if (!isPod) return@withContext null
        if (isArticleCached(context, article.id)) return@withContext null

        val downloadedFile = downloadPodcastAudio(context, article)
        if (downloadedFile != null) {
            rssDao.updateOfflineStatus(article.id, true)
            autoPruneIfNecessary(context, rssDao)
            Log.d(TAG, "Auto-downloaded preferred podcast on Wi-Fi: ${article.title}")
        }
        downloadedFile
    }

    suspend fun getStorageStats(context: Context, rssDao: RssDao): PodcastStorageStats = withContext(Dispatchers.IO) {
        val dir = getCacheDir(context)
        val files = dir.listFiles() ?: emptyArray()

        var totalBytes = 0L
        var listenedBytes = 0L
        var listenedCount = 0

        val articles = try { rssDao.getAllArticlesList() } catch (e: Exception) { emptyList() }
        val articleMap = articles.associateBy { hashId(it.id) }

        for (f in files) {
            val length = f.length()
            totalBytes += length

            val hashKey = f.name.removeSuffix(".mp3")
            val article = articleMap[hashKey]
            if (article != null && (article.isRead || article.isArchived)) {
                listenedCount++
                listenedBytes += length
            }
        }

        PodcastStorageStats(
            totalBytes = totalBytes,
            totalFiles = files.size,
            listenedFilesCount = listenedCount,
            listenedBytes = listenedBytes,
            maxStorageMb = getMaxStorageMb(context),
            autoPruneListened = getAutoPruneListened(context),
            pruneAgeDays = getPruneAgeDays(context)
        )
    }

    suspend fun downloadPodcastAudio(
        context: Context,
        article: ArticleEntity,
        onProgress: (Float) -> Unit = {}
    ): File? = withContext(Dispatchers.IO) {
        val mediaUrl = article.mediaUrl ?: article.link
        if (mediaUrl.isBlank()) return@withContext null

        val targetFile = File(getCacheDir(context), "${hashId(article.id)}.mp3")
        if (targetFile.exists() && isAudioFileValid(targetFile)) {
            return@withContext targetFile
        } else if (targetFile.exists()) {
            targetFile.delete()
        }

        val tempFile = File(getCacheDir(context), "${hashId(article.id)}.tmp")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(mediaUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/120.0")
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Download failed with code: ${connection.responseCode}")
                return@withContext null
            }

            val fileLength = connection.contentLengthLong
            val input: InputStream = connection.inputStream
            val output = FileOutputStream(tempFile)

            val data = ByteArray(8192)
            var total: Long = 0
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    onProgress(total.toFloat() / fileLength.toFloat())
                }
                output.write(data, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            if (tempFile.renameTo(targetFile) || targetFile.exists()) {
                if (isAudioFileValid(targetFile)) {
                    Log.d(TAG, "Podcast audio successfully cached & validated: ${targetFile.absolutePath} (${targetFile.length()} bytes)")
                    return@withContext targetFile
                } else {
                    Log.e(TAG, "Downloaded podcast failed audio validation check! Deleting invalid/corrupted file.")
                    if (targetFile.exists()) targetFile.delete()
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext null
                }
            } else {
                if (isAudioFileValid(tempFile)) {
                    return@withContext tempFile
                } else {
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading podcast audio: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun autoPruneIfNecessary(context: Context, rssDao: RssDao): PruneResult = withContext(Dispatchers.IO) {
        val dir = getCacheDir(context)
        val files = dir.listFiles() ?: return@withContext PruneResult(0, 0L)

        val autoPruneListened = getAutoPruneListened(context)
        val maxStorageBytes = getMaxStorageMb(context) * 1024L * 1024L
        val pruneAgeMs = getPruneAgeDays(context) * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()

        val articles = try { rssDao.getAllArticlesList() } catch (e: Exception) { emptyList() }
        val articleMap = articles.associateBy { hashId(it.id) }

        var prunedCount = 0
        var reclaimedBytes = 0L

        val remainingFiles = mutableListOf<File>()

        for (f in files) {
            val hashKey = f.name.removeSuffix(".mp3")
            val article = articleMap[hashKey]
            val isBookmarked = article?.isBookmarked == true || article?.isSavedForOffline == true
            val isListened = article?.isRead == true
            val age = now - f.lastModified()

            var shouldDelete = false

            // Rule 1: Auto-prune listened/read podcasts unless bookmarked for offline
            if (autoPruneListened && isListened && !isBookmarked) {
                shouldDelete = true
            }

            // Rule 2: Prune files older than age threshold unless bookmarked
            if (pruneAgeMs > 0 && age > pruneAgeMs && !isBookmarked) {
                shouldDelete = true
            }

            if (shouldDelete) {
                val len = f.length()
                if (f.delete()) {
                    prunedCount++
                    reclaimedBytes += len
                }
            } else {
                remainingFiles.add(f)
            }
        }

        // Rule 3: Storage Limit LRU Pruning if total size still exceeds maxStorageBytes
        var currentTotalBytes = remainingFiles.sumOf { it.length() }
        if (currentTotalBytes > maxStorageBytes) {
            // Sort remaining files by lastModified ascending (oldest first)
            val sortedByOldest = remainingFiles.sortedBy { it.lastModified() }
            for (f in sortedByOldest) {
                if (currentTotalBytes <= maxStorageBytes) break
                val hashKey = f.name.removeSuffix(".mp3")
                val article = articleMap[hashKey]
                // Don't prune bookmarked unless forced
                if (article?.isBookmarked == true) continue

                val len = f.length()
                if (f.delete()) {
                    prunedCount++
                    reclaimedBytes += len
                    currentTotalBytes -= len
                }
            }
        }

        Log.d(TAG, "Smart Prune complete: Removed $prunedCount files, reclaimed ${reclaimedBytes / (1024 * 1024)} MB")
        PruneResult(prunedCount, reclaimedBytes)
    }

    suspend fun clearAllCache(context: Context): PruneResult = withContext(Dispatchers.IO) {
        val dir = getCacheDir(context)
        val files = dir.listFiles() ?: return@withContext PruneResult(0, 0L)

        var count = 0
        var bytes = 0L

        for (f in files) {
            val len = f.length()
            if (f.delete()) {
                count++
                bytes += len
            }
        }

        PruneResult(count, bytes)
    }
}
