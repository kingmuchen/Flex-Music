package com.kingmc.flexmusic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kingmc.flexmusic.player.PlaybackUiState

@Composable
fun MiniPlayerBar(
    modifier: Modifier = Modifier,
    playbackUiState: PlaybackUiState,
    onOpenFullPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    val currentSong = playbackUiState.currentSong ?: return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable(onClick = onOpenFullPlayer),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xF2FFFFFF),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = playbackUiState.onlineCoverUrl
                    ?: currentSong.albumArtUri,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE5E7EB))
            )
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = currentSong.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "\u4e0a\u4e00\u9996", tint = Color(0xFF6B7280))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (playbackUiState.isPlaying) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                    contentDescription = "\u64ad\u653e",
                    tint = Color(0xFF10B981)
                )
            }
            IconButton(onClick = onSkipNext, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "\u4e0b\u4e00\u9996", tint = Color(0xFF6B7280))
            }
            Icon(Icons.Rounded.ExpandLess, contentDescription = null, tint = Color(0xFF9CA3AF))
        }
    }
}
