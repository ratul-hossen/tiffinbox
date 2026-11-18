package com.ratulhossen.tiffinbox.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/** One AI agent (MCP client) the customer has connected. Written by the MCP server. */
data class AgentConnectionUi(
    val id: String,
    val clientName: String,
    val createdAt: Long,
    val lastUsedAt: Long
)

/** What the "AI Agent (MCP)" screen needs from app_config and mcp_blocks. */
data class AgentMcpStatus(
    val enabled: Boolean,
    val serverUrl: String,
    val blocked: Boolean
)

/**
 * The app side of "connect your AI agent" (mcp-server/ in this repo).
 *
 * The app never talks to the MCP server. It only writes a one-time code to
 * `mcp_pairing/{code}`; the user types that code on the server's page, and
 * the server (with its service account) checks it, deletes it and records the
 * connection under `users/{uid}/agent_connections`. Disconnecting is just
 * deleting that document - the server checks it on every call.
 */
object FirestoreAgentRepository {

    /** Same alphabet as mcp-server/src/auth.ts and firestore.rules (no 0/O, 1/I/L). */
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 8
    /** Must match LIMITS.pairingCodeTtlMs on the server. */
    const val CODE_TTL_MS = 5L * 60 * 1000

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val random = SecureRandom()

    private fun newCode(): String =
        (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    /** "K7P2M9QX" -> "K7P2-M9QX", easier to read and type. */
    fun formatCode(code: String): String =
        if (code.length == CODE_LENGTH) "${code.take(4)}-${code.drop(4)}" else code

    /**
     * Creates a code for [uid], or null on failure. A code that happens to
     * exist already is refused by the rules (it would be an update), so a few
     * retries cover the astronomically unlikely collision.
     */
    suspend fun createPairingCode(uid: String): String? {
        repeat(3) {
            val code = newCode()
            try {
                db.collection("mcp_pairing").document(code)
                    .set(mapOf("uid" to uid, "createdAt" to FieldValue.serverTimestamp()))
                    .await()
                return code
            } catch (e: Exception) {
                // try another code
            }
        }
        return null
    }

    /** Best effort: an unused code expires on its own after [CODE_TTL_MS]. */
    suspend fun cancelPairingCode(code: String) {
        try {
            db.collection("mcp_pairing").document(code).delete().await()
        } catch (e: Exception) {
            // Already used or gone.
        }
    }

    suspend fun status(uid: String): AgentMcpStatus {
        val settings = try {
            db.collection("app_config").document("settings").get().await()
        } catch (e: Exception) {
            null
        }
        val blocked = try {
            db.collection("mcp_blocks").document(uid).get().await().getBoolean("blocked") == true
        } catch (e: Exception) {
            false
        }
        return AgentMcpStatus(
            enabled = settings?.getBoolean("mcpEnabled") != false,
            serverUrl = settings?.getString("mcpServerUrl").orEmpty().trim(),
            blocked = blocked
        )
    }

    /** Live list of the user's connected agents, newest first. */
    fun listenConnections(uid: String, onChange: (List<AgentConnectionUi>) -> Unit): ListenerRegistration =
        db.collection("users").document(uid).collection("agent_connections")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                onChange(
                    snapshot?.documents.orEmpty().map { doc ->
                        AgentConnectionUi(
                            id = doc.id,
                            clientName = doc.getString("clientName").orEmpty().ifBlank { "AI agent" },
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            lastUsedAt = doc.getLong("lastUsedAt") ?: 0L
                        )
                    }
                )
            }

    suspend fun disconnect(uid: String, connectionId: String): Boolean = try {
        db.collection("users").document(uid).collection("agent_connections").document(connectionId).delete().await()
        true
    } catch (e: Exception) {
        false
    }
}
