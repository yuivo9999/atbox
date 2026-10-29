package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.storage.FavoriteRecord
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.LocalAppAppearance
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeFavoritesScreen(
    modifier: Modifier = Modifier,
    onOpenFavorite: (FavoriteRecord) -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as MyBoxApplication
    val factory = remember(application) { favoritesViewModelFactory(application) }
    val favoritesViewModel: FavoritesViewModel = viewModel(factory = factory)
    val state by favoritesViewModel.uiState.collectAsStateWithLifecycle()
    val appearance = LocalAppAppearance.current
    LaunchedEffect(favoritesViewModel) { favoritesViewModel.load() }

    val presentations = remember(state.favorites) { state.favorites.map(FavoriteRecord::toPresentation) }
    val movieCount = presentations.count { !it.isLive }
    val liveCount = presentations.count { it.isLive }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("我的收藏", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("影视 $movieCount · 直播频道 $liveCount", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
            }
            OutlinedButton(onClick = { favoritesViewModel.load(force = true) }, enabled = !state.loading && state.mutatingFavoriteId == null) {
                Text("刷新")
            }
        }
        Text(
            "点击“打开”返回对应的原生影视详情或直播频道浏览；缺少定位信息时会提示原因。此操作不会启动播放。",
            style = MaterialTheme.typography.bodySmall,
            color = MyBoxTextSecondary,
        )
        state.message?.let { FavoriteStatusCard(it) }
        state.error?.let { error ->
            FavoriteStatusCard("操作失败：${friendlyFavoriteError(error)}")
            Button(onClick = { favoritesViewModel.load(force = true) }, enabled = !state.loading) { Text("重试") }
        }
        when {
            state.loading && state.favorites.isEmpty() -> {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text("正在读取收藏…", color = MyBoxTextSecondary)
                }
            }
            state.loaded && state.favorites.isEmpty() -> FavoriteStatusCard("暂无收藏。浏览内容时添加的收藏记录会显示在这里。")
            else -> {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp)) {
                    items(presentations, key = { it.record.favoriteId }) { favorite ->
                        FavoriteCard(
                            title = favorite.title,
                            subtitle = favorite.subtitle,
                            live = favorite.isLive,
                            busy = state.mutatingFavoriteId == favorite.record.favoriteId,
                            compact = appearance.compact,
                            listStyle = appearance.listCards,
                            onOpen = { onOpenFavorite(favorite.record) },
                            onRemove = { favoritesViewModel.remove(favorite.record) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(title: String, subtitle: String, live: Boolean, busy: Boolean, compact: Boolean, listStyle: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Card(shape = RoundedCornerShape(if (listStyle) 8.dp else 18.dp), colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(if (compact) 10.dp else 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MyBoxTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (live) "直播收藏" else "影视收藏", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onOpen, enabled = !busy) { Text("打开") }
                OutlinedButton(onClick = onRemove, enabled = !busy) { Text(if (busy) "处理中…" else "移除") }
            }
        }
    }
}

@Composable
private fun FavoriteStatusCard(message: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MyBoxSurfaceRaised, modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun friendlyFavoriteError(raw: String): String = when {
    raw.contains("NATIVE_SERVICES_UNAVAILABLE") -> "原生服务初始化失败，请稍后重试。"
    raw.contains("USER_DATA_RECORD_INVALID") -> "收藏数据中存在无法识别的记录；原始数据未被自动清空。"
    else -> raw.take(180)
}

private fun favoritesViewModelFactory(application: MyBoxApplication): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FavoritesViewModel::class.java)) return FavoritesViewModel(application) as T
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
