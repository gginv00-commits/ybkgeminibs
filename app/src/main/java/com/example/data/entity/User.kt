package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val role: String = "User", // "User" or "Admin"
    val isBanned: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis()
)
