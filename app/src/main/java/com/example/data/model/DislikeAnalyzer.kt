package com.example.data.model

import com.example.BuildConfig
import com.example.data.local.ArticleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class DislikeAnalysisResult(
    val articleId: String,
    val articleTitle: String,
    val feedUrl: String = "",
    val feedTitle: String = "",
    val dislikedSubcategory: String,
    val dislikedKeywords: List<String>,
    val dislikedAuthor: String?,
    val explanation: String
)

object DislikeAnalyzer {

    // Common stop words to exclude during keyword extraction
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
        "in", "on", "at", "to", "for", "with", "about", "against", "between", "into", "through",
        "during", "before", "after", "above", "below", "from", "up", "down", "in", "out", "off",
        "over", "under", "again", "further", "then", "once", "here", "there", "when", "where",
        "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some", "such",
        "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "s", "t", "can",
        "will", "just", "don", "should", "now", "new", "says", "said", "how", "why", "what", "which"
    )

    suspend fun analyzeDislike(article: ArticleEntity): DislikeAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val aiResult = analyzeWithGemini(article, apiKey)
                if (aiResult != null) return@withContext aiResult
            } catch (e: Exception) {
                // Fallback to on-device analysis
            }
        }

        // On-device heuristic analysis
        analyzeOnDevice(article)
    }

    fun analyzeOnDevice(article: ArticleEntity): DislikeAnalysisResult {
        val subcat = if (article.subcategory.isNotBlank()) {
            article.subcategory
        } else {
            SubcategoryAnalyzer.analyze(article.title, article.description, article.category)
        }

        val keywords = extractKeywordsFromText("${article.title} ${article.description}")
        val author = if (article.author.isNotBlank()) article.author else null

        val topKeywords = keywords.take(3)
        val explanation = buildString {
            append("Analyzed article on-device. ")
            if (topKeywords.isNotEmpty()) {
                append("Muted keywords: ${topKeywords.joinToString { "#$it" }}. ")
            }
            if (subcat != "GENERAL" && subcat.isNotBlank()) {
                append("Lowered weight for subcategory '$subcat'. ")
            }
            if (author != null) {
                append("Recorded muted author '$author'. ")
            }
        }

        return DislikeAnalysisResult(
            articleId = article.id,
            articleTitle = article.title,
            feedUrl = article.feedUrl,
            feedTitle = article.feedTitle,
            dislikedSubcategory = subcat,
            dislikedKeywords = topKeywords,
            dislikedAuthor = author,
            explanation = explanation
        )
    }

    private fun extractKeywordsFromText(text: String): List<String> {
        val words = text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 && !stopWords.contains(it) }

        // Frequency map
        val freqMap = mutableMapOf<String, Int>()
        for (w in words) {
            freqMap[w] = (freqMap[w] ?: 0) + 1
        }

        return freqMap.entries
            .sortedByDescending { it.value }
            .map { it.key.uppercase(Locale.ROOT) }
    }

    private fun analyzeWithGemini(article: ArticleEntity, apiKey: String): DislikeAnalysisResult? {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val prompt = """
            Analyze why a user disliked this article and extract key topics to mute.
            Title: ${article.title}
            Description: ${article.description}
            Author: ${article.author}
            
            Return JSON with keys:
            - 'subcat': string (1-2 word subcategory)
            - 'keywords': string array of 2-3 specific UPPERCASE topic keywords
            - 'author': string (author name if present, else null)
            - 'explanation': string (1 short summary sentence explaining what was muted)
            Do not wrap in Markdown or backticks.
        """.trimIndent()

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
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
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

            val cleaned = text.replace("```json", "").replace("```", "").trim()
            val parsed = JSONObject(cleaned)

            val subcat = parsed.optString("subcat").ifBlank { "GENERAL" }
            val keywordsArray = parsed.optJSONArray("keywords")
            val keywordsList = mutableListOf<String>()
            if (keywordsArray != null) {
                for (i in 0 until keywordsArray.length()) {
                    keywordsList.add(keywordsArray.getString(i).uppercase(Locale.ROOT))
                }
            }
            val author = if (parsed.has("author") && !parsed.isNull("author")) parsed.getString("author") else null
            val explanation = parsed.optString("explanation").ifBlank { "Analyzed by AI: Muted similar topics." }

            return DislikeAnalysisResult(
                articleId = article.id,
                articleTitle = article.title,
                feedUrl = article.feedUrl,
                feedTitle = article.feedTitle,
                dislikedSubcategory = subcat,
                dislikedKeywords = keywordsList,
                dislikedAuthor = author,
                explanation = explanation
            )
        }
        return null
    }
}
