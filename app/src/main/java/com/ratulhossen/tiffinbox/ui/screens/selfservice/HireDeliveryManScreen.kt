package com.ratulhossen.tiffinbox.ui.screens.selfservice

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreSelfServiceRepository
import com.ratulhossen.tiffinbox.data.model.DeliveryPartnerUi
import com.ratulhossen.tiffinbox.data.model.SelfServiceRequestUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HireDeliveryManScreen(requestId: String, onBack: () -> Unit) {
    var request by remember { mutableStateOf<SelfServiceRequestUi?>(null) }
    var partners by remember { mutableStateOf<List<DeliveryPartnerUi>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hiringId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(requestId) {
        request = FirestoreSelfServiceRepository.getRequest(requestId)
        partners = FirestoreSelfServiceRepository.getActiveDeliveryPartners()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hire_delivery_man_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp)) {
            val currentRequest = request
            if (currentRequest?.status == "assigned") {
                val hiredPartner = partners.find { it.id == currentRequest.deliveryPartnerId }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.hire_delivery_man_hired, hiredPartner?.name.orEmpty()),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                Text(
                    stringResource(R.string.hire_delivery_man_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (!isLoading && partners.isEmpty()) {
                    Text(
                        stringResource(R.string.hire_delivery_man_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                partners.forEach { partner ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.TwoWheeler,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(partner.name, fontWeight = FontWeight.Bold)
                                if (partner.phone.isNotBlank()) {
                                    Text(partner.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Button(
                                enabled = hiringId == null,
                                onClick = {
                                    hiringId = partner.id
                                    FirestoreSelfServiceRepository.assignDeliveryPartner(requestId, partner.id)
                                    request = currentRequest?.copy(status = "assigned", deliveryPartnerId = partner.id)
                                }
                            ) {
                                Text(stringResource(R.string.hire_delivery_man_hire_button))
                            }
                        }
                    }
                }
            }
        }
    }
}
