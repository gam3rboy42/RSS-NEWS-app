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
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
    private const val DEFAULT_PRUNE_AGE_DAYS = 30
    private const val DEFAULT_AUTO_DOWNLOAD_WIFI = true

    val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(object : CookieJar {
                private val cookieStore = HashMap<String, List<Cookie>>()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .build()
    }

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
        if (!file.exists() || file.length() < 30 * 1024) return false
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durStr?.toLongOrNull() ?: 0L
            if (durationMs > 0L) return true
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever could not parse duration for ${file.name}: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        // Secondary validation fallback: inspect header magic bytes to verify legitimate binary audio
        return try {
            val bytes = ByteArray(512)
            val readCount = file.inputStream().use { it.read(bytes) }
            if (readCount < 10) return false

            val headerStr = String(bytes, 0, minOf(readCount, 256), Charsets.ISO_8859_1).lowercase()
            // Reject HTML error responses disguised as audio files
            if (headerStr.contains("<html") || headerStr.contains("<!doctype") || headerStr.contains("<head") || headerStr.contains("{\"error\"")) {
                Log.e(TAG, "File ${file.name} is an HTML error response, not audio")
                return false
            }

            // Check known audio header signatures
            val isId3 = bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
            val isMp3Sync = (bytes[0].toInt() and 0xFF) == 0xFF && ((bytes[1].toInt() and 0xE0) == 0xE0)
            val isM4a = headerStr.contains("ftyp")
            val isOgg = bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() && bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
            val isWav = bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
            val isFlac = bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() && bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()

            isId3 || isMp3Sync || isM4a || isOgg || isWav || isFlac || file.length() >= 64 * 1024
        } catch (e: Exception) {
            Log.e(TAG, "Header check failed: ${e.message}")
            file.length() >= 64 * 1024
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
        val initialMediaUrl = article.mediaUrl ?: article.link
        if (initialMediaUrl.isBlank()) return@withContext null

        val targetFile = File(getCacheDir(context), "${hashId(article.id)}.mp3")
        if (targetFile.exists() && isAudioFileValid(targetFile)) {
            return@withContext targetFile
        } else if (targetFile.exists()) {
            targetFile.delete()
        }

        val tempFile = File(getCacheDir(context), "${hashId(article.id)}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

        try {
            val request = Request.Builder()
                .url(initialMediaUrl)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "audio/*, */*;q=0.9")
                .header("Accept-Encoding", "identity")
                .header("Connection", "keep-alive")
                .build()

            val response = downloadClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                Log.e(TAG, "Download failed with HTTP code ${response.code} for ${article.title}")
                response.close()
                return@withContext null
            }

            val body = response.body!!
            val fileLength = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile)

            val data = ByteArray(16384)
            var totalBytesRead = 0L
            var readCount: Int

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(data).also { readCount = it } != -1) {
                        totalBytesRead += readCount
                        output.write(data, 0, readCount)
                        if (fileLength > 0) {
                            onProgress((totalBytesRead.toFloat() / fileLength.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }
            response.close()

            if (tempFile.exists() && isAudioFileValid(tempFile)) {
                if (targetFile.exists()) targetFile.delete()
                if (tempFile.renameTo(targetFile)) {
                    Log.d(TAG, "Successfully downloaded and validated: ${targetFile.name} (${targetFile.length()} bytes)")
                    return@withContext targetFile
                } else {
                    return@withContext tempFile
                }
            } else {
                Log.e(TAG, "Downloaded file failed audio validation for ${article.title}. Temp length=${tempFile.length()}")
                if (tempFile.exists()) tempFile.delete()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during podcast download for ${article.title}: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            return@withContext null
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
            val fileAge = now - f.lastModified()
            val pubAge = if (article != null && article.pubDateTimestamp > 0) now - article.pubDateTimestamp else 0L

            var shouldDelete = false

            // Rule 1: Auto-prune listened/read podcasts unless bookmarked for offline
            if (autoPruneListened && isListened && !isBookmarked) {
                shouldDelete = true
            }

            // Rule 2: Prune files older than age threshold (file download age or episode publication age) unless bookmarked
            if (pruneAgeMs > 0 && !isBookmarked && (fileAge > pruneAgeMs || pubAge > pruneAgeMs)) {
                shouldDelete = true
            }

            if (shouldDelete) {
                val len = f.length()
                if (f.delete()) {
                    prunedCount++
                    reclaimedBytes += len
                    if (article != null) {
                        try { rssDao.updateOfflineStatus(article.id, false) } catch (_: Exception) {}
                    }
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
                    if (article != null) {
                        try { rssDao.updateOfflineStatus(article.id, false) } catch (_: Exception) {}
                    }
                }
            }
        }

        Log.d(TAG, "Smart Prune complete: Removed $prunedCount files, reclaimed ${reclaimedBytes / (1024 * 1024)} MB")
        PruneResult(prunedCount, reclaimedBytes)
    }

    suspend fun cleanEpisodesOlderThan30Days(context: Context, rssDao: RssDao): PruneResult = withContext(Dispatchers.IO) {
        val originalPruneAge = getPruneAgeDays(context)
        setPruneAgeDays(context, 30)
        val result = autoPruneIfNecessary(context, rssDao)
        setPruneAgeDays(context, originalPruneAge)
        result
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
