package com.ratulhossen.tiffinbox.ui.screens.chefside

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
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.data.model.ChefOrderUi
import com.ratulhossen.tiffinbox.data.model.ChefVideoUi
import com.ratulhossen.tiffinbox.data.model.EarningsSummary
import com.ratulhossen.tiffinbox.domain.DemandForecastEngine
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.ui.components.EarningsChart
import com.ratulhossen.tiffinbox.util.ImageCompressor
import kotlinx.coroutines.launch

@Composable
fun ChefDashboardScreen(chefId: String, onLogout: () -> Unit, onManageMenu: () -> Unit, onOpenMessages: () -> Unit) {
    var available by remember { mutableStateOf(true) }
    var orders by remember { mutableStateOf<List<ChefOrderUi>>(emptyList()) }
    var earnings by remember { mutableStateOf<EarningsSummary?>(null) }
    var forecastPlates by remember { mutableStateOf<Int?>(null) }
    var kitchenName by remember { mutableStateOf<String?>(null) }
    var isApproved by remember { mutableStateOf<Boolean?>(null) }
    var deliveryMode by remember { mutableStateOf("self") }
    var kitchenPhotoBase64 by remember { mutableStateOf("") }
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
                val base64 = ImageCompressor.compressToBase64(context, uri)
                if (base64 != null) {
                    kitchenPhotoBase64 = base64
                    FirestoreChefRepository.updatePhoto(chefId, base64)
                }
                isUploadingPhoto = false
            }
        }
    }

    LaunchedEffect(chefId) {
        val chef = FirestoreCatalogRepository.getChef(chefId)
        kitchenName = chef?.kitchenName
        deliveryMode = chef?.deliveryMode ?: "self"
        kitchenPhotoBase64 = chef?.photoBase64 ?: ""
        videos = chef?.videos ?: emptyList()
        isApproved = chef?.isVerified ?: false
        if (isApproved == true) {
            val dailyCounts = FirestoreOrderRepository.getDailyOrderCountsForChef(chefId)
            forecastPlates = DemandForecastEngine.forecastNextDayPlates(dailyCounts)
            orders = FirestoreOrderRepository.getIncomingOrders(chefId)
            earnings = FirestoreOrderRepository.getEarningsSummary(chefId)
        }
    }

    fun respondToOrder(orderId: String, newStatus: String) {
        FirestoreOrderRepository.updateOrderStatus(orderId, newStatus)
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
            Text(
                stringResource(R.string.chef_dashboard_pending_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(R.string.chef_dashboard_pending_message),
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
            TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_kitchen_photo_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Base64Thumbnail(
                        base64 = kitchenPhotoBase64,
                        emoji = "🍳",
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        enabled = !isUploadingPhoto,
                        onClick = {
                            pickKitchenPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        if (isUploadingPhoto) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.chef_dashboard_kitchen_photo_button))
                        }
                    }
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
                Switch(checked = available, onCheckedChange = { available = it })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chef_dashboard_delivery_mode_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = deliveryMode == "self",
                        onClick = {
                            deliveryMode = "self"
                            FirestoreChefRepository.updateDeliveryMode(chefId, "self")
                        }
                    )
                    Text(
                        stringResource(R.string.chef_dashboard_delivery_mode_self),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = deliveryMode == "tiffinbox",
                        onClick = {
                            deliveryMode = "tiffinbox"
                            FirestoreChefRepository.updateDeliveryMode(chefId, "tiffinbox")
                        }
                    )
                    Text(
                        stringResource(R.string.chef_dashboard_delivery_mode_tiffinbox),
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                Button(
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
            EarningStat(stringResource(R.string.chef_dashboard_earning_today), stringResource(R.string.price_tag, earningsSnapshot?.todayTotal ?: 0), Modifier.weight(1f))
            EarningStat(stringResource(R.string.chef_dashboard_earning_week), stringResource(R.string.price_tag, earningsSnapshot?.weekTotal ?: 0), Modifier.weight(1f))
            EarningStat(stringResource(R.string.chef_dashboard_earning_recent_orders), "${earningsSnapshot?.recentOrderCount ?: 0}", Modifier.weight(1f))
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
                        Button(onClick = { respondToOrder(order.id, "Accepted") }) {
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

@Composable
private fun EarningStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
