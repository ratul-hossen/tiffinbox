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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository

private enum class PaymentMethod(val available: Boolean) {
    CASH_ON_DELIVERY(available = true),
    BKASH(available = false),
    NAGAD(available = false),
    CARD(available = false)
}

@Composable
private fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CASH_ON_DELIVERY -> stringResource(R.string.payment_method_cod)
    PaymentMethod.BKASH -> stringResource(R.string.payment_method_bkash)
    PaymentMethod.NAGAD -> stringResource(R.string.payment_method_nagad)
    PaymentMethod.CARD -> stringResource(R.string.payment_method_card)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onOrderPlaced: () -> Unit
) {
    val defaultAddress = stringResource(R.string.home_location)
    var address by remember { mutableStateOf(defaultAddress) }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.CASH_ON_DELIVERY) }
    val items = CartStore.items

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.checkout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
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
                        Text(stringResource(R.string.checkout_place_order_button, CartStore.total))
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
            Text(stringResource(R.string.checkout_delivery_address_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = { Text(stringResource(R.string.checkout_address_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.checkout_payment_method_title), style = MaterialTheme.typography.titleMedium)
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
                            method.label(),
                            modifier = Modifier.weight(1f),
                            color = if (method.available) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (!method.available) {
                            AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.checkout_coming_soon_chip)) })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.checkout_order_summary_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(stringResource(R.string.cart_summary_subtotal), CartStore.subtotal)
            SummaryRow(stringResource(R.string.cart_summary_delivery_fee), CartStore.DELIVERY_FEE)
            SummaryRow(stringResource(R.string.cart_summary_platform_fee), CartStore.PLATFORM_FEE)
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            SummaryRow(stringResource(R.string.cart_summary_total), CartStore.total, bold = true)
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
        Text(stringResource(R.string.price_tag, amount), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}
