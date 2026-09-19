package com.ratulhossen.tiffinbox.ui.screens.chefside

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp

private data class IncomingOrder(val id: String, val customer: String, val items: String, val amount: Int)

private val dummyOrders = listOf(
    IncomingOrder("o1", "Rafi Ahmed", "2x ভাত-ডাল-মুরগি", 240),
    IncomingOrder("o2", "Mim Akter", "1x ইলিশ থালি", 180)
)

@Composable
fun ChefDashboardScreen(onLogout: () -> Unit, onManageMenu: () -> Unit) {
    var available by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf(dummyOrders) }

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
        Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
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

        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EarningStat("আজকের আয়", "৳ ৪২০", Modifier.weight(1f))
            EarningStat("এই সপ্তাহে", "৳ ২,৯৫০", Modifier.weight(1f))
            EarningStat("মোট অর্ডার", "128", Modifier.weight(1f))
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(order.customer, style = MaterialTheme.typography.titleMedium)
                    Text(
                        order.items,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "৳${order.amount}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { orders = orders.filterNot { it.id == order.id } }) {
                            Text("Accept")
                        }
                        OutlinedButton(onClick = { orders = orders.filterNot { it.id == order.id } }) {
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
    Card(shape = RoundedCornerShape(14.dp), modifier = modifier) {
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
