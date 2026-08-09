package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.service.PodcastPlayerManager
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import java.util.Locale

@Composable
fun PodcastMiniPlayer(
    onExpandPlayer: () -> Unit
) {
    val context = LocalContext.current
    val activeArticle by PodcastPlayerManager.activeArticle.collectAsState()
    val isPlaying by PodcastPlayerManager.isPlaying.collectAsState()
    val isBuffering by PodcastPlayerManager.isBuffering.collectAsState()
    val currentPositionMs by PodcastPlayerManager.currentPositionMs.collectAsState()
    val durationMs by PodcastPlayerManager.durationMs.collectAsState()

    val article = activeArticle ?: return

    val progressRatio = (currentPositionMs.toFloat() / durationMs.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(NothingSurface)
            .border(1.dp, NothingRed, RoundedCornerShape(8.dp))
            .clickable { onExpandPlayer() }
            .testTag("podcast_mini_player_bar")
    ) {
        Column {
            // Progress Line on top
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressRatio)
                    .height(2.dp)
                    .background(NothingRed)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Podcast Icon & Title Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NothingRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!article.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = article.imageUrl,
                                contentDescription = "Podcast Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Podcast",
                                tint = NothingRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = article.title,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NothingWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = article.feedTitle.uppercase(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = NothingRed,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = NothingTextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Playback Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = { PodcastPlayerManager.rewind(context, 10000L) },
                        modifier = Modifier.size(28.dp).testTag("mini_player_rewind")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = NothingWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play/Pause
                    IconButton(
                        onClick = { PodcastPlayerManager.togglePlayPause(context) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NothingRed)
                            .testTag("mini_player_toggle_play_pause")
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = NothingWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = NothingWhite,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { PodcastPlayerManager.fastForward(context, 10000L) },
                        modifier = Modifier.size(28.dp).testTag("mini_player_fast_forward")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = NothingWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Stop / Close
                    IconButton(
                        onClick = { PodcastPlayerManager.stop(context) },
                        modifier = Modifier.size(28.dp).testTag("mini_player_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Player",
                            tint = NothingTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
