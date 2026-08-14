package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class PodcastPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "podcast_playback_channel"
        const val CHANNEL_NAME = "Podcast Playback"

        const val ACTION_PLAY_PODCAST = "com.example.action.PLAY_PODCAST"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_PLAY = "com.example.action.PLAY"
        const val ACTION_PAUSE = "com.example.action.PAUSE"
        const val ACTION_REWIND = "com.example.action.REWIND"
        const val ACTION_FAST_FORWARD = "com.example.action.FAST_FORWARD"
        const val ACTION_SEEK_TO = "com.example.action.SEEK_TO"
        const val ACTION_SET_SPEED = "com.example.action.SET_SPEED"
        const val ACTION_STOP = "com.example.action.STOP"

        const val EXTRA_ARTICLE_ID = "extra_article_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FEED_TITLE = "extra_feed_title"
        const val EXTRA_MEDIA_URL = "extra_media_url"
        const val EXTRA_IMAGE_URL = "extra_image_url"
        const val EXTRA_FEED_URL = "extra_feed_url"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_DELTA_MS = "extra_delta_ms"
        const val EXTRA_SPEED = "extra_speed"

        private const val TAG = "PodcastPlaybackService"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentArticleId: String? = null
    private var currentMediaUrl: String? = null
    private var currentTitle: String = "Podcast Episode"
    private var currentFeedTitle: String = "Nothing RSS"
    private var currentImageUrl: String? = null
    private var currentFeedUrl: String? = null
    private var coverBitmap: Bitmap? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var updateProgressJob: Job? = null

    private var isNoisyReceiverRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                Log.d(TAG, "Audio becoming noisy (headset unplugged / Bluetooth disconnected) -> pausing playback")
                pausePlayback()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                registerReceiver(becomingNoisyReceiver, filter)
                isNoisyReceiverRegistered = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register becoming noisy receiver", e)
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
                isNoisyReceiverRegistered = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister becoming noisy receiver", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
            }
            if (keyEvent != null) {
                mediaSession?.controller?.dispatchMediaButtonEvent(keyEvent)
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_PLAY_PODCAST -> {
                currentArticleId = intent.getStringExtra(EXTRA_ARTICLE_ID)
                val mediaUrl = intent.getStringExtra(EXTRA_MEDIA_URL)
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Podcast Episode"
                currentFeedTitle = intent.getStringExtra(EXTRA_FEED_TITLE) ?: "Nothing RSS"
                currentImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
                currentFeedUrl = intent.getStringExtra(EXTRA_FEED_URL)
                coverBitmap = null

                startForegroundNotification()

                if (!mediaUrl.isNullOrBlank()) {
                    playNewPodcast(mediaUrl)
                }
                fetchCoverBitmap(currentImageUrl, currentFeedUrl, currentFeedTitle, currentTitle)
            }
            ACTION_TOGGLE_PLAY_PAUSE -> {
                if (mediaPlayer?.isPlaying == true) {
                    pausePlayback()
                } else {
                    resumePlayback()
                }
            }
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_REWIND -> {
                val delta = intent.getLongExtra(EXTRA_DELTA_MS, 10000L)
                rewindPlayback(delta)
            }
            ACTION_FAST_FORWARD -> {
                val delta = intent.getLongExtra(EXTRA_DELTA_MS, 10000L)
                fastForwardPlayback(delta)
            }
            ACTION_SEEK_TO -> {
                val pos = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                seekToPosition(pos)
            }
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                setPlaybackSpeedInternal(speed)
            }
            ACTION_STOP -> {
                stopPlaybackAndCleanUp()
            }
        }

        return START_STICKY
    }

    private fun initMediaSession() {
        val mediaButtonReceiverIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this, PodcastPlaybackService::class.java)
        val mediaButtonPendingIntent = PendingIntent.getService(
            this,
            0,
            mediaButtonReceiverIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession(this, "NothingPodcastMediaSession").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setMediaButtonReceiver(mediaButtonPendingIntent)
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
                    }

                    if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                        val keyCode = event.keyCode
                        Log.d(TAG, "MediaSession.Callback.onMediaButtonEvent received keyCode=$keyCode")
                        when (keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                resumePlayback()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                pausePlayback()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK -> {
                                if (mediaPlayer?.isPlaying == true) {
                                    pausePlayback()
                                } else {
                                    resumePlayback()
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT,
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                fastForwardPlayback(10000L)
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                rewindPlayback(10000L)
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                stopPlaybackAndCleanUp()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }

                override fun onPlay() {
                    resumePlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onSkipToPrevious() {
                    rewindPlayback(10000L)
                }

                override fun onSkipToNext() {
                    fastForwardPlayback(10000L)
                }

                override fun onRewind() {
                    rewindPlayback(10000L)
                }

                override fun onFastForward() {
                    fastForwardPlayback(10000L)
                }

                override fun onSeekTo(pos: Long) {
                    seekToPosition(pos)
                }

                override fun onStop() {
                    stopPlaybackAndCleanUp()
                }
            })
            isActive = true
        }
    }

    private var prepareJob: Job? = null

    private suspend fun resolveDirectStreamUrl(initialUrl: String): String = withContext(Dispatchers.IO) {
        val browserUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
        var finalUrl = initialUrl

        try {
            val request = okhttp3.Request.Builder()
                .url(initialUrl)
                .header("User-Agent", browserUserAgent)
                .header("Accept", "audio/*, */*;q=0.9")
                .header("Range", "bytes=0-2048")
                .header("Connection", "keep-alive")
                .build()

            val response = com.example.util.PodcastCacheManager.downloadClient.newCall(request).execute()
            finalUrl = response.request.url.toString()
            response.close()
            Log.d(TAG, "Resolved direct podcast stream URL: $initialUrl -> $finalUrl")
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving direct stream URL for $initialUrl: ${e.message}")
        }
        finalUrl
    }

    private fun playNewPodcast(url: String) {
        currentMediaUrl = url
        requestAudioFocus()

        PodcastPlayerManager.isBuffering.value = true
        PodcastPlayerManager.isPlaying.value = false
        PodcastPlayerManager.errorMessage.value = null

        prepareJob?.cancel()
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing old mediaPlayer", e)
        }
        mediaPlayer = null

        prepareJob = serviceScope.launch {
            val cachedFile = currentArticleId?.let { com.example.util.PodcastCacheManager.getCachedFile(applicationContext, it) }
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
            }
            mediaPlayer = mp

            val timeoutJob = launch {
                delay(60000L) // 60 seconds buffer timeout for high-bitrate/secure CDN streams
                if (PodcastPlayerManager.isBuffering.value) {
                    Log.e(TAG, "Media preparation timed out")
                    PodcastPlayerManager.isBuffering.value = false
                    PodcastPlayerManager.isPlaying.value = false
                    PodcastPlayerManager.errorMessage.value = "Audio stream buffering timed out. Tap play to retry, download offline, or open web link."
                    try {
                        mp.reset()
                        mp.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer == mp) mediaPlayer = null
                }
            }

            try {
                if (cachedFile != null) {
                    Log.d(TAG, "Playing podcast from local offline cache: ${cachedFile.absolutePath}")
                    mp.setDataSource(cachedFile.absolutePath)
                } else {
                    val resolvedUrl = resolveDirectStreamUrl(url)
                    val headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
                        "Accept" to "*/*",
                        "Connection" to "keep-alive"
                    )
                    mp.setDataSource(applicationContext, Uri.parse(resolvedUrl), headers)
                }
                mp.setOnPreparedListener { preparedMp ->
                    timeoutJob.cancel()
                    PodcastPlayerManager.isBuffering.value = false
                    PodcastPlayerManager.durationMs.value = preparedMp.duration.toLong().coerceAtLeast(1L)

                    // Auto-restore saved progress if available
                    currentArticleId?.let { id ->
                        val savedPos = com.example.util.PodcastProgressManager.getProgress(applicationContext, id)
                        if (savedPos > 3000L && savedPos < preparedMp.duration - 5000L) {
                            preparedMp.seekTo(savedPos.toInt())
                            PodcastPlayerManager.currentPositionMs.value = savedPos
                            Log.d(TAG, "Auto-restored podcast progress for $id to ${savedPos / 1000}s")
                        }
                    }

                    setPlaybackSpeedInternal(PodcastPlayerManager.playbackSpeed.value)

                    preparedMp.start()
                    PodcastPlayerManager.isPlaying.value = true
                    registerNoisyReceiver()

                    updateMediaSessionMetadata()
                    updateMediaSessionState(PlaybackState.STATE_PLAYING)
                    startForegroundNotification()
                    startProgressTracker()
                }
                mp.setOnCompletionListener {
                    PodcastPlayerManager.isPlaying.value = false
                    PodcastPlayerManager.currentPositionMs.value = PodcastPlayerManager.durationMs.value
                    updateMediaSessionState(PlaybackState.STATE_PAUSED)
                    updateNotification()
                    stopProgressTracker()

                    currentArticleId?.let { id ->
                        com.example.util.PodcastProgressManager.clearProgress(applicationContext, id)
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                val db = com.example.data.local.AppDatabase.getDatabase(applicationContext)
                                db.rssDao().markArticleAsRead(id)
                                com.example.util.PodcastCacheManager.autoPruneIfNecessary(applicationContext, db.rssDao())
                            } catch (e: Exception) {
                                Log.e(TAG, "Error marking read on completion", e)
                            }
                        }
                    }
                }
                mp.setOnErrorListener { _, what, extra ->
                    timeoutJob.cancel()
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    PodcastPlayerManager.isBuffering.value = false
                    PodcastPlayerManager.isPlaying.value = false

                    val detailedMsg = when {
                        what == -38 || extra == -38 -> "Audio stream blocked or web link (error -38). Tap OPEN LINK to listen on site or RETRY."
                        what == -1004 || extra == -1004 -> "Network connection error (-1004). Check network and RETRY."
                        what == -1007 || extra == -1007 -> "Malformed audio stream format (-1007). Tap OPEN LINK."
                        what == -1010 || extra == -1010 -> "Unsupported audio format (-1010). Tap OPEN LINK."
                        what == -110 || extra == -110 -> "Audio stream connection timed out (-110)."
                        else -> "Failed to stream audio (error $what, extra $extra). Tap play to retry."
                    }
                    PodcastPlayerManager.errorMessage.value = detailedMsg

                    try {
                        mp.reset()
                    } catch (_: Exception) {}

                    updateMediaSessionState(PlaybackState.STATE_ERROR)
                    updateNotification()
                    stopProgressTracker()
                    true
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                timeoutJob.cancel()
                Log.e(TAG, "Error preparing media player", e)
                PodcastPlayerManager.isBuffering.value = false
                PodcastPlayerManager.errorMessage.value = "Unable to play source audio: ${e.message}"
            }
        }

        // Fetch artwork in background
        fetchCoverBitmap(currentImageUrl)
    }

    private fun resumePlayback() {
        val mp = mediaPlayer
        if (mp != null) {
            requestAudioFocus()
            try {
                mp.start()
                PodcastPlayerManager.isPlaying.value = true
                PodcastPlayerManager.errorMessage.value = null
                registerNoisyReceiver()
                updateMediaSessionState(PlaybackState.STATE_PLAYING)
                startForegroundNotification()
                startProgressTracker()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume playback, reinitializing", e)
                currentMediaUrl?.let { playNewPodcast(it) }
            }
        } else {
            currentMediaUrl?.let { playNewPodcast(it) }
        }
    }

    private fun pausePlayback() {
        if (mediaPlayer?.isPlaying == true) {
            try {
                mediaPlayer?.let { mp ->
                    val pos = mp.currentPosition.toLong()
                    val dur = mp.duration.toLong()
                    currentArticleId?.let { id ->
                        com.example.util.PodcastProgressManager.saveProgress(applicationContext, id, pos, dur)
                    }
                    mp.pause()
                }
                PodcastPlayerManager.isPlaying.value = false
                unregisterNoisyReceiver()
                updateMediaSessionState(PlaybackState.STATE_PAUSED)
                updateNotification()
                stopProgressTracker()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause playback", e)
            }
        }
    }

    private fun rewindPlayback(deltaMs: Long) {
        mediaPlayer?.let { mp ->
            val newPos = (mp.currentPosition - deltaMs).coerceAtLeast(0L)
            mp.seekTo(newPos.toInt())
            PodcastPlayerManager.currentPositionMs.value = newPos
            updateMediaSessionState(if (mp.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        }
    }

    private fun fastForwardPlayback(deltaMs: Long) {
        mediaPlayer?.let { mp ->
            val maxPos = mp.duration.toLong()
            val newPos = (mp.currentPosition + deltaMs).coerceAtMost(maxPos)
            mp.seekTo(newPos.toInt())
            PodcastPlayerManager.currentPositionMs.value = newPos
            updateMediaSessionState(if (mp.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        }
    }

    private fun seekToPosition(positionMs: Long) {
        mediaPlayer?.let { mp ->
            mp.seekTo(positionMs.toInt())
            PodcastPlayerManager.currentPositionMs.value = positionMs
            updateMediaSessionState(if (mp.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
        }
    }

    private fun setPlaybackSpeedInternal(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { mp ->
                try {
                    val params = mp.playbackParams ?: PlaybackParams()
                    params.speed = speed
                    mp.playbackParams = params
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set playback speed", e)
                }
            }
        }
    }

    private fun stopPlaybackAndCleanUp() {
        stopProgressTracker()
        try {
            mediaPlayer?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping player", e)
                }
                mp.reset()
                mp.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media player", e)
        }

        abandonAudioFocus()
        unregisterNoisyReceiver()
        PodcastPlayerManager.isPlaying.value = false
        PodcastPlayerManager.isBuffering.value = false
        PodcastPlayerManager.currentPositionMs.value = 0L
        PodcastPlayerManager.activeArticle.value = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping foreground", e)
        }

        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling notification", e)
        }

        stopSelf()
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        var saveCounter = 0
        updateProgressJob = serviceScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val currentPos = mp.currentPosition.toLong()
                        val totalDur = mp.duration.toLong().coerceAtLeast(1L)
                        PodcastPlayerManager.currentPositionMs.value = currentPos
                        PodcastPlayerManager.durationMs.value = totalDur

                        saveCounter++
                        if (saveCounter >= 4 && currentArticleId != null) { // Save every 2s
                            saveCounter = 0
                            val artId = currentArticleId!!
                            com.example.util.PodcastProgressManager.saveProgress(
                                applicationContext,
                                artId,
                                currentPos,
                                totalDur
                            )
                            if (totalDur > 10000L && (currentPos >= totalDur - 5000L || currentPos >= (totalDur * 0.95).toLong())) {
                                serviceScope.launch(Dispatchers.IO) {
                                    try {
                                        val db = com.example.data.local.AppDatabase.getDatabase(applicationContext)
                                        db.rssDao().markArticleAsRead(artId)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error marking episode as read near end", e)
                                    }
                                }
                            }
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopProgressTracker() {
        updateProgressJob?.cancel()
        updateProgressJob = null
    }

    private fun updateMediaSessionMetadata() {
        val session = mediaSession ?: return
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, currentFeedTitle)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, currentFeedTitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, PodcastPlayerManager.durationMs.value)

        coverBitmap?.let {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, it)
            builder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
        }

        session.setMetadata(builder.build())
    }

    private fun updateMediaSessionState(state: Int) {
        val session = mediaSession ?: return
        val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
        val speed = if (state == PlaybackState.STATE_PLAYING) PodcastPlayerManager.playbackSpeed.value else 0f

        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_REWIND or
                        PlaybackState.ACTION_FAST_FORWARD or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP
            )
            .setState(state, pos, speed)

        session.setPlaybackState(stateBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio controls for podcast stream"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val isPlaying = PodcastPlayerManager.isPlaying.value

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rewindIntent = Intent(this, PodcastPlaybackService::class.java).apply { action = ACTION_REWIND }
        val rewindPendingIntent = PendingIntent.getService(
            this, 1, rewindIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, PodcastPlaybackService::class.java).apply { action = ACTION_TOGGLE_PLAY_PAUSE }
        val togglePendingIntent = PendingIntent.getService(
            this, 2, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ffIntent = Intent(this, PodcastPlaybackService::class.java).apply { action = ACTION_FAST_FORWARD }
        val ffPendingIntent = PendingIntent.getService(
            this, 3, ffIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PodcastPlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 4, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder.setContentTitle(currentTitle)
            .setContentText(currentFeedTitle)
            .setSubText("Podcast Audio Stream")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        coverBitmap?.let {
            builder.setLargeIcon(it)
        }

        // Action 0: Rewind 10s
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_rew),
                "Rewind 10s",
                rewindPendingIntent
            ).build()
        )

        // Action 1: Play/Pause
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, playPauseIcon),
                playPauseTitle,
                togglePendingIntent
            ).build()
        )

        // Action 2: Fast Forward 10s
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_ff),
                "Forward 10s",
                ffPendingIntent
            ).build()
        )

        // Action 3: Stop
        builder.addAction(
            Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Close",
                stopPendingIntent
            ).build()
        )

        // Attach MediaSession style
        mediaSession?.sessionToken?.let { token ->
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun fetchCoverBitmap(
        imageUrl: String? = currentImageUrl,
        feedUrl: String? = currentFeedUrl,
        feedTitle: String = currentFeedTitle,
        title: String = currentTitle
    ) {
        serviceScope.launch(Dispatchers.IO) {
            var resolvedUrl = imageUrl?.ifBlank { null }

            // 1. Check DB for feed iconUrl or article imageUrl if primary imageUrl is blank
            if (resolvedUrl == null) {
                try {
                    val db = com.example.data.local.AppDatabase.getDatabase(applicationContext)
                    val rssDao = db.rssDao()
                    if (!feedUrl.isNullOrBlank()) {
                        val feed = rssDao.getFeedByUrl(feedUrl)
                        resolvedUrl = feed?.iconUrl?.ifBlank { null }
                    }
                    if (resolvedUrl == null && !currentArticleId.isNullOrBlank()) {
                        val article = rssDao.getArticleById(currentArticleId!!)
                        resolvedUrl = article?.imageUrl?.ifBlank { null }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error looking up feed icon from database: ${e.message}")
                }
            }

            // 2. Fallback to PodcastMetadataGrabber if still blank
            if (resolvedUrl == null) {
                try {
                    resolvedUrl = com.example.util.PodcastMetadataGrabber.autoGrabBestThumbnail(
                        feedTitle = feedTitle,
                        episodeTitle = title,
                        feedUrl = feedUrl ?: ""
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error auto-grabbing podcast thumbnail: ${e.message}")
                }
            }

            if (resolvedUrl.isNullOrBlank()) return@launch

            try {
                val url = URL(resolvedUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    doInput = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept", "image/*,*/*")
                }
                connection.connect()
                val inputStream: InputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                connection.disconnect()

                if (bitmap != null) {
                    coverBitmap = bitmap
                    withContext(Dispatchers.Main) {
                        updateMediaSessionMetadata()
                        updateNotification()
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not load cover bitmap from $resolvedUrl: ${e.message}")
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> mediaPlayer?.setVolume(0.3f, 0.3f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                resumePlayback()
            }
        }
    }

    override fun onDestroy() {
        unregisterNoisyReceiver()
        stopProgressTracker()
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
        abandonAudioFocus()
        super.onDestroy()
    }
}
