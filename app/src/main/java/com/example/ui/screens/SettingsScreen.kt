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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingDealOrange
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import com.example.ui.viewmodel.RssViewModel

import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.FeedEntity
import com.example.ui.components.CategoryAssignDialog
import com.example.util.InAppBrowser

@Composable
fun SettingsScreen(viewModel: RssViewModel) {
    val context = LocalContext.current
    val allFeeds by viewModel.allFeeds.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val hideDeals by viewModel.hideDeals.collectAsState()
    val onlyPreferred by viewModel.onlyPreferredFeeds.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    var editingFeedForFolder by remember { mutableStateOf<FeedEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Top Header
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
                    text = "FEED & DEAL SETTINGS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NothingWhite
                )
            }
            Text(
                text = "SET PREFERRED SOURCES, DEAL FILTERS & OFFLINE PREFERENCES",
                style = MaterialTheme.typography.labelSmall,
                color = NothingTextMuted
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
        ) {
            // DEAL FILTERING RULE
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingBorder, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "FILTER OUT DEAL ARTICLES",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NothingWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Automatically detects discount, sale, coupon, promo and affiliate offer headlines.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NothingTextMuted
                                )
                            }

                            Switch(
                                checked = hideDeals,
                                onCheckedChange = { viewModel.toggleHideDeals() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NothingWhite,
                                    checkedTrackColor = NothingRed,
                                    uncheckedThumbColor = NothingTextMuted,
                                    uncheckedTrackColor = NothingSurface
                                ),
                                modifier = Modifier.testTag("deal_filter_switch")
                            )
                        }
                    }
                }
            }

            // PREFERRED SOURCES SECTION
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "PREFERRED RSS SOURCES (${allFeeds.count { it.isPreferred }}/${allFeeds.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NothingWhite
                )
                Text(
                    text = "Stories from preferred feeds are ranked higher and chosen first in stacked coverage.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // List of subscribed feeds with preferred star & enable toggle
            items(
                items = allFeeds,
                key = { it.url }
            ) { feed ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(
                            1.dp,
                            if (feed.isPreferred) NothingRed else NothingBorder,
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
                                    text = feed.title.uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (feed.isEnabled) NothingWhite else NothingTextMuted
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Folder/Category Chip Button
                                Box(
                                    modifier = Modifier
                                        .testTag("feed_folder_chip_${feed.title}")
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(NothingSurface)
                                        .border(1.dp, NothingBorder, RoundedCornerShape(2.dp))
                                        .clickable { editingFeedForFolder = feed }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "📁 ${feed.category.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NothingWhite,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (feed.isPreferred) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(NothingRed)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "★ PREFERRED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NothingWhite,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = feed.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingTextMuted,
                                fontSize = 10.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Edit Feed Details
                            IconButton(
                                onClick = { editingFeedForFolder = feed },
                                modifier = Modifier
                                    .testTag("edit_feed_details_${feed.title}")
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Feed Details",
                                    tint = NothingRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Open in-app browser
                            IconButton(
                                onClick = { InAppBrowser.openUrl(context, feed.url) },
                                modifier = Modifier
                                    .testTag("feed_web_preview_${feed.title}")
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Open Web",
                                    tint = NothingWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Star preferred toggle
                            IconButton(
                                onClick = { viewModel.toggleFeedPreferred(feed.url, feed.isPreferred) },
                                modifier = Modifier
                                    .testTag("feed_preferred_star_${feed.title}")
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (feed.isPreferred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Preferred",
                                    tint = if (feed.isPreferred) NothingRed else NothingTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Enable toggle
                            Switch(
                                checked = feed.isEnabled,
                                onCheckedChange = { viewModel.toggleFeedEnabled(feed.url, feed.isEnabled) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NothingWhite,
                                    checkedTrackColor = NothingRed,
                                    uncheckedThumbColor = NothingTextMuted,
                                    uncheckedTrackColor = NothingSurface
                                ),
                                modifier = Modifier
                                    .testTag("feed_enable_switch_${feed.title}")
                                    .size(36.dp)
                            )
                        }
                    }
                }
            }

            // OFFLINE CACHE SECTION
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingBorder, RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "OFFLINE STORAGE & CACHE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NothingWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Articles are saved locally in SQLite Room DB for reading without network connection.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTextMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { viewModel.refreshNews() },
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingSurface,
                                contentColor = NothingWhite
                            ),
                            modifier = Modifier
                                .testTag("sync_now_button")
                                .fillMaxWidth()
                                .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    tint = NothingRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "FORCE FEEDS SYNC NOW",
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

    // Edit Feed Details Dialog
    if (editingFeedForFolder != null) {
        CategoryAssignDialog(
            feed = editingFeedForFolder!!,
            availableCategories = availableCategories,
            onDismiss = { editingFeedForFolder = null },
            onSaveFeed = { newTitle, newUrl, newCategory ->
                viewModel.updateFeedDetails(editingFeedForFolder!!, newTitle, newUrl, newCategory)
                editingFeedForFolder = null
            }
        )
    }
}
