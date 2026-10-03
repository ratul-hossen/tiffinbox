package com.ratulhossen.tiffinbox.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.ChatMessageUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChatRepository
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

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let { chefName = it.kitchenName }
    }

    val chatId = remember(chefId, customerId) { FirestoreChatRepository.chatId(customerId, chefId) }

    ChatScreenContent(
        chatId = chatId,
        currentSenderId = customerId,
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

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let { chefName = it.kitchenName }
    }

    val senderId = remember(chefId) { FirestoreChatRepository.chefSenderId(chefId) }
    val chatId = remember(customerId, chefId) { FirestoreChatRepository.chatId(customerId, chefId) }

    ChatScreenContent(
        chatId = chatId,
        currentSenderId = senderId,
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
    title: String,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    var messages by remember(chatId) { mutableStateOf<List<ChatMessageUi>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    DisposableEffect(chatId) {
        val registration = FirestoreChatRepository.listenMessages(chatId) { messages = it }
        onDispose { registration?.remove() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
