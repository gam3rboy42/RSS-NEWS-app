package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DefaultFeedCatalog
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingSurfaceVariant
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TeachCategoryDialog(
    itemTitle: String,
    currentCategory: String,
    currentSubcategory: String = "",
    sourceFeedName: String = "",
    isFeedLevel: Boolean = false,
    onDismiss: () -> Unit,
    onSaveAndTrain: (newCategory: String, newSubcategory: String, updateSourceFeed: Boolean) -> Unit
) {
    val availableCategories = remember {
        listOf("AI", "TECH", "GAMING", "SCIENCE", "BUSINESS", "DESIGN", "WORLD", "PODCASTS", "LIFESTYLE")
    }

    var selectedCategory by remember {
        mutableStateOf(currentCategory.uppercase().trim().ifBlank { "TECH" })
    }
    var subcategoryText by remember {
        mutableStateOf(currentSubcategory.uppercase().trim())
    }
    var trainSourceFeedAlso by remember {
        mutableStateOf(isFeedLevel || sourceFeedName.isNotBlank())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NothingSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, NothingBorder, RoundedCornerShape(8.dp))
            .testTag("teach_category_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Teach NPU",
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFeedLevel) "TEACH FEED CATEGORY" else "LABEL & TEACH NPU",
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
                        tint = NothingTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Item Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingDarkGray)
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            text = if (isFeedLevel) "FEED: $itemTitle" else itemTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = NothingWhite,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "CURRENT: ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NothingTextMuted
                            )
                            Text(
                                text = currentCategory.ifBlank { "UNCATEGORIZED" }.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NothingRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SELECT CORRECT CATEGORY:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NothingTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Category Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableCategories.forEach { category ->
                        val isSelected = selectedCategory.equals(category, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .testTag("cat_chip_${category.lowercase()}")
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) NothingRed else NothingDarkGray)
                                .border(
                                    1.dp,
                                    if (isSelected) NothingRed else NothingBorder,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    selectedCategory = category
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = category,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NothingWhite else NothingTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optional Subcategory
                Text(
                    text = "OPTIONAL SUBCATEGORY / TOPIC:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NothingTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = subcategoryText,
                    onValueChange = { subcategoryText = it.uppercase() },
                    placeholder = {
                        Text(
                            "e.g. LLMS, ASTRONOMY, CRYPTO, REVIEWS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NothingTextMuted
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NothingRed,
                        unfocusedBorderColor = NothingBorder,
                        focusedTextColor = NothingWhite,
                        unfocusedTextColor = NothingWhite,
                        cursorColor = NothingRed
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("subcategory_input")
                )

                if (!isFeedLevel && sourceFeedName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { trainSourceFeedAlso = !trainSourceFeedAlso }
                    ) {
                        Checkbox(
                            checked = trainSourceFeedAlso,
                            onCheckedChange = { trainSourceFeedAlso = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = NothingRed,
                                checkmarkColor = NothingWhite,
                                uncheckedColor = NothingTextMuted
                            ),
                            modifier = Modifier.testTag("train_source_checkbox")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Also teach model for source feed ($sourceFeedName)",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Explainability Note
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingSurfaceVariant)
                        .border(1.dp, NothingRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Neural Learning",
                            tint = NothingRed,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Continual Learning Active: Saving this label teaches the on-device NPU tensor engine to recognize these keywords and domain for future categorization.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingTextMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveAndTrain(selectedCategory, subcategoryText, trainSourceFeedAlso)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingRed,
                    contentColor = NothingWhite
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("confirm_teach_button")
            ) {
                Text(
                    text = "⚡ TRAIN & SAVE",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "CANCEL",
                    fontFamily = FontFamily.Monospace,
                    color = NothingTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    )
}
