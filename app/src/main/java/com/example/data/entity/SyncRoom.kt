package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "rooms")
data class SyncRoom(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String = "Public",
    val activeUsers: Int = 0,
    val nowPlaying: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
