package com.ratulhossen.tiffinbox.ui.screens.chefside

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreVerificationRepository
import com.ratulhossen.tiffinbox.data.firebase.VerificationUi
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.util.ImageCompressor
import kotlinx.coroutines.launch

/**
 * "Get verified": the chef sends a photo of their NID (kept private - only
 * TiffinBox admins can open it) and the admin also checks their kitchen
 * photo. Both checked = a "Verified" badge customers see on the kitchen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefVerificationScreen(onBack: () -> Unit) {
    val uid = FirebaseAuthRepository.currentUser?.uid.orEmpty()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chef by remember { mutableStateOf<ChefUi?>(null) }
    var request by remember { mutableStateOf<VerificationUi?>(null) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        chef = FirestoreCatalogRepository.getChef(uid)
        request = FirestoreVerificationRepository.get(uid)
    }
    val pickNid = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.verification_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
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
            val c = chef
            if (c?.isFullyVerified == true) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Verified, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.verification_done),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(stringResource(R.string.verification_intro), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            CheckRow(
                done = c?.nidVerified == true,
                waiting = c?.nidVerified != true && request?.status == "pending" && request?.hasNidPhoto == true,
                title = stringResource(R.string.verification_nid_title),
                subtitle = stringResource(R.string.verification_nid_subtitle)
            )
            CheckRow(
                done = c?.kitchenVerified == true,
                waiting = c?.kitchenVerified != true && !c?.photoBase64.isNullOrBlank(),
                title = stringResource(R.string.verification_kitchen_title),
                subtitle = if (c?.photoBase64.isNullOrBlank()) stringResource(R.string.verification_kitchen_missing)
                else stringResource(R.string.verification_kitchen_subtitle)
            )

            if (request?.status == "rejected" && request?.note?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.verification_rejected_note, request?.note.orEmpty()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (c?.nidVerified != true) {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { pickNid.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (picked != null) stringResource(R.string.verification_nid_selected)
                        else stringResource(R.string.verification_pick_nid)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.verification_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                TiffinButton(
                    onClick = {
                        val uri = picked ?: return@TiffinButton
                        sending = true
                        message = null
                        scope.launch {
                            // Readable but small: 1280px, ~300KB.
                            val base64 = ImageCompressor.compressToBase64(context, uri, maxDimension = 1280, maxRawBytes = 300_000)
                            val ok = base64 != null && FirestoreVerificationRepository.submitNid(uid, base64)
                            sending = false
                            message = context.getString(if (ok) R.string.verification_sent else R.string.verification_send_failed)
                            if (ok) {
                                picked = null
                                reload++
                            }
                        }
                    },
                    enabled = picked != null && !sending,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Text(stringResource(R.string.verification_send))
                }
            }
            message?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CheckRow(done: Boolean, waiting: Boolean, title: String, subtitle: String) {
    val (icon: ImageVector, tint) = when {
        done -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        waiting -> Icons.Filled.HourglassTop to MaterialTheme.colorScheme.tertiary
        else -> Icons.Filled.RadioButtonUnchecked to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    done -> stringResource(R.string.verification_status_done)
                    waiting -> stringResource(R.string.verification_status_waiting)
                    else -> subtitle
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
