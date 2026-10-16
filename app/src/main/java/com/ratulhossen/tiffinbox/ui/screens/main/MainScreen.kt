package com.ratulhossen.tiffinbox.ui.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
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
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.ui.screens.favorites.FavoritesScreen
import com.ratulhossen.tiffinbox.ui.screens.home.HomeScreen
import com.ratulhossen.tiffinbox.ui.screens.orders.OrdersScreen
import com.ratulhossen.tiffinbox.ui.screens.profile.ProfileScreen
import com.ratulhossen.tiffinbox.ui.screens.search.SearchScreen
import androidx.compose.material3.TopAppBarDefaults

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onChefClick: (String) -> Unit,
    onMealClick: (String) -> Unit,
    onCartClick: () -> Unit,
    onChatClick: () -> Unit,
    onSubscriptionsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSeeAllChefsClick: (String) -> Unit,
    onPaymentMethodsClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val cartCount = CartStore.items.sumOf { it.quantity }
    val tabs = listOf(
        Tab(stringResource(R.string.nav_home), Icons.Filled.Home),
        Tab(stringResource(R.string.nav_search), Icons.Filled.Search),
        Tab(stringResource(R.string.nav_orders), Icons.Filled.Receipt),
        Tab(stringResource(R.string.nav_favorites), Icons.Filled.Favorite),
        Tab(stringResource(R.string.nav_profile), Icons.Filled.Person)
    )

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onChatClick) {
                        Icon(
                            Icons.Filled.Chat,
                            contentDescription = stringResource(R.string.content_description_chat),
                            tint = Color(0xFF25D366)
                        )
                    }
                    IconButton(onClick = onCartClick) {
                        BadgedBox(
                            badge = {
                                if (cartCount > 0) {
                                    Badge {
                                        AnimatedContent(targetState = cartCount, label = "cartCount") { count ->
                                            Text("$count")
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ShoppingCart, contentDescription = stringResource(R.string.content_description_cart))
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Floating, rounded tab bar with a tinted pill behind the active tab.
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 16.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = {
                                Text(
                                    tab.label,
                                    fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onChefClick = onChefClick,
                    onMealClick = onMealClick,
                    onSearchClick = { selectedTab = 1 },
                    onProfileClick = { selectedTab = 4 },
                    onSeeAllClick = onSeeAllChefsClick,
                    onLocationClick = onEditProfileClick,
                    onRewardsClick = onRewardsClick
                )
                1 -> SearchScreen(onMealClick = onMealClick)
                2 -> OrdersScreen()
                3 -> FavoritesScreen(onChefClick = onChefClick)
                4 -> ProfileScreen(
                    onLogout = onLogout,
                    onSubscriptionsClick = onSubscriptionsClick,
                    onSettingsClick = onSettingsClick,
                    onPaymentMethodClick = onPaymentMethodsClick,
                    onNotificationsClick = onNotificationsClick,
                    onEditProfileClick = onEditProfileClick,
                    onRewardsClick = onRewardsClick,
                    onMyOrdersClick = { selectedTab = 2 },
                    onFavoritesClick = { selectedTab = 3 }
                )
            }
        }
    }
}
