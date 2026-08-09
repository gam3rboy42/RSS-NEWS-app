package com.example.ui.navigation

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@Composable
fun NothingBottomBar(
    currentRoute: String,
    onNavigate: (ScreenRoute) -> Unit
) {
    val items = listOf(
        ScreenRoute.Stream,
        ScreenRoute.Podcasts,
        ScreenRoute.Discover,
        ScreenRoute.Bookmarks,
        ScreenRoute.Settings
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NothingBlack)
            .border(width = 1.dp, color = NothingBorder)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = currentRoute == item.route

                val icon = when (item) {
                    ScreenRoute.Stream -> if (isSelected) Icons.Filled.Newspaper else Icons.Outlined.Newspaper
                    ScreenRoute.Podcasts -> if (isSelected) Icons.Filled.Headphones else Icons.Outlined.Headphones
                    ScreenRoute.Discover -> if (isSelected) Icons.Filled.Explore else Icons.Outlined.Explore
                    ScreenRoute.Bookmarks -> if (isSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
                    ScreenRoute.Settings -> if (isSelected) Icons.Filled.RssFeed else Icons.Outlined.RssFeed
                }

                Box(
                    modifier = Modifier
                        .testTag("nav_${item.route}")
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) NothingRed else NothingBlack)
                        .clickable { onNavigate(item) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = item.label,
                            tint = if (isSelected) NothingWhite else NothingTextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.label,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp,
                            color = if (isSelected) NothingWhite else NothingTextSecondary
                        )
                    }
                }
            }
        }
    }
}
