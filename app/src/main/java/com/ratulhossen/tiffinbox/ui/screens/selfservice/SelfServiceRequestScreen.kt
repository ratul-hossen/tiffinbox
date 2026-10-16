package com.ratulhossen.tiffinbox.ui.screens.selfservice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreSelfServiceRepository
import kotlinx.coroutines.launch
import com.ratulhossen.tiffinbox.ui.components.TiffinButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfServiceRequestScreen(onBack: () -> Unit, onRequestCreated: (requestId: String) -> Unit) {
    var pickupAddress by remember { mutableStateOf("") }
    var dropAddress by remember { mutableStateOf("") }
    var preferredTime by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val requiredMessage = stringResource(R.string.self_service_fields_required)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.self_service_title)) },
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
            Text(
                stringResource(R.string.self_service_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = pickupAddress,
                onValueChange = { pickupAddress = it },
                label = { Text(stringResource(R.string.self_service_pickup_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = dropAddress,
                onValueChange = { dropAddress = it },
                label = { Text(stringResource(R.string.self_service_drop_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = preferredTime,
                onValueChange = { preferredTime = it },
                label = { Text(stringResource(R.string.self_service_time_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.self_service_note_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))
            TiffinButton(
                onClick = {
                    errorMessage = null
                    if (pickupAddress.isBlank() || dropAddress.isBlank()) {
                        errorMessage = requiredMessage
                        return@Button
                    }
                    isLoading = true
                    scope.launch {
                        val requestId = FirestoreSelfServiceRepository.createRequest(
                            pickupAddress = pickupAddress.trim(),
                            dropAddress = dropAddress.trim(),
                            preferredTime = preferredTime.trim(),
                            note = note.trim()
                        )
                        isLoading = false
                        if (requestId != null) onRequestCreated(requestId)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.self_service_submit_button))
                }
            }
        }
    }
}
