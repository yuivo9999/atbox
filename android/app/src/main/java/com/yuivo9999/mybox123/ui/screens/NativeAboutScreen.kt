package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuivo9999.mybox123.BuildConfig
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuivo9999.mybox123.ui.theme.AppAppearanceController
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

/** Native, informational About page. It intentionally makes no unsupported license or capability claims. */
@Composable
fun NativeAboutScreen(modifier: Modifier = Modifier) {
    val appearance by AppAppearanceController.appearance.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Text("关于 MyBox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("应用信息与当前原生化范围", style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)

        AboutInfoCard(
            title = "应用",
            body = "MyBox 是面向 Android 手机的影视目录、直播频道与个人内容管理应用。内容能否加载取决于已配置的数据源及其兼容能力。",
        )
        AboutInfoCard(
            title = "版本",
            body = "版本名称：${BuildConfig.VERSION_NAME}\n版本代码：${BuildConfig.VERSION_CODE}",
        )
        AboutInfoCard(
            title = "架构",
            body = "应用界面正在迁移到 Kotlin 与 Jetpack Compose。内容源、用户数据、影视目录和直播频道浏览由原生代码承载；播放器及部分旧版功能仍处于后续迁移范围。",
        )
        AboutInfoCard(
            title = "版权与第三方组件",
            body = "项目代码及第三方组件的版权、开源许可和使用条件，以仓库内对应的许可证文件与依赖声明为准。本页面不是完整的第三方依赖许可清单。",
        )
        AboutInfoCard(
            title = "功能状态说明",
            body = "页面入口已迁移不代表所有数据源、扩展或播放能力均已兼容。尚未完成或未经运行时验证的能力，不应视为已通过设备验收。",
        )
    }
}

@Composable
private fun AboutInfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MyBoxTextSecondary)
        }
    }
}
