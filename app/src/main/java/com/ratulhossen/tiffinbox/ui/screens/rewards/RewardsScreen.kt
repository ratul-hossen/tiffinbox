package com.ratulhossen.tiffinbox.ui.screens.rewards

import com.ratulhossen.tiffinbox.ui.components.TiffinLottie
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreOrderRepository
import com.ratulhossen.tiffinbox.domain.Rewards
import com.ratulhossen.tiffinbox.domain.RewardsStatus
import com.ratulhossen.tiffinbox.ui.theme.brandGradient

/**
 * TiffinBox Points as a little game: current rank badge (bouncing), level,
 * an XP bar to the next rank, the rank ladder with what each unlocks, and
 * how to earn / spend. All numbers come from FirestoreOrderRepository.getMyRewards().
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf<RewardsStatus?>(null) }
    LaunchedEffect(Unit) { status = FirestoreOrderRepository.getMyRewards() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.rewards_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        val s = status
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            RankHero(s)
            Spacer(modifier = Modifier.height(20.dp))

            Text(stringResource(R.string.rewards_ladder_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Rewards.RANKS.forEach { rank ->
                val unlocked = s.lifetimePoints >= rank.minPoints
                val current = rank.level == s.rank.level
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .alpha(if (unlocked) 1f else 0.6f)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (unlocked) rank.emoji else "🔒", fontSize = 30.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.rewards_level_title, rank.level, rank.title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                listOfNotNull(
                                    stringResource(R.string.rewards_needs_points, rank.minPoints),
                                    if (rank.discountPercent > 0) stringResource(R.string.rewards_perk_discount, rank.discountPercent) else null,
                                    if (rank.freeDelivery) stringResource(R.string.rewards_perk_free_delivery) else null
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (current) {
                            Text(stringResource(R.string.rewards_you), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(stringResource(R.string.rewards_how_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                stringResource(R.string.rewards_how_earn, Rewards.POINTS_PER_TAKA),
                stringResource(R.string.rewards_how_spend, Rewards.POINTS_PER_TAKA_OFF, (Rewards.MAX_REDEEM_SHARE * 100).toInt()),
                stringResource(R.string.rewards_how_rank),
                stringResource(R.string.rewards_how_cancel)
            ).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun RankHero(s: RewardsStatus) {
    // One springy pop when the screen opens (with the star burst behind it),
    // then the badge rests - no endless bouncing.
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    val progress by animateFloatAsState(s.progressToNext, tween(1200, easing = FastOutSlowInEasing), label = "xp")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(brandGradient())
            .padding(20.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.rewards_level_badge, s.rank.level),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(contentAlignment = Alignment.Center) {
                TiffinLottie(res = R.raw.lottie_star_burst, loop = false, modifier = Modifier.size(190.dp))
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .scale(pop.value)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(3.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(s.rank.emoji, fontSize = 58.sp) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(s.rank.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.rewards_balance, s.balance),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                s.nextRank?.let { stringResource(R.string.rewards_to_next, s.pointsToNext, it.emoji, it.title) }
                    ?: stringResource(R.string.rewards_max_rank),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.rewards_stats, s.lifetimePoints, s.deliveredOrders),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
