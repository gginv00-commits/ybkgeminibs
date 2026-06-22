package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    viewModel: MainViewModel,
    onNavigateToRoom: (Int) -> Unit
) {
    var username by remember { mutableStateOf(viewModel.currentUsername.value) }
    var roomCode by remember { mutableStateOf("") }
    var showNameError by remember { mutableStateOf(false) }
    var codeError by remember { mutableStateOf<String?>(null) }

    val handleCreate = {
        if (username.isBlank()) {
            showNameError = true
        } else {
            viewModel.setSavedUsername(username)
            viewModel.createRoom(name = "$username'ın Odası") { newRoomId ->
                onNavigateToRoom(newRoomId)
            }
        }
    }

    val handleJoin = {
        if (username.isBlank()) {
            showNameError = true
        } else if (roomCode.isBlank()) {
            codeError = "Lütfen bir oda kodu girin."
        } else {
            viewModel.setSavedUsername(username)
            viewModel.joinRoomByCode(roomCode) { roomId ->
                if (roomId != null) {
                    onNavigateToRoom(roomId)
                } else {
                    codeError = "Yanlış kod, oda bulunamadı."
                }
            }
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            val activeRoomId = viewModel.activeRoomId
            val activeRoomCode = viewModel.activeRoomCode
            if (activeRoomId != null && activeRoomCode != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .background(SquadPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, SquadPrimary, RoundedCornerShape(4.dp))
                        .clickable { onNavigateToRoom(activeRoomId) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(SquadPrimary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ BAĞLI KANALINIZ AKTİF",
                            color = SquadPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Oda Kodu: $activeRoomCode",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "GERİ DÖN →",
                        color = SquadPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "// SQUAD-UP PROTOKOLÜ",
                color = SquadPrimary,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            val title = buildAnnotatedString {
                append("OYUNDA\nKONUŞ,\n")
                withStyle(SpanStyle(color = SquadPrimary)) {
                    append("MÜZİK ")
                }
                append("AÇ.")
            }
            
            Text(
                text = title,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = SquadTextPrimary,
                lineHeight = 36.sp,
                letterSpacing = (-1).sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Arkadaşınla anında bağlan, C tuşuyla bas-konuş, ortak YouTube müziği senkron dinle. Sıfır kurulum, oda kodu yeter.",
                color = SquadTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Login Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SquadHover, RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(24.dp)
            ) {
                Text("KULLANICI ADI", color = if (showNameError) SquadRed else SquadTextSecondary, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        showNameError = false 
                    },
                    isError = showNameError,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SquadSurfaceDark,
                        unfocusedContainerColor = SquadSurfaceDark,
                        focusedBorderColor = if (showNameError) SquadRed else SquadHover,
                        unfocusedBorderColor = if (showNameError) SquadRed else SquadHover,
                        focusedTextColor = SquadTextPrimary,
                        unfocusedTextColor = SquadTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(2.dp)
                )
                if (showNameError) {
                    Text("Lütfen bir kullanıcı adı girin.", color = SquadRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // Create Action
                Button(
                    onClick = handleCreate,
                    colors = ButtonDefaults.buttonColors(containerColor = SquadPrimary),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(text = "YENİ ODA KUR  →", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Divider(modifier = Modifier.weight(1f), color = SquadHover)
                    Text(" VEYA ", color = SquadTextSecondary, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    Divider(modifier = Modifier.weight(1f), color = SquadHover)
                }
                
                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SquadHover, RoundedCornerShape(2.dp))
                        .background(SquadSurfaceDark),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { 
                            roomCode = it 
                            codeError = null
                        },
                        placeholder = { Text("5 HANELİ KOD", color = SquadTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = SquadTextPrimary,
                            unfocusedTextColor = SquadTextPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    Button(
                        onClick = handleJoin,
                        colors = ButtonDefaults.buttonColors(containerColor = SquadHover),
                        shape = RoundedCornerShape(bottomEnd = 2.dp, topEnd = 2.dp, topStart = 0.dp, bottomStart = 0.dp),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(text = "KATIL", fontSize = 14.sp, color = SquadTextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                codeError?.let {
                    Text(it, color = SquadRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
