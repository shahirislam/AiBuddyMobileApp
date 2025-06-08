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

// Modify constructor to accept callbacks
class TextToSpeechManager(
    private val context: Context,
    private val onSpeechStart: () -> Unit, // Callback for when speech playback begins
    private val onSpeechFinish: () -> Unit // Callback for when speech playback ends (completion or error)
) {

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
                    .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))
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
                onSpeechFinish.invoke() // In case of initialization error, ensure state is reset
            }
        }
    }

    fun speak(text: String) {
        if (textToSpeechClient == null) {
            Log.e("TTSManager", "TextToSpeechClient is not initialized or failed to initialize. Cannot speak.")
            onSpeechFinish.invoke() // Notify that speech did not start
            return
        }

        stopSpeaking() // Ensure any previous playback is stopped before starting new one
        onSpeechStart.invoke() // Notify that speech is about to start

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
                            // onSpeechStart is already invoked before the network call,
                            // if you need a very precise "started playing" state,
                            // you could move onSpeechStart.invoke() here, but for now
                            // it's fine where it is to indicate intent.
                        }
                        setOnCompletionListener {
                            Log.d("TTSManager", "MediaPlayer playback completed.")
                            cleanupMediaPlayer() // Clean up resources
                            outputFile.delete() // Delete temp file
                            onSpeechFinish.invoke() // Notify completion
                        }
                        setOnErrorListener { _, what, extra ->
                            Log.e("TTSManager", "MediaPlayer Error: what: $what, extra: $extra")
                            cleanupMediaPlayer() // Clean up resources
                            outputFile.delete() // Delete temp file
                            onSpeechFinish.invoke() // Notify error as a finish condition
                            true
                        }
                    }
                } else {
                    Log.e("TTSManager", "SynthesizeSpeech response was successful, but audio content is null.")
                    onSpeechFinish.invoke() // If no audio, consider it finished
                }

            } catch (e: Exception) {
                Log.e("TTSManager", "Error synthesizing speech in speak function", e)
                onSpeechFinish.invoke() // Notify error as a finish condition
            }
        }
    }

    fun stopSpeaking() {
        cleanupMediaPlayer() // Centralize cleanup
    }

    private fun cleanupMediaPlayer() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset() // Reset rather than releasing immediately to allow reuse if needed, then release.
            it.release()
            Log.d("TTSManager", "MediaPlayer stopped, reset, and released.")
        }
        mediaPlayer = null
        val outputFile = File(context.cacheDir, "output_tts.mp3")
        if (outputFile.exists()) {
            outputFile.delete()
            Log.d("TTSManager", "Cleaned up temporary TTS audio file.")
        }
        // This is crucial: If stopSpeaking is called externally, it means speech ended.
        onSpeechFinish.invoke()
    }


    fun shutdown() {
        Log.d("TTSManager", "Shutting down TextToSpeechManager...")
        stopSpeaking() // Ensure media player is released and state is correct
        ttsJob.cancel() // Cancel any ongoing coroutines
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