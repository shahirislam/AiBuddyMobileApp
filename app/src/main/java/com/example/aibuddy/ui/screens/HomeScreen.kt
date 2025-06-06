package com.example.aibuddy.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
    var isListeningUserIntent by remember { mutableStateOf(false) } // User's intent to be in listening loop
    var isActuallyRecognizing by remember { mutableStateOf(false) } // SpeechRecognizer active state
    var listeningStatusText by remember { mutableStateOf("Tap Mic to Start Listening") }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    var localPermissionLauncher by remember { mutableStateOf<ActivityResultLauncher<String>?>(null) }

    fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
    }
    
    fun startListeningInternal() {
        if (!isConnected || !isListeningUserIntent) { 
            isActuallyRecognizing = false
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listeningStatusText = "Speech recognition not available."
            Log.e("STT", "Speech recognition not available.")
            isListeningUserIntent = false; isActuallyRecognizing = false
            return
        }
        if (hasAudioPermission) {
            try {
                if (!isActuallyRecognizing) { 
                    isActuallyRecognizing = true
                    speechRecognizer.startListening(createSpeechIntent())
                    Log.d("STT", "startListeningInternal called, STT started.")
                }
            } catch (e: Exception) {
                Log.e("STT", "Exception starting listening: ${e.message}")
                listeningStatusText = "Error starting listener."
                isActuallyRecognizing = false
                isListeningUserIntent = false
            }
        } else {
            listeningStatusText = "Requesting permission..."
            localPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val permissionLauncherInstance = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasAudioPermission = isGranted
        if (isGranted) {
            listeningStatusText = "Permission granted. Tap Mic to Start."
            if (isListeningUserIntent) { 
                startListeningInternal()
            }
        } else {
            listeningStatusText = "Audio permission denied."
            isListeningUserIntent = false
            isActuallyRecognizing = false
        }
    }
    LaunchedEffect(permissionLauncherInstance) {
        localPermissionLauncher = permissionLauncherInstance
    }

    DisposableEffect(key1 = speechRecognizer) {
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listeningStatusText = "Listening..."
                Log.d("STT", "onReadyForSpeech. User intent to listen: $isListeningUserIntent")
            }
            override fun onBeginningOfSpeech() {
                listeningStatusText = "Hearing speech..."
                Log.d("STT", "onBeginningOfSpeech")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                listeningStatusText = "Processing..."
                isActuallyRecognizing = false 
                Log.d("STT", "onEndOfSpeech. User intent to listen: $isListeningUserIntent")
            }
            override fun onError(error: Int) {
                isActuallyRecognizing = false 
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { hasAudioPermission = false; "Permissions error" }
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                Log.e("STT", "Error: $errorMsg ($error). User intent to listen: $isListeningUserIntent")

                if (isListeningUserIntent) { // If user *intends* to be in continuous listening mode
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        speechRecognizer.cancel() // Attempt to cancel immediately
                        listeningStatusText = "Didn't catch that. Listening again..."
                        Log.d("STT", "onError (NO_MATCH/TIMEOUT) - cancelling and restarting listening in continuous mode.")
                        coroutineScope.launch {
                            delay(500) // Brief delay before retrying
                            if(isListeningUserIntent) startListeningInternal() // Re-check intent
                        }
                    } else if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        speechRecognizer.cancel() // Attempt to cancel immediately
                        listeningStatusText = "Listener error, retrying..."
                        Log.d("STT", "onError (CLIENT/BUSY) - cancelling and restarting listening in continuous mode.")
                        coroutineScope.launch {
                            delay(500) // Brief delay before retrying
                            if(isListeningUserIntent) startListeningInternal() // Re-check intent
                        }
                    } else {
                        // For other, more severe errors, stop the continuous loop
                        listeningStatusText = "Error: $errorMsg. Listening stopped."
                        isListeningUserIntent = false
                    }
                } else {
                    // If not in continuous listening mode (e.g., user manually stopped), just report error.
                    listeningStatusText = "Error: $errorMsg."
                }
            }
            override fun onResults(results: Bundle?) {
                isActuallyRecognizing = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                    Log.d("STT", "onResults: ${matches[0]}. User intent to listen: $isListeningUserIntent")
                    homeViewModel.updateInputText(matches[0])
                    homeViewModel.sendMessage() 
                    listeningStatusText = "Message sent. AI responding..." 
                } else {
                    Log.d("STT", "onResults: No suitable match found. User intent to listen: $isListeningUserIntent")
                    listeningStatusText = "Didn't catch that clearly."
                    // If continuously listening and got no results (but not an error handled by onError), retry.
                    if (isListeningUserIntent) { 
                        Log.d("STT", "onResults (no match) - restarting listening for continuous mode.")
                        coroutineScope.launch { delay(500); if(isListeningUserIntent) startListeningInternal() }
                    } else {
                        listeningStatusText += " Tap Mic to speak again."
                    }
                }
                // Auto-re-listening after AI response is handled by LaunchedEffect(aiBuddyResponse)
            }
            override fun onPartialResults(partialResults: Bundle?) { /* ... */ }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose { speechRecognizer.destroy(); Log.d("STT", "SpeechRecognizer destroyed") }
    }

    LaunchedEffect(aiBuddyResponse, isConnected) {
        if (isConnected && isListeningUserIntent && aiBuddyResponse.isNotBlank() && !isLoading) {
            if (isActuallyRecognizing) {
                speechRecognizer.cancel() 
                isActuallyRecognizing = false
                Log.d("STT", "Cancelled STT because AI is about to respond.")
            }
            val estimatedTtsDuration = (aiBuddyResponse.length * 150L).coerceIn(2000L, 10000L)
            Log.d("STT", "AI responded. Waiting approx ${estimatedTtsDuration}ms for TTS then re-listening.")
            listeningStatusText = "AI speaking... then will listen." 
            delay(estimatedTtsDuration)
            if (isConnected && isListeningUserIntent) { 
                Log.d("STT", "Restarting listening after AI response and delay.")
                startListeningInternal()
            } else {
                Log.d("STT", "Conditions for re-listening changed during delay. Not restarting STT.")
                if (!isListeningUserIntent) listeningStatusText = "Listening stopped. Tap Mic to Start."
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column( 
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedEyes()
                Button(
                    onClick = { homeViewModel.toggleConnection() },
                    enabled = !isLoading && !isActuallyRecognizing
                ) {
                    Text(text = if (isConnected) "Disconnect" else "Connect")
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth()) }
                if (aiBuddyResponse.isNotEmpty()) {
                    Text(text = "AI Buddy: $aiBuddyResponse", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                }
            }

            if (isConnected) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = listeningStatusText, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
                    Button( 
                        onClick = {
                            if (isListeningUserIntent) { 
                                isListeningUserIntent = false
                                if(isActuallyRecognizing) speechRecognizer.stopListening() 
                                listeningStatusText = "Listening stopped. Tap Mic to Start."
                            } else { 
                                if (hasAudioPermission) {
                                    isListeningUserIntent = true 
                                    startListeningInternal()
                                } else {
                                    localPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        enabled = !isLoading 
                    ) {
                        Icon(
                            imageVector = if (isListeningUserIntent) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = if (isListeningUserIntent) "Stop Listening Loop" else "Start Listening Loop"
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(text = if (isListeningUserIntent) "Stop Listening" else if (hasAudioPermission) "Start Listening" else "Request Mic Permission")
                    }
                    if (!hasAudioPermission && !isListeningUserIntent) { 
                         Button(onClick = { localPermissionLauncher?.launch(Manifest.permission.RECORD_AUDIO) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                             Text("Grant Microphone Permission")
                         }
                    }
                    Button(
                        onClick = { homeViewModel.stopSpeaking() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        enabled = !isLoading
                    ) {
                        Text(text = "Stop Talking (TTS)")
                    }
                }
            }
        }
    }
}
