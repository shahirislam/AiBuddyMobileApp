package com.example.aibuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aibuddy.ui.screens.HomeScreen
import com.example.aibuddy.ui.screens.SplashScreen
import com.example.aibuddy.ui.screens.ConnectedAiScreen // Uncommented and will be used
import com.example.aibuddy.ui.theme.AiBuddyTheme

object AppDestinations {
    const val SPLASH_ROUTE = "splash"
    const val HOME_ROUTE = "home"
    const val CONNECTED_AI_ROUTE = "connected_ai"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiBuddyTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = AppDestinations.SPLASH_ROUTE
                ) {
                    composable(AppDestinations.SPLASH_ROUTE) {
                        SplashScreen {
                            navController.navigate(AppDestinations.HOME_ROUTE) {
                                popUpTo(AppDestinations.SPLASH_ROUTE) { inclusive = true }
                            }
                        }
                    }
                    composable(AppDestinations.HOME_ROUTE) {
                        HomeScreen(
                            onConnectClicked = {
                                navController.navigate(AppDestinations.CONNECTED_AI_ROUTE)
                            }
                        )
                    }
                    composable(AppDestinations.CONNECTED_AI_ROUTE) {
                        ConnectedAiScreen(navController = navController)
                    }
                }
            }
        }
    }
}
