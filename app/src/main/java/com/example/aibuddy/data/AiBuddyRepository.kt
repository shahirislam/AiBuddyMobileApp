package com.example.aibuddy.data

import android.app.Application
import com.example.aibuddy.BuildConfig
import com.example.aibuddy.data.local.AppDatabase
import com.example.aibuddy.data.local.Conversation
import com.example.aibuddy.data.local.ConversationTopic
import com.example.aibuddy.data.local.UserFact
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class AiBuddyRepository(application: Application) {

    private val generativeModel: GenerativeModel
    private val userFactDao = AppDatabase.getDatabase(application).userFactDao()
    private val conversationTopicDao = AppDatabase.getDatabase(application).conversationTopicDao()
    private val conversationDao = AppDatabase.getDatabase(application).conversationDao()

    init {
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        val config = generationConfig {
            temperature = 0.9f
            topK = 1
            topP = 1f
            maxOutputTokens = 256
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
                IMPORTANT: Keep your responses short and conversational. Aim for 1-3 sentences, and generally under 200 characters.

                If the user asks a question that you cannot answer from your own knowledge, first try to answer it. If you cannot, you can perform a web search. To do this, end your response with the following special token: `<!--SEARCH_QUERY: [your search query]-->`. For example: `I can look that up for you. <!--SEARCH_QUERY: latest news on AI-->`
                
                You have access to the user's personal information and past conversation topics. Use this context to personalize your responses. When the user asks a question, ALWAYS check the provided context first. If the answer is in the context, use it directly. Do not ask clarifying questions if the information is already available in the context.
                
                When you learn a new fact about the user (e.g., their name, interests, work), or a new conversation topic emerges, you MUST embed this information in a structured JSON format at the end of your response.
                The JSON should have two keys: "user_facts" and "conversation_topics".
                "user_facts" should be an array of objects, each with a "key" and "value".
                "conversation_topics" should be an array of objects, each with a "topic" and "keywords".
                
                Example:
                If the user says "My name is John and I like to play football", your response should be something like:
                "It's great to meet you, John! I love football too. Who's your favorite team?
                <!--JSON_START-->
                {
                  "user_facts": [
                    {"key": "name", "value": "John"},
                    {"key": "interest", "value": "football"}
                  ],
                  "conversation_topics": [
                    {"topic": "sports", "keywords": "football"}
                  ]
                }
                <!--JSON_END-->"
                
                If the JSON is not needed, do not include the JSON block.
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

    suspend fun generateContent(userMessage: String, conversationHistory: List<String>): Result<String> {
        return try {
            val userFacts = userFactDao.getAllUserFacts().first()
            val conversationTopics = conversationTopicDao.getAllConversationTopics().first()

            val context = StringBuilder()
            if (userFacts.isNotEmpty()) {
                context.append("User Facts:\n")
                userFacts.forEach { context.append("- ${it.key}: ${it.value}\n") }
            }
            if (conversationTopics.isNotEmpty()) {
                context.append("\nConversation Topics:\n")
                conversationTopics.forEach { context.append("- ${it.topic}: ${it.keywords}\n") }
            }

            val history = if (conversationHistory.isNotEmpty()) {
                "\n\nConversation History:\n${conversationHistory.joinToString("\n")}"
            } else {
                ""
            }

            val prompt = if (context.isNotEmpty() || history.isNotEmpty()) {
                "Context:\n$context$history\n\nUser Message: $userMessage"
            } else {
                userMessage
            }

            val response = generativeModel.generateContent(prompt)
            val responseText = response.text ?: "No response from AI Buddy"
            
            extractAndSaveContext(responseText)

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchAndGenerateContent(query: String, userMessage: String): Result<String> {
        return try {
            val searchResults = performSearch(query)
            val newPrompt = "Based on the following search results, answer the user's question:\n\n$searchResults\n\nUser's question: $userMessage"
            val response = generativeModel.generateContent(newPrompt)
            val responseText = response.text ?: "I couldn't find any information on that."
            
            extractAndSaveContext(responseText)

            Result.success(responseText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun performSearch(query: String): String {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.duckduckgo.com/?q=$encodedQuery&format=json")
            val result = url.readText()
            val json = JSONObject(result)
            val abstract = json.optString("AbstractText", "No summary available.")
            val results = json.optJSONArray("RelatedTopics")
            val snippets = StringBuilder()
            if (results != null) {
                for (i in 0 until minOf(3, results.length())) {
                    val item = results.getJSONObject(i)
                    snippets.append(item.optString("Text", ""))
                    snippets.append("\n")
                }
            }
            "$abstract\n\n$snippets"
        } catch (e: Exception) {
            "Error performing search: ${e.message}"
        }
    }

    private suspend fun extractAndSaveContext(responseText: String) {
        if (responseText.contains("<!--JSON_START-->")) {
            val jsonString = responseText.substringAfter("<!--JSON_START-->").substringBefore("<!--JSON_END-->").trim()
            try {
                val json = JSONObject(jsonString)
                val userFacts = json.optJSONArray("user_facts")
                if (userFacts != null) {
                    for (i in 0 until userFacts.length()) {
                        val fact = userFacts.getJSONObject(i)
                        userFactDao.insertUserFact(UserFact(key = fact.getString("key"), value = fact.getString("value")))
                    }
                }
                val conversationTopics = json.optJSONArray("conversation_topics")
                if (conversationTopics != null) {
                    for (i in 0 until conversationTopics.length()) {
                        val topic = conversationTopics.getJSONObject(i)
                        conversationTopicDao.insertConversationTopic(ConversationTopic(topic = topic.getString("topic"), keywords = topic.getString("keywords")))
                    }
                }
            } catch (e: Exception) {
                // Ignore json parsing errors
            }
        }
    }

    fun getAllUserFacts() = userFactDao.getAllUserFacts()
    fun getAllConversationTopics() = conversationTopicDao.getAllConversationTopics()
    suspend fun insertUserFact(userFact: UserFact) = userFactDao.insertUserFact(userFact)
    suspend fun deleteUserFact(id: Int) = userFactDao.deleteUserFactById(id)
    suspend fun deleteConversationTopic(id: Int) = conversationTopicDao.deleteConversationTopicById(id)

    fun getRecentConversations() = conversationDao.getRecentConversations()
    suspend fun insertConversation(conversation: Conversation) = conversationDao.insertConversation(conversation)

    suspend fun generateConversationTitle(conversationHistory: String): Result<String> {
        return try {
            val prompt = "Based on the following conversation, generate ONLY a short, concise, 2-5 word title. Do NOT include any additional text, punctuation (except apostrophes), or special characters. Provide only the title.\n\nConversation:\n$conversationHistory"

            val response = generativeModel.generateContent(prompt)
            val rawTitle = response.text ?: "New Conversation"

            val cleanedTitle = rawTitle
                .replace(Regex(".*?"), "")
                .replace("\"", "")
                .replace("Title:", "", ignoreCase = true)
                .replace(Regex("[^a-zA-Z0-9\\s']"), "")
                .trim()
            Result.success(cleanedTitle.ifBlank { "New Conversation" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
