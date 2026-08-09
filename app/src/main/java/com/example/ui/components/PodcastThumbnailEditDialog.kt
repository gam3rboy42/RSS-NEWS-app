package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.ArticleEntity
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingSurfaceVariant
import com.example.ui.theme.NothingWhite
import com.example.util.PodcastArtworkCandidate
import com.example.util.PodcastMetadataGrabber
import kotlinx.coroutines.launch

@Composable
fun PodcastThumbnailEditDialog(
    article: ArticleEntity,
    currentSeriesIconUrl: String = "",
    onDismiss: () -> Unit,
    onSaveEpisodeThumbnail: (String?) -> Unit,
    onSaveSeriesThumbnail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("EPISODE") } // "EPISODE" or "SERIES"
    var customUrlInput by remember {
        mutableStateOf(if (activeTab == "EPISODE") (article.imageUrl ?: "") else currentSeriesIconUrl)
    }

    var searchQuery by remember {
        mutableStateOf(
            if (activeTab == "EPISODE") "${article.feedTitle} ${article.title}" else article.feedTitle
        )
    }

    var candidates by remember { mutableStateOf<List<PodcastArtworkCandidate>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun performSearch(queryToUse: String) {
        if (queryToUse.isBlank()) return
        isSearching = true
        statusMessage = "Searching iTunes and web for artwork..."
        coroutineScope.launch {
            val results = PodcastMetadataGrabber.searchArtworkCandidates(queryToUse)
            candidates = results
            isSearching = false
            statusMessage = if (results.isEmpty()) "No artwork found. Try a different search query." else null
        }
    }

    LaunchedEffect(activeTab) {
        customUrlInput = if (activeTab == "EPISODE") (article.imageUrl ?: "") else currentSeriesIconUrl
        searchQuery = if (activeTab == "EPISODE") "${article.feedTitle} ${article.title}" else article.feedTitle
        performSearch(searchQuery)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NothingDarkGray,
            border = BorderStroke(1.dp, NothingBorder.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PODCAST ARTWORK",
                        color = NothingRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = NothingWhite)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Toggle Tab: Episode vs Series
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NothingSurfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == "EPISODE") NothingRed else NothingDarkGray)
                            .clickable { activeTab = "EPISODE" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "EPISODE THUMBNAIL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NothingWhite
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (activeTab == "SERIES") NothingRed else NothingDarkGray)
                            .clickable { activeTab = "SERIES" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SERIES SHOW ART",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NothingWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Preview Box
                val currentImageToDisplay = if (customUrlInput.isNotBlank()) customUrlInput
                else if (activeTab == "EPISODE" && !article.imageUrl.isNullOrBlank()) article.imageUrl
                else currentSeriesIconUrl

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(NothingSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!currentImageToDisplay.isNullOrBlank()) {
                            AsyncImage(
                                model = currentImageToDisplay,
                                contentDescription = "Current Cover Art",
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = NothingWhite.copy(alpha = 0.5f))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeTab == "EPISODE") article.title else article.feedTitle,
                            color = NothingWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (currentImageToDisplay.isNullOrBlank()) "No thumbnail set" else "Custom artwork set",
                            color = if (currentImageToDisplay.isNullOrBlank()) NothingRed else NothingWhite.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }

                    if (!currentImageToDisplay.isNullOrBlank()) {
                        IconButton(
                            onClick = {
                                customUrlInput = ""
                                if (activeTab == "EPISODE") {
                                    onSaveEpisodeThumbnail(null)
                                } else {
                                    onSaveSeriesThumbnail("")
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Artwork", tint = NothingRed)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Custom URL Input
                Text("IMAGE URL", color = NothingWhite.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        placeholder = { Text("https://...", color = NothingWhite.copy(alpha = 0.4f), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NothingRed,
                            unfocusedBorderColor = NothingBorder,
                            focusedTextColor = NothingWhite,
                            unfocusedTextColor = NothingWhite
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (activeTab == "EPISODE") {
                                onSaveEpisodeThumbnail(customUrlInput.ifBlank { null })
                            } else {
                                onSaveSeriesThumbnail(customUrlInput)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NothingRed),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("SAVE", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Web Search Grabber Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ON-DEVICE ARTWORK GRABBER",
                        color = NothingWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = {
                            isSearching = true
                            coroutineScope.launch {
                                val best = PodcastMetadataGrabber.autoGrabBestThumbnail(
                                    article.feedTitle,
                                    if (activeTab == "EPISODE") article.title else "",
                                    article.feedUrl
                                )
                                isSearching = false
                                if (!best.isNullOrBlank()) {
                                    customUrlInput = best
                                    if (activeTab == "EPISODE") onSaveEpisodeThumbnail(best) else onSaveSeriesThumbnail(best)
                                } else {
                                    statusMessage = "Could not auto-grab cover art automatically."
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NothingRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AUTO GRAB", color = NothingRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search podcast artwork...", color = NothingWhite.copy(alpha = 0.4f), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NothingRed,
                            unfocusedBorderColor = NothingBorder,
                            focusedTextColor = NothingWhite,
                            unfocusedTextColor = NothingWhite
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { performSearch(searchQuery) },
                        modifier = Modifier.background(NothingSurfaceVariant, RoundedCornerShape(6.dp))
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = NothingWhite)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isSearching) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NothingRed, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Searching web metadata...", color = NothingWhite.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                }

                statusMessage?.let { msg ->
                    Text(text = msg, color = NothingWhite.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                }

                // Candidate Thumbnails Grid/List
                if (candidates.isNotEmpty()) {
                    Text("TAP TO SELECT ARTWORK:", color = NothingWhite.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        items(candidates) { candidate ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        customUrlInput = candidate.imageUrl
                                        if (activeTab == "EPISODE") {
                                            onSaveEpisodeThumbnail(candidate.imageUrl)
                                        } else {
                                            onSaveSeriesThumbnail(candidate.imageUrl)
                                        }
                                    },
                                color = NothingSurfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = candidate.imageUrl,
                                        contentDescription = candidate.title,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(candidate.title, color = NothingWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                        if (candidate.author.isNotBlank()) {
                                            Text(candidate.author, color = NothingWhite.copy(alpha = 0.6f), fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                    Text("SELECT", color = NothingRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
