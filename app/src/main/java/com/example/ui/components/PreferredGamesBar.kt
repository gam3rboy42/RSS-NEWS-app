package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingBorder
import com.example.ui.theme.NothingDarkGray
import com.example.ui.theme.NothingRed
import com.example.ui.theme.NothingSurface
import com.example.ui.theme.NothingTextMuted
import com.example.ui.theme.NothingTextSecondary
import com.example.ui.theme.NothingWhite

private val POPULAR_GAME_SUGGESTIONS = listOf(
    "Elden Ring", "Genshin Impact", "Cyberpunk 2077", "Zelda", "GTA VI", 
    "Valorant", "Minecraft", "Final Fantasy", "Starfield", "Overwatch", 
    "Diablo IV", "Hollow Knight", "Fallout", "Pokemon", "World of Warcraft"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferredGamesBar(
    preferredGames: List<String>,
    onlyPreferredGames: Boolean,
    onToggleOnlyPreferred: () -> Unit,
    onAddGame: (String) -> Unit,
    onRemoveGame: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newGameInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NothingDarkGray)
            .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = "Gaming",
                    tint = NothingRed,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PREFERRED GAMES",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = NothingWhite
                )
            }

            // Toggle Preferred Only Filter
            Box(
                modifier = Modifier
                    .testTag("toggle_preferred_games_filter")
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (onlyPreferredGames) NothingRed else NothingSurface)
                    .border(1.dp, if (onlyPreferredGames) NothingRed else NothingBorder, RoundedCornerShape(4.dp))
                    .clickable { onToggleOnlyPreferred() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (onlyPreferredGames) "FILTERED TO PREFERRED" else "SHOW ALL GAMING",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (onlyPreferredGames) NothingWhite else NothingTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Row of Preferred Games Chips with ✕ button
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Box(
                    modifier = Modifier
                        .testTag("add_preferred_game_chip")
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingSurface)
                        .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                        .clickable { showAddDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Game",
                            tint = NothingRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ADD GAME",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = NothingRed
                        )
                    }
                }
            }

            items(preferredGames) { game ->
                Box(
                    modifier = Modifier
                        .testTag("preferred_game_chip_$game")
                        .clip(RoundedCornerShape(4.dp))
                        .background(NothingSurface)
                        .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                        .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = game.uppercase(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = NothingWhite,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Prominent ✕ to remove game easily
                        Box(
                            modifier = Modifier
                                .testTag("remove_game_$game")
                                .clip(RoundedCornerShape(2.dp))
                                .clickable { onRemoveGame(game) }
                                .padding(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove $game",
                                tint = NothingRed,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // ADD / MANAGE PREFERRED GAMES DIALOG
    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NothingBlack,
                border = androidx.compose.foundation.BorderStroke(1.dp, NothingRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MANAGE PREFERRED GAMES",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = NothingWhite
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NothingWhite,
                            modifier = Modifier
                                .clickable { showAddDialog = false }
                                .size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "TYPE ANY GAME NAME:",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextMuted,
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newGameInput,
                            onValueChange = { newGameInput = it },
                            placeholder = {
                                Text(
                                    "e.g. World of Warcraft",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = NothingTextMuted
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = NothingWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NothingRed,
                                unfocusedBorderColor = NothingBorder,
                                focusedContainerColor = NothingSurface,
                                unfocusedContainerColor = NothingSurface
                            ),
                            modifier = Modifier
                                .testTag("preferred_game_input")
                                .weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (newGameInput.isNotBlank()) {
                                    onAddGame(newGameInput.trim())
                                    newGameInput = ""
                                }
                            },
                            enabled = newGameInput.isNotBlank(),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NothingRed,
                                contentColor = NothingWhite
                            ),
                            modifier = Modifier
                                .testTag("submit_preferred_game_button")
                                .height(52.dp)
                        ) {
                            Text("ADD", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Preferred Games
                    Text(
                        text = "YOUR ACTIVE GAMES (TAP ✕ TO REMOVE):",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (preferredGames.isEmpty()) {
                        Text(
                            text = "No preferred games set. Add games below!",
                            style = MaterialTheme.typography.bodySmall,
                            color = NothingTextMuted
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            preferredGames.forEach { game ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NothingSurface)
                                        .border(1.dp, NothingRed, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = game,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = NothingWhite
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $game",
                                            tint = NothingRed,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { onRemoveGame(game) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Popular Suggestions
                    Text(
                        text = "POPULAR SUGGESTIONS (TAP TO ADD):",
                        style = MaterialTheme.typography.labelSmall,
                        color = NothingTextMuted,
                        fontSize = 10.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        POPULAR_GAME_SUGGESTIONS.filter { sug ->
                            !preferredGames.any { it.equals(sug, ignoreCase = true) }
                        }.take(10).forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NothingDarkGray)
                                    .border(1.dp, NothingBorder, RoundedCornerShape(4.dp))
                                    .clickable { onAddGame(suggestion) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add $suggestion",
                                        tint = NothingRed,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = suggestion,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = NothingTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showAddDialog = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NothingDarkGray,
                            contentColor = NothingWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DONE", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
