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
    val aiBuddyResponse by homeViewModel.aiBuddyResponse.collectAsState() // Only used for display now
    val isLoading by homeViewModel.isLoading.collectAsState()
    val errorMessage by homeViewModel.errorMessage.collectAsState()
    val isTtsActuallySpeaking by homeViewModel.isTtsSpeaking.collectAsState()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isListeningUserIntent by remember { mutableStateOf(false) } 
    var isActuallyRecognizing by remember { mutableStateOf(false) } 
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
        if (!isConnected || !isListeningUserIntent || isTtsActuallySpeaking) { 
            // Do not start listening if not connected, user doesn't want to listen, or TTS is active
            if(isTtsActuallySpeaking) Log.d("STT", "Blocked STT start because TTS is speaking.")
            isActuallyRecognizing = false // Ensure this is false if we don't start
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
                if(isListeningUserIntent) listeningStatusText = "Listening..." // Only if user intends to listen
                Log.d("STT", "onReadyForSpeech. User intent: $isListeningUserIntent")
            }
            override fun onBeginningOfSpeech() {
                if(isListeningUserIntent) listeningStatusText = "Hearing speech..."
                Log.d("STT", "onBeginningOfSpeech")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                if(isListeningUserIntent) listeningStatusText = "Processing..."
                isActuallyRecognizing = false 
                Log.d("STT", "onEndOfSpeech. User intent: $isListeningUserIntent")
            }
            override fun onError(error: Int) {
                isActuallyRecognizing = false 
                val errorMsg = when (error) { /* ... error messages ... */
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
                Log.e("STT", "Error: $errorMsg ($error). User intent: $isListeningUserIntent")

                if (isListeningUserIntent) { 
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        listeningStatusText = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            "Didn't catch that. Listening again..."
                        } else { "Listener error, retrying..." }
                        Log.d("STT", "onError - restarting listening due to: $errorMsg")
                        coroutineScope.launch { delay(500); if(isListeningUserIntent) startListeningInternal() }
                    } else {
                        listeningStatusText = "Error: $errorMsg. Listening stopped."
                        isListeningUserIntent = false
                    }
                } else { 
                    listeningStatusText = "Error: $errorMsg."
                }
            }
            override fun onResults(results: Bundle?) {
                isActuallyRecognizing = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                    Log.d("STT", "onResults: ${matches[0]}. User intent: $isListeningUserIntent")
                    homeViewModel.updateInputText(matches[0])
                    homeViewModel.sendMessage() 
                    listeningStatusText = "Message sent. AI responding..." 
                } else {
                    Log.d("STT", "onResults: No suitable match. User intent: $isListeningUserIntent")
                    listeningStatusText = "Didn't catch that clearly."
                    if (isListeningUserIntent) { 
                        Log.d("STT", "onResults (no match) - restarting listening.")
                        coroutineScope.launch { delay(500); if(isListeningUserIntent) startListeningInternal() }
                    } else {
                        listeningStatusText += " Tap Mic to speak again."
                    }
                }
                // Auto-re-listening after AI response is now handled by LaunchedEffect(isTtsActuallySpeaking)
            }
            override fun onPartialResults(partialResults: Bundle?) { /* ... */ }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose { speechRecognizer.destroy(); Log.d("STT", "SpeechRecognizer destroyed") }
    }

    // Effect to handle STT state when TTS starts/stops
    LaunchedEffect(isTtsActuallySpeaking, isListeningUserIntent, isConnected) {
        if (isConnected) {
            if (isTtsActuallySpeaking) {
                // TTS is speaking, ensure STT is stopped and update status
                if (isActuallyRecognizing) {
                    speechRecognizer.cancel()
                    isActuallyRecognizing = false
                    Log.d("STT_TTS_SYNC", "STT cancelled because TTS started.")
                }
                listeningStatusText = "AI speaking..."
            } else {
                // TTS is NOT speaking
                if (isListeningUserIntent && !isActuallyRecognizing && !isLoading) {
                    // If user wants to listen, TTS is done, and not already recognizing/loading, start STT
                    Log.d("STT_TTS_SYNC", "TTS finished and user wants to listen. Starting STT.")
                    startListeningInternal()
                } else if (!isListeningUserIntent && !isActuallyRecognizing) {
                    // If user doesn't want to listen and STT not active, set default prompt
                     listeningStatusText = "Tap Mic to Start Listening"
                }
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
                                if(isActuallyRecognizing) {
                                    speechRecognizer.stopListening()
                                    isActuallyRecognizing = false
                                }
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
                        enabled = !isLoading && !isTtsActuallySpeaking // Disable if AI is busy or speaking
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
                        onClick = { homeViewModel.stopSpeaking() }, // This now also sets isTtsSpeaking to false
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
