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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RssViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = RssRepository(application, db.rssDao())

    val selectedCategory = MutableStateFlow("ALL")
    val selectedTimeRange = MutableStateFlow(com.example.data.model.TimeRangeFilter.ALL_TIME)
    val randomOrderSeed = MutableStateFlow(System.currentTimeMillis())
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

    val availableCategories: StateFlow<List<String>> = repository.feeds.map { feeds ->
        val userCategories = feeds.map { it.category.uppercase().trim() }.filter { it.isNotBlank() }
        val defaultCats = DefaultFeedCatalog.categories
        val combined = (listOf("ALL") + (defaultCats + userCategories).distinct().filter { it != "ALL" })
        combined.sortedWith(Comparator { a, b ->
            if (a == "ALL") -1
            else if (b == "ALL") 1
            else a.compareTo(b)
        })
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DefaultFeedCatalog.categories
    )

    private val _selectedClusterForSources = MutableStateFlow<StoryCluster?>(null)
    val selectedClusterForSources: StateFlow<StoryCluster?> = _selectedClusterForSources.asStateFlow()

    private val _selectedArticleForReading = MutableStateFlow<ArticleEntity?>(null)
    val selectedArticleForReading: StateFlow<ArticleEntity?> = _selectedArticleForReading.asStateFlow()

    private val filterState = combine(
        selectedCategory,
        selectedTimeRange,
        hideDeals,
        onlyPreferredFeeds,
        onlyBookmarks
    ) { category, timeRange, deals, preferred, bookmarks ->
        FilterState(category, timeRange, deals, preferred, bookmarks)
    }

    // Active story stream dynamically combining filters & search
    @OptIn(ExperimentalCoroutinesApi::class)
    val storyClusters: StateFlow<List<StoryCluster>> = combine(filterState, randomOrderSeed) { filters, seed ->
        Pair(filters, seed)
    }.flatMapLatest { (filters, seed) ->
        repository.getClusteredFeedStream(
            categoryFilter = filters.category,
            timeRangeFilter = filters.timeRange,
            hideDeals = filters.deals,
            onlyPreferredFeeds = filters.preferred,
            onlyBookmarks = filters.bookmarks,
            randomSeed = seed
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

    fun setTimeRange(timeRange: com.example.data.model.TimeRangeFilter) {
        selectedTimeRange.value = timeRange
    }

    fun reorderSemiRandom() {
        randomOrderSeed.value = System.currentTimeMillis()
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

    fun updateFeedCategory(feedUrl: String, newCategory: String) {
        viewModelScope.launch {
            repository.updateFeedCategory(feedUrl, newCategory)
        }
    }

    fun updateFeedDetails(
        oldFeed: FeedEntity,
        newTitle: String,
        newUrl: String,
        newCategory: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val success = repository.updateFeedDetails(oldFeed, newTitle, newUrl, newCategory)
            if (success) {
                refreshNews()
            }
            onResult(success)
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
                refreshNews()
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

private data class FilterState(
    val category: String,
    val timeRange: com.example.data.model.TimeRangeFilter,
    val deals: Boolean,
    val preferred: Boolean,
    val bookmarks: Boolean
)
