package com.kingmc.flexmusic.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsRoute(
    sdkInt: Int,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    onScanClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()

    SettingsScreen(
        sdkInt = sdkInt,
        hasAudioPermission = hasAudioPermission,
        hasNotificationPermission = hasNotificationPermission,
        uiState = uiState,
        appSettings = appSettings,
        onScanClick = {
            onScanClick()
            viewModel.refreshLibrary()
        },
        onSmartLyricsMatchChange = viewModel::updateSmartLyricsMatch,
        onAutoPlayChange = viewModel::updateAutoPlay,
        onShowNotificationChange = viewModel::updateShowNotification,
        onRememberProgressChange = viewModel::updateRememberProgress,
        onClearMessage = viewModel::clearScanMessage
    )
}

@Composable
private fun SettingsScreen(
    sdkInt: Int,
    hasAudioPermission: Boolean,
    hasNotificationPermission: Boolean,
    uiState: SettingsUiState,
    appSettings: AppSettings,
    onScanClick: () -> Unit,
    onSmartLyricsMatchChange: (Boolean) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit,
    onShowNotificationChange: (Boolean) -> Unit,
    onRememberProgressChange: (Boolean) -> Unit,
    onClearMessage: () -> Unit
) {
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.scanMessage) {
        if (uiState.scanMessage != null) {
            kotlinx.coroutines.delay(3000)
            onClearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF1F2937),
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "播放设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingSwitchRow(
                        title = "自动播放",
                        subtitle = "打开应用时自动继续播放",
                        checked = appSettings.autoPlay,
                        onCheckedChange = onAutoPlayChange
                    )
                    SettingSwitchRow(
                        title = "记住播放进度",
                        subtitle = "退出后再次打开时继续上次进度",
                        checked = appSettings.rememberProgress,
                        onCheckedChange = onRememberProgressChange
                    )
                    SettingSwitchRow(
                        title = "显示通知控制",
                        checked = appSettings.showNotification,
                        onCheckedChange = onShowNotificationChange
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "音乐库",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingArrowRow(
                        title = "扫描本地音乐",
                        trailing = if (uiState.isLoading) {
                            "扫描中..."
                        } else {
                            "已扫描 ${uiState.songCount} 首"
                        },
                        showIcon = !uiState.isLoading,
                        onClick = onScanClick
                    )
                    if (uiState.scanMessage != null) {
                        Text(
                            text = uiState.scanMessage!!,
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "歌词设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingSwitchRow(
                        title = "智能歌词匹配",
                        subtitle = "自动匹配在线歌词",
                        checked = appSettings.smartLyricsMatch,
                        onCheckedChange = onSmartLyricsMatchChange
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "应用信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF6B7280),
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingArrowRow(
                        title = "关于Flex Music",
                        trailing = "v${uiState.appVersion}",
                        onClick = { showAboutDialog = true }
                    )
                    SettingArrowRow(
                        title = "系统信息",
                        trailing = "Android $sdkInt",
                        onClick = { }
                    )
                    SettingArrowRow(
                        title = "权限状态",
                        trailing = if (hasAudioPermission && hasNotificationPermission) "正常" else "需授权",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "Flex Music v${uiState.appVersion}",
            color = Color(0xFF9CA3AF),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        )
    }

    if (showAboutDialog) {
        AboutDialog(
            version = uiState.appVersion,
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
private fun SettingArrowRow(
    title: String,
    trailing: String,
    subtitle: String? = null,
    showIcon: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color(0xFF1F2937),
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                trailing,
                color = Color(0xFF10B981),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 4.dp)
            )
            if (showIcon) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFD1D5DB),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color(0xFF1F2937),
                style = MaterialTheme.typography.bodyLarge
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = Color(0xFF9CA3AF),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF10B981),
                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun AboutDialog(
    version: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                "关于 Flex Music",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "开发技术：Kotlin 原生 Android 开发",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF374151)
                )
                Text(
                    "产品定位：轻量化无广告本地音乐播放器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF374151)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "核心特点",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937)
                )
                val features = listOf(
                    "纯本地播放，无需联网",
                    "极简UI设计，操作流畅",
                    "支持在线歌词匹配与封面获取",
                    "支持歌词偏移调节与音频节奏分析",
                    "支持播放进度记忆与自动续播",
                    "支持通知栏控制与后台播放",
                    "极低内存占用，适配主流安卓版本"
                )
                features.forEach { feature ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("•", color = Color(0xFF10B981), style = MaterialTheme.typography.bodySmall)
                        Text(feature, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5563))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "开源寄语",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937)
                )
                Text(
                    "本项目为个人开源练习作品，代码完全公开，可供安卓初学者学习参考，也欢迎大家参与功能迭代与Bug修复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4B5563)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "隐私承诺",
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1F2937)
                )
                Text(
                    "应用仅申请存储、音频播放必要权限，不会收集、上传任何用户个人信息与本地音乐文件，全程离线使用，隐私安全无忧。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4B5563)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "作者：King沐宸    版本：v$version",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定", color = Color(0xFF10B981))
            }
        }
    )
}
