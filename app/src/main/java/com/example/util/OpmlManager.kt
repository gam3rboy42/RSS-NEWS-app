package com.example.util

import android.util.Xml
import com.example.data.local.FeedEntity
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class ParsedOpmlItem(
    val title: String,
    val xmlUrl: String,
    val category: String
)

object OpmlManager {

    fun exportToOpml(feeds: List<FeedEntity>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>Nothing RSS Subscriptions</title>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")

        val grouped = feeds.groupBy { it.category.ifBlank { "UNCATEGORIZED" }.uppercase() }
        for ((category, categoryFeeds) in grouped) {
            val safeCat = escapeXml(category)
            sb.append("    <outline text=\"$safeCat\" title=\"$safeCat\">\n")
            for (feed in categoryFeeds) {
                val safeTitle = escapeXml(feed.title.ifBlank { "Feed" })
                val safeUrl = escapeXml(feed.url)
                sb.append("      <outline type=\"rss\" text=\"$safeTitle\" title=\"$safeTitle\" xmlUrl=\"$safeUrl\" />\n")
            }
            sb.append("    </outline>\n")
        }

        sb.append("  </body>\n")
        sb.append("</opml>")
        return sb.toString()
    }

    fun parseOpml(xmlContent: String): List<ParsedOpmlItem> {
        val items = mutableListOf<ParsedOpmlItem>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))

            var currentCategory = "UNCATEGORIZED"
            var eventType = parser.eventType

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tagName = parser.name
                    if (tagName.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            ?: parser.getAttributeValue(null, "url")
                        val title = parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "text")
                            ?: "Feed"
                        val type = parser.getAttributeValue(null, "type")

                        if (!xmlUrl.isNullOrBlank()) {
                            items.add(
                                ParsedOpmlItem(
                                    title = title.trim(),
                                    xmlUrl = xmlUrl.trim(),
                                    category = currentCategory
                                )
                            )
                        } else {
                            // Category outline header
                            val textAttr = parser.getAttributeValue(null, "text")
                                ?: parser.getAttributeValue(null, "title")
                            if (!textAttr.isNullOrBlank() && type.isNullOrBlank()) {
                                currentCategory = textAttr.trim().uppercase()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return items
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
