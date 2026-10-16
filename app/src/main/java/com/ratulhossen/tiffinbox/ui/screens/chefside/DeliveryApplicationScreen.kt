package com.ratulhossen.tiffinbox.ui.screens.chefside

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.DeliveryApplicationUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreDeliveryApplicationRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.bottomScrim
import kotlinx.coroutines.launch

/**
 * Chef-side "apply for TiffinBox Delivery" form. The delivery service itself
 * isn't built yet - this only records the chef's interest and details in
 * delivery_applications/{chefUid} so the admin can review it (admin-web's
 * Delivery Applications page) and contact the chef when it launches.
 * Pre-filled from the chef's own profile; editable / withdrawable while
 * still pending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryApplicationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuthRepository.currentUser?.uid.orEmpty()

    var loading by remember { mutableStateOf(true) }
    var existing by remember { mutableStateOf<DeliveryApplicationUi?>(null) }
    var kitchenName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf(0.0) }
    var longitude by remember { mutableStateOf(0.0) }

    var contactNumber by remember { mutableStateOf("") }
    var pickupAddress by remember { mutableStateOf("") }
    var dailyOrders by remember { mutableStateOf("10") }
    var radiusKm by remember { mutableStateOf("3") }
    var slots by remember { mutableStateOf(setOf("Lunch")) }
    var note by remember { mutableStateOf("") }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        val profile = FirestoreUserRepository.getProfile(uid)
        val chef = FirestoreCatalogRepository.getChef(uid)
        kitchenName = chef?.kitchenName.orEmpty()
        ownerName = chef?.ownerName ?: profile?.name.orEmpty()
        latitude = chef?.latitude ?: 0.0
        longitude = chef?.longitude ?: 0.0
        contactNumber = profile?.whatsappNumber?.ifBlank { null } ?: profile?.phoneNumber.orEmpty()
        pickupAddress = profile?.address.orEmpty()
        FirestoreDeliveryApplicationRepository.syncExpiry(uid)?.let { app ->
            existing = app
            contactNumber = app.contactNumber
            pickupAddress = app.pickupAddress
            dailyOrders = app.expectedDailyOrders.toString()
            radiusKm = app.deliveryRadiusKm.toString()
            slots = app.mealSlots.toSet()
            note = app.note
        }
        loading = false
    }

    // Editable unless an agreement is currently running; after a rejection /
    // cancellation / expiry the chef can re-apply with the same form.
    val canEdit = existing?.isActive != true

    fun submit() {
        message = null
        if (contactNumber.isBlank() || pickupAddress.isBlank()) {
            isError = true
            message = context.getString(R.string.delivery_app_required)
            return
        }
        if (slots.isEmpty()) {
            isError = true
            message = context.getString(R.string.delivery_app_slots_required)
            return
        }
        busy = true
        scope.launch {
            val ok = FirestoreDeliveryApplicationRepository.submit(
                chefId = uid,
                kitchenName = kitchenName,
                ownerName = ownerName,
                contactNumber = contactNumber.trim(),
                pickupAddress = pickupAddress.trim(),
                latitude = latitude,
                longitude = longitude,
                expectedDailyOrders = dailyOrders.toIntOrNull() ?: 0,
                deliveryRadiusKm = radiusKm.toIntOrNull() ?: 0,
                mealSlots = FirestoreDeliveryApplicationRepository.MEAL_SLOTS.filter { it in slots },
                note = note.trim(),
                createdAt = existing?.createdAt
            )
            busy = false
            isError = !ok
            message = context.getString(if (ok) R.string.delivery_app_submitted else R.string.delivery_app_failed)
            if (ok) existing = FirestoreDeliveryApplicationRepository.getMyApplication(uid)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.delivery_app_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                FullBleedPhoto(
                    resId = R.drawable.delivery_rider,
                    modifier = Modifier.fillMaxSize(),
                    scrim = bottomScrim(),
                    animated = false
                )
                Text(
                    stringResource(R.string.delivery_app_banner),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                stringResource(R.string.delivery_app_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (loading) {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            existing?.let { app ->
                Spacer(modifier = Modifier.height(16.dp))
                val (statusRes, tone) = when (app.status) {
                    "approved" -> R.string.delivery_app_status_approved to MaterialTheme.colorScheme.primaryContainer
                    "rejected" -> R.string.delivery_app_status_rejected to MaterialTheme.colorScheme.errorContainer
                    "cancelled" -> R.string.delivery_app_status_cancelled to MaterialTheme.colorScheme.errorContainer
                    "expired" -> R.string.delivery_app_status_expired to MaterialTheme.colorScheme.surfaceVariant
                    else -> R.string.delivery_app_status_pending to MaterialTheme.colorScheme.secondaryContainer
                }
                Surface(shape = MaterialTheme.shapes.large, color = tone, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            stringResource(R.string.delivery_app_status_label, stringResource(statusRes)),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (app.isActive && app.validUntil > 0) {
                            Text(
                                stringResource(R.string.delivery_option_until, formatDeliveryDate(app.validUntil)),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (app.adminNote.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.delivery_app_admin_note, app.adminNote),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (kitchenName.isNotBlank()) {
                        Text(kitchenName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = contactNumber,
                        onValueChange = { contactNumber = it },
                        label = { Text(stringResource(R.string.delivery_app_contact_label)) },
                        singleLine = true,
                        enabled = canEdit,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pickupAddress,
                        onValueChange = { pickupAddress = it },
                        label = { Text(stringResource(R.string.delivery_app_pickup_label)) },
                        enabled = canEdit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = dailyOrders,
                            onValueChange = { dailyOrders = it.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(R.string.delivery_app_daily_orders_label)) },
                            singleLine = true,
                            enabled = canEdit,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = radiusKm,
                            onValueChange = { radiusKm = it.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.delivery_app_radius_label)) },
                            singleLine = true,
                            enabled = canEdit,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.delivery_app_slots_label), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FirestoreDeliveryApplicationRepository.MEAL_SLOTS.forEach { slot ->
                            val labelRes = when (slot) {
                                "Breakfast" -> R.string.delivery_app_slot_breakfast
                                "Dinner" -> R.string.delivery_app_slot_dinner
                                else -> R.string.delivery_app_slot_lunch
                            }
                            FilterChip(
                                selected = slot in slots,
                                enabled = canEdit,
                                onClick = { slots = if (slot in slots) slots - slot else slots + slot },
                                label = { Text(stringResource(labelRes)) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.delivery_app_note_label)) },
                        enabled = canEdit,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (message != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    message.orEmpty(),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (canEdit) {
                Spacer(modifier = Modifier.height(18.dp))
                TiffinButton(
                    onClick = { submit() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(
                                when (existing?.status) {
                                    null -> R.string.delivery_app_submit
                                    "pending" -> R.string.delivery_app_update
                                    else -> R.string.delivery_app_reapply
                                }
                            )
                        )
                    }
                }
                if (existing?.status == "pending") {
                    TextButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                if (FirestoreDeliveryApplicationRepository.withdraw(uid)) {
                                    existing = null
                                    isError = false
                                    message = context.getString(R.string.delivery_app_withdrawn)
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.delivery_app_withdraw), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** e.g. "16 Nov 2026" - shared by the delivery screens. */
internal fun formatDeliveryDate(millis: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))
