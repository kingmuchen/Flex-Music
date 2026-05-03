package com.kingmc.flexmusic.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

data class LyricsSettings(
    val fontSize: TextUnit = 20.sp,
    val activeLineColor: Color = Color(0xFF10B981),
    val enableWordByWord: Boolean = false  // 默认关闭逐字歌词
) {
    companion object {
        val predefinedColors = listOf(
            Color(0xFF10B981), // 绿色
            Color(0xFF3B82F6), // 蓝色
            Color(0xFFEF4444), // 红色
            Color(0xFFF59E0B), // 橙色
            Color(0xFF8B5CF6), // 紫色
            Color(0xFFEC4899), // 粉色
            Color(0xFF06B6D4), // 青色
            Color(0xFF111827)  // 黑色
        )
        
        val fontSizeOptions = listOf(
            16.sp to "小",
            18.sp to "标准",
            20.sp to "中",
            22.sp to "大",
            24.sp to "特大"
        )
    }
}
