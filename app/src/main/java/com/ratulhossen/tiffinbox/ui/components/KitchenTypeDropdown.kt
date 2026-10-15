package com.ratulhossen.tiffinbox.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ratulhossen.tiffinbox.R

/**
 * "Home Kitchen" / "Cloud Kitchen" / "Restaurant" - the reference's Kitchen
 * Type field. Deliberately built from plain Box + DropdownMenu rather than
 * Material3's ExposedDropdownMenuBox/ExposedDropdownMenu family - that
 * family's API has shifted across this project's compose-bom version
 * (ExposedDropdownMenu and later exposedDropdownSize both failed to
 * resolve), so this sticks to the oldest, most stable widgets instead of
 * chasing a moving target.
 */
@Composable
fun KitchenTypeDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        stringResource(R.string.kitchen_type_home),
        stringResource(R.string.kitchen_type_cloud),
        stringResource(R.string.kitchen_type_restaurant)
    )
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value.ifBlank { options[0] },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.login_kitchen_type_label)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // A transparent overlay captures the click - the text field itself
        // stays readOnly/non-focusable so no keyboard pops up, and this is
        // the only part that needs to react to taps.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { expanded = !expanded }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
