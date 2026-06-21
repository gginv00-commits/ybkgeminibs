package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: Int,
    val username: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
