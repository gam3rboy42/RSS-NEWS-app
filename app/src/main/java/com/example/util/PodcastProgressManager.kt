package com.example.util

import android.content.Context
import android.util.Log

object PodcastProgressManager {

    private const val TAG = "PodcastProgressManager"
    private const val PREFS_NAME = "podcast_progress_prefs"
    private const val KEY_POS_PREFIX = "pos_"
    private const val KEY_DUR_PREFIX = "dur_"

    fun saveProgress(context: Context, articleId: String, positionMs: Long, durationMs: Long) {
        if (articleId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // If completed or near start, reset position
        if (positionMs < 3000L || (durationMs > 0 && positionMs >= durationMs - 5000L)) {
            editor.remove("$KEY_POS_PREFIX$articleId")
            editor.remove("$KEY_DUR_PREFIX$articleId")
            Log.d(TAG, "Cleared progress for $articleId (at start or near end)")
        } else {
            editor.putLong("$KEY_POS_PREFIX$articleId", positionMs)
            if (durationMs > 0) {
                editor.putLong("$KEY_DUR_PREFIX$articleId", durationMs)
            }
            Log.d(TAG, "Saved progress for $articleId: ${positionMs / 1000}s / ${durationMs / 1000}s")
        }
        editor.apply()
    }

    fun getProgress(context: Context, articleId: String): Long {
        if (articleId.isBlank()) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_POS_PREFIX$articleId", 0L)
    }

    fun getDuration(context: Context, articleId: String): Long {
        if (articleId.isBlank()) return 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("$KEY_DUR_PREFIX$articleId", 0L)
    }

    fun clearProgress(context: Context, articleId: String) {
        if (articleId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("$KEY_POS_PREFIX$articleId")
            .remove("$KEY_DUR_PREFIX$articleId")
            .apply()
    }

    fun formatMs(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
