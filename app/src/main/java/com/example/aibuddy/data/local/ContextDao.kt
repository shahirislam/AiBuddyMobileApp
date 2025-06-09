package com.example.aibuddy.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserFactDao {
    @Query("SELECT * FROM user_facts")
    fun getAllUserFacts(): Flow<List<UserFact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserFact(userFact: UserFact)

    @Query("DELETE FROM user_facts WHERE id = :id")
    suspend fun deleteUserFactById(id: Int)
}

@Dao
interface ConversationTopicDao {
    @Query("SELECT * FROM conversation_topics")
    fun getAllConversationTopics(): Flow<List<ConversationTopic>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversationTopic(conversationTopic: ConversationTopic)

    @Query("DELETE FROM conversation_topics WHERE id = :id")
    suspend fun deleteConversationTopicById(id: Int)
}
