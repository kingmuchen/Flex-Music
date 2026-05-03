package com.kingmc.flexmusic.feature.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import coil.compose.AsyncImage
import com.kingmc.flexmusic.feature.player.lyrics.LyricLine
import com.kingmc.flexmusic.player.PlaybackMode
import com.kingmc.flexmusic.player.PlaybackUiState
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerOverlay(
    playbackState: PlaybackUiState,
    uiState: FullPlayerUiState,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onUpdateLyricsSettings: (LyricsSettings) -> Unit,
    onAdjustLyricOffset: (Long) -> Unit = {},
    onResetLyricOffset: () -> Unit = {},
    onAnalyzeAudio: () -> Unit = {},
    onApplyAnalysisOffset: () -> Unit = {},
    onClearAnalysisResult: () -> Unit = {}
) {
    val song = playbackState.currentSong ?: return
    val pagerState = rememberPagerState(initialPage = 0) { 2 }
    val coroutineScope = rememberCoroutineScope()
    
    val lyricsSettings = uiState.lyricsSettings
    var showSettingsSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFCFAF7))
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> FullPlayerPage(
                    playbackState = playbackState,
                    onlineCoverUrl = uiState.onlineCoverUrl,
                    onDismiss = onDismiss,
                    onPlayPause = onPlayPause,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onSeekTo = onSeekTo,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onShowLyrics = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(1)
                        }
                    },
                    onShowSettings = { showSettingsSheet = true }
                )
                else -> LyricsPage(
                    playbackState = playbackState,
                    uiState = uiState,
                    onDismiss = onDismiss,
                    onPlayPause = onPlayPause,
                    onSkipPrevious = onSkipPrevious,
                    onSkipNext = onSkipNext,
                    onSeekTo = onSeekTo,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    lyricsSettings = lyricsSettings,
                    onShowSettings = { showSettingsSheet = true }
                )
            }
        }
        
        if (showSettingsSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                LyricsSettingsSheet(
                    settings = lyricsSettings,
                    offsetInfo = uiState.offsetInfo,
                    analysisResult = uiState.analysisResult,
                    analyzing = uiState.analyzing,
                    onFontSizeChange = { size ->
                        onUpdateLyricsSettings(lyricsSettings.copy(fontSize = size.sp))
                    },
                    onActiveLineColorChange = { color ->
                        onUpdateLyricsSettings(lyricsSettings.copy(activeLineColor = color))
                    },
                    onWordByWordChange = { enabled ->
                        onUpdateLyricsSettings(lyricsSettings.copy(enableWordByWord = enabled))
                    },
                    onAdjustOffset = onAdjustLyricOffset,
                    onResetOffset = onResetLyricOffset,
                    onAnalyze = onAnalyzeAudio,
                    onApplyAnalysis = onApplyAnalysisOffset,
                    onClearAnalysis = onClearAnalysisResult
                )
            }
        }
    }
}

