package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ratulhossen.tiffinbox.R

/** Distance options for the chef list and map ("All" = no limit). */
val DISTANCE_FILTER_OPTIONS_KM = listOf(1.0, 3.0, 5.0)

/** "All · 1 km · 3 km · 5 km" - [selectedKm] null means All. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistanceFilterChips(selectedKm: Double?, onSelect: (Double?) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(rememberScrollState())
    ) {
        FilterChip(
            selected = selectedKm == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.distance_filter_all)) }
        )
        DISTANCE_FILTER_OPTIONS_KM.forEach { km ->
            FilterChip(
                selected = selectedKm == km,
                onClick = { onSelect(km) },
                label = { Text(stringResource(R.string.distance_filter_km, km.toInt())) }
            )
        }
    }
}
