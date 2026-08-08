package com.example.data.model

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object RssTopicSearchManager {

    // Predefined major news publishers and their RSS news feed endpoints
    private val knownNewsPublishers = listOf(
        FeedDiscoveryItem("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "WORLD", "📰 Major News Publisher: Global breaking news and top headlines from BBC News."),
        FeedDiscoveryItem("CNN Top Stories", "https://rss.cnn.com/rss/edition.rss", "WORLD", "📰 Major News Publisher: Breaking global news and politics from CNN."),
        FeedDiscoveryItem("Reuters Agency News", "https://www.reutersagency.com/feed/", "WORLD", "📰 Major News Publisher: Unbiased global journalism and financial reporting from Reuters."),
        FeedDiscoveryItem("The Guardian World", "https://www.theguardian.com/world/rss", "WORLD", "📰 Major News Publisher: International news, analysis, and opinion from The Guardian."),
        FeedDiscoveryItem("New York Times World", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml", "WORLD", "📰 Major News Publisher: World news stories and investigative reports from NYT."),
        FeedDiscoveryItem("TechCrunch", "https://techcrunch.com/feed/", "TECH", "📰 Tech News Publisher: Technology, startups, venture capital, and AI developments."),
        FeedDiscoveryItem("The Verge", "https://www.theverge.com/rss/index.xml", "TECH", "📰 Tech News Publisher: Technology news, gadget reviews, science, and digital culture."),
        FeedDiscoveryItem("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "TECH", "📰 Tech News Publisher: Deep technical analysis, IT news, hardware, and tech policy."),
        FeedDiscoveryItem("Wired", "https://www.wired.com/feed/rss", "TECH", "📰 Tech News Publisher: Future tech trends, cybersecurity, AI, and digital culture."),
        FeedDiscoveryItem("Forbes Business News", "https://www.forbes.com/real-time/feed2/", "BUSINESS", "📰 Financial News Publisher: Business insights, finance, investing, and market trends."),
        FeedDiscoveryItem("CoinDesk Crypto News", "https://www.coindesk.com/arc/outboundfeeds/rss/", "BUSINESS", "📰 Crypto News Publisher: Blockchain, Bitcoin, Ethereum, and digital finance updates."),
        FeedDiscoveryItem("IGN News", "https://feeds.feedburner.com/ign/all", "GAMING", "📰 Gaming News Publisher: Video game reviews, breaking gaming news, and trailer releases."),
        FeedDiscoveryItem("Polygon", "https://www.polygon.com/rss/index.xml", "GAMING", "📰 Gaming News Publisher: Gaming culture, hardware reviews, and entertainment news."),
        FeedDiscoveryItem("Space.com", "https://www.space.com/feeds/all", "SCIENCE", "📰 Science News Publisher: Astronomy, space exploration missions, and rocket launches."),
        FeedDiscoveryItem("NPR News", "https://feeds.npr.org/1001/rss.xml", "WORLD", "📰 Major News Publisher: National and international news coverage from NPR.")
    )

    suspend fun searchFeedsForTopic(topic: String): List<FeedDiscoveryItem> = withContext(Dispatchers.IO) {
        if (topic.isBlank()) return@withContext emptyList()
        val cleanedTopic = topic.trim()
        val results = mutableListOf<FeedDiscoveryItem>()

        // 1. Check local catalog for direct keyword matches
        val catalogMatches = DefaultFeedCatalog.curatedFeeds.filter { item ->
            item.title.contains(cleanedTopic, ignoreCase = true) ||
            item.description.contains(cleanedTopic, ignoreCase = true) ||
            item.category.contains(cleanedTopic, ignoreCase = true)
        }
        results.addAll(catalogMatches)

        // 2. Check known major news publisher feeds for keyword/brand matches
        val publisherMatches = knownNewsPublishers.filter { item ->
            item.title.contains(cleanedTopic, ignoreCase = true) ||
            item.description.contains(cleanedTopic, ignoreCase = true) ||
            item.category.contains(cleanedTopic, ignoreCase = true)
        }
        results.addAll(publisherMatches)

        // 3. Add Google News, Bing News & Yahoo News RSS Feeds for live topic news coverage
        try {
            val encodedQuery = URLEncoder.encode(cleanedTopic, "UTF-8")

            // Google News Search RSS
            results.add(
                FeedDiscoveryItem(
                    title = "Google News: $cleanedTopic",
                    url = "https://news.google.com/rss/search?q=$encodedQuery&hl=en-US&gl=US&ceid=US:en",
                    category = deriveCategoryFromTopic(cleanedTopic),
                    description = "🌐 Global News Aggregator: Live topic news feed for '$cleanedTopic' from global publishers."
                )
            )

            // Bing News Search RSS
            results.add(
                FeedDiscoveryItem(
                    title = "Bing News: $cleanedTopic",
                    url = "https://www.bing.com/news/search?q=$encodedQuery&format=rss",
                    category = deriveCategoryFromTopic(cleanedTopic),
                    description = "🌐 Global News Aggregator: Real-time news stories and media updates for '$cleanedTopic'."
                )
            )

            // Yahoo News Search RSS
            results.add(
                FeedDiscoveryItem(
                    title = "Yahoo News: $cleanedTopic",
                    url = "https://news.search.yahoo.com/rss?p=$encodedQuery",
                    category = deriveCategoryFromTopic(cleanedTopic),
                    description = "🌐 Global News Aggregator: Comprehensive news coverage and headlines for '$cleanedTopic'."
                )
            )
        } catch (e: Exception) {
            // Ignore encoding errors
        }

        // 4. Check if topic is a domain/URL to probe direct RSS news feed endpoints
        if (cleanedTopic.contains(".") || cleanedTopic.startsWith("http")) {
            val probedFeeds = probeWebsiteRssFeeds(cleanedTopic)
            results.addAll(probedFeeds)
        }

        // 5. Add Reddit Topic / News Community RSS Feeds
        if (!cleanedTopic.contains(" ")) {
            val subreddit = cleanedTopic.lowercase(Locale.ROOT).replace("r/", "").replace("/", "")
            results.add(
                FeedDiscoveryItem(
                    title = "Reddit: r/$subreddit News",
                    url = "https://www.reddit.com/r/$subreddit/.rss",
                    category = deriveCategoryFromTopic(cleanedTopic),
                    description = "💬 Community News: Latest posts, articles, and discussions from r/$subreddit."
                )
            )
        }

        // 6. Query iTunes Podcast API for real podcast news/shows for this topic
        try {
            val podcastResults = queryItunesPodcasts(cleanedTopic)
            results.addAll(podcastResults)
        } catch (e: Exception) {
            // Ignore podcast API errors
        }

        // 7. Query Gemini AI for specialized topic RSS news recommendations if API key exists
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val aiFeeds = queryGeminiForRssFeeds(cleanedTopic, apiKey)
                results.addAll(aiFeeds)
            } catch (e: Exception) {
                // Fallback gracefully
            }
        }

        // Deduplicate results by URL
        results.distinctBy { it.url.trim().lowercase(Locale.ROOT) }
    }

    private fun probeWebsiteRssFeeds(inputUrlOrDomain: String): List<FeedDiscoveryItem> {
        val list = mutableListOf<FeedDiscoveryItem>()
        var baseDomain = inputUrlOrDomain.trim()
        if (!baseDomain.startsWith("http://") && !baseDomain.startsWith("https://")) {
            baseDomain = "https://$baseDomain"
        }
        baseDomain = baseDomain.trimEnd('/')

        val candidatePaths = listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/news/feed")
        for (path in candidatePaths) {
            val targetUrl = "$baseDomain$path"
            try {
                val url = URL(targetUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android RSS Reader)")

                if (conn.responseCode == 200) {
                    val contentType = conn.contentType ?: ""
                    val stream = conn.inputStream
                    val headerBuffer = ByteArray(512)
                    val readBytes = stream.read(headerBuffer)
                    val sample = if (readBytes > 0) String(headerBuffer, 0, readBytes) else ""
                    conn.disconnect()

                    if (contentType.contains("xml") || contentType.contains("rss") || contentType.contains("atom") ||
                        sample.contains("<rss") || sample.contains("<feed") || sample.contains("<?xml")) {

                        val siteName = baseDomain.replace("https://", "").replace("http://", "").replace("www.", "")
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                        list.add(
                            FeedDiscoveryItem(
                                title = "$siteName News Feed",
                                url = targetUrl,
                                category = deriveCategoryFromTopic(siteName),
                                description = "📰 Direct Website RSS News Feed auto-detected at $targetUrl."
                            )
                        )
                        break // Found working endpoint for domain
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                // Skip non-responsive endpoint
            }
        }
        return list
    }

    private fun deriveCategoryFromTopic(topic: String): String {
        val lower = topic.lowercase(Locale.ROOT)
        return when {
            lower.contains("game") || lower.contains("gaming") || lower.contains("playstation") || lower.contains("xbox") || lower.contains("nintendo") -> "GAMING"
            lower.contains("ai") || lower.contains("model") || lower.contains("gpt") || lower.contains("robot") -> "AI"
            lower.contains("tech") || lower.contains("phone") || lower.contains("code") || lower.contains("app") -> "TECH"
            lower.contains("space") || lower.contains("science") || lower.contains("physics") || lower.contains("health") -> "SCIENCE"
            lower.contains("design") || lower.contains("art") || lower.contains("ui") || lower.contains("ux") -> "DESIGN"
            lower.contains("stock") || lower.contains("crypto") || lower.contains("finance") || lower.contains("market") -> "BUSINESS"
            else -> "WORLD"
        }
    }

    private fun queryGeminiForRssFeeds(topic: String, apiKey: String): List<FeedDiscoveryItem> {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val prompt = "Return 4 real, active, valid RSS news feed URLs for the news topic or publisher '$topic'. Output strictly valid JSON array with objects containing keys 'title', 'url', 'description', 'category'. Do not wrap in backticks or Markdown."

        val jsonRequest = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
        }

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.doOutput = true

        conn.outputStream.use { os ->
            os.write(jsonRequest.toString().toByteArray(Charsets.UTF_8))
        }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(responseText)
            val candidates = jsonResponse.optJSONArray("candidates")
            val text = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text") ?: ""

            return parseAiRssJson(text, topic)
        }
        return emptyList()
    }

    private fun queryItunesPodcasts(topic: String): List<FeedDiscoveryItem> {
        val encodedTopic = URLEncoder.encode(topic, "UTF-8")
        val apiUrl = "https://itunes.apple.com/search?term=$encodedTopic&entity=podcast&limit=10"
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val list = mutableListOf<FeedDiscoveryItem>()
        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val resultsArray = json.optJSONArray("results") ?: return emptyList()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val feedUrl = item.optString("feedUrl")
                if (feedUrl.isNullOrBlank()) continue

                val title = item.optString("collectionName").ifBlank { item.optString("trackName") }
                val artist = item.optString("artistName")
                val genre = item.optString("primaryGenreName")
                val isVideo = title.lowercase(Locale.ROOT).contains("video") || feedUrl.lowercase(Locale.ROOT).contains("video") || feedUrl.lowercase(Locale.ROOT).contains(".mp4")

                val badge = if (isVideo) "🎥 Video Podcast" else "🎧 Audio Podcast"
                val desc = "$badge by $artist ($genre). Direct RSS feed."

                list.add(
                    FeedDiscoveryItem(
                        title = title,
                        url = feedUrl,
                        category = "PODCASTS",
                        description = desc,
                        isPodcast = true,
                        isVideoPodcast = isVideo
                    )
                )
            }
        }
        return list
    }

    private fun parseAiRssJson(aiOutput: String, topic: String): List<FeedDiscoveryItem> {
        val cleaned = aiOutput.replace("```json", "").replace("```", "").trim()
        val list = mutableListOf<FeedDiscoveryItem>()
        try {
            val jsonArray = JSONArray(cleaned)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title").ifBlank { "$topic Feed" }
                val feedUrl = obj.optString("url")
                val desc = obj.optString("description").ifBlank { "RSS news coverage for $topic." }
                val cat = obj.optString("category").ifBlank { deriveCategoryFromTopic(topic) }

                if (feedUrl.startsWith("http://") || feedUrl.startsWith("https://")) {
                    list.add(
                        FeedDiscoveryItem(
                            title = title,
                            url = feedUrl,
                            category = cat.uppercase(Locale.ROOT),
                            description = desc
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Failed parsing AI JSON, return empty list
        }
        return list
    }
}
