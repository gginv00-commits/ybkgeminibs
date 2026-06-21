package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.SyncRoom
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToRoom: (Int) -> Unit,
    onNavigateToAdmin: () -> Unit
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aktif Odalar", fontWeight = FontWeight.Bold, color = SquadTextPrimary) },
                actions = {
                    if (role == "Admin") {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = "Yönetici Paneli", tint = SquadPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SquadSurfaceDark
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = SquadPrimary,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Oda Oluştur")
            }
        },
        containerColor = SquadBackground
    ) { innerPadding ->
        if (rooms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Aktif oda bulunmuyor. Başlamak için bir tane oluşturun!", color = SquadTextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rooms, key = { it.id }) { room ->
                    RoomCard(room = room, onClick = { onNavigateToRoom(room.id) })
                }
            }
        }

        if (showCreateDialog) {
            CreateRoomDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, type ->
                    viewModel.createRoom(name, type) { newRoomId ->
                        showCreateDialog = false
                        onNavigateToRoom(newRoomId)
                    }
                }
            )
        }
    }
}

@Composable
fun RoomCard(room: SyncRoom, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SquadSurfaceLayer)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SquadHover),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (room.type == "Private") Icons.Default.Lock else Icons.Default.Public,
                contentDescription = null,
                tint = SquadTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(room.name, color = SquadTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = "Aktif Kullanıcılar", tint = SquadGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${room.activeUsers} kişi seste", color = SquadTextSecondary, fontSize = 12.sp)
            }
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = SquadGreen),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Katıl", fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SquadSurfaceLayer,
        titleContentColor = SquadTextPrimary,
        textContentColor = SquadTextSecondary,
        title = { Text("Yeni Oda Oluştur", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Oda Adı") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SquadPrimary,
                        unfocusedBorderColor = SquadHover,
                        focusedTextColor = SquadTextPrimary,
                        unfocusedTextColor = SquadTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SquadPrimary, checkedTrackColor = SquadHover)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (isPrivate) "Gizli Oda" else "Açık Oda")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (roomName.isNotBlank()) {
                        onCreate(roomName, if (isPrivate) "Private" else "Public")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SquadPrimary)
            ) {
                Text("Oluştur", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal", color = SquadTextSecondary)
            }
        }
    )
}
