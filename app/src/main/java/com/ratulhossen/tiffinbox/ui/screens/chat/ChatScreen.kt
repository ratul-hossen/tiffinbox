package com.ratulhossen.tiffinbox.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.ChatMessageUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChatRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreSubscriptionRepository
import com.ratulhossen.tiffinbox.data.model.SubscriptionUi
import com.ratulhossen.tiffinbox.util.VoicePlayer
import com.ratulhossen.tiffinbox.util.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WhatsAppGreen = Color(0xFF25D366)
private val ChatWallpaperLight = Color(0xFFECE5DD)
private val ChatWallpaperDark = Color(0xFF0B141A)
private val OutgoingBubbleLight = Color(0xFFDCF8C6)
private val OutgoingBubbleDark = Color(0xFF005C4B)

private fun formatMessageTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
}

@Composable
fun CustomerChatScreen(chefId: String, onBack: () -> Unit) {
    val customerId = FirebaseAuthRepository.currentUser?.uid ?: "guest"
    val chefFallbackName = stringResource(R.string.chat_chef_fallback_name)
    var chefName by remember(chefId) { mutableStateOf(SampleData.chefById(chefId)?.kitchenName ?: chefFallbackName) }
    var chefBkashNumber by remember(chefId) { mutableStateOf("") }

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let {
            chefName = it.kitchenName
            chefBkashNumber = it.bkashNumber
        }
    }

    val chatId = remember(chefId, customerId) { FirestoreChatRepository.chatId(customerId, chefId) }

    ChatScreenContent(
        chatId = chatId,
        currentSenderId = customerId,
        customerId = customerId,
        chefId = chefId,
        chefName = chefName,
        chefBkashNumber = chefBkashNumber,
        isChef = false,
        title = chefName,
        onBack = onBack,
        onSend = { text ->
            FirestoreChatRepository.sendMessage(chatId, customerId, chefId, chefName, customerId, text)
        }
    )
}

