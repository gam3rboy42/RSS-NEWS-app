package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flowOn
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

    private val sharedPrefs = application.getSharedPreferences("rss_app_prefs", android.content.Context.MODE_PRIVATE)

    val selectedCategory = MutableStateFlow("ALL")
    val selectedSubcategory = MutableStateFlow("ALL")
    val selectedTimeRange = MutableStateFlow(com.example.data.model.TimeRangeFilter.ONE_WEEK)
    val randomOrderSeed = MutableStateFlow(System.currentTimeMillis())
    val hideDeals = MutableStateFlow(true) // Default to deals hidden
    val onlyPreferredFeeds = MutableStateFlow(false)
    val onlyBookmarks = MutableStateFlow(false)
    val onlyOffline = MutableStateFlow(false)
    val savedSubMenuFilter = MutableStateFlow("ALL") // "ALL", "ARTICLES", "PODCASTS", "DOWNLOADED"
    val onlyRecommendations = MutableStateFlow(false)
    val onlyReadHistory = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val fontSizeMultiplier = MutableStateFlow(1.0f) // 0.8f, 1.0f, 1.2f, 1.4f

    // Preferred Games for GAMING Category
    val preferredGames = MutableStateFlow<List<String>>(loadPreferredGames())
    val onlyPreferredGames = MutableStateFlow(false)

    // Podcast Filter ("ALL", "AUDIO", "VIDEO")
    val podcastTypeFilter = MutableStateFlow("ALL")

    // Podcast Categories (Built-in + User Defined)
    val selectedPodcastCategory = MutableStateFlow("ALL")
    val customPodcastCategories = MutableStateFlow<List<String>>(emptyList())

    private fun loadCustomPodcastCategories(): List<String> {
        val prefs = getApplication<Application>().getSharedPreferences("podcast_categories_prefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet("custom_categories", null)
        val defaultList = listOf(
            "ALL", "TECH & AI", "NEWS & POLITICS", "TRUE CRIME", "SCIENCE",
            "BUSINESS & FINANCE", "COMEDY", "GAMING & GEEK", "CULTURE & HISTORY",
            "HEALTH & FITNESS", "AUDIOBOOKS"
        )
        if (savedSet.isNullOrEmpty()) {
            return defaultList
        }
        return (defaultList + savedSet).distinct()
    }

    fun addCustomPodcastCategory(categoryName: String) {
        val trimmed = categoryName.trim().uppercase()
        if (trimmed.isBlank()) return
        val current = customPodcastCategories.value.toMutableList()
        if (!current.contains(trimmed)) {
            current.add(trimmed)
            customPodcastCategories.value = current
            saveCustomPodcastCategories(current)
        }
        setSelectedPodcastCategory(trimmed)
    }

    fun removeCustomPodcastCategory(categoryName: String) {
        val trimmed = categoryName.trim().uppercase()
        if (trimmed == "ALL") return
        val current = customPodcastCategories.value.toMutableList()
        current.remove(trimmed)
        customPodcastCategories.value = current
        saveCustomPodcastCategories(current)
        if (selectedPodcastCategory.value == trimmed) {
            setSelectedPodcastCategory("ALL")
        }
    }

    private fun saveCustomPodcastCategories(categories: List<String>) {
        val prefs = getApplication<Application>().getSharedPreferences("podcast_categories_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("custom_categories", categories.toSet()).apply()
    }

    fun setSelectedPodcastCategory(category: String) {
        selectedPodcastCategory.value = category
    }

    // Dislike & Muted Topics State
    val lastDislikeAnalysis = MutableStateFlow<com.example.data.model.DislikeAnalysisResult?>(null)

    private val _lastArchivedCluster = MutableStateFlow<StoryCluster?>(null)
    val lastArchivedCluster: StateFlow<StoryCluster?> = _lastArchivedCluster.asStateFlow()

    fun archiveCluster(cluster: StoryCluster) {
        viewModelScope.launch {
            _lastArchivedCluster.value = cluster
            repository.archiveCluster(cluster)
        }
    }

    fun undoArchiveLastCluster() {
        val cluster = _lastArchivedCluster.value ?: return
        viewModelScope.launch {
            repository.unarchiveCluster(cluster)
            _lastArchivedCluster.value = null
        }
    }

    fun clearLastArchivedCluster() {
        _lastArchivedCluster.value = null
    }

    fun decoupleArticle(articleId: String) {
        viewModelScope.launch {
            repository.decoupleArticle(articleId)
        }
    }

    fun decoupleCluster(cluster: StoryCluster) {
        viewModelScope.launch {
            repository.decoupleCluster(cluster)
        }
    }
    val mutedKeywords = MutableStateFlow<Set<String>>(repository.getMutedKeywords())
    val mutedAuthors = MutableStateFlow<Set<String>>(repository.getMutedAuthors())
    val mutedSubcategories = MutableStateFlow<Set<String>>(repository.getMutedSubcategories())

    // Topic RSS Search
    val topicSearchQuery = MutableStateFlow("")
    val topicSearchResults = MutableStateFlow<List<com.example.data.model.FeedDiscoveryItem>>(emptyList())
    val isSearchingTopics = MutableStateFlow(false)

    val backgroundRefreshIntervalMinutes = MutableStateFlow(
        com.example.worker.WorkScheduler.getRefreshIntervalMinutes(application)
    )

    init {
        // Ensure background worker is scheduled if enabled
        val interval = backgroundRefreshIntervalMinutes.value
        if (interval > 0) {
            com.example.worker.WorkScheduler.scheduleBackgroundWork(application, interval)
        }
    }

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
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DefaultFeedCatalog.categories
    )

    val availableSubcategories: StateFlow<List<String>> = combine(selectedCategory, repository.rawArticles) { category, rawArticles ->
        val definedSubcats = com.example.data.model.SubcategoryAnalyzer.getAllSubcategoriesForCategory(category)
        val articlesInCategory = if (category == "ALL") rawArticles else rawArticles.filter { it.category.equals(category, ignoreCase = true) }
        val activeInArticles = articlesInCategory.map { art ->
            art.subcategory.ifBlank { com.example.data.model.SubcategoryAnalyzer.analyze(art.title, art.description, art.category) }
        }.filter { it.isNotBlank() && it != "GENERAL" }.distinct()
        (definedSubcats + activeInArticles).distinct()
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("ALL")
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
        FilterState(category, timeRange, deals, preferred, bookmarks, false, false, false)
    }.combine(onlyOffline) { state, offline ->
        state.copy(offline = offline)
    }.combine(onlyRecommendations) { state, rec ->
        state.copy(recommendations = rec)
    }.combine(onlyReadHistory) { state, readHist ->
        state.copy(readHistory = readHist)
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
            onlyOffline = filters.offline,
            onlyRecommendations = filters.recommendations,
            onlyReadHistory = filters.readHistory,
            randomSeed = seed
        )
    }.combine(selectedSubcategory) { clusters, subcat ->
        if (subcat == "ALL" || subcat.isBlank()) {
            clusters
        } else {
            clusters.filter { cluster ->
                cluster.subcategory.equals(subcat, ignoreCase = true) ||
                cluster.articles.any { art -> art.subcategory.equals(subcat, ignoreCase = true) }
            }
        }
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
    }.combine(onlyPreferredGames) { clusters, preferredOnly ->
        if (!preferredOnly || selectedCategory.value != "GAMING" || preferredGames.value.isEmpty()) {
            clusters
        } else {
            val gameKeywords = preferredGames.value.map { it.lowercase().trim() }
            clusters.filter { cluster ->
                val titleLower = cluster.primaryTitle.lowercase()
                val descLower = cluster.primaryArticle.description.lowercase()
                gameKeywords.any { kw -> titleLower.contains(kw) || descLower.contains(kw) }
            }
        }
    }.combine(podcastTypeFilter) { clusters, podcastFilter ->
        if (selectedCategory.value != "PODCASTS" || podcastFilter == "ALL") {
            clusters
        } else if (podcastFilter == "VIDEO") {
            clusters.filter { cluster ->
                cluster.articles.any { it.isVideoPodcast || it.mediaType == "VIDEO" }
            }
        } else if (podcastFilter == "AUDIO") {
            clusters.filter { cluster ->
                cluster.articles.any { it.isPodcast && !it.isVideoPodcast && it.mediaType != "VIDEO" }
            }
        } else if (podcastFilter == "DOWNLOADED") {
            val appCtx = getApplication<Application>()
            clusters.filter { cluster ->
                cluster.articles.any { art ->
                    val isPod = art.isPodcast || art.isVideoPodcast || art.mediaType == "AUDIO" || art.mediaType == "VIDEO" || art.category.equals("PODCASTS", ignoreCase = true)
                    isPod && (art.isSavedForOffline || com.example.util.PodcastCacheManager.isArticleCached(appCtx, art.id))
                }
            }
        } else {
            clusters
        }
    }.combine(selectedPodcastCategory) { clusters, podcastCat ->
        if (selectedCategory.value != "PODCASTS" || podcastCat == "ALL") {
            clusters
        } else {
            val catUpper = podcastCat.uppercase().trim()
            val catKeywords = when (catUpper) {
                "TECH & AI" -> listOf("tech", "ai", "code", "software", "google", "apple", "developer", "hardware", "cyber", "data", "robot")
                "NEWS & POLITICS" -> listOf("news", "politics", "world", "daily", "report", "briefing", "state", "global", "policy", "senate")
                "TRUE CRIME" -> listOf("crime", "murder", "mystery", "investigation", "detective", "serial", "case", "court", "victim")
                "SCIENCE" -> listOf("science", "space", "physics", "biology", "nature", "huberman", "lab", "research", "nasa", "genetics")
                "BUSINESS & FINANCE" -> listOf("business", "finance", "money", "investing", "crypto", "market", "startup", "economy", "trade", "ceo")
                "COMEDY" -> listOf("comedy", "funny", "humor", "joke", "standup", "banter", "laugh", "parody")
                "GAMING & GEEK" -> listOf("game", "gaming", "ign", "kotaku", "nintendo", "playstation", "xbox", "movie", "anime", "marvel")
                "CULTURE & HISTORY" -> listOf("history", "culture", "art", "music", "philosophy", "story", "book", "society", "museum")
                "HEALTH & FITNESS" -> listOf("health", "fitness", "workout", "mind", "meditation", "diet", "doctor", "wellness", "sleep")
                "AUDIOBOOKS" -> listOf("audiobook", "fiction", "novel", "read", "chapter", "story", "book")
                else -> listOf(catUpper.lowercase())
            }
            clusters.filter { cluster ->
                cluster.articles.any { article ->
                    val artCat = article.category.uppercase()
                    val feedTitle = article.feedTitle.lowercase()
                    val artTitle = article.title.lowercase()
                    val artDesc = article.description.lowercase()

                    artCat.contains(catUpper) ||
                            catKeywords.any { kw -> feedTitle.contains(kw) || artTitle.contains(kw) || artDesc.contains(kw) }
                }
            }
        }
    }.combine(savedSubMenuFilter) { clusters, savedFilter ->
        if (!onlyBookmarks.value || savedFilter == "ALL") {
            clusters
        } else {
            val appCtx = getApplication<Application>()
            clusters.filter { cluster ->
                cluster.articles.any { art ->
                    val isPod = art.isPodcast || art.isVideoPodcast || art.mediaType == "AUDIO" || art.mediaType == "VIDEO" || art.category.equals("PODCASTS", ignoreCase = true)
                    when (savedFilter) {
                        "ARTICLES" -> !isPod
                        "PODCASTS" -> isPod
                        "DOWNLOADED" -> isPod && (art.isSavedForOffline || com.example.util.PodcastCacheManager.isArticleCached(appCtx, art.id))
                        else -> true
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        customPodcastCategories.value = loadCustomPodcastCategories()
        viewModelScope.launch {
            repository.initializeDefaultsIfNeeded()
            checkOnlineStatus()
            val articleCount = repository.getArticleCount()
            if (articleCount == 0) {
                refreshNews()
            }
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
        selectedSubcategory.value = "ALL"
    }

    fun setSubcategory(subcat: String) {
        selectedSubcategory.value = subcat
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

    fun toggleOnlyOffline() {
        onlyOffline.value = !onlyOffline.value
    }

    fun toggleOnlyRecommendations() {
        onlyRecommendations.value = !onlyRecommendations.value
    }

    fun toggleOnlyReadHistory() {
        onlyReadHistory.value = !onlyReadHistory.value
    }

    fun clearReadHistory() {
        viewModelScope.launch {
            repository.clearReadHistory()
        }
    }

    fun toggleBookmark(articleId: String, currentStatus: Boolean) {
        val current = _selectedArticleForReading.value
        if (current?.id == articleId) {
            _selectedArticleForReading.value = current.copy(isBookmarked = !currentStatus)
        }
        viewModelScope.launch {
            repository.toggleBookmark(articleId, currentStatus)
        }
    }

    fun setSavedSubMenuFilter(filter: String) {
        savedSubMenuFilter.value = filter
    }

    fun toggleOfflineStatus(articleId: String, currentSaved: Boolean) {
        val current = _selectedArticleForReading.value
        if (current?.id == articleId) {
            _selectedArticleForReading.value = current.copy(isSavedForOffline = !currentSaved)
        }
        viewModelScope.launch {
            if (currentSaved) {
                repository.toggleOfflineStatus(articleId, true)
                com.example.util.PodcastCacheManager.getCachedFile(getApplication(), articleId)?.delete()
                refreshPodcastStorageStats()
            } else {
                val article = repository.getArticleById(articleId)
                if (article != null) {
                    val isPod = article.isPodcast || article.isVideoPodcast || article.mediaType == "AUDIO" || article.mediaType == "VIDEO" || article.category.equals("PODCASTS", ignoreCase = true)
                    if (isPod) {
                        val file = com.example.util.PodcastCacheManager.downloadPodcastAudio(getApplication(), article)
                        if (file != null) {
                            repository.toggleOfflineStatus(articleId, false)
                            com.example.util.PodcastCacheManager.autoPruneIfNecessary(getApplication(), db.rssDao())
                            refreshPodcastStorageStats()
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(getApplication(), "Podcast downloaded & verified for offline listening!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(getApplication(), "Download failed: Invalid audio file or protected stream", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        repository.toggleOfflineStatus(articleId, false)
                    }
                } else {
                    repository.toggleOfflineStatus(articleId, false)
                }
            }
        }
    }

    fun toggleLikedStatus(articleId: String, currentLiked: Boolean) {
        val current = _selectedArticleForReading.value
        if (current?.id == articleId) {
            _selectedArticleForReading.value = current.copy(isLiked = !currentLiked)
        }
        viewModelScope.launch {
            repository.toggleLikedStatus(articleId, currentLiked)
        }
    }

    fun subscribeDiscoveredFeed(feedUrl: String, feedTitle: String, category: String) {
        viewModelScope.launch {
            repository.addCustomFeed(feedUrl, feedTitle, category)
            refreshNews()
        }
    }

    fun markArticleRead(articleId: String) {
        viewModelScope.launch {
            repository.markArticleRead(articleId)
        }
    }

    fun toggleDislikedStatus(articleId: String, currentDisliked: Boolean) {
        viewModelScope.launch {
            val analysis = repository.toggleDislikedStatus(articleId, currentDisliked)
            if (analysis != null) {
                lastDislikeAnalysis.value = analysis
                refreshMutedState()
            }
        }
    }

    fun dislikeCluster(cluster: StoryCluster) {
        viewModelScope.launch {
            cluster.articles.forEach { article ->
                val analysis = repository.toggleDislikedStatus(article.id, false)
                if (analysis != null) {
                    lastDislikeAnalysis.value = analysis
                }
            }
            refreshMutedState()
        }
    }

    fun dismissDislikeAnalysis() {
        lastDislikeAnalysis.value = null
    }

    fun applyMutingPreferences(
        keywordsToMute: Set<String>,
        muteAuthor: String?,
        muteSubcategory: String?,
        muteFeedUrl: String?
    ) {
        viewModelScope.launch {
            if (keywordsToMute.isNotEmpty()) {
                repository.muteKeywords(keywordsToMute)
            }
            if (!muteAuthor.isNullOrBlank()) {
                repository.muteAuthor(muteAuthor)
            }
            if (!muteSubcategory.isNullOrBlank()) {
                repository.muteSubcategory(muteSubcategory)
            }
            if (!muteFeedUrl.isNullOrBlank()) {
                repository.muteFeed(muteFeedUrl)
            }
            refreshMutedState()
            lastDislikeAnalysis.value = null
        }
    }

    fun unmuteKeyword(keyword: String) {
        repository.unmuteKeyword(keyword)
        refreshMutedState()
    }

    fun unmuteAuthor(author: String) {
        repository.unmuteAuthor(author)
        refreshMutedState()
    }

    fun unmuteSubcategory(subcat: String) {
        repository.unmuteSubcategory(subcat)
        refreshMutedState()
    }

    private fun refreshMutedState() {
        mutedKeywords.value = repository.getMutedKeywords()
        mutedAuthors.value = repository.getMutedAuthors()
        mutedSubcategories.value = repository.getMutedSubcategories()
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

    fun openArticleFromIntent(articleId: String?, articleUrl: String?) {
        viewModelScope.launch {
            var article: ArticleEntity? = null
            if (!articleId.isNullOrBlank()) {
                article = repository.getArticleById(articleId)
            }
            if (article == null && !articleUrl.isNullOrBlank()) {
                article = repository.getArticleByLink(articleUrl)
            }
            if (article != null) {
                openArticleReader(article)
            }
        }
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

    fun autoTagAllFeedsOnDevice(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.autoTagAllFeedsOnDevice()
            onResult(count)
        }
    }

    fun detectCategoryForFeed(feedUrl: String, feedTitle: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val detected = repository.detectCategoryForFeed(feedUrl, feedTitle)
            onResult(detected)
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

    fun setBackgroundRefreshInterval(minutes: Long) {
        backgroundRefreshIntervalMinutes.value = minutes
        com.example.worker.WorkScheduler.setRefreshInterval(getApplication(), minutes)
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val sampleArticle = repository.getSampleArticleForNotification()
            if (sampleArticle != null) {
                val reason = if (sampleArticle.isLiked || sampleArticle.isPreferredSource) {
                    "Suggested story based on your preferences!"
                } else if (sampleArticle.author.isNotBlank()) {
                    "New article by author ${sampleArticle.author}"
                } else {
                    "Fresh headline from ${sampleArticle.feedTitle.ifBlank { "RSS Feed" }}"
                }

                com.example.util.NotificationHelper.showArticleNotification(
                    context = getApplication(),
                    article = sampleArticle,
                    reasonText = reason
                )
            }
        }
    }

    fun sendTestPodcastNotification() {
        viewModelScope.launch {
            val samplePodcast = repository.getSamplePodcastForNotification()
            if (samplePodcast != null) {
                val reason = "New episode on preferred podcast: ${samplePodcast.feedTitle.ifBlank { "Tech Podcasts" }}"
                com.example.util.NotificationHelper.showPodcastNotification(
                    context = getApplication(),
                    article = samplePodcast,
                    reasonText = reason
                )
            } else {
                val fallback = com.example.data.local.ArticleEntity(
                    id = "sample_podcast_notif_1",
                    title = "The Daily Tech Briefing - New Episode #402",
                    link = "https://example.com/podcast/402",
                    description = "Special episode discussing the latest developments in AI and mobile technology.",
                    pubDate = "Today",
                    pubDateTimestamp = System.currentTimeMillis(),
                    feedTitle = "The Daily Tech Briefing",
                    feedUrl = "https://example.com/podcast.xml",
                    category = "PODCASTS",
                    imageUrl = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?q=80&w=800",
                    author = "Tech Daily Team",
                    isPodcast = true,
                    mediaUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    mediaType = "AUDIO",
                    isPreferredSource = true
                )
                com.example.util.NotificationHelper.showPodcastNotification(
                    context = getApplication(),
                    article = fallback,
                    reasonText = "New episode on preferred podcast: The Daily Tech Briefing"
                )
            }
        }
    }

    // Preferred Games Helper Methods
    private fun loadPreferredGames(): List<String> {
        val raw = sharedPrefs.getString("pref_games_list", null)
        if (raw.isNullOrEmpty()) {
            return listOf("Genshin Impact", "Cyberpunk 2077", "Elden Ring", "Zelda", "GTA VI", "Valorant", "Minecraft")
        }
        return raw.split("|").filter { it.isNotBlank() }
    }

    private fun savePreferredGames(games: List<String>) {
        val joined = games.distinct().joinToString("|")
        sharedPrefs.edit().putString("pref_games_list", joined).apply()
        preferredGames.value = games.distinct()
    }

    fun addPreferredGame(game: String) {
        val clean = game.trim()
        if (clean.isBlank()) return
        val current = preferredGames.value.toMutableList()
        if (!current.any { it.equals(clean, ignoreCase = true) }) {
            current.add(clean)
            savePreferredGames(current)
        }
    }

    fun removePreferredGame(game: String) {
        val current = preferredGames.value.filter { !it.equals(game.trim(), ignoreCase = true) }
        savePreferredGames(current)
    }

    fun toggleOnlyPreferredGames() {
        onlyPreferredGames.value = !onlyPreferredGames.value
    }

    fun setPodcastTypeFilter(filter: String) {
        podcastTypeFilter.value = filter
    }

    // Topic RSS Search Method
    fun searchFeedsByTopic(topic: String) {
        topicSearchQuery.value = topic
        if (topic.isBlank()) {
            topicSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            isSearchingTopics.value = true
            val results = com.example.data.model.RssTopicSearchManager.searchFeedsForTopic(topic)
            topicSearchResults.value = results
            isSearchingTopics.value = false
        }
    }

    suspend fun exportOpml(): String {
        return repository.exportOpml()
    }

    suspend fun exportFeedsToJson(): String {
        return repository.exportFeedsToJson()
    }

    suspend fun importFeedsFromJson(json: String): com.example.util.JsonImportResult {
        val result = repository.importFeedsFromJson(json)
        if (result.importedFeedsCount > 0) {
            refreshNews()
        }
        return result
    }

    suspend fun importOpmlXml(xml: String): Int {
        val count = repository.importOpmlXml(xml)
        if (count > 0) {
            refreshNews()
        }
        return count
    }

    fun updateFeedIconUrl(feedUrl: String, iconUrl: String) {
        viewModelScope.launch {
            repository.updateFeedIconUrl(feedUrl, iconUrl)
        }
    }

    fun updateArticleImageUrl(articleId: String, imageUrl: String?) {
        viewModelScope.launch {
            repository.updateArticleImageUrl(articleId, imageUrl)
        }
    }

    suspend fun searchArtworkCandidates(query: String): List<com.example.util.PodcastArtworkCandidate> {
        return repository.searchArtworkCandidates(query)
    }

    fun autoGrabAndSetArticleThumbnail(article: ArticleEntity, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val grabbedUrl = repository.autoGrabPodcastArtwork(article.feedTitle, article.title, article.feedUrl)
            if (!grabbedUrl.isNullOrBlank()) {
                repository.updateArticleImageUrl(article.id, grabbedUrl)
                onComplete(grabbedUrl)
            } else {
                val feed = repository.getAllFeedsList().firstOrNull { it.url.equals(article.feedUrl, ignoreCase = true) }
                if (feed != null && feed.iconUrl.isNotBlank()) {
                    repository.updateArticleImageUrl(article.id, feed.iconUrl)
                    onComplete(feed.iconUrl)
                } else {
                    onComplete(null)
                }
            }
        }
    }

    fun autoGrabAndSetSeriesThumbnail(feedUrl: String, feedTitle: String, onComplete: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val grabbedUrl = repository.autoGrabPodcastArtwork(feedTitle, "", feedUrl)
            if (!grabbedUrl.isNullOrBlank()) {
                repository.updateFeedIconUrl(feedUrl, grabbedUrl)
                onComplete(grabbedUrl)
            } else {
                onComplete(null)
            }
        }
    }

    // --- SMART CACHE MANAGER ---
    val podcastStorageStats = MutableStateFlow<com.example.util.PodcastStorageStats?>(null)
    val autoDownloadWifi = MutableStateFlow(com.example.util.PodcastCacheManager.getAutoDownloadWifi(getApplication()))

    fun refreshPodcastStorageStats() {
        viewModelScope.launch {
            podcastStorageStats.value = com.example.util.PodcastCacheManager.getStorageStats(getApplication(), db.rssDao())
            autoDownloadWifi.value = com.example.util.PodcastCacheManager.getAutoDownloadWifi(getApplication())
        }
    }

    fun setAutoDownloadWifi(enabled: Boolean) {
        com.example.util.PodcastCacheManager.setAutoDownloadWifi(getApplication(), enabled)
        autoDownloadWifi.value = enabled
        refreshPodcastStorageStats()
    }

    fun setMaxStorageMb(mb: Int) {
        com.example.util.PodcastCacheManager.setMaxStorageMb(getApplication(), mb)
        refreshPodcastStorageStats()
    }

    fun setAutoPruneListened(enabled: Boolean) {
        com.example.util.PodcastCacheManager.setAutoPruneListened(getApplication(), enabled)
        refreshPodcastStorageStats()
    }

    fun setPruneAgeDays(days: Int) {
        com.example.util.PodcastCacheManager.setPruneAgeDays(getApplication(), days)
        refreshPodcastStorageStats()
    }

    fun runSmartPrune(onResult: (com.example.util.PruneResult) -> Unit) {
        viewModelScope.launch {
            val result = com.example.util.PodcastCacheManager.autoPruneIfNecessary(getApplication(), db.rssDao())
            refreshPodcastStorageStats()
            onResult(result)
        }
    }

    fun clearAllPodcastCache(onResult: (com.example.util.PruneResult) -> Unit) {
        viewModelScope.launch {
            val result = com.example.util.PodcastCacheManager.clearAllCache(getApplication())
            refreshPodcastStorageStats()
            onResult(result)
        }
    }

    fun downloadPodcastForOffline(
        article: ArticleEntity,
        onProgress: (Float) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val file = com.example.util.PodcastCacheManager.downloadPodcastAudio(getApplication(), article, onProgress)
            if (file != null) {
                repository.toggleOfflineStatus(article.id, false)
                com.example.util.PodcastCacheManager.autoPruneIfNecessary(getApplication(), db.rssDao())
                refreshPodcastStorageStats()
                onComplete(true)
            } else {
                onComplete(false)
            }
        }
    }
}

private data class FilterState(
    val category: String,
    val timeRange: com.example.data.model.TimeRangeFilter,
    val deals: Boolean,
    val preferred: Boolean,
    val bookmarks: Boolean,
    val offline: Boolean,
    val recommendations: Boolean,
    val readHistory: Boolean
)
