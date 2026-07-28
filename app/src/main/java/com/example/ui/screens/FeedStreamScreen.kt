package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.ui.components.ArticleCard
import com.example.ui.components.CategoryPillBar
import com.example.ui.components.NothingHeader
import com.example.ui.components.StoryStackDialog
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import com.example.ui.viewmodel.RssViewModel

@Composable
fun FeedStreamScreen(
    viewModel: RssViewModel,
    onArticleClick: (ArticleEntity) -> Unit
) {
    val clusters by viewModel.storyClusters.collectAsState()
    val category by viewModel.selectedCategory.collectAsState()
    val hideDeals by viewModel.hideDeals.collectAsState()
    val onlyPreferred by viewModel.onlyPreferredFeeds.collectAsState()
    val onlyBookmarks by viewModel.onlyBookmarks.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedClusterForSources by viewModel.selectedClusterForSources.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Nothing Header
        NothingHeader(
            title = if (onlyBookmarks) "BOOKMARKS" else "NEWS STREAM",
            subtitle = if (onlyBookmarks) "SAVED ARTICLES FOR OFFLINE READING" else "AUTOMATICALLY STACKED MULTI-SOURCE FEED",
            isOnline = isOnline,
            syncState = syncState,
            hideDeals = hideDeals,
            onlyPreferred = onlyPreferred,
            onToggleDeals = { viewModel.toggleHideDeals() },
            onTogglePreferred = { viewModel.toggleOnlyPreferred() },
            onRefresh = { viewModel.refreshNews() }
        )

        // Category Pills (if not in bookmarks mode)
        if (!onlyBookmarks) {
            CategoryPillBar(
                selectedCategory = category,
                onSelectCategory = { viewModel.setCategory(it) }
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
            androidx.compose.foundation.layout.Row(
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

        Spacer(modifier = Modifier.height(4.dp))

        // News Story Stream
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
                        text = if (onlyBookmarks) "NO BOOKMARKED ARTICLES YET" else "NO ARTICLES FOUND",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NothingWhite,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (onlyBookmarks) "Tap the bookmark icon on any story card to save it offline."
                               else if (hideDeals) "Deals are currently filtered out. Try toggling [ DEALS SHOWN ]."
                               else "Tap refresh or add custom RSS feeds in the Discover tab.",
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
                    ArticleCard(
                        cluster = cluster,
                        onArticleClick = { article ->
                            viewModel.openArticleReader(article)
                            onArticleClick(article)
                        },
                        onChooseSourceClick = { viewModel.openStorySourcesDialog(it) },
                        onBookmarkToggle = { id, current ->
                            viewModel.toggleBookmark(id, current)
                        }
                    )
                }
            }
        }
    }

    // Story Stack Dialog (when tapping multiple sources button)
    if (selectedClusterForSources != null) {
        StoryStackDialog(
            cluster = selectedClusterForSources!!,
            onDismiss = { viewModel.closeStorySourcesDialog() },
            onSelectSourceArticle = { article ->
                viewModel.openArticleReader(article)
                onArticleClick(article)
            }
        )
    }
}
