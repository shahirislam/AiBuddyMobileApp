package com.example.aibuddy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val timestamp: Long,
    val durationInMinutes: Int
)
