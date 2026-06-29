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
import com.example.data.entity.SavedSong
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

    val savedSongs: StateFlow<List<SavedSong>> = repository.allSavedSongs.stateIn(
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
    val playbackState = MutableStateFlow<String>("PLAYING")
    val playbackTime = MutableStateFlow<Double>(0.0)
    val playbackDuration = MutableStateFlow<Double>(0.0)
    val playbackVersion = MutableStateFlow<Long>(0L)

    private var lastSentState: String? = null
    private var lastSentTime: Double? = null
    private var lastSentSystemTime: Long? = null

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
    val kickedOrBannedEvent = MutableStateFlow<String?>(null)

    // Real-time room tracker state
    var activeRoomId: Int? = null
    var activeRoomCode: String? = null
    private var roomValueListener: ValueEventListener? = null
    private var chatValueListener: ValueEventListener? = null
    private var sharedMp3Listener: ValueEventListener? = null

    private var serverTimeOffset: Long = 0

    fun getEstimatedServerTime(): Long {
        return System.currentTimeMillis() + serverTimeOffset
    }

    init {
        val offsetRef = firebaseManager.database?.getReference(".info/serverTimeOffset")
        offsetRef?.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                serverTimeOffset = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        })

        // Seed some dummy users on first start
        viewModelScope.launch {
            repository.insertUser(User(username = "Admin", role = "Admin"))
            repository.insertUser(User(username = "Alice", role = "User"))
            repository.insertUser(User(username = "Bob", role = "User"))

            try {
                val existingSongs = repository.allSavedSongs.first()
                if (existingSongs.isEmpty()) {
                    val defaults = listOf(
                        SavedSong(title = "Lofi Hip Hop Room Mix", artist = "Chillhop Beats", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", isSystem = true),
                        SavedSong(title = "Neon Waves (Retro Synth)", artist = "Wave Runner", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", isSystem = true),
                        SavedSong(title = "Acoustic Sunsets", artist = "Sona Guitar", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", isSystem = true),
                        SavedSong(title = "Cyberpunk Underground", artist = "Hack Operator", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", isSystem = true),
                        SavedSong(title = "Ege Rüzgarları Lofi Beat", artist = "Efe Beats", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", isSystem = true),
                        SavedSong(title = "Anatolian Ambient Fusion", artist = "Saz Chillout", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3", isSystem = true),
                        SavedSong(title = "Retro Arcade Focus", artist = "Pixel Boy", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3", isSystem = true),
                        SavedSong(title = "Cosmic Silence Ambient", artist = "Galaxy Pulse", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3", isSystem = true),
                        SavedSong(title = "Istanbul Street Jazz Beat", artist = "Taksim Trio (Remix)", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3", isSystem = true),
                        SavedSong(title = "Deep Night Meditation", artist = "Yogi Flow", filePath = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3", isSystem = true)
                    )
                    defaults.forEach { repository.insertSavedSong(it) }
                } else {
                    // Cleanup any blank or empty songs that exist in the database
                    existingSongs.forEach { song ->
                        if (song.title.isBlank() || song.artist.isBlank() || song.filePath.isBlank()) {
                            repository.deleteSavedSong(song.id)
                            Log.d("MainViewModel", "Deleted empty/blank song on startup: ${song.id}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error seeding startup default songs or cleaning up empty songs: ${e.message}", e)
            }
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

    fun joinRoomByCode(roomCode: String, onResult: (Int?, String?) -> Unit) {
        viewModelScope.launch {
            val fbDb = firebaseManager.database
            if (fbDb != null) {
                val roomRef = fbDb.getReference("rooms").child(roomCode)
                roomRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val bannedSnapshot = snapshot.child("banned_users").child(currentUsername.value)
                        val isMeBanned = bannedSnapshot.exists() && bannedSnapshot.getValue(Boolean::class.java) == true
                        if (isMeBanned) {
                            onResult(null, "Bu odadan yasaklandınız!")
                            return@addOnSuccessListener
                        }

                        val name = snapshot.child("name").value as? String ?: "SQUAD Room"
                        val nowPlaying = snapshot.child("nowPlaying").value as? String
                        val timestamp = snapshot.child("timestamp").value as? Long ?: System.currentTimeMillis()
                        val creator = snapshot.child("creator").value as? String
                        
                        viewModelScope.launch {
                            currentRoomCreator.value = creator
                            roomNowPlaying.value = nowPlaying
                            val localRoom = repository.getRoomByCode(roomCode)
                            if (localRoom != null) {
                                onResult(localRoom.id, null)
                            } else {
                                val newLocalRoom = SyncRoom(
                                    name = name,
                                    roomCode = roomCode,
                                    nowPlaying = nowPlaying,
                                    timestamp = timestamp
                                )
                                val newId = repository.insertRoomDirect(newLocalRoom)
                                onResult(newId.toInt(), null)
                            }
                        }
                    } else {
                        viewModelScope.launch {
                            val room = repository.getRoomByCode(roomCode)
                            if (room != null) {
                                onResult(room.id, null)
                            } else {
                                onResult(null, "Yanlış kod, oda bulunamadı.")
                            }
                        }
                    }
                }.addOnFailureListener {
                    viewModelScope.launch {
                        val room = repository.getRoomByCode(roomCode)
                        if (room != null) {
                            onResult(room.id, null)
                        } else {
                            onResult(null, "Yanlış kod, oda bulunamadı.")
                        }
                    }
                }
            } else {
                val room = repository.getRoomByCode(roomCode)
                if (room != null) {
                    onResult(room.id, null)
                } else {
                    onResult(null, "Bağlantı hatası, oda bulunamadı.")
                }
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
                putExtra("isMuted", !webrtcManager.isAudioEnabled.value)
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
                    val kickedUsersSnapshot = snapshot.child("kicked_users")
                    val isMeKicked = kickedUsersSnapshot.child(currentUsername.value).getValue(Boolean::class.java) ?: false

                    val bannedUsersSnapshot = snapshot.child("banned_users")
                    val isMeBanned = bannedUsersSnapshot.child(currentUsername.value).getValue(Boolean::class.java) ?: false

                    if (isMeKicked || isMeBanned) {
                        kickedOrBannedEvent.value = if (isMeBanned) "BAN" else "KICK"
                        return
                    }

                    val nowPlaying = snapshot.child("nowPlaying").getValue(String::class.java)
                    roomNowPlaying.value = nowPlaying
                    viewModelScope.launch {
                        repository.updateRoomNowPlaying(roomId, nowPlaying)
                    }

                    val creator = snapshot.child("creator").getValue(String::class.java)
                    currentRoomCreator.value = creator

                    val playbackSnapshot = snapshot.child("playback")
                    if (playbackSnapshot.exists()) {
                        playbackState.value = playbackSnapshot.child("state").getValue(String::class.java) ?: "PLAYING"
                        playbackTime.value = playbackSnapshot.child("time").getValue(Double::class.java) ?: 0.0
                        playbackDuration.value = playbackSnapshot.child("duration").getValue(Double::class.java) ?: 0.0
                        playbackVersion.value = playbackSnapshot.child("version").getValue(Long::class.java) ?: 0L
                    }

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

            val sharedMp3Ref = roomRef.child("shared_mp3_data")
            sharedMp3Listener = sharedMp3Ref.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val title = snapshot.child("title").getValue(String::class.java) ?: ""
                        val artist = snapshot.child("artist").getValue(String::class.java) ?: ""
                        val base64 = snapshot.child("base64").getValue(String::class.java)
                        if (!title.isNullOrBlank() && !base64.isNullOrBlank()) {
                            viewModelScope.launch {
                                try {
                                    val context = getApplication<Application>()
                                    val songsDir = java.io.File(context.filesDir, "squad_songs")
                                    if (!songsDir.exists()) songsDir.mkdirs()
                                    val safeName = title.lowercase().replace(Regex("[^a-z0-9]"), "_") + ".mp3"
                                    val destinationFile = java.io.File(songsDir, safeName)
                                    if (!destinationFile.exists()) {
                                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                                        destinationFile.writeBytes(bytes)
                                        
                                        val newSong = SavedSong(
                                            title = title,
                                            artist = artist,
                                            filePath = destinationFile.absolutePath,
                                            isSystem = false
                                        )
                                        repository.insertSavedSong(newSong)
                                        Log.d("MainViewModel", "Downloaded shared song locally: ${destinationFile.absolutePath}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainViewModel", "Error saving synced shared song: ${e.message}", e)
                                }
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
            roomRef.child("kicked_users").child(currentUsername.value).removeValue()

            roomValueListener?.let { roomRef.removeEventListener(it) }
            chatValueListener?.let { roomRef.child("messages").removeEventListener(it) }
            sharedMp3Listener?.let { roomRef.child("shared_mp3_data").removeEventListener(it) }
        }

        roomValueListener = null
        chatValueListener = null
        sharedMp3Listener = null
        activeRoomId = null
        activeRoomCode = null
    }

    fun kickUser(roomCode: String, username: String) {
        val fbDb = firebaseManager.database ?: return
        fbDb.getReference("rooms").child(roomCode).child("kicked_users").child(username).setValue(true)
        fbDb.getReference("rooms").child(roomCode).child("members").child(username).removeValue()
    }

    fun banUser(roomCode: String, username: String) {
        val fbDb = firebaseManager.database ?: return
        fbDb.getReference("rooms").child(roomCode).child("banned_users").child(username).setValue(true)
        kickUser(roomCode, username)
    }

    fun updateUserPresence(isMuted: Boolean, isSpeaking: Boolean) {
        val code = activeRoomCode
        val fbDb = firebaseManager.database
        if (fbDb != null && code != null) {
            val myMemberRef = fbDb.getReference("rooms").child(code).child("members").child(currentUsername.value)
            val updates = mapOf(
                "isMuted" to isMuted,
                "isSpeaking" to isSpeaking,
                "name" to currentUsername.value
            )
            myMemberRef.updateChildren(updates)
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
                // Reset playback state for new media
                val now = System.currentTimeMillis()
                lastSentState = "PLAYING"
                lastSentTime = 0.0
                lastSentSystemTime = now

                val playRef = fbDb.getReference("rooms").child(code).child("playback")
                playRef.setValue(mapOf(
                    "state" to "PLAYING",
                    "time" to 0.0,
                    "duration" to 0.0,
                    "version" to com.google.firebase.database.ServerValue.TIMESTAMP
                ))
            }
        }
    }

    fun updatePlaybackState(state: String, time: Double, duration: Double = 0.0) {
        val now = getEstimatedServerTime()
        val prevS = lastSentState
        val prevT = lastSentTime
        val prevSysT = lastSentSystemTime

        if (prevS == state && prevT != null && prevSysT != null) {
            val elapsedWriteTime = now - prevSysT
            if (state == "PLAYING" && elapsedWriteTime < 4000) {
                val expectedProgress = prevT + elapsedWriteTime / 1000.0
                // If actual media time is within 0.5 seconds of expected progress, do NOT rewrite to DB.
                if (Math.abs(time - expectedProgress) < 0.5) {
                    return
                }
            } else if (state == "PAUSED") {
                // If already paused, skip if pausing at roughly same time
                if (Math.abs(time - prevT) < 0.5) {
                    return
                }
            }
        }

        lastSentState = state
        lastSentTime = time
        lastSentSystemTime = now

        val code = activeRoomCode
        val fbDb = firebaseManager.database
        if (fbDb != null && code != null) {
            val playRef = fbDb.getReference("rooms").child(code).child("playback")
            playRef.setValue(mapOf(
                "state" to state,
                "time" to time,
                "duration" to duration,
                "version" to com.google.firebase.database.ServerValue.TIMESTAMP
            ))
        }
    }

    fun saveSong(title: String, artist: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val songsDir = java.io.File(context.filesDir, "squad_songs")
                if (!songsDir.exists()) {
                    songsDir.mkdirs()
                }
                val safeName = title.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + System.currentTimeMillis() + ".mp3"
                val file = java.io.File(songsDir, safeName)
                file.writeBytes(fileBytes)
                
                val newSong = SavedSong(
                    title = title,
                    artist = artist,
                    filePath = file.absolutePath
                )
                repository.insertSavedSong(newSong)
            } catch (e: java.lang.Exception) {
                Log.e("MainViewModel", "Error saving song bytes: ${e.message}", e)
            }
        }
    }

    fun saveSongFromUri(title: String, artist: String, uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val songsDir = java.io.File(context.filesDir, "squad_songs")
                if (!songsDir.exists()) {
                    songsDir.mkdirs()
                }
                val safeName = title.lowercase().replace(Regex("[^a-z0-9]"), "_") + "_" + System.currentTimeMillis() + ".mp3"
                val file = java.io.File(songsDir, safeName)
                
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                val newSong = SavedSong(
                    title = title,
                    artist = artist,
                    filePath = file.absolutePath
                )
                repository.insertSavedSong(newSong)
                Log.d("MainViewModel", "Saved song from SAF picker: ${file.absolutePath}")
            } catch (e: java.lang.Exception) {
                Log.e("MainViewModel", "Error saving song from Uri: ${e.message}", e)
            }
        }
    }

    fun shareLocalMp3File(roomCode: String, title: String, artist: String, filePath: String) {
        viewModelScope.launch {
            try {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    if (bytes.size > 14 * 1024 * 1024) {
                        Log.w("MainViewModel", "MP3 file too large to write in single Firebase string (>14MB)")
                        return@launch
                    }
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
                    
                    val fbDb = firebaseManager.database
                    if (fbDb != null) {
                        val ref = fbDb.getReference("rooms").child(roomCode).child("shared_mp3_data")
                        val metaMap = mapOf(
                            "title" to title,
                            "artist" to artist,
                            "base64" to base64,
                            "timestamp" to System.currentTimeMillis()
                        )
                        ref.setValue(metaMap)
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.e("MainViewModel", "Error sharing local mp3: ${e.message}", e)
            }
        }
    }

    fun deleteSavedSong(songId: Int) {
        viewModelScope.launch {
            try {
                repository.deleteSavedSong(songId)
            } catch (e: java.lang.Exception) {
                Log.e("MainViewModel", "Error deleting saved song: ${e.message}", e)
            }
        }
    }

    fun downloadAndSaveDefaultSong(song: SavedSong, onResult: (String?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val songsDir = java.io.File(context.filesDir, "squad_songs")
                if (!songsDir.exists()) songsDir.mkdirs()
                
                val safeName = song.title.lowercase().replace(Regex("[^a-z0-9]"), "_") + ".mp3"
                val file = java.io.File(songsDir, safeName)
                
                if (!file.exists()) {
                    val url = java.net.URL(song.filePath)
                    val conn = url.openConnection()
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    val bytes = conn.getInputStream().use { it.readBytes() }
                    file.writeBytes(bytes)
                }
                
                val updatedSong = song.copy(filePath = file.absolutePath)
                repository.insertSavedSong(updatedSong)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(file.absolutePath)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error downloading default song: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(null)
                }
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
