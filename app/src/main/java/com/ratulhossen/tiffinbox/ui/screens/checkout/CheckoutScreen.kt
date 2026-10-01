package com.ratulhossen.tiffinbox.ui.screens.checkout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository

private enum class PaymentMethod(val label: String, val available: Boolean) {
    CASH_ON_DELIVERY("Cash on Delivery", available = true),
    BKASH("bKash", available = false),
    NAGAD("Nagad", available = false),
    CARD("Card", available = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onOrderPlaced: () -> Unit
) {
    var address by remember { mutableStateOf("Ashulia, Dhaka") }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.CASH_ON_DELIVERY) }
    val items = CartStore.items

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Checkout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            FirestoreOrderRepository.placeOrder(
                                items = items.toList(),
                                subtotal = CartStore.subtotal,
                                deliveryFee = CartStore.DELIVERY_FEE,
                                platformFee = CartStore.PLATFORM_FEE,
                                total = CartStore.total,
                                deliveryAddress = address,
                                paymentMethod = selectedPayment.name
                            )
                            CartStore.clear()
                            onOrderPlaced()
                        },
                        enabled = address.isNotBlank() && items.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Place Order • ৳${CartStore.total}")
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Delivery Address", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = { Text("আপনার ঠিকানা লিখুন") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("Payment Method", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            PaymentMethod.entries.forEach { method ->
                Surface(
                    onClick = { if (method.available) selectedPayment = method },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selectedPayment == method) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPayment == method,
                            onClick = { if (method.available) selectedPayment = method },
                            enabled = method.available
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            method.label,
                            modifier = Modifier.weight(1f),
                            color = if (method.available) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (!method.available) {
                            AssistChip(onClick = {}, enabled = false, label = { Text("আসছে") })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Order Summary", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow("Subtotal", CartStore.subtotal)
            SummaryRow("Delivery Fee", CartStore.DELIVERY_FEE)
            SummaryRow("Platform Fee", CartStore.PLATFORM_FEE)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            SummaryRow("Total", CartStore.total, bold = true)
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Int, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text("৳$amount", fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
