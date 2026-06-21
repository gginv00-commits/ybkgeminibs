package com.example.data.repository

import com.example.data.dao.AppDao
import com.example.data.entity.SyncRoom
import com.example.data.entity.ChatMessage
import com.example.data.entity.User
import kotlinx.coroutines.flow.Flow

class AppRepository(private val dao: AppDao) {
    val allRooms: Flow<List<SyncRoom>> = dao.getAllRooms()
    val allUsers: Flow<List<User>> = dao.getAllUsers()

    fun getRoomById(roomId: Int): Flow<SyncRoom?> = dao.getRoomById(roomId)

    suspend fun createRoom(name: String, type: String): Long {
        return dao.insertRoom(SyncRoom(name = name, type = type))
    }

    fun getMessagesForRoom(roomId: Int): Flow<List<ChatMessage>> {
        return dao.getMessagesForRoom(roomId)
    }

    suspend fun sendMessage(roomId: Int, username: String, content: String) {
        dao.insertMessage(ChatMessage(roomId = roomId, username = username, content = content))
    }

    suspend fun insertUser(user: User): Long {
        return dao.insertUser(user)
    }

    suspend fun setBanStatus(userId: Int, isBanned: Boolean) {
        dao.updateUserBanStatus(userId, isBanned)
    }

    suspend fun deleteRoom(roomId: Int) {
        dao.deleteRoom(roomId)
    }
}
