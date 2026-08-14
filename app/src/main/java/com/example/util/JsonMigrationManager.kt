package com.example.util

import android.content.Context
import com.example.data.local.FeedEntity
import com.example.data.model.FeedCategoryAutoTagger
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class JsonImportResult(
    val importedFeedsCount: Int,
    val totalFeedsInFile: Int,
    val importedTagsCount: Int,
    val removedFeedsCount: Int = 0
)

object JsonMigrationManager {

    /**
     * Generates a clean, formatted JSON string containing all user feeds, assigned tags/categories,
     * and custom user category rules for offline device-to-device migration.
     */
    fun exportFeedsToJson(context: Context, feeds: List<FeedEntity>): String {
        val root = JSONObject()
        root.put("app", "Nothing RSS")
        root.put("version", 1)
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        root.put("exportedAt", dateFormat.format(Date()))
        root.put("totalFeeds", feeds.size)

        // Extract distinct tags
        val allTags = feeds.map { it.category.ifBlank { "GENERAL" }.uppercase() }.distinct().sorted()
        val tagsArray = JSONArray()
        allTags.forEach { tagsArray.put(it) }
        root.put("tags", tagsArray)

        // Add Feeds Array
        val feedsArray = JSONArray()
        for (feed in feeds) {
            val feedObj = JSONObject()
            feedObj.put("url", feed.url)
            feedObj.put("title", feed.title)
            val category = feed.category.ifBlank { "GENERAL" }.uppercase()
            feedObj.put("category", category)
            
            // Include tags list array for forward compatibility
            val feedTagsArray = JSONArray()
            feedTagsArray.put(category)
            feedObj.put("tags", feedTagsArray)

            feedObj.put("description", feed.description)
            feedObj.put("iconUrl", feed.iconUrl)
            feedObj.put("isPreferred", feed.isPreferred)
            feedObj.put("isEnabled", feed.isEnabled)
            feedObj.put("isCustom", feed.isCustom)
            feedObj.put("lastUpdated", feed.lastUpdated)

            feedsArray.put(feedObj)
        }
        root.put("feeds", feedsArray)

        // User tag preferences & learned rules
        val (exactRules, keywordRules) = FeedCategoryAutoTagger.getUserTagRules(context)
        val tagRulesObj = JSONObject()
        
        val exactObj = JSONObject()
        for ((url, cat) in exactRules) {
            exactObj.put(url, cat)
        }
        tagRulesObj.put("exactUrlRules", exactObj)

        val kwObj = JSONObject()
        for ((kw, cat) in keywordRules) {
            kwObj.put(kw, cat)
        }
        tagRulesObj.put("learnedKeywordRules", kwObj)

        root.put("userTagRules", tagRulesObj)

        return root.toString(2) // 2-space indented pretty print JSON
    }

    /**
     * Parses a migration JSON backup file and returns extracted feeds and tag rules.
     */
    fun parseJsonBackup(context: Context, jsonString: String): Pair<List<FeedEntity>, JsonImportResult> {
        val feedsList = mutableListOf<FeedEntity>()
        var importedFeeds = 0
        var totalInFile = 0
        var tagsCount = 0

        try {
            val trimmedJson = jsonString.trim()
            val root = if (trimmedJson.startsWith("[")) {
                JSONObject().apply {
                    put("feeds", JSONArray(trimmedJson))
                }
            } else {
                JSONObject(trimmedJson)
            }
            
            if (root.has("tags")) {
                val tagsArr = root.getJSONArray("tags")
                tagsCount = tagsArr.length()
            }

            if (root.has("userTagRules")) {
                val tagRulesObj = root.getJSONObject("userTagRules")
                val exactRules = mutableMapOf<String, String>()
                val keywordRules = mutableMapOf<String, String>()

                if (tagRulesObj.has("exactUrlRules")) {
                    val exactObj = tagRulesObj.getJSONObject("exactUrlRules")
                    val keys = exactObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        exactRules[k] = exactObj.getString(k)
                    }
                }

                if (tagRulesObj.has("learnedKeywordRules")) {
                    val kwObj = tagRulesObj.getJSONObject("learnedKeywordRules")
                    val keys = kwObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        keywordRules[k] = kwObj.getString(k)
                    }
                }

                FeedCategoryAutoTagger.importUserTagRules(context, exactRules, keywordRules)
            }

            if (root.has("feeds")) {
                val feedsArr = root.getJSONArray("feeds")
                totalInFile = feedsArr.length()

                for (i in 0 until feedsArr.length()) {
                    val fObj = feedsArr.getJSONObject(i)
                    val rawUrl = fObj.optString("url", "").trim()
                    if (rawUrl.isBlank()) continue

                    val url = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                        "https://$rawUrl"
                    } else rawUrl

                    val title = fObj.optString("title", "Feed").trim()
                    
                    var category = fObj.optString("category", "").trim().uppercase()
                    if (category.isBlank() && fObj.has("tags")) {
                        val tagsArr = fObj.getJSONArray("tags")
                        if (tagsArr.length() > 0) {
                            category = tagsArr.getString(0).trim().uppercase()
                        }
                    }
                    if (category.isBlank()) category = "GENERAL"

                    val description = fObj.optString("description", "")
                    val iconUrl = fObj.optString("iconUrl", "")
                    val isPreferred = fObj.optBoolean("isPreferred", false)
                    val isEnabled = fObj.optBoolean("isEnabled", true)
                    val isCustom = fObj.optBoolean("isCustom", true)

                    val entity = FeedEntity(
                        url = url,
                        title = title,
                        category = category,
                        description = description,
                        iconUrl = iconUrl,
                        isPreferred = isPreferred,
                        isEnabled = isEnabled,
                        isCustom = isCustom,
                        lastUpdated = System.currentTimeMillis()
                    )
                    feedsList.add(entity)
                    importedFeeds++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Pair(feedsList, JsonImportResult(importedFeeds, totalInFile, tagsCount))
    }
}
