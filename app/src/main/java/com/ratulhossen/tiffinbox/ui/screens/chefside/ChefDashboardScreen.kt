package com.ratulhossen.tiffinbox.ui.screens.chefside

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.model.ChefOrderUi
import com.ratulhossen.tiffinbox.data.model.EarningsSummary
import com.ratulhossen.tiffinbox.domain.DemandForecastEngine
import com.ratulhossen.tiffinbox.ui.components.EarningsChart

@Composable
fun ChefDashboardScreen(chefId: String, onLogout: () -> Unit, onManageMenu: () -> Unit, onOpenMessages: () -> Unit) {
    var available by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf<List<ChefOrderUi>>(emptyList()) }
    var earnings by remember { mutableStateOf<EarningsSummary?>(null) }
    var forecastPlates by remember { mutableStateOf<Int?>(null) }
    var kitchenName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chefId) {
        val dailyCounts = FirestoreOrderRepository.getDailyOrderCountsForChef(chefId)
        forecastPlates = DemandForecastEngine.forecastNextDayPlates(dailyCounts)
        orders = FirestoreOrderRepository.getIncomingOrders(chefId)
        earnings = FirestoreOrderRepository.getEarningsSummary(chefId)
        kitchenName = FirestoreCatalogRepository.getChef(chefId)?.kitchenName
    }

    fun respondToOrder(orderId: String, newStatus: String) {
        FirestoreOrderRepository.updateOrderStatus(orderId, newStatus)
        orders = orders.filterNot { it.id == orderId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.chef_dashboard_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    kitchenName ?: stringResource(R.string.chef_dashboard_kitchen_name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.chef_dashboard_available_toggle), style = MaterialTheme.typography.titleMedium)
                Switch(checked = available, onCheckedChange = { available = it })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onManageMenu, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chef_dashboard_manage_menu_button))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenMessages, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.messages_label))
        }

        Spacer(modifier = Modifier.height(16.dp))
        val earningsSnapshot = earnings
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EarningStat(stringResource(R.string.chef_dashboard_earning_today), stringResource(R.string.price_tag, earningsSnapshot?.todayTotal ?: 0), Modifier.weight(1f))
            EarningStat(stringResource(R.string.chef_dashboard_earning_week), stringResource(R.string.price_tag, earningsSnapshot?.weekTotal ?: 0), Modifier.weight(1f))
            EarningStat(stringResource(R.string.chef_dashboard_earning_recent_orders), "${earningsSnapshot?.recentOrderCount ?: 0}", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_chart_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                val dailyTotals = earningsSnapshot?.dailyTotals.orEmpty()
                if (dailyTotals.size >= 2) {
                    EarningsChart(dailyTotals = dailyTotals)
                } else {
                    Text(
                        stringResource(R.string.chef_dashboard_chart_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_forecast_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                val plates = forecastPlates
                if (plates != null) {
                    Text(
                        stringResource(R.string.chef_dashboard_forecast_result, plates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        stringResource(R.string.chef_dashboard_forecast_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.chef_dashboard_new_orders_title), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(10.dp))

        if (orders.isEmpty()) {
            Text(
                stringResource(R.string.chef_dashboard_no_new_orders),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        orders.forEach { order ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(order.itemsSummary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.chef_dashboard_order_number, order.id.take(6)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.price_tag, order.totalPrice),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { respondToOrder(order.id, "Accepted") }) {
                            Text(stringResource(R.string.chef_dashboard_accept_button))
                        }
                        OutlinedButton(onClick = { respondToOrder(order.id, "Cancelled") }) {
                            Text(stringResource(R.string.chef_dashboard_reject_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EarningStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
