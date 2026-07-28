package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey
    val id: String, // Article link or guid
    val feedUrl: String,
    val feedTitle: String,
    val category: String,
    val title: String,
    val description: String,
    val content: String = "",
    val link: String,
    val pubDate: String = "",
    val pubDateTimestamp: Long = System.currentTimeMillis(),
    val imageUrl: String? = null,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val isDeal: Boolean = false,
    val isPreferredSource: Boolean = false,
    val storyClusterHash: String = "" // Normalized hash for stacking identical stories
)
