package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuivo9999.mybox123.app.AppDestination
import com.yuivo9999.mybox123.ui.theme.MyBoxSurfaceRaised
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

/** Temporary native destination used while business screens are migrated in later stages. */
@Composable
fun NativeMigrationScreen(destination: AppDestination, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (destination == AppDestination.HOME) "MyBox" else destination.label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = destination.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MyBoxTextSecondary,
            lineHeight = 22.sp,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = "原生 Android 基础已启用",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "应用入口、主题和主导航已切换到原生界面。当前阶段只建立重构基础，现有业务页面与数据能力会按计划逐项迁移；此临时页面不代表最终产品界面。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MyBoxTextSecondary,
                    lineHeight = 22.sp,
                )
            }
        }

        if (destination == AppDestination.HOME) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MyBoxSurfaceRaised),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Construction,
                        contentDescription = null,
                        tint = MyBoxTextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = "本阶段交付范围",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "原生 Activity、Compose 主题、主导航状态和返回行为的基础入口。直播、影视、源管理、收藏及设置将在后续阶段迁移。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MyBoxTextSecondary,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
}
