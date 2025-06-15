package com.example.aibuddy.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aibuddy.data.AiBuddyRepository
import com.example.aibuddy.data.local.Conversation
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

    private val _conversationHistory = mutableStateListOf<String>()
    val conversationHistory: List<String> = _conversationHistory

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

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val userFacts: StateFlow<List<UserFact>> = repository.getAllUserFacts()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val conversationTopics: StateFlow<List<ConversationTopic>> = repository.getAllConversationTopics()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentConversations: StateFlow<List<Conversation>> = repository.getRecentConversations()
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

    fun addConversation(conversationHistory: String, durationInMinutes: Int) {
        viewModelScope.launch {
            val title = repository.generateConversationTitle(conversationHistory).getOrDefault("New Conversation")
            repository.insertConversation(
                Conversation(
                    title = title,
                    timestamp = System.currentTimeMillis(),
                    durationInMinutes = durationInMinutes
                )
            )
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
            _conversationHistory.clear()
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
            val result = repository.generateContent(initialPrompt, _conversationHistory.toList())
            result.fold(
                onSuccess = { responseText ->
                    val cleanResponse = responseText.substringBefore("<!--JSON_START-->").trim()
                    _aiBuddyResponse.value = cleanResponse
                    if (cleanResponse.isNotBlank()) {
                        _conversationHistory.add("AI: $cleanResponse")
                        speak(cleanResponse)
                    } else {
                        _isTtsSpeaking.value = false
                    }
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = ""
                    _errorMessage.value = "Error initiating conversation: ${exception.localizedMessage}"
                    _isTtsSpeaking.value = false
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
            _conversationHistory.add("User: $inputText")
            val result = repository.generateContent(inputText, _conversationHistory.toList())
            result.fold(
                onSuccess = { responseText ->
                    if (responseText.contains("<!--SEARCH_QUERY:")) {
                        val searchQuery = responseText.substringAfter("<!--SEARCH_QUERY:").substringBefore("-->").trim()
                        _isSearching.value = true
                        val searchResult = repository.searchAndGenerateContent(searchQuery, inputText)
                        searchResult.fold(
                            onSuccess = { searchResponse ->
                                val cleanSearchResponse = searchResponse.substringBefore("<!--JSON_START-->").trim()
                                _aiBuddyResponse.value = cleanSearchResponse
                                if (cleanSearchResponse.isNotBlank()) {
                                    _conversationHistory.add("AI: $cleanSearchResponse")
                                    speak(cleanSearchResponse)
                                } else {
                                    _isTtsSpeaking.value = false
                                }
                            },
                            onFailure = { exception ->
                                _aiBuddyResponse.value = ""
                                _errorMessage.value = "Error: ${exception.localizedMessage}"
                                _isTtsSpeaking.value = false
                            }
                        )
                        _isSearching.value = false
                    } else {
                        val cleanResponse = responseText.substringBefore("<!--JSON_START-->").trim()
                        _aiBuddyResponse.value = cleanResponse
                        if (cleanResponse.isNotBlank()) {
                            _conversationHistory.add("AI: $cleanResponse")
                            speak(cleanResponse)
                        } else {
                            _isTtsSpeaking.value = false
                        }
                    }
                    inputText = ""
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = ""
                    _errorMessage.value = "Error: ${exception.localizedMessage}"
                    _isTtsSpeaking.value = false
                }
            )
            _isLoading.value = false
        }
    }
}
