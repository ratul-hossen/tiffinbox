package com.ratulhossen.tiffinbox.ui.screens.auth

import android.content.Intent
import android.provider.Settings
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
import com.ratulhossen.tiffinbox.data.location.LocationFailure
import com.ratulhossen.tiffinbox.data.location.LocationRepository
import com.ratulhossen.tiffinbox.data.location.LocationResult
import com.ratulhossen.tiffinbox.ui.screens.roleselect.UserRole
import kotlinx.coroutines.launch
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

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

    val locationOffMessage = stringResource(R.string.location_setup_location_off)
    var showOpenSettings by remember { mutableStateOf(false) }

    fun saveLocation(latitude: Double, longitude: Double, resolvedAddress: String) {
        if (isChefRole) {
            FirestoreChefRepository.updateLocation(uid, latitude, longitude, resolvedAddress)
        } else {
            FirestoreUserRepository.saveLocation(uid, latitude, longitude, resolvedAddress)
        }
        onLocationSaved()
    }

    fun detectAndSave() {
        errorMessage = null
        showOpenSettings = false
        isDetecting = true
        scope.launch {
            when (val result = LocationRepository.detectCurrentLocation(context)) {
                is LocationResult.Success -> {
                    val readable = LocationRepository.reverseGeocode(context, result.point).orEmpty()
                    isDetecting = false
                    saveLocation(result.point.latitude, result.point.longitude, readable)
                }
                is LocationResult.Failure -> {
                    isDetecting = false
                    when (result.reason) {
                        LocationFailure.NO_PERMISSION -> errorMessage = permissionDeniedMessage
                        LocationFailure.LOCATION_OFF -> {
                            errorMessage = locationOffMessage
                            showOpenSettings = true
                        }
                        LocationFailure.NO_FIX -> errorMessage = gpsFailedMessage
                    }
                }
            }
        }
    }

    // Asks for both fine and coarse: on Android 12+ the user may grant only
    // "approximate", which used to be treated as a denial when only FINE was
    // requested.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            detectAndSave()
        } else {
            isDetecting = false
            errorMessage = permissionDeniedMessage
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Edge-to-edge: keep content clear of the status bar, the phone's
            // back/home buttons and the keyboard.
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .shadow(10.dp, MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
        ) {
            FullBleedPhoto(resId = R.drawable.location_setup, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(20.dp))
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

        TiffinButton(
            onClick = {
                if (LocationRepository.hasLocationPermission(context)) {
                    detectAndSave()
                } else {
                    errorMessage = null
                    isDetecting = true
                    permissionLauncher.launch(LocationRepository.PERMISSIONS)
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
                        saveLocation(point.latitude, point.longitude, address.trim())
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
        if (showOpenSettings) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text(stringResource(R.string.location_setup_open_settings))
            }
        }
    }
}
