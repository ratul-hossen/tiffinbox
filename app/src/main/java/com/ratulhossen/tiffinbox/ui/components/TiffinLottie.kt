package com.ratulhossen.tiffinbox.ui.components

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Plays one of the app's own vector motion graphics (res/raw/lottie_*.json -
 * flat brand-coloured animations authored for TiffinBox, no third-party
 * assets). [loop] = false plays it once and then calls [onFinished]; looped
 * ones are only used where something is genuinely in progress (cooking,
 * on the way, waiting).
 */
@Composable
fun TiffinLottie(
    @RawRes res: Int,
    modifier: Modifier = Modifier,
    loop: Boolean = true,
    speed: Float = 1f,
    onFinished: () -> Unit = {}
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        speed = speed
    )
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(composition, progress) {
        if (!loop && composition != null && progress >= 1f) finished()
    }
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}
