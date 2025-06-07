package com.example.aibuddy.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aibuddy.data.AiBuddyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository: AiBuddyRepository = AiBuddyRepository()

    private var tts: TextToSpeech? = null
    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking: StateFlow<Boolean> = _isTtsSpeaking.asStateFlow()

    init {
        tts = TextToSpeech(application.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language (US English) specified is not supported or language data is missing.")
            } else {
                Log.i("TTS", "TTS Initialized successfully with US English.")
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isTtsSpeaking.value = true
                    Log.d("TTS", "onStart: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                    Log.d("TTS", "onDone: $utteranceId")
                }

                @Deprecated("deprecated")
                override fun onError(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                    Log.e("TTS", "onError (deprecated): $utteranceId")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isTtsSpeaking.value = false
                    Log.e("TTS", "onError: $utteranceId, errorCode: $errorCode")
                }
            })
        } else {
            Log.e("TTS", "TTS Initialization Failed!")
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return

        // Stop any current speech before starting a new one.
        // This also helps if onDone/onError wasn't called for a previous utterance.
        tts?.stop() 
        _isTtsSpeaking.value = true // Set true immediately, onStart might have a slight delay

        val utteranceId = System.currentTimeMillis().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        Log.d("TTS", "Attempting to speak: '$text' with ID: $utteranceId")
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e("TTS", "Error in tts.speak for utteranceId $utteranceId")
            _isTtsSpeaking.value = false // Reset if speak call itself fails
        }
    }

    fun stopSpeaking() {
        tts?.stop()
        _isTtsSpeaking.value = false // Manually update state as onDone might not be called
        Log.i("TTS", "TTS manually stopped by user.")
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        Log.i("TTS", "TTS Shutdown.")
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

    fun toggleConnection() {
        _isConnected.value = !_isConnected.value
        if (!_isConnected.value) {
            // Reset states when disconnected
            inputText = ""
            _aiBuddyResponse.value = ""
            _errorMessage.value = null
        } else {
            // When connected, initiate AI greeting
            initiateAiGreeting()
        }
    }

    private fun initiateAiGreeting() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            // Reverted prompt for the AI to initiate a conversation in English
            val initialPrompt = "You are AiBuddy, a friendly and empathetic AI companion. Start a short, welcoming conversation with the user. For example, ask them how they are doing, what they are up to, or mention something interesting to break the ice. Keep your initial message concise."
            val result = repository.generateContent(initialPrompt)
            result.fold(
                onSuccess = { responseText ->
                    _aiBuddyResponse.value = responseText
                    if (responseText.isNotBlank()) speak(responseText)
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = "" // Clear previous response
                    _errorMessage.value = "Error initiating conversation: ${exception.localizedMessage}"
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
                    inputText = "" // Clear input after sending
                },
                onFailure = { exception ->
                    _aiBuddyResponse.value = "" // Clear previous response
                    _errorMessage.value = "Error: ${exception.localizedMessage}"
                }
            )
            _isLoading.value = false
        }
    }
}
