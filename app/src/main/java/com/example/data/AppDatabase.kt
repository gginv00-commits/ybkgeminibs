package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.entity.SyncRoom
import com.example.data.entity.ChatMessage
import com.example.data.entity.User
import com.example.data.dao.AppDao

import com.example.data.entity.SavedSong

@Database(entities = [SyncRoom::class, ChatMessage::class, User::class, SavedSong::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
