package com.ratulhossen.tiffinbox.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIntoContainer
import androidx.compose.animation.slideOutOfContainer
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.ui.screens.auth.LoginScreen
import com.ratulhossen.tiffinbox.ui.screens.cart.CartScreen
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

@Composable
fun TiffinNavGraph(navController: NavHostController = rememberNavController()) {
    var selectedRole = UserRole.CUSTOMER

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
                // user (e.g. app was reopened); defaults to the customer
                // home since chef role isn't persisted anywhere yet.
                val destination = if (FirebaseAuthRepository.currentUser != null) {
                    Routes.MAIN
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
                selectedRole = role
                navController.navigate(Routes.LOGIN)
            })
        }

        composable(Routes.LOGIN) {
            LoginScreen(onLoginSuccess = {
                val destination = if (selectedRole == UserRole.CHEF) Routes.CHEF_DASHBOARD else Routes.MAIN
                navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.MAIN) {
            MainScreen(
                onChefClick = { chefId -> navController.navigate(Routes.chefDetail(chefId)) },
                onMealClick = { mealId -> navController.navigate(Routes.mealDetail(mealId)) },
                onCartClick = { navController.navigate(Routes.CART) },
                onLogout = {
                    FirebaseAuthRepository.signOut()
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(Routes.CHEF_DASHBOARD) {
            ChefDashboardScreen(
                onLogout = {
                    FirebaseAuthRepository.signOut()
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(0) { inclusive = true } }
                },
                onManageMenu = { navController.navigate(Routes.MENU_MANAGER) }
            )
        }

        composable(Routes.MENU_MANAGER) {
            MenuManagerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHEF_DETAIL,
            arguments = listOf(navArgument("chefId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chefId = backStackEntry.arguments?.getString("chefId").orEmpty()
            ChefDetailScreen(
                chefId = chefId,
                onBack = { navController.popBackStack() },
                onMealClick = { mealId -> navController.navigate(Routes.mealDetail(mealId)) }
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
