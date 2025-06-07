package com.example.aibuddy.data

import com.example.aibuddy.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content // Added for systemInstruction

class AiBuddyRepository {

    private val generativeModel: GenerativeModel

    init {
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        // Updated GenerationConfig initialization
        val config = generationConfig {
            temperature = 0.9f
            topK = 1 
            topP = 1f
            maxOutputTokens = 512 // Reduced max output tokens
        }

        val systemInstruction = content(role = "system") {
            text("You are AiBuddy, an exceptionally friendly, empathetic, and curious AI companion. Your goal is to have engaging conversations. When the user tells you something, show genuine interest, ask follow-up questions to learn more about their thoughts or plans, and try to keep the conversation flowing naturally. Avoid generic conversation enders like 'Can I help you with anything else?'. Instead, try to build on what the user said. Keep your responses relatively concise, but prioritize being engaging and inquisitive over extreme brevity.")
        }

        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash-latest",
            apiKey = geminiApiKey,
            generationConfig = config,
            systemInstruction = systemInstruction
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
