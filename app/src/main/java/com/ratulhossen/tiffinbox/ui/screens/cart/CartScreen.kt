package com.ratulhossen.tiffinbox.ui.screens.cart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.ui.components.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onPlaceOrder: () -> Unit
) {
    val items = CartStore.items

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("আপনার Cart") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SummaryRow("Subtotal", CartStore.subtotal)
                        SummaryRow("Delivery Fee", CartStore.DELIVERY_FEE)
                        SummaryRow("Platform Fee", CartStore.PLATFORM_FEE)
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        SummaryRow("Total", CartStore.total, bold = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                FirestoreOrderRepository.placeOrder(
                                    items = items.toList(),
                                    subtotal = CartStore.subtotal,
                                    deliveryFee = CartStore.DELIVERY_FEE,
                                    platformFee = CartStore.PLATFORM_FEE,
                                    total = CartStore.total
                                )
                                CartStore.clear()
                                onPlaceOrder()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("Place Order • ৳${CartStore.total}")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "আপনার Cart খালি 🍽️",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.meal.id }) { cartItem ->
                    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(cartItem.meal.emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cartItem.meal.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "৳${cartItem.meal.price} each",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            QuantityStepper(
                                quantity = cartItem.quantity,
                                onDecrease = {
                                    CartStore.updateQuantity(cartItem.meal.id, cartItem.quantity - 1)
                                },
                                onIncrease = {
                                    CartStore.updateQuantity(cartItem.meal.id, cartItem.quantity + 1)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Int, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            "৳$amount",
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
