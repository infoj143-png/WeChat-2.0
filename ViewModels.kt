package com.infoj143.wechat20.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.infoj143.wechat20.data.FirebaseConfig
import com.infoj143.wechat20.models.*
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

interface AuthRepository {
    val currentUser: StateFlow<User?>
    suspend fun sendOtp(
        activity: android.app.Activity,
        phoneNumber: String,
        forceResendingToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken? = null,
        onCodeSent: (String, com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?) -> Unit,
        onVerificationCompleted: (User, Boolean) -> Unit,
        onFailure: (String) -> Unit
    )
    suspend fun verifyOtp(
        verificationId: String,
        otpCode: String,
        onResult: (Result<User>, Boolean) -> Unit
    )
    suspend fun logout()
    suspend fun updateProfile(name: String, status: String, profilePhotoUrl: String): Result<User>
}

interface UserRepository {
    val allUsers: StateFlow<List<User>>
    suspend fun searchUsers(query: String): List<User>
    fun observeUserStatus(userId: String): StateFlow<User?>
}

interface ChatRepository {
    val chatSessions: StateFlow<List<ChatSession>>
    fun getMessages(peerId: String): StateFlow<List<Message>>
    suspend fun sendMessage(peerId: String, content: String, type: MessageType, durationSec: Int = 0, mediaUrl: String? = null): Result<Message>
    suspend fun setTypingState(peerId: String, isTyping: Boolean)
    suspend fun markAsSeen(peerId: String)
}

interface CallRepository {
    val currentCall: StateFlow<CallSession?>
    fun startCall(peer: User, isVideo: Boolean)
    fun acceptCall()
    fun rejectCall()
    fun endCall()
    fun simulateIncomingCall(peer: User, isVideo: Boolean)
}

class AppRepositoryManager : AuthRepository, UserRepository, ChatRepository, CallRepository {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // --- STATE STORES ---
    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    override val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()

    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    override val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    private val messagesStore = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val _currentCall = MutableStateFlow<CallSession?>(null)
    override val currentCall: StateFlow<CallSession?> = _currentCall.asStateFlow()

    // Mock profiles for simulated directory
    private val mockProfiles = listOf(
        User("u1", "+923001234567", "Ayesha Khan", "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&q=80&w=200", "Hey there! WeChatting 🍿", true),
        User("u2", "+923129876543", "Zain Ahmed", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=200", "Focused / Busy 👨‍💻", false),
        User("u3", "+15550192834", "Sarah Jenkins", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=200", "Loving WeChat 2.0 gradients ✨", true),
        User("u4", "+923334567890", "Kamran Malik", "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=200", "Pakistan No.1 dev 🇵🇰", true),
        User("u5", "+447700900077", "Emma Watson", "https://images.unsplash.com/photo-1438761681033-6461ffad8d80?auto=format&fit=crop&q=80&w=200", "At the library 📚", false)
    )

    private var verificationIdPlaceholder = "mock_verification_id_12345"

    init {
        // Initialize with default mock list
        _allUsers.value = mockProfiles
        
        // Setup initial mock messages to showcase beautiful recent chat screens immediately
        mockProfiles.forEach { peer ->
            val flow = MutableStateFlow<List<Message>>(emptyList())
            messagesStore[peer.id] = flow
            
            // Insert 1 or 2 initial chats to make it look active
            val initialMsgs = listOf(
                Message(
                    senderId = peer.id,
                    receiverId = "me",
                    content = if (peer.id == "u1") "Hi! Are we meeting today for chai?" else "Hey, have you checked the new design of WeChat 2.0?",
                    timestamp = System.currentTimeMillis() - 3600000 * 2,
                    type = MessageType.TEXT,
                    status = MessageStatus.SEEN
                )
            )
            flow.value = initialMsgs
        }
        rebuildChatSessions()
    }

    private fun rebuildChatSessions() {
        val me = _currentUser.value ?: User("me", "+923000000000", "Guest", "", "Active")
        val sessions = _allUsers.value.map { peer ->
            val msgs = messagesStore[peer.id]?.value ?: emptyList()
            val last = msgs.lastOrNull()
            val unreadCount = if (last != null && last.senderId == peer.id && last.status != MessageStatus.SEEN) 1 else 0
            ChatSession(peer, last, unreadCount)
        }.filter { it.lastMessage != null }.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
        _chatSessions.value = sessions
    }

    private var pendingPhoneNumber = ""

    private fun handleUserSignInSuccess(
        uid: String,
        phone: String,
        onDone: (User, Boolean) -> Unit
    ) {
        val db = FirebaseConfig.firestore
        if (db != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString("name") ?: ""
                        val status = document.getString("status") ?: "Available"
                        val profilePhotoUrl = document.getString("profilePhotoUrl") ?: ""
                        
                        val user = User(
                            id = uid,
                            phoneNumber = phone,
                            name = name,
                            status = status,
                            profilePhotoUrl = profilePhotoUrl,
                            isOnline = true
                        )
                        _currentUser.value = user
                        val exists = name.trim().isNotEmpty()
                        onDone(user, exists)
                    } else {
                        val user = User(id = uid, phoneNumber = phone, isOnline = true)
                        _currentUser.value = user
                        onDone(user, false)
                    }
                }
                .addOnFailureListener { e ->
                    val user = User(id = uid, phoneNumber = phone, isOnline = true)
                    _currentUser.value = user
                    onDone(user, false)
                }
        } else {
            val user = User(id = uid, phoneNumber = phone, isOnline = true)
            _currentUser.value = user
            onDone(user, false)
        }
    }

