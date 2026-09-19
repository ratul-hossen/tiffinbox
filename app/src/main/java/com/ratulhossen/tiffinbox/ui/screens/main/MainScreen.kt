package com.ratulhossen.tiffinbox.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.ui.screens.home.HomeScreen

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Filled.Home),
    Tab("Search", Icons.Filled.Search),
    Tab("Orders", Icons.Filled.Receipt),
    Tab("Favorites", Icons.Filled.Favorite),
    Tab("Profile", Icons.Filled.Person)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onChefClick: (String) -> Unit,
    onCartClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val cartCount = CartStore.items.sumOf { it.quantity }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TiffinBox") },
                actions = {
                    IconButton(onClick = onCartClick) {
                        BadgedBox(
                            badge = {
                                if (cartCount > 0) {
                                    Badge { Text("$cartCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = "Cart")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(onChefClick = onChefClick)
                else -> ComingSoon(tabs[selectedTab].label)
            }
        }
    }
}

@Composable
private fun ComingSoon(name: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("$name — শীঘ্রই আসছে")
    }
}
