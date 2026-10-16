package com.ratulhossen.tiffinbox.ui.screens.checkout

import kotlinx.coroutines.delay
import com.ratulhossen.tiffinbox.ui.components.TiffinLottie
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import com.ratulhossen.tiffinbox.util.haversineKm
import com.ratulhossen.tiffinbox.domain.RewardsStatus
import com.ratulhossen.tiffinbox.domain.Rewards
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCouponRepository
import com.ratulhossen.tiffinbox.data.firebase.CouponCheck
import com.ratulhossen.tiffinbox.data.firebase.CouponUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.firebase.SavedAddress
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import androidx.compose.foundation.horizontalScroll
import com.ratulhossen.tiffinbox.data.DeliveryQuoter
import com.ratulhossen.tiffinbox.data.CartStore
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.util.ImageCompressor
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults

private enum class PaymentMethod(val available: Boolean) {
    CASH_ON_DELIVERY(available = true),
    BKASH(available = true),
    // Same free manual-proof pattern as bKash - no payment gateway needed.
    NAGAD(available = true),
    // Real card processing needs a paid merchant gateway (SSLCommerz/Stripe/etc.) - out of scope for the free tier.
    CARD(available = false)
}

private val PaymentMethod.isMobileBanking: Boolean
    get() = this == PaymentMethod.BKASH || this == PaymentMethod.NAGAD

