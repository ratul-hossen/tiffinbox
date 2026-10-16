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
    val deliveryFee = if (rank?.freeDelivery == true) 0 else CartStore.DELIVERY_FEE
    val pointsUsed = if (usePoints) {
        Rewards.maxRedeemablePoints(rewards?.balance ?: 0, (CartStore.subtotal - discount - rankDiscount).coerceAtLeast(0))
    } else 0
    val pointsTaka = Rewards.takaForPoints(pointsUsed)
    val payableTotal = (CartStore.subtotal + deliveryFee + CartStore.PLATFORM_FEE - discount - rankDiscount - pointsTaka)
        .coerceAtLeast(0)
    val etaMinutes = Rewards.estimatedDeliveryMinutes(chefDistanceKm)
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val items = CartStore.items
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val transactionIdRequiredMessage = stringResource(R.string.checkout_bkash_transaction_id_required)
    val screenshotRequiredMessage = stringResource(R.string.checkout_bkash_screenshot_required)
    val uploadFailedMessage = stringResource(R.string.checkout_bkash_upload_failed)

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
                                val orderId = FirestoreOrderRepository.placeOrder(
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
                                    estimatedDeliveryMinutes = etaMinutes
                                )
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
                stringResource(
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
            SummaryRow(stringResource(R.string.cart_summary_delivery_fee), CartStore.DELIVERY_FEE)
            if (deliveryFee == 0) {
                SummaryRow(stringResource(R.string.checkout_free_delivery_row), -CartStore.DELIVERY_FEE)
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
                        stringResource(R.string.checkout_success_eta, etaMinutes),
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
