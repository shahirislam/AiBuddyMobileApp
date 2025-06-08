package com.example.aibuddy.texttospeech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.aibuddy.R // Required for R.raw.service_account_key
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.texttospeech.v1.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class TextToSpeechManager(private val context: Context) { // API Key removed from constructor

    private var textToSpeechClient: TextToSpeechClient? = null
    private var mediaPlayer: MediaPlayer? = null
    private val ttsJob = Job()
    private val ttsScope = CoroutineScope(Dispatchers.IO + ttsJob)

    init {
        Log.i("TTSManager", "Initializing TextToSpeechManager with Service Account Key...")

        ttsScope.launch {
            try {
                val inputStream: InputStream = context.resources.openRawResource(R.raw.service_account_key)
                Log.i("TTSManager", "Service account key file opened from res/raw.")

                val credentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform")) // Ensure correct scope
                Log.i("TTSManager", "GoogleCredentials created from service account key.")

                val settings = TextToSpeechSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                    .build()
                Log.i("TTSManager", "TextToSpeechClient settings created with FixedCredentialsProvider. Attempting to create client instance...")

                textToSpeechClient = TextToSpeechClient.create(settings)

                if (textToSpeechClient != null) {
                    Log.i("TTSManager", "TextToSpeechClient initialized SUCCESSFULLY with Service Account.")
                } else {
                    Log.e("TTSManager", "CRITICAL: TextToSpeechClient.create(settings) returned null (Service Account). This is unexpected.")
                }
            } catch (e: Throwable) {
                Log.e("TTSManager", "CRITICAL EXCEPTION during TextToSpeechClient initialization with Service Account.", e)
            }
        }
    }

    fun speak(text: String) {
        if (textToSpeechClient == null) {
            Log.e("TTSManager", "TextToSpeechClient is not initialized or failed to initialize. Cannot speak.")
            return
        }

        stopSpeaking()

        ttsScope.launch {
            try {
                Log.d("TTSManager", "Speak function: Preparing to synthesize speech for text: \"$text\"")
                val input = SynthesisInput.newBuilder()
                    .setText(text)
                    .build()

                val voice = VoiceSelectionParams.newBuilder()
                    .setLanguageCode("en-US")
                    .setName("en-US-Wavenet-D")
                    .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                    .build()

                val audioConfig = AudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.MP3)
                    .build()

                Log.d("TTSManager", "Sending synthesizeSpeech request...")
                val response = textToSpeechClient?.synthesizeSpeech(input, voice, audioConfig)
                val audioContents = response?.audioContent

                if (audioContents != null) {
                    val outputFile = File(context.cacheDir, "output_tts.mp3")
                    FileOutputStream(outputFile).use { fos ->
                        fos.write(audioContents.toByteArray())
                    }
                    Log.d("TTSManager", "Audio content saved to ${outputFile.absolutePath}")

                    mediaPlayer = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(outputFile.absolutePath)
                        prepareAsync()
                        setOnPreparedListener {
                            Log.d("TTSManager", "MediaPlayer prepared, starting playback.")
                            start()
                        }
                        setOnCompletionListener {
                            Log.d("TTSManager", "MediaPlayer playback completed.")
                            stopSpeaking()
                            outputFile.delete()
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e("TTSManager", "MediaPlayer Error: what: $what, extra: $extra")
                            stopSpeaking()
                            outputFile.delete()
                            true
                        }
                    }
                } else {
                    Log.e("TTSManager", "SynthesizeSpeech response was successful, but audio content is null.")
                }

            } catch (e: Exception) {
                Log.e("TTSManager", "Error synthesizing speech in speak function", e)
            }
        }
    }

    fun stopSpeaking() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset()
            it.release()
            Log.d("TTSManager", "MediaPlayer stopped and released.")
        }
        mediaPlayer = null
        val outputFile = File(context.cacheDir, "output_tts.mp3")
        if (outputFile.exists()) {
            outputFile.delete()
            Log.d("TTSManager", "Cleaned up temporary TTS audio file.")
        }
    }

    fun shutdown() {
        Log.d("TTSManager", "Shutting down TextToSpeechManager...")
        stopSpeaking()
        ttsJob.cancel()
        try {
            textToSpeechClient?.shutdownNow()
            textToSpeechClient?.close()
        } catch (e: Exception) {
            Log.e("TTSManager", "Exception during TextToSpeechClient shutdown", e)
        }
        textToSpeechClient = null
        Log.i("TTSManager", "TextToSpeechManager shut down complete.")
    }
}
