package com.example.aibuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.aibuddy.ui.screens.HomeScreen
import com.example.aibuddy.ui.screens.SplashScreen
import com.example.aibuddy.ui.theme.AiBuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiBuddyTheme {
                var showSplashScreen by remember { mutableStateOf(true) }

                if (showSplashScreen) {
                    SplashScreen {
                        showSplashScreen = false
                    }
                } else {
                    HomeScreen()
                }
            }
        }
    }
}
