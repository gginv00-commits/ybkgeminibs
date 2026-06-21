package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onNavigateToDashboard: () -> Unit
) {
    var username by remember { mutableStateOf("player_one") }
    var roomCode by remember { mutableStateOf("") }

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
            modifier = Modifier.fillMaxSize(),
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                "// SQUAD-UP PROTOCOL",
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
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = SquadTextPrimary,
                lineHeight = 44.sp,
                letterSpacing = (-1).sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Arkadaşınla anında bağlan, C tuşuyla bas-konuş, ortak YouTube müziği senkron dinle. Sıfır kurulum, oda kodu yeter.",
                color = SquadTextSecondary,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Login Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SquadHover, RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(24.dp)
            ) {
                Text("KULLANICI ADI", color = SquadTextSecondary, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SquadSurfaceDark,
                        unfocusedContainerColor = SquadSurfaceDark,
                        focusedBorderColor = SquadHover,
                        unfocusedBorderColor = SquadHover,
                        focusedTextColor = SquadTextPrimary,
                        unfocusedTextColor = SquadTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(2.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onNavigateToDashboard,
                    colors = ButtonDefaults.buttonColors(containerColor = SquadPrimary),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(text = "YENİ ODA KUR  →", fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Divider(modifier = Modifier.weight(1f), color = SquadHover)
                    Text(" VEYA ", color = SquadTextSecondary, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    Divider(modifier = Modifier.weight(1f), color = SquadHover)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("ODA KODU", color = SquadTextSecondary, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = roomCode,
                        onValueChange = { roomCode = it },
                        placeholder = { Text("A B C 2 3 X", color = SquadTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SquadSurfaceDark,
                            unfocusedContainerColor = SquadSurfaceDark,
                            focusedBorderColor = SquadHover,
                            unfocusedBorderColor = SquadHover,
                            focusedTextColor = SquadTextPrimary,
                            unfocusedTextColor = SquadTextPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onNavigateToDashboard,
                        colors = ButtonDefaults.buttonColors(containerColor = SquadHover),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.height(56.dp).width(100.dp)
                    ) {
                        Text(text = "KATIL", fontSize = 14.sp, color = SquadTextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
