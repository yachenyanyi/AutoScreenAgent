package com.example.autoscreenagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autoscreenagent.ui.screens.ChatScreen
import com.example.autoscreenagent.ui.screens.DebugMenuScreen
import com.example.autoscreenagent.ui.screens.SettingsScreen
import com.example.autoscreenagent.ui.theme.MyApplicationTheme
import com.example.autoscreenagent.ui.viewmodel.AppViewModel
import com.example.autoscreenagent.util.NotificationHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化通知渠道
        NotificationHelper.createNotificationChannel(this)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val appViewModel: AppViewModel = viewModel()
                    MainScreen(appViewModel = appViewModel)
                }
            }
        }
    }
}

/**
 * 主屏幕路由
 */
@Composable
fun MainScreen(appViewModel: AppViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }

    when (val screen = currentScreen) {
        is Screen.Chat -> {
            ChatScreen(
                appViewModel = appViewModel,
                onNavigateToSettings = { currentScreen = Screen.Settings },
                onOpenDebugMenu = { currentScreen = Screen.Debug }
            )
        }
        is Screen.Settings -> {
            val config by appViewModel.config.collectAsState()
            SettingsScreen(
                config = config,
                onConfigChanged = { newConfig ->
                    appViewModel.saveConfig(newConfig)
                },
                onBack = { currentScreen = Screen.Chat }
            )
        }
        is Screen.Debug -> {
            DebugMenuScreen(
                appViewModel = appViewModel,
                onBack = { currentScreen = Screen.Chat }
            )
        }
    }
}

sealed class Screen {
    data object Chat : Screen()
    data object Settings : Screen()
    data object Debug : Screen()
}