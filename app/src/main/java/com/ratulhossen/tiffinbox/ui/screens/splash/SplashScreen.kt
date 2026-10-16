package com.ratulhossen.tiffinbox.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import com.ratulhossen.tiffinbox.ui.theme.brandGradient
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: suspend () -> Unit) {
    val logoScale = remember { Animatable(0.7f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(500))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, tween(600))
        delay(900)
        onFinished()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Food flat-lay with an empty dark center (res/drawable-nodpi/splash_bg.jpg).
        FullBleedPhoto(
            resId = R.drawable.splash_bg,
            modifier = Modifier.fillMaxSize(),
            scrim = Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.15f))
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().alpha(contentAlpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .scale(logoScale.value)
                    .size(96.dp)
                    .shadow(20.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(brandGradient()),
                contentAlignment = Alignment.Center
            ) {
                Text("🍱", fontSize = 48.sp)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                stringResource(R.string.splash_tagline),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
        }
    }
}
