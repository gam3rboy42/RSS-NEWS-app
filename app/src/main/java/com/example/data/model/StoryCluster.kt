package com.example.data.model

import com.example.data.local.ArticleEntity
import java.util.Locale
import kotlin.random.Random

enum class TimeRangeFilter(val label: String) {
    SAME_DAY("SAME DAY"),
    ONE_WEEK("1 WEEK"),
    ALL_TIME("ALL TIME")
}

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

    fun clusterArticles(
        articles: List<ArticleEntity>,
        timeRangeFilter: TimeRangeFilter = TimeRangeFilter.ALL_TIME,
        randomSeed: Long = 0L
    ): List<StoryCluster> {
        if (articles.isEmpty()) return emptyList()

        val currentTime = System.currentTimeMillis()
        val dayInMillis = 24 * 60 * 60 * 1000L
        val weekInMillis = 7 * dayInMillis

        // 1. Time range filter
        val timeFilteredArticles = articles.filter { article ->
            when (timeRangeFilter) {
                TimeRangeFilter.SAME_DAY -> (currentTime - article.pubDateTimestamp) <= dayInMillis
                TimeRangeFilter.ONE_WEEK -> (currentTime - article.pubDateTimestamp) <= weekInMillis
                TimeRangeFilter.ALL_TIME -> true
            }
        }

        if (timeFilteredArticles.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<ArticleEntity>>()

        for (article in timeFilteredArticles) {
            val articleWords = extractSignificantWords(article.title)
            var matchedCluster: MutableList<ArticleEntity>? = null

            for (cluster in clusters) {
                val representative = cluster.first()
                val repWords = extractSignificantWords(representative.title)
                
                val similarity = calculateJaccardSimilarity(articleWords, repWords)
                if (similarity >= 0.42) {
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

        val unSortedClusters = clusters.mapIndexed { index, clusterArticles ->
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
        }

        // 2. Semi-random ordering that prioritizes preferred sources
        return unSortedClusters.sortedByDescending { cluster ->
            val hasPreferred = cluster.preferredSourceCount > 0 || cluster.articles.any { it.isPreferredSource }
            val preferredBoost = if (hasPreferred) 10000.0 else 0.0
            val recencyScore = (cluster.latestTimestamp.toDouble() / (1000 * 60 * 60 * 24))
            
            // Deterministic pseudo-random variation based on seed and cluster identifier
            val clusterHash = cluster.clusterId.hashCode().toLong()
            val rng = Random(randomSeed xor clusterHash)
            val randomJitter = rng.nextDouble() * 50.0

            preferredBoost + recencyScore + randomJitter
        }
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
