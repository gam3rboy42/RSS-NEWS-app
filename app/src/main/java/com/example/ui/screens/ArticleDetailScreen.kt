package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.ArticleEntity
import com.example.ui.components.calculateReadingTimeMinutes
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ui.components.PodcastMediaDialog
import com.example.ui.components.DislikeAnalysisDialog
import com.example.ui.viewmodel.RssViewModel
import com.example.util.InAppBrowser

@Composable
fun ArticleDetailScreen(
    article: ArticleEntity,
    viewModel: RssViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val fontSizeMultiplier by viewModel.fontSizeMultiplier.collectAsState()
    var showPodcastDialog by remember { mutableStateOf(false) }

    val titleSize = (20 * fontSizeMultiplier).sp
    val bodySize = (15 * fontSizeMultiplier).sp
    val lineSpacing = (22 * fontSizeMultiplier).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Top Reader Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NothingDarkGray)
                .border(1.dp, NothingBorder)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .testTag("article_back_button")
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NothingWhite
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = article.feedTitle.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NothingWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Font Size Buttons & Preset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${(fontSizeMultiplier * 100).toInt()}%",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = NothingRed,
                    fontSize = 11.sp
                )
            }
        }

        // Accessibility Font Size Slider Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NothingSurface)
                .border(1.dp, NothingBorder)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FONT SIZE",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = NothingTextMuted
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "A",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = NothingWhite
            )
            Slider(
                value = fontSizeMultiplier,
                onValueChange = { viewModel.changeFontSize(it) },
                valueRange = 0.7f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = NothingRed,
                    activeTrackColor = NothingRed,
                    inactiveTrackColor = NothingDarkGray
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("font_size_slider")
            )
            Text(
                text = "A",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = NothingWhite
            )
        }

        // Scrollable Article Reader Body
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Category & PubDate
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = article.category.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }

                Text(
                    text = article.pubDate,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = NothingTextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Article Title
            Text(
                text = article.title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                lineHeight = (titleSize.value * 1.25).sp,
                color = NothingWhite
            )

            if (article.author.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BY ${article.author.uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = NothingRed
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${calculateReadingTimeMinutes(article)} MIN READ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = NothingTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Offline Mode Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NothingSurface)
                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NothingRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OFFLINE CACHED ARTICLE MODE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NothingTextSecondary
                )
            }

            // Deal Flag if deal
            if (article.isDeal) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingDealOrange.copy(alpha = 0.15f))
                        .border(1.dp, NothingDealOrange, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "⚡ DEAL DETECTED: This article contains shopping discounts or promotional offer keywords.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingDealOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Podcast / Media Action Banner if applicable
            if (article.isPodcast || article.isVideoPodcast || article.mediaUrl != null) {
                val isVid = article.isVideoPodcast || article.mediaType == "VIDEO"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(NothingDarkGray)
                        .border(1.dp, NothingRed, RoundedCornerShape(6.dp))
                        .clickable { showPodcastDialog = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isVid) "🎥 VIDEO PODCAST EPISODE" else "🎧 AUDIO PODCAST EPISODE",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = NothingRed
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tap to play stream ${if (!article.duration.isNullOrBlank()) "(${article.duration})" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = NothingTextMuted
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingRed)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isVid) "▶ WATCH" else "▶ PLAY",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = NothingWhite
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Article Main Thumbnail if available
            if (!article.imageUrl.isNull_or_blank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = "Article image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, NothingBorder, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Main Text Content
            val displayContent = article.content.ifBlank { article.description }
            Text(
                text = displayContent,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = bodySize,
                lineHeight = lineSpacing,
                color = NothingWhite
            )

            Spacer(modifier = Modifier.height(28.dp))

            // External Source Button
            Button(
                onClick = {
                    InAppBrowser.openUrl(context, article.link)
                },
                modifier = Modifier
                    .testTag("open_original_source_button")
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, NothingRed, RoundedCornerShape(4.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingDarkGray,
                    contentColor = NothingWhite
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "READ ORIGINAL ON ${article.feedTitle.uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = NothingWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open",
                        tint = NothingRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // PERSISTENT BOTTOM ACTION DOCK WITH INSTANT VISUAL FEEDBACK
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NothingDarkGray)
                .border(1.dp, NothingBorder)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LIKE BUTTON
            val likeBgColor by animateColorAsState(
                targetValue = if (article.isLiked) NothingRed else NothingSurface,
                animationSpec = tween(durationMillis = 100),
                label = "LikeBg"
            )
            val likeTextColor by animateColorAsState(
                targetValue = if (article.isLiked) NothingWhite else NothingTextMuted,
                animationSpec = tween(durationMillis = 100),
                label = "LikeText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(likeBgColor)
                    .border(1.dp, if (article.isLiked) NothingRed else NothingBorder, RoundedCornerShape(6.dp))
                    .testTag("like_article_detail")
                    .clickable {
                        viewModel.toggleLikedStatus(article.id, article.isLiked)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (article.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = likeTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (article.isLiked) "LIKED" else "LIKE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = likeTextColor
                    )
                }
            }

            // DISLIKE BUTTON
            val dislikeBgColor by animateColorAsState(
                targetValue = if (article.isDisliked) NothingRed else NothingSurface,
                animationSpec = tween(durationMillis = 100),
                label = "DislikeBg"
            )
            val dislikeTextColor by animateColorAsState(
                targetValue = if (article.isDisliked) NothingWhite else NothingTextMuted,
                animationSpec = tween(durationMillis = 100),
                label = "DislikeText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(dislikeBgColor)
                    .border(1.dp, if (article.isDisliked) NothingRed else NothingBorder, RoundedCornerShape(6.dp))
                    .testTag("dislike_article_detail")
                    .clickable {
                        viewModel.toggleDislikedStatus(article.id, article.isDisliked)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (article.isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Dislike",
                        tint = dislikeTextColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (article.isDisliked) "MUTED" else "DISLIKE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = dislikeTextColor
                    )
                }
            }

            // BOOKMARK BUTTON
            val bookmarkBgColor by animateColorAsState(
                targetValue = if (article.isBookmarked) NothingRed else NothingSurface,
                animationSpec = tween(durationMillis = 100),
                label = "BookmarkBg"
            )
            val bookmarkTextColor by animateColorAsState(
                targetValue = if (article.isBookmarked) NothingWhite else NothingTextMuted,
                animationSpec = tween(durationMillis = 100),
                label = "BookmarkText"
            )

            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(42.dp)
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bookmarkBgColor)
                    .border(1.dp, if (article.isBookmarked) NothingRed else NothingBorder, RoundedCornerShape(6.dp))
                    .testTag("bookmark_article_detail")
                    .clickable {
                        viewModel.toggleBookmark(article.id, article.isBookmarked)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = bookmarkTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (article.isBookmarked) "SAVED" else "SAVE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = bookmarkTextColor
                    )
                }
            }

            // OFFLINE BUTTON
            val offlineBgColor by animateColorAsState(
                targetValue = if (article.isSavedForOffline) NothingSurfaceVariant else NothingSurface,
                animationSpec = tween(durationMillis = 100),
                label = "OfflineBg"
            )
            val offlineTextColor by animateColorAsState(
                targetValue = if (article.isSavedForOffline) NothingWhite else NothingTextMuted,
                animationSpec = tween(durationMillis = 100),
                label = "OfflineText"
            )

            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(42.dp)
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(offlineBgColor)
                    .border(1.dp, if (article.isSavedForOffline) NothingWhite else NothingBorder, RoundedCornerShape(6.dp))
                    .testTag("offline_article_detail")
                    .clickable {
                        viewModel.toggleOfflineStatus(article.id, article.isSavedForOffline)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (article.isSavedForOffline) Icons.Filled.DownloadForOffline else Icons.Outlined.DownloadForOffline,
                        contentDescription = "Save Offline",
                        tint = if (article.isSavedForOffline) NothingRed else offlineTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (article.isSavedForOffline) "OFFLINE" else "CACHE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = offlineTextColor
                    )
                }
            }

            // SHARE BUTTON
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .padding(horizontal = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NothingSurface)
                    .border(1.dp, NothingBorder, RoundedCornerShape(6.dp))
                    .clickable {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, article.title)
                            putExtra(Intent.EXTRA_TEXT, "${article.title}\n\n${article.link}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Article"))
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = NothingWhite,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SHARE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = NothingWhite
                    )
                }
            }
        }
    }

    val dislikeAnalysis by viewModel.lastDislikeAnalysis.collectAsState()
    if (dislikeAnalysis != null) {
        DislikeAnalysisDialog(
            analysis = dislikeAnalysis!!,
            onDismiss = { viewModel.dismissDislikeAnalysis() },
            onApplyMuting = { keywords, author, subcat, feedUrl ->
                viewModel.applyMutingPreferences(keywords, author, subcat, feedUrl)
            }
        )
    }

    if (showPodcastDialog) {
        PodcastMediaDialog(
            article = article,
            onDismiss = { showPodcastDialog = false },
            onOpenInBrowser = { url -> InAppBrowser.openUrl(context, url) }
        )
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
