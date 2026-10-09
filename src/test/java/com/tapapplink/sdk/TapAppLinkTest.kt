package com.tapapplink.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TapAppLinkTest {
  @Before
  fun setUp() {
    TapAppLink.resetForTesting()
    TapAppLink.setStoreForTesting(null)
    TapAppLink.setReferrerReaderForTesting(ReferrerReader { _, _ -> null })
  }

  @Test
  fun configureLeavesOfferAndIdsUnset() {
    TapAppLink.configure(
      TapAppLinkConfig(
        publicKey = "etk_test",
        environment = TapAppLinkEnvironment.SANDBOX,
      ),
    )
    assertNull(TapAppLink.getOffer())
    assertNull(TapAppLink.getAttributionId())
    assertNull(TapAppLink.getAppUserId())
  }

  @Test
  fun resetForTestingClearsCachedState() {
    val prefs = InMemoryKeyValueStore()
    val store = TapAppLinkStore(prefs)
    store.tracked = true
    store.attributionId = "attr_1"
    store.offer = TapAppLinkOffer("Sarah", "SARAH10", "offer_1")
    TapAppLink.setStoreForTesting(store)

    TapAppLink.configure(
      TapAppLinkConfig(
        publicKey = "etk_test",
        environment = TapAppLinkEnvironment.PRODUCTION,
      ),
    )
    TapAppLink.resetForTesting()

    assertNull(TapAppLink.getOffer())
    assertNull(TapAppLink.getAttributionId())
    assertNull(TapAppLink.getAppUserId())
    assertNull(TapAppLink.getInstallId())
  }

  @Test
  fun environmentValuesMatchApi() {
    assertEquals("production", TapAppLinkEnvironment.PRODUCTION.value)
    assertEquals("sandbox", TapAppLinkEnvironment.SANDBOX.value)
  }

  @Test
  fun offerHoldsBillingFields() {
    val offer = TapAppLinkOffer(
      creatorName = "Sarah",
      promoCode = "SARAH10",
      billingOfferId = "offer_1",
    )
    assertEquals("Sarah", offer.creatorName)
    assertEquals("SARAH10", offer.promoCode)
    assertEquals("offer_1", offer.billingOfferId)
  }

  @Test
  fun redactKeyHidesMiddleOfApiKey() {
    val redacted = TapAppLink.redactKey("etk_live_abcdefghijklmnop")
    assertTrue(redacted.startsWith("etk_"))
    assertTrue(redacted.endsWith("mnop"))
    assertTrue(redacted.contains("..."))
    assertTrue(!redacted.contains("abcdefghij"))
  }

  @Test
  fun persistedAttributionIsVisibleAfterHydratingStore() {
    val store = TapAppLinkStore(InMemoryKeyValueStore())
    store.tracked = true
    store.attributionId = "attr_persisted"
    store.offer = TapAppLinkOffer("Alex", null, "billing_9")
    TapAppLink.setStoreForTesting(store)

    assertEquals("attr_persisted", TapAppLink.getAttributionId())
    assertEquals("Alex", TapAppLink.getOffer()?.creatorName)
    assertEquals("billing_9", TapAppLink.getOffer()?.billingOfferId)
  }
}
