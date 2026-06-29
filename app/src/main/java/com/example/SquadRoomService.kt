package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

class SquadRoomService : Service() {

    private var dummyListener: ValueEventListener? = null
    private var roomCode: String? = null

    companion object {
        private const val CHANNEL_ID = "squad_room_active_channel"
        private const val NOTIFICATION_ID = 48512
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rCode = intent?.getStringExtra("roomCode") ?: "..."
        val username = intent?.getStringExtra("username") ?: "User"
        this.roomCode = rCode
        val notification = createNotification(rCode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasMic) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID, 
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                }
            } catch (e: Exception) {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Keep Firebase Connection Alive & actively write user presence to prevent background disconnect freeze
        try {
            val fb = FirebaseDatabase.getInstance()
            fb.goOnline()
            
            // Maintain member node in room list so they never get disconnected
            val memberNode = fb.getReference("rooms").child(rCode).child("members").child(username)
            val updates = mapOf(
                "isMuted" to (intent?.getBooleanExtra("isMuted", true) ?: true),
                "isSpeaking" to false,
                "name" to username
            )
            memberNode.updateChildren(updates)
            memberNode.onDisconnect().removeValue()

            val ref = fb.getReference("rooms").child(rCode).child("members")
            
            // Remove previous if exists
            dummyListener?.let { ref.removeEventListener(it) }
            
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Simple active subscription to maintain WebSocket connectivity
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            dummyListener = listener
            ref.addValueEventListener(listener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        try {
            val rCode = roomCode
            val listener = dummyListener
            if (rCode != null && listener != null) {
                FirebaseDatabase.getInstance().getReference("rooms").child(rCode).child("members").removeEventListener(listener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(roomCode: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ Squad Bağlantısı Aktif")
            .setContentText("Oda: $roomCode • Arka planda senkronizasyon açık.")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Squad Aktif Oda Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Aktif odadayken ses ve yayın senkronizasyonunun arka planda devam etmesini sağlar."
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
