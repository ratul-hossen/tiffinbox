package com.ratulhossen.tiffinbox.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.AGENT_AWAITING_CONFIRMATION
import com.ratulhossen.tiffinbox.data.firebase.AGENT_AWAITING_PAYMENT
import com.ratulhossen.tiffinbox.data.firebase.AgentOrderUi
import com.ratulhossen.tiffinbox.data.firebase.AgentPaymentResult
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Orders tab: orders the customer's AI agent prepared (mcp-server). Each one
 * needs the customer's Confirm here, then an advance bKash/Nagad Send Money
 * to TiffinBox's number and its TrxID (here or through the agent). TiffinBox
 * then verifies the payment and the order moves to the normal history.
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
    val failed = stringResource(R.string.agent_orders_action_failed)
    val txnInvalid = stringResource(R.string.agent_orders_txn_invalid)
    val txnUsed = stringResource(R.string.agent_orders_txn_used)

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
            if (order.kitchenName.isNotBlank()) Text(order.kitchenName, fontWeight = FontWeight.SemiBold)
            Text(order.itemsSummary, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.price_tag, order.total), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            when (order.status) {
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
                    Text(stringResource(R.string.agent_orders_pay_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.agent_orders_pay_steps, order.total), style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(paymentNumber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { clipboard.setText(AnnotatedString(paymentNumber)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.agent_mcp_copy))
                        }
                    }
                    Text(
                        stringResource(R.string.agent_orders_pay_deadline, countdown(order.payBy - now)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TiffinButton(
                            onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    when (FirestoreOrderRepository.submitAgentPayment(order.id, method, txn)) {
                                        AgentPaymentResult.OK -> txn = ""
                                        AgentPaymentResult.INVALID -> error = txnInvalid
                                        AgentPaymentResult.ALREADY_USED_OR_FAILED -> error = txnUsed
                                    }
                                    busy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !busy && txn.isNotBlank()
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
