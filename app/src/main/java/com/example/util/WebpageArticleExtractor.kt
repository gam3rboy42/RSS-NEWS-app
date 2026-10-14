package com.example.util

import android.util.Log
import com.example.data.local.ArticleEntity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

data class ExtractedArticle(
    val fullText: String,
    val leadImageUrl: String? = null,
    val wordCount: Int,
    val title: String? = null
)

/**
 * Intelligent webpage downloader & readable content extractor for RSS feeds.
 * Detects truncated or snippet-only RSS feeds and downloads the full webpage
 * so articles can be viewed in full.
 */
object WebpageArticleExtractor {

    private const val TAG = "WebpageArticleExtractor"

    // Regex for typical truncation indicators in RSS feeds
    private val TRUNCATION_INDICATORS_REGEX = Regex(
        """(?i)(\[\s*\.\.\.\s*\]|\[\s*…\s*\]|\[&#8230;\]|\[&#x2026;\]|\b(?:read\s+more|continue\s+reading|full\s+article|the\s+post\s+.*\s+appeared\s+first\s+on|continue\s+on\s+site|read\s+full\s+story|read\s+the\s+full\s+story|click\s+here\s+to\s+read)\b)"""
    )

    /**
     * Determines whether an RSS article is clearly incomplete/stub/truncated.
     */
    fun isArticleIncomplete(article: ArticleEntity): Boolean {
        // Podcasts are audio/video; we don't treat them as text stubs requiring web scraping
        if (article.isPodcast || article.isVideoPodcast || article.category.equals("PODCASTS", ignoreCase = true)) {
            return false
        }
        val text = article.content.ifBlank { article.description }
        return isArticleIncomplete(text, article.description)
    }

    /**
     * Checks if given content string or description is incomplete.
     */
    fun isArticleIncomplete(content: String, description: String = ""): Boolean {
        val trimmedContent = content.trim()
        val trimmedDesc = description.trim()

        // 1. Content is completely blank or suspiciously short (< 350 chars)
        if (trimmedContent.isBlank()) return true
        if (trimmedContent.length < 350) return true

        val words = trimmedContent.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size

        // 2. Word count is very low (less than 75 words is almost certainly an excerpt/stub)
        if (wordCount < 75) return true

        // 3. Explicit truncation markers
        if (TRUNCATION_INDICATORS_REGEX.containsMatchIn(trimmedContent)) return true
        if (trimmedDesc.isNotBlank() && TRUNCATION_INDICATORS_REGEX.containsMatchIn(trimmedDesc)) return true

        // 4. Ends with ellipsis (...) and is relatively short (< 160 words)
        val endsWithEllipsis = trimmedContent.endsWith("...") || trimmedContent.endsWith("…") ||
                trimmedContent.endsWith(". . .") || trimmedContent.endsWith("&#8230;")
        if (endsWithEllipsis && wordCount < 160) return true

        // 5. If content exactly matches a short description (< 400 chars) with only 1 paragraph
        if (trimmedContent.equals(trimmedDesc, ignoreCase = true) && trimmedContent.length < 400 && !trimmedContent.contains("\n\n")) {
            return true
        }

        return false
    }

    /**
     * Downloads the webpage at [url] and extracts the full readable article text.
     */
    fun extractFromWebpage(
        url: String,
        client: OkHttpClient
    ): ExtractedArticle? {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) {
            return null
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .build()

            // Use client with reasonable timeout
            val fetchClient = client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val response = fetchClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful || resp.body == null) {
                    Log.w(TAG, "Failed HTTP ${resp.code} for $url")
                    return null
                }

                val html = resp.body!!.string()
                if (html.isBlank()) return null

