package com.ratulhossen.tiffinbox.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.AGENT_AWAITING_CONFIRMATION
import com.ratulhossen.tiffinbox.data.firebase.AGENT_AWAITING_PAYMENT
import com.ratulhossen.tiffinbox.data.firebase.AGENT_PAYMENT_GRACE_MS
import com.ratulhossen.tiffinbox.data.firebase.AgentOrderUi
import com.ratulhossen.tiffinbox.data.firebase.AgentPaymentResult
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Orders tab: orders the customer's AI agent prepared (mcp-server). Each one
 * needs the customer's Confirm here, then an advance bKash/Nagad Send Money
 * to TiffinBox's number and its TrxID + the last 4 digits of the paying
 * number (here or through the agent). TiffinBox then verifies the payment by
 * hand and the order moves to the normal history (docs/AGENT_PAYMENTS.md).
 */
@Composable
fun AgentOrdersSection(orders: List<AgentOrderUi>, paymentNumber: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(orders) {
        while (orders.isNotEmpty()) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.agent_orders_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        orders.forEach { AgentOrderCard(it, paymentNumber, now) }
    }
}

private fun countdown(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun AgentOrderCard(order: AgentOrderUi, paymentNumber: String, now: Long) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var method by remember { mutableStateOf("BKASH") }
    var txn by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    val failed = stringResource(R.string.agent_orders_action_failed)
    val txnInvalid = stringResource(R.string.agent_orders_txn_invalid)
    val txnUsed = stringResource(R.string.agent_orders_txn_used)
    val last4Invalid = stringResource(R.string.agent_orders_last4_invalid)

    // The listener only knows kitchens already in the catalog cache; look the rest up once.
    var kitchenName by remember(order.id) { mutableStateOf(order.kitchenName) }
    LaunchedEffect(order.chefId, order.kitchenName) {
        if (order.kitchenName.isNotBlank()) {
            kitchenName = order.kitchenName
        } else if (order.chefId.isNotBlank()) {
            FirestoreCatalogRepository.getChef(order.chefId)?.kitchenName?.let { kitchenName = it }
        }
    }
    val expired = order.isExpired(now)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.agent_orders_by, order.agentName, order.shortId),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            if (kitchenName.isNotBlank()) Text(kitchenName, fontWeight = FontWeight.SemiBold)
            Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium)
            if (order.deliveryAddress.isNotBlank()) {
                Text(
                    stringResource(R.string.agent_orders_deliver_to, order.deliveryAddress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PriceBreakdown(order)
            if (order.estimatedDeliveryMinutes > 0) {
                Text(
                    stringResource(R.string.agent_orders_eta, order.estimatedDeliveryMinutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (expired) {
                // Deadline passed: the next read (here, the server cron or admin-web) cancels it.
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(
                        stringResource(R.string.agent_orders_expired),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else when (order.status) {
                AGENT_AWAITING_CONFIRMATION -> {
                    Text(
                        stringResource(R.string.agent_orders_confirm_hint, countdown(order.confirmBy - now)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TiffinButton(
                            onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    if (!FirestoreOrderRepository.confirmAgentOrder(order.id)) error = failed
                                    busy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !busy
                        ) { Text(stringResource(R.string.agent_orders_confirm)) }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                if (!FirestoreOrderRepository.cancelAgentOrder(order.id)) error = failed
                                busy = false
                            }
                        }) { Text(stringResource(R.string.agent_orders_cancel), color = MaterialTheme.colorScheme.error) }
                    }
                }

                AGENT_AWAITING_PAYMENT -> {
                    if (order.paymentNote.isNotBlank()) {
                        // The admin's "Ask again" note: the first thing the customer must read.
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.agent_orders_payment_note_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    order.paymentNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    Text(stringResource(R.string.agent_orders_pay_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.agent_orders_pay_steps, order.total), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(paymentNumber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { clipboard.setText(AnnotatedString(paymentNumber)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.agent_mcp_copy))
                        }
                    }
                    if (now <= order.payBy) {
                        Text(
                            stringResource(R.string.agent_orders_pay_deadline, countdown(order.payBy - now)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            stringResource(R.string.agent_orders_pay_grace, countdown(order.payBy + AGENT_PAYMENT_GRACE_MS - now)),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = method == "BKASH", onClick = { method = "BKASH" }, label = { Text("bKash") })
                        FilterChip(selected = method == "NAGAD", onClick = { method = "NAGAD" }, label = { Text("Nagad") })
                    }
                    OutlinedTextField(
                        value = txn,
                        onValueChange = { txn = it.take(20) },
                        label = { Text(stringResource(R.string.agent_orders_txn_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = last4,
                        onValueChange = { value -> last4 = value.filter { it.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.agent_orders_last4_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TiffinButton(
                            onClick = {
                                if (last4.length != 4) {
                                    error = last4Invalid
                                } else {
                                    busy = true
                                    error = null
                                    scope.launch {
                                        when (FirestoreOrderRepository.submitAgentPayment(order.id, method, txn, last4)) {
                                            AgentPaymentResult.OK -> {
                                                txn = ""
                                                last4 = ""
                                            }
                                            AgentPaymentResult.INVALID -> error = txnInvalid
                                            AgentPaymentResult.ALREADY_USED_OR_FAILED -> error = txnUsed
                                        }
                                        busy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && txn.isNotBlank() && last4.length == 4
                        ) { Text(stringResource(R.string.agent_orders_pay_submit)) }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                if (!FirestoreOrderRepository.cancelAgentOrder(order.id)) error = failed
                                busy = false
                            }
                        }) { Text(stringResource(R.string.agent_orders_cancel), color = MaterialTheme.colorScheme.error) }
                    }
                }

                else -> {
                    // PaymentSubmitted: waiting for TiffinBox to verify.
                    Text(
                        stringResource(
                            R.string.agent_orders_verifying,
                            if (order.paymentMethod == "NAGAD") "Nagad" else "bKash",
                            order.paymentTransactionId
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Subtotal, fees, discounts and total, like the checkout summary. Orders without a subtotal show just the total. */
@Composable
private fun PriceBreakdown(order: AgentOrderUi) {
    if (order.subtotal <= 0) {
        Text(stringResource(R.string.price_tag, order.total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        return
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BreakdownRow(stringResource(R.string.cart_summary_subtotal), stringResource(R.string.price_tag, order.subtotal))
            BreakdownRow(stringResource(R.string.cart_summary_delivery_fee), stringResource(R.string.price_tag, order.deliveryFee))
            if (order.platformFee > 0) {
                BreakdownRow(stringResource(R.string.cart_summary_platform_fee), stringResource(R.string.price_tag, order.platformFee))
            }
            if (order.discount > 0) {
                BreakdownRow(
                    stringResource(R.string.agent_orders_coupon_row, order.couponCode).trim(),
                    "-" + stringResource(R.string.price_tag, order.discount)
                )
            }
            if (order.rankDiscount > 0) {
                BreakdownRow(
                    stringResource(R.string.agent_orders_rank_discount_row),
                    "-" + stringResource(R.string.price_tag, order.rankDiscount)
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            BreakdownRow(stringResource(R.string.cart_summary_total), stringResource(R.string.price_tag, order.total), bold = true)
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
