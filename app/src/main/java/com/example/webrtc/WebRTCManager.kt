package com.example.webrtc

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.database.*
import org.webrtc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

class WebRTCManager(private val context: Context) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localAudioTrack: AudioTrack? = null
    private var localAudioSource: AudioSource? = null

    private val _isAudioEnabled = MutableStateFlow(false)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled

    // Store active WebRTC sessions for each peer in the room
    private val activeSessions = ConcurrentHashMap<String, PeerSyncSession>()

    init {
        // Initialize WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        createLocalAudioTrack()
    }

    private fun createLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return

        // Create AudioConstraints
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))

        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("local_audio_track", localAudioSource)
        localAudioTrack?.setEnabled(false) // Default to muted
    }

    fun toggleMicrophone(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
        _isAudioEnabled.value = enabled
        Log.d("WebRTCManager", "Microphone enabled state changed to: $enabled")
    }

    /**
     * Periodically synced by MainViewModel whenever the room's members list updates.
     */
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

        // 1. Remove/Dispose connection for any user that left the room
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

        // 2. Add/Establish connection for any new user that joined
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
    }

    private fun createPeerConnectionForPeer(
        peerName: String,
        isInitiator: Boolean,
        roomCode: String,
        myUsername: String,
        database: FirebaseDatabase
    ): PeerSyncSession? {
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

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d("WebRTCManager", "ICE status for peer $peerName: $state")
            }
            
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val candidateMap = mapOf(
                        "sdp" to candidate.sdp,
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex
                    )
                    val targetNode = if (isInitiator) "candidates_X" else "candidates_Y"
                    signalingRef.child(targetNode).push().setValue(candidateMap)
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
            }
        }

        val pc = factory.createPeerConnection(rtcConfig, observer) ?: return null

        // Add local track to this peer connection so they can hear us
        localAudioTrack?.let { track ->
            pc.addTrack(track, listOf("squad_audio_stream"))
        }

        return PeerSyncSession(peerName, pc, isInitiator, signalingRef)
    }

    private fun startSignalingFlow(session: PeerSyncSession) {
        val pc = session.peerConnection
        val signalingRef = session.signalingRef
        val peerName = session.peerName

        // Listen for candidates of the opposition
        val candidateNode = if (session.isInitiator) "candidates_Y" else "candidates_X"
        session.candidatesListener = signalingRef.child(candidateNode).addValueEventListener(object : ValueEventListener {
            private val processedKeys = mutableSetOf<String>()

            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    if (processedKeys.contains(key)) continue

                    val sdp = child.child("sdp").getValue(String::class.java) ?: continue
                    val sdpMid = child.child("sdpMid").getValue(String::class.java) ?: continue
                    val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: continue

                    val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                    pc.addIceCandidate(candidate)
                    processedKeys.add(key)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        if (session.isInitiator) {
            // Offerer: Generate, apply, and publish offer
            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    if (desc != null) {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(d: SessionDescription?) {}
                            override fun onSetSuccess() {
                                signalingRef.child("offer").setValue(desc.description)
                            }
                            override fun onCreateFailure(s: String?) {}
                            override fun onSetFailure(s: String?) {}
                        }, desc)
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(err: String?) {
                    Log.e("WebRTCManager", "Failed to create offer for $peerName: $err")
                }
                override fun onSetFailure(err: String?) {}
            }, MediaConstraints())

            // Listen for responder's answer
            session.descListener = signalingRef.child("answer").addValueEventListener(object : ValueEventListener {
                private var answerSet = false
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (answerSet) return
                    val answerSdp = snapshot.getValue(String::class.java)
                    if (!answerSdp.isNullOrEmpty()) {
                        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                        pc.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(d: SessionDescription?) {}
                            override fun onSetSuccess() {
                                answerSet = true
                                Log.d("WebRTCManager", "Answer set successfully for $peerName")
                            }
                            override fun onCreateFailure(s: String?) {}
                            override fun onSetFailure(s: String?) {}
                        }, remoteDesc)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            // Responder: Listen for initiator's offer, apply, generate answer, apply, and publish
            session.descListener = signalingRef.child("offer").addValueEventListener(object : ValueEventListener {
                private var offerSet = false
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (offerSet) return
                    val offerSdp = snapshot.getValue(String::class.java)
                    if (!offerSdp.isNullOrEmpty()) {
                        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                        pc.setRemoteDescription(object : SdpObserver {
                            override fun onCreateSuccess(d: SessionDescription?) {}
                            override fun onSetSuccess() {
                                offerSet = true
                                pc.createAnswer(object : SdpObserver {
                                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                                        if (answerDesc != null) {
                                            pc.setLocalDescription(object : SdpObserver {
                                                override fun onCreateSuccess(d2: SessionDescription?) {}
                                                override fun onSetSuccess() {
                                                    signalingRef.child("answer").setValue(answerDesc.description)
                                                }
                                                override fun onCreateFailure(s: String?) {}
                                                override fun onSetFailure(s: String?) {}
                                            }, answerDesc)
                                        }
                                    }
                                    override fun onSetSuccess() {}
                                    override fun onCreateFailure(err: String?) {
                                        Log.e("WebRTCManager", "Failed to create answer for $peerName: $err")
                                    }
                                    override fun onSetFailure(err: String?) {}
                                }, MediaConstraints())
                            }
                            override fun onCreateFailure(s: String?) {}
                            override fun onSetFailure(s: String?) {
                                Log.e("WebRTCManager", "Failed to set remote offer for $peerName: $s")
                            }
                        }, remoteDesc)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    fun leaveRoom() {
        val iterator = activeSessions.values.iterator()
        while (iterator.hasNext()) {
            val session = iterator.next()
            session.cleanup()
            iterator.remove()
        }
        Log.d("WebRTCManager", "Left room, cleaned and closed all active peer connections")
    }

    fun release() {
        leaveRoom()
        localAudioTrack?.dispose()
        localAudioSource?.dispose()
        peerConnectionFactory?.dispose()
        PeerConnectionFactory.shutdownInternalTracer()
    }
}

class PeerSyncSession(
    val peerName: String,
    val peerConnection: PeerConnection,
    val isInitiator: Boolean,
    val signalingRef: DatabaseReference
) {
    var descListener: ValueEventListener? = null
    var candidatesListener: ValueEventListener? = null

    fun cleanup() {
        descListener?.let { signalingRef.removeEventListener(it) }
        candidatesListener?.let { signalingRef.removeEventListener(it) }
        try {
            peerConnection.close()
            peerConnection.dispose()
        } catch (e: Exception) {
            Log.e("WebRTCManager", "Error disposing connection for $peerName: ${e.message}")
        }
    }
}
