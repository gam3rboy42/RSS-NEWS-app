package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ArticleEntity
import com.example.data.local.FeedEntity
import com.example.data.model.DefaultFeedCatalog
import com.example.data.model.StoryCluster
import com.example.data.repository.RssRepository
import com.example.data.repository.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RssViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = RssRepository(application, db.rssDao())

    val selectedCategory = MutableStateFlow("ALL")
    val hideDeals = MutableStateFlow(true) // Filter out deal articles by default!
    val onlyPreferredFeeds = MutableStateFlow(false)
    val onlyBookmarks = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val fontSizeMultiplier = MutableStateFlow(1.0f) // 0.8f, 1.0f, 1.2f, 1.4f

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _isOnline = MutableStateFlow(repository.isDeviceOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    val allFeeds: StateFlow<List<FeedEntity>> = repository.feeds
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedClusterForSources = MutableStateFlow<StoryCluster?>(null)
    val selectedClusterForSources: StateFlow<StoryCluster?> = _selectedClusterForSources.asStateFlow()

    private val _selectedArticleForReading = MutableStateFlow<ArticleEntity?>(null)
    val selectedArticleForReading: StateFlow<ArticleEntity?> = _selectedArticleForReading.asStateFlow()

    // Active story stream dynamically combining filters & search
    @OptIn(ExperimentalCoroutinesApi::class)
    val storyClusters: StateFlow<List<StoryCluster>> = combine(
        selectedCategory,
        hideDeals,
        onlyPreferredFeeds,
        onlyBookmarks
    ) { category, deals, preferred, bookmarks ->
        Tuple4(category, deals, preferred, bookmarks)
    }.flatMapLatest { tuple ->
        repository.getClusteredFeedStream(
            categoryFilter = tuple.category,
            hideDeals = tuple.hideDeals,
            onlyPreferredFeeds = tuple.onlyPreferred,
            onlyBookmarks = tuple.onlyBookmarks
        )
    }.combine(searchQuery) { clusters, query ->
        if (query.isBlank()) {
            clusters
        } else {
            val q = query.lowercase().trim()
            clusters.filter { cluster ->
                cluster.primaryTitle.lowercase().contains(q) ||
                cluster.articles.any { art -> art.feedTitle.lowercase().contains(q) || art.description.lowercase().contains(q) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
            refreshNews()
        }
    }

    fun checkOnlineStatus() {
        _isOnline.value = repository.isDeviceOnline()
    }

    fun refreshNews() {
        checkOnlineStatus()
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            val result = repository.refreshFeeds()
            _syncState.value = result
        }
    }

    fun setCategory(category: String) {
        selectedCategory.value = category
    }

    fun toggleHideDeals() {
        hideDeals.value = !hideDeals.value
    }

    fun toggleOnlyPreferred() {
        onlyPreferredFeeds.value = !onlyPreferredFeeds.value
    }

    fun toggleOnlyBookmarks() {
        onlyBookmarks.value = !onlyBookmarks.value
    }

    fun toggleBookmark(articleId: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.toggleBookmark(articleId, currentStatus)
        }
    }

    fun markArticleRead(articleId: String) {
        viewModelScope.launch {
            repository.markArticleRead(articleId)
        }
    }

    fun openStorySourcesDialog(cluster: StoryCluster) {
        _selectedClusterForSources.value = cluster
    }

    fun closeStorySourcesDialog() {
        _selectedClusterForSources.value = null
    }

    fun openArticleReader(article: ArticleEntity) {
        markArticleRead(article.id)
        _selectedArticleForReading.value = article
    }

    fun closeArticleReader() {
        _selectedArticleForReading.value = null
    }

    fun toggleFeedPreferred(feedUrl: String, currentPreferred: Boolean) {
        viewModelScope.launch {
            repository.toggleFeedPreferred(feedUrl, currentPreferred)
        }
    }

    fun toggleFeedEnabled(feedUrl: String, currentEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleFeedEnabled(feedUrl, currentEnabled)
        }
    }

    fun addCustomFeedUrl(url: String, title: String, category: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            val success = repository.addCustomFeed(url, title, category)
            if (success) {
                _syncState.value = SyncState.Success(1)
            } else {
                _syncState.value = SyncState.Error("Invalid or unreachable RSS Feed URL")
            }
            onResult(success)
        }
    }

    fun deleteFeed(feedUrl: String) {
        viewModelScope.launch {
            repository.deleteFeed(feedUrl)
        }
    }

    fun changeFontSize(multiplier: Float) {
        fontSizeMultiplier.value = multiplier
    }
}

private data class Tuple4<A, B, C, D>(val category: A, val hideDeals: B, val onlyPreferred: C, val onlyBookmarks: D)
