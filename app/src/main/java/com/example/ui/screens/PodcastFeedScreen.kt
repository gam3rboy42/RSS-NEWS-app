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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.TextButton
import com.example.data.local.FeedEntity
import com.example.data.local.ArticleEntity
import com.example.ui.components.NothingHeader
import com.example.ui.components.PodcastMediaDialog
import com.example.ui.components.SwipeableArticleCard
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import com.example.ui.viewmodel.RssViewModel

@Composable
fun PodcastFeedScreen(
    viewModel: RssViewModel,
    onNavigateToDiscover: () -> Unit
) {
    val clusters by viewModel.storyClusters.collectAsState()
    val podcastTypeFilter by viewModel.podcastTypeFilter.collectAsState()
    val selectedPodcastCategory by viewModel.selectedPodcastCategory.collectAsState()
    val customPodcastCategories by viewModel.customPodcastCategories.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val allFeeds by viewModel.allFeeds.collectAsState()

    val podcastFeeds = remember(allFeeds) {
        allFeeds.filter { it.category.equals("PODCASTS", ignoreCase = true) || it.url.contains("podcast", ignoreCase = true) }
    }

    var activePodcastArticle by remember { mutableStateOf<ArticleEntity?>(null) }
    var feedToDelete by remember { mutableStateOf<FeedEntity?>(null) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryInput by remember { mutableStateOf("") }
    var showDiscoverPanel by remember(podcastFeeds.size) { mutableStateOf(podcastFeeds.isEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Nothing Header
        NothingHeader(
            title = "PODCASTS",
            subtitle = "AUDIO & VIDEO EPISODES BY CATEGORY",
            isOnline = isOnline,
            syncState = syncState,
            hideDeals = false,
            onlyPreferred = false,
            onToggleDeals = {},
            onTogglePreferred = {},
            onRefresh = { viewModel.refreshNews() }
        )

        // Podcast Filter Bar (Audio vs Video vs Downloaded vs All)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf(
                "ALL" to "ALL FORMATS",
                "AUDIO" to "🎧 AUDIO",
                "VIDEO" to "🎥 VIDEO",
                "DOWNLOADED" to "💾 DOWNLOADED"
            )

            items(filters) { (typeKey, typeLabel) ->
                val isSelected = podcastTypeFilter == typeKey
                Box(
                    modifier = Modifier
                        .testTag("podcast_tab_$typeKey")
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) NothingRed else NothingSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NothingRed else NothingBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { viewModel.setPodcastTypeFilter(typeKey) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = typeLabel,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isSelected) NothingWhite else NothingTextSecondary
                    )
                }
            }
        }

        // Diverse & User-Defined Podcast Categories Bar
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = customPodcastCategories,
                key = { "cat_$it" }
            ) { cat ->
                val isSelected = selectedPodcastCategory == cat
                val isDefaultCat = listOf(
                    "ALL", "TECH & AI", "NEWS & POLITICS", "TRUE CRIME", "SCIENCE",
                    "BUSINESS & FINANCE", "COMEDY", "GAMING & GEEK", "CULTURE & HISTORY",
                    "HEALTH & FITNESS", "AUDIOBOOKS"
                ).contains(cat)

                Box(
                    modifier = Modifier
                        .testTag("podcast_category_$cat")
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) NothingWhite else NothingSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NothingWhite else NothingBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { viewModel.setSelectedPodcastCategory(cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cat,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (isSelected) NothingBlack else NothingWhite
                        )
                        if (!isDefaultCat && cat != "ALL") {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove category",
                                tint = if (isSelected) NothingBlack else NothingRed,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { viewModel.removeCustomPodcastCategory(cat) }
                            )
                        }
                    }
                }
            }

            item(key = "add_custom_category_button") {
                Box(
                    modifier = Modifier
                        .testTag("add_custom_podcast_category_btn")
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingSurface)
                        .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                        .clickable { showAddCategoryDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Category",
                            tint = NothingRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+ NEW CATEGORY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = NothingRed
                        )
                    }
                }
            }
        }

        // Search Bar & Discover Podcasts Toggle Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NothingSurface)
                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = NothingTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "SEARCH PODCAST SHOWS...",
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
                            .testTag("podcast_search_input")
                            .weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.searchQuery.value = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = NothingTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .testTag("toggle_podcast_discover_panel_btn")
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (showDiscoverPanel) NothingRed else NothingSurface)
                    .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                    .clickable { showDiscoverPanel = !showDiscoverPanel }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Discover",
                        tint = NothingWhite,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showDiscoverPanel) "CLOSE DISCOVER" else "+ DISCOVER",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NothingWhite
                    )
                }
            }
        }

        if (showDiscoverPanel) {
            PodcastDiscoverSection(
                viewModel = viewModel,
                onSubscribed = { showDiscoverPanel = false }
            )
        }

        // Subscribed Podcast Shows Artwork Carousel
        if (podcastFeeds.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(podcastFeeds, key = { it.url }) { feed ->
                        val isSelected = searchQuery.equals(feed.title, ignoreCase = true)

                        LaunchedEffect(feed.url, feed.iconUrl) {
                            if (feed.iconUrl.isBlank()) {
                                viewModel.autoGrabAndSetSeriesThumbnail(feed.url, feed.title)
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .testTag("podcast_show_chip_${feed.title.lowercase().replace(" ", "_")}")
                                .width(68.dp)
                                .pointerInput(feed.url) {
                                    detectTapGestures(
                                        onTap = {
                                            if (isSelected) {
                                                viewModel.searchQuery.value = ""
                                            } else {
                                                viewModel.searchQuery.value = feed.title
                                            }
                                        },
                                        onLongPress = {
                                            feedToDelete = feed
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NothingSurface)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) NothingRed else NothingBorder,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (feed.iconUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = feed.iconUrl,
                                        contentDescription = feed.title,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = feed.title,
                                        tint = NothingRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(3.dp))

                            Text(
                                text = feed.title,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NothingRed else NothingTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Podcast Episode Stream
        if (clusters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Podcasts",
                        tint = NothingRed,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "NO PODCAST EPISODES FOUND",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = NothingWhite
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Subscribe to audio or video podcasts from the Discover menu to build your private podcast stream.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onNavigateToDiscover,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingRed,
                            contentColor = NothingWhite
                        ),
                        modifier = Modifier.testTag("explore_podcasts_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = "Explore",
                                tint = NothingWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EXPLORE PODCAST FEEDS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
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
                            if (article.isPodcast || article.isVideoPodcast || article.mediaType == "AUDIO" || article.mediaType == "VIDEO") {
                                activePodcastArticle = article
                            } else {
                                viewModel.openArticleReader(article)
                            }
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
                        onDeleteFeed = { url ->
                            viewModel.deleteFeed(url)
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
    }

    // Custom Category Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddCategoryDialog = false
                newCategoryInput = ""
            },
            title = {
                Text(
                    text = "ADD CUSTOM PODCAST CATEGORY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NothingWhite,
                    fontSize = 14.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a new category name (e.g. PHILOSOPHY, AI NEWS, DESIGN, WORKOUT):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newCategoryInput,
                        onValueChange = { newCategoryInput = it },
                        singleLine = true,
                        placeholder = { Text("Category Name", color = NothingTextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_category_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryInput.isNotBlank()) {
                            viewModel.addCustomPodcastCategory(newCategoryInput)
                            showAddCategoryDialog = false
                            newCategoryInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("confirm_add_category_btn")
                ) {
                    Text("ADD CATEGORY", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = NothingWhite)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showAddCategoryDialog = false
                        newCategoryInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingSurface),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = NothingWhite)
                }
            },
            containerColor = NothingBlack,
            shape = RoundedCornerShape(8.dp)
        )
    }

    // Media Dialog overlay when user taps a podcast
    if (activePodcastArticle != null) {
        PodcastMediaDialog(
            article = activePodcastArticle!!,
            onDismiss = { activePodcastArticle = null },
            onOpenInBrowser = { url ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                viewModel.closeArticleReader()
            }
        )
    }

    // Delete Feed confirmation dialog (triggered via press-and-hold on show chip)
    feedToDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedToDelete = null },
            title = {
                Text(
                    text = "REMOVE PODCAST?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NothingWhite
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${feed.title}' from your podcast feeds? All saved episodes will be deleted.",
                    fontSize = 13.sp,
                    color = NothingTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val urlToDelete = feed.url
                        feedToDelete = null
                        if (searchQuery.equals(feed.title, ignoreCase = true)) {
                            viewModel.searchQuery.value = ""
                        }
                        viewModel.deleteFeed(urlToDelete)
                    }
                ) {
                    Text(
                        text = "REMOVE PODCAST",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { feedToDelete = null }
                ) {
                    Text(
                        text = "CANCEL",
                        fontFamily = FontFamily.Monospace,
                        color = NothingTextMuted
                    )
                }
            },
            containerColor = NothingDarkGray,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
fun PodcastDiscoverSection(
    viewModel: RssViewModel,
    onSubscribed: (String) -> Unit
) {
    val podcastSearchResults by viewModel.podcastSearchResults.collectAsState()
    val isSearchingPodcasts by viewModel.isSearchingPodcasts.collectAsState()
    val allFeeds by viewModel.allFeeds.collectAsState()

    var topicInput by remember { mutableStateOf("") }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    val subscribedUrls = remember(allFeeds) {
        allFeeds.map { it.url.trim().lowercase().removeSuffix("/") }.toSet()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(NothingDarkGray)
            .border(1.dp, NothingRed, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Podcasts",
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PODCAST DISCOVERY & SEARCH",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NothingWhite
                    )
                }
            }

            Text(
                text = "SEARCH AUDIO & VIDEO PODCAST SHOWS VIA ITUNES & RSS",
                style = MaterialTheme.typography.labelSmall,
                color = NothingTextMuted,
                fontSize = 9.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Search Input Row
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = topicInput,
                    onValueChange = { topicInput = it },
                    placeholder = {
                        Text(
                            "Search podcast name or topic...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NothingTextMuted
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = NothingWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NothingRed,
                        unfocusedBorderColor = NothingBorder,
                        focusedContainerColor = NothingSurface,
                        unfocusedContainerColor = NothingSurface
                    ),
                    modifier = Modifier
                        .testTag("podcast_discover_input")
                        .weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (topicInput.isNotBlank()) {
                            viewModel.searchPodcastFeedsByTopic(topicInput.trim())
                        }
                    },
                    enabled = topicInput.isNotBlank() && !isSearchingPodcasts,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingRed,
                        contentColor = NothingWhite
                    ),
                    modifier = Modifier
                        .testTag("search_podcast_button")
                        .height(52.dp)
                ) {
                    Text(
                        text = if (isSearchingPodcasts) "SEARCHING..." else "SEARCH",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            if (resultMsg != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = resultMsg!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingWhite
                )
            }

            // Search Results or Curated Suggestions
            if (isSearchingPodcasts) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "🔍 Searching podcast shows for '$topicInput'...",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = NothingRed
                )
            } else if (podcastSearchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "FOUND ${podcastSearchResults.size} PODCAST SHOWS:",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = NothingWhite
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    podcastSearchResults.forEach { item ->
                        val isSubscribed = subscribedUrls.contains(item.url.trim().lowercase().removeSuffix("/"))
                        PodcastSearchResultCard(
                            item = item,
                            isSubscribed = isSubscribed,
                            onSubscribe = { podItem ->
                                viewModel.addCustomFeedUrl(
                                    url = podItem.url,
                                    title = podItem.title,
                                    category = "PODCASTS"
                                ) { success ->
                                    if (success) {
                                        resultMsg = "✔ Subscribed to '${podItem.title}'"
                                        onSubscribed(podItem.title)
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Curated Podcast Shows Suggestions
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "FEATURED POPULAR PODCAST SHOWS:",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = NothingWhite
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.example.data.model.DefaultFeedCatalog.curatedPodcastFeeds.forEach { item ->
                        val isSubscribed = subscribedUrls.contains(item.url.trim().lowercase().removeSuffix("/"))
                        PodcastSearchResultCard(
                            item = item,
                            isSubscribed = isSubscribed,
                            onSubscribe = { podItem ->
                                viewModel.addCustomFeedUrl(
                                    url = podItem.url,
                                    title = podItem.title,
                                    category = "PODCASTS"
                                ) { success ->
                                    if (success) {
                                        resultMsg = "✔ Subscribed to '${podItem.title}'"
                                        onSubscribed(podItem.title)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PodcastSearchResultCard(
    item: com.example.data.model.FeedDiscoveryItem,
    isSubscribed: Boolean,
    onSubscribe: (com.example.data.model.FeedDiscoveryItem) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(NothingSurface)
            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (item.isVideoPodcast) "🎥 VIDEO" else "🎧 AUDIO",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        color = NothingRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.title,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = NothingWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .testTag("subscribe_podcast_${item.title.lowercase().replace(" ", "_")}")
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSubscribed) NothingSurface else NothingRed)
                    .border(
                        width = 1.dp,
                        color = if (isSubscribed) NothingBorder else NothingRed,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable(enabled = !isSubscribed) { onSubscribe(item) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isSubscribed) "✔ ADDED" else "+ SUBSCRIBE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (isSubscribed) NothingTextMuted else NothingWhite
                )
            }
        }
    }
}
