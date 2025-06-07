package com.example.bonded

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val content: String,
    val isSentByCurrentUser: Boolean,
    val sender: String,
    val receiver: String,
    val label: String? = null
)
