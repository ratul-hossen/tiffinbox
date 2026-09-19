package com.ratulhossen.tiffinbox.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val ROLE_SELECT = "role_select"
    const val LOGIN = "login"
    const val MAIN = "main"
    const val CHEF_DASHBOARD = "chef_dashboard"
    const val MENU_MANAGER = "menu_manager"
    const val CHEF_DETAIL = "chef_detail/{chefId}"
    const val MEAL_DETAIL = "meal_detail/{mealId}"
    const val CART = "cart"
    const val ORDER_TRACKING = "order_tracking"
    const val REVIEW = "review"

    fun chefDetail(chefId: String) = "chef_detail/$chefId"
    fun mealDetail(mealId: String) = "meal_detail/$mealId"
}

object BottomTab {
    const val HOME = "tab_home"
    const val SEARCH = "tab_search"
    const val ORDERS = "tab_orders"
    const val FAVORITES = "tab_favorites"
    const val PROFILE = "tab_profile"
}
