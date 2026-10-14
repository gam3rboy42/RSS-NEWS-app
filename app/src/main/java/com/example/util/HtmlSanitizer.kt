package com.example.util

import android.text.Html
import com.example.data.local.ArticleEntity
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * High-performance HTML sanitizer and entity decoder for RSS and Atom feeds.
 * Decodes numeric character entities (e.g. &#8217; for ’ or &#160; for non-breaking space),
 * hexadecimal entities, and standard named entities, including multi-pass unescaping for
 * double-escaped feed content.
 */
object HtmlSanitizer {

    private val HTML_TAG_REGEX = Pattern.compile("<[^>]+>")
    private val DECIMAL_ENTITY_REGEX = Pattern.compile("&#(\\d{1,7});")
    private val HEX_ENTITY_REGEX = Pattern.compile("&#[xX]([0-9a-fA-F]{1,6});")
    private val MULTI_SPACE_REGEX = Pattern.compile("[ \\t\\x0B\\f\\r]+")
    private val MULTI_NEWLINE_REGEX = Pattern.compile("\\n{3,}")

    // Comprehensive named entities mapping
    private val NAMED_ENTITIES = mapOf(
        "&amp;" to "&",
        "&quot;" to "\"",
        "&apos;" to "'",
        "&lt;" to "<",
        "&gt;" to ">",
        "&nbsp;" to " ",
        "&rsquo;" to "’",
        "&lsquo;" to "‘",
        "&ldquo;" to "“",
        "&rdquo;" to "”",
        "&mdash;" to "—",
        "&ndash;" to "–",
        "&hellip;" to "…",
        "&trade;" to "™",
        "&copy;" to "©",
        "&reg;" to "®",
        "&euro;" to "€",
        "&pound;" to "£",
        "&yen;" to "¥",
        "&cent;" to "¢",
        "&bull;" to "•",
        "&prime;" to "′",
        "&Prime;" to "″",
        "&frac12;" to "½",
        "&frac14;" to "¼",
        "&frac34;" to "¾",
        "&deg;" to "°",
        "&plusmn;" to "±",
        "&times;" to "×",
        "&divide;" to "÷",
        "&para;" to "¶",
        "&sect;" to "§",
        "&middot;" to "·"
    )

    /**
     * Decodes all HTML entities (named, decimal, hex) and strips HTML tags.
     * Performs multi-pass unescaping to handle double-encoded feeds (e.g. &amp;#8217; or &amp;quot;).
     */
    fun cleanText(input: String?): String {
        if (input.isNullOrBlank()) return ""

        var text: String = input

        // 1. Strip HTML tags if present
        if (text.contains('<')) {
            text = HTML_TAG_REGEX.matcher(text).replaceAll(" ")
        }

        // 2. Decode entities (up to 3 passes to unwrap double-escaped entities like &amp;#8217;)
        var passes = 0
        while (passes < 3 && text.contains('&')) {
            val prev = text
            text = decodeEntities(text)
            if (text == prev) break
            passes++
        }

        // 3. Fallback to Android Html.fromHtml if any unhandled entity remains
        if (text.contains('&') && (text.contains("&#") || text.contains(';'))) {
            try {
                text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
            } catch (_: Throwable) {
                // Ignore fallback error
            }
        }

        // 4. Normalize whitespaces and non-breaking spaces
        return normalizeWhitespace(text)
    }

    /**
     * Cleans rich content while preserving paragraph and line breaks for reading.
     */
    fun cleanHtmlContent(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var text: String = input

        // Convert <p>, <br>, <div> into newlines before stripping
        text = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</?p[^>]*>"), "\n\n")
            .replace(Regex("(?i)</?div[^>]*>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "\n• ")

        return cleanText(text)
    }

    private fun decodeEntities(input: String): String {
        var str = input

        // Named entities
        for ((entity, replacement) in NAMED_ENTITIES) {
            if (str.contains(entity, ignoreCase = true)) {
                str = str.replace(entity, replacement, ignoreCase = true)
            }
        }

        // Decimal entities: &#8217;, &#160;, &#39;, etc.
        if (str.contains("&#")) {
            val decMatcher = DECIMAL_ENTITY_REGEX.matcher(str)
            val sbDec = StringBuffer()
            while (decMatcher.find()) {
                val codeStr = decMatcher.group(1) ?: ""
                val fullMatch = decMatcher.group(0) ?: ""
                try {
                    val code = codeStr.toInt()
                    val charStr = when (code) {
                        160 -> " " // Non-breaking space
                        8217 -> "’" // Right single quote / apostrophe
                        8216 -> "‘" // Left single quote
                        8220 -> "“" // Left double quote
                        8221 -> "”" // Right double quote
                        8211 -> "–" // En dash
                        8212 -> "—" // Em dash
                        8230 -> "…" // Ellipsis
                        else -> {
                            if (Character.isValidCodePoint(code)) {
                                String(Character.toChars(code))
                            } else {
                                fullMatch
                            }
                        }
                    }
                    decMatcher.appendReplacement(sbDec, Matcher.quoteReplacement(charStr))
                } catch (_: Exception) {
                    decMatcher.appendReplacement(sbDec, Matcher.quoteReplacement(fullMatch))
                }
            }
            decMatcher.appendTail(sbDec)
            str = sbDec.toString()
        }

        // Hexadecimal entities: &#x2019;, &#xA0;, etc.
        if (str.contains("&#x", ignoreCase = true)) {
            val hexMatcher = HEX_ENTITY_REGEX.matcher(str)
            val sbHex = StringBuffer()
            while (hexMatcher.find()) {
                val hexStr = hexMatcher.group(1) ?: ""
                val fullMatch = hexMatcher.group(0) ?: ""
                try {
                    val code = hexStr.toInt(16)
                    val charStr = when (code) {
                        0xA0 -> " "
                        else -> {
                            if (Character.isValidCodePoint(code)) {
                                String(Character.toChars(code))
                            } else {
                                fullMatch
                            }
                        }
                    }
                    hexMatcher.appendReplacement(sbHex, Matcher.quoteReplacement(charStr))
                } catch (_: Exception) {
                    hexMatcher.appendReplacement(sbHex, Matcher.quoteReplacement(fullMatch))
                }
            }
            hexMatcher.appendTail(sbHex)
            str = sbHex.toString()
        }

        return str
    }

    private fun normalizeWhitespace(input: String): String {
        val replaced = input
            .replace('\u00A0', ' ') // Non-breaking space
            .replace('\u202F', ' ') // Narrow no-break space
            .replace("\u200B", "")  // Zero-width space
            .replace("\uFEFF", "")  // Zero-width no-break space / BOM
        val singleSpaces = MULTI_SPACE_REGEX.matcher(replaced).replaceAll(" ")
        val singleNewlines = MULTI_NEWLINE_REGEX.matcher(singleSpaces).replaceAll("\n\n")
        return singleNewlines.trim()
    }

    /**
     * Sanitizes all user-visible text fields of an ArticleEntity.
     */
    fun sanitizeArticle(article: ArticleEntity): ArticleEntity {
        val cleanTitle = cleanText(article.title)
        val cleanDesc = cleanText(article.description)
        val cleanContent = if (article.content.isNotBlank()) cleanHtmlContent(article.content) else ""
        val cleanAuthor = cleanText(article.author)
        val cleanFeedTitle = cleanText(article.feedTitle)

        if (cleanTitle == article.title &&
            cleanDesc == article.description &&
            cleanContent == article.content &&
            cleanAuthor == article.author &&
            cleanFeedTitle == article.feedTitle
        ) {
            return article
        }

        return article.copy(
            title = cleanTitle,
            description = cleanDesc,
            content = cleanContent,
            author = cleanAuthor,
            feedTitle = cleanFeedTitle
        )
    }
}
