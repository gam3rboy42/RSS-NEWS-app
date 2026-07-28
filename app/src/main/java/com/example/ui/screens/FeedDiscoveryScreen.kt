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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DefaultFeedCatalog
import com.example.data.model.FeedDiscoveryItem
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

@Composable
fun FeedDiscoveryScreen(viewModel: RssViewModel) {
    val allFeeds by viewModel.allFeeds.collectAsState()

    var customUrl by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var customCategory by remember { mutableStateOf("TECH") }
    var discoveryCategory by remember { mutableStateOf("ALL") }
    var addResultMsg by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val subscribedUrls = remember(allFeeds) { allFeeds.map { it.url }.toSet() }
    val preferredUrls = remember(allFeeds) { allFeeds.filter { it.isPreferred }.map { it.url }.toSet() }

    val filteredCurated = remember(discoveryCategory) {
        if (discoveryCategory == "ALL") DefaultFeedCatalog.curatedFeeds
        else DefaultFeedCatalog.curatedFeeds.filter { it.category.equals(discoveryCategory, ignoreCase = true) }
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
                    text = "FIND RSS FEEDS",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = NothingWhite
                )
            }
            Text(
                text = "EXPLORE CURATED SOURCES OR ADD YOUR OWN RSS / ATOM FEED URL",
                style = MaterialTheme.typography.labelSmall,
                color = NothingTextMuted
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
        ) {
            // CUSTOM RSS URL ADDER CARD
            item {
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
                                        addResultMsg = "Validating & fetching RSS feed..."
                                        viewModel.addCustomFeedUrl(
                                            url = customUrl.trim(),
                                            title = customTitle.ifBlank { "Custom Feed" },
                                            category = customCategory
                                        ) { success ->
                                            isSubmitting = false
                                            if (success) {
                                                addResultMsg = "✔ SUCCESS! Feed added and cached."
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

            // CURATED DIRECTORY SECTION
            item {
                Spacer(modifier = Modifier.height(12.dp))
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
                    onSelectCategory = { discoveryCategory = it }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Curated Feeds List
            items(
                items = filteredCurated,
                key = { it.url }
            ) { item ->
                val isSubscribed = subscribedUrls.contains(item.url)
                val isPreferred = preferredUrls.contains(item.url)

                Box(
                    modifier = Modifier
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

                        // Actions: Subscribe & Preferred Star Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Star Preferred Toggle
                            Box(
                                modifier = Modifier
                                    .testTag("toggle_preferred_feed_${item.title}")
                                    .clip(CircleShape)
                                    .background(if (isPreferred) NothingRed.copy(alpha = 0.2f) else NothingSurface)
                                    .border(1.dp, if (isPreferred) NothingRed else NothingBorder, CircleShape)
                                    .clickable {
                                        viewModel.toggleFeedPreferred(item.url, isPreferred)
                                    }
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
                                    .clickable {
                                        if (!isSubscribed) {
                                            viewModel.addCustomFeedUrl(item.url, item.title, item.category) {}
                                        } else {
                                            viewModel.deleteFeed(item.url)
                                        }
                                    }
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
        }
    }
}
