package com.ratulhossen.tiffinbox.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.firebase.MAX_SAVED_ADDRESSES
import com.ratulhossen.tiffinbox.data.firebase.SavedAddress
import com.ratulhossen.tiffinbox.data.location.LocationFailure
import com.ratulhossen.tiffinbox.data.location.LocationRepository
import com.ratulhossen.tiffinbox.data.location.LocationResult
import kotlinx.coroutines.launch

/**
 * Up to [MAX_SAVED_ADDRESSES] saved delivery addresses (e.g. Home + Office)
 * on users/{uid}: add, edit, delete, and pick the default. Each change is
 * saved straight away; the default is mirrored to the top-level
 * latitude/longitude used for "nearby chefs".
 */
@Composable
fun SavedAddressesSection(uid: String) {
    val scope = rememberCoroutineScope()
    var addresses by remember { mutableStateOf<List<SavedAddress>>(emptyList()) }
    var defaultIndex by remember { mutableStateOf(0) }
    // null = closed; -1 = adding a new one; otherwise the index being edited.
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(uid) {
        val (list, def) = FirestoreUserRepository.getSavedAddresses(uid)
        addresses = list
        defaultIndex = def
    }

    fun persist(newList: List<SavedAddress>, newDefault: Int) {
        addresses = newList
        defaultIndex = newDefault.coerceIn(0, (newList.size - 1).coerceAtLeast(0))
        scope.launch { FirestoreUserRepository.saveSavedAddresses(uid, newList, defaultIndex) }
    }

    Column {
        Text(
            stringResource(R.string.saved_addresses_title, MAX_SAVED_ADDRESSES),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        addresses.forEachIndexed { index, item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label.ifBlank { "—" }, fontWeight = FontWeight.SemiBold)
                            if (index == defaultIndex) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Text(
                                        stringResource(R.string.saved_addresses_default),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            item.address.ifBlank { stringResource(R.string.saved_addresses_no_text) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (index != defaultIndex) {
                            TextButton(onClick = { persist(addresses, index) }, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(R.string.saved_addresses_make_default))
                            }
                        }
                    }
                    IconButton(onClick = { editingIndex = index }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.saved_addresses_edit))
                    }
                    IconButton(onClick = {
                        val newList = addresses.filterIndexed { i, _ -> i != index }
                        val newDefault = when {
                            index < defaultIndex -> defaultIndex - 1
                            index == defaultIndex -> 0
                            else -> defaultIndex
                        }
                        persist(newList, newDefault)
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.saved_addresses_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (addresses.size < MAX_SAVED_ADDRESSES) {
            OutlinedButton(
                onClick = { editingIndex = -1 },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.saved_addresses_add))
            }
        }
    }

    editingIndex?.let { idx ->
        AddressEditorDialog(
            initial = addresses.getOrNull(idx),
            defaultLabel = if (addresses.isEmpty()) stringResource(R.string.saved_addresses_label_home)
            else stringResource(R.string.saved_addresses_label_office),
            onDismiss = { editingIndex = null },
            onSave = { saved ->
                val newList = if (idx >= 0) addresses.mapIndexed { i, a -> if (i == idx) saved else a } else addresses + saved
                editingIndex = null
                persist(newList, if (addresses.isEmpty()) 0 else defaultIndex)
            }
        )
    }
}

@Composable
private fun AddressEditorDialog(
    initial: SavedAddress?,
    defaultLabel: String,
    onDismiss: () -> Unit,
    onSave: (SavedAddress) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(initial?.label ?: defaultLabel) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var latitude by remember { mutableStateOf(initial?.latitude ?: 0.0) }
    var longitude by remember { mutableStateOf(initial?.longitude ?: 0.0) }
    // Coordinates are only trusted if they came from GPS or a geocode of the
    // text as it is now; editing the text invalidates them.
    var coordsMatchText by remember { mutableStateOf(initial != null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val notFound = stringResource(R.string.location_setup_address_not_found)
    val gpsFailed = stringResource(R.string.location_setup_gps_failed)
    val locationOff = stringResource(R.string.location_setup_location_off)
    val denied = stringResource(R.string.location_setup_permission_denied)
    val required = stringResource(R.string.location_setup_address_required)

    fun detect() {
        busy = true
        error = null
        scope.launch {
            when (val result = LocationRepository.detectCurrentLocation(context)) {
                is LocationResult.Success -> {
                    latitude = result.point.latitude
                    longitude = result.point.longitude
                    address = LocationRepository.reverseGeocode(context, result.point).orEmpty().ifBlank { address }
                    coordsMatchText = true
                }
                is LocationResult.Failure -> error = when (result.reason) {
                    LocationFailure.NO_PERMISSION -> denied
                    LocationFailure.LOCATION_OFF -> locationOff
                    LocationFailure.NO_FIX -> gpsFailed
                }
            }
            busy = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> if (grants.values.any { it }) detect() else error = denied }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.saved_addresses_add else R.string.saved_addresses_edit))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.saved_addresses_label_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        coordsMatchText = false
                    },
                    label = { Text(stringResource(R.string.location_setup_address_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (LocationRepository.hasLocationPermission(context)) detect()
                        else permissionLauncher.launch(LocationRepository.PERMISSIONS)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.location_setup_use_current_button))
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (address.isBlank()) {
                        error = required
                        return@TextButton
                    }
                    if (coordsMatchText && latitude != 0.0) {
                        onSave(SavedAddress(label.trim(), address.trim(), latitude, longitude))
                        return@TextButton
                    }
                    busy = true
                    scope.launch {
                        val point = LocationRepository.geocodeAddress(context, address.trim())
                        busy = false
                        if (point != null) {
                            onSave(SavedAddress(label.trim(), address.trim(), point.latitude, point.longitude))
                        } else {
                            error = notFound
                        }
                    }
                }
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.edit_profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel_generic)) }
        }
    )
}