    // --- AUTH REPOSITORY ---
    override suspend fun sendOtp(
        activity: android.app.Activity,
        phoneNumber: String,
        forceResendingToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?,
        onCodeSent: (String, com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken?) -> Unit,
        onVerificationCompleted: (User, Boolean) -> Unit,
        onFailure: (String) -> Unit
    ) {
        pendingPhoneNumber = phoneNumber
        delay(800) // Beautiful dynamic loader delay
        if (FirebaseConfig.isFirebaseAvailable) {
            val auth = FirebaseConfig.auth ?: run {
                onFailure("Firebase Auth is not available.")
                return
            }
            val callbacks = object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    Log.d("AuthRepository", "onVerificationCompleted: $credential")
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val firebaseUser = task.result?.user
                                if (firebaseUser != null) {
                                    handleUserSignInSuccess(firebaseUser.uid, firebaseUser.phoneNumber ?: phoneNumber) { user, exists ->
                                        onVerificationCompleted(user, exists)
                                    }
                                } else {
                                    onFailure("Automatic verification sign-in failed.")
                                }
                            } else {
                                onFailure(task.exception?.localizedMessage ?: "Automatic verification failed.")
                            }
                        }
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    Log.e("AuthRepository", "onVerificationFailed callback triggered.", e)
                    
                    val className = e.javaClass.name
                    val message = e.message ?: "No error message provided."
                    var errorCode = "N/A"
                    
                    if (e is com.google.firebase.auth.FirebaseAuthException) {
                        errorCode = e.errorCode
                    }
                    
                    Log.e("AuthRepository", "--- FIREBASE EXCEPTION DETAILS ---")
                    Log.e("AuthRepository", "Exception Class: $className")
                    Log.e("AuthRepository", "Firebase Error Code: $errorCode")
                    Log.e("AuthRepository", "Exception Message: $message")
                    Log.e("AuthRepository", "----------------------------------")
                    
                    val outputError = "Firebase Error Details:\n" +
                            "• Exception: $className\n" +
                            "• Code: $errorCode\n" +
                            "• Message: $message"
                            
