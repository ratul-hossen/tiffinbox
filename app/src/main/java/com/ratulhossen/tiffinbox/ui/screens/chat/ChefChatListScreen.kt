package com.ratulhossen.tiffinbox.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.ChatThreadUi
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChatRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefChatListScreen(
    chefId: String,
    onThreadClick: (customerId: String) -> Unit,
    onBack: () -> Unit
) {
    var threads by remember(chefId) { mutableStateOf<List<ChatThreadUi>>(emptyList()) }

    DisposableEffect(chefId) {
        val registration = FirestoreChatRepository.listenChefThreads(chefId) { threads = it }
        onDispose { registration?.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        if (threads.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.chef_chat_list_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(threads, key = { it.chatId }) { thread ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThreadClick(thread.customerId) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.chef_chat_list_customer_label, thread.customerId.take(6)),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                thread.lastMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerChatListScreen(
    customerId: String,
    onThreadClick: (chefId: String) -> Unit,
    onBack: () -> Unit
) {
    var threads by remember(customerId) { mutableStateOf<List<ChatThreadUi>>(emptyList()) }

    DisposableEffect(customerId) {
        val registration = FirestoreChatRepository.listenCustomerThreads(customerId) { threads = it }
        onDispose { registration?.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        if (threads.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.customer_chat_list_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(threads, key = { it.chatId }) { thread ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThreadClick(thread.chefId) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    thread.chefName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                thread.chefName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                thread.lastMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
