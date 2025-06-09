package com.example.aibuddy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_topics")
data class ConversationTopic(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val topic: String,
    val keywords: String
)
