package com.kingmc.flexmusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kingmc.flexmusic.data.model.Song

private val demoCovers = listOf(
    "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=200&h=200&fit=crop",
    "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=200&h=200&fit=crop",
    "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=200&h=200&fit=crop",
    "https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=200&h=200&fit=crop"
)

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        modifier = modifier,
        uiState = uiState,
        hasAudioPermission = hasAudioPermission,
        onRequestPermission = onRequestPermission,
        onQueryChange = viewModel::onQueryChange,
        onScanClick = viewModel::refreshLibrary,
        onSongClick = viewModel::playSong,
        onToggleShowAll = viewModel::toggleShowAllSongs
    )
}

@Composable
private fun HomeScreen(
    modifier: Modifier,
    uiState: HomeUiState,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onSongClick: (Song) -> Unit,
    onToggleShowAll: () -> Unit
) {
    val songs = uiState.songs
    val displaySongs = if (uiState.showAllSongs) songs else songs.take(10)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFCFAF7)),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Flex Music", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF1F2937))
                    Text("\u672c\u5730\u97f3\u4e50 \u00b7 \u7075\u52a8\u4f53\u9a8c", color = Color(0xFF9CA3AF))
                }
            }
        }

        item {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                label = { Text("\u641c\u7d22\u6b4c\u66f2\u6216\u6b4c\u624b") },
                singleLine = true
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF34D399), Color(0xFF14B8A6)))
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("\u4eca\u65e5\u63a8\u8350", color = Color.White)
                        Text("\u6c1b\u56f4\u611f\u6b4c\u5355 \u00b7 \u843d\u65e5\u98de\u8f66", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = { if (hasAudioPermission) onScanClick() else onRequestPermission() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            modifier = Modifier.padding(top = 8.dp),
                            enabled = !uiState.isLoading && !uiState.isFetchingExtras
                        ) {
                            if (uiState.isLoading || uiState.isFetchingExtras) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            Text(
                                if (uiState.isFetchingExtras) (uiState.fetchProgress ?: "\u83b7\u53d6\u4e2d...")
                                else if (hasAudioPermission) "\u626b\u63cf\u5e76\u66f4\u65b0"
                                else "\u6388\u6743\u5e76\u626b\u63cf",
                                color = Color.White
                            )
                        }
                    }
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=160&h=160&fit=crop",
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("\u672c\u5730\u4e50\u5e93", style = MaterialTheme.typography.titleMedium, color = Color(0xFF1F2937))
                if (songs.isNotEmpty()) {
                    Text(
                        text = if (uiState.showAllSongs) "\u6536\u8d77" else "\u67e5\u770b\u5168\u90e8",
                        color = Color(0xFF10B981),
                        modifier = Modifier.clickable { onToggleShowAll() }
                    )
                }
            }
        }

        if (songs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (uiState.hasScanned) "\u672a\u627e\u5230\u6b4c\u66f2" else "\u8bf7\u5148\u626b\u63cf\u672c\u5730\u6b4c\u66f2",
                        color = Color(0xFF9CA3AF),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        itemsIndexed(displaySongs, key = { _, it -> it.id }) { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSongClick(song) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AsyncImage(
                    model = song.onlineCoverUrl
                        ?: song.albumArtUri
                        ?: demoCovers[index % demoCovers.size],
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF1F2937))
                    Text(
                        "${song.artist} \u00b7 ${formatDuration(song.durationMs)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color(0xFF9CA3AF)
                    )
                }
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFF10B981))
            }
        }

        item {
            Text(
                text = uiState.statusMessage.orEmpty(),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color(0xFF0F766E)
            )
        }

        item { androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(92.dp)) }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun Spacer(modifier: Modifier) {
    androidx.compose.foundation.layout.Spacer(modifier = modifier)
}
