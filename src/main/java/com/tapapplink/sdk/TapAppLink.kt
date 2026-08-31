package com.tapapplink.sdk

import android.content.Context
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
)

data class TapAppLinkOffer(
  val creatorName: String,
  val promoCode: String?,
  val discountBps: Int,
  val billingOfferId: String?,
)

object TapAppLink {
  private var config: TapAppLinkConfig? = null
  private var tracked = false
  private var lastAttributionId: String? = null
  private var lastAppUserId: String? = null
  private var lastOffer: TapAppLinkOffer? = null
  private val io = Executors.newSingleThreadExecutor()

  @JvmStatic
  fun configure(next: TapAppLinkConfig) {
    config = next
  }

  @JvmStatic
  fun trackInstall(
    context: Context,
    installReferrer: String? = null,
    callback: (JSONObject) -> Unit,
  ) {
    io.execute {
      if (tracked) {
        callback(JSONObject().put("matched", false).put("skipped", true))
        return@execute
      }
      val cfg = requireConfig()
      val body = JSONObject()
        .put("platform", "ANDROID")
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
      installReferrer?.let { body.put("installReferrer", it) }
      val result = post(cfg, "/ingestInstall", body)
      tracked = true
      cacheFromResult(result)
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
      lastAttributionId?.let { body.put("attributionId", it) }
      val result = post(cfg, "/redeemCode", body)
      cacheFromResult(result)
      callback(result)
    }
  }

  @JvmStatic
  fun getOffer(): TapAppLinkOffer? = lastOffer

  @JvmStatic
  fun getAttributionId(): String? = lastAttributionId

  @JvmStatic
  fun getAppUserId(): String? = lastAppUserId

  @JvmStatic
  fun linkRevenueCatUser(appUserId: String, callback: (JSONObject) -> Unit) =
    setAppUserId(appUserId, callback)

  @JvmStatic
  fun linkAdaptyUser(customerUserId: String, callback: (JSONObject) -> Unit) =
    setAppUserId(customerUserId, callback)

  @JvmStatic
  fun linkSuperwallUser(appUserId: String, callback: (JSONObject) -> Unit) =
    setAppUserId(appUserId, callback)

  @JvmStatic
  fun linkQonversionUser(userId: String, callback: (JSONObject) -> Unit) =
    setAppUserId(userId, callback)

  @JvmStatic
  fun resetForTesting() {
    tracked = false
    lastAttributionId = null
    lastAppUserId = null
    lastOffer = null
  }

  private fun cacheFromResult(result: JSONObject) {
    lastAttributionId = result.optString("attributionId").takeIf { it.isNotBlank() }
    val offer = result.optJSONObject("offer") ?: return
    lastOffer = TapAppLinkOffer(
      creatorName = offer.optString("creatorName"),
      promoCode = offer.optString("promoCode").takeIf { it.isNotBlank() },
      discountBps = offer.optInt("discountBps"),
      billingOfferId = offer.optString("billingOfferId").takeIf { it.isNotBlank() },
    )
  }

  private fun identify(appUserId: String): JSONObject {
    val cfg = requireConfig()
    val body = JSONObject().put("appUserId", appUserId)
    lastAttributionId?.let { body.put("attributionId", it) }
    return post(cfg, "/ingestIdentify", body)
  }

  private fun requireConfig(): TapAppLinkConfig =
    config ?: throw IllegalStateException("TapAppLink.configure() must be called first")

  private fun isoNow(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date())
  }

  private fun post(cfg: TapAppLinkConfig, path: String, body: JSONObject): JSONObject {
    val base = (cfg.ingestUrl ?: "https://us-central1-tapapplink.cloudfunctions.net")
      .trimEnd('/')
    val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
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
    return if (text.isBlank()) JSONObject() else JSONObject(text)
  }
}
