package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.ArticleEntity
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingDealOrange
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingSurfaceVariant
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite
import com.example.ui.viewmodel.RssViewModel
import com.example.util.InAppBrowser

@Composable
fun ArticleDetailScreen(
    article: ArticleEntity,
    viewModel: RssViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val fontSizeMultiplier by viewModel.fontSizeMultiplier.collectAsState()

    val titleSize = (20 * fontSizeMultiplier).sp
    val bodySize = (15 * fontSizeMultiplier).sp
    val lineSpacing = (22 * fontSizeMultiplier).sp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        // Reader Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NothingDarkGray)
                .border(1.dp, NothingBorder)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("article_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NothingWhite
                    )
                }

                Text(
                    text = article.feedTitle.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = NothingWhite
                )
            }

            // Controls: Font Size, Bookmark, Share, Browser
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Font smaller
                IconButton(
                    onClick = {
                        if (fontSizeMultiplier > 0.8f) {
                            viewModel.changeFontSize(fontSizeMultiplier - 0.2f)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = "A-",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NothingWhite,
                        fontSize = 11.sp
                    )
                }

                // Font larger
                IconButton(
                    onClick = {
                        if (fontSizeMultiplier < 1.6f) {
                            viewModel.changeFontSize(fontSizeMultiplier + 0.2f)
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text(
                        text = "A+",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = NothingWhite,
                        fontSize = 14.sp
                    )
                }

                // Bookmark
                IconButton(
                    onClick = {
                        viewModel.toggleBookmark(article.id, article.isBookmarked)
                    },
                    modifier = Modifier
                        .testTag("bookmark_article_detail")
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (article.isBookmarked) NothingRed else NothingWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Share
                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, article.title)
                            putExtra(Intent.EXTRA_TEXT, "${article.title}\n\n${article.link}")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Article"))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = NothingWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Open in browser
                IconButton(
                    onClick = {
                        InAppBrowser.openUrl(context, article.link)
                    },
                    modifier = Modifier
                        .testTag("open_browser_button")
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Browser",
                        tint = NothingRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Offline Reader Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Source & Meta
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = article.category.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NothingRed
                    )
                }

                Text(
                    text = article.pubDate,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = NothingTextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Article Title
            Text(
                text = article.title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                lineHeight = (titleSize.value * 1.25).sp,
                color = NothingWhite
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Offline Mode Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NothingSurface)
                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(NothingRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "OFFLINE CACHED ARTICLE MODE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = NothingTextSecondary
                )
            }

            // Deal Flag if deal
            if (article.isDeal) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingDealOrange.copy(alpha = 0.15f))
                        .border(1.dp, NothingDealOrange, RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "⚡ DEAL DETECTED: This article contains shopping discounts or promotional offer keywords.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingDealOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Article Main Thumbnail if available
            if (!article.imageUrl.isNull_or_blank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = "Article image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, NothingBorder, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Main Text Content
            val displayContent = article.content.ifBlank { article.description }
            Text(
                text = displayContent,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = bodySize,
                lineHeight = lineSpacing,
                color = NothingWhite
            )

            Spacer(modifier = Modifier.height(32.dp))

            // External Source Button
            Button(
                onClick = {
                    InAppBrowser.openUrl(context, article.link)
                },
                modifier = Modifier
                    .testTag("open_original_source_button")
                    .fillMaxWidth()
                    .height(48.dp)
                    .border(1.dp, NothingRed, RoundedCornerShape(4.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NothingDarkGray,
                    contentColor = NothingWhite
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "READ ORIGINAL ON ${article.feedTitle.uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = NothingWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = "Open",
                        tint = NothingRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

private fun String?.isNull_or_blank(): Boolean = this == null || this.isBlank()
