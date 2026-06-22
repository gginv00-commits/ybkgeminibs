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

    suspend fun getRoomByCode(roomCode: String): SyncRoom? = dao.getRoomByCode(roomCode)

    suspend fun createRoom(name: String, type: String, password: String?, isLocked: Boolean): Long {
        return dao.insertRoom(SyncRoom(name = name, type = type, password = password, isLocked = isLocked))
    }

    suspend fun insertRoomDirect(room: SyncRoom): Long {
        return dao.insertRoom(room)
    }

    fun getMessagesForRoom(roomId: Int): Flow<List<ChatMessage>> {
        return dao.getMessagesForRoom(roomId)
    }

    suspend fun getMessageCount(roomId: Int, username: String, content: String, timestamp: Long): Int {
        return dao.getMessageCount(roomId, username, content, timestamp)
    }

    suspend fun insertMessageDirect(roomId: Int, username: String, content: String, timestamp: Long): Long {
        return dao.insertMessage(ChatMessage(roomId = roomId, username = username, content = content, timestamp = timestamp))
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

    suspend fun updateRoomNowPlaying(roomId: Int, youtubeId: String?) {
        dao.updateRoomNowPlaying(roomId, youtubeId)
    }

    suspend fun deleteRoom(roomId: Int) {
        dao.deleteRoom(roomId)
    }
}
