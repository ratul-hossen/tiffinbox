package com.ratulhossen.tiffinbox.ui.screens.chef

import com.ratulhossen.tiffinbox.ui.components.formatDistanceKm
import com.ratulhossen.tiffinbox.ui.components.isNewKitchen
import com.ratulhossen.tiffinbox.ui.components.NewKitchenBadge
import com.ratulhossen.tiffinbox.util.ImageCompressor
import com.ratulhossen.tiffinbox.data.firebase.ReviewUi
import com.ratulhossen.tiffinbox.data.firebase.FirestoreReviewRepository
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Verified
import com.ratulhossen.tiffinbox.ui.components.MealPlanPromoCard
import com.ratulhossen.tiffinbox.ui.components.cardEdge
import com.ratulhossen.tiffinbox.ui.components.DeliveryQuoteLine
import com.ratulhossen.tiffinbox.domain.DeliveryQuote
import com.ratulhossen.tiffinbox.data.DeliveryQuoter
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreSubscriptionRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.data.model.SubscriptionUi
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.ui.components.MealCard
import com.ratulhossen.tiffinbox.ui.components.RatingRow
import com.ratulhossen.tiffinbox.ui.components.ShimmerBox
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefDetailScreen(
    chefId: String,
    onBack: () -> Unit,
    onMealClick: (String) -> Unit,
    onChatClick: () -> Unit,
    onSubscribeClick: () -> Unit
) {
    var chefState by remember(chefId) { mutableStateOf<ChefUi?>(SampleData.chefById(chefId)) }
    var meals by remember(chefId) { mutableStateOf(SampleData.mealsForChef(chefId)) }
    var isLoading by remember(chefId) { mutableStateOf(chefState == null) }
    val context = LocalContext.current
    var reviews by remember(chefId) { mutableStateOf<List<ReviewUi>>(emptyList()) }

    var existingSub by remember(chefId) { mutableStateOf<SubscriptionUi?>(null) }
    LaunchedEffect(chefId) {
        reviews = FirestoreReviewRepository.getReviewsForChef(chefId)
        val uid = FirebaseAuthRepository.currentUser?.uid
        if (uid != null) {
            existingSub = FirestoreSubscriptionRepository.getCustomerActiveSubscriptionForChef(uid, chefId)
        }
    }

    var delivery by remember(chefId) { mutableStateOf<DeliveryQuote?>(null) }

    LaunchedEffect(chefId) {
        FirestoreCatalogRepository.getChefs().find { it.id == chefId }?.let { found ->
            // Real kitchen -> customer distance (the stored one is only a placeholder).
            val km = DeliveryQuoter.distanceKm(found)
            chefState = if (km != null) found.copy(distanceKm = km) else found
            delivery = DeliveryQuoter.quote(found)
        }
        val firestoreMeals = FirestoreCatalogRepository.getMealsForChef(chefId)
        // Today's specials first.
        if (firestoreMeals.isNotEmpty()) meals = firestoreMeals.sortedByDescending { it.isTodaySpecial }
        isLoading = false
    }

    if (chefState == null) {
        if (isLoading) {
            ChefDetailShimmer()
        }
        return
    }
    val chef = chefState!!

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(chef.kitchenName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                },
                actions = {
                    IconButton(onClick = onChatClick) {
                        Icon(Icons.Filled.Chat, contentDescription = stringResource(R.string.content_description_chat))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Base64Thumbnail(
                    base64 = chef.photoBase64,
                    emoji = chef.emoji,
                    // 16:9 - the frame kitchen photos are cropped to on upload.
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (chef.photoBase64.isBlank()) Modifier.height(140.dp)
                            else Modifier.aspectRatio(ImageCompressor.KITCHEN_ASPECT)
                        ),
                    shape = RectangleShape,
                    emojiFontSize = 56.sp
                )
            }
            item {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chef.kitchenName, style = MaterialTheme.typography.headlineSmall)
                        if (chef.isFullyVerified) {
                            Spacer(modifier = Modifier.width(8.dp))
                            VerifiedPill()
                        }
                        if (chef.isDemo) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    stringResource(R.string.chef_card_demo_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.chef_detail_owner_by, chef.ownerName),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (chef.deliveryMode == "tiffinbox") {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                stringResource(R.string.chef_card_tiffinbox_delivery_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isNewKitchen(chef)) {
                            NewKitchenBadge()
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.chef_distance_away, formatDistanceKm(chef.distanceKm)))
                        } else {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(
                                    R.string.chef_rating_summary,
                                    chef.overallRating.toString(),
                                    chef.totalOrders.toString(),
                                    formatDistanceKm(chef.distanceKm)
                                )
                            )
                        }
                    }
                    delivery?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        DeliveryQuoteLine(it)
                    }
                    if (chef.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(chef.bio, style = MaterialTheme.typography.bodyMedium)
                    }

                    // Kitchen facts: experience, kitchen type, orders served.
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (chef.yearsOfExperience > 0) {
                            FactTile(
                                value = stringResource(R.string.chef_fact_years_value, chef.yearsOfExperience),
                                label = stringResource(R.string.chef_fact_years_label),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (chef.kitchenType.isNotBlank()) {
                            FactTile(value = chef.kitchenType, label = stringResource(R.string.chef_fact_type_label), modifier = Modifier.weight(1f))
                        }
                        FactTile(
                            value = chef.totalOrders.toString(),
                            label = stringResource(R.string.chef_fact_orders_label),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (chef.specialty.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.chef_detail_specialties_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            chef.specialty.forEach { tag ->
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(
                                        tag,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (chef.story.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.chef_detail_story_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(chef.story, style = MaterialTheme.typography.bodyMedium)
                    }

                    // Meal plans, highlighted for students (weekly / monthly).
                    Spacer(modifier = Modifier.height(18.dp))
                    val sub = existingSub
                    if (sub != null) {
                        Surface(
                            onClick = onChatClick,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (sub.isApprovedByChef) "Active Meal Plan: ${sub.plan.name}" else "Meal Plan Pending Approval",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${sub.mealsPerWeek} meals/wk · ${if (sub.scheduleSummary.isNotBlank()) sub.scheduleSummary else sub.mealType}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = onChatClick) {
                                    Text("চ্যাট", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        MealPlanPromoCard(onClick = onSubscribeClick)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(stringResource(R.string.chef_detail_rating_breakdown_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    RatingRow(stringResource(R.string.rating_taste_label), chef.taste)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow(stringResource(R.string.rating_hygiene_label), chef.hygiene)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow(stringResource(R.string.rating_packaging_label), chef.packaging)
                    Spacer(modifier = Modifier.height(6.dp))
                    RatingRow(stringResource(R.string.rating_on_time_label), chef.onTime)

                    if (chef.videos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(stringResource(R.string.chef_detail_videos_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        chef.videos.sortedByDescending { it.postedAt }.forEach { video ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    video.caption.ifBlank { stringResource(R.string.chef_dashboard_video_untitled) },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Text(stringResource(R.string.chef_detail_menu_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            items(meals) { meal ->
                MealCard(
                    meal = meal,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    onClick = { onMealClick(meal.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    stringResource(R.string.chef_detail_reviews_title) + if (reviews.isNotEmpty()) " (${reviews.size})" else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (reviews.isEmpty()) {
                    Text(
                        stringResource(R.string.chef_detail_no_reviews),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            items(reviews, key = { it.id }) { review ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        if (review.photoBase64.isNotBlank()) {
                            Base64Thumbnail(
                                base64 = review.photoBase64,
                                emoji = "🍽️",
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "★ " + String.format(java.util.Locale.US, "%.1f", review.overallRating),
                                color = MaterialTheme.colorScheme.tertiary,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (review.comment.isNotBlank()) {
                                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                review.createdOn,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ChefDetailShimmer() {
    Column(modifier = Modifier.fillMaxSize()) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp), cornerRadius = 0.dp)
        Column(modifier = Modifier.padding(20.dp)) {
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(24.dp))
            Spacer(modifier = Modifier.height(10.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
            Spacer(modifier = Modifier.height(20.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(modifier = Modifier.height(24.dp))
            repeat(3) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(64.dp))
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

/** Admin-checked NID + kitchen photo. */
@Composable
private fun VerifiedPill() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                stringResource(R.string.chef_verified_badge),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun FactTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = cardEdge(),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
