package com.example.aibuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aibuddy.ui.AnimatedEyes // Can keep for visual consistency or remove
import com.example.aibuddy.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onConnectClicked: () -> Unit
) {
    val isConnected by homeViewModel.isConnected.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState() 

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to AiBuddy!",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Button(
                onClick = {
                    if (!isConnected) {
                        homeViewModel.toggleConnection()
                    }
                    onConnectClicked() 
                },
                modifier = Modifier.fillMaxWidth(0.7f),
                enabled = !isLoading
            ) {
                Text(text = if (isConnected) "Re-join AiBuddy" else "Connect to AiBuddy", style = MaterialTheme.typography.titleMedium)
            }
            if (isLoading && !isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
                Text("Connecting...", modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
