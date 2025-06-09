package com.example.aibuddy.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.aibuddy.viewmodel.HomeViewModel
import com.example.aibuddy.data.local.UserFact
import com.example.aibuddy.data.local.ConversationTopic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextManagementScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val userFacts by homeViewModel.userFacts.collectAsState()
    val conversationTopics by homeViewModel.conversationTopics.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Context") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            item {
                Text("User Facts", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(userFacts) { fact ->
                ContextItem(
                    item = fact,
                    onDelete = { homeViewModel.deleteUserFact(fact.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Conversation Topics", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(conversationTopics) { topic ->
                ContextItem(
                    item = topic,
                    onDelete = { homeViewModel.deleteConversationTopic(topic.id) }
                )
            }
        }
    }
}

@Composable
private fun ContextItem(item: Any, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            when (item) {
                is UserFact -> {
                    Column {
                        Text(text = "Key: ${item.key}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Value: ${item.value}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ConversationTopic -> {
                    Column {
                        Text(text = "Topic: ${item.topic}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "Keywords: ${item.keywords}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
