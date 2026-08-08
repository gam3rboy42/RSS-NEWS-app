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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.DislikeAnalysisResult
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DislikeAnalysisDialog(
    analysis: DislikeAnalysisResult,
    onDismiss: () -> Unit,
    onApplyMuting: (keywords: Set<String>, author: String?, subcategory: String?, feedUrl: String?) -> Unit = { _, _, _, _ -> onDismiss() }
) {
    var selectedKeywords by remember(analysis) {
        mutableStateOf(analysis.dislikedKeywords.toSet())
    }
    var muteAuthorChecked by remember(analysis) { mutableStateOf(false) }
    var muteFeedChecked by remember(analysis) { mutableStateOf(false) }
    var muteSubcategoryChecked by remember(analysis) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(NothingDarkGray)
                .border(1.dp, NothingRed, RoundedCornerShape(8.dp))
                .padding(18.dp)
                .testTag("dislike_analysis_dialog")
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingRed)
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ThumbDown,
                                contentDescription = "Disliked",
                                tint = NothingWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "WHY DID YOU DISLIKE THIS?",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NothingRed
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NothingTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "DISLIKED STORY:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = NothingTextMuted,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = analysis.articleTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NothingWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingSurface)
                        .border(1.dp, NothingBorder, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "SELECT REASONS TO MUTE (OPTIONAL)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NothingWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose what to filter out. Unselected options will NOT block future content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTextSecondary,
                            fontSize = 11.sp
                        )

                        // 1. Keywords Section
                        if (analysis.dislikedKeywords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "TOPICS / KEYWORDS:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = NothingTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                analysis.dislikedKeywords.forEach { kw ->
                                    val isSelected = selectedKeywords.contains(kw)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) NothingRed.copy(alpha = 0.25f) else NothingDarkGray)
                                            .border(
                                                1.dp,
                                                if (isSelected) NothingRed else NothingBorder,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                selectedKeywords = if (isSelected) {
                                                    selectedKeywords - kw
                                                } else {
                                                    selectedKeywords + kw
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = NothingRed,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = "#$kw",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isSelected) NothingRed else NothingTextMuted
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Feed Source Option
                        if (analysis.feedTitle.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { muteFeedChecked = !muteFeedChecked }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = muteFeedChecked,
                                    onCheckedChange = { muteFeedChecked = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NothingRed,
                                        uncheckedColor = NothingBorder
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mute publisher '${analysis.feedTitle}'",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (muteFeedChecked) NothingWhite else NothingTextSecondary,
                                    fontWeight = if (muteFeedChecked) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // 3. Author Option
                        if (!analysis.dislikedAuthor.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { muteAuthorChecked = !muteAuthorChecked }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = muteAuthorChecked,
                                    onCheckedChange = { muteAuthorChecked = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NothingRed,
                                        uncheckedColor = NothingBorder
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mute author '${analysis.dislikedAuthor}'",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (muteAuthorChecked) NothingWhite else NothingTextSecondary,
                                    fontWeight = if (muteAuthorChecked) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        // 4. Subcategory Option
                        if (analysis.dislikedSubcategory.isNotBlank() && analysis.dislikedSubcategory != "GENERAL") {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { muteSubcategoryChecked = !muteSubcategoryChecked }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = muteSubcategoryChecked,
                                    onCheckedChange = { muteSubcategoryChecked = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NothingRed,
                                        uncheckedColor = NothingBorder
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mute topic subcategory '${analysis.dislikedSubcategory}'",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (muteSubcategoryChecked) NothingWhite else NothingTextSecondary,
                                    fontWeight = if (muteSubcategoryChecked) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val hasAnyFilter = selectedKeywords.isNotEmpty() || muteFeedChecked || muteAuthorChecked || muteSubcategoryChecked

                Button(
                    onClick = {
                        onApplyMuting(
                            selectedKeywords,
                            if (muteAuthorChecked) analysis.dislikedAuthor else null,
                            if (muteSubcategoryChecked) analysis.dislikedSubcategory else null,
                            if (muteFeedChecked) analysis.feedUrl else null
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NothingRed,
                        contentColor = NothingWhite
                    )
                ) {
                    Text(
                        text = if (hasAnyFilter) "APPLY MUTING PREFERENCES" else "JUST HIDE THIS STORY",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                if (hasAnyFilter) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NothingBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NothingTextMuted
                        )
                    ) {
                        Text(
                            text = "JUST HIDE THIS STORY (NO MUTING)",
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
