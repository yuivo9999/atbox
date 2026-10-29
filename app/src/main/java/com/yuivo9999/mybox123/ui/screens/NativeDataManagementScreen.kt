package com.yuivo9999.mybox123.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NativeDataManagementScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyBoxApplication
    val scope = rememberCoroutineScope()

    fun clearData(actionName: String, action: suspend () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { action() }
            Toast.makeText(context, "$actionName 已清空", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("数据与存储维护", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MyBoxTextPrimary)
        Spacer(modifier = Modifier.height(4.dp))

        DataActionCard(
            title = "清除播放历史",
            description = "清除所有已记录的剧集播放进度与最近播放历史",
            buttonLabel = "清除历史",
            onAction = {
                clearData("播放历史") {
                    app.awaitServices().userDataRepository.saveHistoryRecords(emptyList())
                    app.awaitServices().userDataRepository.saveProgressRecords(emptyList())
                }
            }
        )

        DataActionCard(
            title = "清除收藏列表",
            description = "清除所有收藏的电影、电视剧与直播频道",
            buttonLabel = "清除收藏",
            onAction = {
                clearData("收藏列表") {
                    app.awaitServices().userDataRepository.saveFavoriteRecords(emptyList())
                }
            }
        )

        DataActionCard(
            title = "清除搜索历史",
            description = "重置搜索栏快捷推荐与热搜词记录",
            buttonLabel = "清除搜索",
            onAction = {
                clearData("搜索历史") {
                    app.awaitServices().userDataRepository.saveSearchRecords(emptyList())
                }
            }
        )
    }
}

@Composable
private fun DataActionCard(
    title: String,
    description: String,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MyBoxSurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MyBoxTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(description, fontSize = 12.sp, color = MyBoxTextSecondary)
            }
            OutlinedButton(
                onClick = onAction,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MyBoxGold),
                border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxGold)
            ) {
                Text(buttonLabel, fontSize = 12.sp)
            }
        }
    }
}
