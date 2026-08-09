package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: RssViewModel) {
    val context = LocalContext.current
    val allFeeds by viewModel.allFeeds.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val hideDeals by viewModel.hideDeals.collectAsState()
    val onlyPreferred by viewModel.onlyPreferredFeeds.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val refreshInterval by viewModel.backgroundRefreshIntervalMinutes.collectAsState()
    val mutedKeywords by viewModel.mutedKeywords.collectAsState()
    val mutedAuthors by viewModel.mutedAuthors.collectAsState()
    val mutedSubcategories by viewModel.mutedSubcategories.collectAsState()
    val autoDownloadWifi by viewModel.autoDownloadWifi.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.sendTestNotification()
        }
    }

    val scope = rememberCoroutineScope()
    var editingFeedForFolder by remember { mutableStateOf<FeedEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportedOpmlText by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importInputText by remember { mutableStateOf("") }

    var jsonExportStringForSave by remember { mutableStateOf("") }
    var showJsonPreviewDialog by remember { mutableStateOf(false) }
    var exportedJsonText by remember { mutableStateOf("") }

    val createJsonFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonExportStringForSave.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Saved JSON feed & tag backup to device!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val openJsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    scope.launch {
                        val res = viewModel.importFeedsFromJson(content)
                        Toast.makeText(
                            context,
                            "Imported ${res.importedFeedsCount} feed(s) and tag rules from JSON backup!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing JSON file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val opmlPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    scope.launch {
                        val count = viewModel.importOpmlXml(content)
                        Toast.makeText(context, "Successfully imported $count feed(s) from OPML!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading OPML file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

            // BACKGROUND REFRESH & PERIODIC ARTICLE NOTIFICATIONS
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
                        Text(
                            text = "BACKGROUND REFRESH & NOTIFICATIONS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NothingWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Periodically syncs RSS feeds in the background and sends article suggestions with title, source, author, and article picture.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTextMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "SYNC FREQUENCY",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = NothingTextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val options = listOf(
                            15L to "15 MIN",
                            60L to "1 HOUR",
                            180L to "3 HOURS",
                            360L to "6 HOURS",
                            0L to "OFF"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            options.forEach { (minutes, label) ->
                                val isSelected = refreshInterval == minutes
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) NothingRed else NothingSurface)
                                        .border(1.dp, if (isSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.setBackgroundRefreshInterval(minutes) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) NothingWhite else NothingTextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Test Notification Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.sendTestNotification()
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingSurface,
                                    contentColor = NothingWhite
                                ),
                                modifier = Modifier
                                    .testTag("send_test_notification_button")
                                    .weight(1f)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Test Notification",
                                        tint = NothingRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TEST NEWS ALERT",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.sendTestPodcastNotification()
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingSurface,
                                    contentColor = NothingWhite
                                ),
                                modifier = Modifier
                                    .testTag("send_test_podcast_notification_button")
                                    .weight(1f)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Test Podcast Notification",
                                        tint = NothingRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "TEST PODCAST ALERT",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MUTED / DISLIKED TOPICS & AUTHORS SECTION
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingRed.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Muted",
                                    tint = NothingRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MUTED TOPICS & AUTHORS",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NothingWhite
                                )
                            }
                            val totalMuted = mutedKeywords.size + mutedAuthors.size + mutedSubcategories.size
                            Text(
                                text = "($totalMuted MUTED)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NothingTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "When you dislike articles, topics, subcategories, and authors are automatically extracted and filtered out on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTextMuted
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (mutedKeywords.isEmpty() && mutedAuthors.isEmpty() && mutedSubcategories.isEmpty()) {
                            Text(
                                text = "No muted topics yet. Tap the dislike button on any article to auto-mute.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = NothingTextSecondary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            if (mutedKeywords.isNotEmpty()) {
                                Text(
                                    text = "MUTED KEYWORDS:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = NothingTextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    mutedKeywords.forEach { kw ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(NothingSurface)
                                                .border(1.dp, NothingRed.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                                                .clickable { viewModel.unmuteKeyword(kw) }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "#$kw",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = NothingRed
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Unmute",
                                                    tint = NothingRed,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (mutedAuthors.isNotEmpty()) {
                                Text(
                                    text = "MUTED AUTHORS:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = NothingTextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    mutedAuthors.forEach { author ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(NothingSurface)
                                                .border(1.dp, NothingBorder, RoundedCornerShape(3.dp))
                                                .clickable { viewModel.unmuteAuthor(author) }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = author,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = NothingWhite
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Unmute",
                                                    tint = NothingTextMuted,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (mutedSubcategories.isNotEmpty()) {
                                Text(
                                    text = "MUTED SUBCATEGORIES:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = NothingTextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    mutedSubcategories.forEach { subcat ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(NothingSurface)
                                                .border(1.dp, NothingBorder, RoundedCornerShape(3.dp))
                                                .clickable { viewModel.unmuteSubcategory(subcat) }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = subcat,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = NothingWhite
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Unmute",
                                                    tint = NothingTextMuted,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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

            // PRIVACY & ON-DEVICE GUARANTEE SECTION
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Privacy",
                                tint = NothingRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "100% ON-DEVICE PRIVACY GUARANTEE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = NothingWhite
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "All story clustering, dislike analysis, category auto-tagging, deal filtering, and reading history run locally on your device in a local Room database. Your data never leaves your device unless you choose to export it to transfer to a new phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTextMuted
                        )
                    }
                }
            }

            // EXPORT / IMPORT & NEW PHONE MIGRATION
            item {
                Spacer(modifier = Modifier.height(16.dp))
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
                            text = "OFFLINE DEVICE MIGRATION & BACKUP",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NothingWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Export your feed list, tags, categories, and custom auto-tagger rules directly into a JSON or OPML file. Save locally to your device storage to keep all data 100% private and off-cloud.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTextMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Primary JSON Export / Import Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val jsonString = viewModel.exportFeedsToJson()
                                        jsonExportStringForSave = jsonString
                                        createJsonFileLauncher.launch("nothing_rss_feeds_backup.json")
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingRed,
                                    contentColor = NothingWhite
                                ),
                                modifier = Modifier
                                    .testTag("export_json_button")
                                    .weight(1f)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FileUpload,
                                        contentDescription = "Save JSON",
                                        tint = NothingWhite,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "SAVE JSON TO DEVICE",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    openJsonPickerLauncher.launch("*/*")
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingSurface,
                                    contentColor = NothingWhite
                                ),
                                modifier = Modifier
                                    .testTag("import_json_button")
                                    .weight(1f)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FileDownload,
                                        contentDescription = "Import JSON",
                                        tint = NothingRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "IMPORT JSON FILE",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Secondary Row: Preview/Share JSON & OPML Options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        exportedJsonText = viewModel.exportFeedsToJson()
                                        showJsonPreviewDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingSurface,
                                    contentColor = NothingWhite
                                ),
                                modifier = Modifier
                                    .testTag("preview_json_button")
                                    .weight(1f)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = "PREVIEW / SHARE JSON",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        exportedOpmlText = viewModel.exportOpml()
                                        showExportDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NothingSurface,
                                    contentColor = NothingWhite
                                ),
                                modifier = Modifier
                                    .testTag("export_opml_button")
                                    .weight(1f)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = "OPML BACKUP",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // OFFLINE CACHE SECTION
            item {
                Spacer(modifier = Modifier.height(16.dp))
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AUTO-DOWNLOAD PODCASTS ON WI-FI",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = NothingWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Automatically download audio files for new episodes of preferred podcasts when connected to Wi-Fi",
                                    fontSize = 10.sp,
                                    color = NothingTextMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = autoDownloadWifi,
                                onCheckedChange = { viewModel.setAutoDownloadWifi(it) },
                                modifier = Modifier.testTag("auto_download_wifi_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NothingWhite,
                                    checkedTrackColor = NothingRed,
                                    uncheckedThumbColor = NothingTextMuted,
                                    uncheckedTrackColor = NothingSurface
                                )
                            )
                        }

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

    // Export JSON Preview Dialog
    if (showJsonPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showJsonPreviewDialog = false },
            title = {
                Text(
                    text = "JSON FEED & TAGS BACKUP",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NothingWhite
                )
            },
            text = {
                Column {
                    Text(
                        text = "100% Private, On-Device JSON Export containing your feeds, categories, tags, and auto-tagger rules:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NothingRed,
                            unfocusedBorderColor = NothingBorder,
                            focusedTextColor = NothingWhite,
                            unfocusedTextColor = NothingWhite
                        )
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            jsonExportStringForSave = exportedJsonText
                            showJsonPreviewDialog = false
                            createJsonFileLauncher.launch("nothing_rss_feeds_backup.json")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                    ) {
                        Text("SAVE FILE", fontFamily = FontFamily.Monospace, color = NothingWhite, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_SUBJECT, "Nothing RSS Feeds & Tags Backup")
                                putExtra(Intent.EXTRA_TEXT, exportedJsonText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share JSON Backup"))
                            showJsonPreviewDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingSurface)
                    ) {
                        Text("SHARE", fontFamily = FontFamily.Monospace, color = NothingWhite, fontSize = 11.sp)
                    }
                }
            },
            dismissButton = {
                Button(
                    onClick = { showJsonPreviewDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingSurface)
                ) {
                    Text("CLOSE", fontFamily = FontFamily.Monospace, color = NothingWhite, fontSize = 11.sp)
                }
            },
            containerColor = NothingDarkGray,
            titleContentColor = NothingWhite
        )
    }

    // Export OPML Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "EXPORT OPML SUBSCRIPTIONS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NothingWhite
                )
            },
            text = {
                Column {
                    Text(
                        text = "Share or copy your subscriptions backup string below:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportedOpmlText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NothingRed,
                            unfocusedBorderColor = NothingBorder,
                            focusedTextColor = NothingWhite,
                            unfocusedTextColor = NothingWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/xml"
                            putExtra(Intent.EXTRA_SUBJECT, "Nothing RSS OPML Subscriptions Backup")
                            putExtra(Intent.EXTRA_TEXT, exportedOpmlText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share OPML Backup"))
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                ) {
                    Text("SHARE OPML", fontFamily = FontFamily.Monospace, color = NothingWhite)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingSurface)
                ) {
                    Text("CLOSE", fontFamily = FontFamily.Monospace, color = NothingWhite)
                }
            },
            containerColor = NothingDarkGray,
            titleContentColor = NothingWhite
        )
    }

    // Import OPML Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text(
                    text = "IMPORT OPML SUBSCRIPTIONS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NothingWhite
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose an OPML file from device storage or paste OPML XML content below:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NothingTextMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            showImportDialog = false
                            opmlPickerLauncher.launch("*/*")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Pick File",
                                tint = NothingRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("PICK .OPML FILE FROM DEVICE", fontFamily = FontFamily.Monospace, color = NothingWhite, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = { Text("Or paste <opml> XML content here...", color = NothingTextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NothingRed,
                            unfocusedBorderColor = NothingBorder,
                            focusedTextColor = NothingWhite,
                            unfocusedTextColor = NothingWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importInputText.isNotBlank()) {
                            scope.launch {
                                val count = viewModel.importOpmlXml(importInputText)
                                Toast.makeText(context, "Successfully imported $count feed(s)!", Toast.LENGTH_LONG).show()
                                importInputText = ""
                                showImportDialog = false
                            }
                        }
                    },
                    enabled = importInputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = NothingRed)
                ) {
                    Text("IMPORT TEXT", fontFamily = FontFamily.Monospace, color = NothingWhite)
                }
            },
            dismissButton = {
                Button(
                    onClick = { showImportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NothingSurface)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Monospace, color = NothingWhite)
                }
            },
            containerColor = NothingDarkGray,
            titleContentColor = NothingWhite
        )
    }

    // Edit Feed Details Dialog
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
}
