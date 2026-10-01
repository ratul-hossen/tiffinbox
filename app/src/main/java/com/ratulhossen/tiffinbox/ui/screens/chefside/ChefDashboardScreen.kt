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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.model.ChefOrderUi
import com.ratulhossen.tiffinbox.data.model.EarningsSummary
import com.ratulhossen.tiffinbox.domain.DemandForecastEngine
import com.ratulhossen.tiffinbox.ui.components.EarningsChart

private const val DEMO_CHEF_ID = "chef1"

@Composable
fun ChefDashboardScreen(onLogout: () -> Unit, onManageMenu: () -> Unit, onOpenMessages: () -> Unit) {
    var available by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf<List<ChefOrderUi>>(emptyList()) }
    var earnings by remember { mutableStateOf<EarningsSummary?>(null) }
    var forecastPlates by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val dailyCounts = FirestoreOrderRepository.getDailyOrderCountsForChef(DEMO_CHEF_ID)
        forecastPlates = DemandForecastEngine.forecastNextDayPlates(dailyCounts)
        orders = FirestoreOrderRepository.getIncomingOrders(DEMO_CHEF_ID)
        earnings = FirestoreOrderRepository.getEarningsSummary(DEMO_CHEF_ID)
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
                Text("Chef Dashboard", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Rukhsana's Kitchen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onLogout) { Text("Logout") }
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
                Text("আজ Available আছি", style = MaterialTheme.typography.titleMedium)
                Switch(checked = available, onCheckedChange = { available = it })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onManageMenu, modifier = Modifier.fillMaxWidth()) {
            Text("মেনু ম্যানেজ করুন")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenMessages, modifier = Modifier.fillMaxWidth()) {
            Text("Messages")
        }

        Spacer(modifier = Modifier.height(16.dp))
        val earningsSnapshot = earnings
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EarningStat("আজকের আয়", "৳${earningsSnapshot?.todayTotal ?: 0}", Modifier.weight(1f))
            EarningStat("৭ দিনে আয়", "৳${earningsSnapshot?.weekTotal ?: 0}", Modifier.weight(1f))
            EarningStat("সাম্প্রতিক অর্ডার", "${earningsSnapshot?.recentOrderCount ?: 0}", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("দৈনিক আয় (গত ১৪ দিন)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                val dailyTotals = earningsSnapshot?.dailyTotals.orEmpty()
                if (dailyTotals.size >= 2) {
                    EarningsChart(dailyTotals = dailyTotals)
                } else {
                    Text(
                        "চার্ট দেখানোর জন্য যথেষ্ট অর্ডার ইতিহাস এখনো নেই",
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
                Text("আগামীকালের চাহিদার পূর্বাভাস", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                val plates = forecastPlates
                if (plates != null) {
                    Text(
                        "~$plates প্লেট রান্নার প্রস্তুতি নিন",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "পূর্বাভাসের জন্য এখনো পর্যাপ্ত অর্ডার ইতিহাস নেই",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("নতুন অর্ডার", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(10.dp))

        if (orders.isEmpty()) {
            Text(
                "এই মুহূর্তে কোনো নতুন অর্ডার নেই।",
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
                        "Order #${order.id.take(6)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "৳${order.totalPrice}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { respondToOrder(order.id, "Accepted") }) {
                            Text("Accept")
                        }
                        OutlinedButton(onClick = { respondToOrder(order.id, "Cancelled") }) {
                            Text("Reject")
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
