package com.example.aibuddy.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.aibuddy.AppDestinations
import com.example.aibuddy.R
import com.example.aibuddy.data.local.Conversation
import com.example.aibuddy.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    navController: NavController
) {
    val userFacts by homeViewModel.userFacts.collectAsState()
    val recentConversations by homeViewModel.recentConversations.collectAsState()
    val userName = userFacts.find { it.key == "name" }?.value ?: "User"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AiBuddy") },
                actions = {
                    IconButton(onClick = { /* TODO: Navigate to profile screen */ }) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Welcome back, $userName!",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Ready for a new conversation?",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        navController.navigate(AppDestinations.CONNECTED_AI_ROUTE)
                    },
                    modifier = Modifier.size(120.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Start Conversation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    navController.navigate(AppDestinations.CONNECTED_AI_ROUTE)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect to AiBuddy")
            }
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Conversations",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = { /* TODO: Navigate to all conversations screen */ }) {
                    Text("See all")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(recentConversations) { conversation ->
                    ConversationItem(conversation = conversation)
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(conversation: Conversation) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(conversation.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${conversation.durationInMinutes} min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Divider()
    }
}

@Composable
private fun BottomNavigationBar(navController: NavController) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = true,
            onClick = { /* Do nothing */ }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(id = R.drawable.ic_chat), contentDescription = "Chats") },
            label = { Text("Chats") },
            selected = false,
            onClick = { /* TODO: Navigate to chats screen */ }
        )
        NavigationBarItem(
            icon = { Icon(painterResource(id = R.drawable.ic_context), contentDescription = "Context") },
            label = { Text("Context") },
            selected = false,
            onClick = { navController.navigate(AppDestinations.CONTEXT_MANAGEMENT_ROUTE) }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = false,
            onClick = { /* TODO: Navigate to settings screen */ }
        )
    }
}
