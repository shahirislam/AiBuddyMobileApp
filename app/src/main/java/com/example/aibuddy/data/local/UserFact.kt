package com.example.aibuddy.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_facts")
data class UserFact(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val key: String,
    val value: String
)
