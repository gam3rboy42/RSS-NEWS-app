package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.FeedEntity
import com.example.data.model.DefaultFeedCatalog
import com.example.data.model.FeedDiscoveryItem
import com.example.ui.components.CategoryAssignDialog
import com.example.ui.components.CategoryPillBar
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import com.example.ui.viewmodel.RssViewModel
import com.example.util.InAppBrowser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedDiscoveryScreen(viewModel: RssViewModel) {
    val context = LocalContext.current
    val allFeeds by viewModel.allFeeds.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()

    val topicSearchResults by viewModel.topicSearchResults.collectAsState()
    val isSearchingTopics by viewModel.isSearchingTopics.collectAsState()
    val preferredGames by viewModel.preferredGames.collectAsState()
    val onlyPreferredGames by viewModel.onlyPreferredGames.collectAsState()

    var topicInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var customUrl by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("TECH") }
    var discoveryCategory by remember { mutableStateOf("ALL") }
    var addResultMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var editingFeedForFolder by remember { mutableStateOf<FeedEntity?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }
    var autoTagStatusMsg by remember { mutableStateOf<String?>(null) }
    var isAutoTagging by remember { mutableStateOf(false) }

    val normalizeUrlKey: (String) -> String = remember {
        { url -> url.trim().lowercase().removeSuffix("/") }
    }

    val subscribedUrls by remember(allFeeds) {
        derivedStateOf {
            val set = HashSet<String>(allFeeds.size)
            for (f in allFeeds) {
                set.add(f.url.trim().lowercase().removeSuffix("/"))
            }
            set
        }
    }
    val preferredUrls by remember(allFeeds) {
        derivedStateOf {
            val set = HashSet<String>(allFeeds.size)
            for (f in allFeeds) {
                if (f.isPreferred) {
                    set.add(f.url.trim().lowercase().removeSuffix("/"))
                }
            }
            set
        }
    }

    val onPreviewWeb: (String) -> Unit = remember(context) {
        { url -> InAppBrowser.openUrl(context, url) }
    }

    val onTogglePreferred: (String, Boolean) -> Unit = remember(viewModel) {
        { url, pref -> viewModel.toggleFeedPreferred(url, pref) }
    }

    val onDeleteFeed: (String) -> Unit = remember(viewModel) {
        { url -> viewModel.deleteFeed(url) }
    }

    val onEditFolder: (FeedEntity) -> Unit = remember {
        { feed -> editingFeedForFolder = feed }
    }

    val onToggleSubscribe: (FeedDiscoveryItem, Boolean) -> Unit = remember(viewModel) {
        { feedItem, sub ->
            if (!sub) {
                viewModel.addCustomFeedUrl(feedItem.url, feedItem.title, feedItem.category) {}
            } else {
                viewModel.deleteFeed(feedItem.url)
            }
        }
    }

    val onSubscribeTopic: (FeedDiscoveryItem) -> Unit = remember(viewModel) {
        { itemToSub ->
            viewModel.addCustomFeedUrl(
                url = itemToSub.url,
                title = itemToSub.title,
                category = itemToSub.category
            ) { success ->
                if (success) {
                    addResultMsg = "✔ Subscribed to ${itemToSub.title}"
                }
            }
        }
    }

    val onSelectDiscoveryCategory: (String) -> Unit = remember {
        { cat -> discoveryCategory = cat }
    }

    val filteredCurated by remember(discoveryCategory, searchQuery) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            val isAllCat = discoveryCategory == "ALL" || discoveryCategory.isBlank()
            DefaultFeedCatalog.curatedFeeds.filter { item ->
                val matchesCategory = isAllCat || item.category.equals(discoveryCategory, ignoreCase = true)
                if (!matchesCategory) return@filter false
                if (query.isEmpty()) return@filter true
                item.title.lowercase().contains(query) ||
                        item.description.lowercase().contains(query) ||
                        item.category.lowercase().contains(query) ||
                        item.url.lowercase().contains(query)
            }
        }
    }

    val filteredUserFeeds by remember(allFeeds, searchQuery, discoveryCategory) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            val isAllCat = discoveryCategory == "ALL" || discoveryCategory.isBlank()
            allFeeds.filter { feed ->
                val matchesCategory = isAllCat || feed.category.equals(discoveryCategory, ignoreCase = true)
                if (!matchesCategory) return@filter false
                if (query.isEmpty()) return@filter true
                feed.title.lowercase().contains(query) ||
                        feed.category.lowercase().contains(query) ||
                        feed.url.lowercase().contains(query)
            }
        }
    }

    val feedsGroupedByFolder by remember {
        derivedStateOf {
            filteredUserFeeds.groupBy { it.category.ifBlank { "GENERAL" }.uppercase() }
        }
    }

    val collapsedFolders = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    val popularTopics = remember {
        listOf("Retro Gaming", "Formula 1", "AI Hardware", "SpaceX", "Linux Kernel", "Crypto", "Japanese Cooking")
    }

    val nonAllCategories = remember(availableCategories) {
        availableCategories.filter { it != "ALL" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Top Title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NothingRed)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DISCOVER & MANAGE FEEDS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = NothingWhite
                )
            }
            Text(
                text = "SUBSCRIBE, CATEGORIZE INTO FOLDERS & ADD CUSTOM RSS / ATOM FEEDS",
                style = MaterialTheme.typography.labelSmall,
                color = NothingTextMuted
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
        ) {
            // TOPIC RSS LOOKUP CARD
            item(key = "topic_search_card", contentType = "header_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingRed, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Topic Feeds",
                                tint = NothingRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RSS FEED LOOKUP BY TOPIC",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = NothingWhite
                            )
                        }

                        Text(
                            text = "TYPE ANY TOPIC TO SEARCH AND DISCOVER VALID RSS / ATOM FEEDS",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTextMuted,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = topicInput,
                                onValueChange = { topicInput = it },
                                placeholder = {
                                    Text(
                                        "e.g. Retro Gaming, SpaceX, AI Hardware...",
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
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NothingRed,
                                    unfocusedBorderColor = NothingBorder,
                                    focusedContainerColor = NothingSurface,
                                    unfocusedContainerColor = NothingSurface
                                ),
                                modifier = Modifier
                                    .testTag("topic_rss_search_input")
                                    .weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    if (topicInput.isNotBlank()) {
                                        viewModel.searchFeedsByTopic(topicInput.trim())
                                    }
                                },
                                enabled = topicInput.isNotBlank() && !isSearchingTopics,
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingRed,
                                    contentColor = NothingWhite
                                ),
                                modifier = Modifier
                                    .testTag("search_topic_rss_button")
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = if (isSearchingTopics) "SEARCHING..." else "SEARCH",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Topic Suggestions
                        Text(
                            text = "POPULAR TOPIC IDEAS:",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTextMuted,
                            fontSize = 9.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            popularTopics.forEach { topicIdea ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NothingSurface)
                                        .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                                        .clickable {
                                            topicInput = topicIdea
                                            viewModel.searchFeedsByTopic(topicIdea)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = topicIdea.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NothingTextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        // Search Results Display
                        if (isSearchingTopics) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "🔍 Searching & discovering RSS feeds for '$topicInput'...",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingRed,
                                fontFamily = FontFamily.Monospace
                            )
                        } else if (topicSearchResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "FOUND ${topicSearchResults.size} RSS FEEDS FOR '$topicInput':",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = NothingWhite
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                topicSearchResults.forEach { feedItem ->
                                    val isSubscribed = subscribedUrls.contains(normalizeUrlKey(feedItem.url))
                                    TopicSearchResultCard(
                                        feedItem = feedItem,
                                        isSubscribed = isSubscribed,
                                        onSubscribe = onSubscribeTopic
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // GAMING PREFERRED GAMES MANAGER CARD
            item(key = "preferred_games_card", contentType = "header_card") {
                com.example.ui.components.PreferredGamesBar(
                    preferredGames = preferredGames,
                    onlyPreferredGames = onlyPreferredGames,
                    onToggleOnlyPreferred = { viewModel.toggleOnlyPreferredGames() },
                    onAddGame = { viewModel.addPreferredGame(it) },
                    onRemoveGame = { viewModel.removePreferredGame(it) },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // SEARCH INPUT
            item(key = "search_input_card", contentType = "header_card") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search feeds, categories, or URL...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = NothingTextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = NothingRed,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = NothingWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NothingRed,
                        unfocusedBorderColor = NothingBorder,
                        focusedContainerColor = NothingDarkGray,
                        unfocusedContainerColor = NothingDarkGray
                    ),
                    modifier = Modifier
                        .testTag("feed_discovery_search_input")
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }

            // ADD CUSTOM RSS CARD
            item(key = "add_custom_rss_card", contentType = "header_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingRed, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = NothingRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ADD CUSTOM RSS / ATOM URL",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = NothingWhite
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // URL Input
                        OutlinedTextField(
                            value = customUrl,
                            onValueChange = { customUrl = it },
                            placeholder = {
                                Text(
                                    "https://example.com/rss.xml",
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingRed,
                                unfocusedBorderColor = NothingBorder,
                                focusedContainerColor = NothingSurface,
                                unfocusedContainerColor = NothingSurface
                            ),
                            modifier = Modifier
                                .testTag("custom_rss_url_input")
                                .fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category Selection Chips
                        Text(
                            text = "ASSIGN TO FOLDER / CATEGORY:",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            nonAllCategories.forEach { cat ->
                                val isSelected = cat.equals(customCategory, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) NothingRed else NothingSurface)
                                        .border(1.dp, if (isSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                                        .clickable { customCategory = cat }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) NothingWhite else NothingTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Title optional input
                            OutlinedTextField(
                                value = customTitle,
                                onValueChange = { customTitle = it },
                                placeholder = {
                                    Text(
                                        "Feed Name (Optional)",
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
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NothingRed,
                                    unfocusedBorderColor = NothingBorder,
                                    focusedContainerColor = NothingSurface,
                                    unfocusedContainerColor = NothingSurface
                                ),
                                modifier = Modifier
                                    .testTag("custom_rss_title_input")
                                    .weight(1f)
                            )

                            // Submit Button
                            Button(
                                onClick = {
                                    if (customUrl.isNotBlank()) {
                                        isSubmitting = true
                                        addResultMsg = "Validating & adding feed..."
                                        viewModel.addCustomFeedUrl(
                                            url = customUrl.trim(),
                                            title = customTitle.ifBlank { "Custom Feed" },
                                            category = customCategory
                                        ) { success ->
                                            isSubmitting = false
                                            if (success) {
                                                addResultMsg = "✔ SUCCESS! Feed added and categorized as $customCategory."
                                                customUrl = ""
                                                customTitle = ""
                                            } else {
                                                addResultMsg = "❌ Failed to parse RSS feed URL."
                                            }
                                        }
                                    }
                                },
                                enabled = customUrl.isNotBlank() && !isSubmitting,
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingRed,
                                    contentColor = NothingWhite,
                                    disabledContainerColor = NothingSurface
                                ),
                                modifier = Modifier
                                    .testTag("add_custom_feed_button")
                                    .height(52.dp)
                            ) {
                                Text(
                                    text = if (isSubmitting) "ADDING..." else "ADD FEED",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (addResultMsg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = addResultMsg!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (addResultMsg!!.startsWith("✔")) NothingWhite else NothingRed
                            )
                        }
                    }
                }
            }

            // SUBSCRIBED FEEDS GROUPED BY CUSTOM FOLDERS
            item(key = "user_folders_hdr", contentType = "header_card") {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "YOUR FOLDERS (${feedsGroupedByFolder.size}) • FEEDS (${allFeeds.size})",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NothingWhite
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Auto-Tag Feeds On-Device Button
                        Box(
                            modifier = Modifier
                                .testTag("auto_tag_all_feeds_button")
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingSurface)
                                .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                                .clickable(enabled = !isAutoTagging) {
                                    isAutoTagging = true
                                    autoTagStatusMsg = "⚡ Analyzing feeds & user preferences..."
                                    viewModel.autoTagAllFeedsOnDevice { count ->
                                        isAutoTagging = false
                                        autoTagStatusMsg = "✔ ON-DEVICE AUTO-TAGGER: Categorized $count feeds (Tech, Finance, Science...)"
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = if (isAutoTagging) "⚡ TAGGING..." else "⚡ AUTO-TAG FEEDS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NothingRed
                            )
                        }

                        // Create New Custom Folder Button
                        Box(
                            modifier = Modifier
                                .testTag("create_custom_folder_button")
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingSurface)
                                .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                                .clickable { showNewFolderDialog = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "New Folder",
                                    tint = NothingRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+ NEW FOLDER",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NothingWhite
                                )
                            }
                        }
                    }
                }

                if (autoTagStatusMsg != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingDarkGray)
                            .border(1.dp, NothingRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = autoTagStatusMsg!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = NothingTextMuted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { autoTagStatusMsg = null }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (feedsGroupedByFolder.isEmpty()) {
                item(key = "user_folders_empty", contentType = "header_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NothingDarkGray)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "NO FEEDS SUBSCRIBED FOR THIS FILTER. ADD ONE ABOVE OR SUBSCRIBE BELOW.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                item(key = "collapse_expand_all_btn", contentType = "header_card") {
                    val allCollapsed = feedsGroupedByFolder.keys.all { collapsedFolders[it] == true }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .testTag("toggle_all_folders_button")
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingSurface)
                                .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                                .clickable {
                                    val targetState = !allCollapsed
                                    feedsGroupedByFolder.keys.forEach { folderKey ->
                                        collapsedFolders[folderKey] = targetState
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (allCollapsed) "▼ EXPAND ALL FOLDERS" else "▲ COLLAPSE ALL FOLDERS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NothingWhite
                            )
                        }
                    }
                }

                feedsGroupedByFolder.forEach { (folderName, folderFeeds) ->
                    val isCollapsed = collapsedFolders[folderName] ?: false

                    // Folder Header
                    item(key = "folder_hdr_$folderName", contentType = "folder_header") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingSurface)
                                .border(1.dp, NothingRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .clickable {
                                    collapsedFolders[folderName] = !isCollapsed
                                }
                                .testTag("toggle_folder_$folderName")
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isCollapsed) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (isCollapsed) "Expand Folder" else "Collapse Folder",
                                        tint = NothingRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = NothingRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "FOLDER: $folderName",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = NothingWhite
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${folderFeeds.size} ${if (folderFeeds.size == 1) "FEED" else "FEEDS"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NothingTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCollapsed) "[+] EDIT" else "[-]",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NothingRed
                                    )
                                }
                            }
                        }
                    }

                    // Feeds in Folder (only rendered if folder is expanded)
                    if (!isCollapsed) {
                        items(
                            items = folderFeeds,
                            key = { "user_${it.url}" },
                            contentType = { "user_feed_card" }
                        ) { userFeed ->
                            UserFeedCard(
                                userFeed = userFeed,
                                onEditFolder = onEditFolder,
                                onPreviewWeb = onPreviewWeb,
                                onTogglePreferred = onTogglePreferred,
                                onDeleteFeed = onDeleteFeed
                            )
                        }
                    }
                }
            }

            // CURATED DIRECTORY SECTION
            item(key = "curated_directory_hdr", contentType = "header_card") {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CURATED FEED DIRECTORY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NothingWhite
                )
                Spacer(modifier = Modifier.height(6.dp))

                CategoryPillBar(
                    selectedCategory = discoveryCategory,
                    onSelectCategory = onSelectDiscoveryCategory,
                    categories = availableCategories
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Curated Feeds List
            items(
                items = filteredCurated,
                key = { "curated_${it.url}" },
                contentType = { "curated_feed_card" }
            ) { item ->
                val cleanKey = remember(item.url) { normalizeUrlKey(item.url) }
                val isSubscribed = subscribedUrls.contains(cleanKey)
                val isPreferred = preferredUrls.contains(cleanKey)

                CuratedFeedCard(
                    item = item,
                    isSubscribed = isSubscribed,
                    isPreferred = isPreferred,
                    onPreviewWeb = onPreviewWeb,
                    onTogglePreferred = onTogglePreferred,
                    onToggleSubscribe = onToggleSubscribe
                )
            }
        }
    }

    // Edit Feed Details / Category Dialog
    if (editingFeedForFolder != null) {
        CategoryAssignDialog(
            feed = editingFeedForFolder!!,
            availableCategories = availableCategories,
            onDismiss = { editingFeedForFolder = null },
            onSaveFeed = { newTitle, newUrl, newCategory, newIconUrl ->
                viewModel.updateFeedDetails(editingFeedForFolder!!, newTitle, newUrl, newCategory)
                viewModel.updateFeedIconUrl(newUrl, newIconUrl)
                editingFeedForFolder = null
            }
        )
    }

    // New Custom Folder Creator Dialog
    if (showNewFolderDialog) {
        Dialog(onDismissRequest = { showNewFolderDialog = false }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NothingBlack,
                modifier = Modifier
                    .border(1.dp, NothingRed, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "CREATE CUSTOM FOLDER",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = NothingRed
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter a custom folder name to group your RSS feeds (e.g. TECH, WORK, SCIENCE, PODCASTS):",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingTextMuted
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newFolderNameInput,
                        onValueChange = { newFolderNameInput = it },
                        placeholder = {
                            Text(
                                "FOLDER NAME",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = NothingTextMuted
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = NothingWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NothingRed,
                            unfocusedBorderColor = NothingBorder,
                            focusedContainerColor = NothingSurface,
                            unfocusedContainerColor = NothingSurface
                        ),
                        modifier = Modifier
                            .testTag("new_folder_name_input")
                            .fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                showNewFolderDialog = false
                                newFolderNameInput = ""
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingSurface,
                                contentColor = NothingTextSecondary
                            ),
                            modifier = Modifier.border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = "CANCEL",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                if (newFolderNameInput.isNotBlank()) {
                                    val cat = newFolderNameInput.uppercase().trim()
                                    customCategory = cat
                                    discoveryCategory = cat
                                    showNewFolderDialog = false
                                    newFolderNameInput = ""
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingRed,
                                contentColor = NothingWhite
                            ),
                            modifier = Modifier.testTag("confirm_create_folder_button")
                        ) {
                            Text(
                                text = "CREATE FOLDER",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicSearchResultCard(
    feedItem: FeedDiscoveryItem,
    isSubscribed: Boolean,
    onSubscribe: (FeedDiscoveryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(NothingSurface)
            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
            .padding(10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedItem.title,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NothingWhite,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(NothingRed)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = feedItem.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = feedItem.description,
                style = MaterialTheme.typography.bodySmall,
                color = NothingTextSecondary,
                fontSize = 10.sp,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = feedItem.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { onSubscribe(feedItem) },
                    enabled = !isSubscribed,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) NothingDarkGray else NothingRed,
                        contentColor = NothingWhite
                    ),
                    modifier = Modifier
                        .testTag("subscribe_topic_feed_${feedItem.title}")
                        .height(32.dp)
                ) {
                    Text(
                        text = if (isSubscribed) "SUBSCRIBED" else "+ SUBSCRIBE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UserFeedCard(
    userFeed: FeedEntity,
    onEditFolder: (FeedEntity) -> Unit,
    onPreviewWeb: (String) -> Unit,
    onTogglePreferred: (String, Boolean) -> Unit,
    onDeleteFeed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(NothingDarkGray)
            .border(
                1.dp,
                if (userFeed.isPreferred) NothingRed else NothingBorder,
                RoundedCornerShape(6.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = userFeed.title.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NothingWhite
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Folder Chip Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(NothingSurface)
                            .border(1.dp, NothingBorder, RoundedCornerShape(2.dp))
                            .clickable { onEditFolder(userFeed) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "📁 ${userFeed.category.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingWhite,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = userFeed.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Edit Feed Details
                IconButton(
                    onClick = { onEditFolder(userFeed) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Feed Details",
                        tint = NothingRed,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Web Preview
                IconButton(
                    onClick = { onPreviewWeb(userFeed.url) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Web Preview",
                        tint = NothingWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Preferred Toggle
                IconButton(
                    onClick = { onTogglePreferred(userFeed.url, userFeed.isPreferred) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (userFeed.isPreferred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Star",
                        tint = if (userFeed.isPreferred) NothingRed else NothingTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Delete Feed
                IconButton(
                    onClick = { onDeleteFeed(userFeed.url) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Unsubscribe",
                        tint = NothingTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CuratedFeedCard(
    item: FeedDiscoveryItem,
    isSubscribed: Boolean,
    isPreferred: Boolean,
    onPreviewWeb: (String) -> Unit,
    onTogglePreferred: (String, Boolean) -> Unit,
    onToggleSubscribe: (FeedDiscoveryItem, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(NothingDarkGray)
            .border(
                1.dp,
                if (isPreferred) NothingRed else NothingBorder,
                RoundedCornerShape(6.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = NothingWhite
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .background(NothingSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTextSecondary,
                            fontSize = 8.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NothingTextMuted
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Actions: Web Preview, Preferred Star, Subscribe Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // In-App Browser Preview
                Box(
                    modifier = Modifier
                        .testTag("preview_feed_web_${item.title}")
                        .clip(CircleShape)
                        .background(NothingSurface)
                        .border(1.dp, NothingBorder, CircleShape)
                        .clickable { onPreviewWeb(item.url) }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Preview Web Source",
                        tint = NothingWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Star Preferred Toggle
                Box(
                    modifier = Modifier
                        .testTag("toggle_preferred_feed_${item.title}")
                        .clip(CircleShape)
                        .background(if (isPreferred) NothingRed.copy(alpha = 0.2f) else NothingSurface)
                        .border(1.dp, if (isPreferred) NothingRed else NothingBorder, CircleShape)
                        .clickable { onTogglePreferred(item.url, isPreferred) }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isPreferred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Preferred",
                        tint = if (isPreferred) NothingRed else NothingTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Subscribe Toggle
                Box(
                    modifier = Modifier
                        .testTag("subscribe_feed_${item.title}")
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSubscribed) NothingSurface else NothingRed)
                        .border(1.dp, if (isSubscribed) NothingBorder else NothingRed, RoundedCornerShape(4.dp))
                        .clickable { onToggleSubscribe(item, isSubscribed) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isSubscribed) "ADDED" else "+ SUBSCRIBE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSubscribed) NothingTextSecondary else NothingWhite
                    )
                }
            }
        }
    }
}
