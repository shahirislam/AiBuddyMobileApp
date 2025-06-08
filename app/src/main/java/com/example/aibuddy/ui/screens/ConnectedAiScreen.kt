package com.example.aibuddy.ui.screens

import android.Manifest
import android.app.Activity // Keep this for casting LocalContext.current if needed for other purposes
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
import androidx.activity.ComponentActivity // Added for ViewModel scoping

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAiScreen(
    navController: NavController
    // homeViewModel: HomeViewModel = viewModel() // Old way, parameter removed
) {
    val context = LocalContext.current
    val componentActivity = context as ComponentActivity // For ViewModel scoping
    val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = componentActivity) // Scoped to Activity

    val activity = context as? Activity // Can still be used if needed for specific Activity operations
    val coroutineScope = rememberCoroutineScope()

    val isConnected by homeViewModel.isConnected.collectAsState()
    val aiBuddyResponse by homeViewModel.aiBuddyResponse.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val errorMessage by homeViewModel.errorMessage.collectAsState()
    val isTtsActuallySpeaking by homeViewModel.isTtsSpeaking.collectAsState()
    val currentInputText = homeViewModel.inputText // Changed to direct assignment

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

        // The connection is now expected to be handled by the screen that navigates here (HomeScreen).
        // This screen will react to the established connection state, for example, to auto-start listening.
        if (isConnected && hasAudioPermission && !isTtsActuallySpeaking && !isLoading) { // Added !isLoading
            if (!isListeningUserIntent) {
                isListeningUserIntent = true
                startListeningInternal()
            }
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

    // Helper to track previous value of isTtsActuallySpeaking to detect when it stops
    val ttsPreviouslySpeaking = remember { mutableStateOf(isTtsActuallySpeaking) }

    LaunchedEffect(
        isTtsActuallySpeaking,
        isListeningUserIntent, // User's explicit intent (button press)
        isConnected,
        hasAudioPermission,
        isLoading
    ) {
        val didTtsJustStop = ttsPreviouslySpeaking.value && !isTtsActuallySpeaking

        if (isConnected) {
            if (isTtsActuallySpeaking) { // AI is actively speaking
                if (isActuallyRecognizing) { // If STT is somehow active, cancel it
                    speechRecognizer.cancel()
                    isActuallyRecognizing = false
                }
                listeningStatusText = "AI speaking..."
            } else { // AI is NOT speaking
                if (hasAudioPermission && !isLoading) {
                    // Auto-restart logic: If TTS just stopped and user isn't already intending to listen, set the intent.
                    if (didTtsJustStop && !isListeningUserIntent) {
                        isListeningUserIntent = true // Set user intent to listen. Effect will re-run.
                    }

                    if (isListeningUserIntent) { // User intends to listen (either manually or auto-set)
                        if (!isActuallyRecognizing) {
                            startListeningInternal() // This function has its own guards
                        }
                        // If isActuallyRecognizing is true, listeningStatusText is handled by RecognitionListener
                    } else { // User does NOT intend to listen (e.g., pressed Stop or never started)
                        if (!isActuallyRecognizing) { // Ensure not stuck in a recognizing state
                           listeningStatusText = "Tap Mic to Start Listening"
                        }
                    }
                } else if (!hasAudioPermission && !isLoading) {
                    // Update status text based on whether user intent was active before permission issue
                    listeningStatusText = if (isListeningUserIntent && !isActuallyRecognizing) {
                        "Listening stopped (Permission Denied)"
                    } else {
                        "Tap Mic for Permission"
                    }
                } else if (isLoading) {
                    // If loading (e.g., AI processing), ensure STT is stopped.
                    if (isActuallyRecognizing) {
                        speechRecognizer.cancel() 
                        isActuallyRecognizing = false
                    }
                    // Using the collected state: currentInputText
                    listeningStatusText = if (aiBuddyResponse.isNotEmpty() && (currentInputText?.isNotEmpty() == true)) "AI Responding..." else "Processing..."
                }
            }
        } else { // Not connected
            if (isActuallyRecognizing) {
                speechRecognizer.cancel()
            }
            isListeningUserIntent = false
            isActuallyRecognizing = false
            listeningStatusText = "Disconnected. Tap Connect."
        }

        // Update previous TTS state for the next evaluation
        if (ttsPreviouslySpeaking.value != isTtsActuallySpeaking) {
            ttsPreviouslySpeaking.value = isTtsActuallySpeaking
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
                    homeViewModel.disconnect() // Use the new dedicated disconnect method
                    navController.popBackStack()
                }) {
                    Text("Disconnect & Back")
                }
            }
        }
    }
}
