package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingDealOrange
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingSurfaceVariant
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArticleCard(
    cluster: StoryCluster,
    onArticleClick: (ArticleEntity) -> Unit,
    onChooseSourceClick: (StoryCluster) -> Unit,
    onBookmarkToggle: (String, Boolean) -> Unit,
    onOfflineToggle: (String, Boolean) -> Unit = { _, _ -> },
    onLikeToggle: (String, Boolean) -> Unit = { _, _ -> },
    onDislikeToggle: (String, Boolean) -> Unit = { _, _ -> },
    onSubscribeFeed: (String, String, String) -> Unit = { _, _, _ -> }
) {
    var isExpanded by rememberSaveable(cluster.clusterId) { mutableStateOf(false) }
    var showPodcastMediaDialog by remember { mutableStateOf(false) }

    val primaryArticle = cluster.primaryArticle
    val isStacked = cluster.isStacked
    val isRead = primaryArticle.isRead
    val secondaryArticles = remember(cluster.articles) { cluster.articles.filter { it.id != primaryArticle.id } }

    val context = LocalContext.current
    var effectiveImageUrl by remember(primaryArticle.id, primaryArticle.imageUrl) { mutableStateOf(primaryArticle.imageUrl) }
    val isPodcastEpisode = primaryArticle.isPodcast || primaryArticle.isVideoPodcast || primaryArticle.mediaType == "AUDIO" || primaryArticle.mediaType == "VIDEO" || primaryArticle.category.equals("PODCASTS", ignoreCase = true)

    LaunchedEffect(primaryArticle.id, primaryArticle.imageUrl, primaryArticle.feedUrl) {
        if (effectiveImageUrl.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val db = com.example.data.local.AppDatabase.getDatabase(context)
                    val feed = db.rssDao().getFeedByUrl(primaryArticle.feedUrl)
                    var resolved = feed?.iconUrl?.ifBlank { null }
                    if (resolved.isNullOrBlank() && isPodcastEpisode) {
                        resolved = com.example.util.PodcastMetadataGrabber.autoGrabBestThumbnail(
                            feedTitle = primaryArticle.feedTitle,
                            episodeTitle = primaryArticle.title,
                            feedUrl = primaryArticle.feedUrl
                        )
                        if (!resolved.isNullOrBlank()) {
                            db.rssDao().updateArticleImageUrl(primaryArticle.id, resolved)
                            if (feed != null && feed.iconUrl.isBlank()) {
                                db.rssDao().updateFeedIconUrl(primaryArticle.feedUrl, resolved)
                            }
                        }
                    }
                    if (!resolved.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            effectiveImageUrl = resolved
                        }
                    }
                } catch (e: Exception) {
                    // Ignore resolution errors
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // VISUAL STACK DECK EFFECT (Layered cards behind when collapsed)
        if (isStacked && !isExpanded) {
            // Deepest card layer for 3+ items
            if (cluster.sourceCount > 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .offset(y = 8.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingSurface)
                        .border(1.dp, NothingBorder.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                )
            }

            // Middle card layer for 2+ items
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 7.dp)
                    .offset(y = 4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NothingSurfaceVariant)
                    .border(1.dp, NothingRed.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            )
        }

        // MAIN FRONT CARD CONTAINER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(NothingDarkGray)
                .border(
                    width = 1.dp,
                    color = if (primaryArticle.isDiscoveredRecommendation) NothingRed else if (isStacked) NothingRed.copy(alpha = 0.8f) else NothingBorder,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onArticleClick(primaryArticle) }
                .padding(14.dp)
        ) {
            Column {
                // DISCOVERED FEED RECOMMENDATION BANNER
                if (primaryArticle.isDiscoveredRecommendation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingRed.copy(alpha = 0.2f))
                            .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (primaryArticle.author.isNotBlank()) "⚡ RECOMMENDED (BY ${primaryArticle.author.uppercase()})" else "⚡ RECOMMENDED FEED (MATCHES YOUR LIKES)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = NothingRed,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Box(
                                modifier = Modifier
                                    .testTag("subscribe_discovered_feed_${primaryArticle.id}")
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(NothingRed)
                                    .clickable {
                                        onSubscribeFeed(
                                            primaryArticle.feedUrl,
                                            primaryArticle.feedTitle,
                                            primaryArticle.category
                                        )
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "+ SUBSCRIBE",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    color = NothingWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                // Source Bar & Badges with natural wrapping
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Unread Dot or Read/Played Tag
                        val isPodcastEpisode = primaryArticle.isPodcast || primaryArticle.isVideoPodcast || primaryArticle.mediaType == "AUDIO" || primaryArticle.mediaType == "VIDEO" || primaryArticle.category.equals("PODCASTS", ignoreCase = true)
                        if (!isRead) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(NothingRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .testTag("played_marker_badge")
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isPodcastEpisode) NothingRed.copy(alpha = 0.25f) else NothingSurfaceVariant)
                                    .border(1.dp, if (isPodcastEpisode) NothingRed else NothingBorder, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (isPodcastEpisode) "✓ PLAYED" else "READ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPodcastEpisode) NothingWhite else NothingTextMuted,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
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

                        if (primaryArticle.isPreferredSource) {
                            Spacer(modifier = Modifier.width(6.dp))
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

                        if (primaryArticle.isPodcast || primaryArticle.isVideoPodcast || primaryArticle.mediaType == "AUDIO" || primaryArticle.mediaType == "VIDEO") {
                            Spacer(modifier = Modifier.width(6.dp))
                            val isVid = primaryArticle.isVideoPodcast || primaryArticle.mediaType == "VIDEO"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(NothingSurfaceVariant)
                                    .border(1.dp, NothingRed, RoundedCornerShape(2.dp))
                                    .clickable { showPodcastMediaDialog = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isVid) "🎥 VIDEO PODCAST" else "🎧 AUDIO PODCAST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NothingRed,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "• ${primaryArticle.pubDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextMuted
                    )

                    if (primaryArticle.author.isNotBlank()) {
                        Text(
                            text = "• BY ${primaryArticle.author.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NothingWhite,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "• ${calculateReadingTimeMinutes(primaryArticle)} MIN READ",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
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

                    // Thumbnail / Podcast Cover Box
                    if (!effectiveImageUrl.isNullOrBlank() || isPodcastEpisode) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .testTag("article_thumbnail_box")
                                .size(72.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(NothingSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isPodcastEpisode) NothingRed.copy(alpha = 0.6f) else NothingBorder,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!effectiveImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = effectiveImageUrl,
                                    contentDescription = "Podcast cover artwork",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Headphones,
                                        contentDescription = "Podcast",
                                        tint = NothingRed,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = primaryArticle.feedTitle.ifBlank { "PODCAST" }.take(10).uppercase(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NothingTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer actions & Stacking controls
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

                        // Stacked Story Badge & Inline Expand/Collapse Button
                        if (isStacked) {
                            Box(
                                modifier = Modifier
                                    .testTag("expand_stack_button_${cluster.clusterId}")
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NothingRed)
                                    .clickable { isExpanded = !isExpanded }
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
                                        text = if (isExpanded) "[ HIDE STACK ▲ ]" else "[ STACK: ${cluster.sourceCount} ARTICLES ▼ ]",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NothingWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // Category & Subcategory tag
                            val subcatLabel = cluster.subcategory.ifBlank { primaryArticle.subcategory }
                            val displayTag = if (subcatLabel.isNotBlank() && subcatLabel != "GENERAL") {
                                "${primaryArticle.category.uppercase()} / ${subcatLabel.uppercase()}"
                            } else {
                                primaryArticle.category.uppercase()
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(NothingSurfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = displayTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NothingTextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Optional full dialog trigger if stacked
                        if (isStacked) {
                            Box(
                                modifier = Modifier
                                    .testTag("stacked_sources_button_${cluster.clusterId}")
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(NothingSurface)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(2.dp))
                                    .clickable { onChooseSourceClick(cluster) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "POPUP ↗",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = NothingTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Like Story Toggle Button
                        if (primaryArticle.isPodcast || primaryArticle.isVideoPodcast || primaryArticle.mediaType == "AUDIO" || primaryArticle.mediaType == "VIDEO") {
                            val isVid = primaryArticle.isVideoPodcast || primaryArticle.mediaType == "VIDEO"
                            Box(
                                modifier = Modifier
                                    .testTag("play_podcast_button_${primaryArticle.id}")
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NothingRed)
                                    .clickable { showPodcastMediaDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isVid) "▶ WATCH VIDEO" else "▶ PLAY PODCAST",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = NothingWhite,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = { onLikeToggle(primaryArticle.id, primaryArticle.isLiked) },
                            modifier = Modifier
                                .testTag("like_article_${primaryArticle.id}")
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (primaryArticle.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Like Story",
                                tint = if (primaryArticle.isLiked) NothingRed else NothingTextMuted,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Dislike & Analyze Story Toggle Button
                        IconButton(
                            onClick = { onDislikeToggle(primaryArticle.id, primaryArticle.isDisliked) },
                            modifier = Modifier
                                .testTag("dislike_article_${primaryArticle.id}")
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (primaryArticle.isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                contentDescription = "Dislike & Mute Similar",
                                tint = if (primaryArticle.isDisliked) NothingRed else NothingTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Save For Offline Reading Toggle Button
                        IconButton(
                            onClick = { onOfflineToggle(primaryArticle.id, primaryArticle.isSavedForOffline) },
                            modifier = Modifier
                                .testTag("offline_article_${primaryArticle.id}")
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (primaryArticle.isSavedForOffline) Icons.Filled.DownloadForOffline else Icons.Outlined.DownloadForOffline,
                                contentDescription = "Save for Offline",
                                tint = if (primaryArticle.isSavedForOffline) NothingWhite else NothingTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
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

                // INLINE EXPANDED STACKED ARTICLES
                AnimatedVisibility(
                    visible = isStacked && isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        // Stack Header Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(NothingBorder)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STORY STACK ITEMS (${secondaryArticles.size})",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NothingRed
                            )
                            Text(
                                text = "TAP ARTICLE TO READ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = NothingTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Secondary Articles list
                        secondaryArticles.forEach { article ->
                            Box(
                                modifier = Modifier
                                    .testTag("stacked_sub_article_${article.id}")
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NothingSurface)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                                    .clickable { onArticleClick(article) }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Vertical accent indicator
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(if (article.isPreferredSource) NothingRed else NothingBorder)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = article.feedTitle.uppercase(),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = NothingWhite
                                                )
                                                if (article.isPreferredSource) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "★",
                                                        color = NothingRed,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            Text(
                                                text = article.pubDate,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NothingTextMuted,
                                                fontSize = 9.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = article.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (article.isRead) NothingTextSecondary else NothingWhite,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (!article.imageUrl.isNull_or_blank()) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AsyncImage(
                                            model = article.imageUrl,
                                            contentDescription = "Sub article thumbnail",
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }

                                    IconButton(
                                        onClick = { onBookmarkToggle(article.id, article.isBookmarked) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (article.isBookmarked) NothingRed else NothingTextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Collapse Button at bottom of expanded stack
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingSurfaceVariant)
                                .clickable { isExpanded = false }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = "Collapse Stack",
                                    tint = NothingRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "COLLAPSE STORY STACK",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NothingWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPodcastMediaDialog) {
        PodcastMediaDialog(
            article = primaryArticle,
            onDismiss = { showPodcastMediaDialog = false },
            onOpenInBrowser = { url -> onArticleClick(primaryArticle.copy(link = url)) }
        )
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()

fun calculateReadingTimeMinutes(article: ArticleEntity): Int {
    val fullText = (article.title + " " + article.description + " " + article.content).trim()
    if (fullText.isEmpty()) return 1
    val wordCount = fullText.split(Regex("\\s+")).count { it.isNotBlank() }
    return maxOf(1, (wordCount + 199) / 200)
}
