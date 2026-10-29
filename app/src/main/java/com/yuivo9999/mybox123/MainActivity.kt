package com.yuivo9999.mybox123

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.yuivo9999.mybox123.ui.navigation.MyBoxApp
import com.yuivo9999.mybox123.ui.theme.MyBoxTheme

/**
 * Native Android entry point supporting Android 10 (API 29) through Android 15 (API 35+).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MyBoxTheme {
                MyBoxApp(onExit = ::finish)
            }
        }
    }
}