@Composable
private fun FullPlayerPage(
    playbackState: PlaybackUiState,
    onlineCoverUrl: String?,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowSettings: () -> Unit
) {
    val song = playbackState.currentSong ?: return

    val coverModel = when {
        !onlineCoverUrl.isNullOrBlank() -> onlineCoverUrl
        !song.albumArtUri.isNullOrBlank() -> song.albumArtUri
        else -> null
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(1.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                icon = Icons.Rounded.Close,
                onClick = onDismiss
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.width(48.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x1510B981),
                                    Color(0x0810B981),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} · ${song.album}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9CA3AF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ProgressSection(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                onSeekTo = onSeekTo
            )

            PlaybackControls(
                isPlaying = playbackState.isPlaying,
                onPlayPause = onPlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaybackModeButton(
                    mode = playbackState.mode,
                    onClick = onCyclePlaybackMode
                )
                
                ActionButton(
                    icon = Icons.Rounded.Lyrics,
                    onClick = onShowLyrics,
                    tint = Color(0xFF10B981)
                )
                
                ActionButton(
                    icon = Icons.Rounded.MoreHoriz,
                    onClick = onShowSettings,
                    tint = Color(0xFF9CA3AF)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun LyricsPage(
    playbackState: PlaybackUiState,
    uiState: FullPlayerUiState,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    lyricsSettings: LyricsSettings,
    onShowSettings: () -> Unit
) {
    val song = playbackState.currentSong ?: return
    val lines = uiState.lyrics?.lines.orEmpty()
    val currentPos = playbackState.positionMs
    
    val userOffset = uiState.offsetInfo.totalOffset
    val adjustedPos = currentPos + userOffset
    
    val hasYrcLines = lines.any { it.words.size > 1 }
    val seekForMatch = if (hasYrcLines) adjustedPos else adjustedPos + 300L
    
    val currentIndex = if (lines.isEmpty()) 0
    else {
        val exactMatch = lines.indexOfFirst { 
            seekForMatch >= it.startMs && seekForMatch < it.endMs 
        }
        
        if (exactMatch >= 0) {
            exactMatch
        } else {
            var lastMatch = -1
            for (i in lines.indices) {
                if (lines[i].startMs <= seekForMatch) {
                    lastMatch = i
                } else {
                    break
                }
            }
            
            if (lastMatch >= 0) lastMatch else 0
        }
    }
    val listState = rememberLazyListState()
    val targetIndex = remember { mutableStateOf(0) }
    val isUserScrolling = remember { mutableStateOf(false) }
    
    LaunchedEffect(currentIndex, lines.size) {
        if (lines.isNotEmpty() && currentIndex >= 0 && !isUserScrolling.value) {
            targetIndex.value = currentIndex + 1
            listState.animateScrollToItem(
                index = currentIndex,
                scrollOffset = -400
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(1.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                icon = Icons.Rounded.Close,
                onClick = onDismiss
            )
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.width(48.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
        ) {
            when {
                uiState.loadingLyrics -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color(0xFF10B981),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "歌词加载中...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
                lines.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lyrics,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color(0xFFD1D5DB)
                            )
                            Text(
                                text = "暂无歌词",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFF9CA3AF)
                            )
                            Text(
                                text = "可添加同名 .lrc 文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD1D5DB)
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                        
                        itemsIndexed(lines, key = { index, line -> "${index}_${line.startMs}" }) { index, line ->
                            val isActive = index == currentIndex
                            val isNearActive = abs(index - currentIndex) == 1
                            LyricLineItem(
                                line = line,
                                currentMs = adjustedPos,
                                isActive = isActive,
                                isNearActive = isNearActive,
                                lyricsSettings = lyricsSettings,
                                onClick = {
                                    onSeekTo(line.startMs)
                                }
                            )
                        }
                        
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressSection(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                onSeekTo = onSeekTo
            )

            PlaybackControls(
                isPlaying = playbackState.isPlaying,
                onPlayPause = onPlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaybackModeButton(
                    mode = playbackState.mode,
                    onClick = onCyclePlaybackMode
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (userOffset != 0L) {
                        Text(
                            text = "${if (userOffset > 0) "+" else ""}${userOffset}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF10B981),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x1510B981))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    ActionButton(
                        icon = Icons.Rounded.MoreHoriz,
                        onClick = onShowSettings,
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PlaybackModeButton(
    mode: PlaybackMode,
    onClick: () -> Unit
) {
    val (icon, tint) = when (mode) {
        PlaybackMode.ORDER -> Icons.Rounded.TrendingFlat to Color(0xFF9CA3AF)
        PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle to Color(0xFF10B981)
        PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne to Color(0xFF10B981)
    }
    
    ActionButton(
        icon = icon,
        onClick = onClick,
        tint = tint
    )
}

@Composable
private fun LyricLineItem(
    line: LyricLine,
    currentMs: Long,
    isActive: Boolean,
    isNearActive: Boolean,
    lyricsSettings: LyricsSettings,
    onClick: () -> Unit
) {
    val targetAlpha = when {
        isActive -> 1f
        isNearActive -> 0.42f
        else -> 0.22f
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "line_alpha"
    )
    
    val targetScale = when {
        isActive -> 1f
        isNearActive -> 0.975f
        else -> 0.95f
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "line_scale"
    )

    val blurRadius by animateFloatAsState(
        targetValue = when {
            isActive -> 0f
            isNearActive -> 1.2f
            else -> 2.2f
        },
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "line_blur"
    )

    val textStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = if (isActive) (lyricsSettings.fontSize.value + 4).sp else lyricsSettings.fontSize,
        textAlign = TextAlign.Center,
        lineHeight = (if (isActive) lyricsSettings.fontSize.value + 14 else lyricsSettings.fontSize.value + 12).sp
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .scale(animatedScale)
            .blur(blurRadius.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
    ) {
        val activeColor = lyricsSettings.activeLineColor
        val inactiveColor = Color(0xFFA7ADBA)
        val activeAnnotated = remember(line.text, activeColor) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = activeColor)) { append(line.text) }
            }
        }
        val wordByWordAnnotated = remember(line, currentMs, activeColor) {
            line.toWordByWordAnnotated(
                currentMs = currentMs,
                activeColor = activeColor,
                inactiveColor = Color(0xFFB7BCC8),
                wordAdvanceMs = 120L
            )
        }

        if (isActive) {
            val foreground = if (lyricsSettings.enableWordByWord && line.words.isNotEmpty()) {
                wordByWordAnnotated
            } else {
                activeAnnotated
            }
            Text(
                text = foreground,
                style = textStyle,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        } else {
            Text(
                text = line.text,
                style = textStyle,
                color = inactiveColor,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }

        if (!line.translation.isNullOrBlank()) {
            Text(
                text = line.translation.orEmpty(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (lyricsSettings.fontSize.value * 0.62f).sp,
                    lineHeight = (lyricsSettings.fontSize.value * 0.95f).sp,
                    textAlign = TextAlign.Center
                ),
                color = if (isActive) Color(0xFF8B93A6) else Color(0xFFBCC2CE),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ProgressSection(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    showSource: Boolean = false,
    source: String = ""
) {
    val duration = durationMs.coerceAtLeast(1L)
    val sliderValue = positionMs.coerceIn(0L, duration).toFloat() / duration.toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Slider(
            value = sliderValue,
            onValueChange = { onSeekTo((it * duration).roundToInt().toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF10B981),
                activeTrackColor = Color(0xFF10B981),
                inactiveTrackColor = Color(0xFFE5E7EB)
            )
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9CA3AF)
            )
            if (showSource && source.isNotBlank()) {
                Text(
                    text = "${formatTime(durationMs)} · $source",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            } else {
                Text(
                    text = formatTime(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    compact: Boolean = false
) {
    val mainButtonSize = if (compact) 64.dp else 80.dp
    val sideButtonSize = if (compact) 52.dp else 64.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(
            icon = Icons.Rounded.SkipPrevious,
            onClick = onSkipPrevious,
            size = sideButtonSize
        )
        
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPause,
            size = mainButtonSize
        )
        
        ActionButton(
            icon = Icons.Rounded.SkipNext,
            onClick = onSkipNext,
            size = sideButtonSize
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color = Color(0xFF374151)
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 80.dp
) {
    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "play_button_scale"
    )

    Box(
        modifier = Modifier
            .scale(animatedScale)
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF10B981)),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(size)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

private fun LyricLine.toAnnotated(currentMs: Long, activeColor: Color): AnnotatedString {
    if (words.isEmpty()) {
        return AnnotatedString(text)
    }

    val inactiveColor = Color(0xFF9CA3AF)

    return buildAnnotatedString {
        words.forEach { word ->
            if (currentMs < word.startMs) {
                pushStyle(SpanStyle(color = inactiveColor))
                append(word.text)
                pop()
            } else if (currentMs >= word.endMs) {
                pushStyle(SpanStyle(color = activeColor))
                append(word.text)
                pop()
            } else {
                val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
                val elapsed = (currentMs - word.startMs).coerceIn(0L, duration)
                val progress = elapsed.toFloat() / duration.toFloat()
                
                val charCount = word.text.length
                val highlightCount = (charCount * progress).roundToInt().coerceIn(0, charCount)
                
                if (highlightCount > 0) {
                    pushStyle(SpanStyle(color = activeColor))
                    append(word.text.substring(0, highlightCount))
                    pop()
                }
                
                if (highlightCount < charCount) {
                    pushStyle(SpanStyle(color = inactiveColor))
                    append(word.text.substring(highlightCount))
                    pop()
                }
            }
        }
    }
}

private fun LyricLine.toWordByWordAnnotated(
    currentMs: Long,
    activeColor: Color,
    inactiveColor: Color,
    wordAdvanceMs: Long = 0L
): AnnotatedString {
    if (words.isEmpty()) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = activeColor)) { append(text) }
        }
    }

    return buildAnnotatedString {
        words.forEach { word ->
            val adjustedWordMs = currentMs + wordAdvanceMs
            val progress = when {
                adjustedWordMs < word.startMs -> 0f
                adjustedWordMs >= word.endMs -> 1f
                else -> {
                    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
                    val elapsed = (adjustedWordMs - word.startMs).coerceIn(0L, duration)
                    val linear = elapsed.toFloat() / duration.toFloat()
                    // TTML-like smoother pursuit: slightly accelerate early segment,
                    // while preserving overall timing end point.
                    (linear * 1.08f).coerceAtMost(1f)
                }
            }
            if (word.text.isEmpty()) return@forEach
            val highlightCount = (word.text.length * progress).roundToInt().coerceIn(0, word.text.length)
            if (highlightCount > 0) {
                withStyle(SpanStyle(color = activeColor)) {
                    append(word.text.substring(0, highlightCount))
                }
            }
            if (highlightCount < word.text.length) {
                withStyle(SpanStyle(color = inactiveColor)) {
                    append(word.text.substring(highlightCount))
                }
            }
        }
    }
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

