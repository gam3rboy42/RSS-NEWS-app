package com.example.data.model

import android.content.Context
import com.example.data.local.ArticleEntity
import com.example.data.local.FeedEntity
import java.util.Locale

object FeedCategoryAutoTagger {

    private const val PREFS_NAME = "user_feed_category_rules"
    private const val KEY_EXACT_URL_PREFIX = "exact_url_"
    private const val KEY_LEARNED_KEYWORDS_PREFIX = "learned_kw_"

    // Standard Category Heuristic Keywords
    private val defaultRules = mapOf(
        "TECH" to listOf(
            "tech", "technology", "gadget", "apple", "google", "microsoft", "hardware", "software",
            "android", "ios", "verge", "wired", "cnet", "techcrunch", "engadget", "ars technica",
            "gizmodo", "semiconductor", "cyber", "mobile", "app", "apps", "code", "developer"
        ),
        "FINANCE" to listOf(
            "finance", "money", "invest", "stock", "stocks", "market", "bloomberg", "wsj",
            "wall street", "reuters finance", "crypto", "bitcoin", "ethereum", "economy", "trade",
            "forbes", "business", "bank", "banking", "earnings", "dividend", "sec", "treasury"
        ),
        "SCIENCE" to listOf(
            "science", "nasa", "space", "nature", "physics", "biology", "astronomy", "climate",
            "environment", "health", "medical", "scientific", "cosmos", "quantum", "ocean",
            "genetics", "telescope", "spacex", "mars"
        ),
        "GAMING" to listOf(
            "game", "gaming", "ign", "kotaku", "polygon", "eurogamer", "nintendo", "playstation",
            "xbox", "steam", "esports", "pc gamer", "destructoid", "gamespot", "ps5", "switch",
            "console", "gamedev", "streamer"
        ),
        "DESIGN" to listOf(
            "design", "ui", "ux", "art", "creative", "behance", "dribbble", "architecture",
            "figma", "typography", "dezeen", "wallpaper", "interface", "graphic", "designer"
        ),
        "AI" to listOf(
            "ai", "artificial intelligence", "machine learning", "deepmind", "openai", "claude",
            "llm", "gpt", "robotics", "neural", "huggingface", "generative ai", "gemini", "llama"
        ),
        "WORLD" to listOf(
            "world", "news", "bbc", "cnn", "al jazeera", "reuters", "ap news", "politics",
            "global", "guardian", "npr", "times", "diplomacy", "government", "election"
        ),
        "PODCASTS" to listOf(
            "podcast", "podcasts", "audio", "show", "episode", "fm", "spotify", "broadcast",
            "radio", "listen"
        ),
        "LIFESTYLE" to listOf(
            "lifestyle", "travel", "food", "health", "fitness", "fashion", "culture", "style",
            "wellness", "cooking", "recipe", "home"
        )
    )

    /**
     * Records a user's manual tagging choice to learn user preferences for future auto-tagging.
     */
    fun recordUserTagging(context: Context, feedUrl: String, feedTitle: String, category: String) {
        if (feedUrl.isBlank() || category.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val formattedCategory = category.uppercase().trim()
        val cleanUrl = feedUrl.trim().lowercase()

        val editor = prefs.edit()
        editor.putString(KEY_EXACT_URL_PREFIX + cleanUrl, formattedCategory)

        // Learn keywords from feed title (words >= 4 chars)
        val titleWords = feedTitle.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length >= 4 && !stopWords.contains(it) }

        for (word in titleWords) {
            editor.putString(KEY_LEARNED_KEYWORDS_PREFIX + word, formattedCategory)
        }
        editor.apply()
    }

    /**
     * Retrieves all exact URL tag rules and learned keyword tag rules for export.
     */
    fun getUserTagRules(context: Context): Pair<Map<String, String>, Map<String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        val exactRules = mutableMapOf<String, String>()
        val keywordRules = mutableMapOf<String, String>()

