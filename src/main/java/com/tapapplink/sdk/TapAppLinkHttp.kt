package com.tapapplink.sdk

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class TapAppLinkHttpResponse(
  val status: Int,
  val body: String,
)

internal fun interface TapAppLinkHttpClient {
  fun post(
    url: String,
    headers: Map<String, String>,
    body: String,
  ): TapAppLinkHttpResponse
}

internal object DefaultTapAppLinkHttpClient : TapAppLinkHttpClient {
  override fun post(
    url: String,
    headers: Map<String, String>,
    body: String,
  ): TapAppLinkHttpResponse {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      headers.forEach { (key, value) -> setRequestProperty(key, value) }
      doOutput = true
      connectTimeout = 15_000
      readTimeout = 15_000
    }
    connection.outputStream.use { it.write(body.toByteArray()) }
    val status = connection.responseCode
    val stream =
      if (status in 200..299) connection.inputStream else connection.errorStream
    val text = stream?.bufferedReader()?.readText().orEmpty()
    return TapAppLinkHttpResponse(status = status, body = text)
  }
}

/**
 * Parses a non-2xx redeem response into a typed [TapAppLinkRedeemException].
 *
 * Prefers the JSON `error` field, then falls back to HTTP status.
 */
internal fun mapRedeemFailure(
  status: Int,
  bodyText: String,
): TapAppLinkRedeemException {
  val body = bodyText.trim().takeIf { it.isNotEmpty() }?.let {
    runCatching { JSONObject(it) }.getOrNull()
  }
  val errorCode = body?.optString("error")?.takeIf { it.isNotBlank() }
  val message = body?.optString("message")?.takeIf { it.isNotBlank() }
    ?: bodyText.takeIf { it.isNotBlank() }
    ?: "HTTP $status"

  // Prefer the body `error` field; fall back to status (404 unknown, 410 inactive).
  // A 400 with `wrong_environment` is handled by the body field above.
  return when (errorCode) {
    "unknown_code" -> TapAppLinkRedeemException.UnknownCode()
    "inactive_code" -> TapAppLinkRedeemException.InactiveCode()
    "wrong_environment" -> TapAppLinkRedeemException.WrongEnvironment()
    else -> when (status) {
      404 -> TapAppLinkRedeemException.UnknownCode()
      410 -> TapAppLinkRedeemException.InactiveCode()
      else -> TapAppLinkRedeemException.Other(status, message)
    }
  }
}

internal fun mapNetworkFailure(cause: Throwable): TapAppLinkRedeemException {
  // Timeouts, DNS failures, and other IO errors all surface as Network.
  if (cause is TapAppLinkRedeemException) return cause
  return TapAppLinkRedeemException.Network(cause)
}

/**
 * Throws when [response] is not 2xx. Never returns an error body as success.
 */
internal fun requireSuccessBody(
  path: String,
  response: TapAppLinkHttpResponse,
): JSONObject {
  if (response.status !in 200..299) {
    if (path == "/redeemCode") {
      throw mapRedeemFailure(response.status, response.body)
    }
    val message = response.body.trim().ifEmpty { "HTTP ${response.status}" }
    throw IOException("TapAppLink request $path failed with HTTP ${response.status}: $message")
  }
  return if (response.body.isBlank()) JSONObject() else JSONObject(response.body)
}
