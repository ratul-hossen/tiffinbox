package com.ratulhossen.tiffinbox.ui.screens.onboarding

import com.ratulhossen.tiffinbox.ui.components.FloatingFoodParticles
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.ui.components.FullBleedPhoto
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.ui.components.bottomScrim
import kotlinx.coroutines.launch

private data class OnboardingPage(@DrawableRes val photo: Int, val title: String, val description: String)

/**
 * Full-screen photo pager (res/drawable-nodpi/onboarding_1..3.jpg). Each
 * photo's bottom third is dark by design, so the white title/description
 * and the fixed dots + button sit on top of it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            R.drawable.onboarding_1,
            stringResource(R.string.onboarding_page1_title),
            stringResource(R.string.onboarding_page1_desc)
        ),
        OnboardingPage(
            R.drawable.onboarding_2,
            stringResource(R.string.onboarding_page2_title),
            stringResource(R.string.onboarding_page2_desc)
        ),
        OnboardingPage(
            R.drawable.onboarding_3,
            stringResource(R.string.onboarding_page3_title),
            stringResource(R.string.onboarding_page3_desc)
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = pages[page]
            Box(modifier = Modifier.fillMaxSize()) {
                FullBleedPhoto(
                    resId = item.photo,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.TopCenter,
                    scrim = bottomScrim(0.92f)
                )
                FloatingFoodParticles(modifier = Modifier.fillMaxSize())
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, bottom = 170.dp)
                ) {
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        item.description,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        TextButton(
            onClick = onFinished,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
        ) {
            Text(stringResource(R.string.onboarding_skip_button), color = Color.White, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(pages.size) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .animateContentSize()
                            .height(8.dp)
                            .width(if (selected) 26.dp else 8.dp)
                            .background(
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            TiffinButton(
                onClick = {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(
                    if (pagerState.currentPage < pages.lastIndex) stringResource(R.string.onboarding_next_button)
                    else stringResource(R.string.onboarding_start_button)
                )
            }
        }
    }
}
