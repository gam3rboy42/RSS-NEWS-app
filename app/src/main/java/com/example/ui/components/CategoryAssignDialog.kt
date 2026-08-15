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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.window.Dialog
import com.example.data.local.FeedEntity
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.FeedCategoryAutoTagger

import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.util.PodcastMetadataGrabber
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryAssignDialog(
    feed: FeedEntity,
    availableCategories: List<String>,
    onDismiss: () -> Unit,
    onSaveFeed: (newTitle: String, newUrl: String, newCategory: String, newIconUrl: String, isPreferred: Boolean, preferredScope: String) -> Unit,
    onSaveCategory: ((String) -> Unit)? = null
) {
    var editableTitle by remember { mutableStateOf(feed.title) }
    var editableUrl by remember { mutableStateOf(feed.url) }
    var editableIconUrl by remember { mutableStateOf(feed.iconUrl) }
    var selectedFolder by remember { mutableStateOf(feed.category) }
    var customFolderName by remember { mutableStateOf("") }
    var isPreferred by remember { mutableStateOf(feed.isPreferred) }
    var preferredScope by remember { mutableStateOf(feed.preferredScope) }
    val coroutineScope = rememberCoroutineScope()
    var isGrabbingArtwork by remember { mutableStateOf(false) }

    val activeCategory = if (customFolderName.isNotBlank()) customFolderName.trim().uppercase() else selectedFolder.trim().uppercase()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = NothingDarkGray,
            border = androidx.compose.foundation.BorderStroke(1.dp, NothingBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Feed",
                            tint = NothingRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EDIT FEED DETAILS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = NothingWhite
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NothingTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Editable Feed Title
                Text(
                    text = "FEED NAME / TITLE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = editableTitle,
                    onValueChange = { editableTitle = it },
                    placeholder = {
                        Text(
                            "Feed title...",
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
                        .testTag("edit_feed_title_input")
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Editable Feed URL
                Text(
                    text = "RSS FEED URL:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = editableUrl,
                    onValueChange = { editableUrl = it },
                    placeholder = {
                        Text(
                            "https://example.com/rss.xml",
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
                        .testTag("edit_feed_url_input")
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                val context = LocalContext.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ASSIGN CATEGORY / FOLDER:",
                        style = MaterialTheme.typography.labelSmall,
                        color = com.example.ui.theme.NothingWhite,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .testTag("auto_detect_category_button")
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingSurface)
                            .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                            .clickable {
                                val detected = FeedCategoryAutoTagger.detectCategory(
                                    context = context,
                                    feedUrl = editableUrl,
                                    feedTitle = editableTitle,
                                    feedDescription = feed.description
                                )
                                selectedFolder = detected
                                customFolderName = ""
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⚡ AUTO-DETECT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NothingRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Flow of existing folders/categories
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableCategories.filter { it != "ALL" }.forEach { cat ->
                        val isSelected = cat.equals(selectedFolder, ignoreCase = true) && customFolderName.isBlank()
                        Box(
                            modifier = Modifier
                                .testTag("select_folder_chip_$cat")
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) NothingRed else NothingSurface)
                                .border(1.dp, if (isSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                                .clickable {
                                    selectedFolder = cat
                                    customFolderName = ""
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) NothingWhite else NothingTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = customFolderName,
                    onValueChange = { customFolderName = it },
                    placeholder = {
                        Text(
                            "OR NEW FOLDER (e.g. TECH, NEWS...)",
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
                        .testTag("custom_folder_input")
                        .fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Preferred Source Scope Selection
                Text(
                    text = "PREFERRED SOURCE STATUS & SCOPE:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Option 1: Not Preferred
                    val isOffSelected = !isPreferred
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("preferred_scope_off")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isOffSelected) NothingSurface else NothingDarkGray)
                            .border(1.dp, if (isOffSelected) NothingWhite else NothingBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                isPreferred = false
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OFF",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isOffSelected) NothingWhite else NothingTextMuted
                        )
                    }

                    // Option 2: Category Only Preferred
                    val isCatSelected = isPreferred && preferredScope == FeedEntity.SCOPE_CATEGORY
                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("preferred_scope_category")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isCatSelected) NothingRed.copy(alpha = 0.25f) else NothingDarkGray)
                            .border(1.dp, if (isCatSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                isPreferred = true
                                preferredScope = FeedEntity.SCOPE_CATEGORY
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "★ ${activeCategory.ifBlank { "CATEGORY" }} ONLY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCatSelected) NothingRed else NothingTextSecondary,
                            maxLines = 1
                        )
                    }

                    // Option 3: All Feeds Preferred
                    val isAllSelected = isPreferred && (preferredScope == FeedEntity.SCOPE_ALL || preferredScope.isBlank())
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("preferred_scope_all")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isAllSelected) NothingRed else NothingDarkGray)
                            .border(1.dp, if (isAllSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                isPreferred = true
                                preferredScope = FeedEntity.SCOPE_ALL
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "★ ALL FEEDS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isAllSelected) NothingWhite else NothingTextSecondary,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (!isPreferred) {
                        "Standard source (no special boost)."
                    } else if (preferredScope == FeedEntity.SCOPE_CATEGORY) {
                        "★ Preferred only inside the $activeCategory folder (keeps your main ALL stream balanced)."
                    } else {
                        "★ Preferred everywhere (top priority in ALL stream and in $activeCategory)."
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = if (isPreferred) NothingRed else NothingTextMuted,
                    lineHeight = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Podcast/Feed Artwork URL Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SHOW ART / COVER ICON URL:",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingWhite,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingSurface)
                            .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                            .clickable {
                                isGrabbingArtwork = true
                                coroutineScope.launch {
                                    val grabbed = PodcastMetadataGrabber.autoGrabBestThumbnail(
                                        editableTitle.ifBlank { feed.title },
                                        "",
                                        editableUrl.ifBlank { feed.url }
                                    )
                                    if (!grabbed.isNullOrBlank()) {
                                        editableIconUrl = grabbed
                                    }
                                    isGrabbingArtwork = false
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isGrabbingArtwork) "SEARCHING..." else "✨ AUTO GRAB ART",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NothingRed
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingSurface)
                            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (editableIconUrl.isNotBlank()) {
                            AsyncImage(
                                model = editableIconUrl,
                                contentDescription = "Feed Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = NothingTextMuted, modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = editableIconUrl,
                        onValueChange = { editableIconUrl = it },
                        placeholder = {
                            Text(
                                "https://.../cover.jpg",
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
                            .weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val finalCategory = if (customFolderName.isNotBlank()) customFolderName else selectedFolder
                            onSaveFeed(
                                editableTitle.ifBlank { feed.title },
                                editableUrl.ifBlank { feed.url },
                                finalCategory.ifBlank { feed.category },
                                editableIconUrl.trim(),
                                isPreferred,
                                preferredScope
                            )
                            onSaveCategory?.invoke(finalCategory)
                        },
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingRed,
                            contentColor = NothingWhite
                        ),
                        modifier = Modifier.testTag("save_feed_button")
                    ) {
                        Text(
                            text = "SAVE CHANGES",
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

