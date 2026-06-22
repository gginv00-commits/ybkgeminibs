package com.example.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.view.ViewGroup
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.example.ui.theme.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures

data class SquadSong(
    val title: String,
    val artist: String,
    val url: String,
    val category: String = "Genel"
)

val SQUAD_SONG_LIBRARY = listOf(
    SquadSong("Lofi Hip Hop Room Mix", "Chillhop Beats", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", "Mood & Chill"),
    SquadSong("Neon Waves (Retro Synth)", "Wave Runner", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3", "Synthwave"),
    SquadSong("Acoustic Sunsets", "Sona Guitar", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3", "Acoustic"),
    SquadSong("Cyberpunk Underground", "Hack Operator", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3", "Electronic"),
    SquadSong("Ege Rüzgarları Lofi Beat", "Efe Beats", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3", "Remix & Etnik"),
    SquadSong("Anatolian Ambient Fusion", "Saz Chillout", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3", "Remix & Etnik"),
    SquadSong("Retro Arcade Focus", "Pixel Boy", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3", "Synthwave"),
    SquadSong("Cosmic Silence Ambient", "Galaxy Pulse", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3", "Ambient"),
    SquadSong("Istanbul Street Jazz Beat", "Taksim Trio (Remix)", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3", "Remix & Etnik"),
    SquadSong("Deep Night Meditation", "Yogi Flow", "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3", "Ambient"),
    SquadSong("SNAP", "Manifest", "https://m.soundcloud.com/manifest-388266869/snap", "Ambient")


)

fun extractYoutubeId(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    
    val isYtMusic = trimmed.contains("music.youtube.com")
    
    if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
        return if (isYtMusic) "yt_music:$trimmed" else trimmed
    }
    
    // Check for standard video ID matches
    val videoPatterns = listOf(
        Regex("(?:v=)([a-zA-Z0-9_-]{11})"),
        Regex("(?:embed\\/)([a-zA-Z0-9_-]{11})"),
        Regex("(?:shorts\\/)([a-zA-Z0-9_-]{11})"),
        Regex("(?:live\\/)([a-zA-Z0-9_-]{11})"),
        Regex("(?:youtu\\.be\\/)([a-zA-Z0-9_-]{11})"),
        Regex("(?:v\\/)([a-zA-Z0-9_-]{11})")
    )
    
    var foundVideoId: String? = null
    for (pattern in videoPatterns) {
        val matchResult = pattern.find(trimmed)
        if (matchResult != null) {
            foundVideoId = matchResult.groupValues[1]
            break
        }
    }
    
    // Check for list= parameter
    val listPattern = Regex("(?:list=)([a-zA-Z0-9_-]+)")
    val playlistMatch = listPattern.find(trimmed)
    val foundPlaylistId = playlistMatch?.groupValues?.get(1)
    
    if (foundVideoId != null && foundPlaylistId != null) {
        return if (isYtMusic) "yt_music_both:$foundVideoId|$foundPlaylistId" else "both:$foundVideoId|$foundPlaylistId"
    } else if (foundPlaylistId != null) {
        return if (isYtMusic) "yt_music_playlist:$foundPlaylistId" else "playlist:$foundPlaylistId"
    } else if (foundVideoId != null) {
        return if (isYtMusic) "yt_music:$foundVideoId" else foundVideoId
    }
    
    // Fallbacks
    if (trimmed.contains("list=")) {
        val pId = trimmed.substringAfter("list=").substringBefore("&")
        if (pId.isNotEmpty()) {
            if (trimmed.contains("v=")) {
                val vId = trimmed.substringAfter("v=").substringBefore("&")
                if (vId.length == 11) {
                    return if (isYtMusic) "yt_music_both:$vId|$pId" else "both:$vId|$pId"
                }
            }
            return if (isYtMusic) "yt_music_playlist:$pId" else "playlist:$pId"
        }
    }
    if (trimmed.contains("v=")) {
        val id = trimmed.substringAfter("v=").substringBefore("&")
        if (id.length == 11 && id.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return if (isYtMusic) "yt_music:$id" else id
        }
    }
    if (trimmed.contains("youtu.be/")) {
        val id = trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("/")
        if (id.length == 11 && id.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return if (isYtMusic) "yt_music:$id" else id
        }
    }
    if (trimmed.contains("shorts/")) {
        val id = trimmed.substringAfter("shorts/").substringBefore("?").substringBefore("/")
        if (id.length == 11 && id.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return if (isYtMusic) "yt_music:$id" else id
        }
    }
    
    return null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RoomScreen(
    roomId: Int,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val roomFlow = remember(roomId) { viewModel.getRoom(roomId) }
    val room by roomFlow.collectAsStateWithLifecycle()
    val members by viewModel.activeMembers.collectAsStateWithLifecycle()
    val currentRoomCreator by viewModel.currentRoomCreator.collectAsStateWithLifecycle()
    val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
    val roomNowPlaying by viewModel.roomNowPlaying.collectAsStateWithLifecycle()
    val sharedIncomingMedia by viewModel.sharedIncomingMedia.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }
    
    // Push-to-Talk (Bas-Konuş) State
    var pttModeEnabled by remember { mutableStateOf(false) }
    var pttKeyCode by remember { mutableStateOf(android.view.KeyEvent.KEYCODE_SPACE) }
    var pttKeyName by remember { mutableStateOf("SPACE") }
    var isKeyBindingActive by remember { mutableStateOf(false) }
    var isKeyPressed by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val customSongs = remember { mutableStateListOf<SquadSong>() }

    val audioPermissionState = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    val isMicEnabled = audioPermissionState.status.isGranted && !isMuted

    LaunchedEffect(isMicEnabled) {
        viewModel.webrtcManager.toggleMicrophone(isMicEnabled)
        viewModel.updateUserPresence(isMuted = !isMicEnabled, isSpeaking = false)
    }

    LaunchedEffect(pttModeEnabled) {
        if (pttModeEnabled) {
            isMuted = true
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(room) {
        val code = room?.roomCode
        if (code != null && viewModel.activeRoomCode != code) {
            viewModel.startSyncingRoom(roomId, code)
        }
    }

    BackHandler {
        viewModel.stopSyncingRoom()
        onNavigateBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            // Keep background room connection alive! Syncing only stops via BackHandler or ODADAN ÇIK click.
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SquadBackground)
            .drawBehind {
                val gridSize = 40.dp.toPx()
                val gridColor = Color.White.copy(alpha = 0.05f)
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y))
                    y += gridSize
                }
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height))
                    x += gridSize
                }
            }
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (pttModeEnabled) {
                    val keyCode = keyEvent.nativeKeyEvent.keyCode
                    if (isKeyBindingActive) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            pttKeyCode = keyCode
                            pttKeyName = android.view.KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "")
                            isKeyBindingActive = false
                        }
                        true
                    } else if (keyCode == pttKeyCode) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            if (!isKeyPressed) {
                                isKeyPressed = true
                                isMuted = false
                            }
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            isKeyPressed = false
                            isMuted = true
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Header Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SquadSurfaceDark, RoundedCornerShape(8.dp))
                    .border(1.dp, SquadHover, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("SQUAD ROOM:", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(SquadSurfaceDark, RoundedCornerShape(4.dp))
                            .border(1.dp, SquadPrimary.copy(alpha=0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(room?.roomCode ?: "...", color = SquadPrimary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 14.sp)
                    }
                }
                
                OutlinedButton(
                    onClick = {
                        viewModel.stopSyncingRoom()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SquadRed),
                    border = BorderStroke(1.dp, SquadRed.copy(alpha=0.5f)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("BAĞLANTIYI KES", fontSize = 10.sp, letterSpacing = 1.sp)
                }
            }

            // ACTIVE COMMUNICATORS
            Column {
                Text(
                    "// ACTIVE COMMUNICATORS",
                    color = SquadTextSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SquadSurfaceDark, RoundedCornerShape(8.dp))
                        .border(1.dp, SquadHover, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    members.forEach { member ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(SquadHover, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(member.name.take(1).uppercase(), color = SquadPrimary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(member.name, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(if (!member.isMuted) SquadGreen else SquadTextSecondary, CircleShape))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (!member.isMuted) "ACTIVE" else "MUTED", color = SquadTextSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // COMMUNICATIONS CONTROLS CARD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SquadSurfaceDark, RoundedCornerShape(8.dp))
                        .border(1.dp, SquadHover, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        "İLETİŞİM MODU SEÇİN",
                        color = SquadTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Mode selector segmented row
                    Row(
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { pttModeEnabled = false },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!pttModeEnabled) SquadPrimary else SquadHover,
                                contentColor = if (!pttModeEnabled) Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Sürekli Mikrofon", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { pttModeEnabled = true },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (pttModeEnabled) SquadPrimary else SquadHover,
                                contentColor = if (pttModeEnabled) Color.Black else Color.White
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Bas-Konuş (PTT)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!pttModeEnabled) {
                        // Continuous Mode Panel
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Mikrofonunuz siz kapatana kadar kesintisiz yayın yapar.",
                                color = SquadTextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (!audioPermissionState.status.isGranted) {
                                        audioPermissionState.launchPermissionRequest()
                                    } else {
                                        isMuted = !isMuted
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMicEnabled) SquadGreen else SquadHover
                                )
                            ) {
                                Text(
                                    if (isMicEnabled) "MİKROFON AÇIK (KAPATMAK İÇİN TIKLA)" else "MİKROFON KAPALI (AÇMAK İÇİN TIKLA)",
                                    color = if (isMicEnabled) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Push-to-Talk Mode Panel
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Key config row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SquadBackground, RoundedCornerShape(4.dp))
                                    .border(1.dp, SquadHover, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bas-Konuş Kısayol Tuşu:", color = Color.White, fontSize = 12.sp)
                                
                                Button(
                                    onClick = { 
                                        isKeyBindingActive = true
                                        focusRequester.requestFocus()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isKeyBindingActive) SquadRed else SquadHover
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = if (isKeyBindingActive) "BİR TUŞA BASIN..." else "[ $pttKeyName ]",
                                        color = if (isKeyBindingActive) Color.White else SquadPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Large Dynamic On-Screen Button using pointerInput
                            val isPttButtonPressed = isKeyPressed && pttModeEnabled
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .background(
                                        color = if (isPttButtonPressed) SquadGreen else SquadHover,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isPttButtonPressed) SquadGreen else SquadPrimary.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                if (audioPermissionState.status.isGranted) {
                                                    try {
                                                        isKeyPressed = true
                                                        isMuted = false
                                                        awaitRelease()
                                                    } finally {
                                                        isKeyPressed = false
                                                        isMuted = true
                                                    }
                                                } else {
                                                    audioPermissionState.launchPermissionRequest()
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (isPttButtonPressed) "ŞU AN SESİNİZ GİDİYOR" else "BASILI TUTARAK KONUŞ",
                                        color = if (isPttButtonPressed) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = if (isPttButtonPressed) "Bıraktığınızda mikrofon kapanır" else "Mobil için basılı tutun, klavye için [ $pttKeyName ] basılı tutun",
                                        color = if (isPttButtonPressed) Color.Black.copy(alpha=0.6f) else SquadTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // YT SYNC PROTOCOL
                Column {
                    Text(
                        "// SQUAD SEYİR VE SES SENKRONİZASYON PROTOKOLÜ (S4)",
                        color = SquadTextSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SquadSurfaceDark, RoundedCornerShape(8.dp))
                            .border(1.dp, SquadHover, RoundedCornerShape(8.dp))
                    ) {
                        val isRoomCreator = (currentRoomCreator == null) || (currentRoomCreator == currentUsername)
                        
                        var selectedTab by remember { mutableStateOf("youtube") }
                        
                        val playingNow = roomNowPlaying
                        
                        LaunchedEffect(playingNow) {
                            if (playingNow != null) {
                                selectedTab = when {
                                    playingNow.startsWith("squad_beam:") -> "squad_beam"
                                    playingNow.startsWith("mp3:") -> "squad_music"
                                    else -> "youtube"
                                }
                            }
                        }
                        
                        // Tab Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val tabs = listOf(
                                "youtube" to "📺 YouTube",
                                "squad_music" to "🎵 Müzik Arşivi",
                                "squad_beam" to "🔊 SQUAD BEAM"
                            )
                            tabs.forEach { (key, label) ->
                                val isSelected = selectedTab == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .background(
                                            color = if (isSelected) SquadPrimary else SquadHover.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) SquadPrimary else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable(enabled = isRoomCreator) {
                                            selectedTab = key
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        
                        Divider(color = SquadHover)
                        
                        // Inputs depending on Tab & Creator status
                        if (isRoomCreator) {
                            when (selectedTab) {
                                "youtube" -> {
                                    var linkText by remember { mutableStateOf("") }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = linkText,
                                            onValueChange = { linkText = it },
                                            placeholder = { Text("YouTube / YT Music Linki Yapıştır", color = SquadTextSecondary, fontSize = 12.sp) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = SquadPrimary,
                                                unfocusedBorderColor = SquadHover,
                                                focusedContainerColor = SquadBackground,
                                                unfocusedContainerColor = SquadBackground,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 8.dp),
                                            maxLines = 1,
                                            singleLine = true
                                        )
                                        
                                        Button(
                                            onClick = { 
                                                val trimmed = linkText.trim()
                                                if (trimmed.isEmpty()) {
                                                    android.widget.Toast.makeText(context, "Lütfen bir YouTube veya YT Music linki yapıştırın", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    val ytId = extractYoutubeId(trimmed)
                                                    if (ytId != null) {
                                                        viewModel.updateRoomNowPlaying(roomId, ytId)
                                                        linkText = ""
                                                        android.widget.Toast.makeText(context, "YouTube / YouTube Music medyası senkronize edildi!", android.widget.Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        android.widget.Toast.makeText(context, "Geçersiz YouTube/YT Music linki! Lütfen geçerli bir şarkı, çalma listesi veya video linki girdiğinizden emin olun.", android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SquadPrimary),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text("BAŞLAT", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                }
                                "squad_music" -> {
                                    var searchQuery by remember { mutableStateOf("") }
                                    var selectedCategory by remember { mutableStateOf("Tümü") }
                                    
                                    val allSongs = SQUAD_SONG_LIBRARY + customSongs
                                    val categories = listOf("Tümü") + allSongs.map { it.category }.distinct()
                                    
                                    val filteredSongs = allSongs.filter { song ->
                                        (selectedCategory == "Tümü" || song.category == selectedCategory) &&
                                        (song.title.contains(searchQuery, ignoreCase = true) || 
                                         song.artist.contains(searchQuery, ignoreCase = true) ||
                                         song.category.contains(searchQuery, ignoreCase = true))
                                    }
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "🎵 GRUBUNUZLA MÜZİK KEYFİ (GELİŞTİRİCİ ARŞİVİ)",
                                            color = SquadPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        
                                        // Search Bar
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            placeholder = { Text("Şarkı veya sanatçı ara...", color = SquadTextSecondary, fontSize = 11.sp) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "Ara",
                                                    tint = SquadTextSecondary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                if (searchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { searchQuery = "" }) {
                                                        Icon(
                                                            imageVector = Icons.Default.Clear,
                                                            contentDescription = "Temizle",
                                                            tint = SquadTextSecondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = SquadPrimary,
                                                unfocusedBorderColor = SquadHover,
                                                focusedContainerColor = SquadBackground,
                                                unfocusedContainerColor = SquadBackground,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            maxLines = 1,
                                            singleLine = true
                                        )
                                        
                                        // Category Pills
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(categories) { category ->
                                                val isCatSelected = selectedCategory == category
                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            color = if (isCatSelected) SquadPrimary else SquadBackground,
                                                            shape = RoundedCornerShape(12.dp)
                                                        )
                                                        .border(1.dp, if (isCatSelected) SquadPrimary else SquadHover, RoundedCornerShape(12.dp))
                                                        .clickable { selectedCategory = category }
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = category,
                                                        color = if (isCatSelected) Color.Black else Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        
                                        // Song List
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 180.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (filteredSongs.isEmpty()) {
                                                item {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 12.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "Aramanızla eşleşen şarkı bulunamadı.",
                                                            color = SquadTextSecondary,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            } else {
                                                items(filteredSongs) { song ->
                                                    val isCurrentlyPlayingThis = playingNow == "mp3:${song.title}|${song.url}"
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(
                                                                if (isCurrentlyPlayingThis) SquadPrimary.copy(alpha = 0.15f) else SquadBackground.copy(alpha = 0.3f),
                                                                RoundedCornerShape(6.dp)
                                                            )
                                                            .border(
                                                                1.dp,
                                                                if (isCurrentlyPlayingThis) SquadPrimary else SquadHover.copy(alpha = 0.4f),
                                                                RoundedCornerShape(6.dp)
                                                            )
                                                            .clickable {
                                                                viewModel.updateRoomNowPlaying(roomId, "mp3:${song.title}|${song.url}")
                                                                android.widget.Toast.makeText(context, "${song.title} başlatıldı!", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isCurrentlyPlayingThis) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                                                                contentDescription = null,
                                                                tint = if (isCurrentlyPlayingThis) SquadPrimary else SquadTextSecondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Column {
                                                                Text(
                                                                    text = song.title,
                                                                    color = Color.White,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(
                                                                    text = song.artist,
                                                                    color = SquadTextSecondary,
                                                                    fontSize = 9.sp,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    if (isCurrentlyPlayingThis) SquadPrimary else SquadHover,
                                                                    RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        ) {
                                                            Text(
                                                                text = if (isCurrentlyPlayingThis) "ÇALIYOR" else "OYNAT",
                                                                color = if (isCurrentlyPlayingThis) Color.Black else Color.White,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Custom song adding form
                                        var isAddFormExpanded by remember { mutableStateOf(false) }
                                        var newSongTitle by remember { mutableStateOf("") }
                                        var newSongArtist by remember { mutableStateOf("") }
                                        var newSongUrl by remember { mutableStateOf("") }

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = SquadBackground.copy(alpha = 0.6f)),
                                            border = BorderStroke(1.dp, SquadHover.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { isAddFormExpanded = !isAddFormExpanded }
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.MusicNote,
                                                            contentDescription = null,
                                                            tint = SquadPrimary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "➕ KENDİ MP3 ŞARKINI EKLE (URL)",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Text(
                                                        text = if (isAddFormExpanded) "KAPAT" else "FORMU AÇ",
                                                        color = SquadPrimary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                if (isAddFormExpanded) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedTextField(
                                                        value = newSongTitle,
                                                        onValueChange = { newSongTitle = it },
                                                        placeholder = { Text("Şarkı Başlığı (Örn: Dert Gecesi)", color = SquadTextSecondary, fontSize = 10.sp) },
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = SquadPrimary,
                                                            unfocusedBorderColor = SquadHover,
                                                            focusedContainerColor = SquadBackground,
                                                            unfocusedContainerColor = SquadBackground,
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                                        singleLine = true
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    OutlinedTextField(
                                                        value = newSongArtist,
                                                        onValueChange = { newSongArtist = it },
                                                        placeholder = { Text("Sanatçı / Yayıncı (Örn: Yıldız Tilbe)", color = SquadTextSecondary, fontSize = 10.sp) },
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = SquadPrimary,
                                                            unfocusedBorderColor = SquadHover,
                                                            focusedContainerColor = SquadBackground,
                                                            unfocusedContainerColor = SquadBackground,
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                                        singleLine = true
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    OutlinedTextField(
                                                        value = newSongUrl,
                                                        onValueChange = { newSongUrl = it },
                                                        placeholder = { Text("Direkt MP3 Linki (https://...mp3)", color = SquadTextSecondary, fontSize = 10.sp) },
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = SquadPrimary,
                                                            unfocusedBorderColor = SquadHover,
                                                            focusedContainerColor = SquadBackground,
                                                            unfocusedContainerColor = SquadBackground,
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White
                                                        ),
                                                        modifier = Modifier.fillMaxWidth().height(44.dp),
                                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
                                                        singleLine = true
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(
                                                        onClick = {
                                                            if (newSongTitle.trim().isEmpty() || newSongUrl.trim().isEmpty()) {
                                                                android.widget.Toast.makeText(context, "Lütfen başlık ve MP3 linki alanlarını doldurun!", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                val cleanUrl = newSongUrl.trim()
                                                                val cleanTitle = newSongTitle.trim()
                                                                val cleanArtist = if (newSongArtist.trim().isEmpty()) "Bilinmeyen Sanatçı" else newSongArtist.trim()
                                                                
                                                                customSongs.add(SquadSong(cleanTitle, cleanArtist, cleanUrl, "Özel Eklenenler"))
                                                                
                                                                newSongTitle = ""
                                                                newSongArtist = ""
                                                                newSongUrl = ""
                                                                isAddFormExpanded = false
                                                                android.widget.Toast.makeText(context, "Şarkı başarıyla kütüphanenize eklendi!", android.widget.Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                                        shape = RoundedCornerShape(4.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = SquadPrimary),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("LİSTEYE EKLE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        Text(
                                            text = "ℹ️ Geliştirici Notu: SQUAD_SONG_LIBRARY listesine yeni MP3 linkleri ekleyerek veya yukarıdaki formu kullanarak arşivi dilediğiniz gibi güncelleyebilirsiniz.",
                                            color = SquadTextSecondary.copy(alpha = 0.7f),
                                            fontSize = 8.5.sp,
                                            lineHeight = 11.sp
                                        )
                                    }
                                }
                                "squad_beam" -> {
                                    val isBroadcastingMyBeam = playingNow?.startsWith("squad_beam:$currentUsername") == true
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "🔊 SQUAD ULTRA-BEAM AUDIO LINK",
                                            color = SquadPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "Cihazınızda çalan tüm müzikleri, oyun seslerini ve mikrofonunuzu yüksek kaliteli WebRTC ses hattıyla odadaki herkesle anında senkronize paylaşın.",
                                            color = SquadTextSecondary,
                                            fontSize = 10.sp,
                                            textAlign = TextAlign.Center,
                                            lineHeight = 14.sp
                                        )
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        Button(
                                            onClick = {
                                                if (isBroadcastingMyBeam) {
                                                    viewModel.updateRoomNowPlaying(roomId, null)
                                                } else {
                                                    viewModel.updateRoomNowPlaying(roomId, "squad_beam:$currentUsername")
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isBroadcastingMyBeam) SquadRed else SquadPrimary
                                            ),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.fillMaxWidth().height(40.dp)
                                        ) {
                                            Text(
                                                text = if (isBroadcastingMyBeam) "🛑 SQUAD BEAM SES YAYININI DURDUR" else "⚡ SQUAD BEAM SES YAYININI BAŞLAT",
                                                color = if (isBroadcastingMyBeam) Color.White else Color.Black,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(if (isBroadcastingMyBeam) SquadPrimary else SquadHover, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isBroadcastingMyBeam) "SES KANALI CANLI AKTARILIYOR (MÜKEMMEL KALİTE)" else "Cihaz ses mikseri beklemede...",
                                                color = if (isBroadcastingMyBeam) SquadPrimary else SquadTextSecondary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                            Divider(color = SquadHover)
                        } else {
                            // Non-creator: show information banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(SquadPrimary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Şu anda ${currentRoomCreator ?: "oda sahibi"} tarafından senkronize edilen medyayı izlemektesiniz.",
                                    color = SquadTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Divider(color = SquadHover)
                        }
                        
                        // SHARED INCOMING MEDIA TRACK NOTIFICATION BANNER
                        if (sharedIncomingMedia != null) {
                            val mediaTitle = if (sharedIncomingMedia!!.startsWith("mp3:")) {
                                sharedIncomingMedia!!.substringAfter("mp3:").substringBefore("|")
                            } else {
                                "Paylaşılan YouTube Videosu"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .background(SquadPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(1.dp, SquadPrimary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        viewModel.updateRoomNowPlaying(roomId, sharedIncomingMedia!!)
                                        viewModel.sharedIncomingMedia.value = null
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(SquadPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📱 DIŞARIDAN PAYLAŞIM ALINDI",
                                        color = SquadPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        text = "$mediaTitle (Başlatmak İçin Dokunun)",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.sharedIncomingMedia.value = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Yoksay",
                                        tint = SquadTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        
                        // Output player section notice
                        if (playingNow == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(SquadHover, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SquadTextSecondary)
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column {
                                    Text("Herhangi bir medya oynatılmıyor", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top=4.dp)) {
                                        Box(modifier = Modifier.size(6.dp).background(SquadTextSecondary, CircleShape))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("IDLE / BEKLEMEDE", color = SquadTextSecondary, fontSize = 10.sp, letterSpacing = 1.sp)
                                    }
                                }
                            }
                        } else {
                            val mediaId = playingNow
                            key(mediaId) {
                                var webViewRef by remember { mutableStateOf<WebView?>(null) }

                                DisposableEffect(mediaId) {
                                    onDispose {
                                        webViewRef?.let { wv ->
                                            try {
                                                wv.stopLoading()
                                                wv.loadUrl("about:blank")
                                                wv.onPause()
                                                wv.destroy()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        webViewRef = null
                                    }
                                }

                                val isSquadBeam = mediaId.startsWith("squad_beam:")
                                val isMp3 = mediaId.startsWith("mp3:")
                                val isYtMusic = mediaId.startsWith("yt_music:") || mediaId.startsWith("yt_music_playlist:") || mediaId.startsWith("yt_music_both:")
                                val isYtPlaylistOnly = mediaId.startsWith("playlist:")
                                val isYtBoth = mediaId.startsWith("both:")
                                val isYt = !isMp3 && !isSquadBeam

                                val embedUrl = when {
                                    isMp3 -> {
                                        val parts = mediaId.removePrefix("mp3:").split("|")
                                        parts.getOrNull(1) ?: parts.getOrNull(0) ?: ""
                                    }
                                    else -> mediaId
                                }

                                val trackTitle = when {
                                    isMp3 -> mediaId.removePrefix("mp3:").split("|").firstOrNull() ?: "Ortak Parça"
                                    isSquadBeam -> {
                                        val name = mediaId.removePrefix("squad_beam:")
                                        "🔊 $name ŞU ANDA SİSTEM SESİNİ YAYINLIYOR"
                                    }
                                    isYtMusic -> {
                                        when {
                                            mediaId.startsWith("yt_music_both:") -> "YouTube Music Parçası & Listesi"
                                            mediaId.startsWith("yt_music_playlist:") -> "YouTube Music Çalma Listesi"
                                            else -> "YouTube Music Parçası"
                                        }
                                    }
                                    isYtPlaylistOnly -> "YouTube Çalma Listesi"
                                    isYtBoth -> "YouTube Videosu & Listesi"
                                    else -> "YouTube Medyası"
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    // Mini Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        SquadPrimary.copy(alpha = 0.2f),
                                                        RoundedCornerShape(4.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = SquadPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = trackTitle,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    if (isYtMusic) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(SquadPrimary.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                                                .border(0.5.dp, SquadPrimary, RoundedCornerShape(3.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("YT MUSIC", color = SquadPrimary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    } else if (isYt) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(SquadPrimary.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                                                .border(0.5.dp, SquadPrimary, RoundedCornerShape(3.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("YOUTUBE", color = SquadPrimary, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    
                                                    if (isYtPlaylistOnly || mediaId.contains("playlist") || mediaId.contains("both")) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("LIST", color = Color.White.copy(alpha = 0.7f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Close button
                                        IconButton(
                                            onClick = { viewModel.updateRoomNowPlaying(roomId, null) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Kapat",
                                                tint = SquadRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    // WebView Container
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(2.dp, SquadPrimary, RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                    ) {
                                        AndroidView(
                                            factory = { ctx ->
                                                WebView(ctx).apply {
                                                    webViewRef = this
                                                    layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    settings.javaScriptEnabled = true
                                                    settings.domStorageEnabled = true
                                                    settings.databaseEnabled = true
                                                    settings.mediaPlaybackRequiresUserGesture = false
                                                    settings.useWideViewPort = true
                                                    settings.loadWithOverviewMode = true
                                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                                    setBackgroundColor(android.graphics.Color.BLACK)

                                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                                    cookieManager.setAcceptCookie(true)
                                                    cookieManager.setAcceptThirdPartyCookies(this, true)

                                                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                                                    if (isYt) {
                                                        webViewClient = object : WebViewClient() {
                                                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                                                handler?.proceed()
                                                            }
                                                        }
                                                        webChromeClient = WebChromeClient()

                                                        val finalIframeSrc = when {
                                                            mediaId.startsWith("playlist:") -> {
                                                                val listId = mediaId.removePrefix("playlist:")
                                                                "https://www.youtube.com/embed/videoseries?list=$listId&autoplay=1&playsinline=1&enablejsapi=1"
                                                            }
                                                            mediaId.startsWith("both:") -> {
                                                                val parts = mediaId.removePrefix("both:").split("|")
                                                                val videoId = parts.getOrNull(0) ?: ""
                                                                val listId = parts.getOrNull(1) ?: ""
                                                                "https://www.youtube.com/embed/$videoId?list=$listId&autoplay=1&playsinline=1&enablejsapi=1"
                                                            }
                                                            mediaId.startsWith("yt_music_playlist:") -> {
                                                                val listId = mediaId.removePrefix("yt_music_playlist:")
                                                                "https://www.youtube.com/embed/videoseries?list=$listId&autoplay=1&playsinline=1&enablejsapi=1"
                                                            }
                                                            mediaId.startsWith("yt_music_both:") -> {
                                                                val parts = mediaId.removePrefix("yt_music_both:").split("|")
                                                                val videoId = parts.getOrNull(0) ?: ""
                                                                val listId = parts.getOrNull(1) ?: ""
                                                                "https://www.youtube.com/embed/$videoId?list=$listId&autoplay=1&playsinline=1&enablejsapi=1"
                                                            }
                                                            mediaId.startsWith("yt_music:") -> {
                                                                val videoId = mediaId.removePrefix("yt_music:")
                                                                "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1"
                                                            }
                                                            else -> {
                                                                "https://www.youtube.com/embed/$mediaId?autoplay=1&playsinline=1&enablejsapi=1"
                                                            }
                                                        }

                                                        val (videoId, playlistId) = when {
                                                            mediaId.startsWith("playlist:") -> {
                                                                null to mediaId.removePrefix("playlist:")
                                                            }
                                                            mediaId.startsWith("both:") -> {
                                                                val parts = mediaId.removePrefix("both:").split("|")
                                                                (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
                                                             }
                                                            mediaId.startsWith("yt_music_playlist:") -> {
                                                                null to mediaId.removePrefix("yt_music_playlist:")
                                                            }
                                                            mediaId.startsWith("yt_music_both:") -> {
                                                                val parts = mediaId.removePrefix("yt_music_both:").split("|")
                                                                (parts.getOrNull(0) ?: "") to (parts.getOrNull(1) ?: "")
                                                            }
                                                            mediaId.startsWith("yt_music:") -> {
                                                                mediaId.removePrefix("yt_music:") to null
                                                            }
                                                            else -> {
                                                                mediaId to null
                                                            }
                                                        }

                                                        val videoIdJs = if (videoId != null) "'$videoId'" else "null"
                                                        val listIdJs = if (playlistId != null) "'$playlistId'" else "null"

                                                        val ytHtml = """
                                                            <!DOCTYPE html>
                                                            <html>
                                                            <head>
                                                              <meta charset="utf-8">
                                                              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                              <style>
                                                                body, html {
                                                                  margin: 0; padding: 0;
                                                                  background: #000000;
                                                                  width: 100%; height: 100%;
                                                                  overflow: hidden;
                                                                }
                                                                #player {
                                                                  border: none;
                                                                  width: 100%; height: 100%;
                                                                  position: absolute;
                                                                  top: 0; left: 0;
                                                                }
                                                              </style>
                                                            </head>
                                                            <body>
                                                              <iframe id="player" src="$finalIframeSrc&origin=https://www.youtube.com&widget_referrer=https://www.youtube.com&rel=0" allow="autoplay; encrypted-media; picture-in-picture" allowfullscreen></iframe>
                                                              <script>
                                                                var tag = document.createElement('script');
                                                                tag.src = "https://www.youtube.com/iframe_api";
                                                                document.body.appendChild(tag);


                                                                var player;
                                                                function onYouTubeIframeAPIReady() {
                                                                  var playerConfig = {
                                                                    height: '100%',
                                                                    width: '100%',
                                                                    playerVars: {
                                                                      'playsinline': 1,
                                                                      'autoplay': 1,
                                                                      'rel': 0,
                                                                      'controls': 1,
                                                                      'showinfo': 0,
                                                                      'ecver': 2,
                                                                      'enablejsapi': 1,
                                                                      'origin': 'https://www.youtube.com',
                                                                      'widget_referrer': 'https://www.youtube.com'
                                                                    },
                                                                    events: {
                                                                      'onReady': onPlayerReady,
                                                                      'onError': onPlayerError
                                                                    }
                                                                  };
                                                                  
                                                                  var vId = $videoIdJs;
                                                                  var lId = $listIdJs;
                                                                  
                                                                  if (vId) {
                                                                    playerConfig.videoId = vId;
                                                                  }
                                                                  if (lId) {
                                                                    playerConfig.playerVars.list = lId;
                                                                    if (!vId) {
                                                                      playerConfig.playerVars.listType = 'playlist';
                                                                    }
                                                                  }
                                                                  
                                                                  player = new YT.Player('player', playerConfig);
                                                                }

                                                                function onPlayerReady(event) {
                                                                  event.target.playVideo();
                                                                  setTimeout(function() {
                                                                    try {
                                                                      if (player && player.playVideo) {
                                                                        player.playVideo();
                                                                      }
                                                                    } catch(e) {}
                                                                  }, 1000);
                                                                }

                                                                function onPlayerError(event) {
                                                                  console.log("YouTube API Player Error: " + event.data);
                                                                }
                                                              </script>
                                                            </body>
                                                            </html>
                                                        """.trimIndent()

                                                        loadDataWithBaseURL("https://www.youtube.com", ytHtml, "text/html", "UTF-8", null)
                                                    } else {
                                                        webViewClient = WebViewClient()
                                                        webChromeClient = WebChromeClient()
                                                        val htmlData = if (isSquadBeam) {
                                                            """
                                                                <!DOCTYPE html>
                                                                <html>
                                                                <head>
                                                                  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                                  <style>
                                                                    body, html {
                                                                      margin: 0; padding: 0;
                                                                      background: #0d0e13;
                                                                      color: #FFFFFF;
                                                                      font-family: -apple-system, sans-serif;
                                                                      width: 100%; height: 100%;
                                                                      display: flex; justify-content: center; align-items: center;
                                                                      overflow: hidden;
                                                                    }
                                                                    .container {
                                                                      text-align: center;
                                                                      display: flex;
                                                                      flex-direction: column;
                                                                      align-items: center;
                                                                      justify-content: center;
                                                                      width: 100%; height: 100%;
                                                                    }
                                                                    .beam-visualizer {
                                                                      display: flex;
                                                                      align-items: center;
                                                                      justify-content: center;
                                                                      gap: 4px;
                                                                      height: 60px;
                                                                    }
                                                                    .bar {
                                                                      width: 5px;
                                                                      background: linear-gradient(180deg, #00FFCC 0%, #00bcff 100%);
                                                                      border-radius: 3px;
                                                                      animation: beam-bounce 0.6s ease-in-out infinite alternate;
                                                                      box-shadow: 0 0 8px rgba(0, 255, 204, 0.4);
                                                                    }
                                                                    @keyframes beam-bounce {
                                                                      0% { height: 8px; opacity: 0.3; }
                                                                      100% { height: 50px; opacity: 1; }
                                                                    }
                                                                    .beam-glow {
                                                                      width: 24px;
                                                                      height: 24px;
                                                                      background-color: #00FFCC;
                                                                      border-radius: 50%;
                                                                      position: absolute;
                                                                      filter: blur(12px);
                                                                      opacity: 0.5;
                                                                      animation: glow-pulse 1.5s ease-in-out infinite alternate;
                                                                    }
                                                                    @keyframes glow-pulse {
                                                                      0% { transform: scale(1); opacity: 0.3; }
                                                                      100% { transform: scale(2.2); opacity: 0.7; }
                                                                    }
                                                                    .title {
                                                                      font-size: 11px;
                                                                      font-weight: bold;
                                                                      letter-spacing: 0.6px;
                                                                      color: #FFFFFF;
                                                                      margin-top: 10px;
                                                                      margin-bottom: 4px;
                                                                    }
                                                                    .status {
                                                                      font-size: 8px;
                                                                      letter-spacing: 1.2px;
                                                                      color: #00FFCC;
                                                                      font-weight: 800;
                                                                      text-transform: uppercase;
                                                                    }
                                                                  </style>
                                                                </head>
                                                                <body>
                                                                  <div class="container">
                                                                    <div style="position: relative; display: flex; align-items: center; justify-content: center;">
                                                                      <div class="beam-glow"></div>
                                                                      <div class="beam-visualizer">
                                                                        <div class="bar" style="animation-duration: 0.5s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.1s; animation-duration: 0.7s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.2s; animation-duration: 0.4s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.3s; animation-duration: 0.8s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.4s; animation-duration: 0.5s;"></div>
                                                                      </div>
                                                                    </div>
                                                                    <div class="title">${trackTitle}</div>
                                                                    <div class="status">⚡ SQUAD ULTRA-BEAM AKTİF ⚡</div>
                                                                  </div>
                                                                </body>
                                                                </html>
                                                            """.trimIndent()
                                                        } else {
                                                            """
                                                                <!DOCTYPE html>
                                                                <html>
                                                                <head>
                                                                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                                                  <style>
                                                                    body {
                                                                      margin: 0;
                                                                      padding: 0;
                                                                      background: #0E0F14;
                                                                      display: flex;
                                                                      flex-direction: column;
                                                                      align-items: center;
                                                                      justify-content: center;
                                                                      height: 100vh;
                                                                      font-family: sans-serif;
                                                                      overflow: hidden;
                                                                    }
                                                                    .container {
                                                                      text-align: center;
                                                                      color: #00FFCC;
                                                                      width: 100%;
                                                                    }
                                                                    .visualizer {
                                                                      display: flex;
                                                                      align-items: flex-end;
                                                                      justify-content: center;
                                                                      height: 40px;
                                                                      gap: 4px;
                                                                      margin-bottom: 6px;
                                                                    }
                                                                    .bar {
                                                                      width: 4px;
                                                                      background: #00FFCC;
                                                                      border-radius: 2px 2px 0 0;
                                                                      animation: bounce 0.8s ease-in-out infinite alternate;
                                                                      box-shadow: 0 0 6px #00FFCC;
                                                                    }
                                                                    @keyframes bounce {
                                                                      0% { height: 4px; opacity: 0.3; }
                                                                      100% { height: 32px; opacity: 1; }
                                                                    }
                                                                    .title {
                                                                      font-size: 10px;
                                                                      font-weight: bold;
                                                                      letter-spacing: 0.4px;
                                                                      color: #FFFFFF;
                                                                      margin-bottom: 4px;
                                                                      word-break: break-all;
                                                                      padding: 0 8px;
                                                                    }
                                                                    .status {
                                                                      font-size: 8px;
                                                                      letter-spacing: 1px;
                                                                      color: #00FFCC;
                                                                    }
                                                                    audio {
                                                                      width: 90%;
                                                                      margin-top: 6px;
                                                                    }
                                                                  </style>
                                                                </head>
                                                                <body>
                                                                  <div class="container">
                                                                    <div class="visualizer">
                                                                      <div class="bar" style="animation-duration: 0.7s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.2s; animation-duration: 0.9s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.4s; animation-duration: 0.6s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.1s; animation-duration: 0.8s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.3s; animation-duration: 0.5s;"></div>
                                                                    </div>
                                                                    <div class="title">${trackTitle}</div>
                                                                    <div class="status">SQUAD ORTAK MEDYA SİSTEMİ</div>
                                                                    <audio id="audio" src="${embedUrl}" ${if (isMp3) "controls" else ""} autoplay playsinline></audio>
                                                                  </div>
                                                                </body>
                                                                </html>
                                                            """.trimIndent()
                                                        }
                                                        loadDataWithBaseURL("https://localhost", htmlData, "text/html", "UTF-8", null)
                                                    }
                                                }
                                            },
                                            onRelease = { wv ->
                                                try {
                                                    wv.stopLoading()
                                                    wv.loadUrl("about:blank")
                                                    wv.onPause()
                                                    wv.destroy()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    if (isYt) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "⚠️ Ses gelmiyorsa lütfen video üzerindeki oynat/ses düğmesine basınız.",
                                            color = SquadPrimary.copy(alpha = 0.85f),
                                            fontSize = 8.5.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        // DEPRECATED INSTANCE
                        if (false) {
                            val playingNow = roomNowPlaying ?: ""
                            key(playingNow) {
                                var offsetX by remember { mutableStateOf(0f) }
                                var offsetY by remember { mutableStateOf(0f) }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .widthIn(max = 330.dp)
                                        .fillMaxWidth()
                                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                offsetX += dragAmount.x
                                                offsetY += dragAmount.y
                                            }
                                        }
                                        .background(SquadSurfaceDark, RoundedCornerShape(12.dp))
                                        .border(1.5.dp, SquadPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val isSquadBeam = playingNow.startsWith("squad_beam:")
                                        val isYt = !playingNow.startsWith("mp3:") && !isSquadBeam
                                        val isMp3 = playingNow.startsWith("mp3:")
                                        
                                        val embedUrl = when {
                                            isMp3 -> {
                                                val parts = playingNow.removePrefix("mp3:").split("|")
                                                parts.getOrNull(1) ?: parts.getOrNull(0) ?: ""
                                            }
                                            else -> playingNow // Video ID
                                        }
                                        
                                        val trackTitle = when {
                                            isMp3 -> playingNow.removePrefix("mp3:").split("|").firstOrNull() ?: "Ortak Parça"
                                            isSquadBeam -> {
                                                val name = playingNow.removePrefix("squad_beam:")
                                                "🔊 $name ŞU ANDA SİSTEM SESİNİ YAYINLIYOR"
                                            }
                                            else -> "Sanal YouTube Medyası"
                                        }
                                        
                                        AndroidView(
                                            factory = { context ->
                                                WebView(context).apply {
                                                    layoutParams = ViewGroup.LayoutParams(
                                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                                        ViewGroup.LayoutParams.MATCH_PARENT
                                                    )
                                                    settings.javaScriptEnabled = true
                                                    settings.domStorageEnabled = true
                                                    settings.databaseEnabled = true
                                                    settings.mediaPlaybackRequiresUserGesture = false
                                                    settings.useWideViewPort = true
                                                    settings.loadWithOverviewMode = true
                                                    
                                                    setBackgroundColor(android.graphics.Color.BLACK)
                                                    
                                                    if (isYt) {
                                                        settings.mediaPlaybackRequiresUserGesture = false
                                                        webViewClient = object : WebViewClient() {
                                                            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                                                handler?.proceed()
                                                            }
                                                        }
                                                        webChromeClient = WebChromeClient()
                                                        
                                                        val ytHtml = """
                                                            <!DOCTYPE html>
                                                            <html>
                                                            <head>
                                                              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                              <style>
                                                                body, html {
                                                                  margin: 0; padding: 0;
                                                                  background: #000000;
                                                                  width: 100%; height: 100%;
                                                                  overflow: hidden;
                                                                }
                                                                iframe {
                                                                  border: none;
                                                                  width: 100%; height: 100%;
                                                                }
                                                              </style>
                                                            </head>
                                                            <body>
                                                              <iframe id="player" 
                                                                      src="https://www.youtube.com/embed/$playingNow?autoplay=1&playsinline=1&enablejsapi=1&origin=https://www.youtube.com&widget_referrer=https://www.youtube.com&rel=0" 
                                                                      allow="autoplay; encrypted-media" 
                                                                      allowfullscreen></iframe>
                                                            </body>
                                                            </html>
                                                        """.trimIndent()
                                                        loadDataWithBaseURL("https://www.youtube.com", ytHtml, "text/html", "UTF-8", null)
                                                    } else {
                                                        webViewClient = WebViewClient()
                                                        webChromeClient = WebChromeClient()
                                                        val htmlData = if (isSquadBeam) {
                                                            """
                                                                <!DOCTYPE html>
                                                                <html>
                                                                <head>
                                                                  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                                                  <style>
                                                                    body, html {
                                                                      margin: 0; padding: 0;
                                                                      background: #0d0e13;
                                                                      color: #FFFFFF;
                                                                      font-family: -apple-system, sans-serif;
                                                                      width: 100%; height: 100%;
                                                                      display: flex; justify-content: center; align-items: center;
                                                                      overflow: hidden;
                                                                    }
                                                                    .container {
                                                                      text-align: center;
                                                                      display: flex;
                                                                      flex-direction: column;
                                                                      align-items: center;
                                                                      justify-content: center;
                                                                      width: 100%; height: 100%;
                                                                    }
                                                                    .beam-visualizer {
                                                                      display: flex;
                                                                      align-items: center;
                                                                      justify-content: center;
                                                                      gap: 4px;
                                                                      height: 80px;
                                                                    }
                                                                    .bar {
                                                                      width: 6px;
                                                                      background: linear-gradient(180deg, #00FFCC 0%, #00bcff 100%);
                                                                      border-radius: 4px;
                                                                      animation: beam-bounce 0.6s ease-in-out infinite alternate;
                                                                      box-shadow: 0 0 10px rgba(0, 255, 204, 0.5);
                                                                    }
                                                                    @keyframes beam-bounce {
                                                                      0% { height: 10px; opacity: 0.4; }
                                                                      100% { height: 70px; opacity: 1; }
                                                                    }
                                                                    .beam-glow {
                                                                      width: 32px;
                                                                      height: 32px;
                                                                      background-color: #00FFCC;
                                                                      border-radius: 50%;
                                                                      position: absolute;
                                                                      filter: blur(15px);
                                                                      opacity: 0.6;
                                                                      animation: glow-pulse 1.5s ease-in-out infinite alternate;
                                                                    }
                                                                    @keyframes glow-pulse {
                                                                      0% { transform: scale(1); opacity: 0.4; }
                                                                      100% { transform: scale(2.5); opacity: 0.8; }
                                                                    }
                                                                    .title {
                                                                      font-size: 13px;
                                                                      font-weight: bold;
                                                                      letter-spacing: 0.8px;
                                                                      color: #FFFFFF;
                                                                      margin-top: 15px;
                                                                      margin-bottom: 6px;
                                                                      text-shadow: 0 0 4px rgba(255,255,255,0.2);
                                                                    }
                                                                    .status {
                                                                      font-size: 9px;
                                                                      letter-spacing: 1.5px;
                                                                      color: #00FFCC;
                                                                      font-weight: 800;
                                                                      text-transform: uppercase;
                                                                    }
                                                                  </style>
                                                                </head>
                                                                <body>
                                                                  <div class="container">
                                                                    <div style="position: relative; display: flex; align-items: center; justify-content: center;">
                                                                      <div class="beam-glow"></div>
                                                                      <div class="beam-visualizer">
                                                                        <div class="bar" style="animation-duration: 0.5s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.1s; animation-duration: 0.7s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.2s; animation-duration: 0.4s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.3s; animation-duration: 0.8s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.4s; animation-duration: 0.5s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.5s; animation-duration: 0.7s;"></div>
                                                                        <div class="bar" style="animation-delay: 0.15s; animation-duration: 0.6s;"></div>
                                                                      </div>
                                                                    </div>
                                                                    <div class="title">${trackTitle}</div>
                                                                    <div class="status">⚡ SQUAD ULTRA-BEAM AKTİF ⚡</div>
                                                                  </div>
                                                                </body>
                                                                </html>
                                                            """.trimIndent()
                                                        } else {
                                                            """
                                                                <!DOCTYPE html>
                                                                <html>
                                                                <head>
                                                                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                                                  <style>
                                                                    body {
                                                                      margin: 0;
                                                                      padding: 0;
                                                                      background: #0E0F14;
                                                                      display: flex;
                                                                      flex-direction: column;
                                                                      align-items: center;
                                                                      justify-content: center;
                                                                      height: 100vh;
                                                                      font-family: sans-serif;
                                                                      overflow: hidden;
                                                                    }
                                                                    .container {
                                                                      text-align: center;
                                                                      color: #00FFCC;
                                                                      width: 100%;
                                                                    }
                                                                    .visualizer {
                                                                      display: flex;
                                                                      align-items: flex-end;
                                                                      justify-content: center;
                                                                      height: 52px;
                                                                      gap: 4px;
                                                                      margin-bottom: 8px;
                                                                    }
                                                                    .bar {
                                                                      width: 5px;
                                                                      background: #00FFCC;
                                                                      border-radius: 2px 2px 0 0;
                                                                      animation: bounce 0.8s ease-in-out infinite alternate;
                                                                      box-shadow: 0 0 8px #00FFCC;
                                                                    }
                                                                    @keyframes bounce {
                                                                      0% { height: 4px; opacity: 0.3; }
                                                                      100% { height: 44px; opacity: 1; }
                                                                    }
                                                                    .title {
                                                                      font-size: 11px;
                                                                      font-weight: bold;
                                                                      letter-spacing: 0.5px;
                                                                      color: #FFFFFF;
                                                                      margin-bottom: 4px;
                                                                      word-break: break-all;
                                                                      padding: 0 10px;
                                                                    }
                                                                    .status {
                                                                      font-size: 8px;
                                                                      letter-spacing: 1px;
                                                                      color: #00FFCC;
                                                                    }
                                                                    audio {
                                                                      width: 90%;
                                                                      margin-top: 8px;
                                                                    }
                                                                  </style>
                                                                </head>
                                                                <body>
                                                                  <div class="container">
                                                                    <div class="visualizer">
                                                                      <div class="bar" style="animation-duration: 0.7s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.2s; animation-duration: 0.9s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.4s; animation-duration: 0.6s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.1s; animation-duration: 0.8s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.3s; animation-duration: 0.5s;"></div>
                                                                      <div class="bar" style="animation-delay: 0.5s; animation-duration: 0.7s;"></div>
                                                                    </div>
                                                                    <div class="title">${trackTitle}</div>
                                                                    <div class="status">SQUAD ORTAK MEDYA SİSTEMİ</div>
                                                                    <audio id="audio" src="${embedUrl}" ${if (isMp3) "controls" else ""} autoplay playsinline></audio>
                                                                  </div>
                                                                </body>
                                                                </html>
                                                            """.trimIndent()
                                                        }
                                                        loadDataWithBaseURL("https://localhost", htmlData, "text/html", "UTF-8", null)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .widthIn(max = 330.dp)
                                                .fillMaxWidth()
                                                .aspectRatio(16f/9f)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(2.dp, SquadPrimary, RoundedCornerShape(6.dp))
                                        )
                                        if (isYt) {
                                            Text(
                                                text = "⚠️ Ses gelmiyorsa ekrandaki videonun oynat/ses düğmesine basınız.",
                                                color = SquadPrimary.copy(alpha = 0.8f),
                                                fontSize = 9.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier
                                                .widthIn(max = 330.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isYt) "📺 OYNATILIYOR: YOUTUBE" else if (isSquadBeam) "🔊 SQUAD BEAM ULTRA-SES" else "🎵 OYNATILIYOR: ORTAK MP3",
                                                    color = SquadPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = trackTitle,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(4.dp))
                                            
                                            if (!isSquadBeam) {
                                                Button(
                                                    onClick = {
                                                        try {
                                                            val finalIntentUrl = if (isYt) "https://www.youtube.com/watch?v=$playingNow" else embedUrl
                                                            val intent = android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse(finalIntentUrl)
                                                            )
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SquadPrimary),
                                                    border = BorderStroke(1.dp, SquadPrimary.copy(alpha = 0.3f)),
                                                    shape = RoundedCornerShape(4.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Dışarıda Aç", fontSize = 8.sp, letterSpacing = 0.5.sp)
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Top Right Close/X button to close the medium entirely!
                                    IconButton(
                                        onClick = { viewModel.updateRoomNowPlaying(roomId, null) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 10.dp, end = 10.dp)
                                            .size(28.dp)
                                            .background(Color.Black.copy(alpha = 0.85f), CircleShape)
                                            .border(1.5.dp, SquadPrimary, CircleShape)
                                            .pointerInput(Unit) {
                                                detectTapGestures {
                                                    viewModel.updateRoomNowPlaying(roomId, null)
                                                }
                                            }
                                            .zIndex(1000f) // Keep it completely on top of all system webviews
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Medya Kapat",
                                            tint = SquadPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        } // END OF DEPRECATED INSTANCE
                    }
                }
        }
    }
}
}