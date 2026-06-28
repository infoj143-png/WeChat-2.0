package com.infoj143.wechat20.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.infoj143.wechat20.models.Message
import com.infoj143.wechat20.models.MessageStatus
import com.infoj143.wechat20.models.MessageType
import com.infoj143.wechat20.models.User
import com.infoj143.wechat20.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToVoiceCall: (String, Boolean) -> Unit,
    onNavigateToVideoCall: (String, Boolean) -> Unit
) {
    val peer by viewModel.peer.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val soundWaves by viewModel.soundWaves.collectAsState()

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Mark as seen when entering chat
    LaunchedEffect(messages.size) {
        viewModel.markAsSeen()
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    peer?.let { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(p.profilePhotoUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = p.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    p.name, 
                                    color = MaterialTheme.colorScheme.onBackground, 
                                    fontSize = 16.sp, 
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.25).sp
                                )
                                if (p.isTypingTo == "me") {
                                    Text("typing...", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Text(
                                        if (p.isOnline) "Online" else "Offline",
                                        color = if (p.isOnline) Color(0xFF22C55E) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    peer?.let { p ->
                        IconButton(onClick = { onNavigateToVoiceCall(p.id, false) }) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        IconButton(onClick = { onNavigateToVideoCall(p.id, false) }) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Messages List Area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    
                    items(messages) { msg ->
                        ChatBubble(
                            message = msg,
                            isMe = msg.senderId == "me",
                            peerAvatar = peer?.profilePhotoUrl ?: ""
                        )
                    }
                }

                // Bottom Input/Recording Layout (Glassmorphic input row)
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (isRecording) {
                            // RECORDING MODE
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(25.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Flashing microphone icon
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val micScale by infiniteTransition.animateFloat(
                                        initialValue = 0.8f,
                                        targetValue = 1.2f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(500, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        )
                                    )
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color.Red,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .scale(micScale)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = String.format("%02d:%02d", recordingDuration / 600, (recordingDuration / 10) % 60),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Interactive Animated Sound Waves
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(100.dp)
                                ) {
                                    soundWaves.forEach { amp ->
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height((30 * amp).dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                    }
                                }

                                // Cancel and Send icons
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    IconButton(
                                        onClick = { viewModel.stopVoiceRecording(shouldSend = false) },
                                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.stopVoiceRecording(shouldSend = true) },
                                        modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else {
                            // REGULAR TEXT INPUT MODE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Picture Attachment FAB
                                IconButton(
                                    onClick = { viewModel.sendMockImage() },
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Send Image", tint = MaterialTheme.colorScheme.onBackground)
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Main Text Field
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { viewModel.updateInputText(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_input"),
                                    placeholder = { Text("Write your message...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    maxLines = 4,
                                    singleLine = false
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Audio/Send Context Switcher
                                if (inputText.trim().isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.sendMessage() },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .testTag("send_button")
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send Message", tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                } else {
                                    IconButton(
                                        onClick = { viewModel.startVoiceRecording() },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                                    ) {
                                        Icon(Icons.Default.Mic, contentDescription = "Record Voice", tint = MaterialTheme.colorScheme.onBackground)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    isMe: Boolean,
    peerAvatar: String
) {
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val isVoice = message.type == MessageType.VOICE
    val isImage = message.type == MessageType.IMAGE

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(peerAvatar)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Chat Bubble card
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isMe) {
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                                )
                            )
                        }
                    )
                    .combinedClickable(
                        onLongClick = { /* Message reactions */ },
                        onClick = { /* Handle media playback */ }
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    if (isImage && message.mediaUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.mediaUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Image message",
                            modifier = Modifier
                                .width(200.dp)
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (isVoice) {
                        var isPlaying by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isPlaying = !isPlaying }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Play voice note",
                                tint = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Simulated mini equalizer wave
                            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                repeat(12) { i ->
                                    val infiniteTransition = rememberInfiniteTransition()
                                    val waveHt by if (isPlaying) {
                                        infiniteTransition.animateFloat(
                                            initialValue = 4f,
                                            targetValue = (12..24).random().toFloat(),
                                            animationSpec = infiniteRepeatable(
                                                animation = tween((300..600).random(), easing = FastOutSlowInEasing),
                                                repeatMode = RepeatMode.Reverse
                                            )
                                        )
                                    } else {
                                        remember { mutableStateOf(6f) }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(waveHt.dp)
                                            .background(
                                                if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${message.durationSec} s",
                                color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    } else if (!isImage) {
                        // Standard text rendering
                        Text(
                            text = message.content,
                            color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Time and Delivery/Seen Ticks Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = sdf.format(Date(message.timestamp)),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                
                if (isMe) {
                    val tickColor = when (message.status) {
                        MessageStatus.SEEN -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    }
                    val tickIcon = when (message.status) {
                        MessageStatus.SENT -> Icons.Default.Check
                        else -> Icons.Default.DoneAll
                    }
                    Icon(
                        imageVector = tickIcon,
                        contentDescription = message.status.name,
                        tint = tickColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
