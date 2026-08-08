package com.example.data.model

import com.example.data.local.ArticleEntity
import java.util.Locale

object DealDetector {

    private val dealKeywords = listOf(
        "deal", "discount", "sale", "coupon", "promo", "save $",
        "price drop", "lowest price", "clearance", "bogo",
        "buy 1 get 1", "bargain", "discounted", "rebate",
        "cashback", "black friday", "cyber monday", "prime day",
        "daily deal", "best buy deal", "amazon deal"
    )

    fun isDeal(title: String, description: String): Boolean {
        val text = "${title.lowercase(Locale.ROOT)} ${description.lowercase(Locale.ROOT)}"
        
        // Check for discount percentage or dollar savings pattern like "20% off", "save $50", "$10 off"
        if (Regex("""\b\d{1,2}%\s+off\b""").containsMatchIn(text)) return true
        if (Regex("""\bsave\s+\$\d+""").containsMatchIn(text)) return true
        if (Regex("""\b\$\d+(\.\d{2})?\s+off\b""").containsMatchIn(text)) return true
        if (Regex("""\b\d+%\s+discount\b""").containsMatchIn(text)) return true
        if (Regex("""\b(great|huge|best|special)\s+deal\b""").containsMatchIn(text)) return true

        return dealKeywords.any { keyword ->
            text.contains(keyword)
        }
    }

    fun processArticles(articles: List<ArticleEntity>): List<ArticleEntity> {
        return articles.map { article ->
            val detected = isDeal(article.title, article.description)
            if (article.isDeal != detected) {
                article.copy(isDeal = detected)
            } else {
                article
            }
        }
    }
}
