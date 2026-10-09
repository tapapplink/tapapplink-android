package com.tapapplink.sdk

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Persists install attribution state across process death.
 */
internal class TapAppLinkStore(
  private val prefs: KeyValueStore,
) {
  fun installId(): String {
    val existing = prefs.getString(KEY_INSTALL_ID, null)
    if (!existing.isNullOrBlank()) return existing
    val created = UUID.randomUUID().toString()
    prefs.putString(KEY_INSTALL_ID, created)
    return created
  }

  var tracked: Boolean
    get() = prefs.getBoolean(KEY_TRACKED, false)
    set(value) = prefs.putBoolean(KEY_TRACKED, value)

  var attributionId: String?
    get() = prefs.getString(KEY_ATTRIBUTION_ID, null)?.takeIf { it.isNotBlank() }
    set(value) {
      if (value.isNullOrBlank()) {
        prefs.remove(KEY_ATTRIBUTION_ID)
      } else {
        prefs.putString(KEY_ATTRIBUTION_ID, value)
      }
    }

  var offer: TapAppLinkOffer?
    get() {
      val creatorName = prefs.getString(KEY_OFFER_CREATOR, null) ?: return null
      return TapAppLinkOffer(
        creatorName = creatorName,
        promoCode = prefs.getString(KEY_OFFER_PROMO, null)?.takeIf { it.isNotBlank() },
        billingOfferId = prefs.getString(KEY_OFFER_BILLING, null)?.takeIf { it.isNotBlank() },
      )
    }
    set(value) {
      if (value == null) {
        prefs.remove(KEY_OFFER_CREATOR)
        prefs.remove(KEY_OFFER_PROMO)
        prefs.remove(KEY_OFFER_BILLING)
        return
      }
      prefs.putString(KEY_OFFER_CREATOR, value.creatorName)
      if (value.promoCode.isNullOrBlank()) {
        prefs.remove(KEY_OFFER_PROMO)
      } else {
        prefs.putString(KEY_OFFER_PROMO, value.promoCode)
      }
      if (value.billingOfferId.isNullOrBlank()) {
        prefs.remove(KEY_OFFER_BILLING)
      } else {
        prefs.putString(KEY_OFFER_BILLING, value.billingOfferId)
      }
    }

  fun clear() {
    prefs.clear()
  }

  fun debugSnapshot(): String = "installId=${installId()} tracked=$tracked attributionId=$attributionId offer=$offer"

  companion object {
    internal const val PREFS_NAME = "tapapplink_sdk"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_TRACKED = "tracked"
    private const val KEY_ATTRIBUTION_ID = "attribution_id"
    private const val KEY_OFFER_CREATOR = "offer_creator_name"
    private const val KEY_OFFER_PROMO = "offer_promo_code"
    private const val KEY_OFFER_BILLING = "offer_billing_offer_id"

    fun from(context: Context): TapAppLinkStore = TapAppLinkStore(
      SharedPreferencesKeyValueStore(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
      ),
    )
  }
}

internal interface KeyValueStore {
  fun getString(key: String, default: String?): String?
  fun putString(key: String, value: String)
  fun getBoolean(key: String, default: Boolean): Boolean
  fun putBoolean(key: String, value: Boolean)
  fun remove(key: String)
  fun clear()
}

internal class SharedPreferencesKeyValueStore(
  private val prefs: SharedPreferences,
) : KeyValueStore {
  override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

  override fun putString(key: String, value: String) {
    prefs.edit().putString(key, value).apply()
  }

  override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

  override fun putBoolean(key: String, value: Boolean) {
    prefs.edit().putBoolean(key, value).apply()
  }

  override fun remove(key: String) {
    prefs.edit().remove(key).apply()
  }

  override fun clear() {
    prefs.edit().clear().apply()
  }
}