                    onFailure(outputError)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d("AuthRepository", "onCodeSent: $verificationId")
                    onCodeSent(verificationId, token)
                }
            }

            val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            if (forceResendingToken != null) {
                optionsBuilder.setForceResendingToken(forceResendingToken)
            }

            PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
        } else {
            verificationIdPlaceholder = "mock_verification_id_" + (100000..999999).random()
            onCodeSent(verificationIdPlaceholder, null)
        }
    }

    override suspend fun verifyOtp(
        verificationId: String,
        otpCode: String,
        onResult: (Result<User>, Boolean) -> Unit
    ) {
        delay(1000)
        if (FirebaseConfig.isFirebaseAvailable) {
            val auth = FirebaseConfig.auth ?: run {
                onResult(Result.failure(Exception("Firebase Auth is not available.")), false)
                return
            }
            try {
                val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val firebaseUser = task.result?.user
                            if (firebaseUser != null) {
                                handleUserSignInSuccess(firebaseUser.uid, firebaseUser.phoneNumber ?: pendingPhoneNumber) { user, exists ->
                                    onResult(Result.success(user), exists)
                                }
                            } else {
                                onResult(Result.failure(Exception("Verification failed to retrieve user.")), false)
                            }
                        } else {
                            val exception = task.exception
                            val friendlyError = when (exception) {
                                is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Wrong OTP code entered."
                                else -> exception?.localizedMessage ?: "OTP Verification failed."
                            }
                            onResult(Result.failure(Exception(friendlyError)), false)
                        }
                    }
            } catch (e: Exception) {
                onResult(Result.failure(e), false)
            }
        } else {
            if (otpCode == "123456" || otpCode.length == 6) {
                val enteredPhone = pendingPhoneNumber.trim()
                val matchedMock = mockProfiles.find { mock ->
                    mock.phoneNumber.endsWith(enteredPhone.takeLast(7)) || enteredPhone.endsWith(mock.phoneNumber.takeLast(7))
                }
                val user = if (matchedMock != null) {
                    matchedMock.copy(isOnline = true)
                } else {
                    User(
                        id = "me",
                        phoneNumber = pendingPhoneNumber,
                        name = "Zayed Dev",
                        profilePhotoUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=200",
                        status = "WeChat 2.0 is incredible! 🇵🇰",
                        isOnline = true
                    )
                }
                _currentUser.value = user
                val exists = matchedMock != null
                onResult(Result.success(user), exists)
            } else {
                onResult(Result.failure(Exception("Incorrect OTP. Please enter '123456' or any 6 digits for testing.")), false)
            }
        }
    }

    override suspend fun logout() {
        _currentUser.value = null
        if (FirebaseConfig.isFirebaseAvailable) {
            FirebaseConfig.auth?.signOut()
        }
    }

    override suspend fun updateProfile(name: String, status: String, profilePhotoUrl: String): Result<User> {
        delay(500)
        val current = _currentUser.value ?: User("me")
        val updated = current.copy(name = name, status = status, profilePhotoUrl = profilePhotoUrl)
        _currentUser.value = updated
        
        if (FirebaseConfig.isFirebaseAvailable) {
            val db = FirebaseConfig.firestore
            if (db != null && current.id.isNotEmpty() && current.id != "me") {
                val userMap = hashMapOf(
                    "id" to updated.id,
                    "phoneNumber" to updated.phoneNumber,
                    "name" to updated.name,
                    "status" to updated.status,
                    "profilePhotoUrl" to updated.profilePhotoUrl,
                    "isOnline" to true
                )
                db.collection("users").document(updated.id).set(userMap)
            }
        }
        return Result.success(updated)
    }

    // --- USER REPOSITORY ---
    override suspend fun searchUsers(query: String): List<User> {
        if (query.isEmpty()) return _allUsers.value
        return _allUsers.value.filter {
            it.name.contains(query, ignoreCase = true) || it.phoneNumber.contains(query)
        }
    }

    override fun observeUserStatus(userId: String): StateFlow<User?> {
        val userFlow = MutableStateFlow<User?>(null)
        scope.launch {
            _allUsers.collect { list ->
                userFlow.value = list.find { it.id == userId }
            }
        }
        return userFlow.asStateFlow()
    }

    // --- CHAT REPOSITORY ---
    override fun getMessages(peerId: String): StateFlow<List<Message>> {
        return messagesStore.getOrPut(peerId) { MutableStateFlow(emptyList()) }.asStateFlow()
    }

    override suspend fun sendMessage(peerId: String, content: String, type: MessageType, durationSec: Int, mediaUrl: String?): Result<Message> {
        if (content.trim().isEmpty() && type == MessageType.TEXT) {
            return Result.failure(Exception("Cannot send empty message"))
        }

        val myMsg = Message(
            senderId = "me",
            receiverId = peerId,
            content = content,
            type = type,
            durationSec = durationSec,
            mediaUrl = mediaUrl,
            status = MessageStatus.SENT,
            timestamp = System.currentTimeMillis()
        )

        // Insert into list
        val listFlow = messagesStore.getOrPut(peerId) { MutableStateFlow(emptyList()) }
        listFlow.update { it + myMsg }
        rebuildChatSessions()

        // Simulate message delivery status change
        scope.launch {
            delay(800)
            listFlow.update { list ->
                list.map { if (it.id == myMsg.id) it.copy(status = MessageStatus.DELIVERED) else it }
            }
            rebuildChatSessions()

            delay(1000)
            listFlow.update { list ->
                list.map { if (it.id == myMsg.id) it.copy(status = MessageStatus.SEEN) else it }
            }
            rebuildChatSessions()

            // Trigger Mock Reply
            triggerMockReply(peerId, content, type)
        }

        return Result.success(myMsg)
    }

    private fun triggerMockReply(peerId: String, myMessage: String, type: MessageType) {
        scope.launch {
            delay(1500)
            // Typing...
            _allUsers.update { list ->
                list.map { if (it.id == peerId) it.copy(isTypingTo = "me") else it }
            }
            delay(2000)

            val replyText = when (type) {
                MessageType.IMAGE -> "Wow, what a gorgeous picture! 📸 WeChat 2.0 looks premium."
                MessageType.VOICE -> "Got your voice message! Loud and clear. 🎙️"
                MessageType.TEXT -> {
                    val q = myMessage.lowercase()
                    when {
                        q.contains("hello") || q.contains("hi") || q.contains("hey") -> "Hello! Zayed here. Hope you are enjoying WeChat 2.0's beautiful glassmorphism."
                        q.contains("call") || q.contains("phone") -> "Sure! Tap the call button at the top right to test the voice or video call screens! They look spectacular."
                        q.contains("pakistan") -> "Pakistan is beautiful! I love Lahore, Karachi, and Islamabad. 🇵🇰"
                        q.contains("how are you") -> "I am doing amazing, coding some awesome features in Jetpack Compose!"
                        else -> "That sounds interesting! WeChat 2.0 is designed for smooth communication."
                    }
                }
            }

            val reply = Message(
                senderId = peerId,
                receiverId = "me",
                content = replyText,
                type = MessageType.TEXT,
                status = MessageStatus.SENT,
                timestamp = System.currentTimeMillis()
            )

            // Remove typing and add message
            _allUsers.update { list ->
                list.map { if (it.id == peerId) it.copy(isTypingTo = null) else it }
            }

            val listFlow = messagesStore.getOrPut(peerId) { MutableStateFlow(emptyList()) }
            listFlow.update { it + reply }
            rebuildChatSessions()
        }
    }

    override suspend fun setTypingState(peerId: String, isTyping: Boolean) {
        // Safe placeholder for server sync
    }

    override suspend fun markAsSeen(peerId: String) {
        val listFlow = messagesStore[peerId] ?: return
        listFlow.update { list ->
            list.map { if (it.senderId == peerId) it.copy(status = MessageStatus.SEEN) else it }
        }
        rebuildChatSessions()
    }

    // --- CALL REPOSITORY ---
    override fun startCall(peer: User, isVideo: Boolean) {
        _currentCall.value = CallSession(
            peer = peer,
            isIncoming = false,
            isVideo = isVideo,
            status = CallStatus.DIALING
        )
        // Auto connect after 3 seconds for beautiful UI demo
        scope.launch {
            delay(1500)
            _currentCall.update { it?.copy(status = CallStatus.RINGING) }
            delay(3000)
            _currentCall.update { it?.copy(status = CallStatus.CONNECTED, startTime = System.currentTimeMillis()) }
        }
    }

    override fun acceptCall() {
        _currentCall.update { it?.copy(status = CallStatus.CONNECTED, startTime = System.currentTimeMillis()) }
    }

    override fun rejectCall() {
        _currentCall.update { it?.copy(status = CallStatus.REJECTED) }
        scope.launch {
            delay(1000)
            _currentCall.value = null
        }
    }

    override fun endCall() {
        _currentCall.update { it?.copy(status = CallStatus.DISCONNECTED) }
        scope.launch {
            delay(1000)
            _currentCall.value = null
        }
    }

    override fun simulateIncomingCall(peer: User, isVideo: Boolean) {
        _currentCall.value = CallSession(
            peer = peer,
            isIncoming = true,
            isVideo = isVideo,
            status = CallStatus.RINGING
        )
    }
}
