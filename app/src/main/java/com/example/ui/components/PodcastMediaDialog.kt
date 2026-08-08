package com.example.ui.components

import android.media.AudioAttributes
import android.media.MediaPlayer
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
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.data.local.ArticleEntity
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import kotlinx.coroutines.delay

@Composable
fun PodcastMediaDialog(
    article: ArticleEntity,
    onDismiss: () -> Unit,
    onOpenInBrowser: (String) -> Unit
) {
    val context = LocalContext.current
    val isVideo = article.isVideoPodcast || article.mediaType == "VIDEO" || article.link.contains("youtube.com") || article.link.contains("youtu.be")
    val mediaUrl = article.mediaUrl ?: article.link

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableFloatStateOf(0f) }
    var durationMs by remember { mutableFloatStateOf(1f) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                            text = if (isVideo) "🎥 VIDEO PODCAST PLAYER" else "🎧 AUDIO PODCAST PLAYER",
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
                    text = "${article.feedTitle.uppercase()} ${if (!article.duration.isNullOrBlank()) "• ${article.duration}" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

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
                            // YouTube Link - Prominent YouTube Streaming Button
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
                                            isBuffering = false
                                            durationMs = mp.duration.toFloat().coerceAtLeast(1f)
                                            start()
                                            isPlaying = true
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            errorMessage = "Direct video stream unavailable. Open external link."
                                            true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        }
                    }
                } else {
                    // AUDIO PLAYER WITH ANIMATED NOTHING WAVEFORM
                    val mediaPlayer = remember { MediaPlayer() }

                    DisposableEffect(mediaUrl) {
                        try {
                            mediaPlayer.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            mediaPlayer.setDataSource(mediaUrl)
                            mediaPlayer.prepareAsync()
                            mediaPlayer.setOnPreparedListener { mp ->
                                isBuffering = false
                                durationMs = mp.duration.toFloat().coerceAtLeast(1f)
                                mp.start()
                                isPlaying = true
                            }
                            mediaPlayer.setOnErrorListener { _, _, _ ->
                                errorMessage = "Audio stream unavailable. Open in browser."
                                true
                            }
                        } catch (e: Exception) {
                            errorMessage = "Audio playback error: ${e.message}"
                        }

                        onDispose {
                            try {
                                if (mediaPlayer.isPlaying) mediaPlayer.stop()
                                mediaPlayer.release()
                            } catch (e: Exception) { }
                        }
                    }

                    // Progress update ticker
                    LaunchedEffect(isPlaying) {
                        while (isPlaying) {
                            try {
                                currentPositionMs = mediaPlayer.currentPosition.toFloat()
                            } catch (e: Exception) { }
                            delay(500)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NothingDarkGray)
                            .border(1.dp, NothingBorder, RoundedCornerShape(6.dp))
                            .padding(14.dp)
                    ) {
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
                                val factor = if (isPlaying) (index % 5 + 1) * 0.18f * animState else 0.1f
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height((36.dp * factor).coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isPlaying) NothingRed else NothingTextMuted)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Seek Slider
                        Slider(
                            value = currentPositionMs,
                            onValueChange = { newPos ->
                                currentPositionMs = newPos
                                try {
                                    mediaPlayer.seekTo(newPos.toInt())
                                } catch (e: Exception) { }
                            },
                            valueRange = 0f..durationMs,
                            colors = SliderDefaults.colors(
                                thumbColor = NothingRed,
                                activeTrackColor = NothingRed,
                                inactiveTrackColor = NothingBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Time stamps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTimeMs(currentPositionMs.toLong()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NothingTextMuted
                            )
                            Text(
                                text = formatTimeMs(durationMs.toLong()),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = NothingTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Player Controls (Play / Pause / Rewind / Fast Forward)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val newPos = (currentPositionMs - 10000f).coerceAtLeast(0f)
                                    currentPositionMs = newPos
                                    try { mediaPlayer.seekTo(newPos.toInt()) } catch (e: Exception) { }
                                }
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
                                        try {
                                            if (mediaPlayer.isPlaying) {
                                                mediaPlayer.pause()
                                                isPlaying = false
                                            } else {
                                                mediaPlayer.start()
                                                isPlaying = true
                                            }
                                        } catch (e: Exception) { }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = NothingWhite,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            IconButton(
                                onClick = {
                                    val newPos = (currentPositionMs + 10000f).coerceAtMost(durationMs)
                                    currentPositionMs = newPos
                                    try { mediaPlayer.seekTo(newPos.toInt()) } catch (e: Exception) { }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Forward10, contentDescription = "Forward 10s", tint = NothingWhite)
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = NothingRed
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
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
                        Text("OPEN ORIGINAL LINK", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
