package com.ratulhossen.tiffinbox.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import kotlinx.coroutines.launch

private data class OnboardingPage(val emoji: String, val title: String, val description: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            "🏠",
            stringResource(R.string.onboarding_page1_title),
            stringResource(R.string.onboarding_page1_desc)
        ),
        OnboardingPage(
            "✅",
            stringResource(R.string.onboarding_page2_title),
            stringResource(R.string.onboarding_page2_desc)
        ),
        OnboardingPage(
            "🤖",
            stringResource(R.string.onboarding_page3_title),
            stringResource(R.string.onboarding_page3_desc)
        )
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = onFinished,
            modifier = Modifier.align(Alignment.End).padding(top = 16.dp, end = 8.dp)
        ) {
            Text(stringResource(R.string.onboarding_skip_button))
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val item = pages[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(item.emoji, fontSize = 72.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    item.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    item.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        ) {
            repeat(pages.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage < pages.lastIndex) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinished()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(if (pagerState.currentPage < pages.lastIndex) stringResource(R.string.onboarding_next_button) else stringResource(R.string.onboarding_start_button))
        }
    }
}
