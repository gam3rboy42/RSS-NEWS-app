package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DefaultFeedCatalog
import com.example.data.model.TimeRangeFilter
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@Composable
fun CategoryPillBar(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    categories: List<String> = DefaultFeedCatalog.categories,
    selectedTimeRange: TimeRangeFilter = TimeRangeFilter.ALL_TIME,
    onSelectTimeRange: (TimeRangeFilter) -> Unit = {},
    onShuffleOrder: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NothingBlack)
            .padding(vertical = 4.dp)
    ) {
        // Category Pills
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = category.equals(selectedCategory, ignoreCase = true)

                Box(
                    modifier = Modifier
                        .testTag("category_pill_$category")
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) NothingWhite else NothingSurface
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NothingWhite else NothingBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onSelectCategory(category) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "[ $category ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) NothingBlack else NothingTextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Time Range & Semi-Random Ordering Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Range Options
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIME:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted,
                    fontWeight = FontWeight.Bold
                )
                
                TimeRangeFilter.values().forEach { filter ->
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
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
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
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
