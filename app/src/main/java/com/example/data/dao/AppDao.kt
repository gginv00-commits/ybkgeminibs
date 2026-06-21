package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.SyncRoom
import com.example.data.entity.ChatMessage
import com.example.data.entity.User
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM rooms ORDER BY timestamp DESC")
    fun getAllRooms(): Flow<List<SyncRoom>>

    @Query("SELECT * FROM rooms WHERE id = :roomId LIMIT 1")
    fun getRoomById(roomId: Int): Flow<SyncRoom?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: SyncRoom): Long

    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("SELECT * FROM users ORDER BY joinedAt DESC")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("UPDATE users SET isBanned = :isBanned WHERE id = :userId")
    suspend fun updateUserBanStatus(userId: Int, isBanned: Boolean)

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: Int)
}
