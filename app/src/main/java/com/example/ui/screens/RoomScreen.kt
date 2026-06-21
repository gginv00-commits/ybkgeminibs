package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ChatMessage
import com.example.data.entity.SyncRoom
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomId: Int,
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val room by viewModel.getRoom(roomId).collectAsStateWithLifecycle()
    val messages by viewModel.getMessages(roomId).collectAsStateWithLifecycle()
    var isMuted by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(room?.name ?: "Yükleniyor...", fontWeight = FontWeight.Bold, color = SquadTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = SquadTextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { isMuted = !isMuted }) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Susturmayı Aç/Kapat",
                            tint = if (isMuted) SquadRed else SquadTextPrimary
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Headset, contentDescription = "Kulaklığı Kapat", tint = SquadTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SquadSurfaceDark)
            )
        },
        containerColor = SquadBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Media Player Area
            MediaPlayerMock(room)

            // Voice Channel Users
            VoiceChannelSection()

            // Chat Messages
            ChatSection(
                messages = messages,
                modifier = Modifier.weight(1f),
                onSendMessage = { content ->
                    viewModel.sendMessage(roomId, "Sen", content)
                }
            )
        }
    }
}

@Composable
fun MediaPlayerMock(room: SyncRoom?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Oynat",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Simüle Edilmiş YouTube Oynatıcı - Senkronizasyon Bekleniyor",
                color = Color.White,
                fontSize = 14.sp
            )
        }
        
        // Progress bar at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(4.dp)
                .background(SquadHover)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .background(SquadRed)
            )
        }
    }
}

@Composable
fun VoiceChannelSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SquadSurfaceLayer)
            .padding(12.dp)
    ) {
        Text("Ses Kanalı (3 Bağlı)", color = SquadTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(3) { index ->
                UserAvatar(
                    name = listOf("Sen", "Gizem", "Emre")[index],
                    isSpeaking = index == 1
                )
            }
        }
    }
}

@Composable
fun UserAvatar(name: String, isSpeaking: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (name == "Sen") SquadPrimary else SquadHover),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (isSpeaking) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        // This simulates a green ring for speaking
                        .padding(2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(name, color = SquadTextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun ChatSection(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onSendMessage: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                ChatMessageItem(msg)
            }
        }

        // Input Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SquadSurfaceLayer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Odaya mesaj gönder...", color = SquadTextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = SquadTextPrimary,
                    unfocusedTextColor = SquadTextPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank()) {
                            onSendMessage(text)
                            text = ""
                        }
                    }
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder", tint = SquadPrimary)
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeString = timeFormatter.format(Date(message.timestamp))

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (message.username == "Sen") SquadPrimary else SquadHover),
            contentAlignment = Alignment.Center
        ) {
            Text(message.username.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(message.username, color = SquadTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(timeString, color = SquadTextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(message.content, color = SquadTextPrimary, fontSize = 15.sp)
        }
    }
}
