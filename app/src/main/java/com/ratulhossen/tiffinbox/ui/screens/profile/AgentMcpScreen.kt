package com.ratulhossen.tiffinbox.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.AgentConnectionUi
import com.ratulhossen.tiffinbox.data.firebase.AgentMcpStatus
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreAgentRepository
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Profile -> AI Agent (MCP): connect an AI agent (Claude, ChatGPT, ...) to
 * this account through TiffinBox's MCP server (mcp-server/ in the repo).
 * Shows the server address, a one-time code for the server's sign-in page,
 * and the connected agents with a Disconnect button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentMcpScreen(onBack: () -> Unit) {
    val uid = FirebaseAuthRepository.currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var status by remember { mutableStateOf<AgentMcpStatus?>(null) }
    var connections by remember { mutableStateOf<List<AgentConnectionUi>>(emptyList()) }
    var code by remember { mutableStateOf<String?>(null) }
    var codeCreatedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var creating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var copiedUrl by remember { mutableStateOf(false) }
    var toDisconnect by remember { mutableStateOf<AgentConnectionUi?>(null) }

    val codeFailed = stringResource(R.string.agent_mcp_code_failed)
    val connectedMessage = stringResource(R.string.agent_mcp_new_connection)

    LaunchedEffect(uid) { if (uid.isNotEmpty()) status = FirestoreAgentRepository.status(uid) }

    DisposableEffect(uid) {
        val registration = if (uid.isNotEmpty()) {
            FirestoreAgentRepository.listenConnections(uid) { list ->
                // A connection newer than the code on screen means the code was just used.
                val fresh = list.firstOrNull { codeCreatedAt > 0 && it.createdAt >= codeCreatedAt - 60_000 }
                if (code != null && fresh != null) {
                    code = null
                    codeCreatedAt = 0L
                    message = connectedMessage.format(fresh.clientName)
                }
                connections = list
            }
        } else null
        onDispose { registration?.remove() }
    }

    // Countdown for the code on screen.
    LaunchedEffect(code) {
        while (code != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val remainingMs = if (code != null) FirestoreAgentRepository.CODE_TTL_MS - (now - codeCreatedAt) else 0L
    val codeExpired = code != null && remainingMs <= 0
    val canConnect = status?.enabled == true && status?.blocked == false && status?.serverUrl?.isNotEmpty() == true

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.agent_mcp_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            McpCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.agent_mcp_intro_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(stringResource(R.string.agent_mcp_intro_body), style = MaterialTheme.typography.bodyMedium)
            }

            status?.let { s ->
                when {
                    s.blocked -> Notice(stringResource(R.string.agent_mcp_blocked))
                    !s.enabled -> Notice(stringResource(R.string.agent_mcp_disabled))
                }
            }

            McpCard {
                SectionTitle(stringResource(R.string.agent_mcp_server_title))
                val url = status?.serverUrl.orEmpty()
                if (url.isEmpty()) {
                    Text(
                        stringResource(R.string.agent_mcp_server_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    SelectionContainer {
                        Text(url, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedButton(onClick = {
                        clipboard.setText(AnnotatedString(url))
                        copiedUrl = true
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (copiedUrl) R.string.agent_mcp_copied else R.string.agent_mcp_copy))
                    }
                }
            }

            McpCard {
                SectionTitle(stringResource(R.string.agent_mcp_steps_title))
                listOf(
                    R.string.agent_mcp_step_1,
                    R.string.agent_mcp_step_2,
                    R.string.agent_mcp_step_3,
                    R.string.agent_mcp_step_4
                ).forEachIndexed { i, res ->
                    Text("${i + 1}. ${stringResource(res)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            McpCard {
                val shownCode = code
                if (shownCode != null && !codeExpired) {
                    SectionTitle(stringResource(R.string.agent_mcp_code_title))
                    Text(
                        FirestoreAgentRepository.formatCode(shownCode),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val seconds = (remainingMs / 1000).coerceAtLeast(0)
                    Text(
                        stringResource(R.string.agent_mcp_code_expires, "%d:%02d".format(seconds / 60, seconds % 60)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipboard.setText(AnnotatedString(shownCode)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.agent_mcp_copy))
                        }
                        TextButton(onClick = {
                            code = null
                            codeCreatedAt = 0L
                            scope.launch { FirestoreAgentRepository.cancelPairingCode(shownCode) }
                        }) { Text(stringResource(R.string.agent_mcp_code_cancel)) }
                    }
                } else {
                    if (codeExpired) {
                        Text(stringResource(R.string.agent_mcp_code_expired), color = MaterialTheme.colorScheme.error)
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    TiffinButton(
                        onClick = {
                            creating = true
                            message = null
                            scope.launch {
                                val newCode = FirestoreAgentRepository.createPairingCode(uid)
                                creating = false
                                if (newCode == null) {
                                    message = codeFailed
                                } else {
                                    codeCreatedAt = System.currentTimeMillis()
                                    now = codeCreatedAt
                                    code = newCode
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canConnect && !creating && uid.isNotEmpty()
                    ) { Text(stringResource(R.string.agent_mcp_connect)) }
                }
            }

            McpCard {
                SectionTitle(stringResource(R.string.agent_mcp_connected_title))
                if (connections.isEmpty()) {
                    Text(
                        stringResource(R.string.agent_mcp_connected_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                connections.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.clientName, fontWeight = FontWeight.SemiBold)
                            if (c.redirectHost.isNotBlank()) {
                                Text(
                                    stringResource(R.string.agent_mcp_sends_to, c.redirectHost),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (c.knownClient == false || c.browseOnly) {
                                Column(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (c.knownClient == false) {
                                        StatusPill(
                                            stringResource(R.string.agent_mcp_unverified),
                                            MaterialTheme.colorScheme.errorContainer,
                                            MaterialTheme.colorScheme.onErrorContainer,
                                            Icons.Filled.Warning
                                        )
                                    }
                                    if (c.browseOnly) {
                                        StatusPill(
                                            stringResource(R.string.agent_mcp_browse_only),
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            MaterialTheme.colorScheme.onSecondaryContainer,
                                            null
                                        )
                                    }
                                }
                            }
                            if (c.lastUsedAt > 0) {
                                Text(
                                    stringResource(R.string.agent_mcp_last_used, formatTime(c.lastUsedAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = { toDisconnect = c }) {
                            Text(stringResource(R.string.agent_mcp_disconnect), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            McpCard {
                SectionTitle(stringResource(R.string.agent_mcp_safety_title))
                Text(stringResource(R.string.agent_mcp_safety_body), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    toDisconnect?.let { c ->
        AlertDialog(
            onDismissRequest = { toDisconnect = null },
            title = { Text(stringResource(R.string.agent_mcp_disconnect_confirm_title, c.clientName)) },
            text = { Text(stringResource(R.string.agent_mcp_disconnect_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    toDisconnect = null
                    scope.launch { FirestoreAgentRepository.disconnect(uid, c.id) }
                }) { Text(stringResource(R.string.agent_mcp_disconnect), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { toDisconnect = null }) { Text(stringResource(R.string.agent_mcp_cancel)) }
            }
        )
    }
}

private fun formatTime(millis: Long): String =
    if (millis <= 0) "-" else SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(millis))

@Composable
private fun McpCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color, icon: ImageVector?) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall, color = content, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
