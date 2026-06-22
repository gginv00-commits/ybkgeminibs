package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase

class FirebaseManager private constructor(context: Context) {
    var database: FirebaseDatabase? = null
        private set

    init {
        val apiKey = BuildConfig.FIREBASE_API_KEY
        val appId = BuildConfig.FIREBASE_APP_ID
        val dbUrl = BuildConfig.FIREBASE_DB_URL
        val projectId = BuildConfig.FIREBASE_PROJECT_ID

        val isConfigured = apiKey.isNotEmpty() && !apiKey.contains("YOUR_") &&
                appId.isNotEmpty() && !appId.contains("YOUR_") &&
                dbUrl.isNotEmpty() && !dbUrl.contains("YOUR_") &&
                projectId.isNotEmpty() && !projectId.contains("YOUR_")

        if (isConfigured) {
            try {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .setDatabaseUrl(dbUrl)
                    .setProjectId(projectId)
                    .build()
                
                val app = if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context, options)
                } else {
                    FirebaseApp.getInstance()
                }
                
                if (app != null) {
                    database = FirebaseDatabase.getInstance(app)
                    try {
                        database?.setPersistenceEnabled(true)
                        Log.d("FirebaseManager", "Firebase offline persistence enabled")
                    } catch (e: Exception) {
                        Log.w("FirebaseManager", "Could not enable persistence: ${e.message}")
                    }
                    Log.d("FirebaseManager", "Firebase Realtime Database initialized successfully at: $dbUrl")
                } else {
                    Log.e("FirebaseManager", "Failed to initialize FirebaseApp")
                }
            } catch (e: Exception) {
                Log.e("FirebaseManager", "Error initializing Firebase: ${e.message}", e)
            }
        } else {
            Log.w("FirebaseManager", "Firebase is not fully configured. Starting helper in Simulation Mode.")
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
