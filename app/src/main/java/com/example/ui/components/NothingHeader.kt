package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SyncState
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDealOrange
import com.example.ui.theme.NothingGreen
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

@Composable
fun NothingHeader(
    title: String,
    subtitle: String,
    isOnline: Boolean,
    syncState: SyncState,
    hideDeals: Boolean,
    onlyPreferred: Boolean,
    onToggleDeals: () -> Unit,
    onTogglePreferred: () -> Unit,
    onRefresh: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (syncState is SyncState.Syncing) 360f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NothingBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top status strip
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Online/Offline Dot Indicator
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
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) NothingGreen else NothingRed)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOnline) "• ONLINE" else "• OFFLINE MODE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOnline) NothingGreen else NothingRed,
                    fontWeight = FontWeight.Bold
                )
            }

            // Quick toggles: Deal Filter & Preferred Source Filter
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Deal Filter Toggle Button
                Box(
                    modifier = Modifier
                        .testTag("deal_filter_toggle")
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (hideDeals) NothingSurface else NothingDealOrange.copy(alpha = 0.2f))
                        .border(
                            width = 1.dp,
                            color = if (hideDeals) NothingBorder else NothingDealOrange,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onToggleDeals() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (hideDeals) "[ DEALS HIDDEN ]" else "[ DEALS SHOWN ]",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hideDeals) NothingTextSecondary else NothingDealOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Preferred Filter Toggle Button
                Box(
                    modifier = Modifier
                        .testTag("preferred_filter_toggle")
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (onlyPreferred) NothingRed.copy(alpha = 0.25f) else NothingSurface)
                        .border(
                            width = 1.dp,
                            color = if (onlyPreferred) NothingRed else NothingBorder,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onTogglePreferred() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (onlyPreferred) "★ PREFERRED" else "☆ ALL FEEDS",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (onlyPreferred) NothingRed else NothingTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Main Title Block with Nothing red accent dot
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Signature Nothing Red Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NothingRed)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title.uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = NothingWhite,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextMuted
                    )
                }
            }

            // Refresh button
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .testTag("refresh_feeds_button")
                    .border(1.dp, NothingBorder, CircleShape)
                    .background(NothingSurface, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh News",
                    tint = NothingWhite,
                    modifier = Modifier.rotate(rotation)
                )
            }
        }

        // Status banner if error or syncing
        if (syncState is SyncState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NothingRed.copy(alpha = 0.15f))
                    .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = syncState.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = NothingWhite
                )
            }
        } else if (syncState is SyncState.Syncing) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "FETCHING RSS FEEDS & STACKING STORIES...",
                style = MaterialTheme.typography.labelSmall,
                color = NothingRed,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
