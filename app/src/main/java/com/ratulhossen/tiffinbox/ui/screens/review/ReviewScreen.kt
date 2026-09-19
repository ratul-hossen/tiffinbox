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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.data.firebase.FirestoreReviewRepository
import com.ratulhossen.tiffinbox.ui.components.StarRatingInput

// The tracking/review demo flow doesn't carry a real order/chef id yet,
// so this matches the single chef ("Rukhsana's Kitchen") used throughout
// the rest of the dummy-data demo.
private const val DEMO_CHEF_ID = "chef1"

@Composable
fun ReviewScreen(onSubmitted: () -> Unit) {
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
        Text("খাবার কেমন লাগলো?", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Rukhsana's Kitchen — ৪টা আলাদা দিক থেকে রেটিং দিন",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))
        StarRatingInput("🍽️ স্বাদ", taste, { taste = it })
        Spacer(modifier = Modifier.height(16.dp))
        StarRatingInput("🧼 Hygiene", hygiene, { hygiene = it })
        Spacer(modifier = Modifier.height(16.dp))
        StarRatingInput("📦 Packaging", packaging, { packaging = it })
        Spacer(modifier = Modifier.height(16.dp))
        StarRatingInput("⏱️ On-Time", onTime, { onTime = it })

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("মন্তব্য (ঐচ্ছিক)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = {
                FirestoreReviewRepository.submitReview(
                    chefId = DEMO_CHEF_ID,
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
            Text("রিভিউ Submit করুন", fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSubmitted, modifier = Modifier.fillMaxWidth()) {
            Text("এখন না, Skip করুন")
        }
    }
}
