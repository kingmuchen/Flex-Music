package com.kingmc.flexmusic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = FlexPrimary,
    secondary = FlexSecondary,
    background = FlexBackground,
    surface = FlexSurface
)

private val DarkScheme = darkColorScheme(
    primary = FlexPrimary,
    secondary = FlexSecondary
)

@Composable
fun FlexMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = Typography,
        content = content
    )
}
