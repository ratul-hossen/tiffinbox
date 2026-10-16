package com.ratulhossen.tiffinbox.ui.screens.home

import com.ratulhossen.tiffinbox.domain.RewardsStatus
import com.ratulhossen.tiffinbox.ui.components.shiftingBrandBackground
import com.ratulhossen.tiffinbox.ui.components.FoodOrbit
import androidx.compose.ui.graphics.RectangleShape
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCouponRepository
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.ratulhossen.tiffinbox.ui.theme.brandGradient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.components.LightStatusBarIcons
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.ratulhossen.tiffinbox.data.firebase.FirestoreReviewRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreAppConfigRepository
import com.ratulhossen.tiffinbox.domain.RecommendationContext
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.location.LocationRepository
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.domain.RecommendationEngine
import com.ratulhossen.tiffinbox.domain.ml.MlRecommendModel
import com.ratulhossen.tiffinbox.ui.components.ChefCard
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.util.NEARBY_RADIUS_KM
import com.ratulhossen.tiffinbox.util.recommendable
import com.ratulhossen.tiffinbox.util.withRealDistances
import com.ratulhossen.tiffinbox.ui.components.CirclePhoto
import com.ratulhossen.tiffinbox.ui.components.PhotoEmptyState

@Composable
fun HomeScreen(
    onChefClick: (String) -> Unit,
    onMealClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSeeAllClick: (String) -> Unit,
    onLocationClick: () -> Unit = {},
    onRewardsClick: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    cartCount: Int = 0
) {
    val context = LocalContext.current
    // The header/photo runs behind the status bar - keep its icons white.
    LightStatusBarIcons()
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var chefs by remember { mutableStateOf<List<ChefUi>>(emptyList()) }
    var aiRecommendedMeals by remember { mutableStateOf<List<MealUi>>(emptyList()) }
    var catalogMeals by remember { mutableStateOf<List<MealUi>>(emptyList()) }
    // Only true once we've replaced the static/demo distanceKm with a real
    // haversine distance from the customer's own saved location - until
    // then the 2-3km "nearby" radius filter below would be meaningless.
    var hasRealDistances by remember { mutableStateOf(false) }
    // The customer's own saved (default) address - the same point used for
    // the distance math below, so the header and "nearby" always agree.
    // Starts from the copy saved on the phone last time, so the header shows
    // the address instantly instead of "…" while Firestore loads.
    val uidForAddress = FirebaseAuthRepository.currentUser?.uid
    var myAddress by remember { mutableStateOf(HomeAddressCache.read(context, uidForAddress)) }
    LaunchedEffect(uidForAddress) {
        // Refresh on its own (not after the meals/chefs loads) and only touch
        // the header if the saved address actually changed.
        val uid = uidForAddress ?: run { myAddress = ""; return@LaunchedEffect }
        // A failed read (offline) keeps the saved copy instead of blanking it.
        val profile = FirestoreUserRepository.getProfile(uid) ?: run {
            if (myAddress == null) myAddress = ""
            return@LaunchedEffect
        }
        val fresh = profile.address.ifBlank { null }
            ?: FirestoreUserRepository.getLocation(uid)?.let { LocationRepository.reverseGeocode(context, it) }
            ?: ""
        if (fresh != myAddress) myAddress = fresh
        HomeAddressCache.write(context, uid, fresh)
    }
    var rewards by remember { mutableStateOf<RewardsStatus?>(null) }
    LaunchedEffect(Unit) {
        if (FirebaseAuthRepository.currentUser != null) rewards = FirestoreOrderRepository.getMyRewards()
    }

    // Signals the recommender uses (review averages, delivery ratings, the
    // admin-set radius, my orders, offers); filled by the load below.
    var recContext by remember { mutableStateOf(RecommendationContext()) }

    LaunchedEffect(Unit) {
        val allMeals = FirestoreCatalogRepository.getAllMeals()
        catalogMeals = allMeals

        val firestoreChefs = FirestoreCatalogRepository.getChefs()
        if (firestoreChefs.isEmpty()) return@LaunchedEffect
        val uid = FirebaseAuthRepository.currentUser?.uid
        val myLocation = uid?.let { FirestoreUserRepository.getLocation(it) }
        val loadedChefs = if (myLocation != null) firestoreChefs.withRealDistances(myLocation) else firestoreChefs

        // The remaining signals are independent reads - fetch them together.
        val ctx = coroutineScope {
            val offers = async { FirestoreCouponRepository.getActiveOfferCounts() }
            val signals = async { FirestoreOrderRepository.getMyOrderSignals() }
            val radius = async { FirestoreAppConfigRepository.recommendRadiusKm() }
            val reviews = async { FirestoreReviewRepository.getChefReviewStats() }
            RecommendationContext(
                hasRealDistances = myLocation != null,
                radiusKm = radius.await(),
                reviewStats = reviews.await(),
                myOrderCounts = signals.await().first,
                offerCounts = offers.await()
            ) to signals.await().second
        }
        val (recCtx, orderedMealIds) = ctx

        if (allMeals.isNotEmpty()) {
            // "AI Recommended For You": the main signal is MlRecommendModel.kt,
            // a logistic regression trained offline on a public Swiggy dataset
            // (see ml/), run on-device from the meal's price/veg/offers/
            // popularity. On top of it: the kitchen's quality (customers'
            // rating + delivery stars), how close it is, kitchens the customer
            // ordered from again, and categories they ordered before.
            val chefById = loadedChefs.associateBy { it.id }
            val platformOffers = recCtx.offerCounts[""] ?: 0
            val orderedCategories = allMeals.filter { it.id in orderedMealIds }.map { it.category }.toSet()
            aiRecommendedMeals = allMeals
                .sortedByDescending { meal ->
                    val modelScore = MlRecommendModel.predict(
                        avgPrice = meal.price.toDouble(),
                        vegFraction = if (meal.isVeg) 1.0 else 0.0,
                        numOffers = ((recCtx.offerCounts[meal.chefId] ?: 0) + platformOffers).toDouble(),
                        popularity = meal.availableQuantity.toDouble()
                    )
                    val chef = chefById[meal.chefId]
                    val kitchenQuality = chef?.let { 0.15 * RecommendationEngine.quality(it, recCtx) } ?: 0.0
                    val closeness = chef?.let { 0.10 * RecommendationEngine.proximity(it.distanceKm, recCtx) } ?: 0.0
                    val chefAffinity = 0.15 * ((recCtx.myOrderCounts[meal.chefId] ?: 0).coerceAtMost(3) / 3.0)
                    val categoryAffinity = if (meal.category in orderedCategories) 0.08 else 0.0
                    modelScore + kitchenQuality + closeness + chefAffinity + categoryAffinity
                }
        }

        recContext = recCtx
        chefs = loadedChefs
        hasRealDistances = myLocation != null
    }

    // Category tiles filter both the meals shown and the chef rows: only
    // chefs who have at least one meal in the selected category remain.
    val categoryMeals = remember(catalogMeals, selectedCategory) {
        selectedCategory?.let { cat -> catalogMeals.filter { it.category.equals(cat, ignoreCase = true) } }.orEmpty()
    }
    val visibleChefs = remember(chefs, categoryMeals, selectedCategory) {
        if (selectedCategory == null) chefs
        else categoryMeals.map { it.chefId }.toSet().let { ids -> chefs.filter { it.id in ids } }
    }
    val recommendedChefs = remember(visibleChefs, hasRealDistances, recContext) {
        RecommendationEngine.recommend(visibleChefs.recommendable(hasRealDistances, recContext.radiusKm), recContext)
    }
    // Kitchens close enough to recommend from (see RECOMMEND_RADIUS_KM). Empty
    // until the chefs load, so far-away meals never flash in first.
    val recommendableChefIds = remember(chefs, hasRealDistances, recContext) {
        chefs.recommendable(hasRealDistances, recContext.radiusKm).map { it.id }.toSet()
    }
    val aiMealsShown = remember(aiRecommendedMeals, recommendableChefIds) {
        aiRecommendedMeals.filter { it.chefId in recommendableChefIds }.take(10)
    }
    // Chefs marked these as Today's Special in Menu Manager (it lapses at
    // midnight by itself). Only from chefs listed to this customer, nearest
    // first. If nobody marked one today, fall back to the top AI picks with
    // a real photo so the banner is never an empty promise.
    val todaySpecials = remember(catalogMeals, chefs, recommendableChefIds) {
        val chefDistance = chefs.filter { it.id in recommendableChefIds }.associate { it.id to it.distanceKm }
        catalogMeals
            .filter { it.isTodaySpecial && it.chefId in chefDistance && it.availableQuantity > 0 }
            .sortedBy { chefDistance[it.chefId] ?: Double.MAX_VALUE }
            .take(8)
    }
    val heroIsSpecial = todaySpecials.isNotEmpty()
    val heroMeals = remember(todaySpecials, aiMealsShown) {
        todaySpecials.ifEmpty {
            aiMealsShown.filter { it.photoBase64.isNotBlank() }.take(5)
        }
    }

    // The AI row below skips whatever the banner above already shows, so the
    // same meal never appears twice on Home.
    val aiListMeals = remember(aiMealsShown, heroMeals) {
        val inBanner = heroMeals.map { it.id }.toSet()
        aiMealsShown.filter { it.id !in inBanner }
    }

    val nearbyChefs = remember(visibleChefs, hasRealDistances) {
        val sorted = visibleChefs.sortedBy { it.distanceKm }
        if (hasRealDistances) sorted.filter { it.distanceKm <= NEARBY_RADIUS_KM } else sorted
    }

    // Sections fade/slide in once on first composition, so Home feels alive
    // instead of popping in all at once.
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        // Header: profile + app name + chat/cart, then location + points,
        // greeting + orbiting food plates, search - on a slowly colour-shifting
        // theme gradient that runs up behind the status bar (no separate top bar).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .shiftingBrandBackground()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 22.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderCircleButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = stringResource(R.string.home_profile_content_description),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.app_name),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.weight(1f)
                    )
                    HeaderCircleButton(onClick = onChatClick) {
                        Icon(
                            Icons.Filled.Chat,
                            contentDescription = stringResource(R.string.content_description_chat),
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    HeaderCircleButton(onClick = onCartClick) {
                        BadgedBox(
                            badge = {
                                if (cartCount > 0) {
                                    Badge(
                                        containerColor = Color.White,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ) { Text("$cartCount") }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.ShoppingCart,
                                contentDescription = stringResource(R.string.content_description_cart),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.clickable(onClick = onLocationClick)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    when (val a = myAddress) {
                                        null -> "…"
                                        "" -> stringResource(R.string.home_location_not_set)
                                        else -> a
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    rewards?.let { r ->
                        Spacer(modifier = Modifier.width(8.dp))
                        // Points chip -> Rewards screen.
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier.clickable(onClick = onRewardsClick)
                        ) {
                            Text(
                                "${r.rank.emoji} ⚡${r.balance}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.home_greeting),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 28.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.home_header_tagline),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    FoodOrbit(
                        center = R.drawable.hero_food,
                        satellites = listOf(
                            R.drawable.cat_breakfast,
                            R.drawable.cat_lunch,
                            R.drawable.cat_snack,
                            R.drawable.cat_dinner,
                            R.drawable.cat_dessert
                        ),
                        size = 112.dp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onSearchClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.home_search_content_description),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hero banner with a real food photo (res/drawable-nodpi/hero_food.jpg).
        AnimatedVisibility(
            visible = revealed,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }
        ) {
            if (heroMeals.isNotEmpty()) {
                // Today's Special: meals chefs marked in Menu Manager today
                // (nearest first); otherwise the top AI picks that have a photo.
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(heroMeals, key = { it.id }) { meal ->
                        TodaySpecialCard(
                            meal = meal,
                            chefName = chefs.find { it.id == meal.chefId }?.kitchenName.orEmpty(),
                            badge = stringResource(if (heroIsSpecial) R.string.home_hero_badge else R.string.home_hero_badge_pick),
                            modifier = Modifier.fillParentMaxWidth(if (heroMeals.size == 1) 1f else 0.86f),
                            onClick = { onMealClick(meal.id) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .height(170.dp)
                        .shadow(12.dp, MaterialTheme.shapes.large)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    Image(
                        painter = painterResource(R.drawable.hero_food),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.CenterEnd,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(20.dp)
                            .fillMaxWidth(0.7f)
                    ) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                            Text(
                                stringResource(R.string.home_hero_badge_generic),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.home_hero_title),
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.home_hero_subtitle),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Categories as round emoji tiles.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(SampleData.categories) { category ->
                val isSelected = selectedCategory == category
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { selectedCategory = if (isSelected) null else category }
                        .padding(4.dp)
                ) {
                    // Real food photo in a circle; the selected one gets a
                    // brand-gradient ring.
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) Modifier.background(brandGradient())
                                else Modifier.background(Color.Transparent)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CirclePhoto(
                            resId = categoryPhoto(category),
                            size = if (isSelected) 58.dp else 64.dp,
                            elevation = if (isSelected) 0.dp else 4.dp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        category,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        selectedCategory?.let { cat ->
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.home_category_meals_title, cat),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (categoryMeals.isEmpty()) {
                Text(
                    stringResource(R.string.home_category_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(categoryMeals, key = { it.id }) { meal ->
                        MealCard(
                            meal = meal,
                            modifier = Modifier.width(240.dp),
                            onClick = { onMealClick(meal.id) }
                        )
                    }
                }
            }
        }

        // Also shown when kitchens exist but none is within reach of the
        // customer (e.g. every kitchen is in another city), instead of empty
        // section headers with nothing under them.
        val nothingNearby = chefs.isNotEmpty() && selectedCategory == null &&
            recommendedChefs.isEmpty() && nearbyChefs.isEmpty() && aiMealsShown.isEmpty()
        if (chefs.isEmpty() || nothingNearby) {
            Spacer(modifier = Modifier.height(28.dp))
            PhotoEmptyState(
                resId = R.drawable.empty_orders,
                title = stringResource(R.string.home_empty_title),
                subtitle = stringResource(R.string.home_empty_subtitle)
            )
        }

        if (recommendedChefs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(
                title = stringResource(R.string.home_section_recommended),
                onSeeAllClick = { onSeeAllClick("recommended") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(recommendedChefs) { chef ->
                    ChefCard(
                        chef = chef,
                        modifier = Modifier.width(180.dp),
                        onClick = { onChefClick(chef.id) }
                    )
                }
            }
        }

        if (aiListMeals.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.home_section_ai_recommended),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(aiListMeals, key = { it.id }) { meal ->
                    MealCard(
                        meal = meal,
                        modifier = Modifier.width(220.dp),
                        onClick = { onMealClick(meal.id) }
                    )
                }
            }
        }

        if (nearbyChefs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(
                title = stringResource(R.string.home_section_nearby),
                onSeeAllClick = { onSeeAllClick("nearby") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(nearbyChefs) { chef ->
                    ChefCard(
                        chef = chef,
                        modifier = Modifier.width(180.dp),
                        onClick = { onChefClick(chef.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAllClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        // A light text link, not a filled pill: "See all" is secondary to the content.
        TextButton(onClick = onSeeAllClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(
                stringResource(R.string.home_see_all),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun categoryPhoto(category: String): Int = when (category) {
    "Breakfast" -> R.drawable.cat_breakfast
    "Dinner" -> R.drawable.cat_dinner
    "Snack" -> R.drawable.cat_snack
    "Dessert" -> R.drawable.cat_dessert
    else -> R.drawable.cat_lunch
}

/** One Today's Special banner card: the chef's food photo (or the stock hero photo), name, kitchen, price. */
@Composable
private fun TodaySpecialCard(
    meal: MealUi,
    chefName: String,
    badge: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(180.dp)
            .shadow(12.dp, MaterialTheme.shapes.large)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        if (meal.photoBase64.isNotBlank()) {
            Base64Thumbnail(
                base64 = meal.photoBase64,
                emoji = meal.emoji,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape
            )
        } else {
            Image(
                painter = painterResource(R.drawable.hero_food),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp)
        ) {
            Text(
                badge,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                meal.name,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(chefName, "৳${meal.price}").filter { it.isNotBlank() }.joinToString(" · "),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Round, translucent-white button used in the Home header (profile, chat, cart). */
@Composable
private fun HeaderCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Last known Home header address per account, kept on the phone (SharedPreferences). */
private object HomeAddressCache {
    private const val PREFS = "home_address_cache"

    fun read(context: android.content.Context, uid: String?): String? =
        uid?.let { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(it, null) }

    fun write(context: android.content.Context, uid: String, address: String) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(uid, address).apply()
    }
}
