package com.ratulhossen.tiffinbox.ui.screens.order

import com.ratulhossen.tiffinbox.ui.components.TiffinLottie
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.model.OrderStage
import com.ratulhossen.tiffinbox.data.model.label
import com.ratulhossen.tiffinbox.data.model.toOrderStage
import com.ratulhossen.tiffinbox.ui.components.TiffinButton

private val TRACKED_STAGES = listOf(
    OrderStage.PLACED,
    OrderStage.ACCEPTED,
    OrderStage.COOKING,
    OrderStage.OUT_FOR_DELIVERY,
    OrderStage.DELIVERED
)

/**
 * Live-tracks a real order's status as the chef actually updates it from
 * ChefDashboardScreen (accept/reject) and, eventually, delivery — no local
 * timer, no guessing. [onDone] fires once with the order's chefId when the
 * chef marks it Delivered, so the caller can route to the review screen for
 * the right chef.
 */
@Composable
fun OrderTrackingScreen(orderId: String, onDone: (chefId: String) -> Unit) {
    var stage by remember(orderId) { mutableStateOf<OrderStage?>(null) }
    var chefId by remember(orderId) { mutableStateOf<String?>(null) }
    var kitchenName by remember(orderId) { mutableStateOf<String?>(null) }

    DisposableEffect(orderId) {
        val registration = FirestoreOrderRepository.listenOrder(orderId) { status, orderChefId ->
            stage = status?.toOrderStage()
            chefId = orderChefId
        }
        onDispose { registration?.remove() }
    }

    LaunchedEffect(chefId) {
        chefId?.let { id -> kitchenName = FirestoreCatalogRepository.getChef(id)?.kitchenName }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(stringResource(R.string.order_tracking_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            kitchenName?.let { stringResource(R.string.order_tracking_subtitle_named, it) }
                ?: stringResource(R.string.order_tracking_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        when (val currentStage = stage) {
            null -> Text(
                stringResource(R.string.order_tracking_loading),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OrderStage.CANCELLED -> CancelledState()
            else -> {
                // What's happening right now, as motion: waiting -> boiling pot ->
                // pin on the way -> a one-time tick when it's delivered.
                val stageAnim = when (currentStage) {
                    OrderStage.COOKING -> R.raw.lottie_cooking_pot
                    OrderStage.OUT_FOR_DELIVERY -> R.raw.lottie_delivery_pin
                    OrderStage.DELIVERED -> R.raw.lottie_success_check
                    else -> R.raw.lottie_hourglass
                }
                Crossfade(targetState = stageAnim, label = "stageAnim") { res ->
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TiffinLottie(
                            res = res,
                            loop = res != R.raw.lottie_success_check,
                            modifier = Modifier.size(150.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                val currentIndex = TRACKED_STAGES.indexOf(currentStage).coerceAtLeast(0)
                TRACKED_STAGES.forEachIndexed { index, trackedStage ->
                    val done = index <= currentIndex
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (done) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (done) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (index != TRACKED_STAGES.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(36.dp)
                                        .background(
                                            if (index < currentIndex) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.padding(bottom = if (index != TRACKED_STAGES.lastIndex) 24.dp else 0.dp)) {
                            Text(
                                trackedStage.label(),
                                fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp,
                                color = if (done) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (index == currentIndex && index != TRACKED_STAGES.lastIndex) {
                                Text(
                                    stringResource(R.string.order_tracking_in_progress),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        if (stage == OrderStage.DELIVERED) {
            TiffinButton(
                onClick = { chefId?.let(onDone) },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(stringResource(R.string.order_tracking_review_button))
            }
        }
    }
}

@Composable
private fun CancelledState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            stringResource(R.string.order_tracking_cancelled_message),
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium
        )
    }
}
