import React, { useState, useEffect, useRef } from 'react';
import { initializeApp } from 'firebase/app';
import { 
    getDatabase, 
    ref, 
    onValue, 
    set, 
    push, 
    onDisconnect, 
    get, 
    off, 
    child 
} from 'firebase/database';

// --- FIREBASE CONFIGURATION (PRESERVED) ---
const firebaseConfig = {
    apiKey: "AIzaSyAE8n1lkX48l78QJar5gJHsDmHcPMpleJI",
    authDomain: "ybkvc-4fda4.firebaseapp.com",
    databaseURL: "https://ybkvc-4fda4-default-rtdb.firebaseio.com",
    projectId: "ybkvc-4fda4",
    storageBucket: "ybkvc-4fda4.appspot.com",
    messagingSenderId: "312176106940",
    appId: "1:312176106940:web:e5e937d9a138256449dfb9"
};

// Initialize Firebase App and Database
const app = initializeApp(firebaseConfig);
const db = getDatabase(app);

// Declare global YouTube variables for TS
declare global {
    interface Window {
        onYouTubeIframeAPIReady: () => void;
        YT: any;
    }
}

interface Member {
    name: string;
    isMuted: boolean;
    isSpeaking: boolean;
}

interface Playback {
    state: string;
    time: number;
    version: number;
}

interface PeerSync {
    peerConnection: RTCPeerConnection;
    signalingRef: any;
    isInitiator: boolean;
    remoteDescriptionSet: boolean;
    queuedCandidates: RTCIceCandidate[];
    candidateListener?: any;
    descListener?: any;
    remoteAudioElement?: HTMLAudioElement;
}

