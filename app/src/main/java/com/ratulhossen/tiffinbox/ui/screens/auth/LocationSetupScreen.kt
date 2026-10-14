package com.ratulhossen.tiffinbox.ui.screens.auth

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreChefRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.location.LocationRepository
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import kotlinx.coroutines.launch

@Composable
fun LocationSetupScreen(uid: String, role: String, onLocationSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isChefRole = role == UserRole.CHEF.name

    var address by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }
    var isGeocoding by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionDeniedMessage = stringResource(R.string.location_setup_permission_denied)
    val gpsFailedMessage = stringResource(R.string.location_setup_gps_failed)
    val addressNotFoundMessage = stringResource(R.string.location_setup_address_not_found)
    val addressRequiredMessage = stringResource(R.string.location_setup_address_required)

    fun saveLocation(latitude: Double, longitude: Double) {
        if (isChefRole) {
            FirestoreChefRepository.updateLocation(uid, latitude, longitude)
        } else {
            FirestoreUserRepository.saveLocation(uid, latitude, longitude)
        }
        onLocationSaved()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            isDetecting = false
            errorMessage = permissionDeniedMessage
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val point = LocationRepository.getCurrentLocation(context)
            isDetecting = false
            if (point != null) {
                saveLocation(point.latitude, point.longitude)
            } else {
                errorMessage = gpsFailedMessage
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.location_setup_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            if (isChefRole) {
                stringResource(R.string.location_setup_subtitle_chef)
            } else {
                stringResource(R.string.location_setup_subtitle_customer)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                errorMessage = null
                isDetecting = true
                if (LocationRepository.hasLocationPermission(context)) {
                    scope.launch {
                        val point = LocationRepository.getCurrentLocation(context)
                        isDetecting = false
                        if (point != null) {
                            saveLocation(point.latitude, point.longitude)
                        } else {
                            errorMessage = gpsFailedMessage
                        }
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            enabled = !isDetecting && !isGeocoding,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isDetecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.location_setup_use_current_button))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.location_setup_or_divider),
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(stringResource(R.string.location_setup_address_label)) },
            placeholder = { Text(stringResource(R.string.location_setup_address_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                errorMessage = null
                if (address.isBlank()) {
                    errorMessage = addressRequiredMessage
                    return@OutlinedButton
                }
                isGeocoding = true
                scope.launch {
                    val point = LocationRepository.geocodeAddress(context, address.trim())
                    isGeocoding = false
                    if (point != null) {
                        saveLocation(point.latitude, point.longitude)
                    } else {
                        errorMessage = addressNotFoundMessage
                    }
                }
            },
            enabled = !isDetecting && !isGeocoding,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (isGeocoding) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.location_setup_confirm_address_button))
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                errorMessage.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }
    }
}
