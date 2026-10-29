package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.source.NativeContentSource
import com.yuivo9999.mybox123.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NativeSourceManagementScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyBoxApplication
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf<List<NativeContentSource>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            val list = withContext(Dispatchers.IO) {
                app.awaitServices().contentSourceRepository.getAll()
            }
            sources = list
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("内容源配置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MyBoxTextPrimary)
            IconButton(onClick = { refresh() }) {
                Icon(Icons.Default.Sync, contentDescription = "刷新", tint = MyBoxGold)
            }
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MyBoxGold)
            }
        } else if (sources.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无自定义内容源，系统默认使用内置高速精选源", color = MyBoxTextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(sources, key = { it.sourceId }) { src ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MyBoxSurfaceRaised,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(src.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MyBoxTextPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if (src.sourceType == "live") "直播源" else "影视源", fontSize = 12.sp, color = MyBoxGold)
                            }
                            Switch(
                                checked = src.enabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        val updated = sources.map { if (it.sourceId == src.sourceId) it.copy(enabled = enabled) else it }
                                        withContext(Dispatchers.IO) {
                                            app.awaitServices().contentSourceRepository.saveAll(updated)
                                        }
                                        sources = updated
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MyBoxBackground,
                                    checkedTrackColor = MyBoxGold,
                                    uncheckedThumbColor = MyBoxTextMuted,
                                    uncheckedTrackColor = MyBoxSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
