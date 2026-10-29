package com.yuivo9999.mybox123

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.yuivo9999.mybox123.ui.navigation.MyBoxApp
import com.yuivo9999.mybox123.ui.theme.MyBoxTheme

/**
 * Native Android entry point.
 *
 * This Activity deliberately does not extend Capacitor's BridgeActivity and does not
 * load HTML, create a WebView, or initialize a JavaScript UI bridge. Existing legacy
 * bridge classes are retained only as migration references until their owning features
 * are moved to native services in later refactor stages.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.rgb(7, 8, 11)
        window.navigationBarColor = android.graphics.Color.rgb(7, 8, 11)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false

        setContent {
            MyBoxTheme {
                MyBoxApp(onExit = ::finish)
            }
        }
    }
}
