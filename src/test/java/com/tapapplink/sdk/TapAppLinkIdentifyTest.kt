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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TapAppLinkIdentifyTest {
  private lateinit var prefs: InMemoryKeyValueStore

  @Before
  fun setUp() {
    TapAppLink.resetForTesting()
    prefs = InMemoryKeyValueStore()
    val store = TapAppLinkStore(prefs)
    store.tracked = true
    store.attributionId = "attr_id"
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
  fun skipsIngestIdentifyWhenAppUserIdMatchesLastSuccessfulIdentify() {
    val identifyCalls = AtomicInteger(0)
    TapAppLink.setHttpClientForTesting { url, _, _ ->
      if (url.contains("/ingestIdentify")) {
        identifyCalls.incrementAndGet()
        TapAppLinkHttpResponse(200, """{"ok":true}""")
      } else {
        TapAppLinkHttpResponse(200, "{}")
      }
    }

    val first = awaitIdentify("user_same")
    val second = awaitIdentify("user_same")

    assertEquals(true, first.optBoolean("ok"))
    assertTrue(second.getBoolean("skipped"))
    assertEquals("user_same", second.getString("appUserId"))
    assertEquals("attr_id", second.getString("attributionId"))
    assertEquals(1, identifyCalls.get())
    assertEquals("user_same", TapAppLinkStore(prefs).appUserId)
  }

  @Test
  fun doesNotPostIngestIdentifyAgainAfterColdStartWithSameAppUserId() {
    val identifyCalls = AtomicInteger(0)
    val mockClient = TapAppLinkHttpClient { url, _, _ ->
      if (url.contains("/ingestIdentify")) {
        identifyCalls.incrementAndGet()
        TapAppLinkHttpResponse(200, """{"ok":true}""")
      } else {
        TapAppLinkHttpResponse(200, "{}")
      }
    }
    TapAppLink.setHttpClientForTesting(mockClient)

    awaitIdentify("user_persisted")
    assertEquals(1, identifyCalls.get())

    // Simulate process death: new store instance on prefs that kept appUserId.
    val coldPrefs = InMemoryKeyValueStore()
    coldPrefs.putString("install_id", "install_cold")
    coldPrefs.putBoolean("tracked", true)
    coldPrefs.putString("attribution_id", "attr_cold_id")
    coldPrefs.putString("app_user_id", "user_persisted")
    TapAppLink.resetForTesting()
    TapAppLink.setStoreForTesting(TapAppLinkStore(coldPrefs))
    TapAppLink.setHttpClientForTesting(mockClient)
    TapAppLink.configure(
      TapAppLinkConfig(
        publicKey = "etk_test_key_123456",
        environment = TapAppLinkEnvironment.SANDBOX,
        ingestUrl = "https://example.test",
      ),
    )

    val resumed = awaitIdentify("user_persisted")

    assertEquals(1, identifyCalls.get())
    assertTrue(resumed.getBoolean("skipped"))
    assertEquals("user_persisted", resumed.getString("appUserId"))
    assertEquals("attr_cold_id", resumed.getString("attributionId"))
    assertEquals("user_persisted", TapAppLink.getAppUserId())
  }

  @Test
  fun coalescesConcurrentIdenticalSetAppUserIdCallsIntoOnePost() {
    val identifyGate = CountDownLatch(1)
    val identifyStarted = CountDownLatch(1)
    val identifyCalls = AtomicInteger(0)
    TapAppLink.setHttpClientForTesting { url, _, _ ->
      if (url.contains("/ingestIdentify")) {
        identifyCalls.incrementAndGet()
        identifyStarted.countDown()
        assertTrue(identifyGate.await(5, TimeUnit.SECONDS))
        TapAppLinkHttpResponse(200, """{"ok":true}""")
      } else {
        TapAppLinkHttpResponse(200, "{}")
      }
    }

    val latch = CountDownLatch(3)
    val results = arrayOfNulls<JSONObject>(3)
    TapAppLink.setAppUserId("user_race") {
      results[0] = it
      latch.countDown()
    }
    // Wait until the first POST is in flight so A is registered before B/C join.
    assertTrue(identifyStarted.await(5, TimeUnit.SECONDS))
    TapAppLink.setAppUserId("user_race") {
      results[1] = it
      latch.countDown()
    }
    TapAppLink.setAppUserId("user_race") {
      results[2] = it
      latch.countDown()
    }
    identifyGate.countDown()
    assertTrue("timed out waiting for coalesced identify", latch.await(5, TimeUnit.SECONDS))

    assertEquals(1, identifyCalls.get())
    assertEquals(results[0].toString(), results[1].toString())
    assertEquals(results[1].toString(), results[2].toString())
    assertEquals(true, results[0]!!.optBoolean("ok"))
  }

  @Test
  fun postsIngestIdentifyAgainWhenAppUserIdChanges() {
    val identifyBodies = mutableListOf<String>()
    TapAppLink.setHttpClientForTesting { url, _, body ->
      if (url.contains("/ingestIdentify")) {
        identifyBodies.add(body)
        TapAppLinkHttpResponse(200, """{"ok":true}""")
      } else {
        TapAppLinkHttpResponse(200, "{}")
      }
    }

    awaitIdentify("user_a")
    awaitIdentify("user_b")

    assertEquals(2, identifyBodies.size)
    assertEquals("user_a", JSONObject(identifyBodies[0]).getString("appUserId"))
    assertEquals("user_b", JSONObject(identifyBodies[1]).getString("appUserId"))
    assertEquals("user_b", TapAppLink.getAppUserId())
    assertEquals("user_b", TapAppLinkStore(prefs).appUserId)
  }

  @Test
  fun doesNotSkipIdentifyAfterFailedIngestIdentify() {
    val identifyCalls = AtomicInteger(0)
    TapAppLink.setHttpClientForTesting { url, _, _ ->
      if (url.contains("/ingestIdentify")) {
        val n = identifyCalls.incrementAndGet()
        if (n == 1) {
          throw IOException("identify failed")
        }
        TapAppLinkHttpResponse(200, """{"ok":true}""")
      } else {
        TapAppLinkHttpResponse(200, "{}")
      }
    }

    val failedLatch = CountDownLatch(1)
    TapAppLink.setAppUserId("user_retry") { failedLatch.countDown() }
    assertFalse(
      "failed identify must not invoke callback or persist",
      failedLatch.await(400, TimeUnit.MILLISECONDS),
    )
    assertEquals(null, TapAppLinkStore(prefs).appUserId)

    val retry = awaitIdentify("user_retry")
    assertEquals(true, retry.optBoolean("ok"))
    assertEquals(2, identifyCalls.get())
    assertEquals("user_retry", TapAppLinkStore(prefs).appUserId)
  }

  private fun awaitIdentify(appUserId: String): JSONObject {
    val latch = CountDownLatch(1)
    val held = AtomicReference<JSONObject>()
    TapAppLink.setAppUserId(appUserId) { result ->
      held.set(result)
      latch.countDown()
    }
    assertTrue("timed out waiting for setAppUserId", latch.await(5, TimeUnit.SECONDS))
    return held.get()
  }
}
