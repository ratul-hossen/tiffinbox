package com.ratulhossen.tiffinbox.ui.screens.cart

import com.ratulhossen.tiffinbox.data.AppConfigStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.ratulhossen.tiffinbox.data.DeliveryQuoter
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.ui.components.QuantityStepper
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onCheckout: () -> Unit
) {
    val items = CartStore.items
    // Distance-based delivery fee for this kitchen (admin-set pricing).
    var etaMinutes by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(items.firstOrNull()?.meal?.chefId) {
        val chefId = items.firstOrNull()?.meal?.chefId ?: return@LaunchedEffect
        DeliveryQuoter.quote(chefId)?.let {
            CartStore.deliveryFee = it.fee
            etaMinutes = it.minutes
        }
    }

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.cart_top_bar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Surface(shadowElevation = 8.dp) {
                    // navigationBarsPadding: stay above the phone's back/home buttons.
                    Column(modifier = Modifier.navigationBarsPadding().padding(16.dp)) {
                        SummaryRow(stringResource(R.string.cart_summary_subtotal), CartStore.subtotal)
                        SummaryRow(stringResource(R.string.cart_summary_delivery_fee), CartStore.deliveryFee)
                        etaMinutes?.let {
                            Text(
                                stringResource(R.string.delivery_eta_short, it),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SummaryRow(stringResource(R.string.cart_summary_platform_fee), AppConfigStore.platformFee)
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        SummaryRow(stringResource(R.string.cart_summary_total), CartStore.total, bold = true)
                        Spacer(modifier = Modifier.height(12.dp))
                        TiffinButton(
                            onClick = onCheckout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text(stringResource(R.string.cart_checkout_button, CartStore.total))
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
                PhotoEmptyState(resId = R.drawable.empty_cart, title = stringResource(R.string.cart_empty_message))
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
                            // The meal's real photo (same as on its card), emoji if it has none.
                            Base64Thumbnail(
                                base64 = cartItem.meal.photoBase64,
                                emoji = cartItem.meal.emoji,
                                modifier = Modifier.size(56.dp),
                                emojiFontSize = 26.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cartItem.meal.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.cart_item_price_each, cartItem.meal.price),
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
            stringResource(R.string.price_tag, amount),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
