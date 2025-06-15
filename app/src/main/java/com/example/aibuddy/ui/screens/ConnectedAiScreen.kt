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
import androidx.activity.ComponentActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectedAiScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val componentActivity = context as ComponentActivity
    val homeViewModel: HomeViewModel = viewModel(viewModelStoreOwner = componentActivity)

    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val isConnected by homeViewModel.isConnected.collectAsState()
    val aiBuddyResponse by homeViewModel.aiBuddyResponse.collectAsState()
    val isLoading by homeViewModel.isLoading.collectAsState()
    val isSearching by homeViewModel.isSearching.collectAsState()
    val errorMessage by homeViewModel.errorMessage.collectAsState()
    val isTtsActuallySpeaking by homeViewModel.isTtsSpeaking.collectAsState()

    var conversationStartTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        homeViewModel.connectAndGreet()
        conversationStartTime = System.currentTimeMillis()
    }

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

    // This state helps us detect the *moment* TTS stops
    val ttsPreviouslySpeaking = remember { mutableStateOf(false) }


    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    var localPermissionLauncherState by remember { mutableStateOf<ActivityResultLauncher<String>?>(null) }

    fun createSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 8000L)
        }
    }

    fun startListeningInternal() {
        if (!isConnected || isTtsActuallySpeaking || isLoading) { // Ensure not listening if AI is speaking or loading
            Log.d("STT", "Blocked STT start: isConnected=$isConnected, isTtsActuallySpeaking=$isTtsActuallySpeaking, isLoading=$isLoading")
            isActuallyRecognizing = false
            isListeningUserIntent = false // Explicitly set to false if conditions prevent starting
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listeningStatusText = "Speech recognition not available."
            Log.e("STT", "Speech recognition not available.")
            isListeningUserIntent = false
            isActuallyRecognizing = false
            return
        }
        if (hasAudioPermission) {
            try {
                if (!isActuallyRecognizing) {
                    isActuallyRecognizing = true
                    speechRecognizer.startListening(createSpeechIntent())
                    listeningStatusText = "Listening..."
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
            // If permission is granted and we intend to listen, start immediately
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

        // Initialize ttsPreviouslySpeaking on screen start
        ttsPreviouslySpeaking.value = isTtsActuallySpeaking

        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (isActuallyRecognizing) {
                speechRecognizer.stopListening()
            }
            isListeningUserIntent = false
            isActuallyRecognizing = false
            // Important: Stop TTS when leaving the screen
            homeViewModel.stopSpeaking()
        }
    }

    DisposableEffect(key1 = speechRecognizer) {
        val recognitionListener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { if(isListeningUserIntent) listeningStatusText = "Listening..." }
            override fun onBeginningOfSpeech() { if(isListeningUserIntent) listeningStatusText = "Hearing speech..." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                // This indicates the speech recognizer has finished processing user's speech
                if(isListeningUserIntent) listeningStatusText = "Processing...";
                isActuallyRecognizing = false
            }
            override fun onError(error: Int) {
                isActuallyRecognizing = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { hasAudioPermission = false; "Permissions error" }
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
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
                        // Automatically re-start listening if there was a minor error or no speech, if still intending to listen
                        listeningStatusText = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "Didn't catch that. Listening again..." else "Listener error, retrying..."
                        coroutineScope.launch { delay(500); if(isListeningUserIntent) startListeningInternal() }
                    } else {
                        listeningStatusText = "Error: $errorMsg. Listening stopped.";
                        isListeningUserIntent = false // Stop trying to listen on serious errors
                    }
                } else { listeningStatusText = "Error: $errorMsg." }
            }
            override fun onResults(results: Bundle?) {
                isActuallyRecognizing = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty() && matches[0].isNotBlank()) {
                    val userMessage = matches[0]
                    homeViewModel.updateInputText(userMessage)
                    homeViewModel.sendMessage()
                    listeningStatusText = "Message sent. AI responding..."
                    isListeningUserIntent = false // User's turn is over, AI will speak next
                } else {
                    listeningStatusText = "Didn't catch that clearly."
                    // If user is still intended to speak, re-start listening
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

    // This LaunchedEffect manages the conversational flow and button states
    LaunchedEffect(
        isTtsActuallySpeaking,
        isConnected,
        hasAudioPermission,
        isLoading
    ) {
        val didTtsJustStop = ttsPreviouslySpeaking.value && !isTtsActuallySpeaking
        // Update the previous TTS speaking state for the next recomposition
        ttsPreviouslySpeaking.value = isTtsActuallySpeaking


        if (!isConnected) {
            if (isActuallyRecognizing) speechRecognizer.cancel()
            isListeningUserIntent = false
            isActuallyRecognizing = false
            listeningStatusText = "Disconnected. Tap Connect."
            return@LaunchedEffect
        }

        if (isLoading) {
            if (isActuallyRecognizing) speechRecognizer.cancel()
            isListeningUserIntent = false // Stop listening while AI is processing
            isActuallyRecognizing = false
            listeningStatusText = "Processing..."
            return@LaunchedEffect
        }

        if (isTtsActuallySpeaking) {
            if (isActuallyRecognizing) {
                speechRecognizer.cancel()
                isActuallyRecognizing = false
            }
            isListeningUserIntent = false // Ensure we are not listening when AI is speaking
            listeningStatusText = "AI speaking..."
            return@LaunchedEffect
        }

        // From here, TTS is NOT speaking and NOT loading.
        // This is the point where we decide if we should automatically start listening.

        if (didTtsJustStop) {
            // AI just finished speaking, automatically start listening
            Log.d("ConnectedAiScreen", "AI just finished speaking. Attempting to auto-start listening.")
            if (hasAudioPermission) {
                isListeningUserIntent = true // Set intent to true to allow startListeningInternal
                // Give a small delay before starting to avoid cutting off last part of TTS
                delay(200) // Small delay (e.g., 200ms)
                startListeningInternal()
            } else {
                listeningStatusText = "AI finished. Tap Mic for Permission to speak."
                isListeningUserIntent = false // Cannot listen without permission
            }
        } else if (!isListeningUserIntent && !isActuallyRecognizing) {
            // Default state when AI is not speaking, not loading, and we are not currently listening
            // This covers the initial state or when user manually stops listening.
            listeningStatusText = "Tap Mic to Start Listening"
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
                if (isSearching) {
                    Text("Searching the web...", style = MaterialTheme.typography.bodyLarge)
                } else if (aiBuddyResponse.isNotEmpty()) {
                    Text(text = "AI Buddy: $aiBuddyResponse", style = MaterialTheme.typography.bodyLarge)
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(text = listeningStatusText, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Start Listening / Stop Listening Button
                    Button(
                        onClick = {
                            if (isListeningUserIntent) {
                                // User wants to stop listening
                                isListeningUserIntent = false
                                if(isActuallyRecognizing) { speechRecognizer.stopListening(); isActuallyRecognizing = false }
                                listeningStatusText = "Listening stopped. Tap Mic to Start."
                            } else {
                                // User wants to start listening manually
                                if (hasAudioPermission) {
                                    isListeningUserIntent = true
                                    startListeningInternal()
                                } else {
                                    localPermissionLauncherState?.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        // Disabled if AI is speaking OR if AI is loading OR if already listening
                        enabled = !isLoading && !isTtsActuallySpeaking && (!isListeningUserIntent || isActuallyRecognizing)
                    ) {
                        Icon(if (isListeningUserIntent) Icons.Filled.Stop else Icons.Filled.Mic, null)
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (isListeningUserIntent) "Stop Listening" else if (hasAudioPermission) "Start Listening" else "Mic Permission")
                    }

                    // Stop AI Talking Button
                    Button(
                        onClick = {
                            homeViewModel.stopSpeaking()
                            // No need to explicitly set isListeningUserIntent here,
                            // the LaunchedEffect will react to isTtsActuallySpeaking becoming false
                        },
                        enabled = isTtsActuallySpeaking // Only enabled when AI is actively speaking
                    ) {
                        Text("Stop AI Talk")
                    }
                }
                Button(onClick = {
                    val durationInMinutes = ((System.currentTimeMillis() - conversationStartTime) / 60000).toInt()
                    homeViewModel.addConversation(homeViewModel.conversationHistory.joinToString("\n"), durationInMinutes)
                    homeViewModel.disconnect()
                    navController.popBackStack()
                }) {
                    Text("Disconnect & Back")
                }
                Button(onClick = {
                    navController.navigate(com.example.aibuddy.AppDestinations.CONTEXT_MANAGEMENT_ROUTE)
                }) {
                    Text("Manage Context")
                }
            }
        }
    }
}
