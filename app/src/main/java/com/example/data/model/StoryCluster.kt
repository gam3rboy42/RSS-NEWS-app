package com.example.data.model

import com.example.data.local.ArticleEntity
import java.util.Locale

data class StoryCluster(
    val clusterId: String,
    val primaryTitle: String,
    val category: String,
    val isDeal: Boolean,
    val isBookmarked: Boolean,
    val latestTimestamp: Long,
    val articles: List<ArticleEntity>
) {
    val sourceCount: Int get() = articles.size
    val isStacked: Boolean get() = sourceCount > 1
    val preferredSourceCount: Int get() = articles.count { it.isPreferredSource }
    
    // Pick primary article: preferred source first, otherwise newest
    val primaryArticle: ArticleEntity
        get() = articles.firstOrNull { it.isPreferredSource } ?: articles.first()
}

object StoryClusterer {

    private val stopWords = setOf(
        "a", "an", "the", "in", "on", "at", "to", "for", "of", "with", "by", "from",
        "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
        "it", "its", "this", "that", "these", "those", "how", "what", "why", "who",
        "new", "says", "report", "first", "after", "over", "about", "more"
    )

    fun clusterArticles(articles: List<ArticleEntity>): List<StoryCluster> {
        if (articles.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<ArticleEntity>>()

        for (article in articles) {
            val articleWords = extractSignificantWords(article.title)
            var matchedCluster: MutableList<ArticleEntity>? = null

            for (cluster in clusters) {
                // Check against existing articles in cluster
                val representative = cluster.first()
                val repWords = extractSignificantWords(representative.title)
                
                val similarity = calculateJaccardSimilarity(articleWords, repWords)
                if (similarity >= 0.42) { // 42%+ word overlap in significant title keywords = same story
                    matchedCluster = cluster
                    break
                }
            }

            if (matchedCluster != null) {
                matchedCluster.add(article)
            } else {
                clusters.add(mutableListOf(article))
            }
        }

        return clusters.mapIndexed { index, clusterArticles ->
            // Sort cluster articles: preferred feed first, then newest timestamp
            val sortedArticles = clusterArticles.sortedWith(
                compareByDescending<ArticleEntity> { it.isPreferredSource }
                    .thenByDescending { it.pubDateTimestamp }
            )

            val primary = sortedArticles.first()
            val maxTimestamp = sortedArticles.maxOf { it.pubDateTimestamp }
            val hasDeal = sortedArticles.any { it.isDeal }
            val hasBookmark = sortedArticles.any { it.isBookmarked }

            StoryCluster(
                clusterId = "cluster_${index}_${primary.id.hashCode()}",
                primaryTitle = primary.title,
                category = primary.category,
                isDeal = hasDeal,
                isBookmarked = hasBookmark,
                latestTimestamp = maxTimestamp,
                articles = sortedArticles
            )
        }.sortedByDescending { it.latestTimestamp }
    }

    private fun extractSignificantWords(title: String): Set<String> {
        return title.lowercase(Locale.ROOT)
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { word -> word.length > 2 && !stopWords.contains(word) }
            .toSet()
    }

    private fun calculateJaccardSimilarity(set1: Set<String>, set2: Set<String>): Double {
        if (set1.isEmpty() || set2.isEmpty()) return 0.0
        val intersection = set1.intersect(set2).size.toDouble()
        val union = set1.union(set2).size.toDouble()
        return intersection / union
    }
}
