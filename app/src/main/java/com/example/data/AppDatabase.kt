package com.example.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.entity.SyncRoom
import com.example.data.entity.ChatMessage
import com.example.data.entity.User
import com.example.data.dao.AppDao

@Database(entities = [SyncRoom::class, ChatMessage::class, User::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
