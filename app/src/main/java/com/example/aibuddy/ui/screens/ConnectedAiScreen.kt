package com.example.aibuddy.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.navigation.NavController
import com.example.aibuddy.ui.RoboEyes
import com.example.aibuddy.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAiScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val isConnected by homeViewModel.isConnected.collectAsState()
    val aiBuddyResponse by homeViewModel.aiBuddyResponse.collectAsState()
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
    var localPermissionLauncherState by remember { mutableStateOf<ActivityResultLauncher<String>?>(null) }

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
            if(isTtsActuallySpeaking) Log.d("STT", "Blocked STT start because TTS is speaking.")
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
            localPermissionLauncherState?.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasAudioPermission = isGranted
        if (isGranted) {
            listeningStatusText = "Permission granted."
            if (isListeningUserIntent) {
                startListeningInternal()
            } else {
                 listeningStatusText += " Tap Mic to Start."
            }
        } else {
            listeningStatusText = "Audio permission denied."
            isListeningUserIntent = false
            isActuallyRecognizing = false
        }
    }
    LaunchedEffect(permissionLauncher) {
        localPermissionLauncherState = permissionLauncher
    }

    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        if (!isConnected) { 
            homeViewModel.toggleConnection()
        } else if (!isListeningUserIntent && hasAudioPermission && !isTtsActuallySpeaking) {
            isListeningUserIntent = true
            startListeningInternal()
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (isActuallyRecognizing) {
                speechRecognizer.stopListening()
            }
            isListeningUserIntent = false
            isActuallyRecognizing = false
        }
    }

    DisposableEffect(key1 = speechRecognizer) {
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if(isListeningUserIntent) listeningStatusText = "Listening..." }
            override fun onBeginningOfSpeech() { if(isListeningUserIntent) listeningStatusText = "Hearing speech..." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { if(isListeningUserIntent) listeningStatusText = "Processing..."; isActuallyRecognizing = false }
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
                Log.e("STT", "Error: $errorMsg ($error). User intent: $isListeningUserIntent")
                if (isListeningUserIntent) {
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        speechRecognizer.cancel()
                        listeningStatusText = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Didn't catch that. Listening again..." else "Listener error, retrying..."
                        coroutineScope.launch { delay(500); if(isListeningUserIntent) startListeningInternal() }
                    } else {
                        listeningStatusText = "Error: $errorMsg. Listening stopped."; isListeningUserIntent = false
                    }
                } else { listeningStatusText = "Error: $errorMsg." }
            }
            override fun onResults(results: Bundle?) {
                isActuallyRecognizing = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                    homeViewModel.updateInputText(matches[0]); homeViewModel.sendMessage()
                    listeningStatusText = "Message sent. AI responding..."
                } else {
                    listeningStatusText = "Didn't catch that clearly."
                    if (isListeningUserIntent) { coroutineScope.launch { delay(500); if(isListeningUserIntent) startListeningInternal() } }
                    else { listeningStatusText += " Tap Mic to speak again." }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) { }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(recognitionListener)
        onDispose { speechRecognizer.destroy() }
    }

    LaunchedEffect(isTtsActuallySpeaking, isListeningUserIntent, isConnected, hasAudioPermission) {
        if (isConnected) {
            if (isTtsActuallySpeaking) {
                if (isActuallyRecognizing) { speechRecognizer.cancel(); isActuallyRecognizing = false }
                listeningStatusText = "AI speaking..."
            } else {
                if (isListeningUserIntent && hasAudioPermission && !isActuallyRecognizing && !isLoading) {
                    startListeningInternal()
                } else if (!isListeningUserIntent && !isActuallyRecognizing) {
                     if (hasAudioPermission) listeningStatusText = "Tap Mic to Start Listening"

                }
            }
        } else {
            if(isActuallyRecognizing) speechRecognizer.cancel()
            isListeningUserIntent = false
            isActuallyRecognizing = false
            listeningStatusText = "Disconnected. Tap Connect."
        }
    }

    Scaffold { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround 
            ) {
                RoboEyes(
                    baseEyeSize = 100.dp, 
                    isAiSpeaking = isTtsActuallySpeaking,
                    isListeningToUser = isActuallyRecognizing 
                )
                if (aiBuddyResponse.isNotEmpty()) {
                    Text(text = "AI Buddy: $aiBuddyResponse", style = MaterialTheme.typography.bodyLarge)
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(text = listeningStatusText, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button( 
                        onClick = {
                            if (isListeningUserIntent) { 
                                isListeningUserIntent = false
                                if(isActuallyRecognizing) { speechRecognizer.stopListening(); isActuallyRecognizing = false }
                                listeningStatusText = "Listening stopped. Tap Mic to Start."
                            } else { 
                                if (hasAudioPermission) { isListeningUserIntent = true; startListeningInternal() } 
                                else { localPermissionLauncherState?.launch(Manifest.permission.RECORD_AUDIO) }
                            }
                        },
                        enabled = !isLoading && !isTtsActuallySpeaking 
                    ) {
                        Icon(if (isListeningUserIntent) Icons.Filled.Stop else Icons.Filled.Mic, null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (isListeningUserIntent) "Stop Listening" else if (hasAudioPermission) "Start Listening" else "Mic Permission")
                    }
                    Button(onClick = { homeViewModel.stopSpeaking() }, enabled = !isLoading && isTtsActuallySpeaking) {
                        Text("Stop AI Talk")
                    }
                }
                 Button(onClick = {
                    if (isConnected) homeViewModel.toggleConnection() 
                    navController.popBackStack()
                }) {
                    Text("Disconnect & Back")
                }
            }
        }
    }
}
