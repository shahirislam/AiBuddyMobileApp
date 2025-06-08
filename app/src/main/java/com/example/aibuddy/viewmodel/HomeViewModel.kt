package com.example.aibuddy.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aibuddy.data.AiBuddyRepository
import com.example.aibuddy.texttospeech.TextToSpeechManager // Added
import kotlinx.coroutines.flow.MutableStateFlow
import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AiBuddyRepository = AiBuddyRepository()

    private var textToSpeechManager: TextToSpeechManager? = null
    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    // Flag to ensure greeting happens only once per connection session
    private var hasGreetedThisSession = false

    // API Key field removed as we are now using Service Account JSON

    init {
        Log.i("HomeViewModel", "HomeViewModel instance created: ${this.hashCode()} (PID: ${android.os.Process.myPid()})") // Diagnostic log
        textToSpeechManager = TextToSpeechManager(application.applicationContext) // API Key parameter removed
        // The TextToSpeechManager initializes asynchronously using the service_account_key.json.
        // We'll manage _isTtsSpeaking when speak() and stopSpeaking() are called.
    }

    private fun speak(text: String) {
        if (text.isBlank()) {
            Log.w("TTS", "Speak called with blank text.")
            return
        }
        _isTtsSpeaking.value = true // Assume speaking starts immediately for UI feedback
        Log.d("TTS", "Attempting to speak: '$text' using Google Cloud TTS")
        textToSpeechManager?.speak(text)
        // Note: The actual start and stop of speech is handled within TextToSpeechManager.
        // For more accurate _isTtsSpeaking, TextToSpeechManager would need to provide callbacks.
        // For now, stopSpeaking() will set _isTtsSpeaking to false.
    }

    fun stopSpeaking() {
        textToSpeechManager?.stopSpeaking()
        _isTtsSpeaking.value = false // Manually set to false when stopping
        Log.i("TTS", "TTS manually stopped by user (or playback finished).")
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechManager?.shutdown()
        Log.i("TTS", "TextToSpeechManager Shutdown.")
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var inputText by mutableStateOf("")
        private set

    private val _aiBuddyResponse = MutableStateFlow("")
    val aiBuddyResponse: StateFlow<String> = _aiBuddyResponse.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun connectAndGreet() {
        Log.i("HomeViewModel", "Instance ${this.hashCode()} - connectAndGreet called. Current isConnected: ${_isConnected.value}, hasGreeted: $hasGreetedThisSession")
        if (!_isConnected.value) { // Only proceed if not already connected
            _isConnected.value = true
            Log.i("HomeViewModel", "Instance ${this.hashCode()} - connectAndGreet: Set isConnected to true.")
            if (!hasGreetedThisSession) {
                initiateAiGreeting()
                hasGreetedThisSession = true
                Log.i("HomeViewModel", "Instance ${this.hashCode()} - connectAndGreet: Initiated greeting and set hasGreetedThisSession to true.")
            } else {
                Log.i("HomeViewModel", "Instance ${this.hashCode()} - connectAndGreet: Already greeted this session.")
            }
        } else {
            Log.i("HomeViewModel", "Instance ${this.hashCode()} - connectAndGreet: Already connected. Doing nothing.")
        }
        // If already connected, do nothing, preventing a toggle.
    }

    fun disconnect() {
        Log.i("HomeViewModel", "Instance ${this.hashCode()} - disconnect() called. Current isConnected: ${_isConnected.value}")
        if (_isConnected.value) { // Only if currently connected
            _isConnected.value = false
            Log.i("HomeViewModel", "Instance ${this.hashCode()} - disconnect: Set isConnected to false.")
            inputText = ""
            _aiBuddyResponse.value = ""
            _errorMessage.value = null
            hasGreetedThisSession = false // Reset greeting flag
            Log.i("HomeViewModel", "Instance ${this.hashCode()} - Disconnected by disconnect() call.")
        }
    }

    // toggleConnection will now primarily act as a way to flip the state.
    // For explicit connect with greeting, use connectAndGreet().
    // For explicit disconnect, use disconnect().
    fun toggleConnection() {
        Log.i("HomeViewModel", "Instance ${this.hashCode()} - toggleConnection called. Current state isConnected: ${_isConnected.value}")
        if (_isConnected.value) {
            // If currently connected, toggle means disconnect.
            disconnect()
        } else {
            // If currently disconnected, toggle means connect (but without the greeting logic here, connectAndGreet handles that).
            // This path might be hit if something calls toggleConnection when already disconnected.
            // We'll connect, but rely on hasGreetedThisSession to prevent re-greeting if it already happened.
            _isConnected.value = true
            Log.i("HomeViewModel", "Instance ${this.hashCode()} - toggleConnection: Transitioned to connected. Greeting will depend on hasGreetedThisSession.")
            // No direct call to initiateAiGreeting() here to avoid conflicts with connectAndGreet's logic.
            // If a greeting is needed, it should have been through connectAndGreet.
            // If hasGreetedThisSession is false, a subsequent UI effect might trigger it if appropriate.
        }
    }

    private fun initiateAiGreeting() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val initialPrompt = "You are AiBuddy, a friendly and empathetic AI companion. Start a short, welcoming conversation with the user. For example, ask them how they are doing, what they are up to, or mention something interesting to break the ice. Keep your initial message concise."
            val result = repository.generateContent(initialPrompt)
            result.fold(
                onSuccess = { responseText ->
                    _aiBuddyResponse.value = responseText
                    if (responseText.isNotBlank()) {
                        speak(responseText)
                    }
                    // If greeting was blank or failed to speak, hasGreetedThisSession is still true.
                    // This means it won't try again until a full disconnect/reconnect.
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = ""
                    _errorMessage.value = "Error initiating conversation: ${exception.localizedMessage}"
                    // If greeting fails, hasGreetedThisSession is still true.
                    // This prevents repeated attempts if the failure is persistent,
                    // until a full disconnect and reconnect.
                }
            )
            _isLoading.value = false
        }
    }

    fun updateInputText(newText: String) {
        inputText = newText
    }

    fun sendMessage() {
        if (inputText.isBlank()) {
            _errorMessage.value = "Message cannot be empty."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.generateContent(inputText)
            result.fold(
                onSuccess = { responseText ->
                    _aiBuddyResponse.value = responseText
                    if (responseText.isNotBlank()) speak(responseText)
                    inputText = ""
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = ""
                    _errorMessage.value = "Error: ${exception.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }

    // Ensure that initiateAiGreeting also logs the instance
    // This is already implicitly handled as other methods log the instance.
}
