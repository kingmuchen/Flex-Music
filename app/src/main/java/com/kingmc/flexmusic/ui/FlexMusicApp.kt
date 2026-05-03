package com.kingmc.flexmusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kingmc.flexmusic.feature.player.FullPlayerOverlay
import com.kingmc.flexmusic.feature.player.FullPlayerViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kingmc.flexmusic.feature.home.HomeRoute
import com.kingmc.flexmusic.feature.home.HomeViewModel
import com.kingmc.flexmusic.feature.settings.SettingsRoute
import com.kingmc.flexmusic.ui.navigation.BottomDestination
import com.kingmc.flexmusic.ui.components.MiniPlayerBar
import com.kingmc.flexmusic.ui.components.MusicBottomBar

@Composable
fun FlexMusicApp(
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    sdkInt: Int,
    onRequestPermission: () -> Unit
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val fullPlayerViewModel: FullPlayerViewModel = hiltViewModel()
    val playbackState by homeViewModel.playbackState.collectAsStateWithLifecycle()
    val fullPlayerUiState by fullPlayerViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: BottomDestination.Home.route
    var showFullPlayer by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (!showFullPlayer) {
                    MusicBottomBar(
                        destinations = BottomDestination.items,
                        currentRoute = currentRoute,
                        onNavigate = { destination ->
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentRoute,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxSize(),
                    label = "route_transition"
                ) {
                    FlexMusicNavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = navController,
                        hasAudioPermission = hasAudioPermission,
                        hasNotificationPermission = hasNotificationPermission,
                        sdkInt = sdkInt,
                        onRequestPermission = onRequestPermission,
                        onScanLibrary = homeViewModel::refreshLibrary
                    )
                }
                if (playbackState.currentSong != null && currentRoute == BottomDestination.Home.route && !showFullPlayer) {
                    MiniPlayerBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        playbackUiState = playbackState,
                        onOpenFullPlayer = { showFullPlayer = true },
                        onPlayPause = homeViewModel::playOrPause,
                        onSkipPrevious = homeViewModel::skipPrevious,
                        onSkipNext = homeViewModel::skipNext
                    )
                }
            }
        }

        if (showFullPlayer && playbackState.currentSong != null) {
            FullPlayerOverlay(
                playbackState = playbackState,
                uiState = fullPlayerUiState,
                onDismiss = { showFullPlayer = false },
                onPlayPause = fullPlayerViewModel::playOrPause,
                onSkipPrevious = fullPlayerViewModel::skipPrevious,
                onSkipNext = fullPlayerViewModel::skipNext,
                onSeekTo = fullPlayerViewModel::seekTo,
                onCyclePlaybackMode = fullPlayerViewModel::cyclePlaybackMode,
                onUpdateLyricsSettings = fullPlayerViewModel::updateLyricsSettings,
                onAdjustLyricOffset = fullPlayerViewModel::adjustLyricOffset,
                onResetLyricOffset = fullPlayerViewModel::resetLyricOffset,
                onAnalyzeAudio = fullPlayerViewModel::analyzeCurrentSong,
                onApplyAnalysisOffset = fullPlayerViewModel::applyAnalysisOffset,
                onClearAnalysisResult = fullPlayerViewModel::clearAnalysisResult
            )
        }
    }
}

@Composable
private fun FlexMusicNavHost(
    modifier: Modifier,
    navController: androidx.navigation.NavHostController,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    sdkInt: Int,
    onRequestPermission: () -> Unit,
    onScanLibrary: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = BottomDestination.Home.route,
        modifier = modifier
    ) {
        composable(BottomDestination.Home.route) {
            HomeRoute(
                hasAudioPermission = hasAudioPermission,
                onRequestPermission = onRequestPermission
            )
        }
        composable(BottomDestination.Settings.route) {
            SettingsRoute(
                sdkInt = sdkInt,
                hasAudioPermission = hasAudioPermission,
                hasNotificationPermission = hasNotificationPermission,
                onScanClick = onScanLibrary
            )
        }
    }
}
