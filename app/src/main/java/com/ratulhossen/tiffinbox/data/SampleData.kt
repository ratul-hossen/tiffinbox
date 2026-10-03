package com.ratulhossen.tiffinbox.data

import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.data.model.OrderStage
import com.ratulhossen.tiffinbox.data.model.PastOrderUi

object SampleData {

    val categories = listOf("Breakfast", "Lunch", "Dinner", "Snack", "Dessert")

    val chefs = listOf(
        ChefUi(
            id = "chef1",
            kitchenName = "Rukhsana's Kitchen",
            ownerName = "Rukhsana Begum",
            emoji = "🍛",
            specialty = listOf("Bengali", "Home-style"),
            bio = "রান্না আমার নেশা। ২০ বছর ধরে রান্না করছি, এখন সেই স্বাদ আপনাদের কাছে পৌঁছে দিচ্ছি।",
            distanceKm = 0.8,
            overallRating = 4.7,
            taste = 4.9,
            hygiene = 4.8,
            packaging = 4.5,
            onTime = 4.6,
            totalOrders = 128,
            isVerified = true,
            isAvailableToday = true
        ),
        ChefUi(
            id = "chef2",
            kitchenName = "Amina's Home Food",
            ownerName = "Amina Khatun",
            emoji = "🍲",
            specialty = listOf("Diabetic-friendly", "Low-oil"),
            bio = "স্বাস্থ্যকর ও কম তেলের রান্নায় বিশেষজ্ঞ। ডায়াবেটিক-ফ্রেন্ডলি মেনু।",
            distanceKm = 1.4,
            overallRating = 4.5,
            taste = 4.6,
            hygiene = 4.7,
            packaging = 4.2,
            onTime = 4.3,
            totalOrders = 64,
            isVerified = true,
            isAvailableToday = true
        ),
        ChefUi(
            id = "chef3",
            kitchenName = "Nusrat's Tiffin",
            ownerName = "Nusrat Jahan",
            emoji = "🍚",
            specialty = listOf("Veg", "Quick meals"),
            bio = "নতুন শেফ হলেও প্রতিটা প্লেটে যত্ন নিয়ে রান্না করি।",
            distanceKm = 2.1,
            overallRating = 4.3,
            taste = 4.4,
            hygiene = 4.5,
            packaging = 4.0,
            onTime = 4.1,
            totalOrders = 9,
            isVerified = false,
            isAvailableToday = false
        )
    )

    val meals = listOf(
        MealUi(
            id = "meal1",
            chefId = "chef1",
            name = "ভাত, ডাল, মুরগির ঝোল",
            emoji = "🍛",
            description = "ঘরোয়া স্টাইলে রান্না মুরগির ঝোল, গরম ভাত আর ডাল সহ।",
            price = 120,
            category = "Lunch",
            isVeg = false,
            allergens = listOf("None"),
            dietaryTags = listOf("Low-oil"),
            availableQuantity = 5,
            portionSize = "1 plate"
        ),
        MealUi(
            id = "meal2",
            chefId = "chef1",
            name = "ইলিশ ভাজা স্পেশাল থালি",
            emoji = "🍟",
            description = "ইলিশ মাছ ভাজা, ভাত, ডাল ও সবজি সহ পূর্ণ থালি।",
            price = 180,
            category = "Lunch",
            isVeg = false,
            allergens = listOf("Fish"),
            dietaryTags = emptyList(),
            availableQuantity = 3,
            portionSize = "1 plate"
        ),
        MealUi(
            id = "meal3",
            chefId = "chef2",
            name = "লো-অয়েল ভেজিটেবল খিচুড়ি",
            emoji = "🍚",
            description = "কম তেলে রান্না সবজি খিচুড়ি, ডায়াবেটিক-ফ্রেন্ডলি।",
            price = 90,
            category = "Dinner",
            isVeg = true,
            allergens = listOf("None"),
            dietaryTags = listOf("Diabetic-friendly", "Low-oil"),
            availableQuantity = 8,
            portionSize = "1 bowl"
        ),
        MealUi(
            id = "meal4",
            chefId = "chef3",
            name = "পরোটা ও সবজি নাস্তা",
            emoji = "🥞",
            description = "সকালের নাস্তায় গরম পরোটা আর মিক্সড সবজি।",
            price = 60,
            category = "Breakfast",
            isVeg = true,
            allergens = listOf("Gluten"),
            dietaryTags = listOf("Veg"),
            availableQuantity = 10,
            portionSize = "2 pieces"
        )
    )

    val pastOrders = listOf(
        PastOrderUi(
            id = "order1",
            chefName = "Rukhsana's Kitchen",
            chefEmoji = "🍛",
            itemsSummary = "১x ভাত, ডাল, মুরগির ঝোল",
            total = 150,
            status = OrderStage.DELIVERED,
            placedOn = "গতকাল, ১:৩০ PM"
        ),
        PastOrderUi(
            id = "order2",
            chefName = "Amina's Home Food",
            chefEmoji = "🍲",
            itemsSummary = "২x লো-অয়েল ভেজিটেবল খিচুড়ি",
            total = 210,
            status = OrderStage.DELIVERED,
            placedOn = "৩ দিন আগে"
        ),
        PastOrderUi(
            id = "order3",
            chefName = "Rukhsana's Kitchen",
            chefEmoji = "🍛",
            itemsSummary = "১x ইলিশ ভাজা স্পেশাল থালি",
            total = 210,
            status = OrderStage.OUT_FOR_DELIVERY,
            placedOn = "আজ, ১২:৪৫ PM"
        )
    )

    fun chefById(id: String): ChefUi? = chefs.find { it.id == id }
    fun mealById(id: String): MealUi? = meals.find { it.id == id }
    fun mealsForChef(chefId: String): List<MealUi> = meals.filter { it.chefId == chefId }
}
