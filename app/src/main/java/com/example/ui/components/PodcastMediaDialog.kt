package com.example.ui.components

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.fillMaxSize
import com.example.data.local.ArticleEntity
import com.example.service.PodcastPlayerManager
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.local.AppDatabase
import com.example.util.PodcastMetadataGrabber
import com.example.ui.theme.NothingSurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PodcastMediaDialog(
    article: ArticleEntity,
    onDismiss: () -> Unit,
    onOpenInBrowser: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val rssDao = remember { db.rssDao() }

    var currentArticleState by remember { mutableStateOf(article) }
    var currentSeriesIconUrl by remember { mutableStateOf("") }
    var showThumbnailDialog by remember { mutableStateOf(false) }
    var showEditCategoryDialog by remember { mutableStateOf(false) }
    var showConfirmRemoveFeedDialog by remember { mutableStateOf(false) }

    val isVideo = currentArticleState.isVideoPodcast || currentArticleState.mediaType == "VIDEO" || currentArticleState.link.contains("youtube.com") || currentArticleState.link.contains("youtu.be")
    val mediaUrl = currentArticleState.mediaUrl ?: currentArticleState.link

    // Collect global background podcast state
    val activeArticle by PodcastPlayerManager.activeArticle.collectAsState()
    val isPlaying by PodcastPlayerManager.isPlaying.collectAsState()
    val isBuffering by PodcastPlayerManager.isBuffering.collectAsState()
    val currentPositionMs by PodcastPlayerManager.currentPositionMs.collectAsState()
    val durationMs by PodcastPlayerManager.durationMs.collectAsState()
    val playbackSpeed by PodcastPlayerManager.playbackSpeed.collectAsState()
    val managerErrorMessage by PodcastPlayerManager.errorMessage.collectAsState()

    val isCurrentArticlePlaying = activeArticle?.id == currentArticleState.id

    // Auto-start background playback if this article isn't currently loaded
    LaunchedEffect(currentArticleState.id) {
        if (!isVideo && !isCurrentArticlePlaying) {
            PodcastPlayerManager.playPodcast(context, currentArticleState)
        }
        coroutineScope.launch(Dispatchers.IO) {
            val dbArticle = rssDao.getArticleById(currentArticleState.id)
            if (dbArticle != null) currentArticleState = dbArticle
            val dbFeed = rssDao.getFeedByUrl(currentArticleState.feedUrl)
            if (dbFeed != null) currentSeriesIconUrl = dbFeed.iconUrl

            // Auto-grab artwork if missing both episode image and series icon
            if (currentArticleState.imageUrl.isNullOrBlank() && currentSeriesIconUrl.isBlank()) {
                val grabbed = PodcastMetadataGrabber.autoGrabBestThumbnail(
                    currentArticleState.feedTitle,
                    currentArticleState.title,
                    currentArticleState.feedUrl
                )
                if (!grabbed.isNullOrBlank()) {
                    rssDao.updateArticleImageUrl(currentArticleState.id, grabbed)
                    rssDao.updateFeedIconUrl(currentArticleState.feedUrl, grabbed)
                    currentArticleState = currentArticleState.copy(imageUrl = grabbed)
                    currentSeriesIconUrl = grabbed
                }
            }
        }
    }

    Dialog(onDismissRequest = { onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = NothingBlack,
            border = androidx.compose.foundation.BorderStroke(1.dp, NothingRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isVideo) Icons.Default.Videocam else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Podcast",
                            tint = NothingRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isVideo) "🎥 VIDEO PODCAST PLAYER" else "🎧 BACKGROUND PODCAST PLAYER",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = NothingWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onDismiss() },
                        modifier = Modifier
                            .testTag("close_podcast_dialog")
                            .size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NothingWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Title and Publisher Info
                Text(
                    text = article.title,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = NothingWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${currentArticleState.feedTitle.uppercase()} ${if (!currentArticleState.duration.isNullOrBlank()) "• ${currentArticleState.duration}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .testTag("edit_podcast_category_badge")
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingSurface)
                            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                            .clickable { showEditCategoryDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Category",
                                tint = NothingRed,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CAT: ${currentArticleState.category.ifBlank { "PODCASTS" }.uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = NothingWhite
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .testTag("toggle_podcast_played_status_badge")
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (currentArticleState.isRead) NothingRed.copy(alpha = 0.25f) else NothingSurface)
                            .border(1.dp, if (currentArticleState.isRead) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                            .clickable {
                                val newReadState = !currentArticleState.isRead
                                currentArticleState = currentArticleState.copy(isRead = newReadState)
                                coroutineScope.launch(Dispatchers.IO) {
                                    rssDao.setArticleReadState(currentArticleState.id, newReadState)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (currentArticleState.isRead) "✓ PLAYED" else "+ MARK PLAYED",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = if (currentArticleState.isRead) NothingWhite else NothingTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .testTag("remove_podcast_feed_badge")
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingSurface)
                            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                            .clickable { showConfirmRemoveFeedDialog = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Show",
                                tint = NothingRed,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REMOVE SHOW",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = NothingRed
                            )
                        }
                    }
                }

                val savedProgressMs = remember(currentArticleState.id) {
                    com.example.util.PodcastProgressManager.getProgress(context, currentArticleState.id)
                }
                val savedDurationMs = remember(currentArticleState.id) {
                    com.example.util.PodcastProgressManager.getDuration(context, currentArticleState.id)
                }
                val parsedDurMs = remember(currentArticleState.duration) {
                    parseDurationStringToMs(currentArticleState.duration)
                }
                val totalDurMs = if (savedDurationMs > 0L) savedDurationMs else parsedDurMs

                if (savedProgressMs > 3000L && !currentArticleState.isRead) {
                    val remainingMs = totalDurMs - savedProgressMs
                    val resumeLabel = if (totalDurMs > savedProgressMs) {
                        "▶ RESUMING FROM ${com.example.util.PodcastProgressManager.formatMs(savedProgressMs)} (${com.example.util.PodcastProgressManager.formatMs(remainingMs)} LEFT)"
                    } else {
                        "▶ RESUMING FROM ${com.example.util.PodcastProgressManager.formatMs(savedProgressMs)}"
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .testTag("saved_progress_badge")
                            .clip(RoundedCornerShape(4.dp))
                            .background(NothingRed.copy(alpha = 0.2f))
                            .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = resumeLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NothingWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // VIDEO VS AUDIO PLAYER DISPLAY
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NothingDarkGray)
                            .border(1.dp, NothingBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaUrl.contains("youtube.com") || mediaUrl.contains("youtu.be")) {
                            // YouTube Link
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "YOUTUBE VIDEO PODCAST",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NothingWhite
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Watch this video podcast episode directly on YouTube.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NothingTextMuted
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { onOpenInBrowser(mediaUrl) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NothingRed,
                                        contentColor = NothingWhite
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = "Open YouTube",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("OPEN IN YOUTUBE / BROWSER", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            // Direct Video Stream (MP4 / M4V) using Android VideoView
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoURI(Uri.parse(mediaUrl))
                                        val mediaController = MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                        setOnPreparedListener { mp ->
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        }
                    }
                } else {
                    // AUDIO BACKGROUND PLAYER WITH NOTHING WAVEFORM
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NothingDarkGray)
                            .border(1.dp, NothingBorder, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
                        val effectiveCoverUrl = currentArticleState.imageUrl?.ifBlank { null } ?: currentSeriesIconUrl.ifBlank { null }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(NothingBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!effectiveCoverUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = effectiveCoverUrl,
                                    contentDescription = "Podcast Cover Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "No Artwork",
                                    tint = NothingWhite.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }

                            // Edit Cover Badge
                            Surface(
                                color = NothingBlack.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, NothingRed.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clickable { showThumbnailDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Thumbnail",
                                        tint = NothingRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "EDIT ARTWORK",
                                        color = NothingWhite,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        if (isCurrentArticlePlaying && managerErrorMessage != null) {
                            Surface(
                                color = NothingRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, NothingRed.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = managerErrorMessage!!,
                                        color = NothingWhite,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(
                                            onClick = {
                                                PodcastPlayerManager.playPodcast(context, article)
                                            }
                                        ) {
                                            Text("RETRY", color = NothingRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        TextButton(
                                            onClick = {
                                                onOpenInBrowser(article.link)
                                            }
                                        ) {
                                            Text("OPEN LINK", color = NothingWhite, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Waveform Visualizer Animation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val transition = rememberInfiniteTransition(label = "waveform")
                            val animState by transition.animateFloat(
                                initialValue = 0.2f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "wave"
                            )

                            repeat(18) { index ->
                                val factor = if (isPlaying && isCurrentArticlePlaying) (index % 5 + 1) * 0.18f * animState else 0.1f
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height((36.dp * factor).coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isPlaying && isCurrentArticlePlaying) NothingRed else NothingTextMuted)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Seek Slider
                        var isUserDraggingSlider by remember { androidx.compose.runtime.mutableStateOf(false) }
                        var dragPosMs by remember { mutableFloatStateOf(0f) }

                        val sliderDisplayPos = if (isUserDraggingSlider) dragPosMs else currentPositionMs.toFloat()

                        Slider(
                            value = sliderDisplayPos.coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
                            onValueChange = { newPos ->
                                isUserDraggingSlider = true
                                dragPosMs = newPos
                            },
                            onValueChangeFinished = {
                                isUserDraggingSlider = false
                                PodcastPlayerManager.seekTo(context, dragPosMs.toLong())
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = NothingRed,
                                activeTrackColor = NothingRed,
                                inactiveTrackColor = NothingBorder
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("podcast_dialog_seek_slider")
                        )

                        // Time stamps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTimeMs(currentPositionMs),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NothingTextMuted
                            )
                            Text(
                                text = formatTimeMs(durationMs),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NothingTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Player Controls (Rewind -10s / Play/Pause / Fast Forward +10s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { PodcastPlayerManager.rewind(context, 10000L) },
                                modifier = Modifier.testTag("dialog_rewind_10s")
                            ) {
                                Icon(imageVector = Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = NothingWhite)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(NothingRed)
                                    .clickable {
                                        if (!isCurrentArticlePlaying) {
                                            PodcastPlayerManager.playPodcast(context, article)
                                        } else {
                                            PodcastPlayerManager.togglePlayPause(context)
                                        }
                                    }
                                    .testTag("dialog_toggle_play_pause"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isBuffering && isCurrentArticlePlaying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = NothingWhite,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (isPlaying && isCurrentArticlePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = NothingWhite,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            IconButton(
                                onClick = { PodcastPlayerManager.fastForward(context, 10000L) },
                                modifier = Modifier.testTag("dialog_fast_forward_10s")
                            ) {
                                Icon(imageVector = Icons.Default.Forward10, contentDescription = "Forward 10s", tint = NothingWhite)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Speed Controls (1.0x, 1.25x, 1.5x, 2.0x)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                            speeds.forEach { speedVal ->
                                val isSelected = (playbackSpeed == speedVal)
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) NothingRed else NothingSurface)
                                        .border(1.dp, if (isSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                                        .clickable { PodcastPlayerManager.setPlaybackSpeed(context, speedVal) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${speedVal}x",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) NothingWhite else NothingTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                if (managerErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = managerErrorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingRed
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isDownloading by remember { mutableStateOf(false) }
                    var isDownloaded by remember(currentArticleState.id) {
                        mutableStateOf(com.example.util.PodcastCacheManager.isArticleCached(context, currentArticleState.id) || currentArticleState.isSavedForOffline)
                    }

                    Button(
                        onClick = {
                            if (!isDownloading) {
                                if (isDownloaded) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        com.example.util.PodcastCacheManager.getCachedFile(context, currentArticleState.id)?.delete()
                                        rssDao.updateOfflineStatus(currentArticleState.id, false)
                                        withContext(Dispatchers.Main) {
                                            isDownloaded = false
                                            android.widget.Toast.makeText(context, "Removed offline podcast download", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    isDownloading = true
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val file = com.example.util.PodcastCacheManager.downloadPodcastAudio(context, currentArticleState)
                                        if (file != null) {
                                            rssDao.updateOfflineStatus(currentArticleState.id, true)
                                            withContext(Dispatchers.Main) {
                                                isDownloading = false
                                                isDownloaded = true
                                                android.widget.Toast.makeText(context, "Podcast downloaded & verified for offline playback!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isDownloading = false
                                                android.widget.Toast.makeText(context, "Download failed: Stream file invalid or protected", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDownloaded) NothingSurfaceVariant else NothingRed,
                            contentColor = NothingWhite
                        ),
                        shape = RoundedCornerShape(4.dp),
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = NothingWhite, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DOWNLOADING...", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        } else {
                            Icon(imageVector = if (isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download, contentDescription = "Download", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isDownloaded) "SAVED OFFLINE ✓" else "DOWNLOAD OFFLINE", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }

                    Button(
                        onClick = { onOpenInBrowser(article.link) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingDarkGray,
                            contentColor = NothingWhite
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = "Open Link", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WEB LINK", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }
    }

    if (showThumbnailDialog) {
        PodcastThumbnailEditDialog(
            article = currentArticleState,
            currentSeriesIconUrl = currentSeriesIconUrl,
            onDismiss = { showThumbnailDialog = false },
            onSaveEpisodeThumbnail = { newUrl ->
                coroutineScope.launch(Dispatchers.IO) {
                    rssDao.updateArticleImageUrl(currentArticleState.id, newUrl)
                    currentArticleState = currentArticleState.copy(imageUrl = newUrl)
                }
            },
            onSaveSeriesThumbnail = { newUrl ->
                coroutineScope.launch(Dispatchers.IO) {
                    rssDao.updateFeedIconUrl(currentArticleState.feedUrl, newUrl)
                    currentSeriesIconUrl = newUrl
                }
            }
        )
    }

    if (showEditCategoryDialog) {
        PodcastSeriesCategoryEditDialog(
            feedTitle = currentArticleState.feedTitle,
            currentCategory = currentArticleState.category,
            onDismiss = { showEditCategoryDialog = false },
            onSaveCategory = { newCategory ->
                coroutineScope.launch(Dispatchers.IO) {
                    rssDao.updateFeedCategory(currentArticleState.feedUrl, newCategory)
                    rssDao.updateArticlesCategoryByFeedUrl(currentArticleState.feedUrl, newCategory, true)
                    currentArticleState = currentArticleState.copy(category = newCategory, isPodcast = true)
                    withContext(Dispatchers.Main) {
                        showEditCategoryDialog = false
                        android.widget.Toast.makeText(
                            context,
                            "Reclassified '${currentArticleState.feedTitle}' as $newCategory",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    if (showConfirmRemoveFeedDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRemoveFeedDialog = false },
            title = {
                Text(
                    text = "REMOVE PODCAST SHOW?",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = NothingWhite
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove '${currentArticleState.feedTitle}' from your subscribed podcasts? All saved episodes will be deleted.",
                    fontSize = 13.sp,
                    color = NothingTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmRemoveFeedDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            rssDao.deleteFeedByUrl(currentArticleState.feedUrl)
                        }
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "REMOVE SHOW",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmRemoveFeedDialog = false }
                ) {
                    Text(
                        text = "CANCEL",
                        fontFamily = FontFamily.Monospace,
                        color = NothingTextMuted
                    )
                }
            },
            containerColor = NothingDarkGray,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastSeriesCategoryEditDialog(
    feedTitle: String,
    currentCategory: String,
    onDismiss: () -> Unit,
    onSaveCategory: (newCategory: String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(currentCategory.ifBlank { "PODCASTS" }) }
    var customCategoryInput by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    val presetCategories = listOf(
        "PODCASTS", "TECH & AI", "NEWS & POLITICS", "TRUE CRIME", "SCIENCE",
        "BUSINESS & FINANCE", "COMEDY", "GAMING & GEEK", "CULTURE & HISTORY",
        "HEALTH & FITNESS", "AUDIOBOOKS"
    )

    AlertDialog(
        onDismissRequest = { onDismiss() },
        containerColor = NothingBlack,
        shape = RoundedCornerShape(8.dp),
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Series Category",
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EDIT PODCAST CATEGORY",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NothingWhite
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Change category for '$feedTitle' to reclassify series",
                    style = MaterialTheme.typography.bodySmall,
                    color = NothingTextMuted,
                    fontSize = 10.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SELECT CATEGORY PRESET:",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = NothingTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetCategories.forEach { cat ->
                        val isSelected = !isCustomSelected && selectedCategory.equals(cat, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .testTag("category_option_$cat")
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) NothingRed else NothingSurface)
                                .border(1.dp, if (isSelected) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                                .clickable {
                                    isCustomSelected = false
                                    selectedCategory = cat
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = cat,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                color = if (isSelected) NothingWhite else NothingTextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "OR ENTER CUSTOM CATEGORY:",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = NothingTextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = customCategoryInput,
                    onValueChange = {
                        customCategoryInput = it
                        if (it.isNotBlank()) {
                            isCustomSelected = true
                        }
                    },
                    placeholder = {
                        Text(
                            text = "e.g. DESIGN, INVESTING...",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NothingTextMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_category_input"),
                    shape = RoundedCornerShape(4.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NothingSurface,
                        unfocusedContainerColor = NothingSurface,
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
                    val finalCategory = if (isCustomSelected && customCategoryInput.isNotBlank()) {
                        customCategoryInput.trim().uppercase()
                    } else {
                        selectedCategory
                    }
                    onSaveCategory(finalCategory)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingRed,
                    contentColor = NothingWhite
                ),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("save_podcast_category_button")
            ) {
                Text(
                    text = "SAVE CATEGORY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() },
                modifier = Modifier.testTag("cancel_podcast_category_button")
            ) {
                Text(
                    text = "CANCEL",
                    fontFamily = FontFamily.Monospace,
                    color = NothingTextMuted,
                    fontSize = 10.sp
                )
            }
        }
    )
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
