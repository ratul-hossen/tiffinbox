package com.ratulhossen.tiffinbox.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.ChatMessageUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChatRepository

@Composable
fun CustomerChatScreen(chefId: String, onBack: () -> Unit) {
    val customerId = FirebaseAuthRepository.currentUser?.uid ?: "guest"
    var chefName by remember(chefId) { mutableStateOf(SampleData.chefById(chefId)?.kitchenName ?: "Chef") }

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
    var chefName by remember(chefId) { mutableStateOf(SampleData.chefById(chefId)?.kitchenName ?: "Chef") }

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let { chefName = it.kitchenName }
    }

    val senderId = remember(chefId) { FirestoreChatRepository.chefSenderId(chefId) }
    val chatId = remember(customerId, chefId) { FirestoreChatRepository.chatId(customerId, chefId) }

    ChatScreenContent(
        chatId = chatId,
        currentSenderId = senderId,
        title = "Customer (${customerId.take(6)})",
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("মেসেজ লিখুন...") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
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
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "কথোপকথন শুরু করুন",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    val isMine = message.senderId == currentSenderId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMine) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            contentColor = if (isMine) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                message.text,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
