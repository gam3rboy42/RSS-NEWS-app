package com.example.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val WORK_TAG_BACKGROUND_REFRESH = "rss_background_refresh_work"
    private const val PREFS_NAME = "rss_background_prefs"
    private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"

    fun setRefreshInterval(context: Context, intervalMinutes: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_REFRESH_INTERVAL_MINUTES, intervalMinutes).apply()

        if (intervalMinutes > 0) {
            scheduleBackgroundWork(context, intervalMinutes)
        } else {
            cancelBackgroundWork(context)
        }
    }

    fun getRefreshIntervalMinutes(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_REFRESH_INTERVAL_MINUTES, 60L) // Default 1 hour
    }

    fun scheduleBackgroundWork(context: Context, intervalMinutes: Long) {
        val minInterval = maxOf(intervalMinutes, 15L) // WorkManager minimum is 15 minutes

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<RssBackgroundWorker>(
            minInterval,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(WORK_TAG_BACKGROUND_REFRESH)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_TAG_BACKGROUND_REFRESH,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }

    fun cancelBackgroundWork(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG_BACKGROUND_REFRESH)
    }
}
