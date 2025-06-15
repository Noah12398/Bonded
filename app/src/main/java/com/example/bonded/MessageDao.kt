package com.example.bonded

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE (sender = :user1 AND receiver = :user2) OR (sender = :user2 AND receiver = :user1)")
    suspend fun getMessagesBetween(user1: String, user2: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE content = :content AND sender = :sender AND receiver = :receiver LIMIT 1")
    suspend fun getSpecificMessage(content: String, sender: String, receiver: String): MessageEntity?

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE (sender = :user1 AND receiver = :user2) OR (sender = :user2 AND receiver = :user1) ORDER BY timestamp ASC")
    fun getMessagesBetweenLive(user1: String, user2: String): LiveData<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Int): MessageEntity?

    @Query("""
    SELECT * FROM messages 
    WHERE (sender = :user1 AND receiver = :user2) 
       OR (sender = :user2 AND receiver = :user1) 
    ORDER BY timestamp ASC
""")
    fun getMessagesBetweenFlow(user1: String, user2: String): Flow<List<MessageEntity>>

    @Query("""
    SELECT DISTINCT 
        CASE 
            WHEN sender = :username THEN receiver 
            ELSE sender 
        END AS otherUser
    FROM messages
    WHERE sender = :username OR receiver = :username
""")
    suspend fun getUsersChattedWith(username: String): List<String>

}
