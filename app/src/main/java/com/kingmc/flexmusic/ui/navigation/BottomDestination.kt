package com.kingmc.flexmusic.ui.navigation

sealed class BottomDestination(
    val route: String,
    val label: String
) {
    data object Home : BottomDestination("home", "首页")
    data object Settings : BottomDestination("settings", "设置")

    companion object {
        val items = listOf(Home, Settings)
    }
}
