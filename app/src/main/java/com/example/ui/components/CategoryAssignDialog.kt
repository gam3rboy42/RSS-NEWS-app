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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryAssignDialog(
    feed: FeedEntity,
    availableCategories: List<String>,
    onDismiss: () -> Unit,
    onSaveFeed: (newTitle: String, newUrl: String, newCategory: String) -> Unit,
    onSaveCategory: ((String) -> Unit)? = null
) {
    var editableTitle by remember { mutableStateOf(feed.title) }
    var editableUrl by remember { mutableStateOf(feed.url) }
    var selectedFolder by remember { mutableStateOf(feed.category) }
    var customFolderName by remember { mutableStateOf("") }

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
                                finalCategory.ifBlank { feed.category }
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

