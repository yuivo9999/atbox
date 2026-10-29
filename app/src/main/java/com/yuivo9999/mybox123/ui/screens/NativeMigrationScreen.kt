package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuivo9999.mybox123.app.AppDestination
import com.yuivo9999.mybox123.ui.theme.MyBoxBackground
import com.yuivo9999.mybox123.ui.theme.MyBoxGold
import com.yuivo9999.mybox123.ui.theme.MyBoxTextSecondary

@Composable
fun NativeMigrationScreen(
    destination: AppDestination,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MyBoxBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(destination.label, fontSize = 20.sp, color = MyBoxGold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(destination.description, fontSize = 14.sp, color = MyBoxTextSecondary)
        }
    }
}