        for ((key, value) in allEntries) {
            val strValue = value as? String ?: continue
            if (key.startsWith(KEY_EXACT_URL_PREFIX)) {
                val url = key.removePrefix(KEY_EXACT_URL_PREFIX)
                exactRules[url] = strValue
            } else if (key.startsWith(KEY_LEARNED_KEYWORDS_PREFIX)) {
                val kw = key.removePrefix(KEY_LEARNED_KEYWORDS_PREFIX)
                keywordRules[kw] = strValue
            }
        }
        return Pair(exactRules, keywordRules)
    }

    /**
     * Imports exact URL tag rules and learned keyword rules.
     */
    fun importUserTagRules(
        context: Context,
        exactUrlRules: Map<String, String>,
        learnedKeywordRules: Map<String, String>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for ((url, cat) in exactUrlRules) {
            editor.putString(KEY_EXACT_URL_PREFIX + url.trim().lowercase(), cat.uppercase().trim())
        }
        for ((kw, cat) in learnedKeywordRules) {
            editor.putString(KEY_LEARNED_KEYWORDS_PREFIX + kw.trim().lowercase(), cat.uppercase().trim())
        }
        editor.apply()
    }

    /**
     * Detects category on-device using user tagging rules + keyword heuristic analysis.
     */
    fun detectCategory(
        context: Context,
        feedUrl: String,
        feedTitle: String,
        feedDescription: String = "",
        articleTitles: List<String> = emptyList()
    ): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanUrl = feedUrl.trim().lowercase()

        // 1. Check exact user URL override
        val exactRule = prefs.getString(KEY_EXACT_URL_PREFIX + cleanUrl, null)
        if (!exactRule.isNullOrBlank()) {
            return exactRule
        }

        // 2. Aggregate text to analyze
        val fullText = "$feedTitle $feedUrl $feedDescription ${articleTitles.joinToString(" ")}".lowercase(Locale.ROOT)

        // Check user-learned keyword rules
        val wordsInText = fullText.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 4 }
        val learnedScores = mutableMapOf<String, Int>()
        for (word in wordsInText) {
            val learnedCat = prefs.getString(KEY_LEARNED_KEYWORDS_PREFIX + word, null)
            if (!learnedCat.isNullOrBlank()) {
                learnedScores[learnedCat] = (learnedScores[learnedCat] ?: 0) + 3
            }
        }

        // If user learned rule matched strongly, use it
        val highestLearned = learnedScores.maxByOrNull { it.value }
        if (highestLearned != null && highestLearned.value >= 3) {
            return highestLearned.key
        }

        // 3. Heuristic keyword matching
        val defaultScores = mutableMapOf<String, Int>()
        for ((category, keywords) in defaultRules) {
            var score = 0
            for (kw in keywords) {
                if (kw.length <= 3) {
                    // Exact word boundary match for short keywords like "ai", "ios", "ui", "ux"
                    val regex = Regex("\\b${Regex.escape(kw)}\\b")
                    if (regex.containsMatchIn(fullText)) {
                        score += 2
                    }
                } else if (fullText.contains(kw)) {
                    score += 1
                }
            }
            if (score > 0) {
                defaultScores[category] = score
            }
        }

        // Include user learned score boosts
        for ((cat, sc) in learnedScores) {
            defaultScores[cat] = (defaultScores[cat] ?: 0) + sc
        }

        val bestCategory = defaultScores.maxByOrNull { it.value }
        return if (bestCategory != null && bestCategory.value >= 1) {
            bestCategory.key
        } else {
            "GENERAL"
        }
    }

    /**
     * Auto-tags all feeds in the list on-device.
     */
    fun autoTagAllFeeds(
        context: Context,
        feeds: List<FeedEntity>,
        articles: List<ArticleEntity>
    ): Map<String, String> {
        val articlesByFeed = articles.groupBy { it.feedUrl.trim().lowercase() }
        val results = mutableMapOf<String, String>()

        for (feed in feeds) {
            val cleanUrl = feed.url.trim().lowercase()
            val sampleTitles = articlesByFeed[cleanUrl]?.take(10)?.map { it.title } ?: emptyList()

            val detected = detectCategory(
                context = context,
                feedUrl = feed.url,
                feedTitle = feed.title,
                feedDescription = feed.description,
                articleTitles = sampleTitles
            )
            results[feed.url] = detected
        }
        return results
    }

    private val stopWords = setOf(
        "the", "and", "news", "feed", "rss", "blog", "daily", "official", "com", "org", "net",
        "post", "times", "journal", "world", "today", "latest", "updates", "tech"
    )
}
