/*
package com.example.aibuddy.texttospeech

import android.content.Context
import android.util.Log
import com.google.cloud.texttospeech.v1.AudioConfig
import com.google.cloud.texttospeech.v1.AudioEncoding
import com.google.cloud.texttospeech.v1.SsmlVoiceGender
import com.google.cloud.texttospeech.v1.SynthesisInput
import com.google.cloud.texttospeech.v1.TextToSpeechClient
import com.google.cloud.texttospeech.v1.VoiceSelectionParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileOutputStream

class TextToSpeechManager(private val context: Context) {

    private var textToSpeechClient: TextToSpeechClient? = null

    init {
        // Initialize the client in a background thread to avoid blocking the UI
        GlobalScope.launch(Dispatchers.IO) {
            try {
                textToSpeechClient = TextToSpeechClient.create()
                Log.d("TTSManager", "TextToSpeechClient initialized successfully")
            } catch (e: Exception) {
                Log.e("TTSManager", "Error initializing TextToSpeechClient", e)
            }
        }
    }

    fun speak(text: String) {
        if (textToSpeechClient == null) {
            Log.e("TTSManager", "TextToSpeechClient is not initialized")
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val input = SynthesisInput.newBuilder()
                    .setText(text)
                    .build()

                // Select the voice and audio profile
                val voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("en-US") // Or any other language code
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL) // Or FEMALE, MALE
                    .build()

                // Audio configuration
                val audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3) // Or LINEAR16, OGG_OPUS
                    .build()

                // Perform the text-to-speech request
                val response = textToSpeechClient?.synthesizeSpeech(input, voice, audioConfig)

                // Get the audio contents from the response
                val audioContents = response?.audioContent

                // Play the audio (this part requires an audio player implementation)
                // For simplicity, we'll just save it to a file for now
                if (audioContents != null) {
                    val filename = "aibuddy_response_${System.currentTimeMillis()}.mp3"
                    val file = context.filesDir.resolve(filename)
                    FileOutputStream(file).use { fos ->
                        fos.write(audioContents.toByteArray())
                    }
                    Log.d("TTSManager", "Audio content saved to ${file.absolutePath}")
                    // TODO: Implement audio playback
                }

            } catch (e: Exception) {
                Log.e("TTSManager", "Error synthesizing speech", e)
            }
        }
    }

    fun stopSpeaking() {
        // TODO: Implement logic to stop audio playback if it's playing
    }

    fun shutdown() {
        textToSpeechClient?.shutdownNow()
        Log.d("TTSManager", "TextToSpeechClient shut down")
    }
}

 */