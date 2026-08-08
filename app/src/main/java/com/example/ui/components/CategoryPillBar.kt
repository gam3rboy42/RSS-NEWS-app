package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DefaultFeedCatalog
import com.example.data.model.TimeRangeFilter
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPillBar(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    categories: List<String> = DefaultFeedCatalog.categories,
    selectedSubcategory: String = "ALL",
    onSelectSubcategory: (String) -> Unit = {},
    subcategories: List<String> = emptyList(),
    selectedTimeRange: TimeRangeFilter = TimeRangeFilter.ONE_WEEK,
    onSelectTimeRange: (TimeRangeFilter) -> Unit = {},
    onShuffleOrder: () -> Unit = {},
    onlyOffline: Boolean = false,
    onToggleOffline: () -> Unit = {},
    onlyRecommendations: Boolean = false,
    onToggleRecommendations: () -> Unit = {},
    onlyReadHistory: Boolean = false,
    onToggleReadHistory: () -> Unit = {},
    preferredGames: List<String> = emptyList(),
    onlyPreferredGames: Boolean = false,
    onToggleOnlyPreferredGames: () -> Unit = {},
    onAddPreferredGame: (String) -> Unit = {},
    onRemovePreferredGame: (String) -> Unit = {},
    podcastTypeFilter: String = "ALL",
    onSelectPodcastTypeFilter: (String) -> Unit = {}
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    val podcastTypes = remember {
        listOf(
            "ALL" to "ALL PODCASTS",
            "AUDIO" to "🎧 AUDIO ONLY",
            "VIDEO" to "🎥 VIDEO PODCASTS"
        )
    }
    val timeFilterValues = remember { TimeRangeFilter.values() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NothingBlack)
            .padding(vertical = 4.dp)
    ) {
        // Topic Dropdown & Quick Filter Bar wrapping smoothly
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // TOPICS DROPDOWN MENU
            val hasActiveTopicFilter = !onlyOffline && !onlyRecommendations && !onlyReadHistory
            Box {
                Box(
                    modifier = Modifier
                        .testTag("topics_dropdown_button")
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hasActiveTopicFilter) NothingWhite else NothingSurface)
                        .border(
                            width = 1.dp,
                            color = if (hasActiveTopicFilter) NothingWhite else NothingBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { dropdownExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TOPIC: ${selectedCategory.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = if (hasActiveTopicFilter) NothingBlack else NothingTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select Topic Dropdown",
                            tint = if (hasActiveTopicFilter) NothingBlack else NothingTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier
                        .background(NothingDarkGray)
                        .border(1.dp, NothingBorder)
                ) {
                    categories.forEach { category ->
                        val isSelected = category.equals(selectedCategory, ignoreCase = true) && hasActiveTopicFilter
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = if (isSelected) NothingRed else NothingWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            onClick = {
                                onSelectCategory(category)
                                dropdownExpanded = false
                            },
                            modifier = Modifier.testTag("category_pill_$category")
                        )
                    }
                }
            }

            // Quick Filter: Saved Offline
            Box(
                modifier = Modifier
                    .testTag("toggle_offline_pill")
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (onlyOffline) NothingWhite else NothingSurface)
                    .border(
                        width = 1.dp,
                        color = if (onlyOffline) NothingWhite else NothingBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleOffline() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (onlyOffline) "[ 💾 OFFLINE ✓ ]" else "[ 💾 SAVED OFFLINE ]",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (onlyOffline) NothingBlack else NothingTextSecondary,
                    fontWeight = if (onlyOffline) FontWeight.Bold else FontWeight.Medium
                )
            }

            // Quick Filter: Discovered Recommendations
            Box(
                modifier = Modifier
                    .testTag("toggle_recommendations_pill")
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (onlyRecommendations) NothingRed else NothingSurface)
                    .border(
                        width = 1.dp,
                        color = if (onlyRecommendations) NothingRed else NothingBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleRecommendations() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (onlyRecommendations) "[ ⚡ DISCOVERED ✓ ]" else "[ ⚡ DISCOVERED FEEDS ]",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (onlyRecommendations) NothingWhite else NothingTextSecondary,
                    fontWeight = if (onlyRecommendations) FontWeight.Bold else FontWeight.Medium
                )
            }

            // Quick Filter: Read History
            Box(
                modifier = Modifier
                    .testTag("toggle_read_history_pill")
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (onlyReadHistory) NothingWhite else NothingSurface)
                    .border(
                        width = 1.dp,
                        color = if (onlyReadHistory) NothingWhite else NothingBorder,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onToggleReadHistory() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (onlyReadHistory) "[ 📖 HISTORY ✓ ]" else "[ 📖 READ HISTORY ]",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (onlyReadHistory) NothingBlack else NothingTextSecondary,
                    fontWeight = if (onlyReadHistory) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        if (subcategories.size > 1) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SUBCAT:",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
                items(subcategories) { subcat ->
                    val isSelected = subcat.equals(selectedSubcategory, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .testTag("subcat_pill_$subcat")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) NothingRed else NothingSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NothingRed else NothingBorder,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectSubcategory(subcat) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = subcat.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) NothingWhite else NothingTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Time Range & Semi-Random Ordering Bar wrapping smoothly
        if (selectedCategory == "GAMING") {
            Spacer(modifier = Modifier.height(6.dp))
            PreferredGamesBar(
                preferredGames = preferredGames,
                onlyPreferredGames = onlyPreferredGames,
                onToggleOnlyPreferred = onToggleOnlyPreferredGames,
                onAddGame = onAddPreferredGame,
                onRemoveGame = onRemovePreferredGame,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (selectedCategory == "PODCASTS") {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(NothingDarkGray)
                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PODCASTS:",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = NothingWhite
                )

                podcastTypes.forEach { (typeKey, typeLabel) ->
                    val isSelected = podcastTypeFilter == typeKey
                    Box(
                        modifier = Modifier
                            .testTag("podcast_type_filter_$typeKey")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSelected) NothingRed else NothingSurface)
                            .border(1.dp, if (isSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                            .clickable { onSelectPodcastTypeFilter(typeKey) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (isSelected) NothingWhite else NothingTextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TIME:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            timeFilterValues.forEach { filter ->
                val isSelected = filter == selectedTimeRange
                Box(
                    modifier = Modifier
                        .testTag("time_filter_${filter.name}")
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) NothingRed.copy(alpha = 0.2f) else NothingSurface)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NothingRed else NothingBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onSelectTimeRange(filter) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NothingRed else NothingTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Semi-Random Shuffle Order Button
            Box(
                modifier = Modifier
                    .testTag("shuffle_order_button")
                    .clip(RoundedCornerShape(4.dp))
                    .background(NothingSurface)
                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                    .clickable { onShuffleOrder() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle Order",
                        tint = NothingWhite,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SHUFFLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
