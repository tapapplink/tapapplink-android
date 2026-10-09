package com.tapapplink.sdk

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

enum class TapAppLinkEnvironment(val value: String) {
  PRODUCTION("production"),
  SANDBOX("sandbox"),
}

data class TapAppLinkConfig(
  val publicKey: String,
  val environment: TapAppLinkEnvironment,
  val ingestUrl: String? = null,
  val debugLogging: Boolean = false,
)

data class TapAppLinkOffer(
  val creatorName: String,
  val promoCode: String?,
  val billingOfferId: String?,
)

object TapAppLink {
  private const val TAG = "TapAppLink"

  private var config: TapAppLinkConfig? = null
  private var store: TapAppLinkStore? = null
  private var lastAppUserId: String? = null
  private var referrerReader: ReferrerReader = PlayInstallReferrerReader()
  private val io = Executors.newSingleThreadExecutor()

  @JvmStatic
  fun configure(next: TapAppLinkConfig) {
    config = next
    debugLog("configure environment=${next.environment.value} debugLogging=${next.debugLogging}")
  }

  @JvmStatic
  fun trackInstall(
    context: Context,
    installReferrer: String? = null,
    callback: (JSONObject) -> Unit,
  ) {
    // Hydrate prefs on the caller thread so getOffer / setAppUserId see stored state
    // immediately after trackInstall returns on later launches.
    val persisted = ensureStore(context)
    io.execute {
      if (persisted.tracked) {
        val skipped = skippedResult(persisted)
        debugLog("trackInstall skipped; ${persisted.debugSnapshot()}")
        callback(skipped)
        return@execute
      }
      val cfg = requireConfig()
      val installId = persisted.installId()
      val referrer = installReferrer?.takeIf { it.isNotBlank() }
        ?: referrerReader.read(context, PlayInstallReferrerReader.DEFAULT_TIMEOUT_MS)
      val body = JSONObject()
        .put("platform", "ANDROID")
        .put("installId", installId)
        .put(
          "deviceFamily",
          if (context.resources.configuration.smallestScreenWidthDp >= 600) {
            "Android Tablet"
          } else {
            "Android"
          },
        )
        .put("locale", Locale.getDefault().toLanguageTag())
        .put("networkContext", Locale.getDefault().country.ifEmpty { "unknown" })
        .put("firstOpenAt", isoNow())
      referrer?.takeIf { it.isNotBlank() }?.let { body.put("installReferrer", it) }
      debugLog("trackInstall posting installId=$installId hasReferrer=${body.has("installReferrer")}")
      val result = post(cfg, "/ingestInstall", body)
      persisted.tracked = true
      cacheFromResult(persisted, result)
      debugLog("trackInstall response=$result; ${persisted.debugSnapshot()}")
      callback(result)
    }
  }

  @JvmStatic
  fun setAppUserId(appUserId: String, callback: (JSONObject) -> Unit) {
    lastAppUserId = appUserId
    io.execute { callback(identify(appUserId)) }
  }

  @JvmStatic
  fun applyCode(code: String, callback: (JSONObject) -> Unit) {
    io.execute {
      val cfg = requireConfig()
      val body = JSONObject().put("code", code).put("platform", "ANDROID")
      lastAppUserId?.let { body.put("appUserId", it) }
      store?.attributionId?.let { body.put("attributionId", it) }
      val result = post(cfg, "/redeemCode", body)
      store?.let { cacheFromResult(it, result) }
      callback(result)
    }
  }

  @JvmStatic
  fun getOffer(): TapAppLinkOffer? = store?.offer

  @JvmStatic
  fun getAttributionId(): String? = store?.attributionId

  @JvmStatic
  fun getAppUserId(): String? = lastAppUserId

  @JvmStatic
  fun getInstallId(): String? = store?.installId()

  @JvmStatic
  fun linkRevenueCatUser(appUserId: String, callback: (JSONObject) -> Unit) = setAppUserId(appUserId, callback)

