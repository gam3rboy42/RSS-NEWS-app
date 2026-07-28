package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RssDao {
    // FEEDS
    @Query("SELECT * FROM feeds ORDER BY isPreferred DESC, title ASC")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE isEnabled = 1")
    suspend fun getEnabledFeeds(): List<FeedEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultFeeds(feeds: List<FeedEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeds(feeds: List<FeedEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: FeedEntity)

    @Query("UPDATE feeds SET isPreferred = :isPreferred WHERE url = :url")
    suspend fun updateFeedPreferred(url: String, isPreferred: Boolean)

    @Query("UPDATE feeds SET isEnabled = :isEnabled WHERE url = :url")
    suspend fun updateFeedEnabled(url: String, isEnabled: Boolean)

    @Query("UPDATE feeds SET category = :category WHERE url = :url")
    suspend fun updateFeedCategory(url: String, category: String)

    @Query("UPDATE feeds SET title = :title, category = :category WHERE url = :url")
    suspend fun updateFeedDetails(url: String, title: String, category: String)

    @Query("UPDATE articles SET feedUrl = :newUrl WHERE feedUrl = :oldUrl")
    suspend fun updateArticlesFeedUrl(oldUrl: String, newUrl: String)

    @Query("DELETE FROM feeds WHERE url = :url")
    suspend fun deleteFeedByUrl(url: String)

    // ARTICLES
    @Query("SELECT * FROM articles ORDER BY pubDateTimestamp DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY pubDateTimestamp DESC")
    fun getBookmarkedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: String, isBookmarked: Boolean)

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markArticleAsRead(id: String)

    @Query("DELETE FROM articles WHERE isBookmarked = 0")
    suspend fun clearNonBookmarkedArticles()

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun getArticleCount(): Int
}
