package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.repository.AppRepository
import com.example.data.entity.SyncRoom
import com.example.data.entity.ChatMessage
import com.example.data.entity.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "syncspace-db"
    ).fallbackToDestructiveMigration().build()

    private val repository = AppRepository(db.appDao())

    val rooms: StateFlow<List<SyncRoom>> = repository.allRooms.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val users: StateFlow<List<User>> = repository.allUsers.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current user context for role-based access control
    val currentUserRole = MutableStateFlow("Admin") // "Admin" or "User"

    init {
        // Seed some dummy users on first start
        viewModelScope.launch {
            repository.insertUser(User(username = "Admin", role = "Admin"))
            repository.insertUser(User(username = "Alice", role = "User"))
            repository.insertUser(User(username = "Bob", role = "User"))
        }
    }

    fun createRoom(name: String, type: String = "Public", onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val id = repository.createRoom(name, type)
            onCreated(id.toInt())
        }
    }

    fun getRoom(roomId: Int): StateFlow<SyncRoom?> {
        return repository.getRoomById(roomId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    fun getMessages(roomId: Int): StateFlow<List<ChatMessage>> {
        return repository.getMessagesForRoom(roomId).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun sendMessage(roomId: Int, username: String, content: String) {
        viewModelScope.launch {
            repository.sendMessage(roomId, username, content)
        }
    }

    // --- Admin Actions ---

    fun setBanStatus(userId: Int, isBanned: Boolean) {
        viewModelScope.launch {
            repository.setBanStatus(userId, isBanned)
        }
    }

    fun deleteRoom(roomId: Int) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
        }
    }
}
