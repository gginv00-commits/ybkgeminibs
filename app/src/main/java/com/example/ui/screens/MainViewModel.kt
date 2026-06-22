package com.example.ui.screens

import android.app.Application
import android.content.Intent
import android.os.Build
import com.example.SquadRoomService
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.FirebaseManager
import com.example.data.repository.AppRepository
import com.example.data.entity.SyncRoom
import com.example.data.entity.ChatMessage
import com.example.data.entity.User
import com.example.webrtc.WebRTCManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class VoiceMember(
    val name: String,
    val isMuted: Boolean,
    val isSpeaking: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java, "syncspace-db"
    ).fallbackToDestructiveMigration().build()

    private val repository = AppRepository(db.appDao())
    val webrtcManager = WebRTCManager(application)
    val firebaseManager = FirebaseManager.getInstance(application)

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

    private val sharedPrefs = application.getSharedPreferences("squad_prefs", android.content.Context.MODE_PRIVATE)
    val currentUserRole = MutableStateFlow("Admin") // "Admin" or "User"
    val currentUsername = MutableStateFlow(sharedPrefs.getString("saved_username", "") ?: "")
    val currentRoomCreator = MutableStateFlow<String?>(null)
    val roomNowPlaying = MutableStateFlow<String?>(null)
    val sharedIncomingMedia = MutableStateFlow<String?>(null)

    fun setSavedUsername(name: String) {
        currentUsername.value = name
        sharedPrefs.edit().putString("saved_username", name).apply()
    }

    fun handleIncomingShare(text: String) {
        val extracted = extractSharedYoutubeId(text)
        if (extracted != null) {
            val roomId = activeRoomId
            val isCreator = currentRoomCreator.value == null || currentRoomCreator.value == currentUsername.value
            if (roomId != null && isCreator) {
                updateRoomNowPlaying(roomId, extracted)
            } else {
                sharedIncomingMedia.value = extracted
            }
        }
    }

    private fun extractSharedYoutubeId(url: String): String? {
        val trimmed = url.trim()
        
        // Check for rich shared messages where url is embedded, find first http link if matches
        val linkToProcess = if (trimmed.contains("http")) {
            val extractedUrl = trimmed.substring(trimmed.indexOf("http")).split(" ", "\n").firstOrNull() ?: trimmed
            extractedUrl
        } else {
            trimmed
        }

        if (linkToProcess.length == 11) return linkToProcess

        if (linkToProcess.contains("v=")) {
            val id = linkToProcess.substringAfter("v=").substringBefore("&").substringBefore("?")
            if (id.length == 11) return id
        }
        if (linkToProcess.contains("youtu.be/")) {
            val id = linkToProcess.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
            if (id.length == 11) return id
        }
        if (linkToProcess.contains("embed/")) {
            val id = linkToProcess.substringAfter("embed/").substringBefore("?").substringBefore("/")
            if (id.length == 11) return id
        }
        if (linkToProcess.contains("shorts/")) {
            val id = linkToProcess.substringAfter("shorts/").substringBefore("?").substringBefore("/")
            if (id.length == 11) return id
        }
        if (linkToProcess.contains("live/")) {
            val id = linkToProcess.substringAfter("live/").substringBefore("?").substringBefore("/")
            if (id.length == 11) return id
        }
        if (linkToProcess.startsWith("http") && (linkToProcess.contains(".mp3") || linkToProcess.contains("audio"))) {
            val filename = linkToProcess.substringAfterLast("/").substringBefore("?").ifBlank { "Ortak Şarkı" }
            return "mp3:$filename|$linkToProcess"
        }
        return null
    }

    // Real-time synchronization state for voice users
    val activeMembers = MutableStateFlow<List<VoiceMember>>(emptyList())

    // Real-time room tracker state
    var activeRoomId: Int? = null
    var activeRoomCode: String? = null
    private var roomValueListener: ValueEventListener? = null
    private var chatValueListener: ValueEventListener? = null

    init {
        // Seed some dummy users on first start
        viewModelScope.launch {
            repository.insertUser(User(username = "Admin", role = "Admin"))
            repository.insertUser(User(username = "Alice", role = "User"))
            repository.insertUser(User(username = "Bob", role = "User"))
        }
    }

    fun createRoom(name: String, type: String = "Public", password: String? = null, isLocked: Boolean = false, onCreated: (Int) -> Unit) {
        viewModelScope.launch {
            val id = repository.createRoom(name, type, password, isLocked)
            val insertedId = id.toInt()
            
            currentRoomCreator.value = currentUsername.value
            roomNowPlaying.value = null

            val fbDb = firebaseManager.database
            if (fbDb != null) {
                val localRoom = repository.getRoomById(insertedId).first()
                if (localRoom != null) {
                    val code = localRoom.roomCode
                    val roomRef = fbDb.getReference("rooms").child(code)
                    roomRef.child("name").setValue(localRoom.name)
                    roomRef.child("roomCode").setValue(code)
                    roomRef.child("nowPlaying").setValue(null)
                    roomRef.child("timestamp").setValue(localRoom.timestamp)
                    roomRef.child("creator").setValue(currentUsername.value)
                }
            }
            onCreated(insertedId)
        }
    }

    fun joinRoomByCode(roomCode: String, onResult: (Int?) -> Unit) {
        viewModelScope.launch {
            val fbDb = firebaseManager.database
            if (fbDb != null) {
                val roomRef = fbDb.getReference("rooms").child(roomCode)
                roomRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val name = snapshot.child("name").value as? String ?: "SQUAD Room"
                        val nowPlaying = snapshot.child("nowPlaying").value as? String
                        val timestamp = snapshot.child("timestamp").value as? Long ?: System.currentTimeMillis()
                        val creator = snapshot.child("creator").value as? String
                        
                        viewModelScope.launch {
                            currentRoomCreator.value = creator
                            roomNowPlaying.value = nowPlaying
                            val localRoom = repository.getRoomByCode(roomCode)
                            if (localRoom != null) {
                                onResult(localRoom.id)
                            } else {
                                val newLocalRoom = SyncRoom(
                                    name = name,
                                    roomCode = roomCode,
                                    nowPlaying = nowPlaying,
                                    timestamp = timestamp
                                )
                                val newId = repository.insertRoomDirect(newLocalRoom)
                                onResult(newId.toInt())
                            }
                        }
                    } else {
                        viewModelScope.launch {
                            val room = repository.getRoomByCode(roomCode)
                            onResult(room?.id)
                        }
                    }
                }.addOnFailureListener {
                    viewModelScope.launch {
                        val room = repository.getRoomByCode(roomCode)
                        onResult(room?.id)
                    }
                }
            } else {
                val room = repository.getRoomByCode(roomCode)
                onResult(room?.id)
            }
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

    // --- Firebase Sync Methods ---

    fun startSyncingRoom(roomId: Int, roomCode: String) {
        stopSyncingRoom()
        activeRoomId = roomId
        activeRoomCode = roomCode

        try {
            val context = getApplication<Application>()
            val serviceIntent = Intent(context, SquadRoomService::class.java).apply {
                putExtra("roomCode", roomCode)
                putExtra("username", currentUsername.value)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to start SquadRoomService: ${e.message}")
        }

        val fbDb = firebaseManager.database
        if (fbDb != null) {
            val roomRef = fbDb.getReference("rooms").child(roomCode)

            roomValueListener = roomRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nowPlaying = snapshot.child("nowPlaying").getValue(String::class.java)
                    roomNowPlaying.value = nowPlaying
                    viewModelScope.launch {
                        repository.updateRoomNowPlaying(roomId, nowPlaying)
                    }

                    val creator = snapshot.child("creator").getValue(String::class.java)
                    currentRoomCreator.value = creator

                    val membersList = mutableListOf<VoiceMember>()
                    val membersSnapshot = snapshot.child("members")
                    for (child in membersSnapshot.children) {
                        val name = child.key ?: continue
                        val isMuted = child.child("isMuted").getValue(Boolean::class.java) ?: true
                        val isSpeaking = child.child("isSpeaking").getValue(Boolean::class.java) ?: false
                        membersList.add(VoiceMember(name, isMuted, isSpeaking))
                    }

                    val hasMe = membersList.any { it.name == currentUsername.value }
                    if (!hasMe) {
                        membersList.add(0, VoiceMember(currentUsername.value, !webrtcManager.isAudioEnabled.value, false))
                    }

                    activeMembers.value = membersList
                    val otherUsers = membersList.map { it.name }.filter { it != currentUsername.value }
                    webrtcManager.updatePeers(otherUsers, roomCode, currentUsername.value, fbDb)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("MainViewModel", "Firebase Database Error: ${error.message}")
                }
            })

            val chatRef = roomRef.child("messages")
            chatValueListener = chatRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    viewModelScope.launch {
                        for (child in snapshot.children) {
                            val username = child.child("username").getValue(String::class.java) ?: ""
                            val content = child.child("content").getValue(String::class.java) ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                            val count = repository.getMessageCount(roomId, username, content, timestamp)
                            if (count == 0) {
                                repository.insertMessageDirect(roomId, username, content, timestamp)
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })

            updateUserPresence(!webrtcManager.isAudioEnabled.value, false)
        } else {
            activeMembers.value = listOf(
                VoiceMember(currentUsername.value, !webrtcManager.isAudioEnabled.value, false),
                VoiceMember("Gizem", isMuted = false, isSpeaking = true),
                VoiceMember("Emre", isMuted = true, isSpeaking = false)
            )
        }
    }

    fun stopSyncingRoom() {
        webrtcManager.leaveRoom()
        currentRoomCreator.value = null
        roomNowPlaying.value = null

        try {
            val context = getApplication<Application>()
            val serviceIntent = Intent(context, SquadRoomService::class.java)
            context.stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to stop SquadRoomService: ${e.message}")
        }

        val code = activeRoomCode
        val fbDb = firebaseManager.database
        if (fbDb != null && code != null) {
            val roomRef = fbDb.getReference("rooms").child(code)
            
            roomRef.child("members").child(currentUsername.value).removeValue()

            roomValueListener?.let { roomRef.removeEventListener(it) }
            chatValueListener?.let { roomRef.child("messages").removeEventListener(it) }
        }

        roomValueListener = null
        chatValueListener = null
        activeRoomId = null
        activeRoomCode = null
    }

    fun updateUserPresence(isMuted: Boolean, isSpeaking: Boolean) {
        val code = activeRoomCode
        val fbDb = firebaseManager.database
        if (fbDb != null && code != null) {
            val myMemberRef = fbDb.getReference("rooms").child(code).child("members").child(currentUsername.value)
            myMemberRef.setValue(mapOf("isMuted" to isMuted, "isSpeaking" to isSpeaking))
            myMemberRef.onDisconnect().removeValue()
        } else {
            activeMembers.value = activeMembers.value.map {
                if (it.name == currentUsername.value) {
                    it.copy(isMuted = isMuted, isSpeaking = isSpeaking)
                } else {
                    it
                }
            }
        }
    }

    fun sendMessage(roomId: Int, username: String, content: String) {
        viewModelScope.launch {
            repository.sendMessage(roomId, username, content)

            val code = activeRoomCode
            val fbDb = firebaseManager.database
            if (fbDb != null && code != null) {
                val chatRef = fbDb.getReference("rooms").child(code).child("messages").push()
                chatRef.setValue(mapOf(
                    "username" to username,
                    "content" to content,
                    "timestamp" to System.currentTimeMillis()
                ))
            }
        }
    }

    fun updateRoomNowPlaying(roomId: Int, youtubeId: String?) {
        viewModelScope.launch {
            roomNowPlaying.value = youtubeId
            repository.updateRoomNowPlaying(roomId, youtubeId)

            val code = activeRoomCode
            val fbDb = firebaseManager.database
            if (fbDb != null && code != null) {
                fbDb.getReference("rooms").child(code).child("nowPlaying").setValue(youtubeId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSyncingRoom()
        webrtcManager.release()
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