@Composable
fun ChefChatScreen(customerId: String, chefId: String, onBack: () -> Unit) {
    val chefFallbackName = stringResource(R.string.chat_chef_fallback_name)
    var chefName by remember(chefId) { mutableStateOf(SampleData.chefById(chefId)?.kitchenName ?: chefFallbackName) }
    var chefBkashNumber by remember(chefId) { mutableStateOf("") }

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let {
            chefName = it.kitchenName
            chefBkashNumber = it.bkashNumber
        }
    }

    val senderId = remember(chefId) { FirestoreChatRepository.chefSenderId(chefId) }
    val chatId = remember(customerId, chefId) { FirestoreChatRepository.chatId(customerId, chefId) }

    ChatScreenContent(
        chatId = chatId,
        currentSenderId = senderId,
        customerId = customerId,
        chefId = chefId,
        chefName = chefName,
        chefBkashNumber = chefBkashNumber,
        isChef = true,
        title = stringResource(R.string.chat_customer_title, customerId.take(6)),
        onBack = onBack,
        onSend = { text ->
            FirestoreChatRepository.sendMessage(chatId, customerId, chefId, chefName, senderId, text)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    chatId: String,
    currentSenderId: String,
    customerId: String,
    chefId: String,
    chefName: String,
    chefBkashNumber: String,
    isChef: Boolean,
    title: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var messages by remember(chatId) { mutableStateOf<List<ChatMessageUi>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Audio recording & playback helpers
    val voiceRecorder = remember { VoiceRecorder(context) }
    val voicePlayer = remember { VoicePlayer(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordDurationSec by remember { mutableIntStateOf(0) }
    var currentlyPlayingMessageId by remember { mutableStateOf<String?>(null) }

    // Dialog state for Chef payment request
    var showPaymentRequestDialog by remember { mutableStateOf(false) }

    DisposableEffect(chatId) {
        val registration = FirestoreChatRepository.listenMessages(chatId) {
            messages = it
            // Open on screen = read; clears this side's unread badge.
            FirestoreChatRepository.markRead(chatId, asChef = isChef)
        }
        onDispose {
            registration?.remove()
            voicePlayer.stop()
            voiceRecorder.cancel()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Timer while recording
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordDurationSec = 0
            while (isRecording) {
                delay(1000L)
                recordDurationSec++
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val ok = voiceRecorder.start()
            if (ok) isRecording = true
            else Toast.makeText(context, "Could not start audio recorder", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, context.getString(R.string.chat_voice_permission_needed), Toast.LENGTH_SHORT).show()
        }
    }

    val isDark = isSystemInDarkTheme()
    val wallpaper = if (isDark) ChatWallpaperDark else ChatWallpaperLight
    val outgoingBubble = if (isDark) OutgoingBubbleDark else OutgoingBubbleLight
    val outgoingText = if (isDark) Color.White else Color(0xFF111B21)
    val incomingBubble = if (isDark) Color(0xFF202C33) else Color.White
    val incomingText = if (isDark) Color.White else Color(0xFF111B21)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = WhatsAppGreen.copy(alpha = 0.18f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    title.firstOrNull()?.uppercase() ?: "?",
                                    color = WhatsAppGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    if (isChef) {
                        Button(
                            onClick = { showPaymentRequestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Filled.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.chat_request_payment_btn), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Above the phone's nav buttons and, while typing, above the keyboard.
            Surface(shadowElevation = 8.dp, modifier = Modifier.navigationBarsPadding().imePadding()) {
                if (isRecording) {
                    // Recording bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.chat_voice_recording, recordDurationSec),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Cancel
                            IconButton(onClick = {
                                voiceRecorder.cancel()
                                isRecording = false
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.chat_voice_cancel), tint = MaterialTheme.colorScheme.error)
                            }
                            // Send
                            IconButton(
                                onClick = {
                                    val audio = voiceRecorder.stopAndGetAudio()
                                    isRecording = false
                                    if (audio != null) {
                                        FirestoreChatRepository.sendVoiceMessage(
                                            chatId = chatId,
                                            customerId = customerId,
                                            chefId = chefId,
                                            chefName = chefName,
                                            senderId = currentSenderId,
                                            audioBase64 = audio.first,
                                            durationSec = audio.second
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_voice_send), tint = WhatsAppGreen)
                            }
                        }
                    }
                } else {
                    // Normal typing bar with Voice Note button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text(stringResource(R.string.chat_message_placeholder)) },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        if (input.trim().isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = WhatsAppGreen,
                                modifier = Modifier.size(46.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val text = input.trim()
                                        if (text.isNotEmpty()) {
                                            onSend(text)
                                            input = ""
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = stringResource(R.string.chat_send_content_description),
                                        tint = Color.White
                                    )
                                }
                            }
                        } else {
                            // Mic button for voice note
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(46.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            val ok = voiceRecorder.start()
                                            if (ok) isRecording = true
                                            else Toast.makeText(context, "Could not start recorder", Toast.LENGTH_SHORT).show()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = stringResource(R.string.chat_voice_record_hint),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(wallpaper),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.chat_empty_state),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(wallpaper),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isMine = !message.isAdmin && message.senderId == currentSenderId
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (message.isAdmin || isMine) Alignment.End else Alignment.Start
                    ) {
                        if (message.isAdmin) {
                            Text(
                                stringResource(R.string.chat_admin_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(bottom = 2.dp, end = 2.dp)
                            )
                        }

                        when (message.type) {
                            "payment_request" -> {
                                PaymentRequestCard(
                                    message = message,
                                    chatId = chatId,
                                    isMine = isMine,
                                    isChef = isChef
                                )
                            }
                            "voice" -> {
                                VoiceMessageCard(
                                    message = message,
                                    isMine = isMine,
                                    isPlaying = currentlyPlayingMessageId == message.id,
                                    onTogglePlay = {
                                        if (currentlyPlayingMessageId == message.id) {
                                            voicePlayer.stop()
                                            currentlyPlayingMessageId = null
                                        } else {
                                            voicePlayer.play(
                                                messageId = message.id,
                                                base64Audio = message.audioBase64,
                                                onPlaying = { currentlyPlayingMessageId = message.id },
                                                onCompletion = { currentlyPlayingMessageId = null }
                                            )
                                        }
                                    },
                                    bubbleColor = when {
                                        message.isAdmin -> MaterialTheme.colorScheme.tertiaryContainer
                                        isMine -> outgoingBubble
                                        else -> incomingBubble
                                    },
                                    textColor = when {
                                        message.isAdmin -> MaterialTheme.colorScheme.onTertiaryContainer
                                        isMine -> outgoingText
                                        else -> incomingText
                                    }
                                )
                            }
                            else -> {
                                // Regular text message
                                Surface(
                                    color = when {
                                        message.isAdmin -> MaterialTheme.colorScheme.tertiaryContainer
                                        isMine -> outgoingBubble
                                        else -> incomingBubble
                                    },
                                    contentColor = when {
                                        message.isAdmin -> MaterialTheme.colorScheme.onTertiaryContainer
                                        isMine -> outgoingText
                                        else -> incomingText
                                    },
                                    shape = RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isMine || message.isAdmin) 14.dp else 2.dp,
                                        bottomEnd = if (isMine || message.isAdmin) 2.dp else 14.dp
                                    ),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            message.text,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            formatMessageTime(message.sentAt),
                                            fontSize = 11.sp,
                                            color = LocalContentColor.current.copy(alpha = 0.6f),
                                            modifier = Modifier.align(Alignment.End)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPaymentRequestDialog) {
        ChefPaymentRequestDialog(
            customerId = customerId,
            chefId = chefId,
            chefBkashNumber = chefBkashNumber,
            onDismiss = { showPaymentRequestDialog = false },
            onSubmit = { amount, method, number, paymentFor, subId ->
                showPaymentRequestDialog = false
                FirestoreChatRepository.sendPaymentRequest(
                    chatId = chatId,
                    customerId = customerId,
                    chefId = chefId,
                    chefName = chefName,
                    senderId = currentSenderId,
                    amount = amount,
                    paymentMethod = method,
                    paymentNumber = number,
                    paymentFor = paymentFor,
                    subscriptionId = subId
                )
            }
        )
    }
}

@Composable
private fun VoiceMessageCard(
    message: ChatMessageUi,
    isMine: Boolean,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    bubbleColor: Color,
    textColor: Color
) {
    Surface(
        color = bubbleColor,
        contentColor = textColor,
        shape = RoundedCornerShape(
            topStart = 14.dp,
            topEnd = 14.dp,
            bottomStart = if (isMine) 14.dp else 2.dp,
            bottomEnd = if (isMine) 2.dp else 14.dp
        ),
        shadowElevation = 1.dp,
        modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = WhatsAppGreen,
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(onClick = onTogglePlay) {
                        Icon(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Audio waveform visualization simulation
                    Row(
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val barHeights = listOf(6, 12, 16, 8, 14, 10, 15, 7, 13, 9, 14, 11)
                        barHeights.forEach { h ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(h.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (isPlaying) WhatsAppGreen else textColor.copy(alpha = 0.5f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "0:${message.audioDurationSec.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                formatMessageTime(message.sentAt),
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PaymentRequestCard(
    message: ChatMessageUi,
    chatId: String,
    isMine: Boolean,
    isChef: Boolean
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isPaid = message.paymentStatus == "paid"
    var trxInput by remember { mutableStateOf("") }
    var isSubmittingTrx by remember { mutableStateOf(false) }
    var isConfirmingReceived by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 320.dp)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPaid) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header badge & title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Payment,
                        contentDescription = null,
                        tint = if (isPaid) WhatsAppGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Payment Request",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isPaid) WhatsAppGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        stringResource(if (isPaid) R.string.chat_payment_paid_badge else R.string.chat_payment_pending_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPaid) WhatsAppGreen else MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Amount
            Text(
                "৳${message.paymentAmount}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (message.paymentFor.isNotBlank()) {
                Text(
                    message.paymentFor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (isPaid) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = WhatsAppGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.chat_payment_received_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = WhatsAppGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (message.paymentTransactionId.isNotBlank()) {
                    Text(
                        "TrxID: ${message.paymentTransactionId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else {
                // Number with copy
                if (message.paymentNumber.isNotBlank()) {
                    Text(
                        stringResource(R.string.chat_payment_send_money_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${message.paymentMethod}: ${message.paymentNumber}",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.paymentNumber))
                                Toast.makeText(context, context.getString(R.string.subscribe_bkash_number_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Customer actions: Submit TrxID
                if (!isChef) {
                    if (message.paymentTransactionId.isBlank()) {
                        OutlinedTextField(
                            value = trxInput,
                            onValueChange = { trxInput = it },
                            placeholder = { Text(stringResource(R.string.chat_payment_trx_placeholder), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (trxInput.trim().isNotBlank()) {
                                    isSubmittingTrx = true
                                    FirestoreChatRepository.submitPaymentTransactionId(
                                        chatId = chatId,
                                        messageId = message.id,
                                        transactionId = trxInput.trim()
                                    )
                                    isSubmittingTrx = false
                                }
                            },
                            enabled = trxInput.trim().isNotBlank() && !isSubmittingTrx,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.chat_payment_submit_trx))
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.chat_payment_trx_submitted, message.paymentTransactionId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                // Chef actions: Confirm Received
                if (isChef) {
                    if (message.paymentTransactionId.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    "গ্রাহক TrxID দিয়েছেন: ${message.paymentTransactionId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isConfirmingReceived = true
                                scope.launch {
                                    FirestoreChatRepository.confirmPaymentReceived(
                                        chatId = chatId,
                                        messageId = message.id,
                                        subscriptionId = message.subscriptionId,
                                        transactionId = message.paymentTransactionId,
                                        paymentMethod = message.paymentMethod
                                    )
                                    isConfirmingReceived = false
                                    Toast.makeText(context, context.getString(R.string.chat_payment_received_done), Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isConfirmingReceived,
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.chat_payment_confirm_received), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "গ্রাহকের পেমেন্ট ও TrxID-এর অপেক্ষায়...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                formatMessageTime(message.sentAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ChefPaymentRequestDialog(
    customerId: String,
    chefId: String,
    chefBkashNumber: String,
    onDismiss: () -> Unit,
    onSubmit: (amount: Int, method: String, number: String, paymentFor: String, subscriptionId: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("bKash") }
    var numberText by remember { mutableStateOf(chefBkashNumber) }
    var paymentForText by remember { mutableStateOf("Custom Weekly Meal Plan") }
    var linkedSubscriptionId by remember { mutableStateOf("") }

    var pendingSubscriptions by remember { mutableStateOf<List<SubscriptionUi>>(emptyList()) }

    LaunchedEffect(customerId, chefId) {
        pendingSubscriptions = FirestoreSubscriptionRepository.getPendingCustomSubscriptions(customerId, chefId)
        if (pendingSubscriptions.isNotEmpty()) {
            val first = pendingSubscriptions.first()
            amountText = first.totalPrice.toString()
            paymentForText = "${first.plan.name} Plan (${first.scheduleSummary})"
            linkedSubscriptionId = first.id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_payment_request_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (pendingSubscriptions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.chat_pending_plan_recommendation),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pendingSubscriptions.forEach { sub ->
                            FilterChip(
                                selected = linkedSubscriptionId == sub.id,
                                onClick = {
                                    linkedSubscriptionId = sub.id
                                    amountText = sub.totalPrice.toString()
                                    paymentForText = "${sub.plan.name} Plan (${sub.scheduleSummary})"
                                },
                                label = { Text("৳${sub.totalPrice} • ${sub.plan.name}", fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() }) amountText = it },
                    label = { Text(stringResource(R.string.chat_payment_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = method == "bKash",
                        onClick = { method = "bKash" },
                        label = { Text("bKash") }
                    )
                    FilterChip(
                        selected = method == "Nagad",
                        onClick = { method = "Nagad" },
                        label = { Text("Nagad") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = numberText,
                    onValueChange = { numberText = it },
                    label = { Text(stringResource(R.string.chat_payment_number_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = paymentForText,
                    onValueChange = { paymentForText = it },
                    label = { Text(stringResource(R.string.chat_payment_for_label)) },
                    placeholder = { Text(stringResource(R.string.chat_payment_for_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toIntOrNull() ?: 0
                    if (amt > 0) {
                        onSubmit(amt, method, numberText.trim(), paymentForText.trim(), linkedSubscriptionId)
                    }
                },
                enabled = (amountText.toIntOrNull() ?: 0) > 0
            ) {
                Text(stringResource(R.string.chat_payment_send_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
