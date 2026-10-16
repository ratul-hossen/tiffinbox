package com.ratulhossen.tiffinbox.ui.screens.chefside

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.CouponUi
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCouponRepository
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HOUR_MS = 60L * 60 * 1000

/**
 * A chef's own promo codes for their kitchen: create (percent or flat, min
 * order, how long it stays valid in hours or days, optional max uses) and
 * pause/resume. Coupons an admin created for this kitchen show up here too
 * (read-only); one an admin cancelled can't be turned back on by the chef.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefCouponsScreen(chefId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var coupons by remember { mutableStateOf<List<CouponUi>>(emptyList()) }
    var reloadKey by remember { mutableStateOf(0) }

    var code by remember { mutableStateOf("") }
    var isPercent by remember { mutableStateOf(true) }
    var value by remember { mutableStateOf("10") }
    var minOrder by remember { mutableStateOf("0") }
    var maxDiscount by remember { mutableStateOf("0") }
    var duration by remember { mutableStateOf("7") }
    var durationInDays by remember { mutableStateOf(true) }
    var maxUses by remember { mutableStateOf("0") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(chefId, reloadKey) {
        coupons = FirestoreCouponRepository.getChefCoupons(chefId)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.chef_coupons_title)) },
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
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.chef_coupons_new), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text(stringResource(R.string.chef_coupons_code_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isPercent, onClick = { isPercent = true }, label = { Text("%") })
                        FilterChip(selected = !isPercent, onClick = { isPercent = false }, label = { Text("৳") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value, { value = it },
                            stringResource(if (isPercent) R.string.chef_coupons_percent_label else R.string.chef_coupons_flat_label),
                            Modifier.weight(1f)
                        )
                        NumberField(minOrder, { minOrder = it }, stringResource(R.string.chef_coupons_min_order_label), Modifier.weight(1f))
                    }
                    if (isPercent) {
                        NumberField(maxDiscount, { maxDiscount = it }, stringResource(R.string.chef_coupons_max_discount_label), Modifier.fillMaxWidth())
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.chef_coupons_validity_label), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(duration, { duration = it }, stringResource(R.string.chef_coupons_duration_label), Modifier.weight(1f))
                        FilterChip(selected = !durationInDays, onClick = { durationInDays = false }, label = { Text(stringResource(R.string.chef_coupons_hours)) })
                        FilterChip(selected = durationInDays, onClick = { durationInDays = true }, label = { Text(stringResource(R.string.chef_coupons_days)) })
                    }
                    NumberField(maxUses, { maxUses = it }, stringResource(R.string.chef_coupons_max_uses_label), Modifier.fillMaxWidth())

                    if (message != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            message.orEmpty(),
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TiffinButton(
                        onClick = {
                            busy = true
                            message = null
                            val now = System.currentTimeMillis()
                            val units = duration.toLongOrNull() ?: 0L
                            val until = now + units * HOUR_MS * (if (durationInDays) 24 else 1)
                            scope.launch {
                                val err = FirestoreCouponRepository.createChefCoupon(
                                    chefId = chefId,
                                    code = code,
                                    discountType = if (isPercent) "percent" else "flat",
                                    value = value.toIntOrNull() ?: 0,
                                    minOrder = minOrder.toIntOrNull() ?: 0,
                                    maxDiscount = maxDiscount.toIntOrNull() ?: 0,
                                    validFrom = now,
                                    validUntil = until,
                                    maxUses = maxUses.toIntOrNull() ?: 0
                                )
                                busy = false
                                isError = err != null
                                message = context.getString(err ?: R.string.chef_coupons_created)
                                if (err == null) {
                                    code = ""
                                    reloadKey++
                                }
                            }
                        },
                        enabled = !busy && code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(stringResource(R.string.chef_coupons_create_button))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.chef_coupons_list_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            if (coupons.isEmpty()) {
                Text(
                    stringResource(R.string.chef_coupons_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }
            coupons.forEach { coupon ->
                CouponRow(
                    coupon = coupon,
                    validUntilText = if (coupon.validUntil > 0) dateFormat.format(Date(coupon.validUntil)) else "—",
                    onToggle = {
                        scope.launch {
                            FirestoreCouponRepository.setChefCouponActive(coupon.code, !coupon.isActive)
                            reloadKey++
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun CouponRow(coupon: CouponUi, validUntilText: String, onToggle: () -> Unit) {
    val statusRes = when {
        coupon.cancelledByAdmin && !coupon.isActive -> R.string.coupon_status_cancelled_admin
        !coupon.isActive -> R.string.coupon_status_paused
        coupon.isExpired -> R.string.coupon_status_expired
        coupon.isUsedUp -> R.string.coupon_status_used_up
        else -> R.string.coupon_status_active
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(coupon.code, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(
                    (if (coupon.discountType == "flat") "৳${coupon.value}" else "${coupon.value}%") +
                        (if (coupon.minOrder > 0) " · min ৳${coupon.minOrder}" else "") +
                        " · ${coupon.usedCount}" + (if (coupon.maxUses > 0) "/${coupon.maxUses}" else "") + " used",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.chef_coupons_valid_until, validUntilText) + " · " + stringResource(statusRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Only the chef's own coupons can be paused/resumed, and never one an admin cancelled.
            if (coupon.createdBy == "chef" && !coupon.cancelledByAdmin && !coupon.isExpired) {
                TextButton(onClick = onToggle) {
                    Text(stringResource(if (coupon.isActive) R.string.chef_coupons_pause else R.string.chef_coupons_resume))
                }
            }
        }
    }
}
