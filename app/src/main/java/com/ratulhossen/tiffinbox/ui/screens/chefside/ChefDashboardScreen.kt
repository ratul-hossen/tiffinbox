package com.ratulhossen.tiffinbox.ui.screens.chefside

import com.ratulhossen.tiffinbox.ui.components.TiffinLottie
import com.ratulhossen.tiffinbox.data.model.MealUi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Edit
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChefRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreDeliveryApplicationRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.model.ChefOrderUi
import com.ratulhossen.tiffinbox.data.model.ChefVideoUi
import com.ratulhossen.tiffinbox.data.model.EarningsSummary
import com.ratulhossen.tiffinbox.domain.DemandForecastEngine
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.ui.components.EarningsChart
import com.ratulhossen.tiffinbox.ui.components.StatTile
import com.ratulhossen.tiffinbox.util.ImageCompressor
import kotlinx.coroutines.launch
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ChefDashboardScreen(
    chefId: String,
    onLogout: () -> Unit,
    onManageMenu: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenCoupons: () -> Unit = {},
    onOpenDeliveryOption: () -> Unit = {}
) {
    var available by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf<List<ChefOrderUi>>(emptyList()) }
    var preOrders by remember { mutableStateOf<List<ChefOrderUi>>(emptyList()) }
    var earnings by remember { mutableStateOf<EarningsSummary?>(null) }
    var forecastPlates by remember { mutableStateOf<Int?>(null) }
    var kitchenName by remember { mutableStateOf<String?>(null) }
    var isApproved by remember { mutableStateOf<Boolean?>(null) }
    var isRejected by remember { mutableStateOf(false) }
    var deliveryMode by remember { mutableStateOf("self") }
    var deliveryUntil by remember { mutableStateOf(0L) }
    var kitchenPhotoBase64 by remember { mutableStateOf("") }
    // The chef's own full menu (incl. switched-off meals) - feeds the
    // "complete your kitchen" suggestions below.
    var myMeals by remember { mutableStateOf<List<MealUi>?>(null) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<ChefVideoUi>>(emptyList()) }
    var videoUrl by remember { mutableStateOf("") }
    var videoCaption by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pickKitchenPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingPhoto = true
            scope.launch {
                // 16:9 cover crop, EXIF rotation fixed - same frame customers see.
                val base64 = ImageCompressor.compressKitchenPhoto(context, uri)
                val ok = base64 != null && FirestoreChefRepository.updatePhoto(chefId, base64)
                if (ok) kitchenPhotoBase64 = base64!!
                Toast.makeText(
                    context,
                    context.getString(if (ok) R.string.menu_photo_saved else R.string.menu_photo_failed),
                    Toast.LENGTH_SHORT
                ).show()
                isUploadingPhoto = false
            }
        }
    }

    LaunchedEffect(chefId) {
        val chef = FirestoreCatalogRepository.getChef(chefId)
        kitchenName = chef?.kitchenName
        // Ends an expired delivery agreement first, so the summary below is current.
        FirestoreDeliveryApplicationRepository.syncExpiry(chefId)
        val freshChef = FirestoreCatalogRepository.getChef(chefId)
        deliveryMode = freshChef?.deliveryMode ?: "self"
        deliveryUntil = freshChef?.deliveryUntil ?: 0L
        kitchenPhotoBase64 = chef?.photoBase64 ?: ""
        available = chef?.isAvailableToday ?: true
        myMeals = FirestoreCatalogRepository.getMealsForChef(chefId, includeUnavailable = true)
        videos = chef?.videos ?: emptyList()
        isApproved = chef?.isVerified ?: false
        isRejected = chef?.approvalStatus == "rejected"
        if (isApproved == true) {
            val dailyCounts = FirestoreOrderRepository.getDailyOrderCountsForChef(chefId)
            forecastPlates = DemandForecastEngine.forecastNextDayPlates(dailyCounts)
            orders = FirestoreOrderRepository.getIncomingOrders(chefId)
            preOrders = FirestoreOrderRepository.getUpcomingPreOrders(chefId)
            earnings = FirestoreOrderRepository.getEarningsSummary(chefId)
        }
    }

    fun respondToOrder(orderId: String, newStatus: String) {
        val customerId = orders.find { it.id == orderId }?.customerId
        FirestoreOrderRepository.updateOrderStatus(orderId, newStatus, customerId)
        orders = orders.filterNot { it.id == orderId }
    }

    var viewingScreenshotBase64 by remember { mutableStateOf<String?>(null) }

    if (isApproved == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (isApproved == false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isRejected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .shadow(10.dp, MaterialTheme.shapes.large)
                        .clip(MaterialTheme.shapes.large)
                ) {
                    FullBleedPhoto(resId = R.drawable.chef_pending, modifier = Modifier.fillMaxSize(), animated = false)
                }
            } else {
                // Waiting for approval: the hourglass keeps turning - something
                // really is in progress here.
                TiffinLottie(res = R.raw.lottie_hourglass, modifier = Modifier.size(180.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(if (isRejected) R.string.chef_dashboard_rejected_title else R.string.chef_dashboard_pending_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(if (isRejected) R.string.chef_dashboard_rejected_message else R.string.chef_dashboard_pending_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.chef_dashboard_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    kitchenName ?: stringResource(R.string.chef_dashboard_kitchen_name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenCoupons) {
                    Icon(Icons.Filled.LocalOffer, contentDescription = stringResource(R.string.chef_coupons_title))
                }
                IconButton(onClick = onEditProfile) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_profile_title))
                }
                IconButton(onClick = onOpenNotifications) {
                    Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.notifications_title))
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(R.string.action_logout),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        // Photo banner (res/drawable-nodpi/chef_dashboard_banner.jpg) - the
        // photo's left side is dark by design, so the kitchen name sits there.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .shadow(10.dp, MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
        ) {
            if (kitchenPhotoBase64.isNotBlank()) {
                // The chef's own kitchen photo replaces the stock banner.
                Base64Thumbnail(
                    base64 = kitchenPhotoBase64,
                    emoji = "🍳",
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.large
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                )
            } else {
                FullBleedPhoto(
                    resId = R.drawable.chef_dashboard_banner,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.CenterEnd,
                    scrim = Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
            }
            Text(
                kitchenName ?: stringResource(R.string.chef_dashboard_kitchen_name),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(0.6f)
            )
        }
        val pickKitchenPhoto = {
            pickKitchenPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        myMeals?.let { meals ->
            Spacer(modifier = Modifier.height(16.dp))
            KitchenSetupCard(
                hasKitchenPhoto = kitchenPhotoBase64.isNotBlank(),
                mealCount = meals.size,
                mealsWithPhoto = meals.count { it.photoBase64.isNotBlank() },
                hasVideo = videos.isNotEmpty(),
                hasTodaySpecial = meals.any { it.isTodaySpecial },
                onAddKitchenPhoto = pickKitchenPhoto,
                onOpenMenu = onManageMenu
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_kitchen_photo_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.kitchen_photo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                // Same 16:9 frame as the customer-facing kitchen cover.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ImageCompressor.KITCHEN_ASPECT)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(enabled = !isUploadingPhoto) { pickKitchenPhoto() }
                ) {
                    Base64Thumbnail(
                        base64 = kitchenPhotoBase64,
                        emoji = "🍳",
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.medium,
                        emojiFontSize = 44.sp
                    )
                    if (isUploadingPhoto) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(color = Color.White) }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    enabled = !isUploadingPhoto,
                    onClick = pickKitchenPhoto,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (kitchenPhotoBase64.isBlank()) R.string.chef_dashboard_kitchen_photo_button
                            else R.string.kitchen_photo_change
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.chef_dashboard_available_toggle), style = MaterialTheme.typography.titleMedium)
                Switch(checked = available, onCheckedChange = { on ->
                    available = on
                    scope.launch {
                        if (!FirestoreChefRepository.updateAvailableToday(chefId, on)) {
                            available = !on
                            Toast.makeText(context, context.getString(R.string.menu_save_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.chef_dashboard_delivery_mode_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (deliveryMode == "tiffinbox") {
                            stringResource(R.string.delivery_option_service_title) + " · " +
                                stringResource(R.string.delivery_option_until, formatDeliveryDate(deliveryUntil))
                        } else {
                            stringResource(R.string.delivery_option_self_title)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onOpenDeliveryOption) {
                    Text(stringResource(R.string.delivery_option_change))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onManageMenu, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.chef_dashboard_manage_menu_button))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenMessages, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.messages_label))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_videos_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text(stringResource(R.string.chef_dashboard_video_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = videoCaption,
                    onValueChange = { videoCaption = it },
                    label = { Text(stringResource(R.string.chef_dashboard_video_caption_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                TiffinButton(
                    onClick = {
                        if (videoUrl.isNotBlank()) {
                            FirestoreChefRepository.addVideo(chefId, videoUrl, videoCaption)
                            videos = videos + ChefVideoUi(videoUrl, videoCaption, System.currentTimeMillis())
                            videoUrl = ""
                            videoCaption = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.chef_dashboard_post_video_button))
                }

                if (videos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    videos.sortedByDescending { it.postedAt }.forEach { video ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    video.caption.ifBlank { stringResource(R.string.chef_dashboard_video_untitled) },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    video.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val earningsSnapshot = earnings
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(stringResource(R.string.chef_dashboard_earning_today), stringResource(R.string.price_tag, earningsSnapshot?.todayTotal ?: 0), Modifier.weight(1f))
            StatTile(stringResource(R.string.chef_dashboard_earning_week), stringResource(R.string.price_tag, earningsSnapshot?.weekTotal ?: 0), Modifier.weight(1f))
            StatTile(stringResource(R.string.chef_dashboard_earning_recent_orders), "${earningsSnapshot?.recentOrderCount ?: 0}", Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_chart_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                val dailyTotals = earningsSnapshot?.dailyTotals.orEmpty()
                if (dailyTotals.size >= 2) {
                    EarningsChart(dailyTotals = dailyTotals)
                } else {
                    Text(
                        stringResource(R.string.chef_dashboard_chart_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_forecast_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                val plates = forecastPlates
                if (plates != null) {
                    Text(
                        stringResource(R.string.chef_dashboard_forecast_result, plates),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        stringResource(R.string.chef_dashboard_forecast_empty),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (preOrders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            PreOrderDemandCard(preOrders)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.chef_dashboard_new_orders_title), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(10.dp))

        if (orders.isEmpty()) {
            Text(
                stringResource(R.string.chef_dashboard_no_new_orders),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        orders.forEach { order ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (order.scheduledFor > 0) {
                        Text(
                            stringResource(R.string.chef_preorder_badge, formatPreOrderSlot(order.scheduledFor)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(order.itemsSummary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.chef_dashboard_order_number, order.id.take(6)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.price_tag, order.totalPrice),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (order.paymentTransactionId.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.chef_dashboard_payment_transaction_label, order.paymentMethod, order.paymentTransactionId),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (order.paymentScreenshotBase64.isNotBlank()) {
                            TextButton(
                                onClick = { viewingScreenshotBase64 = order.paymentScreenshotBase64 },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.chef_dashboard_view_screenshot_button), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TiffinButton(onClick = { respondToOrder(order.id, "Accepted") }) {
                            Text(stringResource(R.string.chef_dashboard_accept_button))
                        }
                        OutlinedButton(onClick = { respondToOrder(order.id, "Cancelled") }) {
                            Text(stringResource(R.string.chef_dashboard_reject_button))
                        }
                    }
                }
            }
        }
    }

    val screenshotBase64 = viewingScreenshotBase64
    if (screenshotBase64 != null) {
        val bitmap = remember(screenshotBase64) {
            runCatching {
                val bytes = Base64.decode(screenshotBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        Dialog(onDismissRequest = { viewingScreenshotBase64 = null }) {
            Surface(shape = MaterialTheme.shapes.large) {
                Box(modifier = Modifier.padding(8.dp)) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(R.string.chef_dashboard_view_screenshot_button),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            stringResource(R.string.chef_dashboard_screenshot_load_failed),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Complete your kitchen" checklist. Photos and videos are optional, so
 * this only suggests - but kitchens with photos look far more trustworthy
 * to customers. Hidden once everything is done.
 */
@Composable
private fun KitchenSetupCard(
    hasKitchenPhoto: Boolean,
    mealCount: Int,
    mealsWithPhoto: Int,
    hasVideo: Boolean,
    hasTodaySpecial: Boolean,
    onAddKitchenPhoto: () -> Unit,
    onOpenMenu: () -> Unit
) {
    val steps = listOf(
        Triple(hasKitchenPhoto, stringResource(R.string.setup_step_kitchen_photo), onAddKitchenPhoto),
        Triple(mealCount > 0, stringResource(R.string.setup_step_menu), onOpenMenu),
        Triple(
            mealCount > 0 && mealsWithPhoto == mealCount,
            stringResource(R.string.setup_step_meal_photos, mealsWithPhoto, mealCount),
            onOpenMenu
        ),
        Triple(hasTodaySpecial, stringResource(R.string.setup_step_special), onOpenMenu),
        Triple(hasVideo, stringResource(R.string.setup_step_video), {})
    )
    val done = steps.count { it.first }
    if (done == steps.size) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { done / steps.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small)
            )
            Spacer(modifier = Modifier.height(6.dp))
            steps.forEach { (isDone, label, action) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable(enabled = !isDone, onClick = action)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isDone) "✅" else "⬜", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isDone) {
                        Text("→", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatPreOrderSlot(millis: Long): String =
    java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))

/**
 * Upcoming pre-orders grouped by slot with total plates per dish, so the chef
 * knows exactly how much to cook (and buys only that) - less waste.
 */
@Composable
private fun PreOrderDemandCard(preOrders: List<ChefOrderUi>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.chef_preorders_title, preOrders.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                stringResource(R.string.chef_preorders_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            preOrders.groupBy { it.scheduledFor }.entries.take(6).forEach { (slot, slotOrders) ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    formatPreOrderSlot(slot),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                val plates = slotOrders.flatMap { it.itemQuantities.entries }
                    .groupBy({ it.key }, { it.value })
                    .mapValues { (_, q) -> q.sum() }
                plates.forEach { (dish, count) ->
                    Text(
                        stringResource(R.string.chef_preorders_dish_line, count, dish),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
