package com.example.data.parser

import android.util.Log
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
    val articles: List<ArticleEntity>,
    val iconUrl: String = ""
)

object RssXmlParser {

    fun parse(inputStream: InputStream, feedUrl: String, defaultCategory: String, defaultFeedTitle: String, isPreferredSource: Boolean): ParsedFeed {
        return try {
            inputStream.use { stream ->
                val parser: XmlPullParser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val name = parser.name?.lowercase(Locale.ROOT) ?: ""
                        if (name == "rss" || name == "channel") {
                            return parseRss2(parser, feedUrl, defaultCategory, defaultFeedTitle, isPreferredSource)
                        } else if (name == "feed") {
                            return parseAtom(parser, feedUrl, defaultCategory, defaultFeedTitle, isPreferredSource)
                        }
                    }
                    eventType = parser.next()
                }
                ParsedFeed(defaultFeedTitle, "", emptyList())
            }
        } catch (e: Exception) {
            Log.w("RssXmlParser", "XML Parsing error for $feedUrl: ${e.localizedMessage}")
            ParsedFeed(defaultFeedTitle, "", emptyList())
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
        var feedIconUrl = ""
        val articles = mutableListOf<ArticleEntity>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name?.lowercase(Locale.ROOT) ?: ""
                when {
                    name == "title" -> if (feedTitle.isEmpty() || feedTitle == "Feed" || feedTitle == defaultFeedTitle) {
                        val parsedTitle = readText(parser)
                        if (parsedTitle.isNotBlank()) feedTitle = parsedTitle
                    }
                    name == "description" -> if (feedDescription.isEmpty()) {
                        feedDescription = stripHtml(readText(parser))
                    }
                    name == "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if (href.isNotBlank() && feedIconUrl.isEmpty()) {
                            feedIconUrl = href
                        }
                    }
                    name == "image" -> {
                        if (feedIconUrl.isEmpty()) {
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            if (href.isNotBlank()) {
                                feedIconUrl = href
                            }
                        }
                    }
                    name == "item" -> {
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
            articles = articles,
            iconUrl = feedIconUrl
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
                when {
                    tagName == "title" -> title = readText(parser)
                    tagName == "link" -> {
                        val text = readText(parser)
                        if (text.isNotBlank()) link = text
                    }
                    tagName == "guid" -> guid = readText(parser)
                    tagName == "author" || tagName == "dc:creator" || tagName == "creator" -> author = cleanAuthor(readText(parser))
                    tagName == "itunes:duration" || tagName == "duration" -> duration = readText(parser)
                    tagName == "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if (href.isNotBlank() && imageUrl == null) {
                            imageUrl = href
                        }
                        skipTag(parser)
                    }
                    tagName == "description" -> {
                        val rawDesc = readText(parser)
                        description = stripHtml(rawDesc)
                        if (imageUrl == null) {
                            imageUrl = extractImageFromHtml(rawDesc)
                        }
                    }
                    tagName == "content:encoded" || tagName == "content" -> {
                        val rawContent = readText(parser)
                        content = stripHtml(rawContent)
                        if (imageUrl == null) {
                            imageUrl = extractImageFromHtml(rawContent)
                        }
                    }
                    tagName == "pubdate" || tagName == "dc:date" || tagName == "published" || tagName == "updated" -> pubDateStr = readText(parser)
                    tagName == "enclosure" -> {
                        val type = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT) ?: ""
                        val url = parser.getAttributeValue(null, "url") ?: ""
                        if (url.isNotBlank()) {
                            val lowerUrl = url.lowercase(Locale.ROOT)
                            if (type.startsWith("image")) {
                                if (imageUrl == null) imageUrl = url
                            } else if (type.startsWith("audio") || lowerUrl.contains(".mp3") || lowerUrl.contains(".m4a") || lowerUrl.contains(".ogg") || lowerUrl.contains(".wav") || lowerUrl.contains(".aac")) {
                                mediaUrl = url
                                mediaType = "AUDIO"
                            } else if (type.startsWith("video") || lowerUrl.contains(".mp4") || lowerUrl.contains(".m4v") || lowerUrl.contains(".webm")) {
                                if (mediaUrl == null) {
                                    mediaUrl = url
                                    mediaType = "VIDEO"
                                }
                            }
                        }
                        skipTag(parser)
                    }
                    tagName == "media:content" || tagName == "media:thumbnail" -> {
                        val type = parser.getAttributeValue(null, "type")?.lowercase(Locale.ROOT) ?: ""
                        val url = parser.getAttributeValue(null, "url") ?: ""
                        if (url.isNotBlank()) {
                            val lowerUrl = url.lowercase(Locale.ROOT)
                            if (type.startsWith("audio") || lowerUrl.contains(".mp3") || lowerUrl.contains(".m4a") || lowerUrl.contains(".ogg") || lowerUrl.contains(".wav")) {
                                if (mediaUrl == null || mediaType != "AUDIO") {
                                    mediaUrl = url
                                    mediaType = "AUDIO"
                                }
                            } else if (type.startsWith("video") || lowerUrl.contains(".mp4") || lowerUrl.contains(".m4v")) {
                                if (mediaUrl == null) {
                                    mediaUrl = url
                                    mediaType = "VIDEO"
                                }
                            } else if (type.startsWith("image") || tagName == "media:thumbnail") {
                                if (imageUrl == null) imageUrl = url
                            }
                        }
                        skipTag(parser)
                    }
                    else -> skipTag(parser)
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

        val lowerLink = finalLink.lowercase(Locale.ROOT)
        val lowerMediaUrl = (mediaUrl ?: "").lowercase(Locale.ROOT)

        val isAudio = mediaType == "AUDIO" ||
                lowerMediaUrl.contains(".mp3") || lowerMediaUrl.contains(".m4a") ||
                lowerMediaUrl.contains(".ogg") || lowerMediaUrl.contains(".wav") ||
                lowerMediaUrl.contains(".aac") || lowerMediaUrl.contains(".flac") ||
                lowerLink.contains(".mp3") || lowerLink.contains(".m4a")

        val isVideo = !isAudio && (
                mediaType == "VIDEO" ||
                lowerMediaUrl.contains(".mp4") || lowerMediaUrl.contains(".m4v") ||
                lowerMediaUrl.contains(".webm") || lowerLink.contains(".mp4") ||
                lowerLink.contains(".m4v")
        )

        val isPodcastItem = category.equals("PODCASTS", ignoreCase = true) || isVideo || isAudio || mediaType == "AUDIO" || mediaType == "VIDEO" || !duration.isNullOrBlank()

        val finalMediaUrl = mediaUrl ?: if (isVideo || isAudio) finalLink else null
        val finalMediaType = if (isAudio) "AUDIO" else if (isVideo) "VIDEO" else mediaType

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
                when {
                    tagName == "title" -> title = readText(parser)
                    tagName == "id" -> id = readText(parser)
                    tagName == "author" || tagName == "dc:creator" || tagName == "creator" -> author = cleanAuthor(readText(parser))
                    tagName == "link" -> {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        if (rel == null || rel == "alternate") {
                            if (!href.isNullOrBlank()) link = href
                        }
                        skipTag(parser)
                    }
                    tagName == "summary" -> {
                        val raw = readText(parser)
                        summary = stripHtml(raw)
                        if (imageUrl == null) imageUrl = extractImageFromHtml(raw)
                    }
                    tagName == "content" -> {
                        val raw = readText(parser)
                        content = stripHtml(raw)
                        if (imageUrl == null) imageUrl = extractImageFromHtml(raw)
                    }
                    tagName == "updated" || tagName == "published" -> updated = readText(parser)
                    else -> skipTag(parser)
                }
            }
            if (eventType == XmlPullParser.END_DOCUMENT) break
            eventType = parser.next()
        }

        if (title.isBlank() && link.isBlank()) return null

        val articleId = id.ifBlank { link.ifBlank { "$feedUrl#${title.hashCode()}" } }
        val finalLink = link.ifBlank { id }
        val timestamp = parsePubDate(updated)
        val cleanDesc = summary.ifBlank { content.take(200) }.trim()
        val analyzedSubcat = com.example.data.model.SubcategoryAnalyzer.analyze(title, cleanDesc, category)

        val isVideo = link.endsWith(".mp4") || link.endsWith(".m4v")
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

    private val REGEX_HTML_TAGS = Regex("<[^>]*>")
    private val REGEX_NBSP = Regex("&nbsp;")
    private val REGEX_AMP = Regex("&amp;")
    private val REGEX_LT = Regex("&lt;")
    private val REGEX_GT = Regex("&gt;")
    private val REGEX_QUOT = Regex("&quot;")
    private val REGEX_APOS = Regex("&#39;|&#8217;")
    private val REGEX_MULTI_SPACE = Regex("\\s+")
    private val REGEX_IMG_SRC = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val REGEX_EMAIL = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""")
    private val REGEX_PAREN_CONTENT = Regex("""\(([^)]+)\)""")

    private val DATE_FORMAT_STRINGS = arrayOf(
        "EEE, dd MMM yyyy HH:mm:ss z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm z",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss"
    )

    private val THREAD_LOCAL_FORMATS = ThreadLocal.withInitial {
        DATE_FORMAT_STRINGS.map { fmt ->
            SimpleDateFormat(fmt, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }

    private fun cleanAuthor(raw: String): String {
        val stripped = stripHtml(raw).trim()
        val matchParen = REGEX_PAREN_CONTENT.find(stripped)
        if (matchParen != null) {
            val extracted = matchParen.groupValues[1].trim()
            if (extracted.isNotBlank()) return extracted
        }
        val noEmail = stripped.replace(REGEX_EMAIL, "").trim()
        return noEmail.ifBlank { stripped }
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    sb.append(parser.text)
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString().trim()
    }

    private fun skipTag(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        if (!html.contains('<') && !html.contains('&')) return html.trim()

        return html.replace(REGEX_HTML_TAGS, " ")
            .replace(REGEX_NBSP, " ")
            .replace(REGEX_AMP, "&")
            .replace(REGEX_LT, "<")
            .replace(REGEX_GT, ">")
            .replace(REGEX_QUOT, "\"")
            .replace(REGEX_APOS, "'")
            .replace(REGEX_MULTI_SPACE, " ")
            .trim()
    }

    private fun extractImageFromHtml(html: String): String? {
        if (html.isBlank() || !html.contains("<img", ignoreCase = true)) return null
        val match = REGEX_IMG_SRC.find(html)
        return match?.groupValues?.get(1)
    }

    private fun parsePubDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val cleanDateStr = dateStr.trim()
        val formatters = THREAD_LOCAL_FORMATS.get()
        for (sdf in formatters) {
            try {
                val date = sdf.parse(cleanDateStr)
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
