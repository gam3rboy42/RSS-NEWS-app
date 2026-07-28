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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.ArticleEntity
import com.example.data.model.StoryCluster
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingDealOrange
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingSurfaceVariant
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@Composable
fun ArticleCard(
    cluster: StoryCluster,
    onArticleClick: (ArticleEntity) -> Unit,
    onChooseSourceClick: (StoryCluster) -> Unit,
    onBookmarkToggle: (String, Boolean) -> Unit
) {
    val primaryArticle = cluster.primaryArticle
    val isStacked = cluster.isStacked
    val isRead = primaryArticle.isRead

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(NothingDarkGray)
            .border(
                width = 1.dp,
                color = if (isStacked) NothingRed.copy(alpha = 0.6f) else NothingBorder,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onArticleClick(primaryArticle) }
            .padding(14.dp)
    ) {
        Column {
            // Source Bar & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Unread Dot
                    if (!isRead) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NothingRed)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Feed Title
                    Text(
                        text = primaryArticle.feedTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextSecondary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Preferred Source Tag
                    if (primaryArticle.isPreferredSource) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(NothingRed.copy(alpha = 0.2f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PREFERRED",
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = primaryArticle.pubDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingTextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Article Content & Thumbnail Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Title
                    Text(
                        text = primaryArticle.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isRead) NothingTextSecondary else NothingWhite,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )

                    if (primaryArticle.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = primaryArticle.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NothingTextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Thumbnail if available
                if (!primaryArticle.imageUrl.isNull_or_blank()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    AsyncImage(
                        model = primaryArticle.imageUrl,
                        contentDescription = "Article image",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer actions & Stacking badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Deal Flag Badge if deal
                    if (primaryArticle.isDeal) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(NothingDealOrange.copy(alpha = 0.2f))
                                .border(1.dp, NothingDealOrange, RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEAL / SALE",
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingDealOrange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Stacked Story Badge & Source Chooser Button
                    if (isStacked) {
                        Box(
                            modifier = Modifier
                                .testTag("stacked_sources_button_${cluster.clusterId}")
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingRed)
                                .clickable { onChooseSourceClick(cluster) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Layers,
                                    contentDescription = "Stacked Sources",
                                    tint = NothingWhite,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "[ STACKED: ${cluster.sourceCount} SOURCES ]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NothingWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Category tag
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(NothingSurfaceVariant)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = primaryArticle.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = NothingTextSecondary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                // Bookmark Toggle Button
                IconButton(
                    onClick = { onBookmarkToggle(primaryArticle.id, primaryArticle.isBookmarked) },
                    modifier = Modifier
                        .testTag("bookmark_article_${primaryArticle.id}")
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (primaryArticle.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark Article",
                        tint = if (primaryArticle.isBookmarked) NothingRed else NothingTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
