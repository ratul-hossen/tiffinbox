package com.ratulhossen.tiffinbox.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.ui.screens.auth.LoginScreen
import com.ratulhossen.tiffinbox.ui.screens.cart.CartScreen
import com.ratulhossen.tiffinbox.ui.screens.chat.ChefChatListScreen
import com.ratulhossen.tiffinbox.ui.screens.chat.ChefChatScreen
import com.ratulhossen.tiffinbox.ui.screens.chat.CustomerChatScreen
import com.ratulhossen.tiffinbox.ui.screens.chef.ChefDetailScreen
import com.ratulhossen.tiffinbox.ui.screens.chefside.ChefDashboardScreen
import com.ratulhossen.tiffinbox.ui.screens.chefside.MenuManagerScreen
import com.ratulhossen.tiffinbox.ui.screens.main.MainScreen
import com.ratulhossen.tiffinbox.ui.screens.meal.MealDetailScreen
import com.ratulhossen.tiffinbox.ui.screens.onboarding.OnboardingScreen
import com.ratulhossen.tiffinbox.ui.screens.order.OrderTrackingScreen
import com.ratulhossen.tiffinbox.ui.screens.review.ReviewScreen
import com.ratulhossen.tiffinbox.ui.screens.roleselect.RoleSelectionScreen
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import com.ratulhossen.tiffinbox.ui.screens.splash.SplashScreen
import com.ratulhossen.tiffinbox.ui.screens.subscription.MySubscriptionsScreen
import com.ratulhossen.tiffinbox.ui.screens.subscription.SubscribeScreen

@Composable
fun TiffinNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            fadeIn(animationSpec = tween(250)) +
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(250))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(250))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(250))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(250)) +
                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(250))
        }
    ) {

        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                // Skip straight back in if Firebase already has a signed-in
                // user (e.g. app was reopened), using their persisted role
                // (users/{uid}.role) to decide which home screen to land on.
                val user = FirebaseAuthRepository.currentUser
                val destination = if (user != null) {
                    val role = FirestoreUserRepository.getRole(user.uid)
                    if (role == UserRole.CHEF.name) Routes.CHEF_DASHBOARD else Routes.MAIN
                } else {
                    Routes.ONBOARDING
                }
                navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                navController.navigate(Routes.ROLE_SELECT) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }

        composable(Routes.ROLE_SELECT) {
            RoleSelectionScreen(onRoleSelected = { role ->
                navController.navigate(Routes.login(role.name))
            })
        }

        composable(
            route = Routes.LOGIN,
            arguments = listOf(navArgument("role") { type = NavType.StringType })
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: UserRole.CUSTOMER.name
            LoginScreen(
                role = role,
                onLoginSuccess = {
                    val destination = if (role == UserRole.CHEF.name) Routes.CHEF_DASHBOARD else Routes.MAIN
                    navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onChefClick = { chefId -> navController.navigate(Routes.chefDetail(chefId)) },
                onMealClick = { mealId -> navController.navigate(Routes.mealDetail(mealId)) },
                onCartClick = { navController.navigate(Routes.CART) },
                onSubscriptionsClick = { navController.navigate(Routes.MY_SUBSCRIPTIONS) },
                onLogout = {
                    FirebaseAuthRepository.signOut()
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.MY_SUBSCRIPTIONS) {
            MySubscriptionsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.SUBSCRIBE,
            arguments = listOf(navArgument("chefId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chefId = backStackEntry.arguments?.getString("chefId").orEmpty()
            SubscribeScreen(
                chefId = chefId,
                onBack = { navController.popBackStack() },
                onSubscribed = { navController.popBackStack() }
            )
        }

        composable(Routes.CHEF_DASHBOARD) {
            ChefDashboardScreen(
                onLogout = {
                    FirebaseAuthRepository.signOut()
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(0) { inclusive = true } }
                },
                onManageMenu = { navController.navigate(Routes.MENU_MANAGER) },
                onOpenMessages = { navController.navigate(Routes.CHEF_CHAT_LIST) }
            )
        }

        composable(Routes.MENU_MANAGER) {
            MenuManagerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CHEF_CHAT_LIST) {
            ChefChatListScreen(
                chefId = "chef1",
                onThreadClick = { customerId ->
                    navController.navigate(Routes.chefChat(customerId, "chef1"))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CHEF_CHAT,
            arguments = listOf(
                navArgument("customerId") { type = NavType.StringType },
                navArgument("chefId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId").orEmpty()
            val chefId = backStackEntry.arguments?.getString("chefId").orEmpty()
            ChefChatScreen(
                customerId = customerId,
                chefId = chefId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CUSTOMER_CHAT,
            arguments = listOf(navArgument("chefId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chefId = backStackEntry.arguments?.getString("chefId").orEmpty()
            CustomerChatScreen(
                chefId = chefId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.CHEF_DETAIL,
            arguments = listOf(navArgument("chefId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chefId = backStackEntry.arguments?.getString("chefId").orEmpty()
            ChefDetailScreen(
                chefId = chefId,
                onBack = { navController.popBackStack() },
                onMealClick = { mealId -> navController.navigate(Routes.mealDetail(mealId)) },
                onChatClick = { navController.navigate(Routes.customerChat(chefId)) },
                onSubscribeClick = { navController.navigate(Routes.subscribe(chefId)) }
            )
        }

        composable(
            route = Routes.MEAL_DETAIL,
            arguments = listOf(navArgument("mealId") { type = NavType.StringType })
        ) { backStackEntry ->
            val mealId = backStackEntry.arguments?.getString("mealId").orEmpty()
            MealDetailScreen(
                mealId = mealId,
                onBack = { navController.popBackStack() },
                onAddedToCart = { navController.navigate(Routes.CART) }
            )
        }

        composable(Routes.CART) {
            CartScreen(
                onBack = { navController.popBackStack() },
                onPlaceOrder = {
                    navController.navigate(Routes.ORDER_TRACKING) { popUpTo(Routes.MAIN) }
                }
            )
        }

        composable(Routes.ORDER_TRACKING) {
            OrderTrackingScreen(onDone = {
                navController.navigate(Routes.REVIEW) { popUpTo(Routes.ORDER_TRACKING) { inclusive = true } }
            })
        }

        composable(Routes.REVIEW) {
            ReviewScreen(onSubmitted = {
                navController.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } }
            })
        }
    }
}
