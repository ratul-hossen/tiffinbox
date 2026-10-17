package com.ratulhossen.tiffinbox.ui.screens.chefside

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.TwoWheeler
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.DeliveryApplicationUi
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreAppConfigRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChefRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreDeliveryApplicationRepository
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.domain.DeliveryPricing
import com.ratulhossen.tiffinbox.domain.DeliveryQuotes
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import kotlinx.coroutines.launch

/**
 * Chef's kitchen setting "Delivery Option" (matches the reference design):
 * Self Delivery is the default; "TiffinBox Delivery Service" needs an
 * application that admin-web approves for a fixed agreement period. While
 * that agreement runs the chef can switch between the two; when it ends
 * (or the admin cancels it) the kitchen falls back to Self Delivery and
 * the customer-facing badge disappears automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryOptionScreen(onBack: () -> Unit, onOpenApplication: () -> Unit) {
    val uid = FirebaseAuthRepository.currentUser?.uid.orEmpty()
    var deliveryMode by remember { mutableStateOf("self") }
    var application by remember { mutableStateOf<DeliveryApplicationUi?>(null) }
    var chef by remember { mutableStateOf<ChefUi?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Runs again whenever the chef comes back here (e.g. after submitting the
    // form) - Navigation recreates the destination's composition.
    LaunchedEffect(uid) {
        application = FirestoreDeliveryApplicationRepository.syncExpiry(uid)
        val loaded = FirestoreCatalogRepository.getChef(uid)
        chef = loaded
        deliveryMode = loaded?.deliveryMode ?: "self"
        loading = false
    }

    val agreementActive = application?.isActive == true

    fun select(mode: String) {
        deliveryMode = mode
        FirestoreChefRepository.updateDeliveryMode(uid, mode)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.delivery_option_title)) },
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
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                return@Column
            }

            DeliveryOptionCard(
                icon = Icons.Filled.TwoWheeler,
                title = stringResource(R.string.delivery_option_self_title),
                description = stringResource(R.string.delivery_option_self_desc),
                selected = deliveryMode != "tiffinbox",
                onClick = { select("self") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (deliveryMode != "tiffinbox") {
                SelfDeliveryChargeCard(chef)
            } else {
                Text(
                    stringResource(R.string.delivery_charge_tiffinbox_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            DeliveryOptionCard(
                icon = Icons.Filled.DeliveryDining,
                title = stringResource(R.string.delivery_option_service_title),
                description = stringResource(R.string.delivery_option_service_desc),
                selected = deliveryMode == "tiffinbox",
                onClick = if (agreementActive) ({ select("tiffinbox") }) else null
            ) {
                val app = application
                val statusText = when (app?.status) {
                    null -> null
                    "approved" -> stringResource(R.string.delivery_option_until, formatDeliveryDate(app.validUntil))
                    "pending" -> stringResource(R.string.delivery_option_status_pending)
                    "rejected" -> stringResource(R.string.delivery_app_status_rejected)
                    "cancelled" -> stringResource(R.string.delivery_app_status_cancelled)
                    else -> stringResource(R.string.delivery_app_status_expired)
                }
                if (statusText != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (agreementActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                when {
                    agreementActive && deliveryMode == "tiffinbox" -> Unit // selected - check mark shown
                    agreementActive -> TiffinButton(
                        onClick = { select("tiffinbox") },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text(stringResource(R.string.delivery_option_select)) }
                    app?.status == "pending" -> OutlinedButton(
                        onClick = onOpenApplication,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.delivery_option_view_application)) }
                    else -> TiffinButton(
                        onClick = onOpenApplication,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            stringResource(
                                if (app == null) R.string.delivery_option_request else R.string.delivery_app_reapply
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.delivery_option_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeliveryOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        border = if (selected) BorderStroke(2.dp, primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = primary.copy(alpha = 0.12f), modifier = Modifier.size(64.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = primary, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = primary, modifier = Modifier.size(28.dp))
                }
            }
            extra()
        }
    }
}

@Composable
private fun SelfDeliveryChargeCard(chef: ChefUi?) {
    val uid = FirebaseAuthRepository.currentUser?.uid.orEmpty()
    val scope = rememberCoroutineScope()
    var cap by remember { mutableStateOf(0) }
    var defaults by remember { mutableStateOf(DeliveryPricing()) }
    var base by remember(chef) { mutableStateOf((chef?.selfDeliveryBaseFee ?: -1).takeIf { it >= 0 }?.toString().orEmpty()) }
    var perKm by remember(chef) { mutableStateOf((chef?.selfDeliveryPerKmFee ?: -1).takeIf { it >= 0 }?.toString().orEmpty()) }
    var included by remember(chef) { mutableStateOf((chef?.selfDeliveryIncludedKm ?: -1.0).takeIf { it >= 0 }?.toString().orEmpty()) }
    var savedOk by remember(chef) { mutableStateOf(false) }

    LaunchedEffect(chef) {
        cap = chef?.maxDeliveryFee?.takeIf { it > 0 } ?: FirestoreAppConfigRepository.globalMaxDeliveryFee()
        defaults = FirestoreAppConfigRepository.deliveryPricing()
    }

    val baseValue = base.toIntOrNull()
    val perKmValue = perKm.toIntOrNull()
    val includedValue = included.toDoubleOrNull()
    val baseOk = base.isBlank() || (baseValue != null && baseValue in 0..500)
    val perKmOk = perKm.isBlank() || (perKmValue != null && perKmValue in 0..100)
    val includedOk = included.isBlank() || (includedValue != null && includedValue in 0.0..20.0)
    val overCap = cap > 0 && (baseValue ?: 0) > cap
    val valid = baseOk && perKmOk && includedOk && !overCap

    val preview = DeliveryQuotes.quote(
        3.0,
        DeliveryPricing(
            baseFee = baseValue ?: defaults.baseFee,
            perKmFee = perKmValue ?: defaults.perKmFee,
            includedKm = includedValue ?: defaults.includedKm,
            maxFee = cap
        )
    ).fee

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                stringResource(R.string.delivery_charge_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.delivery_charge_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = base,
                onValueChange = { base = it; savedOk = false },
                label = { Text(stringResource(R.string.delivery_charge_base)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = !baseOk || overCap,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = perKm,
                onValueChange = { perKm = it; savedOk = false },
                label = { Text(stringResource(R.string.delivery_charge_per_km)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = !perKmOk,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = included,
                onValueChange = { included = it; savedOk = false },
                label = { Text(stringResource(R.string.delivery_charge_included_km)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !includedOk,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                stringResource(R.string.delivery_charge_default_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.delivery_charge_free_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cap > 0) {
                Text(
                    stringResource(R.string.delivery_charge_max_note, cap),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!baseOk || !perKmOk || !includedOk) {
                Text(
                    stringResource(R.string.delivery_charge_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (overCap) {
                Text(
                    stringResource(R.string.delivery_charge_over_max, cap),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (valid) {
                Text(
                    stringResource(R.string.delivery_charge_example, 3, preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            TiffinButton(
                onClick = {
                    scope.launch {
                        savedOk = FirestoreChefRepository.updateSelfDeliveryCharge(
                            uid,
                            baseValue ?: -1,
                            perKmValue ?: -1,
                            includedValue ?: -1.0
                        )
                    }
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(stringResource(R.string.delivery_charge_save)) }

            if (savedOk) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.delivery_charge_saved),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
