package com.infoj143.wechat20.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * --- DEVELOPER GUIDE: GOOGLE-SERVICES.JSON LOCATION ---
 * 
 * To fully integrate Firebase with your WeChat 2.0 app:
 * 1. Download your 'google-services.json' file from the Firebase Console (https://console.firebase.google.com/).
 * 2. Place the 'google-services.json' file into the '/app/' directory (i.e. at /app/google-services.json).
 * 3. Make sure to enable the 'Phone' Sign-in provider under Firebase Console -> Authentication -> Sign-in method.
 * 4. Configure your Firestore Database rules and Firebase Storage rules appropriately.
 * 
 * If 'google-services.json' is missing, the app will run in a polished local interactive simulation mode.
 */
object FirebaseConfig {
    private const val TAG = "FirebaseConfig"
    
    val isFirebaseAvailable: Boolean by lazy {
        try {
            val app = FirebaseApp.getInstance()
            Log.d(TAG, "Firebase initialized successfully: ${app.name}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Firebase is not initialized. Using local interactive simulation mode.")
            false
        }
    }

    val auth: FirebaseAuth?
        get() = if (isFirebaseAvailable) FirebaseAuth.getInstance() else null

    val firestore: FirebaseFirestore?
        get() = if (isFirebaseAvailable) FirebaseFirestore.getInstance() else null

    val storage: FirebaseStorage?
        get() = if (isFirebaseAvailable) FirebaseStorage.getInstance() else null
}
