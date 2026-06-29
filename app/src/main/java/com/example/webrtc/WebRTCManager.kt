package com.example.webrtc

import android.content.Context
import android.util.Log
import android.media.AudioManager
import android.media.AudioAttributes
import com.example.BuildConfig
import com.google.firebase.database.*
import org.webrtc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class WebRTCManager(private val context: Context) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null

    private val _isAudioEnabled = MutableStateFlow(false)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled

    private val activeSessions = ConcurrentHashMap<String, PeerSyncSession>()
    private var currentVoiceVolume: Double = 1.0

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var audioMonitorJob: Job? = null

    init {
        startAudioMonitoring()
        try {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val javaAudioDeviceModule = org.webrtc.audio.JavaAudioDeviceModule.builder(context)
                .setAudioAttributes(audioAttributes)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(javaAudioDeviceModule)
                .createPeerConnectionFactory()

            createLocalAudioTrack()
            Log.d("WebRTCManager", "WebRTC initialized successfully with custom JavaAudioDeviceModule and USAGE_VOICE_COMMUNICATION")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error initializing WebRTC: ${e.message}", e)
        }
    }

    private fun startAudioMonitoring() {
        audioMonitorJob?.cancel()
        audioMonitorJob = mainScope.launch {
            while (isActive) {
                try {
                    // Force MODE_NORMAL to allow standard background music mixing and prevent earpiece call mode ducking
                    if (audioManager.mode != AudioManager.MODE_NORMAL) {
                        audioManager.mode = AudioManager.MODE_NORMAL
                        Log.d("WebRTCManager", "Enforced audio mode: MODE_NORMAL")
                    }
                    if (!audioManager.isSpeakerphoneOn) {
                        audioManager.isSpeakerphoneOn = true
                        Log.d("WebRTCManager", "Enforced speakerphone: ON")
                    }
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "Error in audio monitoring loop: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    private fun createLocalAudioTrack() {
        try {
            val factory = peerConnectionFactory ?: return
            val audioConstraints = MediaConstraints()
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))

            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("local_audio_track", localAudioSource)
            localAudioTrack?.setEnabled(false)
            Log.d("WebRTCManager", "Local audio track created successfully")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error creating local audio track: ${e.message}", e)
        }
    }

    fun setVoiceVolume(volume: Float) {
        currentVoiceVolume = volume.toDouble()
        activeSessions.values.forEach { session ->
            session.remoteAudioTracks.forEach { track ->
                try {
                    track.setVolume(currentVoiceVolume)
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "Error setting track volume: ${e.message}")
                }
            }
        }
    }

    fun toggleMicrophone(enabled: Boolean) {
        try {
            localAudioTrack?.setEnabled(enabled)
            _isAudioEnabled.value = enabled
            Log.d("WebRTCManager", "Microphone enabled state changed to: $enabled")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error toggling microphone: ${e.message}", e)
        }
    }

    fun setMusicSharingMode(enabled: Boolean) {
        try {
            Log.d("WebRTCManager", "setMusicSharingMode: $enabled")
            val factory = peerConnectionFactory ?: return
            
            // Dispose existing track/source if any
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            
            val audioConstraints = MediaConstraints()
            if (enabled) {
                // High-fidelity music sharing mode: disable filtering
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "false"))
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
                Log.d("WebRTCManager", "Configured High-Fidelity Music Sharing audio constraints")
            } else {
                // Voice communication mode: enable filters for clear voice without echo
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                Log.d("WebRTCManager", "Configured Echo-cancelled Voice communication constraints")
            }
            
            localAudioSource = factory.createAudioSource(audioConstraints)
            localAudioTrack = factory.createAudioTrack("local_audio_track", localAudioSource)
            
            // Restore enabled status based on current _isAudioEnabled flow
            localAudioTrack?.setEnabled(_isAudioEnabled.value)
            
            // Re-add/update track in active PeerConnections
            activeSessions.values.forEach { session ->
                val pc = session.peerConnection
                try {
                    val senders = pc.senders
                    senders.forEach { sender ->
                        try {
                            if (sender.track()?.id() == "local_audio_track" || sender.track() == null) {
                                sender.setTrack(localAudioTrack, true)
                            }
                        } catch (e: Exception) {
                            Log.e("WebRTCManager", "Error setting track on sender: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "Error replacing tracks in peer connection: ${e.message}")
                }
            }
            Log.d("WebRTCManager", "Music sharing mode updated and applied to all active peer connections")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error in setMusicSharingMode: ${e.message}", e)
        }
    }

    fun updatePeers(
        otherUsers: List<String>,
        roomCode: String,
        myUsername: String,
        database: FirebaseDatabase?
    ) {
        if (database == null) {
            Log.w("WebRTCManager", "Database is null, skipping real peer connections (Simulation Mode).")
            return
        }

        try {
            val iterator = activeSessions.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val peerName = entry.key
                if (!otherUsers.contains(peerName)) {
                    entry.value.cleanup()
                    iterator.remove()
                    Log.d("WebRTCManager", "Removed and disposed peer connection for: $peerName")
                }
            }

            for (peerName in otherUsers) {
                if (!activeSessions.containsKey(peerName)) {
                    val isInitiator = myUsername < peerName
                    Log.d("WebRTCManager", "Initiating peer connection with $peerName (isInitiator=$isInitiator)")
                    val session = createPeerConnectionForPeer(peerName, isInitiator, roomCode, myUsername, database)
                    if (session != null) {
                        activeSessions[peerName] = session
                        startSignalingFlow(session)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error updating peers: ${e.message}", e)
        }
    }

    private fun createPeerConnectionForPeer(
        peerName: String,
        isInitiator: Boolean,
        roomCode: String,
        myUsername: String,
        database: FirebaseDatabase
    ): PeerSyncSession? {
        return try {
            val factory = peerConnectionFactory ?: return null

            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
            )

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            }

            val connectionKey = if (myUsername < peerName) "${myUsername}_${peerName}" else "${peerName}_${myUsername}"
            val signalingRef = database.getReference("rooms").child(roomCode).child("signaling").child(connectionKey)

            val remoteAudioTracks = mutableListOf<AudioTrack>()

            val observer = object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
                
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d("WebRTCManager", "ICE status for peer $peerName: $state")
                }
                
                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
                
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        try {
                            val candidateMap = mapOf(
                                "candidate" to candidate.sdp,
                                "sdpMid" to (candidate.sdpMid ?: ""),
                                "sdpMLineIndex" to candidate.sdpMLineIndex
                            )
                            val targetNode = if (isInitiator) "candidates_X" else "candidates_Y"
                            signalingRef.child(targetNode).push().setValue(candidateMap)
                        } catch (e: Exception) {
                            Log.e("WebRTCManager", "Error adding ICE candidate: ${e.message}", e)
                        }
                    }
                }
                
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                
                override fun onAddStream(stream: MediaStream?) {
                    Log.d("WebRTCManager", "Stream added from $peerName")
                }
                
                override fun onRemoveStream(stream: MediaStream?) {}
                
                override fun onDataChannel(channel: DataChannel?) {}
                
                override fun onRenegotiationNeeded() {}
                
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    Log.d("WebRTCManager", "Track added from $peerName: ${receiver?.track()?.kind()}")
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        try {
                            track.setVolume(currentVoiceVolume)
                            remoteAudioTracks.add(track)
                        } catch (e: Exception) {
                            Log.e("WebRTCManager", "Error setting remote track volume: ${e.message}")
                        }
                    }
                }
            }

            val pc = factory.createPeerConnection(rtcConfig, observer)
            if (pc == null) {
                Log.e("WebRTCManager", "Failed to create peer connection for $peerName")
                return null
            }

            localAudioTrack?.let { track ->
                try {
                    pc.addTrack(track, listOf("squad_audio_stream"))
                    Log.d("WebRTCManager", "Local audio track added to peer $peerName")
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "Error adding local track to peer $peerName: ${e.message}", e)
                }
            }

            PeerSyncSession(peerName, pc, isInitiator, signalingRef, remoteAudioTracks)
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error creating peer connection for $peerName: ${e.message}", e)
            null
        }
    }

    private fun startSignalingFlow(session: PeerSyncSession) {
        try {
            val pc = session.peerConnection
            val signalingRef = session.signalingRef
            val peerName = session.peerName

            val candidateNode = if (session.isInitiator) "candidates_Y" else "candidates_X"
            session.candidatesListener = signalingRef.child(candidateNode).addValueEventListener(object : ValueEventListener {
                private val processedKeys = mutableSetOf<String>()

                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        for (child in snapshot.children) {
                            val key = child.key ?: continue
                            if (processedKeys.contains(key)) continue

                            val sdp = child.child("candidate").getValue(String::class.java) 
                                ?: child.child("sdp").getValue(String::class.java) ?: continue
                            val sdpMid = child.child("sdpMid").getValue(String::class.java) ?: ""
                            val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0

                            val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                            if (session.isRemoteDescriptionSet) {
                                pc.addIceCandidate(candidate)
                                Log.d("WebRTCManager", "ICE candidate added for $peerName")
                            } else {
                                synchronized(session.queuedCandidates) {
                                    session.queuedCandidates.add(candidate)
                                }
                                Log.d("WebRTCManager", "Queued ICE candidate for $peerName (remote desc not set yet)")
                            }
                            processedKeys.add(key)
                        }
                    } catch (e: Exception) {
                        Log.e("WebRTCManager", "Error processing ICE candidates: ${e.message}", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("WebRTCManager", "Candidate listener cancelled for $peerName: ${error.message}")
                }
            })

            if (session.isInitiator) {
                signalingRef.setValue(null).addOnCompleteListener {
                    pc.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            if (desc != null) {
                                try {
                                    pc.setLocalDescription(object : SdpObserver {
                                        override fun onCreateSuccess(d: SessionDescription?) {}
                                        override fun onSetSuccess() {
                                            try {
                                                val offerData = mapOf(
                                                    "type" to "offer",
                                                    "sdp" to desc.description
                                                )
                                                signalingRef.child("offer").setValue(offerData)
                                                Log.d("WebRTCManager", "Offer sent for $peerName")
                                            } catch (e: Exception) {
                                                Log.e("WebRTCManager", "Error sending offer: ${e.message}", e)
                                            }
                                        }
                                        override fun onCreateFailure(s: String?) {
                                            Log.e("WebRTCManager", "Failed to set local offer: $s")
                                        }
                                        override fun onSetFailure(s: String?) {
                                            Log.e("WebRTCManager", "Failed to set local offer: $s")
                                        }
                                    }, desc)
                                } catch (e: Exception) {
                                    Log.e("WebRTCManager", "Error creating local description: ${e.message}", e)
                                }
                            }
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(err: String?) {
                            Log.e("WebRTCManager", "Failed to create offer for $peerName: $err")
                        }
                        override fun onSetFailure(err: String?) {
                            Log.e("WebRTCManager", "Failed to set offer: $err")
                        }
                    }, MediaConstraints())
                }

                session.descListener = signalingRef.child("answer").addValueEventListener(object : ValueEventListener {
                    private var answerSet = false
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (answerSet) return
                        try {
                            var answerSdp = snapshot.child("sdp").getValue(String::class.java)
                            if (answerSdp.isNullOrEmpty()) {
                                answerSdp = snapshot.getValue(String::class.java)
                            }
                            if (!answerSdp.isNullOrEmpty()) {
                                val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                                pc.setRemoteDescription(object : SdpObserver {
                                    override fun onCreateSuccess(d: SessionDescription?) {}
                                    override fun onSetSuccess() {
                                        answerSet = true
                                        Log.d("WebRTCManager", "Answer set successfully for $peerName")
                                        session.processQueuedCandidates()
                                    }
                                    override fun onCreateFailure(s: String?) {
                                        Log.e("WebRTCManager", "Failed to create remote description: $s")
                                    }
                                    override fun onSetFailure(s: String?) {
                                        Log.e("WebRTCManager", "Failed to set remote description: $s")
                                    }
                                }, remoteDesc)
                            }
                        } catch (e: Exception) {
                            Log.e("WebRTCManager", "Error processing answer: ${e.message}", e)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("WebRTCManager", "Answer listener cancelled: ${error.message}")
                    }
                })
            } else {
                session.descListener = signalingRef.child("offer").addValueEventListener(object : ValueEventListener {
                    private var offerSet = false
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (offerSet) return
                        try {
                            var offerSdp = snapshot.child("sdp").getValue(String::class.java)
                            if (offerSdp.isNullOrEmpty()) {
                                offerSdp = snapshot.getValue(String::class.java)
                            }
                            if (!offerSdp.isNullOrEmpty()) {
                                val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                                pc.setRemoteDescription(object : SdpObserver {
                                    override fun onCreateSuccess(d: SessionDescription?) {}
                                    override fun onSetSuccess() {
                                        offerSet = true
                                        session.processQueuedCandidates()
                                        try {
                                            pc.createAnswer(object : SdpObserver {
                                                override fun onCreateSuccess(answerDesc: SessionDescription?) {
                                                    if (answerDesc != null) {
                                                        try {
                                                            pc.setLocalDescription(object : SdpObserver {
                                                                override fun onCreateSuccess(d2: SessionDescription?) {}
                                                                override fun onSetSuccess() {
                                                                    try {
                                                                        val answerData = mapOf(
                                                                            "type" to "answer",
                                                                            "sdp" to answerDesc.description
                                                                        )
                                                                        signalingRef.child("answer").setValue(answerData)
                                                                        Log.d("WebRTCManager", "Answer sent for $peerName")
                                                                    } catch (e: Exception) {
                                                                        Log.e("WebRTCManager", "Error sending answer: ${e.message}", e)
                                                                    }
                                                                }
                                                                override fun onCreateFailure(s: String?) {
                                                                    Log.e("WebRTCManager", "Failed to create local answer: $s")
                                                                }
                                                                override fun onSetFailure(s: String?) {
                                                                    Log.e("WebRTCManager", "Failed to set local answer: $s")
                                                                }
                                                            }, answerDesc)
                                                        } catch (e: Exception) {
                                                            Log.e("WebRTCManager", "Error setting local description: ${e.message}", e)
                                                        }
                                                    }
                                                }
                                                override fun onSetSuccess() {}
                                                override fun onCreateFailure(err: String?) {
                                                    Log.e("WebRTCManager", "Failed to create answer for $peerName: $err")
                                                }
                                                override fun onSetFailure(err: String?) {
                                                    Log.e("WebRTCManager", "Failed to set answer: $err")
                                                }
                                            }, MediaConstraints())
                                        } catch (e: Exception) {
                                            Log.e("WebRTCManager", "Error creating answer: ${e.message}", e)
                                        }
                                    }
                                    override fun onCreateFailure(s: String?) {
                                        Log.e("WebRTCManager", "Failed to create remote description: $s")
                                    }
                                    override fun onSetFailure(s: String?) {
                                        Log.e("WebRTCManager", "Failed to set remote description: $s")
                                    }
                                }, remoteDesc)
                            }
                        } catch (e: Exception) {
                            Log.e("WebRTCManager", "Error processing offer: ${e.message}", e)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e("WebRTCManager", "Offer listener cancelled: ${error.message}")
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error in startSignalingFlow: ${e.message}", e)
        }
    }

    fun leaveRoom() {
        try {
            val iterator = activeSessions.values.iterator()
            while (iterator.hasNext()) {
                val session = iterator.next()
                session.cleanup()
                iterator.remove()
            }
            Log.d("WebRTCManager", "Left room, cleaned and closed all active peer connections")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error leaving room: ${e.message}", e)
        }
    }

    fun release() {
        try {
            audioMonitorJob?.cancel()
            mainScope.cancel()
            leaveRoom()
            localAudioTrack?.dispose()
            localAudioSource?.dispose()
            peerConnectionFactory?.dispose()
            PeerConnectionFactory.shutdownInternalTracer()
            Log.d("WebRTCManager", "WebRTCManager released")
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error releasing WebRTCManager: ${e.message}", e)
        }
    }
}

class PeerSyncSession(
    val peerName: String,
    val peerConnection: PeerConnection,
    val isInitiator: Boolean,
    val signalingRef: DatabaseReference,
    val remoteAudioTracks: MutableList<AudioTrack> = mutableListOf()
) {
    var descListener: ValueEventListener? = null
    var candidatesListener: ValueEventListener? = null

    var isRemoteDescriptionSet = false
    val queuedCandidates = mutableListOf<IceCandidate>()

    fun processQueuedCandidates() {
        isRemoteDescriptionSet = true
        synchronized(queuedCandidates) {
            queuedCandidates.forEach { candidate ->
                try {
                    peerConnection.addIceCandidate(candidate)
                    Log.d("WebRTCManager", "Processed queued ICE candidate for $peerName")
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "Error adding queued candidate: ${e.message}")
                }
            }
            queuedCandidates.clear()
        }
    }

    fun cleanup() {
        try {
            descListener?.let { 
                try {
                    signalingRef.removeEventListener(it)
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "Error removing desc listener: ${e.message}")
                }
            }
            candidatesListener?.let { 
                try {
                    signalingRef.removeEventListener(it)
                } catch (e: Exception) {
                    Log.e("WebRTCManager", "Error removing candidates listener: ${e.message}")
                }
            }
            try {
                peerConnection.close()
            } catch (e: Exception) {
                Log.e("WebRTCManager", "Error closing peer connection for $peerName: ${e.message}")
            }
            try {
                peerConnection.dispose()
            } catch (e: Exception) {
                Log.e("WebRTCManager", "Error disposing peer connection for $peerName: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error in cleanup for $peerName: ${e.message}", e)
        }
    }
}
