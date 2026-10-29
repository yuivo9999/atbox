package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuivo9999.mybox123.ui.theme.*

@Composable
fun NativeAboutScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MyBoxGold.copy(alpha = 0.15f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = MyBoxGold,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("MyBox 影音", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MyBoxTextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("版本 1.0.0 (Native Release)", fontSize = 13.sp, color = MyBoxTextSecondary)

        Spacer(modifier = Modifier.height(32.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MyBoxSurfaceRaised,
            border = androidx.compose.foundation.BorderStroke(1.dp, MyBoxBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("架构特性：", fontWeight = FontWeight.Bold, color = MyBoxGold, fontSize = 14.sp)
                Text("• 纯原生 Kotlin + Jetpack Compose 打造", color = MyBoxTextSecondary, fontSize = 13.sp)
                Text("• 原生 Media3 ExoPlayer 支持 4K H.265 / HLS", color = MyBoxTextSecondary, fontSize = 13.sp)
                Text("• 适配 Android 10 到 Android 15 现代系统", color = MyBoxTextSecondary, fontSize = 13.sp)
                Text("• 全面兼容 TVBox 多仓源及 M3U 电视直播格式", color = MyBoxTextSecondary, fontSize = 13.sp)
            }
        }
    }
}