  @JvmStatic
  fun linkAdaptyUser(customerUserId: String, callback: (JSONObject) -> Unit) = setAppUserId(customerUserId, callback)

  @JvmStatic
  fun linkSuperwallUser(appUserId: String, callback: (JSONObject) -> Unit) = setAppUserId(appUserId, callback)

  @JvmStatic
  fun linkQonversionUser(userId: String, callback: (JSONObject) -> Unit) = setAppUserId(userId, callback)

  /**
   * Clears in-memory and persisted install state. Pass [context] so SharedPreferences
   * are cleared even when [trackInstall] has not run in this process.
   */
  @JvmStatic
  @JvmOverloads
  fun resetForTesting(context: Context? = null) {
    lastAppUserId = null
    if (context != null) {
      ensureStore(context).clear()
    } else {
      store?.clear()
    }
    store = null
    debugLog("resetForTesting cleared local state")
  }

  internal fun setReferrerReaderForTesting(reader: ReferrerReader) {
    referrerReader = reader
  }

  internal fun setStoreForTesting(next: TapAppLinkStore?) {
    store = next
  }

  private fun ensureStore(context: Context): TapAppLinkStore {
    val existing = store
    if (existing != null) return existing
    return TapAppLinkStore.from(context).also { store = it }
  }

  private fun skippedResult(persisted: TapAppLinkStore): JSONObject {
    val result = JSONObject()
      .put("matched", false)
      .put("skipped", true)
    persisted.attributionId?.let { result.put("attributionId", it) }
    persisted.offer?.let { offer ->
      result.put(
        "offer",
        JSONObject()
          .put("creatorName", offer.creatorName)
          .put("promoCode", offer.promoCode ?: JSONObject.NULL)
          .put("billingOfferId", offer.billingOfferId ?: JSONObject.NULL),
      )
    }
    return result
  }

  private fun cacheFromResult(persisted: TapAppLinkStore, result: JSONObject) {
    result.optString("attributionId").takeIf { it.isNotBlank() }?.let {
      persisted.attributionId = it
    }
    val offerJson = result.optJSONObject("offer") ?: return
    persisted.offer = TapAppLinkOffer(
      creatorName = offerJson.optString("creatorName"),
      promoCode = offerJson.optString("promoCode").takeIf { it.isNotBlank() },
      billingOfferId = offerJson.optString("billingOfferId").takeIf { it.isNotBlank() },
    )
  }

  private fun identify(appUserId: String): JSONObject {
    val cfg = requireConfig()
    val body = JSONObject().put("appUserId", appUserId)
    store?.attributionId?.let { body.put("attributionId", it) }
    debugLog("ingestIdentify attributionId=${store?.attributionId}")
    return post(cfg, "/ingestIdentify", body)
  }

  private fun requireConfig(): TapAppLinkConfig = config ?: throw IllegalStateException("TapAppLink.configure() must be called first")

  private fun isoNow(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
  }

  private fun post(cfg: TapAppLinkConfig, path: String, body: JSONObject): JSONObject {
    val base = (cfg.ingestUrl ?: "https://us-central1-tapapplink.cloudfunctions.net")
      .trimEnd('/')
    val url = "$base$path"
    debugLog("POST $url body=$body auth=${redactKey(cfg.publicKey)}")
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      setRequestProperty("Authorization", "Bearer ${cfg.publicKey}")
      setRequestProperty("Content-Type", "application/json")
      doOutput = true
      connectTimeout = 15_000
      readTimeout = 15_000
    }
    connection.outputStream.use { it.write(body.toString().toByteArray()) }
    val stream =
      if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
    val text = stream?.bufferedReader()?.readText().orEmpty()
    debugLog("POST $path status=${connection.responseCode} response=$text")
    return if (text.isBlank()) JSONObject() else JSONObject(text)
  }

  private fun debugLog(message: String) {
    if (config?.debugLogging == true) {
      Log.d(TAG, message)
    }
  }

  internal fun redactKey(key: String): String {
    if (key.length <= 8) return "***"
    return "${key.take(4)}...${key.takeLast(4)}"
  }
}
