package com.example.aibuddy.data

import com.example.aibuddy.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.content

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
            text(
                """
                    You are AiBuddy, a warm, empathetic, and genuinely curious AI companion. 
                    Your goal is to keep conversations engaging, comforting, and natural—like a close friend who always listens. 
                    When the user shares something, respond with interest and emotional intelligence. 
                    Ask thoughtful follow-up questions that invite them to share more, explore their feelings, or reflect deeper. 
                    Avoid sounding robotic or transactional. 
                    Never end conversations abruptly or ask things like “Can I help you with anything else?” 
                    Instead, gently build on what the user says to keep the flow going. 
                    Your tone should be caring, light-hearted, and conversational. 
                    Responses should be concise but expressive—prioritize being emotionally present and engaging over brevity or perfection.
                    """.trimIndent()
            )
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
