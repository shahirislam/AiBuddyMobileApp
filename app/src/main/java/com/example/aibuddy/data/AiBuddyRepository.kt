package com.example.aibuddy.data

import com.example.aibuddy.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig // Updated import

// applicationContext is not directly needed for the new SDK initialization here
class AiBuddyRepository {

    private val generativeModel: GenerativeModel

    init {
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        // Updated GenerationConfig initialization
        val config = generationConfig {
            temperature = 0.9f
            topK = 1 // Default is 16, but keeping user's original value
            topP = 1f
            maxOutputTokens = 2048
        }

        // Updated GenerativeModel initialization
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash-latest", // Changed to a current model
            apiKey = geminiApiKey,
            generationConfig = config
        )
    }

    suspend fun generateContent(userMessage: String): Result<String> {
        return try {
            val response = generativeModel.generateContent(userMessage)
            Result.success(response.text ?: "No response from AI Buddy")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
