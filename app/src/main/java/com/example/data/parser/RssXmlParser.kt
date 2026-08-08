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
        var author = ""
        var mediaUrl: String? = null
        var mediaType: String? = null
        var duration: String? = null

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name?.lowercase(Locale.ROOT) == "item")) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name?.lowercase(Locale.ROOT) ?: ""
                when (tagName) {
                    "title" -> title = readText(parser)
                    "link" -> link = readText(parser)
                    "guid" -> guid = readText(parser)
                    "author", "dc:creator", "creator" -> author = cleanAuthor(readText(parser))
                    "itunes:duration", "duration" -> duration = readText(parser)
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
                        val type = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT) ?: ""
                        val url = parser.getAttributeValue(null, "url") ?: ""
                        if (url.isNotBlank()) {
                            if (type.startsWith("image")) {
                                if (imageUrl == null) imageUrl = url
                            } else if (type.startsWith("video") || url.endsWith(".mp4") || url.endsWith(".m4v") || url.endsWith(".webm") || url.contains("youtube.com") || url.contains("youtu.be")) {
                                mediaUrl = url
                                mediaType = "VIDEO"
                            } else if (type.startsWith("audio") || url.endsWith(".mp3") || url.endsWith(".m4a") || url.endsWith(".ogg") || url.endsWith(".wav")) {
                                if (mediaUrl == null) {
                                    mediaUrl = url
                                    mediaType = "AUDIO"
                                }
                            }
                        }
                    }
                    "media:content", "media:thumbnail" -> {
                        val type = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT) ?: ""
                        val url = parser.getAttributeValue(null, "url") ?: ""
                        if (url.isNotBlank()) {
                            if (type.startsWith("video") || url.endsWith(".mp4") || url.endsWith(".m4v") || url.contains("youtube.com")) {
                                mediaUrl = url
                                mediaType = "VIDEO"
                            } else if (type.startsWith("image") || tagName == "media:thumbnail") {
                                if (imageUrl == null) imageUrl = url
                            } else if (type.startsWith("audio")) {
                                if (mediaUrl == null) {
                                    mediaUrl = url
                                    mediaType = "AUDIO"
                                }
                            }
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
        val analyzedSubcat = com.example.data.model.SubcategoryAnalyzer.analyze(title, cleanDesc, category)

        // Determine if Video Podcast or Audio Podcast
        val isVideo = mediaType == "VIDEO" || finalLink.contains("youtube.com/watch") || finalLink.contains("youtu.be") || finalLink.endsWith(".mp4") || finalLink.endsWith(".m4v")
        val isAudio = mediaType == "AUDIO" || finalLink.endsWith(".mp3") || finalLink.endsWith(".m4a")
        val isPodcastItem = category.equals("PODCASTS", ignoreCase = true) || isVideo || isAudio || !mediaUrl.isNullOrBlank() || !duration.isNullOrBlank()

        val finalMediaUrl = mediaUrl ?: if (isVideo || isAudio) finalLink else null
        val finalMediaType = if (isVideo) "VIDEO" else if (isAudio) "AUDIO" else mediaType

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
            author = author,
            isBookmarked = false,
            isRead = false,
            isDeal = false,
            isPreferredSource = isPreferredSource,
            storyClusterHash = "",
            subcategory = analyzedSubcat,
            mediaUrl = finalMediaUrl,
            mediaType = finalMediaType,
            isPodcast = isPodcastItem,
            isVideoPodcast = isVideo,
            duration = duration
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
        var author = ""

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name?.lowercase(Locale.ROOT) == "entry")) {
            if (eventType == XmlPullParser.START_TAG) {
                val tagName = parser.name?.lowercase(Locale.ROOT) ?: ""
                when (tagName) {
                    "title" -> title = readText(parser)
                    "id" -> id = readText(parser)
                    "author", "dc:creator", "creator" -> author = cleanAuthor(readText(parser))
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
        val cleanDesc = summary.ifBlank { content.take(200) }.trim()
        val analyzedSubcat = com.example.data.model.SubcategoryAnalyzer.analyze(title, cleanDesc, category)

        val isYouTube = link.contains("youtube.com") || link.contains("youtu.be") || feedUrl.contains("youtube.com")
        val isVideo = isYouTube || link.endsWith(".mp4") || link.endsWith(".m4v")
        val isAudio = link.endsWith(".mp3") || link.endsWith(".m4a")
        val isPodcastItem = category.equals("PODCASTS", ignoreCase = true) || isVideo || isAudio

        return ArticleEntity(
            id = articleId,
            feedUrl = feedUrl,
            feedTitle = feedTitle,
            category = category,
            title = title.trim(),
            description = cleanDesc,
            content = content.ifBlank { summary }.trim(),
            link = link.trim(),
            pubDate = formatTimestamp(timestamp),
            pubDateTimestamp = timestamp,
            imageUrl = imageUrl,
            author = author,
            isBookmarked = false,
            isRead = false,
            isDeal = false,
            isPreferredSource = isPreferredSource,
            storyClusterHash = "",
            subcategory = analyzedSubcat,
            mediaUrl = if (isVideo || isAudio) link.trim() else null,
            mediaType = if (isVideo) "VIDEO" else if (isAudio) "AUDIO" else null,
            isPodcast = isPodcastItem,
            isVideoPodcast = isVideo
        )
    }

    private fun cleanAuthor(raw: String): String {
        val stripped = stripHtml(raw).trim()
        val matchParen = Regex("""\(([^)]+)\)""").find(stripped)
        if (matchParen != null) {
            val extracted = matchParen.groupValues[1].trim()
            if (extracted.isNotBlank()) return extracted
        }
        val noEmail = stripped.replace(Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""), "").trim()
        return noEmail.ifBlank { stripped }
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
