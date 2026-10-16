package com.ratulhossen.tiffinbox.ui.screens.orders

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.Icons
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.model.OrderStage
import com.ratulhossen.tiffinbox.data.model.PastOrderUi
import com.ratulhossen.tiffinbox.data.model.label
import com.ratulhossen.tiffinbox.ui.components.StatTile
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState

private val MINI_TRACKED_STAGES = listOf(
    OrderStage.PLACED,
    OrderStage.ACCEPTED,
    OrderStage.COOKING,
    OrderStage.OUT_FOR_DELIVERY,
    OrderStage.DELIVERED
)

@Composable
fun OrdersScreen(onOrderAgain: () -> Unit = {}) {
    var orders by remember { mutableStateOf<List<PastOrderUi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var confirmCancel by remember { mutableStateOf<PastOrderUi?>(null) }
    var cancelFailed by remember { mutableStateOf(false) }
    var reorderUnavailable by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        val firestoreOrders = FirestoreOrderRepository.getMyOrders()
        orders = firestoreOrders
        isLoading = false
    }
    // Ticks once a second while any order is still inside its 5-minute
    // cancel window, so the countdown (and the button disappearing) is live.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(orders) {
        while (orders.any { it.cancelMillisLeft() > 0 }) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    confirmCancel?.let { order ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text(stringResource(R.string.orders_cancel_confirm_title)) },
            text = { Text(stringResource(R.string.orders_cancel_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = null
                    scope.launch {
                        cancelFailed = !FirestoreOrderRepository.cancelOrderByCustomer(order.id)
                        reloadKey++
                    }
                }) {
                    Text(stringResource(R.string.orders_cancel_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = null }) {
                    Text(stringResource(R.string.orders_cancel_keep_button))
                }
            }
        )
    }

    if (!isLoading && orders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PhotoEmptyState(resId = R.drawable.empty_orders, title = stringResource(R.string.orders_empty_message))
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.orders_stat_total_orders),
                    "${orders.size}",
                    Modifier.weight(1f)
                )
                StatTile(
                    stringResource(R.string.orders_stat_total_spent),
                    stringResource(R.string.price_tag, orders.filter { it.status != OrderStage.CANCELLED }.sumOf { it.total }),
                    Modifier.weight(1f)
                )
            }
            if (reorderUnavailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.orders_reorder_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (cancelFailed) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.orders_cancel_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(orders, key = { it.id }) { order ->
            OrderRow(
                order,
                now = now,
                onCancel = { confirmCancel = order },
                onOrderAgain = {
                    // Refill the cart with the same dishes (whatever is still on
                    // the menu) and go straight to it - one tap.
                    scope.launch {
                        val menu = FirestoreCatalogRepository.getMealsForChef(order.chefId).associateBy { it.id }
                        val again = order.mealQuantities.mapNotNull { (id, qty) -> menu[id]?.let { it to qty } }
                        if (again.isEmpty()) {
                            reorderUnavailable = true
                        } else {
                            CartStore.clear()
                            again.forEach { (meal, qty) ->
                                CartStore.add(meal, if (meal.availableQuantity > 0) qty.coerceAtMost(meal.availableQuantity) else qty)
                            }
                            onOrderAgain()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun OrderRow(order: PastOrderUi, now: Long, onCancel: () -> Unit, onOrderAgain: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(order.chefEmoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(order.chefName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        order.itemsSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        order.placedOn,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    stringResource(R.string.price_tag, order.total),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (order.status == OrderStage.DELIVERED || order.status == OrderStage.CANCELLED) {
                StatusChip(order.status)
                if (order.status == OrderStage.CANCELLED && order.cancelReason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(
                            when (order.cancelReason) {
                                "auto_expired" -> R.string.orders_cancel_reason_expired
                                "customer" -> R.string.orders_cancel_reason_customer
                                else -> R.string.orders_cancel_reason_other
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (order.pointsEarned > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.orders_points_earned, order.pointsEarned),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (order.mealQuantities.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onOrderAgain, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.orders_order_again))
                    }
                }
            } else {
                MiniProgressTimeline(order.status)
                if (order.estimatedDeliveryAt > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        if (order.scheduledFor > 0) stringResource(
                            R.string.checkout_preorder_for,
                            java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault())
                                .format(java.util.Date(order.scheduledFor))
                        ) else stringResource(
                            R.string.orders_eta,
                            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                                .format(java.util.Date(order.estimatedDeliveryAt))
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            val cancelLeft = order.cancelMillisLeft(now)
            if (cancelLeft > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.orders_cancel_window_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onCancel) {
                        val secs = (cancelLeft / 1000).toInt()
                        Text(
                            stringResource(R.string.orders_cancel_with_timer, secs / 60, secs % 60),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (order.status == OrderStage.PLACED) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.orders_waiting_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniProgressTimeline(status: OrderStage) {
    val currentIndex = MINI_TRACKED_STAGES.indexOf(status).coerceAtLeast(0)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MINI_TRACKED_STAGES.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (index <= currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                )
                if (index != MINI_TRACKED_STAGES.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(if (index < currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            status.label(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusChip(status: OrderStage) {
    val isDelivered = status == OrderStage.DELIVERED
    val containerColor = if (isDelivered) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isDelivered) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            status.label(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
