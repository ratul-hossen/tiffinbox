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
import com.ratulhossen.tiffinbox.ui.screens.auth.CompleteGoogleProfileScreen
import com.ratulhossen.tiffinbox.ui.screens.auth.EmailVerificationScreen
import com.ratulhossen.tiffinbox.ui.screens.auth.LocationSetupScreen
import com.ratulhossen.tiffinbox.ui.screens.auth.LoginScreen
import com.ratulhossen.tiffinbox.ui.screens.cart.CartScreen
import com.ratulhossen.tiffinbox.ui.screens.chat.ChefChatListScreen
import com.ratulhossen.tiffinbox.ui.screens.chat.ChefChatScreen
import com.ratulhossen.tiffinbox.ui.screens.chat.CustomerChatListScreen
import com.ratulhossen.tiffinbox.ui.screens.chat.CustomerChatScreen
import com.ratulhossen.tiffinbox.ui.screens.chef.ChefDetailScreen
import com.ratulhossen.tiffinbox.ui.screens.checkout.CheckoutScreen
import com.ratulhossen.tiffinbox.ui.screens.chefside.ChefDashboardScreen
import com.ratulhossen.tiffinbox.ui.screens.chefside.MenuManagerScreen
import com.ratulhossen.tiffinbox.ui.screens.cheflist.ChefListScreen
import com.ratulhossen.tiffinbox.ui.screens.main.MainScreen
import com.ratulhossen.tiffinbox.ui.screens.meal.MealDetailScreen
import com.ratulhossen.tiffinbox.ui.screens.notifications.NotificationsScreen
import com.ratulhossen.tiffinbox.ui.screens.onboarding.OnboardingScreen
import com.ratulhossen.tiffinbox.ui.screens.order.OrderTrackingScreen
import com.ratulhossen.tiffinbox.ui.screens.review.ReviewScreen
import com.ratulhossen.tiffinbox.ui.screens.roleselect.RoleSelectionScreen
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import com.ratulhossen.tiffinbox.ui.screens.selfservice.HireDeliveryManScreen
import com.ratulhossen.tiffinbox.ui.screens.selfservice.SelfServiceRequestScreen
import com.ratulhossen.tiffinbox.ui.screens.settings.ChangePasswordScreen
import com.ratulhossen.tiffinbox.ui.screens.settings.HelpSupportScreen
import com.ratulhossen.tiffinbox.ui.screens.settings.PaymentMethodsScreen
import com.ratulhossen.tiffinbox.ui.screens.settings.PrivacyPolicyScreen
import com.ratulhossen.tiffinbox.ui.screens.settings.SettingsScreen
import com.ratulhossen.tiffinbox.ui.screens.settings.TermsScreen
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
                    FirestoreUserRepository.registerFcmToken(user.uid)
                    val role = FirestoreUserRepository.getRole(user.uid) ?: UserRole.CUSTOMER.name
                    // isEmailVerified is cached on the device - reload once
                    // so someone who clicked the link since last launch isn't
                    // sent back to the verification screen.
                    if (FirebaseAuthRepository.needsEmailVerification(user) &&
                        !FirebaseAuthRepository.reloadAndCheckEmailVerified()
                    ) {
                        Routes.emailVerification(role, "home")
                    } else if (role == UserRole.CHEF.name) Routes.CHEF_DASHBOARD else Routes.MAIN
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
                onLoginSuccess = { resolvedRole ->
                    val destination = when {
                        FirebaseAuthRepository.needsEmailVerification() -> Routes.emailVerification(resolvedRole, "home")
                        resolvedRole == UserRole.CHEF.name -> Routes.CHEF_DASHBOARD
                        else -> Routes.MAIN
                    }
                    navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
                onSignupComplete = { uid, signupRole ->
                    val next = if (FirebaseAuthRepository.needsEmailVerification()) {
                        Routes.emailVerification(signupRole, "location")
                    } else {
                        Routes.locationSetup(uid, signupRole)
                    }
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onGoogleNeedsProfile = { uid, verifyRole ->
                    navController.navigate(Routes.completeGoogleProfile(uid, verifyRole))
                }
            )
        }

        composable(
            route = Routes.COMPLETE_GOOGLE_PROFILE,
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            val role = backStackEntry.arguments?.getString("role") ?: UserRole.CUSTOMER.name
            CompleteGoogleProfileScreen(
                uid = uid,
                role = role,
                onCompleted = {
                    navController.navigate(Routes.locationSetup(uid, role)) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.EMAIL_VERIFICATION,
            arguments = listOf(
                navArgument("role") { type = NavType.StringType },
                navArgument("next") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: UserRole.CUSTOMER.name
            val next = backStackEntry.arguments?.getString("next") ?: "home"
            EmailVerificationScreen(
                onVerified = {
                    val uid = FirebaseAuthRepository.currentUser?.uid.orEmpty()
                    val destination = when {
                        next == "location" -> Routes.locationSetup(uid, role)
                        role == UserRole.CHEF.name -> Routes.CHEF_DASHBOARD
                        else -> Routes.MAIN
                    }
                    navController.navigate(destination) { popUpTo(0) { inclusive = true } }
                },
                onLogout = {
                    FirebaseAuthRepository.signOut()
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(
            route = Routes.LOCATION_SETUP,
            arguments = listOf(
                navArgument("uid") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid").orEmpty()
            val role = backStackEntry.arguments?.getString("role") ?: UserRole.CUSTOMER.name
            LocationSetupScreen(
                uid = uid,
                role = role,
                onLocationSaved = {
                    val destination = if (role == UserRole.CHEF.name) Routes.CHEF_DASHBOARD else Routes.MAIN
                    navController.navigate(destination) {
                        popUpTo(Routes.LOCATION_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            MainScreen(
                onChefClick = { chefId -> navController.navigate(Routes.chefDetail(chefId)) },
                onMealClick = { mealId -> navController.navigate(Routes.mealDetail(mealId)) },
                onCartClick = { navController.navigate(Routes.CART) },
                onChatClick = { navController.navigate(Routes.CUSTOMER_CHAT_LIST) },
                onSubscriptionsClick = { navController.navigate(Routes.MY_SUBSCRIPTIONS) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onSeeAllChefsClick = { sort -> navController.navigate(Routes.chefList(sort)) },
                onPaymentMethodsClick = { navController.navigate(Routes.PAYMENT_METHODS) },
                onNotificationsClick = { navController.navigate(Routes.NOTIFICATIONS) },
                onSelfServiceClick = { navController.navigate(Routes.SELF_SERVICE_REQUEST) },
                onLogout = {
                    FirebaseAuthRepository.signOut()
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(
            route = Routes.CHEF_LIST,
            arguments = listOf(navArgument("sort") { type = NavType.StringType })
        ) { backStackEntry ->
            val sort = backStackEntry.arguments?.getString("sort").orEmpty()
            ChefListScreen(
                initialSort = sort,
                onBack = { navController.popBackStack() },
                onChefClick = { chefId -> navController.navigate(Routes.chefDetail(chefId)) }
            )
        }

        composable(Routes.MY_SUBSCRIPTIONS) {
            MySubscriptionsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onChangePasswordClick = { navController.navigate(Routes.CHANGE_PASSWORD) },
                onPaymentMethodsClick = { navController.navigate(Routes.PAYMENT_METHODS) },
                onHelpSupportClick = { navController.navigate(Routes.HELP_SUPPORT) },
                onTermsClick = { navController.navigate(Routes.TERMS) },
                onPrivacyPolicyClick = { navController.navigate(Routes.PRIVACY_POLICY) }
            )
        }

        composable(Routes.CHANGE_PASSWORD) {
            ChangePasswordScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PAYMENT_METHODS) {
            PaymentMethodsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.HELP_SUPPORT) {
            HelpSupportScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TERMS) {
            TermsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SELF_SERVICE_REQUEST) {
            SelfServiceRequestScreen(
                onBack = { navController.popBackStack() },
                onRequestCreated = { requestId -> navController.navigate(Routes.hireDeliveryMan(requestId)) }
            )
        }

        composable(
            route = Routes.HIRE_DELIVERY_MAN,
            arguments = listOf(navArgument("requestId") { type = NavType.StringType })
        ) { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString("requestId").orEmpty()
            HireDeliveryManScreen(requestId = requestId, onBack = { navController.popBackStack() })
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
            val chefId = FirebaseAuthRepository.currentUser?.uid.orEmpty()
            ChefDashboardScreen(
                chefId = chefId,
                onLogout = {
                    FirebaseAuthRepository.signOut()
                    navController.navigate(Routes.ROLE_SELECT) { popUpTo(0) { inclusive = true } }
                },
                onManageMenu = { navController.navigate(Routes.MENU_MANAGER) },
                onOpenMessages = { navController.navigate(Routes.CHEF_CHAT_LIST) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }

        composable(Routes.MENU_MANAGER) {
            val chefId = FirebaseAuthRepository.currentUser?.uid.orEmpty()
            MenuManagerScreen(chefId = chefId, onBack = { navController.popBackStack() })
        }

        composable(Routes.CHEF_CHAT_LIST) {
            val chefId = FirebaseAuthRepository.currentUser?.uid.orEmpty()
            ChefChatListScreen(
                chefId = chefId,
                onThreadClick = { customerId ->
                    navController.navigate(Routes.chefChat(customerId, chefId))
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

        composable(Routes.CUSTOMER_CHAT_LIST) {
            val customerId = FirebaseAuthRepository.currentUser?.uid.orEmpty()
            CustomerChatListScreen(
                customerId = customerId,
                onThreadClick = { chefId -> navController.navigate(Routes.customerChat(chefId)) },
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
                onCheckout = { navController.navigate(Routes.CHECKOUT) }
            )
        }

        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                onBack = { navController.popBackStack() },
                onOrderPlaced = { orderId ->
                    navController.navigate(Routes.orderTracking(orderId)) { popUpTo(Routes.MAIN) }
                }
            )
        }

        composable(
            route = Routes.ORDER_TRACKING,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
            OrderTrackingScreen(
                orderId = orderId,
                onDone = { chefId ->
                    navController.navigate(Routes.review(chefId)) { popUpTo(Routes.ORDER_TRACKING) { inclusive = true } }
                }
            )
        }

        composable(
            route = Routes.REVIEW,
            arguments = listOf(navArgument("chefId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chefId = backStackEntry.arguments?.getString("chefId").orEmpty()
            ReviewScreen(chefId = chefId, onSubmitted = {
                navController.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } }
            })
        }
    }
}
