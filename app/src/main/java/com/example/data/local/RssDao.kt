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

    @Query("SELECT * FROM feeds WHERE url = :url LIMIT 1")
    suspend fun getFeedByUrl(url: String): FeedEntity?

    @Query("SELECT * FROM feeds")
    suspend fun getAllFeedsList(): List<FeedEntity>

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

    @Query("UPDATE feeds SET isPreferred = :isPreferred, preferredScope = :preferredScope WHERE url = :url")
    suspend fun updateFeedPreferredWithScope(url: String, isPreferred: Boolean, preferredScope: String)

    @Query("UPDATE feeds SET preferredScope = :preferredScope WHERE url = :url")
    suspend fun updateFeedPreferredScope(url: String, preferredScope: String)

    @Query("UPDATE feeds SET isEnabled = :isEnabled WHERE url = :url")
    suspend fun updateFeedEnabled(url: String, isEnabled: Boolean)

    @Query("UPDATE feeds SET category = :category WHERE url = :url")
    suspend fun updateFeedCategory(url: String, category: String)

    @Query("UPDATE feeds SET title = :title, category = :category WHERE url = :url")
    suspend fun updateFeedDetails(url: String, title: String, category: String)

    @Query("UPDATE feeds SET title = :title, category = :category, isPreferred = :isPreferred, preferredScope = :preferredScope WHERE url = :url")
    suspend fun updateFeedDetailsWithScope(url: String, title: String, category: String, isPreferred: Boolean, preferredScope: String)

    @Query("UPDATE feeds SET iconUrl = :iconUrl WHERE url = :url")
    suspend fun updateFeedIconUrl(url: String, iconUrl: String)

    @Query("UPDATE articles SET feedUrl = :newUrl WHERE feedUrl = :oldUrl")
    suspend fun updateArticlesFeedUrl(oldUrl: String, newUrl: String)

    @Query("UPDATE articles SET category = :category, isPodcast = :isPodcast WHERE feedUrl = :feedUrl")
    suspend fun updateArticlesCategoryByFeedUrl(feedUrl: String, category: String, isPodcast: Boolean = true)

    @Query("UPDATE articles SET subcategory = :subcategory WHERE feedUrl = :feedUrl")
    suspend fun updateArticlesSubcategoryByFeedUrl(feedUrl: String, subcategory: String)

    @Query("UPDATE articles SET category = :category, subcategory = :subcategory, isPodcast = :isPodcast WHERE feedUrl = :feedUrl")
    suspend fun updateArticlesCategoryAndSubcategoryByFeedUrl(feedUrl: String, category: String, subcategory: String, isPodcast: Boolean = true)

    @Query("UPDATE articles SET category = :category, isPodcast = :isPodcast WHERE id = :id")
    suspend fun updateArticleCategoryById(id: String, category: String, isPodcast: Boolean = true)

    @Query("UPDATE articles SET subcategory = :subcategory WHERE id = :id")
    suspend fun updateArticleSubcategoryById(id: String, subcategory: String)

    @Query("DELETE FROM feeds WHERE url = :url")
    suspend fun deleteFeedByUrl(url: String)

    // ARTICLES
    @Query("SELECT * FROM articles ORDER BY pubDateTimestamp DESC")
    fun getAllArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles")
    suspend fun getAllArticlesList(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY pubDateTimestamp DESC")
    fun getBookmarkedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isSavedForOffline = 1 ORDER BY pubDateTimestamp DESC")
    fun getOfflineArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isRead = 1 ORDER BY pubDateTimestamp DESC")
    fun getReadArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isLiked = 1 ORDER BY pubDateTimestamp DESC")
    fun getLikedArticlesFlow(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isLiked = 1")
    suspend fun getLikedArticles(): List<ArticleEntity>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Query("SELECT * FROM articles WHERE link = :link LIMIT 1")
    suspend fun getArticleByLink(link: String): ArticleEntity?

    @Query("SELECT * FROM articles ORDER BY pubDateTimestamp DESC LIMIT 1")
    suspend fun getLatestArticle(): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: String, isBookmarked: Boolean)

    @Query("UPDATE articles SET imageUrl = :imageUrl WHERE id = :id")
    suspend fun updateArticleImageUrl(id: String, imageUrl: String?)

    @Query("UPDATE articles SET isSavedForOffline = :isSavedForOffline WHERE id = :id")
    suspend fun updateOfflineStatus(id: String, isSavedForOffline: Boolean)

    @Query("UPDATE articles SET isLiked = :isLiked WHERE id = :id")
    suspend fun updateLikedStatus(id: String, isLiked: Boolean)

    @Query("UPDATE articles SET isDisliked = :isDisliked WHERE id = :id")
    suspend fun updateDislikedStatus(id: String, isDisliked: Boolean)

    @Query("UPDATE articles SET isArchived = :isArchived WHERE id = :id")
    suspend fun updateArchivedStatus(id: String, isArchived: Boolean)

    @Query("UPDATE articles SET isArchived = :isArchived WHERE storyClusterHash = :hash AND storyClusterHash != ''")
    suspend fun updateClusterArchivedStatus(hash: String, isArchived: Boolean)

    @Query("UPDATE articles SET isDecoupled = :isDecoupled WHERE id = :id")
    suspend fun updateDecoupledStatus(id: String, isDecoupled: Boolean)

    @Query("UPDATE articles SET isDecoupled = :isDecoupled WHERE id IN (:ids)")
    suspend fun updateDecoupledStatusForIds(ids: List<String>, isDecoupled: Boolean)

    @Query("SELECT * FROM articles WHERE author LIKE '%' || :authorQuery || '%'")
    suspend fun getArticlesByAuthor(authorQuery: String): List<ArticleEntity>

    @Query("UPDATE articles SET isDiscoveredRecommendation = 1 WHERE id = :id")
    suspend fun markAsDiscoveredRecommendation(id: String)

    @Query("UPDATE articles SET isRead = 1 WHERE id = :id")
    suspend fun markArticleAsRead(id: String)

    @Query("UPDATE articles SET isRead = :isRead WHERE id = :id")
    suspend fun setArticleReadState(id: String, isRead: Boolean)

    @Query("UPDATE articles SET isRead = 0")
    suspend fun clearReadHistory()

    @Query("DELETE FROM articles WHERE feedUrl = :feedUrl AND isBookmarked = 0 AND isSavedForOffline = 0 AND isLiked = 0")
    suspend fun deleteNonSavedArticlesByFeedUrl(feedUrl: String)

    @Query("DELETE FROM articles WHERE isBookmarked = 0 AND isSavedForOffline = 0 AND isLiked = 0 AND isRead = 0")
    suspend fun clearNonBookmarkedArticles()

    @Query("SELECT COUNT(*) FROM articles")
    suspend fun getArticleCount(): Int
}
