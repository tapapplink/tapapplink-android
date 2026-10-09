package com.tapapplink.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TapAppLinkTest {
  @Before
  fun setUp() {
    TapAppLink.resetForTesting()
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
}
