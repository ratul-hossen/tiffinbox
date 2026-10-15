package com.ratulhossen.tiffinbox.ui.screens.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ratulhossen.tiffinbox.R

@Composable
fun TermsScreen(onBack: () -> Unit) {
    StaticInfoScreen(title = stringResource(R.string.terms_title), onBack = onBack) {
        Text(stringResource(R.string.terms_body))
    }
}
