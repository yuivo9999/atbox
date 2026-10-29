package com.yuivo9999.mybox123.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.ui.theme.MyBoxAmber
import com.yuivo9999.mybox123.ui.theme.MyBoxBackground
import com.yuivo9999.mybox123.ui.theme.MyBoxBorder
import com.yuivo9999.mybox123.ui.theme.MyBoxGold
import com.yuivo9999.mybox123.ui.theme.MyBoxGoldLight
import com.yuivo9999.mybox123.ui.theme.MyBoxOutline
import com.yuivo9999.mybox123.ui.theme.MyBoxSurface
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextMuted
import com.yuivo9999.mybox123.ui.theme.MyBoxTextPrimary
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 我的 (Profile) 中心：严格对标前端 MyPage.jsx
 * 包含：用户卡片、播放历史、搜索历史、源管理、多仓管理、播放设置、数据管理、关于
 */
@Composable
fun NativeProfileHubScreen(
    selectedPageName: String,
    onSelectedPageNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val selectedPage = ProfileSubpage.entries.firstOrNull { it.name == selectedPageName } ?: ProfileSubpage.MAIN

    var sourceCount by remember { mutableStateOf(0) }
    var historyCount by remember { mutableStateOf(0) }
    var searchCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val services = application.awaitServices()
                sourceCount = services.contentSourceRepository.getAll().size
                historyCount = services.userDataRepository.getHistoryRecords().size
                searchCount = services.userDataRepository.getSearchRecords().size
            } catch (_: Exception) {}
        }
    }

    BackHandler(enabled = selectedPage != ProfileSubpage.MAIN) {
        onSelectedPageNameChange(ProfileSubpage.MAIN.name)
    }

    if (selectedPage != ProfileSubpage.MAIN) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MyBoxBackground)
                .statusBarsPadding()
        ) {
            // Subpage Top Bar with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { onSelectedPageNameChange(ProfileSubpage.MAIN.name) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MyBoxTextPrimary
                    )
                }
                Text(
                    text = subpageTitle(selectedPage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MyBoxTextPrimary
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedPage) {
                    ProfileSubpage.SOURCES -> NativeSourceManagementScreen()
                    ProfileSubpage.SETTINGS -> NativeSettingsScreen()
                    ProfileSubpage.REPOSITORIES -> NativeRepositoryManagementScreen()
                    ProfileSubpage.DATA -> NativeDataManagementScreen()
                    ProfileSubpage.ABOUT -> NativeAboutScreen()
                    ProfileSubpage.HISTORY -> NativePlaybackHistoryScreen()
                    ProfileSubpage.MAIN -> {}
                }
            }
        }
        return
    }

    // Main Profile Hub
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 84.dp)
    ) {
        // 1. Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = MyBoxGold,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "TVBOX 4K 影音",
                        style = MaterialTheme.typography.labelSmall,
                        color = MyBoxGold,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MyBoxTextPrimary
                )
            }
        }

        // 2. User Gold Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MyBoxSurfaceRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "T",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF101217)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "TVBox 用户",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MyBoxTextPrimary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MyBoxGold.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MyBoxGold)
                        }
                    }
                    Text(
                        text = "本地数据独立存储 · 4K影音尊享版",
                        style = MaterialTheme.typography.bodySmall,
                        color = MyBoxTextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Section: 常用记录
        Text(
            text = "记录与订阅",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MyBoxGold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MyBoxSurfaceRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column {
                SangtianMenuItem(
                    icon = Icons.Default.History,
                    title = "播放历史",
                    badge = if (historyCount > 0) "$historyCount 条" else null,
                    onClick = { onSelectedPageNameChange(ProfileSubpage.HISTORY.name) }
                )
                HorizontalDivider(color = MyBoxBorder, thickness = 0.5.dp)
                SangtianMenuItem(
                    icon = Icons.Default.Storage,
                    title = "源管理",
                    badge = if (sourceCount > 0) "$sourceCount 个源" else null,
                    onClick = { onSelectedPageNameChange(ProfileSubpage.SOURCES.name) }
                )
                HorizontalDivider(color = MyBoxBorder, thickness = 0.5.dp)
                SangtianMenuItem(
                    icon = Icons.Default.CloudDownload,
                    title = "多仓管理",
                    onClick = { onSelectedPageNameChange(ProfileSubpage.REPOSITORIES.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Section: 设置与系统
        Text(
            text = "系统与维护",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MyBoxGold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MyBoxSurfaceRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column {
                SangtianMenuItem(
                    icon = Icons.Default.Settings,
                    title = "播放与偏好设置",
                    onClick = { onSelectedPageNameChange(ProfileSubpage.SETTINGS.name) }
                )
                HorizontalDivider(color = MyBoxBorder, thickness = 0.5.dp)
                SangtianMenuItem(
                    icon = Icons.Default.CleaningServices,
                    title = "数据管理与清理",
                    onClick = { onSelectedPageNameChange(ProfileSubpage.DATA.name) }
                )
                HorizontalDivider(color = MyBoxBorder, thickness = 0.5.dp)
                SangtianMenuItem(
                    icon = Icons.Default.Info,
                    title = "关于本应用",
                    badge = "v0.3.0",
                    onClick = { onSelectedPageNameChange(ProfileSubpage.ABOUT.name) }
                )
            }
        }
    }
}

@Composable
private fun SangtianMenuItem(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MyBoxGold,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MyBoxTextPrimary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MyBoxSurface)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 11.sp,
                        color = MyBoxTextMuted
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MyBoxTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun subpageTitle(page: ProfileSubpage): String = when (page) {
    ProfileSubpage.SOURCES -> "源管理"
    ProfileSubpage.SETTINGS -> "播放与偏好设置"
    ProfileSubpage.REPOSITORIES -> "多仓管理"
    ProfileSubpage.DATA -> "数据管理"
    ProfileSubpage.ABOUT -> "关于"
    ProfileSubpage.HISTORY -> "播放历史"
    ProfileSubpage.MAIN -> "我的"
}

enum class ProfileSubpage { MAIN, SOURCES, SETTINGS, REPOSITORIES, DATA, ABOUT, HISTORY }
