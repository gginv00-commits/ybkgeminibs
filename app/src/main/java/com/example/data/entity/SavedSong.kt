package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "saved_songs")
data class SavedSong(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val filePath: String,
    val isSystem: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
