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
    val author: String = "",
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false,
    val isDeal: Boolean = false,
    val isPreferredSource: Boolean = false,
    val isSavedForOffline: Boolean = false,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val isArchived: Boolean = false,
    val isDiscoveredRecommendation: Boolean = false,
    val storyClusterHash: String = "", // Normalized hash for stacking identical stories
    val subcategory: String = "", // Auto-analyzed subcategory for smart recommendations
    val mediaUrl: String? = null, // Audio or Video media file URL (MP3, MP4, M4A, YouTube, etc.)
    val mediaType: String? = null, // "AUDIO", "VIDEO"
    val isPodcast: Boolean = false, // Flag indicating podcast audio/video episode
    val isVideoPodcast: Boolean = false, // Flag indicating video podcast
    val duration: String? = null, // Episode duration (e.g., "45:12")
    val isDecoupled: Boolean = false // User manually decoupled/unstacked this story from cluster
)
