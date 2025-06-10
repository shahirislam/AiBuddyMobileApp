package com.example.aibuddy

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aibuddy.ui.screens.HomeScreen
import com.example.aibuddy.ui.screens.OnboardingScreen
import com.example.aibuddy.ui.screens.SplashScreen
import com.example.aibuddy.ui.screens.ConnectedAiScreen // Uncommented and will be used
import com.example.aibuddy.ui.theme.AiBuddyTheme

object AppDestinations {
    const val SPLASH_ROUTE = "splash"
    const val ONBOARDING_ROUTE = "onboarding"
    const val HOME_ROUTE = "home"
    const val CONNECTED_AI_ROUTE = "connected_ai"
    const val CONTEXT_MANAGEMENT_ROUTE = "context_management"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiBuddyTheme {
                val navController = rememberNavController()
                val sharedPreferences = getSharedPreferences("aibuddy_prefs", Context.MODE_PRIVATE)
                val isOnboardingComplete = sharedPreferences.getBoolean("onboarding_complete", false)
                val startDestination = if (isOnboardingComplete) AppDestinations.HOME_ROUTE else AppDestinations.ONBOARDING_ROUTE

                NavHost(
                    navController = navController,
                    startDestination = startDestination
                ) {
                    composable(AppDestinations.ONBOARDING_ROUTE) {
                        OnboardingScreen(navController = navController)
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
                    composable(AppDestinations.CONTEXT_MANAGEMENT_ROUTE) {
                        com.example.aibuddy.ui.screens.ContextManagementScreen(navController = navController)
                    }
                }
            }
        }
    }
}
