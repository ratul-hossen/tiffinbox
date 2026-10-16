package com.ratulhossen.tiffinbox.ui.screens.auth

import androidx.annotation.StringRes
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.util.normalizeBangladeshPhone

/** Normalized contact numbers collected at signup, ready to write to Firestore. */
data class SignupContacts(
    val phoneNumber: String,
    val whatsappNumber: String,
    val bkashNumber: String,
    val nagadNumber: String
)

sealed class SignupContactResult {
    data class Valid(val contacts: SignupContacts) : SignupContactResult()
    data class Invalid(@StringRes val messageRes: Int) : SignupContactResult()
}

/**
 * Shared by the email/password signup ([LoginScreen]) and the Google signup
 * path ([CompleteGoogleProfileScreen]) so both enforce the same rules:
 * phone + WhatsApp numbers are required for everyone, and a chef must also
 * give at least one of bKash / Nagad (to receive payments). There is no SMS
 * OTP step - Firebase Phone Auth SMS needs the paid Blaze plan, and this
 * project stays on the free Spark plan - so these numbers are format-checked
 * only; chefs are still gated by admin approval (isVerified) before going live.
 */
fun validateSignupContacts(
    isChef: Boolean,
    phoneNumber: String,
    whatsappNumber: String,
    bkashNumber: String,
    nagadNumber: String
): SignupContactResult {
    if (phoneNumber.isBlank()) return SignupContactResult.Invalid(R.string.login_phone_number_required)
    val phone = normalizeBangladeshPhone(phoneNumber)
        ?: return SignupContactResult.Invalid(R.string.login_phone_number_invalid)

    if (whatsappNumber.isBlank()) return SignupContactResult.Invalid(R.string.login_whatsapp_number_required)
    val whatsapp = normalizeBangladeshPhone(whatsappNumber)
        ?: return SignupContactResult.Invalid(R.string.login_whatsapp_number_invalid)

    var bkash = ""
    var nagad = ""
    if (isChef) {
        if (bkashNumber.isBlank() && nagadNumber.isBlank()) {
            return SignupContactResult.Invalid(R.string.login_bkash_number_required)
        }
        if (bkashNumber.isNotBlank()) {
            if (normalizeBangladeshPhone(bkashNumber) == null) {
                return SignupContactResult.Invalid(R.string.login_payment_number_invalid)
            }
            bkash = bkashNumber.trim()
        }
        if (nagadNumber.isNotBlank()) {
            if (normalizeBangladeshPhone(nagadNumber) == null) {
                return SignupContactResult.Invalid(R.string.login_payment_number_invalid)
            }
            nagad = nagadNumber.trim()
        }
    }
    return SignupContactResult.Valid(SignupContacts(phone, whatsapp, bkash, nagad))
}
