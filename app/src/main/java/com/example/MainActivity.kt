package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import com.example.ui.navigation.NothingBottomBar
import com.example.ui.navigation.ScreenRoute
import com.example.ui.screens.ArticleDetailScreen
import com.example.ui.screens.FeedDiscoveryScreen
import com.example.ui.screens.FeedStreamScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingTheme
import com.example.ui.viewmodel.RssViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RssViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NothingTheme {
                var currentRoute by remember { mutableStateOf<ScreenRoute>(ScreenRoute.Stream) }
                val activeArticle by viewModel.selectedArticleForReading.collectAsState()

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NothingBlack)
                        .statusBarsPadding(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (activeArticle == null) {
                            NothingBottomBar(
                                currentRoute = currentRoute.route,
                                onNavigate = { route ->
                                    if (route == ScreenRoute.Bookmarks) {
                                        viewModel.onlyBookmarks.value = true
                                    } else if (currentRoute == ScreenRoute.Bookmarks) {
                                        viewModel.onlyBookmarks.value = false
                                    }
                                    currentRoute = route
                                }
                            )
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
                                    ScreenRoute.Stream -> {
                                        FeedStreamScreen(
                                            viewModel = viewModel,
                                            onArticleClick = { /* Article set in ViewModel */ }
                                        )
                                    }
                                    ScreenRoute.Discover -> {
                                        FeedDiscoveryScreen(viewModel = viewModel)
                                    }
                                    ScreenRoute.Bookmarks -> {
                                        FeedStreamScreen(
                                            viewModel = viewModel,
                                            onArticleClick = { /* Article set in ViewModel */ }
                                        )
                                    }
                                    ScreenRoute.Settings -> {
                                        SettingsScreen(viewModel = viewModel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
