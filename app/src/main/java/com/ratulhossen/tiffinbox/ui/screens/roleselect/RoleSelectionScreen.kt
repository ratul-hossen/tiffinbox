package com.ratulhossen.tiffinbox.ui.screens.roleselect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

enum class UserRole { CUSTOMER, CHEF }

@Composable
fun RoleSelectionScreen(onRoleSelected: (UserRole) -> Unit) {
    var selected by remember { mutableStateOf<UserRole?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "আপনি কে হিসেবে যুক্ত হতে চান?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "পরে আপনি এটা পরিবর্তন করতে পারবেন",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        RoleCard(
            emoji = "🍽️",
            title = "আমি একজন Customer",
            description = "কাছাকাছি home chef থেকে খাবার অর্ডার করতে চাই",
            selected = selected == UserRole.CUSTOMER,
            onClick = { selected = UserRole.CUSTOMER }
        )
        Spacer(modifier = Modifier.height(16.dp))
        RoleCard(
            emoji = "👩‍🍳",
            title = "আমি একজন Home Chef",
            description = "আমার রান্না বিক্রি করে আয় করতে চাই",
            selected = selected == UserRole.CHEF,
            onClick = { selected = UserRole.CHEF }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { selected?.let(onRoleSelected) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun RoleCard(
    emoji: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
