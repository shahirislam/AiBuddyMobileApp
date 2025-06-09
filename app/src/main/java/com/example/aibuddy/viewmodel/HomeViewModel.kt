package com.example.aibuddy.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aibuddy.data.AiBuddyRepository
import com.example.aibuddy.data.local.ConversationTopic
import com.example.aibuddy.data.local.UserFact
import com.example.aibuddy.texttospeech.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import android.util.Log
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AiBuddyRepository = AiBuddyRepository(application)

    private var textToSpeechManager: TextToSpeechManager? = null
    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    // Flag to ensure greeting happens only once per connection session
    private var hasGreetedThisSession = false

    init {
        Log.i("HomeViewModel", "HomeViewModel instance created: ${this.hashCode()} (PID: ${android.os.Process.myPid()})")

        // Initialize TextToSpeechManager with callbacks
        textToSpeechManager = TextToSpeechManager(
            application.applicationContext,
            onSpeechStart = {
                // This callback is invoked by TextToSpeechManager when speech playback actually starts.
                Log.d("HomeViewModel", "TTSManager reports speech started.")
                _isTtsSpeaking.value = true
            },
            onSpeechFinish = {
                // This callback is invoked by TextToSpeechManager when speech playback completes or errors.
                Log.d("HomeViewModel", "TTSManager reports speech finished.")
                _isTtsSpeaking.value = false
            }
        )
    }

    // `speak` function now just calls the TTS manager, its state will be managed by callbacks
    private fun speak(text: String) {
        if (text.isBlank()) {
            Log.w("TTS", "Speak called with blank text. Not speaking.")
            _isTtsSpeaking.value = false // Ensure state is false if nothing to speak
            return
        }
        Log.d("TTS", "Attempting to speak: '$text' using Google Cloud TTS")
        textToSpeechManager?.speak(text)
        // _isTtsSpeaking is now updated via onSpeechStart callback from TextToSpeechManager
    }

    fun stopSpeaking() {
        textToSpeechManager?.stopSpeaking()
        // _isTtsSpeaking is now updated via onSpeechFinish callback from TextToSpeechManager
        // which is called by stopSpeaking() in TextToSpeechManager's cleanupMediaPlayer().
        Log.i("TTS", "TTS manually stopped by user.")
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

    val userFacts: StateFlow<List<UserFact>> = repository.getAllUserFacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val conversationTopics: StateFlow<List<ConversationTopic>> = repository.getAllConversationTopics()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteUserFact(id: Int) {
        viewModelScope.launch {
            repository.deleteUserFact(id)
        }
    }

    fun deleteConversationTopic(id: Int) {
        viewModelScope.launch {
            repository.deleteConversationTopic(id)
        }
    }

    fun connectAndGreet() {
        Log.i("HomeViewModel", "Instance ${this.hashCode()} - connectAndGreet called. Current isConnected: ${_isConnected.value}, hasGreeted: $hasGreetedThisSession")
        if (!_isConnected.value) {
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
    }

    fun disconnect() {
        Log.i("HomeViewModel", "Instance ${this.hashCode()} - disconnect() called. Current isConnected: ${_isConnected.value}")
        if (_isConnected.value) {
            _isConnected.value = false
            inputText = ""
            _aiBuddyResponse.value = ""
            _errorMessage.value = null
            hasGreetedThisSession = false
            stopSpeaking() // Ensure TTS is stopped and _isTtsSpeaking is false via callback
            Log.i("HomeViewModel", "Instance ${this.hashCode()} - Disconnected by disconnect() call.")
        }
    }

    fun toggleConnection() {
        Log.i("HomeViewModel", "Instance ${this.hashCode()} - toggleConnection called. Current state isConnected: ${_isConnected.value}")
        if (_isConnected.value) {
            disconnect()
        } else {
            _isConnected.value = true
            Log.i("HomeViewModel", "Instance ${this.hashCode()} - toggleConnection: Transitioned to connected. Greeting will depend on hasGreetedThisSession.")
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
                    } else {
                        Log.w("HomeViewModel", "AI greeting response is blank. No speech will be generated.")
                        _isTtsSpeaking.value = false // Explicitly set to false if no speech
                    }
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = ""
                    _errorMessage.value = "Error initiating conversation: ${exception.localizedMessage}"
                    _isTtsSpeaking.value = false // Ensure TTS speaking state is false on error
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
                    if (responseText.isNotBlank()) {
                        speak(responseText)
                    } else {
                        Log.w("HomeViewModel", "AI response for message is blank. No speech will be generated.")
                        _isTtsSpeaking.value = false // Explicitly set to false if no speech
                    }
                    inputText = ""
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = ""
                    _errorMessage.value = "Error: ${exception.localizedMessage}"
                    _isTtsSpeaking.value = false // Ensure TTS speaking state is false on error
                }
            )
            _isLoading.value = false
        }
    }
}
