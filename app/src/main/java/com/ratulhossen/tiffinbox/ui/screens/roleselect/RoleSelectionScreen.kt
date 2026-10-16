package com.ratulhossen.tiffinbox.ui.screens.roleselect

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.bottomScrim

enum class UserRole { CUSTOMER, CHEF }

@Composable
fun RoleSelectionScreen(onRoleSelected: (UserRole) -> Unit) {
    var selected by remember { mutableStateOf<UserRole?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.role_select_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.role_select_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(28.dp))

        // Two tall photo cards side by side (res/drawable-nodpi/role_customer.jpg, role_chef.jpg).
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoleCard(
                photo = R.drawable.role_customer,
                title = stringResource(R.string.role_customer_title),
                description = stringResource(R.string.role_customer_desc),
                selected = selected == UserRole.CUSTOMER,
                onClick = { selected = UserRole.CUSTOMER },
                modifier = Modifier.weight(1f)
            )
            RoleCard(
                photo = R.drawable.role_chef,
                title = stringResource(R.string.role_chef_title),
                description = stringResource(R.string.role_chef_desc),
                selected = selected == UserRole.CHEF,
                onClick = { selected = UserRole.CHEF },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        TiffinButton(
            onClick = { selected?.let(onRoleSelected) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(stringResource(R.string.role_select_continue_button))
        }
    }
}

@Composable
private fun RoleCard(
    @DrawableRes photo: Int,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardScale by animateFloatAsState(if (selected) 1.03f else 1f, label = "roleCardScale")
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .scale(cardScale)
            .aspectRatio(3f / 4.3f)
            .shadow(if (selected) 16.dp else 6.dp, shape)
            .clip(shape)
            .then(
                if (selected) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        FullBleedPhoto(
            resId = photo,
            modifier = Modifier.fillMaxSize(),
            scrim = bottomScrim(0.9f)
        )
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(28.dp)
                    .clip(CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                description,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3
            )
        }
    }
}
