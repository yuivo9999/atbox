package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import org.json.JSONObject

@Composable
fun NativeSettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MyBoxApplication
    val scope = rememberCoroutineScope()

    var hwDecode by remember { mutableStateOf(true) }
    var autoPlayNext by remember { mutableStateOf(true) }
    var speedMemory by remember { mutableStateOf(true) }
    var darkObsidian by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val s = app.awaitServices().userDataRepository.getSettings()
            hwDecode = s.optBoolean("hwDecode", true)
            autoPlayNext = s.optBoolean("autoPlayNext", true)
            speedMemory = s.optBoolean("speedMemory", true)
            darkObsidian = s.optBoolean("darkObsidian", true)
        }
    }

    fun save(key: String, value: Boolean) {
        scope.launch(Dispatchers.IO) {
            val repo = app.awaitServices().userDataRepository
            val s = repo.getSettings()
            s.put(key, value)
            repo.saveSettings(s)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("播放与画质", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MyBoxGold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))

        SettingSwitchItem(
            title = "硬件加速解码 (MediaCodec)",
            description = "支持 4K H.265 / AV1 超清流畅原生解码",
            checked = hwDecode,
            onCheckedChange = { hwDecode = it; save("hwDecode", it) }
        )

        SettingSwitchItem(
            title = "自动播放下一集",
            description = "当前剧集播放完毕时自动连播下集",
            checked = autoPlayNext,
            onCheckedChange = { autoPlayNext = it; save("autoPlayNext", it) }
        )

        SettingSwitchItem(
            title = "记忆倍速设置",
            description = "跨剧集和视频记忆上一次播放速度",
            checked = speedMemory,
            onCheckedChange = { speedMemory = it; save("speedMemory", it) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("显示与外观", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MyBoxGold, modifier = Modifier.padding(vertical = 8.dp))

        SettingSwitchItem(
            title = "暗黑黑曜石主题",
            description = "为 OLED / 高对比度屏幕优化的原生深色系",
            checked = darkObsidian,
            onCheckedChange = { darkObsidian = it; save("darkObsidian", it) }
        )
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MyBoxSurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
