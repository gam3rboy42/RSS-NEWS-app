package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import com.example.data.local.ArticleEntity
import com.example.data.model.StoryCluster
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@Composable
fun StoryStackDialog(
    cluster: StoryCluster,
    onDismiss: () -> Unit,
    onSelectSourceArticle: (ArticleEntity) -> Unit,
    onDecoupleArticle: ((ArticleEntity) -> Unit)? = null,
    onDecoupleCluster: ((StoryCluster) -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NothingRed, RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
            color = NothingBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STACKED STORY SOURCES",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = NothingWhite
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDecoupleCluster != null && cluster.articles.size > 1) {
                            TextButton(
                                onClick = {
                                    onDecoupleCluster(cluster)
                                    onDismiss()
                                },
                                modifier = Modifier.testTag("decouple_all_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CallSplit,
                                        contentDescription = "Decouple All",
                                        tint = NothingRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "UNSTACK ALL",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NothingRed
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = NothingWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = cluster.primaryTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NothingWhite
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Select a source to read, or use the menu on any unrelated article to decouple it from this stack:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Sources list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(280.dp)
                ) {
                    items(cluster.articles) { article ->
                        StoryStackItemCard(
                            article = article,
                            onSelectSourceArticle = { selected ->
                                onSelectSourceArticle(selected)
                                onDismiss()
                            },
                            onDecoupleArticle = { toDecouple ->
                                onDecoupleArticle?.invoke(toDecouple)
                                if (cluster.articles.size <= 2) {
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryStackItemCard(
    article: ArticleEntity,
    onSelectSourceArticle: (ArticleEntity) -> Unit,
    onDecoupleArticle: (ArticleEntity) -> Unit
) {
    var showSubmenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .testTag("source_option_${article.feedTitle}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(NothingDarkGray)
            .border(
                1.dp,
                if (article.isPreferredSource) NothingRed else NothingBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable {
                onSelectSourceArticle(article)
            }
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = article.feedTitle.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = NothingWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (article.isPreferredSource) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(NothingRed)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = article.pubDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextMuted
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Box {
                        IconButton(
                            onClick = { showSubmenu = true },
                            modifier = Modifier
                                .testTag("article_submenu_${article.id.hashCode()}")
                                .size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Submenu",
                                tint = NothingTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSubmenu,
                            onDismissRequest = { showSubmenu = false },
                            modifier = Modifier
                                .background(NothingSurface)
                                .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CallSplit,
                                            contentDescription = null,
                                            tint = NothingRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "DECOUPLE / UNSTACK STORY",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NothingWhite
                                        )
                                    }
                                },
                                onClick = {
                                    showSubmenu = false
                                    onDecoupleArticle(article)
                                },
                                modifier = Modifier.testTag("decouple_item_${article.id.hashCode()}")
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                            contentDescription = null,
                                            tint = NothingWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "READ ARTICLE SOURCE",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = NothingWhite
                                        )
                                    }
                                },
                                onClick = {
                                    showSubmenu = false
                                    onSelectSourceArticle(article)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium,
                color = NothingTextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick Decouple Action button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingBlack)
                        .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                        .clickable { onDecoupleArticle(article) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallSplit,
                        contentDescription = "Decouple",
                        tint = NothingTextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "DECOUPLE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = NothingTextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSelectSourceArticle(article) }
                ) {
                    Text(
                        text = "READ SOURCE ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "Read",
                        tint = NothingRed,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
