package com.ratulhossen.tiffinbox.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ratulhossen.tiffinbox.R

/**
 * Hind Siliguri (SIL Open Font License, see /licenses/HIND_SILIGURI_OFL.txt)
 * - a proper dual-script Bengali+Latin Google Font, used here instead of the
 * system default (Roboto) because Roboto's Bengali glyph coverage/shaping is
 * weak. Bundled as merged bengali+latin+latin-ext subsets per weight so a
 * single TextStyle renders both scripts cleanly without a fallback-font seam.
 */
val HindSiliguri = FontFamily(
    Font(R.font.hind_siliguri_light, FontWeight.Light),
    Font(R.font.hind_siliguri_regular, FontWeight.Normal),
    Font(R.font.hind_siliguri_medium, FontWeight.Medium),
    Font(R.font.hind_siliguri_semibold, FontWeight.SemiBold),
    Font(R.font.hind_siliguri_bold, FontWeight.Bold)
)
