package com.tapapplink.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TapAppLinkRedeemExceptionTest {
  @Test
  fun mapsErrorFieldUnknownCode() {
    val error = mapRedeemFailure(404, """{"error":"unknown_code"}""")
    assertTrue(error is TapAppLinkRedeemException.UnknownCode)
  }

  @Test
  fun mapsErrorFieldInactiveCode() {
    val error = mapRedeemFailure(410, """{"error":"inactive_code"}""")
    assertTrue(error is TapAppLinkRedeemException.InactiveCode)
  }

  @Test
  fun mapsErrorFieldWrongEnvironment() {
    val error = mapRedeemFailure(400, """{"error":"wrong_environment"}""")
    assertTrue(error is TapAppLinkRedeemException.WrongEnvironment)
  }

  @Test
  fun prefersErrorFieldOverStatusOnLegacy404() {
    val inactive = mapRedeemFailure(404, """{"error":"inactive_code"}""")
    assertTrue(inactive is TapAppLinkRedeemException.InactiveCode)

    val wrong = mapRedeemFailure(404, """{"error":"wrong_environment"}""")
    assertTrue(wrong is TapAppLinkRedeemException.WrongEnvironment)
  }

  @Test
  fun legacy404WithoutErrorFieldIsUnknownCode() {
    val error = mapRedeemFailure(404, """{"message":"not found"}""")
    assertTrue(error is TapAppLinkRedeemException.UnknownCode)
  }

  @Test
  fun status410FallbackIsInactiveCode() {
    val error = mapRedeemFailure(410, "{}")
    assertTrue(error is TapAppLinkRedeemException.InactiveCode)
  }

  @Test
  fun status400WithWrongEnvironmentIsWrongEnvironment() {
    val error = mapRedeemFailure(400, """{"error":"wrong_environment","message":"sandbox"}""")
    assertTrue(error is TapAppLinkRedeemException.WrongEnvironment)
  }

  @Test
  fun status400WithoutKnownErrorIsOther() {
    val error = mapRedeemFailure(400, """{"message":"bad request"}""")
    assertTrue(error is TapAppLinkRedeemException.Other)
    val other = error as TapAppLinkRedeemException.Other
    assertEquals(400, other.status)
    assertEquals("bad request", other.message)
  }

  @Test
  fun otherStatusesBecomeOther() {
    val error = mapRedeemFailure(500, """{"message":"boom"}""")
    assertTrue(error is TapAppLinkRedeemException.Other)
    assertEquals(500, (error as TapAppLinkRedeemException.Other).status)
  }

  @Test
  fun emptyBodyUsesHttpStatusMessageForOther() {
    val error = mapRedeemFailure(503, "")
    assertTrue(error is TapAppLinkRedeemException.Other)
    assertEquals("HTTP 503", error.message)
  }

  @Test
  fun wrongEnvironmentDeveloperWarningExactText() {
    assertEquals(
      "This code belongs to the other environment (Sandbox or Production). Check your API key.",
      TapAppLinkRedeemException.WrongEnvironment.DEVELOPER_WARNING,
    )
  }

  @Test
  fun mapNetworkFailureWrapsTimeoutsAndIo() {
    assertTrue(mapNetworkFailure(SocketTimeoutException("t")) is TapAppLinkRedeemException.Network)
    assertTrue(mapNetworkFailure(UnknownHostException("h")) is TapAppLinkRedeemException.Network)
    assertTrue(mapNetworkFailure(IOException("io")) is TapAppLinkRedeemException.Network)
  }

  @Test
  fun mapNetworkFailurePassesThroughRedeemExceptions() {
    val original = TapAppLinkRedeemException.UnknownCode()
    assertTrue(mapNetworkFailure(original) === original)
  }

  @Test
  fun requireSuccessBodyReturnsJsonOn2xx() {
    val json = requireSuccessBody("/ingestInstall", TapAppLinkHttpResponse(200, """{"ok":true}"""))
    assertTrue(json.getBoolean("ok"))
  }

  @Test
  fun requireSuccessBodyNeverReturnsErrorBodyAsSuccess() {
    try {
      requireSuccessBody("/ingestInstall", TapAppLinkHttpResponse(500, """{"error":"nope"}"""))
      throw AssertionError("expected IOException")
    } catch (error: IOException) {
      assertTrue(error.message!!.contains("500"))
    }
  }

  @Test
  fun requireSuccessBodyMapsRedeemFailures() {
    try {
      requireSuccessBody(
        "/redeemCode",
        TapAppLinkHttpResponse(404, """{"error":"unknown_code"}"""),
      )
      throw AssertionError("expected UnknownCode")
    } catch (error: TapAppLinkRedeemException.UnknownCode) {
      // expected
    }
  }
}
