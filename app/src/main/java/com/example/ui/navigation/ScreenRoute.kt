package com.example.ui.navigation

sealed class ScreenRoute(val route: String, val label: String) {
    object Stream : ScreenRoute("stream", "STREAM")
    object Podcasts : ScreenRoute("podcasts", "PODCASTS")
    object Discover : ScreenRoute("discover", "DISCOVER")
    object Bookmarks : ScreenRoute("bookmarks", "SAVED")
    object Settings : ScreenRoute("settings", "SETTINGS")
}