@Composable
private fun PaymentMethod.label(): String = when (this) {
    PaymentMethod.CASH_ON_DELIVERY -> stringResource(R.string.payment_method_cod)
    PaymentMethod.BKASH -> stringResource(R.string.payment_method_bkash)
    PaymentMethod.NAGAD -> stringResource(R.string.payment_method_nagad)
    PaymentMethod.CARD -> stringResource(R.string.payment_method_card)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onOrderPlaced: (orderId: String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var couponInput by remember { mutableStateOf("") }
    var appliedCoupon by remember { mutableStateOf<CouponUi?>(null) }
    var couponMessage by remember { mutableStateOf<String?>(null) }
    var couponChecking by remember { mutableStateOf(false) }
    // Re-computed from the live subtotal so changing the cart can't keep a stale discount.
    val discount = appliedCoupon?.takeIf { CartStore.subtotal >= it.minOrder }?.discountFor(CartStore.subtotal) ?: 0
    // TiffinBox Points: rank discount (+ free delivery at the top rank) and
    // optional points redemption - see domain/Rewards.kt.
    var rewards by remember { mutableStateOf<RewardsStatus?>(null) }
    var usePoints by remember { mutableStateOf(false) }
    var chefDistanceKm by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) { rewards = FirestoreOrderRepository.getMyRewards() }
    val rank = rewards?.rank
    val rankDiscount = rank?.let { Rewards.rankDiscount(it, CartStore.subtotal) } ?: 0
    // Distance-based fee for this kitchen (DeliveryQuoter / admin pricing); free at the top rank.
    val deliveryFee = if (rank?.freeDelivery == true) 0 else CartStore.deliveryFee
    val pointsUsed = if (usePoints) {
        Rewards.maxRedeemablePoints(rewards?.balance ?: 0, (CartStore.subtotal - discount - rankDiscount).coerceAtLeast(0))
    } else 0
    val pointsTaka = Rewards.takaForPoints(pointsUsed)
    val payableTotal = (CartStore.subtotal + deliveryFee + CartStore.PLATFORM_FEE - discount - rankDiscount - pointsTaka)
        .coerceAtLeast(0)
    val etaMinutes = Rewards.estimatedDeliveryMinutes(chefDistanceKm)
    // null = as soon as possible; otherwise the chosen pre-order slot (epoch millis).
    var preOrderSlot by remember { mutableStateOf<Long?>(null) }
    // Starts in pre-order mode when something in the cart has no plates left for today.
    var preOrderMode by remember { mutableStateOf(CartStore.items.any { it.meal.availableQuantity < it.quantity }) }
    var usableCoupons by remember { mutableStateOf<List<CouponUi>>(emptyList()) }
    var savedAddresses by remember { mutableStateOf<List<SavedAddress>>(emptyList()) }
    LaunchedEffect(Unit) {
        val uid = FirebaseAuthRepository.currentUser?.uid ?: return@LaunchedEffect
        val (list, defaultIndex) = FirestoreUserRepository.getSavedAddresses(uid)
        savedAddresses = list
        if (address.isBlank()) address = list.getOrNull(defaultIndex)?.address.orEmpty()
    }
    var selectedPayment by remember { mutableStateOf(PaymentMethod.CASH_ON_DELIVERY) }
    var placing by remember { mutableStateOf(false) }
    var placedOrderId by remember { mutableStateOf<String?>(null) }
    var transactionId by remember { mutableStateOf("") }
    var paymentScreenshotUri by remember { mutableStateOf<Uri?>(null) }
    var chefBkashNumber by remember { mutableStateOf("") }
    var chefNagadNumber by remember { mutableStateOf("") }
    // The part of the delivery fee that came from an admin area surcharge, so
    // it gets its own summary line instead of silently inflating the total.
    var deliverySurcharge by remember { mutableIntStateOf(0) }
    var deliverySurchargeArea by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val items = CartStore.items
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val transactionIdRequiredMessage = stringResource(R.string.checkout_bkash_transaction_id_required)
    val screenshotRequiredMessage = stringResource(R.string.checkout_bkash_screenshot_required)
    val uploadFailedMessage = stringResource(R.string.checkout_bkash_upload_failed)
    val pickSlotMessage = stringResource(R.string.checkout_preorder_pick_slot)

    val pickScreenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) paymentScreenshotUri = uri }

    LaunchedEffect(items.firstOrNull()?.meal?.chefId) {
        val chefId = items.firstOrNull()?.meal?.chefId
        if (chefId != null) {
            val chef = FirestoreCatalogRepository.getChef(chefId)
            chefBkashNumber = chef?.bkashNumber.orEmpty()
            chefNagadNumber = chef?.nagadNumber.orEmpty()
            // Kitchen -> customer distance for the delivery-time estimate.
            val me = FirebaseAuthRepository.currentUser?.uid?.let { FirestoreUserRepository.getLocation(it) }
            if (me != null && chef != null && chef.latitude != 0.0) {
                chefDistanceKm = haversineKm(me.latitude, me.longitude, chef.latitude, chef.longitude)
            }
            // Through DeliveryQuoter, not DeliveryQuotes directly, so the
            // kitchen's own rates, the admin's per-chef cap and any area
            // surcharge all apply - see DeliveryQuoter's docs.
            chef?.let { c ->
                val quote = DeliveryQuoter.quote(c)
                CartStore.deliveryFee = quote.fee
                deliverySurcharge = quote.surcharge
                deliverySurchargeArea = quote.surchargeArea
            }
            usableCoupons = FirestoreCouponRepository.getUsableCoupons(chefId)
        }
    }

    Scaffold(
        // Transparent so the app-wide AnimatedGradientBackground shows through.
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.checkout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (errorMessage != null) {
                        Text(
                            errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    TiffinButton(
                        onClick = {
                            errorMessage = null
                            if (preOrderMode && preOrderSlot == null) {
                                errorMessage = pickSlotMessage
                                return@TiffinButton
                            }
                            if (selectedPayment.isMobileBanking) {
                                if (transactionId.isBlank()) {
                                    errorMessage = transactionIdRequiredMessage
                                    return@TiffinButton
                                }
                                if (paymentScreenshotUri == null) {
                                    errorMessage = screenshotRequiredMessage
                                    return@TiffinButton
                                }
                            }
                            placing = true
                            scope.launch {
                                var screenshotBase64: String? = null
                                if (selectedPayment.isMobileBanking) {
                                    screenshotBase64 = paymentScreenshotUri?.let {
                                        ImageCompressor.compressToBase64(context, it)
                                    }
                                    if (screenshotBase64 == null) {
                                        placing = false
                                        errorMessage = uploadFailedMessage
                                        return@launch
                                    }
                                }
                                val result = FirestoreOrderRepository.placeOrder(
                                    items = items.toList(),
                                    subtotal = CartStore.subtotal,
                                    deliveryFee = deliveryFee,
                                    platformFee = CartStore.PLATFORM_FEE,
                                    total = payableTotal,
                                    deliveryAddress = address,
                                    paymentMethod = selectedPayment.name,
                                    paymentTransactionId = if (selectedPayment.isMobileBanking) transactionId.trim() else null,
                                    paymentScreenshotBase64 = screenshotBase64,
                                    couponCode = if (discount > 0) appliedCoupon?.code.orEmpty() else "",
                                    discount = discount,
                                    pointsRedeemed = pointsUsed,
                                    rankDiscount = rankDiscount,
                                    estimatedDeliveryMinutes = etaMinutes,
                                    scheduledFor = if (preOrderMode) preOrderSlot ?: 0L else 0L
                                )
                                val orderId = result.orderId
                                if (orderId == null) {
                                    errorMessage = when {
                                        result.soldOutMeal != null -> context.getString(
                                            R.string.checkout_sold_out_error,
                                            result.soldOutMeal.ifBlank { context.getString(R.string.checkout_sold_out_generic_meal) }
                                        )
                                        else -> context.getString(R.string.checkout_place_failed)
                                    }
                                }
                                if (orderId != null && discount > 0) {
                                    appliedCoupon?.let { FirestoreCouponRepository.recordUse(it.code) }
                                }
                                placing = false
                                // Celebrate first; the success dialog navigates on when its animation ends.
                                if (orderId != null) placedOrderId = orderId
                            }
                        },
                        enabled = !placing && address.isNotBlank() && items.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.checkout_place_order_button, payableTotal))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(stringResource(R.string.checkout_delivery_address_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (savedAddresses.isNotEmpty()) {
                Text(
                    stringResource(R.string.checkout_saved_addresses_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedAddresses.forEach { saved ->
                        FilterChip(
                            selected = address == saved.address,
                            onClick = { address = saved.address },
                            label = { Text(saved.label.ifBlank { saved.address.take(18) }) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = { Text(stringResource(R.string.checkout_address_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            // When: now, or a pre-order slot so the chef cooks only what's ordered.
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.checkout_when_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !preOrderMode,
                    onClick = { preOrderMode = false },
                    label = { Text(stringResource(R.string.checkout_when_now)) }
                )
                FilterChip(
                    selected = preOrderMode,
                    onClick = { preOrderMode = true },
                    label = { Text(stringResource(R.string.checkout_when_preorder)) }
                )
            }
            if (preOrderMode) {
                Spacer(modifier = Modifier.height(8.dp))
                PreOrderSlotPicker(selected = preOrderSlot, onSelect = { preOrderSlot = it })
                Text(
                    stringResource(R.string.checkout_preorder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.checkout_payment_method_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            PaymentMethod.entries.forEach { method ->
                Surface(
                    onClick = { if (method.available) selectedPayment = method },
                    shape = MaterialTheme.shapes.medium,
                    color = if (selectedPayment == method) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedPayment == method,
                            onClick = { if (method.available) selectedPayment = method },
                            enabled = method.available
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            method.label(),
                            modifier = Modifier.weight(1f),
                            color = if (method.available) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (!method.available) {
                            AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.checkout_coming_soon_chip)) })
                        }
                    }
                }
            }

            if (selectedPayment.isMobileBanking) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val mobileBankingNumber = if (selectedPayment == PaymentMethod.BKASH) chefBkashNumber else chefNagadNumber
                        if (mobileBankingNumber.isNotBlank()) {
                            Text(
                                stringResource(R.string.checkout_mobile_banking_number_notice, selectedPayment.label(), mobileBankingNumber),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        OutlinedTextField(
                            value = transactionId,
                            onValueChange = { transactionId = it },
                            label = { Text(stringResource(R.string.checkout_bkash_transaction_id_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                pickScreenshotLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (paymentScreenshotUri != null) Icons.Filled.CheckCircle else Icons.Filled.Upload,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (paymentScreenshotUri != null) {
                                    stringResource(R.string.checkout_bkash_screenshot_selected)
                                } else {
                                    stringResource(R.string.checkout_bkash_select_screenshot_button)
                                }
                            )
                        }
                    }
                }
            }

            // Promo code: the chef's own, an admin coupon for this chef, or a platform-wide one.
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.coupon_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = couponInput,
                    onValueChange = {
                        couponInput = it
                        if (appliedCoupon != null) {
                            appliedCoupon = null
                            couponMessage = null
                        }
                    },
                    placeholder = { Text(stringResource(R.string.coupon_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    enabled = !couponChecking && couponInput.isNotBlank(),
                    onClick = {
                        couponChecking = true
                        couponMessage = null
                        val chefId = items.firstOrNull()?.meal?.chefId.orEmpty()
                        scope.launch {
                            when (val check = FirestoreCouponRepository.validate(couponInput, chefId, CartStore.subtotal)) {
                                is CouponCheck.Valid -> {
                                    appliedCoupon = check.coupon
                                    couponMessage = context.getString(R.string.coupon_applied, check.discount)
                                }
                                is CouponCheck.Invalid -> {
                                    appliedCoupon = null
                                    couponMessage = context.getString(check.messageRes)
                                }
                            }
                            couponChecking = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.coupon_apply))
                }
            }
            if (usableCoupons.isNotEmpty() && appliedCoupon == null) {
                // Tap an available offer instead of having to know its code.
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.coupon_available_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    usableCoupons.forEach { coupon ->
                        AssistChip(
                            onClick = { couponInput = coupon.code; couponMessage = null },
                            label = {
                                Text(
                                    if (coupon.discountType == "flat") stringResource(R.string.coupon_chip_flat, coupon.code, coupon.value)
                                    else stringResource(R.string.coupon_chip_percent, coupon.code, coupon.value)
                                )
                            }
                        )
                    }
                }
            }
            if (couponMessage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    couponMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (appliedCoupon != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            rewards?.let { status ->
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            stringResource(R.string.checkout_rank_line, status.rank.emoji, status.rank.title, status.balance),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val redeemable = Rewards.maxRedeemablePoints(
                            status.balance,
                            (CartStore.subtotal - discount - rankDiscount).coerceAtLeast(0)
                        )
                        val canRedeem = redeemable > 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (canRedeem) stringResource(R.string.checkout_use_points, redeemable, Rewards.takaForPoints(redeemable))
                                else stringResource(R.string.checkout_points_not_enough, Rewards.POINTS_PER_TAKA_OFF),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(checked = usePoints && canRedeem, enabled = canRedeem, onCheckedChange = { usePoints = it })
                        }
                        Text(
                            stringResource(R.string.checkout_points_will_earn, Rewards.pointsFor(payableTotal)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                if (preOrderMode && preOrderSlot != null) stringResource(R.string.checkout_preorder_for, formatSlot(preOrderSlot!!))
                else stringResource(
                    R.string.checkout_eta,
                    etaMinutes,
                    java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(System.currentTimeMillis() + etaMinutes * 60_000L))
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.checkout_order_summary_title), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SummaryRow(stringResource(R.string.cart_summary_subtotal), CartStore.subtotal)
            // The surcharge is already inside CartStore.deliveryFee; show the
            // kitchen's own fee here and break the extra out below, so the
            // customer can see why a festival-day bill is higher.
            SummaryRow(stringResource(R.string.cart_summary_delivery_fee), CartStore.deliveryFee - deliverySurcharge)
            if (deliverySurcharge > 0) {
                SummaryRow(
                    if (deliverySurchargeArea.isNotBlank()) {
                        stringResource(R.string.checkout_delivery_surcharge_row, deliverySurchargeArea)
                    } else {
                        stringResource(R.string.checkout_delivery_surcharge_generic)
                    },
                    deliverySurcharge
                )
            }
            if (deliveryFee == 0) {
                SummaryRow(stringResource(R.string.checkout_free_delivery_row), -CartStore.deliveryFee)
            }
            SummaryRow(stringResource(R.string.cart_summary_platform_fee), CartStore.PLATFORM_FEE)
            if (discount > 0) {
                SummaryRow(stringResource(R.string.coupon_discount_row, appliedCoupon?.code.orEmpty()), -discount)
            }
            if (rankDiscount > 0 && rank != null) {
                SummaryRow(stringResource(R.string.checkout_rank_discount_row, rank.emoji, rank.discountPercent), -rankDiscount)
            }
            if (pointsTaka > 0) {
                SummaryRow(stringResource(R.string.checkout_points_row, pointsUsed), -pointsTaka)
            }
            Divider(modifier = Modifier.padding(vertical = 6.dp))
            SummaryRow(stringResource(R.string.cart_summary_total), payableTotal, bold = true)
        }
    }

    placedOrderId?.let { orderId ->
        var navigated by remember { mutableStateOf(false) }
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TiffinLottie(
                        res = R.raw.lottie_success_check,
                        loop = false,
                        modifier = Modifier.size(150.dp),
                        onFinished = {
                            if (!navigated) {
                                navigated = true
                                scope.launch {
                                    delay(450)
                                    CartStore.clear()
                                    onOrderPlaced(orderId)
                                }
                            }
                        }
                    )
                    Text(
                        stringResource(R.string.checkout_success_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (preOrderMode && preOrderSlot != null) stringResource(R.string.checkout_preorder_for, formatSlot(preOrderSlot!!))
                        else stringResource(R.string.checkout_success_eta, etaMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, amount: Int, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        // Negative amounts are discounts: show "-৳50" in the accent color, not "৳-50".
        Text(
            if (amount < 0) "-" + stringResource(R.string.price_tag, -amount) else stringResource(R.string.price_tag, amount),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (amount < 0) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    }
}

/** "Tomorrow, 12:30 PM"-style label for a pre-order slot. */
private fun formatSlot(millis: Long): String =
    java.text.SimpleDateFormat("EEE d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))

/** Lunch / dinner slots chefs usually cook for. */
private val PRE_ORDER_SLOTS = listOf(12 to 30, 13 to 30, 19 to 30, 20 to 30)

/** A pre-order must leave the chef at least this long to shop and cook. */
private const val PRE_ORDER_MIN_LEAD_MS = 2L * 60 * 60 * 1000

/** Day chips (today + next 2 days) and lunch/dinner time chips; past or too-soon slots are disabled. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreOrderSlotPicker(selected: Long?, onSelect: (Long) -> Unit) {
    val dayLabels = listOf(
        stringResource(R.string.checkout_day_today),
        stringResource(R.string.checkout_day_tomorrow),
        stringResource(R.string.checkout_day_after)
    )
    var dayOffset by remember { mutableStateOf(1) }
    fun slotMillis(day: Int, hour: Int, minute: Int): Long =
        java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, day)
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    val earliest = System.currentTimeMillis() + PRE_ORDER_MIN_LEAD_MS
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dayLabels.forEachIndexed { index, label ->
            FilterChip(selected = dayOffset == index, onClick = { dayOffset = index }, label = { Text(label) })
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        PRE_ORDER_SLOTS.forEach { (h, m) ->
            val millis = slotMillis(dayOffset, h, m)
            FilterChip(
                selected = selected == millis,
                enabled = millis >= earliest,
                onClick = { onSelect(millis) },
                label = {
                    Text(java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis)))
                }
            )
        }
    }
}