                parseAndExtractContent(html, url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting webpage from $url: ${e.localizedMessage}")
            null
        }
    }

    /**
     * Parses raw HTML string and extracts the main article content.
     */
    fun parseAndExtractContent(html: String, baseUrl: String): ExtractedArticle? {
        val doc = Jsoup.parse(html, baseUrl)

        // 1. Remove non-content elements
        val removeSelectors = listOf(
            "script", "style", "noscript", "svg", "form", "iframe", "aside", "nav",
            "header", "footer", ".ad", ".advertisement", ".ads", ".sidebar", ".comments",
            "#comments", ".social-share", ".social", ".share-buttons", ".cookie-banner",
            ".newsletter", ".promo", ".related-posts", ".recommended-articles",
            ".tags", ".post-tags", ".author-bio", ".meta-info", "button", "input"
        )
        for (sel in removeSelectors) {
            doc.select(sel).remove()
        }

        // 2. Extract Lead Image from OpenGraph / Twitter meta tags
        var leadImage: String? = doc.selectFirst("meta[property='og:image']")?.attr("content")
        if (leadImage.isNullOrBlank()) {
            leadImage = doc.selectFirst("meta[name='twitter:image']")?.attr("content")
        }
        if (leadImage.isNullOrBlank()) {
            leadImage = doc.selectFirst("meta[property='og:image:url']")?.attr("content")
        }

        // 3. Locate best article container
        val candidateSelectors = listOf(
            "article",
            "[itemprop='articleBody']",
            ".entry-content",
            ".post-content",
            ".article-body",
            ".article__content",
            ".story-body",
            ".article-content",
            ".content-body",
            ".rich-text",
            "#article-body",
            "#post-content",
            "main",
            "[role='main']"
        )

        var contentContainer: Element? = null
        for (sel in candidateSelectors) {
            val candidate = doc.selectFirst(sel)
            if (candidate != null) {
                val pCount = candidate.select("p").size
                val textLength = candidate.text().length
                if (pCount >= 2 && textLength > 400) {
                    contentContainer = candidate
                    break
                }
            }
        }

        // Fallback: Find container element with the most paragraph text
        if (contentContainer == null) {
            var bestScore = 0
            for (element in doc.select("div, section, main")) {
                val pElements = element.select("> p, > div > p")
                val combinedLen = pElements.sumOf { it.text().length }
                if (combinedLen > bestScore && combinedLen > 300) {
                    bestScore = combinedLen
                    contentContainer = element
                }
            }
        }

        // If still null, fallback to doc.body()
        val root = contentContainer ?: doc.body() ?: return null

        // 4. Extract paragraphs and headings in order
        val contentElements = root.select("p, h2, h3, h4, blockquote, ul, ol")
        val paragraphs = mutableListOf<String>()

        for (el in contentElements) {
            // Check if element has already been captured by an ancestor
            if (el.parents().any { it in contentElements }) continue

            val text = el.text().trim()
            if (text.isBlank()) continue

            // Skip common small noise lines
            if (text.length < 25 && (text.startsWith("Photo:") || text.startsWith("Credit:") ||
                        text.startsWith("Follow ") || text.startsWith("Share ") ||
                        text.equals("Advertisement", ignoreCase = true))) {
                continue
            }

            val cleaned = HtmlSanitizer.cleanText(text)
            if (cleaned.isNotBlank()) {
                paragraphs.add(cleaned)
            }
        }

        if (paragraphs.isEmpty()) {
            val fallbackText = HtmlSanitizer.cleanText(root.text())
            if (fallbackText.length < 250) return null
            paragraphs.add(fallbackText)
        }

        val fullText = paragraphs.joinToString("\n\n")
        val words = fullText.split(Regex("\\s+")).filter { it.isNotBlank() }

        // Find article image if not found in meta
        if (leadImage.isNullOrBlank()) {
            val firstImg = root.selectFirst("figure img, img[src]")
            leadImage = firstImg?.absUrl("src")?.ifBlank { firstImg.attr("src") }
        }

        return ExtractedArticle(
            fullText = fullText,
            leadImageUrl = leadImage?.takeIf { it.isNotBlank() },
            wordCount = words.size,
            title = doc.title().takeIf { it.isNotBlank() }
        )
    }
}
