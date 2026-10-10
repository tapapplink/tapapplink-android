package com.tapapplink.sdk

import java.util.Locale

/**
 * Normalises a promo code for pending-redeem requestId reuse:
 * uppercase, strip non-alphanumerics, max 24 characters.
 */
internal fun normalizeRedeemCode(code: String): String = code.uppercase(Locale.US).filter { it.isLetterOrDigit() }.take(24)
