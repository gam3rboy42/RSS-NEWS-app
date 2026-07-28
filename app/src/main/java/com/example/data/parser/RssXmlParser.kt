package com.example.data.parser

import android.util.Xml
import com.example.data.local.ArticleEntity
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ParsedFeed(
    val title: String,
    val description: String,
    val articles: List<ArticleEntity>
)

object RssXmlParser {

    fun parse(inputStream: InputStream, feedUrl: String, defaultCategory: String, defaultFeedTitle: String, isPreferredSource: Boolean): ParsedFeed {
        inputStream.use { stream ->
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name?.lowercase(Locale.ROOT)
                    if (tagName == "rss" || tagName == "channel") {
                        return parseRss2(parser, feedUrl, defaultCategory, defaultFeedTitle, isPreferredSource)
                    } else if (tagName == "feed") {
                        return parseAtom(parser, feedUrl, defaultCategory, defaultFeedTitle, isPreferredSource)
                    }
                }
                eventType = parser.next()
            }
            return ParsedFeed(defaultFeedTitle, "", emptyList())
        }
    }

    private fun parseRss2(
        parser: XmlPullParser,
        feedUrl: String,
        defaultCategory: String,
        defaultFeedTitle: String,
        isPreferredSource: Boolean
    ): ParsedFeed {
        var feedTitle = defaultFeedTitle
        var feedDescription = ""
        val articles = mutableListOf<ArticleEntity>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name?.lowercase(Locale.ROOT)) {
                    "title" -> if (feedTitle.isEmpty() || feedTitle == "Feed" || feedTitle == defaultFeedTitle) {
                        val parsedTitle = readText(parser)
                        if (parsedTitle.isNotBlank()) feedTitle = parsedTitle
                    }
                    "description" -> if (feedDescription.isEmpty()) {
                        feedDescription = stripHtml(readText(parser))
                    }
                    "item" -> {
                        val article = readRssItem(parser, feedUrl, defaultCategory, feedTitle.ifEmpty { defaultFeedTitle }, isPreferredSource)
                        if (article != null) {
                            articles.add(article)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = feedTitle.ifEmpty { defaultFeedTitle },
            description = feedDescription,
            articles = articles
        )
    }

    private fun readRssItem(
        parser: XmlPullParser,
        feedUrl: String,
        category: String,
        feedTitle: String,
        isPreferredSource: Boolean
    ): ArticleEntity? {
        var title = ""
        var link = ""
        var description = ""
        var content = ""
        var pubDateStr = ""
        var imageUrl: String? = null
        var guid = ""

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name?.lowercase(Locale.ROOT) == "item")) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name?.lowercase(Locale.ROOT) ?: ""
                when (tagName) {
                    "title" -> title = readText(parser)
                    "link" -> link = readText(parser)
                    "guid" -> guid = readText(parser)
                    "description" -> {
                        val rawDesc = readText(parser)
                        description = stripHtml(rawDesc)
                        if (imageUrl == null) {
                            imageUrl = extractImageFromHtml(rawDesc)
                        }
                    }
                    "content:encoded", "content" -> {
                        val rawContent = readText(parser)
                        content = stripHtml(rawContent)
                        if (imageUrl == null) {
                            imageUrl = extractImageFromHtml(rawContent)
                        }
                    }
                    "pubdate", "dc:date", "published", "updated" -> pubDateStr = readText(parser)
                    "enclosure" -> {
                        val type = parser.getAttributeValue(null, "type")
                        if (type != null && type.startsWith("image")) {
                            val url = parser.getAttributeValue(null, "url")
                            if (!url.isNullOrBlank()) imageUrl = url
                        }
                    }
                    "media:content", "media:thumbnail" -> {
                        val url = parser.getAttributeValue(null, "url")
                        if (!url.isNullOrBlank()) {
                            imageUrl = url
                        }
                    }
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }

        if (title.isBlank() && link.isBlank()) return null

        val articleId = guid.ifBlank { link.ifBlank { "$feedUrl#${title.hashCode()}" } }
        val finalLink = link.ifBlank { guid }
        val cleanDesc = description.ifBlank { content.take(200) }
        val timestamp = parsePubDate(pubDateStr)

        return ArticleEntity(
            id = articleId,
            feedUrl = feedUrl,
            feedTitle = feedTitle,
            category = category,
            title = title.trim(),
            description = cleanDesc.trim(),
            content = content.ifBlank { description }.trim(),
            link = finalLink.trim(),
            pubDate = formatTimestamp(timestamp),
            pubDateTimestamp = timestamp,
            imageUrl = imageUrl,
            isBookmarked = false,
            isRead = false,
            isDeal = false,
            isPreferredSource = isPreferredSource,
            storyClusterHash = ""
        )
    }

    private fun parseAtom(
        parser: XmlPullParser,
        feedUrl: String,
        defaultCategory: String,
        defaultFeedTitle: String,
        isPreferredSource: Boolean
    ): ParsedFeed {
        var feedTitle = defaultFeedTitle
        val articles = mutableListOf<ArticleEntity>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name?.lowercase(Locale.ROOT)) {
                    "title" -> if (feedTitle.isEmpty() || feedTitle == "Feed" || feedTitle == defaultFeedTitle) {
                        val parsedTitle = readText(parser)
                        if (parsedTitle.isNotBlank()) feedTitle = parsedTitle
                    }
                    "entry" -> {
                        val article = readAtomEntry(parser, feedUrl, defaultCategory, feedTitle.ifEmpty { defaultFeedTitle }, isPreferredSource)
                        if (article != null) {
                            articles.add(article)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = feedTitle.ifEmpty { defaultFeedTitle },
            description = "",
            articles = articles
        )
    }

    private fun readAtomEntry(
        parser: XmlPullParser,
        feedUrl: String,
        category: String,
        feedTitle: String,
        isPreferredSource: Boolean
    ): ArticleEntity? {
        var title = ""
        var link = ""
        var summary = ""
        var content = ""
        var updated = ""
        var id = ""
        var imageUrl: String? = null

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name?.lowercase(Locale.ROOT) == "entry")) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name?.lowercase(Locale.ROOT) ?: ""
                when (tagName) {
                    "title" -> title = readText(parser)
                    "id" -> id = readText(parser)
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        if (rel == null || rel == "alternate") {
                            if (!href.isNullOrBlank()) link = href
                        }
                    }
                    "summary" -> {
                        val raw = readText(parser)
                        summary = stripHtml(raw)
                        if (imageUrl == null) imageUrl = extractImageFromHtml(raw)
                    }
                    "content" -> {
                        val raw = readText(parser)
                        content = stripHtml(raw)
                        if (imageUrl == null) imageUrl = extractImageFromHtml(raw)
                    }
                    "updated", "published" -> updated = readText(parser)
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }

        if (title.isBlank()) return null

        val articleId = id.ifBlank { link.ifBlank { "$feedUrl#${title.hashCode()}" } }
        val timestamp = parsePubDate(updated)

        return ArticleEntity(
            id = articleId,
            feedUrl = feedUrl,
            feedTitle = feedTitle,
            category = category,
            title = title.trim(),
            description = summary.ifBlank { content.take(200) }.trim(),
            content = content.ifBlank { summary }.trim(),
            link = link.trim(),
            pubDate = formatTimestamp(timestamp),
            pubDateTimestamp = timestamp,
            imageUrl = imageUrl,
            isBookmarked = false,
            isRead = false,
            isDeal = false,
            isPreferredSource = isPreferredSource,
            storyClusterHash = ""
        )
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var eventType = parser.next()
        while (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.CDSECT) {
            sb.append(parser.text)
            eventType = parser.next()
        }
        return sb.toString().trim()
    }

    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractImageFromHtml(html: String): String? {
        val match = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
        return match?.groupValues?.get(1)
    }

    private fun parsePubDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()

        val formats = arrayOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss"
        )

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(dateStr)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "JUST NOW"
            minutes < 60 -> "${minutes}M AGO"
            hours < 24 -> "${hours}H AGO"
            days < 7 -> "${days}D AGO"
            else -> {
                val sdf = SimpleDateFormat("MMM dd", Locale.US)
                sdf.format(Date(timestamp)).uppercase()
            }
        }
    }
}
