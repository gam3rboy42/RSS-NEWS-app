package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.StoryCluster
import com.example.data.local.ArticleEntity
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingWhite
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SwipeableArticleCard(
    cluster: StoryCluster,
    onArticleClick: (ArticleEntity) -> Unit,
    onChooseSourceClick: (StoryCluster) -> Unit,
    onBookmarkToggle: (String, Boolean) -> Unit,
    onOfflineToggle: (String, Boolean) -> Unit,
    onLikeToggle: (String, Boolean) -> Unit,
    onDislikeToggle: (String, Boolean) -> Unit,
    onSubscribeFeed: (String, String, String) -> Unit,
    onArchive: (StoryCluster) -> Unit,
    onDislikeSwipe: ((StoryCluster) -> Unit)? = null,
    onDeleteFeed: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val swipeThresholdPx = with(density) { 130.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val currentOffset = offsetX.value
    val isPastThreshold = abs(currentOffset) >= swipeThresholdPx
    val isSwipingLeft = currentOffset < 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("swipeable_article_${cluster.clusterId}")
    ) {
        // BACKGROUND REVEAL CONTAINER (Nothing OS Aesthetic)
        if (abs(currentOffset) > 8f) {
            val bgContainerColor = if (isPastThreshold) NothingRed else NothingDarkGray
            val bgBorderColor = if (isPastThreshold) NothingRed else NothingBorder

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgContainerColor)
                    .border(1.dp, bgBorderColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (isSwipingLeft) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isSwipingLeft) {
                        Icon(
                            imageVector = if (isPastThreshold) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike & Remove",
                            tint = NothingWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPastThreshold) "RELEASE TO DISLIKE & REMOVE" else "SWIPE LEFT TO DISLIKE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NothingWhite,
                            letterSpacing = 0.5.sp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPastThreshold) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = "Save for Later",
                            tint = NothingWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPastThreshold) "RELEASE TO SAVE FOR LATER" else "SWIPE RIGHT TO SAVE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NothingWhite,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // FOREGROUND CONTENT CARD
        Box(
            modifier = Modifier
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
                .pointerInput(cluster.clusterId) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasTriggeredHaptic = false
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (abs(offsetX.value) >= swipeThresholdPx) {
                                    val isLeft = offsetX.value < 0
                                    if (isLeft) {
                                        val targetX = -1500f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        offsetX.animateTo(
                                            targetValue = targetX,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                        if (onDislikeSwipe != null) {
                                            onDislikeSwipe(cluster)
                                        } else {
                                            onDislikeToggle(cluster.primaryArticle.id, cluster.primaryArticle.isDisliked)
                                        }
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onBookmarkToggle(cluster.primaryArticle.id, cluster.primaryArticle.isBookmarked)
                                        offsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                } else {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newX = offsetX.value + dragAmount
                            coroutineScope.launch {
                                offsetX.snapTo(newX)
                            }

                            if (abs(newX) >= swipeThresholdPx && !hasTriggeredHaptic) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hasTriggeredHaptic = true
                            } else if (abs(newX) < swipeThresholdPx && hasTriggeredHaptic) {
                                hasTriggeredHaptic = false
                            }
                        }
                    )
                }
        ) {
            ArticleCard(
                cluster = cluster,
                onArticleClick = onArticleClick,
                onChooseSourceClick = onChooseSourceClick,
                onBookmarkToggle = onBookmarkToggle,
                onOfflineToggle = onOfflineToggle,
                onLikeToggle = onLikeToggle,
                onDislikeToggle = onDislikeToggle,
                onSubscribeFeed = onSubscribeFeed,
                onDeleteFeed = onDeleteFeed
            )
        }
    }
}
