package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChatRepository

/** Standard red unread counter ("3", "99+"); draws nothing at 0. */
@Composable
fun UnreadCountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Surface(
        shape = CircleShape,
        color = Color(0xFFE53935),
        contentColor = Color.White,
        modifier = modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/** Live total of unread chat messages for the signed-in customer (all threads). */
@Composable
fun rememberCustomerUnreadChatCount(): Int {
    var total by remember { mutableIntStateOf(0) }
    val customerId = FirebaseAuthRepository.currentUser?.uid
    DisposableEffect(customerId) {
        val registration = customerId?.let { uid ->
            FirestoreChatRepository.listenCustomerThreads(uid) { threads ->
                total = threads.sumOf { it.unreadCount }
            }
        }
        onDispose { registration?.remove() }
    }
    return total
}

/** Live total of unread chat messages for a chef (all customers' threads). */
@Composable
fun rememberChefUnreadChatCount(chefId: String): Int {
    var total by remember { mutableIntStateOf(0) }
    DisposableEffect(chefId) {
        val registration = if (chefId.isBlank()) null else
            FirestoreChatRepository.listenChefThreads(chefId) { threads ->
                total = threads.sumOf { it.unreadCount }
            }
        onDispose { registration?.remove() }
    }
    return total
}
