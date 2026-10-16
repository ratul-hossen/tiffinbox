package com.ratulhossen.tiffinbox.ui.screens.orders

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
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.model.OrderStage
import com.ratulhossen.tiffinbox.data.model.PastOrderUi
import com.ratulhossen.tiffinbox.data.model.label
import com.ratulhossen.tiffinbox.ui.components.StatTile

private val MINI_TRACKED_STAGES = listOf(
    OrderStage.PLACED,
    OrderStage.ACCEPTED,
    OrderStage.COOKING,
    OrderStage.OUT_FOR_DELIVERY,
    OrderStage.DELIVERED
)

@Composable
fun OrdersScreen() {
    var orders by remember { mutableStateOf<List<PastOrderUi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val firestoreOrders = FirestoreOrderRepository.getMyOrders()
        orders = firestoreOrders
        isLoading = false
    }

    if (!isLoading && orders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.orders_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    stringResource(R.string.price_tag, orders.sumOf { it.total }),
                    Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(orders, key = { it.id }) { order ->
            OrderRow(order)
        }
    }
}

@Composable
private fun OrderRow(order: PastOrderUi) {
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
            } else {
                MiniProgressTimeline(order.status)
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
