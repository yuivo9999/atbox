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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.ui.theme.*

@Composable
fun NativeRepositoryManagementScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyBoxApplication
    val vm: RepositoryManagementViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                RepositoryManagementViewModel(app) as T
        }
    )
    val state by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.load()
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
            Text("多仓订阅配置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MyBoxTextPrimary)
            IconButton(onClick = { vm.load(force = true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MyBoxGold)
            }
        }

        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MyBoxGold)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.repositories, key = { it.id }) { repo ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MyBoxSurfaceRaised,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(repo.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MyBoxTextPrimary)
                                Switch(
                                    checked = repo.enabled,
                                    onCheckedChange = { vm.toggle(repo, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MyBoxBackground,
                                        checkedTrackColor = MyBoxGold,
                                        uncheckedThumbColor = MyBoxTextMuted,
                                        uncheckedTrackColor = MyBoxSurface
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(repo.primaryUrl, fontSize = 12.sp, color = MyBoxTextSecondary, maxLines = 1)
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { vm.syncPreview(repo) },
                                    enabled = repo.enabled && state.syncingRepositoryId == null
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MyBoxGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("同步解析", color = MyBoxGold, fontSize = 12.sp)
                                }
                                IconButton(onClick = { vm.remove(repo) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = MyBoxTextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
