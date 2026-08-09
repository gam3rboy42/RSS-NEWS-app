package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.content.Intent
import com.example.data.local.ArticleEntity
import com.example.service.PodcastPlayerManager
import com.example.ui.components.PodcastMediaDialog
import com.example.ui.components.PodcastMiniPlayer
import com.example.ui.navigation.NothingBottomBar
import com.example.ui.navigation.ScreenRoute
import com.example.ui.screens.ArticleDetailScreen
import com.example.ui.screens.FeedDiscoveryScreen
import com.example.ui.screens.FeedStreamScreen
import com.example.ui.screens.PodcastFeedScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingTheme
import com.example.ui.viewmodel.RssViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RssViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            NothingTheme {
                var routeBackStack by remember { mutableStateOf(listOf<ScreenRoute>(ScreenRoute.Stream)) }
                val currentRoute = routeBackStack.lastOrNull() ?: ScreenRoute.Stream
                val activeArticle by viewModel.selectedArticleForReading.collectAsState()
                val playingPodcastArticle by PodcastPlayerManager.activeArticle.collectAsState()
                var expandedPodcastArticle by remember { mutableStateOf<ArticleEntity?>(null) }

                // Intercept back gesture when article reader is active
                BackHandler(enabled = activeArticle != null) {
                    viewModel.closeArticleReader()
                }

                // Intercept back gesture to navigate to previous screen tab when backstack is not empty
                BackHandler(enabled = activeArticle == null && routeBackStack.size > 1) {
                    val previousStack = routeBackStack.dropLast(1)
                    val nextRoute = previousStack.lastOrNull() ?: ScreenRoute.Stream

                    if (currentRoute == ScreenRoute.Bookmarks && nextRoute != ScreenRoute.Bookmarks) {
                        viewModel.onlyBookmarks.value = false
                    } else if (nextRoute == ScreenRoute.Bookmarks) {
                        viewModel.onlyBookmarks.value = true
                    }

                    if (nextRoute == ScreenRoute.Podcasts) {
                        viewModel.setCategory("PODCASTS")
                    } else if (currentRoute == ScreenRoute.Podcasts && nextRoute != ScreenRoute.Podcasts) {
                        viewModel.setCategory("ALL")
                    }

                    routeBackStack = previousStack
                }

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NothingBlack)
                        .statusBarsPadding(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        Column {
                            if (activeArticle == null && playingPodcastArticle != null) {
                                PodcastMiniPlayer(
                                    onExpandPlayer = {
                                        expandedPodcastArticle = playingPodcastArticle
                                    }
                                )
                            }
                            if (activeArticle == null) {
                                NothingBottomBar(
                                    currentRoute = currentRoute.route,
                                    onNavigate = { route ->
                                        if (route == ScreenRoute.Bookmarks) {
                                            viewModel.onlyBookmarks.value = true
                                            if (viewModel.selectedCategory.value == "PODCASTS") {
                                                viewModel.setCategory("ALL")
                                            }
                                        } else if (route == ScreenRoute.Stream) {
                                            viewModel.onlyBookmarks.value = false
                                            if (viewModel.selectedCategory.value == "PODCASTS") {
                                                viewModel.setCategory("ALL")
                                            }
                                        } else if (route == ScreenRoute.Podcasts) {
                                            viewModel.onlyBookmarks.value = false
                                            viewModel.setCategory("PODCASTS")
                                        } else {
                                            viewModel.onlyBookmarks.value = false
                                            if (viewModel.selectedCategory.value == "PODCASTS") {
                                                viewModel.setCategory("ALL")
                                            }
                                        }

                                        if (route != currentRoute) {
                                            if (route == ScreenRoute.Stream) {
                                                routeBackStack = listOf(ScreenRoute.Stream)
                                            } else {
                                                routeBackStack = routeBackStack.filter { it != route } + route
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(NothingBlack)
                            .padding(innerPadding)
                    ) {
                        if (activeArticle != null) {
                            ArticleDetailScreen(
                                article = activeArticle!!,
                                viewModel = viewModel,
                                onBackClick = { viewModel.closeArticleReader() }
                            )
                        } else {
                            AnimatedContent(
                                targetState = currentRoute,
                                label = "ScreenTransition"
                            ) { targetRoute ->
                                when (targetRoute) {
                                    is ScreenRoute.Stream -> {
                                        FeedStreamScreen(
                                            viewModel = viewModel,
                                            onArticleClick = { /* Article set in ViewModel */ }
                                        )
                                    }
                                    is ScreenRoute.Podcasts -> {
                                        PodcastFeedScreen(
                                            viewModel = viewModel,
                                            onNavigateToDiscover = {
                                                routeBackStack = routeBackStack.filter { it != ScreenRoute.Discover } + ScreenRoute.Discover
                                            }
                                        )
                                    }
                                    is ScreenRoute.Discover -> {
                                        FeedDiscoveryScreen(viewModel = viewModel)
                                    }
                                    is ScreenRoute.Bookmarks -> {
                                        FeedStreamScreen(
                                            viewModel = viewModel,
                                            onArticleClick = { /* Article set in ViewModel */ }
                                        )
                                    }
                                    is ScreenRoute.Settings -> {
                                        SettingsScreen(viewModel = viewModel)
                                    }
                                }
                            }
                        }

                        // Expanded Podcast Media Overlay
                        if (expandedPodcastArticle != null) {
                            PodcastMediaDialog(
                                article = expandedPodcastArticle!!,
                                onDismiss = { expandedPodcastArticle = null },
                                onOpenInBrowser = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            val articleId = it.getStringExtra("EXTRA_ARTICLE_ID")
            val articleUrl = it.getStringExtra("EXTRA_ARTICLE_URL")
            val isPodcast = it.getBooleanExtra("EXTRA_IS_PODCAST", false)
            if (isPodcast) {
                viewModel.setCategory("PODCASTS")
            }
            if (!articleId.isNullOrBlank() || !articleUrl.isNullOrBlank()) {
                viewModel.openArticleFromIntent(articleId, articleUrl)
            }
        }
    }
}
