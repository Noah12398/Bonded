package com.example.bonded

import androidx.room.*

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE (sender = :user1 AND receiver = :user2) OR (sender = :user2 AND receiver = :user1)")
    suspend fun getMessagesBetween(user1: String, user2: String): List<MessageEntity>
}
