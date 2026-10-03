package com.ratulhossen.tiffinbox.util

/**
 * Normalizes a Bangladeshi phone number to "880XXXXXXXXXX" (country code,
 * no leading '+') for storage and for building the E.164 string Firebase
 * Phone Auth expects ("+880XXXXXXXXXX"). Returns null if the input doesn't
 * look like a valid BD mobile number.
 */
fun normalizeBangladeshPhone(input: String): String? {
    val digits = input.filter { it.isDigit() }
    return when {
        digits.length == 11 && digits.startsWith("0") -> "880" + digits.drop(1)
        digits.length == 13 && digits.startsWith("880") -> digits
        digits.length == 10 && !digits.startsWith("0") -> "880$digits"
        else -> null
    }
}
