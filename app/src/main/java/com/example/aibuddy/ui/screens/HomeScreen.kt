package com.example.aibuddy.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aibuddy.ui.AnimatedEyes
import com.example.aibuddy.viewmodel.HomeViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val isConnected by homeViewModel.isConnected.collectAsState()
    val aiBuddyResponse by homeViewModel.aiBuddyResponse.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val errorMessage by homeViewModel.errorMessage.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasAudioPermission = isGranted
        if (!isGranted) {
            // Handle permission denial, e.g., show a message to the user
            // For now, we'll just update the state.
            // You might want to show a Snackbar or dialog.
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val spokenText: ArrayList<String>? =
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!spokenText.isNullOrEmpty()) {
                // Update ViewModel with spoken text and send it
                homeViewModel.updateInputText(spokenText[0])
                homeViewModel.sendMessage()
            }
        }
    }

    fun launchSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag()) // Reverted to US English
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...") // Reverted prompt
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            // Handle exception if speech recognizer is not available
            // For example, show a Toast or update errorMessage in ViewModel
            homeViewModel.updateInputText("") // Clear any stale input
            // homeViewModel.setErrorMessage("Speech recognizer not available: ${e.localizedMessage}")
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween, // Changed for better layout
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedEyes()

                Button(
                    onClick = { homeViewModel.toggleConnection() },
                    enabled = !isLoading
                ) {
                    Text(text = if (isConnected) "Disconnect" else "Connect")
                }

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (aiBuddyResponse.isNotEmpty()) {
                    Text(
                        text = "AI Buddy: $aiBuddyResponse",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Voice input and control buttons at the bottom
            if (isConnected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = {
                            if (hasAudioPermission) {
                                launchSpeechRecognizer()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp), // Adjusted padding
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = "Speak")
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(text = if (hasAudioPermission) "Tap to Speak" else "Request Mic Permission")
                    }

                    Button(
                        onClick = { homeViewModel.stopSpeaking() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp), // Adjusted padding
                        enabled = !isLoading // Can be pressed even if AI is not currently speaking to stop queued speech
                    ) {
                        // Consider an icon like Icons.Filled.Stop or similar if you add material-icons-extended
                        Text(text = "Stop Talking")
                    }
                }
            }
        }
    }
}
