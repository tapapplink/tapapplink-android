package com.tapapplink.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TapAppLinkApplyCodeTest {
  @Before
  fun setUp() {
    TapAppLink.resetForTesting()
    TapAppLink.setStoreForTesting(TapAppLinkStore(InMemoryKeyValueStore()))
    TapAppLink.configure(
      TapAppLinkConfig(
        publicKey = "etk_test_key_123456",
        environment = TapAppLinkEnvironment.SANDBOX,
        ingestUrl = "https://example.test",
      ),
    )
  }

  @Test
  fun applyCodeSuccessCachesOfferAndAttribution() {
    val seenHeaders = AtomicReference<Map<String, String>>()
    TapAppLink.setHttpClientForTesting { _, headers, _ ->
      seenHeaders.set(headers)
      TapAppLinkHttpResponse(
        200,
        """{"attributionId":"attr_9","alreadyAttributed":false,"offer":{"creatorName":"Sarah","promoCode":"SARAH10","billingOfferId":"offer_1"}}""",
      )
    }

    val result = awaitApply("SARAH10")
    assertTrue(result.isSuccess)
    val body = result.getOrThrow()
    assertEquals("attr_9", body.getString("attributionId"))
    assertFalse(body.getBoolean("alreadyAttributed"))
    assertEquals("attr_9", TapAppLink.getAttributionId())
    assertEquals("Sarah", TapAppLink.getOffer()?.creatorName)
    assertEquals("0.3.1", seenHeaders.get()["X-TapAppLink-SDK-Version"])
    assertEquals(TapAppLink.SDK_VERSION, seenHeaders.get()["X-TapAppLink-SDK-Version"])
  }

  @Test
  fun applyCodeAlreadyAttributedSuccess() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      TapAppLinkHttpResponse(
        200,
        """{"attributionId":"attr_1","alreadyAttributed":true,"offer":{"creatorName":"Alex","promoCode":null,"billingOfferId":"b1"}}""",
      )
    }

    val result = awaitApply("ALEX")
    assertTrue(result.isSuccess)
    assertTrue(result.getOrThrow().getBoolean("alreadyAttributed"))
  }

  @Test
  fun applyCodeUnknownCodeFromBody() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      TapAppLinkHttpResponse(404, """{"error":"unknown_code"}""")
    }
    val result = awaitApply("NOPE")
    assertTrue(result.exceptionOrNull() is TapAppLinkRedeemException.UnknownCode)
  }

  @Test
  fun applyCodeInactiveCodeFrom410() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      TapAppLinkHttpResponse(410, """{"error":"inactive_code"}""")
    }
    val result = awaitApply("OLD")
    assertTrue(result.exceptionOrNull() is TapAppLinkRedeemException.InactiveCode)
  }

  @Test
  fun applyCodeWrongEnvironmentFrom400() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      TapAppLinkHttpResponse(400, """{"error":"wrong_environment"}""")
    }
    val result = awaitApply("LIVEONLY")
    assertTrue(result.exceptionOrNull() is TapAppLinkRedeemException.WrongEnvironment)
  }

  @Test
  fun applyCodeLegacy404WithBodyError() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      TapAppLinkHttpResponse(404, """{"error":"inactive_code","message":"gone"}""")
    }
    val result = awaitApply("LEGACY")
    assertTrue(result.exceptionOrNull() is TapAppLinkRedeemException.InactiveCode)
  }

  @Test
  fun applyCodeLegacy404WithoutBodyErrorIsUnknown() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      TapAppLinkHttpResponse(404, """{"message":"missing"}""")
    }
    val result = awaitApply("MISSING")
    assertTrue(result.exceptionOrNull() is TapAppLinkRedeemException.UnknownCode)
  }

  @Test
  fun applyCodeNetworkFailure() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      throw IOException("offline")
    }
    val result = awaitApply("SARAH10")
    assertTrue(result.exceptionOrNull() is TapAppLinkRedeemException.Network)
  }

  @Test
  fun applyCodeOtherFailure() {
    TapAppLink.setHttpClientForTesting { _, _, _ ->
      TapAppLinkHttpResponse(502, """{"message":"bad gateway"}""")
    }
    val result = awaitApply("SARAH10")
    val error = result.exceptionOrNull()
    assertTrue(error is TapAppLinkRedeemException.Other)
    assertEquals(502, (error as TapAppLinkRedeemException.Other).status)
  }

  @Test
  fun nonRedeemNon2xxDoesNotReturnBodyAsSuccess() {
    try {
      requireSuccessBody("/ingestInstall", TapAppLinkHttpResponse(404, """{"error":"unknown_code"}"""))
      throw AssertionError("expected failure")
    } catch (error: IOException) {
      assertTrue(error.message!!.contains("404"))
    }
  }

  private fun awaitApply(code: String): Result<JSONObject> {
    val latch = CountDownLatch(1)
    val held = AtomicReference<Result<JSONObject>>()
    TapAppLink.applyCode(code) { result ->
      held.set(result)
      latch.countDown()
    }
    assertTrue("timed out waiting for applyCode", latch.await(5, TimeUnit.SECONDS))
    return held.get()
  }
}
