package com.ratulhossen.tiffinbox.ui.screens.roleselect

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.bottomScrim
import com.ratulhossen.tiffinbox.ui.theme.brandGradient

enum class UserRole { CUSTOMER, CHEF }

/**
 * "Who are you?" - brand header, two photo cards, a perks panel that
 * explains the chosen role, and a "Continue as ..." button pinned at the
 * bottom. Motion is limited to one entrance (content rises in once) and
 * the selection response itself - nothing loops.
 */
@Composable
fun RoleSelectionScreen(onRoleSelected: (UserRole) -> Unit) {
    var selected by remember { mutableStateOf<UserRole?>(null) }

    // One-time entrance: header, cards, then panel fade/rise in, staggered.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    fun Modifier.rise(order: Int): Modifier = graphicsLayer {
        val p = ((enter.value - order * 0.15f) / 0.7f).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 48f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            BrandHeader(Modifier.rise(0))
            Spacer(modifier = Modifier.height(28.dp))

            Column(Modifier.rise(0)) {
                Text(
                    stringResource(R.string.role_select_title),
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    stringResource(R.string.role_select_subtitle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(22.dp))

            // Two tall photo cards side by side (res/drawable-nodpi/role_customer.jpg, role_chef.jpg).
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.rise(1)
            ) {
                RoleCard(
                    photo = R.drawable.role_customer,
                    tag = stringResource(R.string.role_customer_tag),
                    title = stringResource(R.string.role_customer_title),
                    description = stringResource(R.string.role_customer_desc),
                    selected = selected == UserRole.CUSTOMER,
                    dimmed = selected == UserRole.CHEF,
                    onClick = { selected = UserRole.CUSTOMER },
                    modifier = Modifier.weight(1f)
                )
                RoleCard(
                    photo = R.drawable.role_chef,
                    tag = stringResource(R.string.role_chef_tag),
                    title = stringResource(R.string.role_chef_title),
                    description = stringResource(R.string.role_chef_desc),
                    selected = selected == UserRole.CHEF,
                    dimmed = selected == UserRole.CUSTOMER,
                    onClick = { selected = UserRole.CHEF },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))

            PerksPanel(selected, Modifier.rise(2))
            Spacer(modifier = Modifier.height(12.dp))
        }

        TiffinButton(
            onClick = { selected?.let(onRoleSelected) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                when (selected) {
                    UserRole.CUSTOMER -> stringResource(R.string.role_continue_as_customer)
                    UserRole.CHEF -> stringResource(R.string.role_continue_as_chef)
                    null -> stringResource(R.string.role_select_pick_first)
                },
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(10.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(brandGradient()),
            contentAlignment = Alignment.Center
        ) { Text("🍱", fontSize = 22.sp) }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.app_name), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.splash_tagline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RoleCard(
    @DrawableRes photo: Int,
    tag: String,
    title: String,
    description: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardScale by animateFloatAsState(if (selected) 1.03f else if (dimmed) 0.97f else 1f, label = "roleCardScale")
    val cardAlpha by animateFloatAsState(if (dimmed) 0.6f else 1f, label = "roleCardAlpha")
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .scale(cardScale)
            .alpha(cardAlpha)
            .aspectRatio(3f / 4.3f)
            .shadow(if (selected) 18.dp else 6.dp, shape)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        FullBleedPhoto(
            resId = photo,
            modifier = Modifier.fillMaxSize(),
            scrim = bottomScrim(0.9f)
        )
        // Small label chip top-left ("Order food" / "Sell food").
        Text(
            tag,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
        // Selection badge: empty ring when not chosen, brand-filled tick when chosen.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(26.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(brandGradient())
                    else Modifier.background(Color.Black.copy(alpha = 0.2f))
                )
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                description,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 3
            )
        }
        // Brand-gradient ring drawn on top so the photo never covers it.
        if (selected) {
            Box(Modifier.matchParentSize().border(3.dp, brandGradient(), shape))
        }
    }
}

/** What the chosen role gets - or a gentle hint before anything is picked. */
@Composable
private fun PerksPanel(selected: UserRole?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        AnimatedContent(
            targetState = selected,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "rolePerks"
        ) { role ->
            Column(modifier = Modifier.padding(16.dp)) {
                when (role) {
                    null -> Text(
                        stringResource(R.string.role_select_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UserRole.CUSTOMER -> {
                        PanelTitle(stringResource(R.string.role_customer_perks_title))
                        Perk("📍", stringResource(R.string.role_customer_perk_1))
                        Perk("💳", stringResource(R.string.role_customer_perk_2))
                        Perk("⚡", stringResource(R.string.role_customer_perk_3))
                    }
                    UserRole.CHEF -> {
                        PanelTitle(stringResource(R.string.role_chef_perks_title))
                        Perk("🍳", stringResource(R.string.role_chef_perk_1))
                        Perk("💰", stringResource(R.string.role_chef_perk_2))
                        Perk("🛡️", stringResource(R.string.role_chef_perk_3))
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun Perk(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 15.sp) }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
