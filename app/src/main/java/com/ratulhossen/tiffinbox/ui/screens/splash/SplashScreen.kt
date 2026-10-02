package com.ratulhossen.tiffinbox.ui.screens.splash

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: suspend () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onFinished()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🍱", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.app_name),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.splash_tagline),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                fontSize = 13.sp
            )
        }
    }
}
