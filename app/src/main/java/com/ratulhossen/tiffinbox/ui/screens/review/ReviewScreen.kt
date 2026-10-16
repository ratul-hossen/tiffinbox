package com.ratulhossen.tiffinbox.ui.screens.review

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.firebase.FirestoreReviewRepository
import com.ratulhossen.tiffinbox.ui.components.StarRatingInput
import com.ratulhossen.tiffinbox.ui.components.TiffinButton

@Composable
fun ReviewScreen(chefId: String, onSubmitted: () -> Unit) {
    var taste by remember { mutableIntStateOf(0) }
    var hygiene by remember { mutableIntStateOf(0) }
    var packaging by remember { mutableIntStateOf(0) }
    var onTime by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    val canSubmit = taste > 0 && hygiene > 0 && packaging > 0 && onTime > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("🍽️", fontSize = 40.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(R.string.review_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            stringResource(R.string.review_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))
        StarRatingInput(stringResource(R.string.rating_taste_label), taste, { taste = it })
        Spacer(modifier = Modifier.height(16.dp))
        StarRatingInput(stringResource(R.string.rating_hygiene_label), hygiene, { hygiene = it })
        Spacer(modifier = Modifier.height(16.dp))
        StarRatingInput(stringResource(R.string.rating_packaging_label), packaging, { packaging = it })
        Spacer(modifier = Modifier.height(16.dp))
        StarRatingInput(stringResource(R.string.rating_on_time_label), onTime, { onTime = it })

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text(stringResource(R.string.review_comment_label)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))
        TiffinButton(
            onClick = {
                FirestoreReviewRepository.submitReview(
                    chefId = chefId,
                    taste = taste,
                    hygiene = hygiene,
                    packaging = packaging,
                    onTime = onTime,
                    comment = comment
                )
                onSubmitted()
            },
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.review_submit_button), fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSubmitted, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.review_skip_button))
        }
    }
}
