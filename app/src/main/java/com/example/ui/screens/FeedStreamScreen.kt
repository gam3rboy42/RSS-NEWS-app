package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ArticleEntity
import com.example.data.model.StoryCluster
import com.example.data.repository.SyncState
import com.example.ui.components.ArticleCard
import com.example.ui.components.SwipeableArticleCard
import com.example.ui.components.CategoryPillBar
import com.example.ui.components.DislikeAnalysisDialog
import com.example.ui.components.NothingHeader
import com.example.ui.components.StoryStackDialog
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingSurfaceVariant
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import com.example.ui.viewmodel.RssViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedStreamScreen(
    viewModel: RssViewModel,
    onArticleClick: (ArticleEntity) -> Unit
) {
    val clusters by viewModel.storyClusters.collectAsState()
    val category by viewModel.selectedCategory.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val selectedSubcategory by viewModel.selectedSubcategory.collectAsState()
    val availableSubcategories by viewModel.availableSubcategories.collectAsState()
    val timeRange by viewModel.selectedTimeRange.collectAsState()
    val hideDeals by viewModel.hideDeals.collectAsState()
    val onlyPreferred by viewModel.onlyPreferredFeeds.collectAsState()
    val onlyBookmarks by viewModel.onlyBookmarks.collectAsState()
    val onlyOffline by viewModel.onlyOffline.collectAsState()
    val onlyRecommendations by viewModel.onlyRecommendations.collectAsState()
    val onlyReadHistory by viewModel.onlyReadHistory.collectAsState()
    val preferredGames by viewModel.preferredGames.collectAsState()
    val onlyPreferredGames by viewModel.onlyPreferredGames.collectAsState()
    val podcastTypeFilter by viewModel.podcastTypeFilter.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedClusterForSources by viewModel.selectedClusterForSources.collectAsState()
    val lastArchivedCluster by viewModel.lastArchivedCluster.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Nothing Header
        NothingHeader(
            title = if (onlyBookmarks) "BOOKMARKS" else if (onlyReadHistory) "READ HISTORY" else "NEWS STREAM",
            subtitle = if (onlyBookmarks) "SAVED ARTICLES FOR OFFLINE READING" else if (onlyReadHistory) "TRACKING ALL PREVIOUSLY OPENED ARTICLES" else "AUTOMATICALLY STACKED MULTI-SOURCE FEED",
            isOnline = isOnline,
            syncState = syncState,
            hideDeals = hideDeals,
            onlyPreferred = onlyPreferred,
            onToggleDeals = { viewModel.toggleHideDeals() },
            onTogglePreferred = { viewModel.toggleOnlyPreferred() },
            onRefresh = { viewModel.refreshNews() }
        )

        // Category Pills & Time Filters (if not in bookmarks mode)
        if (!onlyBookmarks) {
            CategoryPillBar(
                selectedCategory = category,
                onSelectCategory = { viewModel.setCategory(it) },
                categories = availableCategories,
                selectedSubcategory = selectedSubcategory,
                onSelectSubcategory = { viewModel.setSubcategory(it) },
                subcategories = availableSubcategories,
                selectedTimeRange = timeRange,
                onSelectTimeRange = { viewModel.setTimeRange(it) },
                onShuffleOrder = { viewModel.reorderSemiRandom() },
                onlyOffline = onlyOffline,
                onToggleOffline = { viewModel.toggleOnlyOffline() },
                onlyRecommendations = onlyRecommendations,
                onToggleRecommendations = { viewModel.toggleOnlyRecommendations() },
                onlyReadHistory = onlyReadHistory,
                onToggleReadHistory = { viewModel.toggleOnlyReadHistory() },
                preferredGames = preferredGames,
                onlyPreferredGames = onlyPreferredGames,
                onToggleOnlyPreferredGames = { viewModel.toggleOnlyPreferredGames() },
                onAddPreferredGame = { viewModel.addPreferredGame(it) },
                onRemovePreferredGame = { viewModel.removePreferredGame(it) },
                podcastTypeFilter = podcastTypeFilter,
                onSelectPodcastTypeFilter = { viewModel.setPodcastTypeFilter(it) }
            )
        }

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(NothingSurface)
                .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = NothingTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "FILTER BY HEADLINE OR KEYWORD...",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextMuted
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = NothingWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    cursorBrush = SolidColor(NothingRed),
                    modifier = Modifier
                        .testTag("news_search_input")
                        .fillMaxWidth()
                )
            }
        }

        // Read History Banner when active
        if (onlyReadHistory && clusters.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NothingSurfaceVariant)
                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📖 READ HISTORY (${clusters.size} STORIES READ)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NothingWhite
                    )

                    Box(
                        modifier = Modifier
                            .testTag("clear_read_history_button")
                            .clip(RoundedCornerShape(2.dp))
                            .background(NothingRed.copy(alpha = 0.2f))
                            .border(1.dp, NothingRed, RoundedCornerShape(2.dp))
                            .clickable { viewModel.clearReadHistory() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "[ CLEAR HISTORY ]",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = NothingRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // News Story Stream with Pull to Refresh
        val isRefreshing = syncState is SyncState.Syncing

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshNews() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (clusters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Inbox,
                            contentDescription = "Empty",
                            tint = NothingTextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (onlyBookmarks) "NO BOOKMARKED ARTICLES YET"
                                   else if (onlyReadHistory) "NO READ ARTICLES IN HISTORY"
                                   else "NO ARTICLES FOUND",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NothingWhite,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (onlyBookmarks) "Tap the bookmark icon on any story card to save it offline."
                                   else if (onlyReadHistory) "Articles you open and read will automatically be recorded here in your Room database."
                                   else if (hideDeals) "Deals are currently filtered out. Try toggling [ DEALS SHOWN ]."
                                   else "Tap refresh or pull down to update feeds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = clusters,
                        key = { it.clusterId }
                    ) { cluster ->
                        SwipeableArticleCard(
                            cluster = cluster,
                            onArticleClick = { article ->
                                viewModel.openArticleReader(article)
                                onArticleClick(article)
                            },
                            onChooseSourceClick = { viewModel.openStorySourcesDialog(it) },
                            onBookmarkToggle = { id, current ->
                                viewModel.toggleBookmark(id, current)
                            },
                            onOfflineToggle = { id, current ->
                                viewModel.toggleOfflineStatus(id, current)
                            },
                            onLikeToggle = { id, current ->
                                viewModel.toggleLikedStatus(id, current)
                            },
                            onDislikeToggle = { id, current ->
                                viewModel.toggleDislikedStatus(id, current)
                            },
                            onSubscribeFeed = { url, title, cat ->
                                viewModel.subscribeDiscoveredFeed(url, title, cat)
                            },
                            onArchive = { archivedCluster ->
                                viewModel.archiveCluster(archivedCluster)
                            },
                            onDislikeSwipe = { dislikedCluster ->
                                viewModel.dislikeCluster(dislikedCluster)
                            }
                        )
                    }
                }
            }

            // Undo Archive Banner (Floating at bottom above navigation)
            if (lastArchivedCluster != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingRed, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("undo_archive_banner")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = NothingRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "STORY ARCHIVED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NothingWhite
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NothingRed)
                                    .clickable { viewModel.undoArchiveLastCluster() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("undo_archive_button")
                            ) {
                                Text(
                                    text = "UNDO",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = NothingWhite
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = NothingTextMuted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { viewModel.clearLastArchivedCluster() }
                            )
                        }
                    }
                }
            }
        }
    }

    val dislikeAnalysis by viewModel.lastDislikeAnalysis.collectAsState()

    // Dislike Analysis Dialog (when an article is disliked & analyzed)
    if (dislikeAnalysis != null) {
        DislikeAnalysisDialog(
            analysis = dislikeAnalysis!!,
            onDismiss = { viewModel.dismissDislikeAnalysis() },
            onApplyMuting = { keywords, author, subcat, feedUrl ->
                viewModel.applyMutingPreferences(keywords, author, subcat, feedUrl)
            }
        )
    }

    // Story Stack Dialog (when tapping multiple sources button)
    if (selectedClusterForSources != null) {
        StoryStackDialog(
            cluster = selectedClusterForSources!!,
            onDismiss = { viewModel.closeStorySourcesDialog() },
            onSelectSourceArticle = { article ->
                viewModel.openArticleReader(article)
                onArticleClick(article)
            },
            onDecoupleArticle = { article ->
                viewModel.decoupleArticle(article.id)
            },
            onDecoupleCluster = { cluster ->
                viewModel.decoupleCluster(cluster)
            }
        )
    }
}
