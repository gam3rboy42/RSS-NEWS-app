package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.ArticleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PodcastPlayerManager {

    val activeArticle = MutableStateFlow<ArticleEntity?>(null)
    val isPlaying = MutableStateFlow(false)
    val isBuffering = MutableStateFlow(false)
    val currentPositionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(1L)
    val playbackSpeed = MutableStateFlow(1.0f)
    val errorMessage = MutableStateFlow<String?>(null)

    fun playPodcast(context: Context, article: ArticleEntity) {
        activeArticle.value = article
        errorMessage.value = null
        isBuffering.value = true
        isPlaying.value = false

        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_PLAY_PODCAST
            putExtra(PodcastPlaybackService.EXTRA_ARTICLE_ID, article.id)
            putExtra(PodcastPlaybackService.EXTRA_TITLE, article.title)
            putExtra(PodcastPlaybackService.EXTRA_FEED_TITLE, article.feedTitle)
            putExtra(PodcastPlaybackService.EXTRA_MEDIA_URL, article.mediaUrl ?: article.link)
            putExtra(PodcastPlaybackService.EXTRA_IMAGE_URL, article.imageUrl)
            putExtra(PodcastPlaybackService.EXTRA_FEED_URL, article.feedUrl)
            putExtra(PodcastPlaybackService.EXTRA_IS_VIDEO, article.isVideoPodcast || article.mediaType == "VIDEO")
        }
        startServiceHelper(context, intent)
    }

    fun togglePlayPause(context: Context) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_TOGGLE_PLAY_PAUSE
        }
        startServiceHelper(context, intent)
    }

    fun play(context: Context) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_PLAY
        }
        startServiceHelper(context, intent)
    }

    fun pause(context: Context) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_PAUSE
        }
        startServiceHelper(context, intent)
    }

    fun seekTo(context: Context, positionMs: Long) {
        currentPositionMs.value = positionMs
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_SEEK_TO
            putExtra(PodcastPlaybackService.EXTRA_POSITION_MS, positionMs)
        }
        startServiceHelper(context, intent)
    }

    fun rewind(context: Context, deltaMs: Long = 10000L) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_REWIND
            putExtra(PodcastPlaybackService.EXTRA_DELTA_MS, deltaMs)
        }
        startServiceHelper(context, intent)
    }

    fun fastForward(context: Context, deltaMs: Long = 10000L) {
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_FAST_FORWARD
            putExtra(PodcastPlaybackService.EXTRA_DELTA_MS, deltaMs)
        }
        startServiceHelper(context, intent)
    }

    fun setPlaybackSpeed(context: Context, speed: Float) {
        playbackSpeed.value = speed
        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_SET_SPEED
            putExtra(PodcastPlaybackService.EXTRA_SPEED, speed)
        }
        startServiceHelper(context, intent)
    }

    fun stop(context: Context) {
        activeArticle.value = null
        isPlaying.value = false
        isBuffering.value = false
        currentPositionMs.value = 0L
        durationMs.value = 1L

        val intent = Intent(context, PodcastPlaybackService::class.java).apply {
            action = PodcastPlaybackService.ACTION_STOP
        }
        startServiceHelper(context, intent)
    }

    private fun startServiceHelper(context: Context, intent: Intent) {
        try {
            if (intent.action == PodcastPlaybackService.ACTION_PLAY_PODCAST) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
