package com.example.util

import android.util.Log
import com.example.data.local.ArticleEntity
import com.example.data.local.FeedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class PodcastArtworkCandidate(
    val imageUrl: String,
    val title: String,
    val author: String = "",
    val source: String = "iTunes"
)

object PodcastMetadataGrabber {

    private const val TAG = "PodcastMetaGrabber"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * Search on-device for podcast artwork candidates using iTunes Search API & Web HTML Fallback
     */
    suspend fun searchArtworkCandidates(query: String): List<PodcastArtworkCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<PodcastArtworkCandidate>()

        // 1. Try iTunes Search API
        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val apiUrl = "https://itunes.apple.com/search?media=podcast&limit=6&term=$encodedQuery"
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val resultArray = json.optJSONArray("results")
                if (resultArray != null) {
                    for (i in 0 until resultArray.length()) {
                        val item = resultArray.getJSONObject(i)
                        val artwork600 = item.optString("artworkUrl600", "")
                        val artwork100 = item.optString("artworkUrl100", "")
                        val artworkUrl = artwork600.ifBlank { artwork100 }
                        val collectionName = item.optString("collectionName", item.optString("trackName", query))
                        val artistName = item.optString("artistName", "")

                        if (artworkUrl.isNotBlank() && results.none { it.imageUrl == artworkUrl }) {
                            results.add(
                                PodcastArtworkCandidate(
                                    imageUrl = artworkUrl,
                                    title = collectionName,
                                    author = artistName,
                                    source = "iTunes"
                                )
                            )
                        }
                    }
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "iTunes search failed for '$query': ${e.message}")
        }

        // 2. If query looks like a URL (e.g., feedUrl or website link), try web scraping og:image
        if (query.startsWith("http://") || query.startsWith("https://")) {
            val webImage = extractOgImageFromUrl(query)
            if (!webImage.isNullOrBlank() && results.none { it.imageUrl == webImage }) {
                results.add(
                    PodcastArtworkCandidate(
                        imageUrl = webImage,
                        title = "Web Page Cover",
                        author = "",
                        source = "Web Metadata"
                    )
                )
            }
        }

        return@withContext results
    }

    /**
     * Grabs a single best thumbnail URL automatically for a podcast or feed
     */
    suspend fun autoGrabBestThumbnail(feedTitle: String, episodeTitle: String = "", feedUrl: String = ""): String? = withContext(Dispatchers.IO) {
        // Try episode + feed title
        val combinedQuery = if (episodeTitle.isNotBlank() && feedTitle.isNotBlank()) "$feedTitle $episodeTitle" else feedTitle.ifBlank { episodeTitle }
        val candidates = searchArtworkCandidates(combinedQuery)
        if (candidates.isNotEmpty()) {
            return@withContext candidates.first().imageUrl
        }

        // Fallback to feedTitle alone
        if (feedTitle.isNotBlank() && feedTitle != combinedQuery) {
            val feedCandidates = searchArtworkCandidates(feedTitle)
            if (feedCandidates.isNotEmpty()) {
                return@withContext feedCandidates.first().imageUrl
            }
        }

        // Fallback to URL og:image
        if (feedUrl.isNotBlank()) {
            val webImage = extractOgImageFromUrl(feedUrl)
            if (!webImage.isNullOrBlank()) {
                return@withContext webImage
            }
        }

        return@withContext null
    }

    /**
     * Extract og:image, twitter:image, or apple-touch-icon from web HTML
     */
    private fun extractOgImageFromUrl(pageUrl: String): String? {
        return try {
            val url = URL(pageUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode == 200) {
                val html = connection.inputStream.bufferedReader().use { reader ->
                    // Read first 100KB which covers head section
                    val charArray = CharArray(100000)
                    val read = reader.read(charArray, 0, 100000)
                    if (read > 0) String(charArray, 0, read) else ""
                }
                connection.disconnect()

                // Regex search for og:image, twitter:image, or apple-touch-icon
                val ogPattern = Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                var matcher = ogPattern.matcher(html)
                if (matcher.find()) return matcher.group(1)

                val ogPattern2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE)
                matcher = ogPattern2.matcher(html)
                if (matcher.find()) return matcher.group(1)

                val twitterPattern = Pattern.compile("<meta[^>]+name=[\"']twitter:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                matcher = twitterPattern.matcher(html)
                if (matcher.find()) return matcher.group(1)

                val iconPattern = Pattern.compile("<link[^>]+rel=[\"'](apple-touch-icon|icon)[\"'][^>]+href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                matcher = iconPattern.matcher(html)
                if (matcher.find()) return matcher.group(2)
            } else {
                connection.disconnect()
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed og:image extract from $pageUrl: ${e.message}")
            null
        }
    }
}
