package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.example.data.entity.User
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()

    if (role != "Admin") {
        AccessDeniedScreen(onNavigateBack)
        return
    }

    val users by viewModel.users.collectAsStateWithLifecycle()
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Genel Bakış", "Kullanıcılar", "Odalar")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yönetici Paneli", fontWeight = FontWeight.Bold, color = SquadTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = SquadTextPrimary)
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
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SquadSurfaceDark,
                contentColor = SquadTextPrimary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = SquadPrimary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.Bold, color = if (selectedTab == index) SquadPrimary else SquadTextSecondary) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> OverviewTab(users, rooms)
                    1 -> UsersTab(users, onToggleBan = { user -> viewModel.setBanStatus(user.id, !user.isBanned) })
                    2 -> RoomsAdminTab(rooms, onDeleteRoom = { room -> viewModel.deleteRoom(room.id) })
                }
            }
        }
    }
}

@Composable
fun AccessDeniedScreen(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SquadBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Block, contentDescription = "Reddedildi", tint = SquadRed, modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Erişim Reddedildi", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = SquadTextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Bu sayfayı görüntüleme yetkiniz yok.", color = SquadTextSecondary)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateBack, colors = ButtonDefaults.buttonColors(containerColor = SquadPrimary)) {
            Text("Geri Dön", color = Color.Black)
        }
    }
}

@Composable
fun OverviewTab(users: List<User>, rooms: List<SyncRoom>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Sistem İstatistikleri", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SquadTextPrimary)
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Toplam Kullanıcı", users.size.toString(), Modifier.weight(1f))
                StatCard("Aktif Odalar", rooms.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Yasaklı Kullanıcılar", users.count { it.isBanned }.toString(), Modifier.weight(1f))
                StatCard("Yöneticiler", users.count { it.role == "Admin" }.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SquadSurfaceLayer)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SquadPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 14.sp, color = SquadTextSecondary)
    }
}

@Composable
fun UsersTab(users: List<User>, onToggleBan: (User) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(users, key = { it.id }) { user ->
            UserAdminCard(user, onToggleBan)
        }
    }
}

@Composable
fun UserAdminCard(user: User, onToggleBan: (User) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SquadSurfaceLayer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, color = SquadTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (user.role == "Admin") {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(SquadPrimary).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("Yönetici", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (user.isBanned) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(SquadRed).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text("Yasaklı", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            val dateStr = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date(user.joinedAt))
            Text("Katılım: $dateStr", color = SquadTextSecondary, fontSize = 12.sp)
        }
        if (user.role != "Admin") { // Prevent banning other admins for simplicity
            IconButton(onClick = { onToggleBan(user) }) {
                Icon(
                    imageVector = if (user.isBanned) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = "Yasağı Değiştir",
                    tint = if (user.isBanned) SquadGreen else SquadRed
                )
            }
        }
    }
}

@Composable
fun RoomsAdminTab(rooms: List<SyncRoom>, onDeleteRoom: (SyncRoom) -> Unit) {
    if (rooms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aktif oda bulunmuyor.", color = SquadTextSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rooms, key = { it.id }) { room ->
                RoomAdminCard(room, onDeleteRoom)
            }
        }
    }
}

@Composable
fun RoomAdminCard(room: SyncRoom, onDeleteRoom: (SyncRoom) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SquadSurfaceLayer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(room.name, color = SquadTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val typeStr = if (room.type == "Private") "Gizli" else "Açık"
            Text("Tür: $typeStr • Aktif: ${room.activeUsers}", color = SquadTextSecondary, fontSize = 12.sp)
        }
        IconButton(onClick = { onDeleteRoom(room) }) {
            Icon(Icons.Default.Delete, contentDescription = "Odayı Sil", tint = SquadRed)
        }
    }
}
