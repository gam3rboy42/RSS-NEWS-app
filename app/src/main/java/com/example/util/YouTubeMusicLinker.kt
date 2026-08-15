package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object YouTubeMusicLinker {

    /**
     * Attempts to open the podcast episode directly in YouTube Music or YouTube app.
     * Priority:
     * 1. If direct YouTube video ID / URL is available, launch YouTube / YT Music player intent.
     * 2. Search query targeted at YouTube Music app (market:// or vnd.youtube.music).
     * 3. Fallback to YouTube Music web search (https://music.youtube.com/search?q=...)
     */
    fun openInYouTubeMusic(
        context: Context,
        episodeTitle: String,
        feedTitle: String,
        webLink: String = ""
    ) {
        val cleanEpisode = episodeTitle.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
        val cleanFeed = feedTitle.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
        val query = "$cleanFeed $cleanEpisode".trim()
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query.replace(" ", "+")
        }

        // Check if article link is already a youtube / youtu.be link
        if (webLink.contains("youtube.com") || webLink.contains("youtu.be")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webLink)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // fallback
            }
        }

        // Try YouTube Music App Search Intent
        val ytMusicAppUri = Uri.parse("https://music.youtube.com/search?q=$encodedQuery")
        val ytMusicIntent = Intent(Intent.ACTION_VIEW, ytMusicAppUri).apply {
            setPackage("com.google.android.apps.youtube.music")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(ytMusicIntent)
            Toast.makeText(context, "Opening episode in YouTube Music...", Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            // YouTube Music app not installed or cannot resolve intent
        }

        // Try Main YouTube App Search Intent
        val ytAppIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(ytAppIntent)
            Toast.makeText(context, "Opening search in YouTube...", Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            // Main YouTube app cannot handle or not installed
        }

        // Web Fallback
        val webFallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=$encodedQuery")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(webFallbackIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser for YouTube Music", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens YouTube search directly for the episode
     */
    fun openInYouTube(
        context: Context,
        episodeTitle: String,
        feedTitle: String
    ) {
        val query = "$feedTitle $episodeTitle".trim()
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query.replace(" ", "+")
        }

        val ytAppUri = Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
        val ytIntent = Intent(Intent.ACTION_VIEW, ytAppUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(ytIntent)
            Toast.makeText(context, "Opening in YouTube...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch YouTube", Toast.LENGTH_SHORT).show()
        }
    }
}
