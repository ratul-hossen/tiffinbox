package com.ratulhossen.tiffinbox.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val ROLE_SELECT = "role_select"
    const val LOGIN = "login/{role}"
    const val MAIN = "main"
    const val CHEF_DASHBOARD = "chef_dashboard"
    const val MENU_MANAGER = "menu_manager"
    const val CHEF_DETAIL = "chef_detail/{chefId}"
    const val MEAL_DETAIL = "meal_detail/{mealId}"
    const val CART = "cart"
    const val ORDER_TRACKING = "order_tracking/{orderId}"
    const val REVIEW = "review/{chefId}"
    const val CUSTOMER_CHAT = "chat/{chefId}"
    const val CHEF_CHAT_LIST = "chef_chat_list"
    const val CHEF_CHAT = "chef_chat/{customerId}/{chefId}"
    const val SUBSCRIBE = "subscribe/{chefId}"
    const val MY_SUBSCRIPTIONS = "my_subscriptions"
    const val CHEF_LIST = "chef_list/{sort}"
    const val CHECKOUT = "checkout"

    fun login(role: String) = "login/$role"
    fun orderTracking(orderId: String) = "order_tracking/$orderId"
    fun review(chefId: String) = "review/$chefId"
    fun chefDetail(chefId: String) = "chef_detail/$chefId"
    fun mealDetail(mealId: String) = "meal_detail/$mealId"
    fun customerChat(chefId: String) = "chat/$chefId"
    fun chefChat(customerId: String, chefId: String) = "chef_chat/$customerId/$chefId"
    fun subscribe(chefId: String) = "subscribe/$chefId"
    fun chefList(sort: String) = "chef_list/$sort"
}

object BottomTab {
    const val HOME = "tab_home"
    const val SEARCH = "tab_search"
    const val ORDERS = "tab_orders"
    const val FAVORITES = "tab_favorites"
    const val PROFILE = "tab_profile"
}
