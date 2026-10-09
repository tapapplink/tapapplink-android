package com.tapapplink.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class TapAppLinkRedeemRequestIdTest {
  private lateinit var prefs: InMemoryKeyValueStore
  private lateinit var store: TapAppLinkStore

  @Before
  fun setUp() {
    TapAppLink.resetForTesting()
    prefs = InMemoryKeyValueStore()
    store = TapAppLinkStore(prefs)
    TapAppLink.setStoreForTesting(store)
    TapAppLink.configure(
      TapAppLinkConfig(
        publicKey = "etk_test_key_123456",
        environment = TapAppLinkEnvironment.SANDBOX,
        ingestUrl = "https://example.test",
      ),
    )
  }

  @Test
  fun normalizeRedeemCodeUppercasesStripsAndTruncates() {
    assertEquals("SARAH10", normalizeRedeemCode("sarah-10"))
    assertEquals("ABCDEFGHIJKLMNOPQRSTUVWX", normalizeRedeemCode("abcdefghijklmnopqrstuvwxyz"))
    assertEquals("AB12", normalizeRedeemCode(" ab_12!! "))
  }

  @Test
  fun retryAfterTimeoutReusesSameRequestId() {
    val seenIds = mutableListOf<String>()
    var calls = 0
    TapAppLink.setHttpClientForTesting { _, _, body ->
      val requestId = JSONObject(body).getString("requestId")
      seenIds.add(requestId)
      calls += 1
      if (calls == 1) {
        throw SocketTimeoutException("timeout")
      }
      TapAppLinkHttpResponse(
        200,
        """{"attributionId":"attr_1","alreadyAttributed":false,"offer":{"creatorName":"Sam","promoCode":"SAM","billingOfferId":"o1"}}""",
      )
    }

    val first = awaitApply("SAM")
    assertTrue(first.exceptionOrNull() is TapAppLinkRedeemException.Network)
    val second = awaitApply("SAM")
    assertTrue(second.isSuccess)

    assertEquals(2, seenIds.size)
    assertEquals(seenIds[0], seenIds[1])
    assertNull(store.pendingRedeemRequestId())
  }

  @Test
  fun restartWithPendingAttemptReusesRequestId() {
    val pendingId = store.resolveRedeemRequestId(normalizeRedeemCode("SARAH10"))
    assertEquals(pendingId, store.pendingRedeemRequestId())

    // Simulate process restart: drop the in-memory store without clearing prefs
    // (resetForTesting clears SharedPreferences and would wipe the pending id).
    TapAppLink.setStoreForTesting(null)
    val reloaded = TapAppLinkStore(prefs)
    TapAppLink.setStoreForTesting(reloaded)

    val seenId = AtomicReference<String>()
    TapAppLink.setHttpClientForTesting { _, _, body ->
      seenId.set(JSONObject(body).getString("requestId"))
      TapAppLinkHttpResponse(
        200,
        """{"attributionId":"attr_9","alreadyAttributed":false,"offer":{"creatorName":"Sarah","promoCode":"SARAH10","billingOfferId":"offer_1"}}""",
      )
    }

    val result = awaitApply("sarah-10")
    assertTrue(result.isSuccess)
    assertEquals(pendingId, seenId.get())
    assertNull(reloaded.pendingRedeemRequestId())
  }

  @Test
  fun differentCodeGetsNewRequestId() {
    val seenIds = mutableListOf<String>()
    TapAppLink.setHttpClientForTesting { _, _, body ->
      seenIds.add(JSONObject(body).getString("requestId"))
      throw IOException("offline")
    }

    awaitApply("CODEA")
    awaitApply("CODEB")

    assertEquals(2, seenIds.size)
    assertNotEquals(seenIds[0], seenIds[1])
    assertEquals(normalizeRedeemCode("CODEB"), store.pendingRedeemNormalizedCode())
    assertEquals(seenIds[1], store.pendingRedeemRequestId())
  }

  @Test
  fun definitiveUnknownCodeClearsPendingRequestId() {
    val seenIds = mutableListOf<String>()
    TapAppLink.setHttpClientForTesting { _, _, body ->
      seenIds.add(JSONObject(body).getString("requestId"))
      TapAppLinkHttpResponse(404, """{"error":"unknown_code"}""")
    }

    val first = awaitApply("NOPE")
    assertTrue(first.exceptionOrNull() is TapAppLinkRedeemException.UnknownCode)
    assertNull(store.pendingRedeemRequestId())

    val second = awaitApply("NOPE")
    assertTrue(second.exceptionOrNull() is TapAppLinkRedeemException.UnknownCode)
    assertEquals(2, seenIds.size)
    assertNotEquals(seenIds[0], seenIds[1])
  }

  @Test
  fun definitiveSuccessClearsPendingRequestId() {
    TapAppLink.setHttpClientForTesting { _, _, body ->
      JSONObject(body).getString("requestId")
      TapAppLinkHttpResponse(
        200,
        """{"attributionId":"attr_ok","alreadyAttributed":false,"offer":{"creatorName":"Ok","promoCode":"OK","billingOfferId":"o"}}""",
      )
    }
    assertTrue(awaitApply("OK").isSuccess)
    assertNull(store.pendingRedeemRequestId())
  }

  @Test
  fun sdkVersionHeaderIs032() {
    val headers = AtomicReference<Map<String, String>>()
    TapAppLink.setHttpClientForTesting { _, hdrs, _ ->
      headers.set(hdrs)
      TapAppLinkHttpResponse(200, """{"attributionId":"a"}""")
    }
    awaitApply("X")
    assertEquals("0.3.2", headers.get()["X-TapAppLink-SDK-Version"])
    assertEquals(TapAppLink.SDK_VERSION, headers.get()["X-TapAppLink-SDK-Version"])
  }

  private fun awaitApply(code: String): Result<JSONObject> {
    val latch = CountDownLatch(1)
    val held = AtomicReference<Result<JSONObject>>()
    TapAppLink.applyCode(code) { result: Result<JSONObject> ->
      held.set(result)
      latch.countDown()
    }
    assertTrue("timed out waiting for applyCode", latch.await(5, TimeUnit.SECONDS))
    return held.get()
  }
}
