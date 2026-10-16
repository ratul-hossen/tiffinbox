package com.ratulhossen.tiffinbox.ui.screens.cheflist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirebaseAuthRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreUserRepository
import com.ratulhossen.tiffinbox.data.location.GeoPoint as AppGeoPoint
import com.ratulhossen.tiffinbox.data.model.ChefUi
import com.ratulhossen.tiffinbox.ui.components.DistanceFilterChips
import com.ratulhossen.tiffinbox.ui.components.formatDistanceKm
import com.ratulhossen.tiffinbox.util.withRealDistances
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/**
 * Nearby kitchens as pins on an OpenStreetMap map (osmdroid - free, no API
 * key, no billing; Google Maps would need the paid Blaze plan). The
 * customer's saved location is the centre, with a circle for the chosen
 * distance filter. Tapping a pin shows the kitchen's name; tapping that
 * bubble opens the kitchen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefMapScreen(onBack: () -> Unit, onChefClick: (String) -> Unit) {
    val context = LocalContext.current
    var me by remember { mutableStateOf<AppGeoPoint?>(null) }
    var chefs by remember { mutableStateOf<List<ChefUi>>(emptyList()) }
    var maxKm by remember { mutableStateOf<Double?>(5.0) }

    LaunchedEffect(Unit) {
        val all = FirestoreCatalogRepository.getChefs().filter { it.latitude != 0.0 || it.longitude != 0.0 }
        val mine = FirebaseAuthRepository.currentUser?.uid?.let { FirestoreUserRepository.getLocation(it) }
        me = mine
        chefs = if (mine != null) all.withRealDistances(mine) else all
    }
    val shown = remember(chefs, maxKm, me) {
        if (me == null) chefs else maxKm?.let { km -> chefs.filter { it.distanceKm <= km } } ?: chefs
    }

    // osmdroid needs a user agent (OpenStreetMap tile usage policy) and its cache settings.
    remember {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
            userAgentValue = context.packageName
        }
    }
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            overlays.add(CopyrightOverlay(context))
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.chef_map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (me != null) {
                DistanceFilterChips(
                    selectedKm = maxKm,
                    onSelect = { maxKm = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            } else {
                Text(
                    stringResource(R.string.chef_map_no_location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            val youLabel = stringResource(R.string.chef_map_you)
            val openLabel = stringResource(R.string.chef_map_open_kitchen)
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { map ->
                    // Rebuild markers whenever the list / filter changes (keeps the copyright overlay).
                    map.overlays.removeAll { it is Marker || it is Polygon }
                    val centre = me?.let { GeoPoint(it.latitude, it.longitude) }
                        ?: shown.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) }
                    me?.let { mine ->
                        val here = GeoPoint(mine.latitude, mine.longitude)
                        maxKm?.let { km ->
                            map.overlays.add(Polygon(map).apply {
                                points = Polygon.pointsAsCircle(here, km * 1000)
                                fillPaint.color = 0x22E11D2E
                                outlinePaint.color = 0xAAE11D2E.toInt()
                                outlinePaint.strokeWidth = 3f
                                setOnClickListener { _, _, _ -> false }
                            })
                        }
                        map.overlays.add(Marker(map).apply {
                            position = here
                            title = youLabel
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    shown.forEach { chef ->
                        map.overlays.add(Marker(map).apply {
                            position = GeoPoint(chef.latitude, chef.longitude)
                            title = chef.kitchenName
                            snippet = if (me != null) "${formatDistanceKm(chef.distanceKm)} km · $openLabel" else openLabel
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            setOnMarkerClickListener { marker, _ ->
                                if (marker.isInfoWindowShown) {
                                    marker.closeInfoWindow()
                                    onChefClick(chef.id)
                                } else {
                                    marker.showInfoWindow()
                                }
                                true
                            }
                        })
                    }
                    centre?.let { map.controller.setCenter(it) }
                    map.invalidate()
                }
            )
        }
    }
}