export default function App() {
    // UI Navigation State
    const [inRoom, setInRoom] = useState(false);
    const [firebaseConnected, setFirebaseConnected] = useState(false);
    const [toast, setToast] = useState<{ message: string; isError: boolean; visible: boolean }>({
        message: "",
        isError: false,
        visible: false
    });

    // Inputs
    const [username, setUsername] = useState("");
    const [roomCodeInput, setRoomCodeInput] = useState("");

    // Active Room States
    const [activeRoomCode, setActiveRoomCode] = useState("");
    const [members, setMembers] = useState<{ [key: string]: Member }>({});
    const [nowPlaying, setNowPlaying] = useState("");
    const [playback, setPlayback] = useState<Playback | null>(null);
    const [remoteStreams, setRemoteStreams] = useState<{ [username: string]: MediaStream }>({});

    // Audio / Voice Streaming States
    const [isMuted, setIsMuted] = useState(true);
    const [isPttMode, setIsPttMode] = useState(false);
    const [isSpacePressed, setIsSpacePressed] = useState(false);

    // Volume Mixers
    const [mediaVolume, setMediaVolume] = useState(100);
    const [voiceVolume, setVoiceVolume] = useState(100); // represents 0% to 200% (0.0 to 2.0)

    // Refs
    const localStreamRef = useRef<MediaStream | null>(null);
    const audioContextRef = useRef<AudioContext | null>(null);
    const analyserIntervalRef = useRef<any>(null);
    const ytPlayerRef = useRef<any>(null);
    const activePeersRef = useRef<{ [key: string]: PeerSync }>({});
    
    // Track dynamic changes of volume in ref so WebRTC tracks can read it instantly
    const voiceVolumeRef = useRef<number>(1.0);

    // Toast manager helper
    const showToast = (message: string, isError = false) => {
        setToast({ message, isError, visible: true });
    };

    useEffect(() => {
        if (toast.visible) {
            const timer = setTimeout(() => {
                setToast(prev => ({ ...prev, visible: false }));
            }, 3500);
            return () => clearTimeout(timer);
        }
    }, [toast.visible]);

    // Check Firebase connection status
    useEffect(() => {
        const connectedRef = ref(db, '.info/connected');
        const unsubscribe = onValue(connectedRef, (snap) => {
            setFirebaseConnected(!!snap.val());
        });
        return () => unsubscribe();
    }, []);

    // Spacebar listener for Push-to-Talk (PTT)
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (!inRoom || !isPttMode) return;
            if (e.code === "Space") {
                e.preventDefault();
                if (!isSpacePressed) {
                    setIsSpacePressed(true);
                }
            }
        };

        const handleKeyUp = (e: KeyboardEvent) => {
            if (!inRoom || !isPttMode) return;
            if (e.code === "Space") {
                e.preventDefault();
                setIsSpacePressed(false);
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        window.addEventListener('keyup', handleKeyUp);
        return () => {
            window.removeEventListener('keydown', handleKeyDown);
            window.removeEventListener('keyup', handleKeyUp);
        };
    }, [inRoom, isPttMode, isSpacePressed]);

    // Synchronize local microphone track on isMuted changes
    useEffect(() => {
        const stream = localStreamRef.current;
        if (stream) {
            stream.getAudioTracks().forEach(track => {
                track.enabled = !isMuted;
            });
        }
        
        // Push mute state to Firebase
        if (inRoom && activeRoomCode && username) {
            const myMemberRef = ref(db, `rooms/${activeRoomCode}/members/${username}`);
            set(child(myMemberRef, 'isMuted'), isMuted);
        }
    }, [isMuted, inRoom, activeRoomCode, username]);

    // Push-to-Talk logic effect: when Space is pressed, unmute. When released, mute.
    useEffect(() => {
        if (!isPttMode) return;
        setIsMuted(!isSpacePressed);
    }, [isSpacePressed, isPttMode]);

    // Update voice volumes dynamically when slider changes
    useEffect(() => {
        const factor = voiceVolume / 100.0;
        voiceVolumeRef.current = factor;
        // Apply volume to all currently playing audio sink elements
        Object.keys(remoteStreams).forEach(peerName => {
            const audioEl = document.getElementById(`audio_sink_${peerName}`) as HTMLAudioElement;
            if (audioEl) {
                audioEl.volume = factor;
            }
        });
    }, [voiceVolume, remoteStreams]);

    // Setup YouTube IFrame API or bind target
    useEffect(() => {
        if (inRoom) {
            const initPlayer = () => {
                if (window.YT && window.YT.Player) {
                    try {
                        ytPlayerRef.current = new window.YT.Player('ytPlayerTarget', {
                            height: '100%',
                            width: '100%',
                            videoId: nowPlaying || undefined,
                            playerVars: {
                                playsinline: 1,
                                controls: 0,
                                disablekb: 1,
                                rel: 0,
                                origin: window.location.origin
                            },
                            events: {
                                onReady: (event: any) => {
                                    event.target.setVolume(mediaVolume);
                                    if (nowPlaying) {
                                        event.target.loadVideoById(nowPlaying);
                                    }
                                }
                            }
                        });
                        console.log("YouTube Player initialized successfully.");
                    } catch (e) {
                        console.error("Error creating YouTube player instance", e);
                    }
                }
            };

            // If YT is already loaded in global window
            if (window.YT && window.YT.Player) {
                initPlayer();
            } else {
                window.onYouTubeIframeAPIReady = () => {
                    initPlayer();
                };
            }
        } else {
            // Cleanup player on leaving
            if (ytPlayerRef.current) {
                try {
                    ytPlayerRef.current.destroy();
                } catch (e) {}
                ytPlayerRef.current = null;
            }
        }
    }, [inRoom]);

    // Apply YouTube volume changes instantly
    useEffect(() => {
        if (ytPlayerRef.current && ytPlayerRef.current.setVolume) {
            ytPlayerRef.current.setVolume(mediaVolume);
        }
    }, [mediaVolume]);

    // Generate random room code
    const handleCreateRoom = async () => {
        const formattedName = username.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '');
        if (!formattedName) {
            showToast("Lütfen sadece İngilizce karakter, sayı veya alt çizgi içeren bir kullanıcı adı girin.", true);
            return;
        }

        const chars = "0123456789";
        let newCode = "";
        for (let i = 0; i < 5; i++) {
            newCode += chars.charAt(Math.floor(Math.random() * chars.length));
        }

        setUsername(formattedName);
        await enterRoom(newCode, formattedName, true);
    };

    // Join room with 5-digit code
    const handleJoinRoom = async () => {
        const formattedName = username.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '');
        const code = roomCodeInput.trim();

        if (!formattedName) {
            showToast("Lütfen sadece İngilizce karakter, sayı veya alt çizgi içeren bir kullanıcı adı girin.", true);
            return;
        }
        if (!code || code.length !== 5 || !/^\d+$/.test(code)) {
            showToast("Lütfen 5 haneli sayısal bir oda kodu girin.", true);
            return;
        }

        setUsername(formattedName);
        
        // Check if room exists first
        const roomRef = ref(db, `rooms/${code}`);
        try {
            const snap = await get(roomRef);
            if (!snap.exists()) {
                showToast("Oda bulunamadı! Lütfen geçerli bir oda kodu girin.", true);
                return;
            }
            await enterRoom(code, formattedName, false);
        } catch (err: any) {
            showToast("Oda kontrolü başarısız: " + err.message, true);
        }
    };

    // Enter room flow
    const enterRoom = async (code: string, myName: string, isCreator: boolean) => {
        showToast("Odaya bağlanılıyor...");

        // 1. Get microphone access
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    echoCancellation: true,
                    noiseSuppression: true,
                    autoGainControl: true
                },
                video: false
            });
            localStreamRef.current = stream;
            stream.getAudioTracks().forEach(track => {
                track.enabled = !isMuted;
            });
            console.log("Local microphone stream fetched.");
        } catch (err) {
            console.warn("Mikrofon izni reddedildi. Dinleyici olarak giriliyor.", err);
            showToast("Mikrofon bulunamadı. Sadece dinleyici modundasınız.");
            localStreamRef.current = null;
        }

        // 2. Setup Firebase state inside room
        const roomRef = ref(db, `rooms/${code}`);
        if (isCreator) {
            await set(child(roomRef, 'nowPlaying'), "");
            await set(child(roomRef, 'playback'), {
                state: "PAUSED",
                time: 0,
                version: 1
            });
        }

        // Write own presence record
        const myMemberRef = ref(db, `rooms/${code}/members/${myName}`);
        await set(myMemberRef, {
            name: myName,
            isMuted: isMuted,
            isSpeaking: false
        });

        // Set onDisconnect handler
        onDisconnect(myMemberRef).remove();

        setActiveRoomCode(code);
        setInRoom(true);
        showToast("Bağlantı kuruldu! Odaya girildi.");

        // Initialize audio visualizer for detecting speaking threshold
        setupSpeakingVisualizer(code, myName);

        // Listen to room dynamics
        listenToRoomChanges(code, myName);
    };

    // Setup local mic amplitude visualizer
    const setupSpeakingVisualizer = (code: string, myName: string) => {
        const stream = localStreamRef.current;
        if (!stream) return;

        try {
            const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
            const audioContext = new AudioCtx();
            audioContextRef.current = audioContext;

            const analyser = audioContext.createAnalyser();
            const source = audioContext.createMediaStreamSource(stream);
            source.connect(analyser);
            analyser.fftSize = 32;

            const bufferLength = analyser.frequencyBinCount;
            const dataArray = new Uint8Array(bufferLength);

            analyserIntervalRef.current = setInterval(() => {
                // If muted locally, always set isSpeaking to false
                if (localStreamRef.current && localStreamRef.current.getAudioTracks()[0]?.enabled === false) {
                    set(ref(db, `rooms/${code}/members/${myName}/isSpeaking`), false);
                    return;
                }

                analyser.getByteFrequencyData(dataArray);
                let sum = 0;
                for (let i = 0; i < bufferLength; i++) {
                    sum += dataArray[i];
                }
                const average = sum / bufferLength;
                const isSpeaking = average > 25; // speaking amplitude threshold

                set(ref(db, `rooms/${code}/members/${myName}/isSpeaking`), isSpeaking);
            }, 180);
        } catch (e) {
            console.error("Failed to start speaking detector analyzer", e);
        }
    };

    // Main real-time synchronization loops
    const listenToRoomChanges = (code: string, myName: string) => {
        const roomRef = ref(db, `rooms/${code}`);

        // 1. Members synchronizer
        onValue(child(roomRef, 'members'), (snapshot) => {
            const val = snapshot.val() || {};
            setMembers(val);

            // Sync peer connections based on member names list
            const allUsers = Object.keys(val);
            syncWebRTCPeers(allUsers, code, myName);
        });

        // 2. YouTube ID change sync
        onValue(child(roomRef, 'nowPlaying'), (snapshot) => {
            const videoId = snapshot.val() || "";
            setNowPlaying(videoId);
            if (ytPlayerRef.current && ytPlayerRef.current.loadVideoById) {
                try {
                    if (videoId) {
                        ytPlayerRef.current.loadVideoById(videoId);
                    } else {
                        ytPlayerRef.current.stopVideo();
                    }
                } catch (e) {
                    console.error("Error setting video track:", e);
                }
            }
        });

        // 3. Playback State play/seek synchronization
        onValue(child(roomRef, 'playback'), (snapshot) => {
            const data = snapshot.val() as Playback | null;
            if (!data) return;
            setPlayback(data);

            const player = ytPlayerRef.current;
            if (player && player.getPlayerState) {
                try {
                    const playerState = player.getPlayerState();
                    const currentSecs = player.getCurrentTime() || 0;

                    // Sync state (Play vs Pause)
                    if (data.state === "PLAYING" && playerState !== 1) {
                        player.playVideo();
                    } else if (data.state === "PAUSED" && playerState !== 2) {
                        player.pauseVideo();
                    }

                    // Seek synchronization (if drift is larger than 2.5 seconds)
                    if (Math.abs(currentSecs - data.time) > 2.5) {
                        player.seekTo(data.time, true);
                    }
                } catch (e) {
                    console.warn("YouTube player deferred sync action due to incomplete state initialization.");
                }
            }
        });
    };

    // --- WebRTC signaling & synchronization layer (matching Android specs) ---
    const syncWebRTCPeers = (allUsers: string[], code: string, myName: string) => {
        const activePeers = activePeersRef.current;

        // Clean up peers who are no longer in the room
        Object.keys(activePeers).forEach(peerName => {
            if (!allUsers.includes(peerName)) {
                cleanupPeerSession(peerName);
            }
        });

        // Connect with new peers
        allUsers.forEach(peerName => {
            if (peerName === myName) return;
            if (!activePeers[peerName]) {
                setupPeerSession(peerName, code, myName);
            }
        });
    };

    const setupPeerSession = (peerName: string, code: string, myName: string) => {
        console.log(`[WebRTC] Constructing Peer Connection with: ${peerName}`);
        
        const isInitiator = myName < peerName;
        const connectionKey = isInitiator ? `${myName}_${peerName}` : `${peerName}_${myName}`;
        const signalingRef = ref(db, `rooms/${code}/signaling/${connectionKey}`);

        const rtcConfig = {
            iceServers: [
                { urls: "stun:stun.l.google.com:19302" },
                { urls: "stun:stun1.l.google.com:19302" },
                { urls: "stun:stun2.l.google.com:19302" }
            ]
        };

        const pc = new RTCPeerConnection(rtcConfig);
        const peerObj: PeerSync = {
            peerConnection: pc,
            signalingRef,
            isInitiator,
            remoteDescriptionSet: false,
            queuedCandidates: []
        };

        // Add local tracks to peer connection
        if (localStreamRef.current) {
            localStreamRef.current.getTracks().forEach(track => {
                pc.addTrack(track, localStreamRef.current!);
            });
        }

        // Handle sending ICE candidates to the target Firebase signaling nodes
        pc.onicecandidate = (event) => {
            if (event.candidate) {
                const candidateMap = {
                    candidate: event.candidate.candidate,
                    sdpMid: event.candidate.sdpMid || "",
                    sdpMLineIndex: event.candidate.sdpMLineIndex
                };
                const targetNode = isInitiator ? "candidates_X" : "candidates_Y";
                push(child(signalingRef, targetNode), candidateMap);
            }
        };

        // Handle receiving audio tracks from the remote peer
        pc.ontrack = (event) => {
            console.log(`[WebRTC] Received remote track stream from ${peerName}`);
            const stream = event.streams && event.streams[0] ? event.streams[0] : new MediaStream([event.track]);
            setRemoteStreams(prev => ({
                ...prev,
                [peerName]: stream
            }));
        };

        // Monitor remote candidate nodes in Firebase
        const listenNode = isInitiator ? "candidates_Y" : "candidates_X";
        const candRef = child(signalingRef, listenNode);
        const processedKeys = new Set<string>();

        const candListener = onValue(candRef, (snapshot) => {
            snapshot.forEach((childSnap) => {
                const key = childSnap.key;
                if (!key || processedKeys.has(key)) return;
                
                const data = childSnap.val();
                if (data) {
                    const sdpStr = data.candidate || data.sdp;
                    const candidate = new RTCIceCandidate({
                        sdpMid: data.sdpMid,
                        sdpMLineIndex: data.sdpMLineIndex,
                        candidate: sdpStr
                    });

                    if (peerObj.remoteDescriptionSet) {
                        pc.addIceCandidate(candidate).catch(err => {
                            console.warn("Failed to apply candidate immediately:", err);
                        });
                    } else {
                        peerObj.queuedCandidates.push(candidate);
                    }
                    processedKeys.add(key);
                }
            });
        });
        peerObj.candidateListener = candListener;

        // Perform SDP Handshake
        if (isInitiator) {
            // Initiator clears out the signaling ref node and publishes Offer
            set(signalingRef, null).then(() => {
                pc.createOffer().then(desc => {
                    pc.setLocalDescription(desc).then(() => {
                        set(child(signalingRef, 'offer'), {
                            type: "offer",
                            sdp: desc.sdp
                        });
                        console.log(`[WebRTC] Offer sent to ${peerName}`);
                    });
                });
            });

            // Listen for Receiver's Answer
            const answerRef = child(signalingRef, 'answer');
            let isAnswerApplied = false;
            const descListener = onValue(answerRef, (snap) => {
                if (isAnswerApplied) return;
                const data = snap.val();
                if (data && data.sdp) {
                    const remoteDesc = new RTCSessionDescription({
                        type: "answer",
                        sdp: data.sdp
                    });
                    pc.setRemoteDescription(remoteDesc).then(() => {
                        isAnswerApplied = true;
                        peerObj.remoteDescriptionSet = true;
                        // Flush any queued ICE candidates
                        flushIceCandidatesQueue(peerObj);
                        console.log(`[WebRTC] Answer applied for initiator: ${peerName}`);
                    });
                }
            });
            peerObj.descListener = descListener;

        } else {
            // Receiver listens to the Offer node
            const offerRef = child(signalingRef, 'offer');
            let isOfferApplied = false;
            const descListener = onValue(offerRef, (snap) => {
                if (isOfferApplied) return;
                const data = snap.val();
                if (data && data.sdp) {
                    const remoteDesc = new RTCSessionDescription({
                        type: "offer",
                        sdp: data.sdp
                    });
                    pc.setRemoteDescription(remoteDesc).then(() => {
                        isOfferApplied = true;
                        peerObj.remoteDescriptionSet = true;
                        flushIceCandidatesQueue(peerObj);

                        // Create and send back Answer
                        pc.createAnswer().then(answerDesc => {
                            pc.setLocalDescription(answerDesc).then(() => {
                                set(child(signalingRef, 'answer'), {
                                    type: "answer",
                                    sdp: answerDesc.sdp
                                });
                                console.log(`[WebRTC] Answer dispatched back to ${peerName}`);
                            });
                        });
                    });
                }
            });
            peerObj.descListener = descListener;
        }

        // Store peer reference
        activePeersRef.current[peerName] = peerObj;
    };

    const flushIceCandidatesQueue = (peerObj: PeerSync) => {
        while (peerObj.queuedCandidates.length > 0) {
            const candidate = peerObj.queuedCandidates.shift();
            if (candidate) {
                peerObj.peerConnection.addIceCandidate(candidate).catch(err => {
                    console.warn("Failed to apply queued candidate:", err);
                });
            }
        }
    };

    const cleanupPeerSession = (peerName: string) => {
        const peerObj = activePeersRef.current[peerName];
        if (!peerObj) return;

        console.log(`[WebRTC] Disposing session connection with: ${peerName}`);

        try {
            // Stop listening to Firebase signaling events
            if (peerObj.candidateListener) off(child(peerObj.signalingRef, peerObj.isInitiator ? "candidates_Y" : "candidates_X"));
            if (peerObj.descListener) off(child(peerObj.signalingRef, peerObj.isInitiator ? 'answer' : 'offer'));

            peerObj.peerConnection.close();
        } catch (e) {}

        // Remove DOM audio element
        setRemoteStreams(prev => {
            const copy = { ...prev };
            delete copy[peerName];
            return copy;
        });

        delete activePeersRef.current[peerName];
    };

    // Full tear-down cleanup of current room connection
    const handleLeaveRoom = async () => {
        if (!activeRoomCode) return;

        showToast("Oda kapatılıyor...");

        // Stop mic analyser interval
        if (analyserIntervalRef.current) {
            clearInterval(analyserIntervalRef.current);
            analyserIntervalRef.current = null;
        }

        // Stop local microphone stream
        if (localStreamRef.current) {
            try {
                localStreamRef.current.getTracks().forEach(track => track.stop());
            } catch (e) {}
            localStreamRef.current = null;
        }

        // Remove presence record from Firebase
        if (username) {
            const myMemberRef = ref(db, `rooms/${activeRoomCode}/members/${username}`);
            await set(myMemberRef, null);
        }

        // Remove listeners
        const roomRef = ref(db, `rooms/${activeRoomCode}`);
        off(child(roomRef, 'members'));
        off(child(roomRef, 'nowPlaying'));
        off(child(roomRef, 'playback'));

        // Cleanup all peer streams
        Object.keys(activePeersRef.current).forEach(peerName => {
            cleanupPeerSession(peerName);
        });

        // Reset variables
        setInRoom(false);
        setActiveRoomCode("");
        setMembers({});
        setNowPlaying("");
        setPlayback(null);
        setIsMuted(true);
        setIsPttMode(false);
        setIsSpacePressed(false);
        showToast("Odadan çıkıldı.");
    };

    const copyRoomCode = () => {
        if (!activeRoomCode) return;
        navigator.clipboard.writeText(activeRoomCode).then(() => {
            showToast("Oda kodu kopyalandı: " + activeRoomCode);
        }).catch(() => {
            showToast("Kopyalanamadı.", true);
        });
    };

    return (
        <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh', width: '100%' }}>
            
            {/* Header */}
            <header>
                <div className="logo">
                    <i className="fa-solid fa-headset"></i>
                    <span>SQUAD // WEB CONSOLE</span>
                </div>
                <div className={`connection-badge ${firebaseConnected ? 'connected' : ''}`}>
                    <div className="dot"></div>
                    <span>{firebaseConnected ? 'Bağlı' : 'Bağlantı Yok'}</span>
                </div>
            </header>

            {/* Main Application Container */}
            <main>
                {!inRoom ? (
                    /* SCREEN 1: LANDING AND LOGIN SCREEN */
                    <div className="auth-card">
                        <span className="protocol-prefix">// WEBRTC REALTIME SYSTEM</span>
                        <h1 className="auth-headline">
                            <span className="headline-white">YÜKSEK SES VE</span><br />
                            <span className="headline-cyan">VİDEO</span> <span className="headline-green">SENKRONİZASYONU</span>
                        </h1>
                        <p className="auth-description">
                            Squad ses sistemine tarayıcı üzerinden anında katılın. Mobil uygulamadaki tüm üyelerle kesintisiz konuşun ve çalınan müzikleri eşzamanlı dinleyin.
                        </p>

                        <div className="auth-box">
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                                <label className="form-label">KULLANICI ADINIZ</label>
                                <div className="dark-input-wrapper">
                                    <input 
                                        type="text" 
                                        className="dark-input" 
                                        placeholder="ÖRN: MEHMET_WEB" 
                                        maxLength={15} 
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ''))}
                                    />
                                </div>
                            </div>

                            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '4px' }}>
                                <label className="form-label">ODA OLUŞTURUN</label>
                                <button className="btn-cyan" onClick={handleCreateRoom} disabled={!firebaseConnected}>
                                    <i className="fa-solid fa-plus-circle"></i> YENİ ODA KUR VE KATIL
                                </button>
                            </div>

                            <div className="divider-or">VEYA</div>

                            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                                <label className="form-label">5 HANELİ ODA KODU İLE KATIL</label>
                                <div className="join-row">
                                    <input 
                                        type="text" 
                                        className="dark-input" 
                                        placeholder="5 HANELİ KOD" 
                                        maxLength={5} 
                                        value={roomCodeInput}
                                        onChange={(e) => setRoomCodeInput(e.target.value.replace(/[^0-9]/g, ''))}
                                    />
                                    <button className="btn-dark" onClick={handleJoinRoom} disabled={!firebaseConnected}>KATIL</button>
                                </div>
                            </div>
                        </div>

                        {/* SOURCE CODE DOWNLOAD BAR */}
                        <div style={{
                            marginTop: '20px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '10px',
                            padding: '16px',
                            background: 'rgba(255, 255, 255, 0.03)',
                            borderRadius: '8px',
                            border: '1px solid rgba(255, 255, 255, 0.08)'
                        }}>
                            <span style={{ fontSize: '11px', color: 'var(--squad-primary)', fontWeight: 'bold', letterSpacing: '1px', textTransform: 'uppercase', fontFamily: 'var(--squad-mono)' }}>
                                <i className="fa-solid fa-code-branch"></i> Kaynak Kodları İndir (Download Sources)
                            </span>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
                                <a href="/web.zip" download="web.zip" style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: '8px',
                                    padding: '10px',
                                    background: 'rgba(0, 240, 255, 0.1)',
                                    color: '#00f0ff',
                                    borderRadius: '4px',
                                    fontSize: '11px',
                                    fontWeight: '800',
                                    textDecoration: 'none',
                                    border: '1px solid rgba(0, 240, 255, 0.3)',
                                    textAlign: 'center',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s'
                                }}>
                                    <i className="fa-solid fa-file-zipper"></i> WEB KODLARI (.ZIP)
                                </a>
                                <a href="/squad_project.zip" download="squad_project.zip" style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    gap: '8px',
                                    padding: '10px',
                                    background: 'rgba(16, 185, 129, 0.1)',
                                    color: '#10b981',
                                    borderRadius: '4px',
                                    fontSize: '11px',
                                    fontWeight: '800',
                                    textDecoration: 'none',
                                    border: '1px solid rgba(16, 185, 129, 0.3)',
                                    textAlign: 'center',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s'
                                }}>
                                    <i className="fa-solid fa-box-archive"></i> TÜM PROJE (.ZIP)
                                </a>
                            </div>
                        </div>
                    </div>
                ) : (
                    /* SCREEN 2: ACTIVE CONTROL CONSOLE */
                    <div className="room-dashboard">
                        
                        {/* LEFT COLUMN: YT VIDEO STREAMER & RANGE CONTROLLERS */}
                        <div className="left-col">
                            
                            {/* YouTube Synchronizer Box */}
                            <div className="video-box">
                                <div className="video-box-header">
                                    <i className="fa-solid fa-circle-play"></i>
                                    <span>CANLI SENKRONİZE EDİLEN VİDEO</span>
                                </div>
                                <div className="video-box-frame">
                                    <div id="ytPlayerTarget" style={{ display: nowPlaying ? 'block' : 'none' }}></div>
                                    {!nowPlaying && (
                                        <div className="no-video-container">
                                            <i className="fa-solid fa-circle-play"></i>
                                            <p>Şu anda odada oynatılan video bulunmuyor.<br />
                                                <span style={{ fontSize: '11px', color: 'var(--squad-text-sec)' }}>
                                                    Mobilden video açıldığında otomatik olarak eşzamanlanır.
                                                </span>
                                            </p>
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Sound Mixer Console */}
                            <div className="mixer-console">
                                <h3 className="panel-header-mono" style={{ marginBottom: 0 }}>// SES VE KONTROL SEVİYELERİ</h3>
                                
                                <div className="mixer-row">
                                    <div className="mixer-info-row">
                                        <span className="mixer-title">YOUTUBE VİDEO SESİ</span>
                                        <span className="mixer-volume-val">{mediaVolume}%</span>
                                    </div>
                                    <input 
                                        type="range" 
                                        className="range-slider" 
                                        min="0" 
                                        max="100" 
                                        value={mediaVolume}
                                        onChange={(e) => setMediaVolume(parseInt(e.target.value))}
                                    />
                                </div>

                                <div className="mixer-row">
                                    <div className="mixer-info-row">
                                        <span className="mixer-title">ÜYE SESLERİ (KONUŞMA)</span>
                                        <span className="mixer-volume-val">{voiceVolume}%</span>
                                    </div>
                                    <input 
                                        type="range" 
                                        className="range-slider" 
                                        min="0" 
                                        max="200" 
                                        value={voiceVolume}
                                        onChange={(e) => setVoiceVolume(parseInt(e.target.value))}
                                    />
                                </div>
                            </div>

                        </div>

                        {/* RIGHT COLUMN: MIC MODES & USER CHANNELS */}
                        <div className="right-col">
                            
                            {/* Dashboard Stats / Control Header */}
                            <div className="console-header-block">
                                <div className="room-code-tag">
                                    Oda:
                                    <div className="code-pill" onClick={copyRoomCode} title="Kopyalamak için tıklayın">
                                        <span>{activeRoomCode}</span>
                                        <i className="fa-regular fa-copy"></i>
                                    </div>
                                </div>
                                <button className="btn-disconnect-red" onClick={handleLeaveRoom}>ÇIK</button>
                            </div>

                            {/* Broadcaster controller panel */}
                            <div className="broadcaster-console">
                                <h3 className="panel-header-mono" style={{ marginBottom: 0 }}>// MİKROFON YAYIN KONTROLÜ</h3>
                                
                                <div className="switch-mode-row">
                                    <button 
                                        className={`switch-btn ${!isPttMode ? 'active' : ''}`}
                                        onClick={() => {
                                            setIsPttMode(false);
                                            setIsMuted(true); // default to muted upon swap for safety
                                        }}
                                    >
                                        KESİNTİSİZ
                                    </button>
                                    <button 
                                        className={`switch-btn ${isPttMode ? 'active' : ''}`}
                                        onClick={() => {
                                            setIsPttMode(true);
                                            setIsMuted(true);
                                        }}
                                    >
                                        BAS-KONUŞ
                                    </button>
                                </div>

                                <p className="console-desc-text">
                                    {isPttMode 
                                        ? "Spacebar tuşuna basılı tuttuğunuzda mikrofonunuz açılır, bıraktığınızda kapanır."
                                        : "Mikrofonunuz siz alttaki butondan kapatana kadar kesintisiz yayın yapar."
                                    }
                                </p>

                                <button 
                                    className={`btn-mic-action ${!isMuted ? 'active-broadcasting' : ''}`}
                                    onClick={() => setIsMuted(!isMuted)}
                                    disabled={isPttMode}
                                >
                                    {isMuted ? (
                                        <>
                                            <i className="fa-solid fa-microphone-slash"></i> MİKROFON KAPALI (AÇMAK İÇİN TIKLA)
                                        </>
                                    ) : (
                                        <>
                                            <i className="fa-solid fa-microphone"></i> MİKROFONU KAPAT
                                        </>
                                    )}
                                </button>

                                {isPttMode && (
                                    <div className="keyboard-ptt-hint">
                                        {isSpacePressed 
                                            ? "[SPACEBAR BASILI] - MİKROFON AÇIK, KONUŞUYORSUNUZ" 
                                            : "[SPACEBAR] BASILI TUTARAK KONUŞUN"
                                        }
                                    </div>
                                )}
                            </div>

                            {/* Dynamic Members Directory */}
                            <div className="communicators-section">
                                <h3 className="panel-header-mono">// ODADAKİLER</h3>
                                <div className="members-container">
                                    {Object.keys(members).map((memberName) => {
                                        const member = members[memberName];
                                        const initial = memberName.charAt(0).toUpperCase();
                                        const isCurrentUser = memberName === username;
                                        const showSpeaking = member.isSpeaking && !member.isMuted;

                                        return (
                                            <div 
                                                key={memberName} 
                                                className={`member-item-card ${showSpeaking ? 'speaking' : ''}`}
                                            >
                                                <div className="member-profile-row">
                                                    <div className="member-avatar">{initial}</div>
                                                    <span className="member-username">
                                                        {memberName} {isCurrentUser && (
                                                            <span style={{ color: 'var(--squad-primary)', fontSize: '11px', fontWeight: 'normal' }}>
                                                                (Siz)
                                                            </span>
                                                        )}
                                                    </span>
                                                </div>
                                                <div className="member-status-pills">
                                                    {showSpeaking && (
                                                        <span style={{ color: 'var(--squad-green)', fontSize: '11px', fontWeight: '800', marginRight: '8px', fontFamily: 'var(--squad-mono)' }}>
                                                            SPEAKING
                                                        </span>
                                                    )}
                                                    {member.isMuted && (
                                                        <i className="fa-solid fa-microphone-slash muted-pill-indicator"></i>
                                                    )}
                                                    <div className="active-glow-dot">ACTIVE</div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>

                        </div>

                    </div>
                )}
            </main>

            {/* Floating Banner Toast Notifications */}
            <div className={`toast ${toast.visible ? 'show' : ''} ${toast.isError ? 'error' : ''}`}>
                <i id="toastIcon" className={toast.isError ? "fa-solid fa-circle-exclamation" : "fa-solid fa-circle-info"}></i>
                <span>{toast.message}</span>
            </div>

            {/* Managed Remote Audio Streams Sinks */}
            <div style={{ display: 'none' }}>
                {Object.keys(remoteStreams).map(peerName => (
                    <audio
                        key={peerName}
                        id={`audio_sink_${peerName}`}
                        autoPlay
                        playsInline
                        ref={el => {
                            if (el && el.srcObject !== remoteStreams[peerName]) {
                                el.srcObject = remoteStreams[peerName];
                                el.volume = voiceVolume / 100.0;
                                el.play().catch(err => console.warn("Autoplay deferred for " + peerName, err));
                            }
                        }}
                    />
                ))}
            </div>

        </div>
    );
}
