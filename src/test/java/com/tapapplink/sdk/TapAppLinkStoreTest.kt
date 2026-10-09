package com.tapapplink.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TapAppLinkStoreTest {
  private lateinit var prefs: InMemoryKeyValueStore
  private lateinit var store: TapAppLinkStore

  @Before
  fun setUp() {
    prefs = InMemoryKeyValueStore()
    store = TapAppLinkStore(prefs)
  }

  @Test
  fun installIdIsCreatedOnceAndReused() {
    val first = store.installId()
    val second = store.installId()
    assertNotNull(first)
    assertTrue(first.isNotBlank())
    assertEquals(first, second)
  }

  @Test
  fun installIdSurvivesNewStoreInstanceOnSamePrefs() {
    val original = store.installId()
    val reloaded = TapAppLinkStore(prefs)
    assertEquals(original, reloaded.installId())
  }

  @Test
  fun trackedFlagPersists() {
    assertFalse(store.tracked)
    store.tracked = true
    assertTrue(TapAppLinkStore(prefs).tracked)
  }

  @Test
  fun attributionAndOfferPersistAcrossReload() {
    store.attributionId = "attr_123"
    store.offer = TapAppLinkOffer(
      creatorName = "Sarah",
      promoCode = "SARAH10",
      billingOfferId = "offer_1",
    )

    val reloaded = TapAppLinkStore(prefs)
    assertEquals("attr_123", reloaded.attributionId)
    assertEquals("Sarah", reloaded.offer?.creatorName)
    assertEquals("SARAH10", reloaded.offer?.promoCode)
    assertEquals("offer_1", reloaded.offer?.billingOfferId)
  }

  @Test
  fun clearRemovesAllStateAndAllowsNewInstallId() {
    val original = store.installId()
    store.tracked = true
    store.attributionId = "attr_123"
    store.offer = TapAppLinkOffer("Sarah", "SARAH10", "offer_1")

    store.clear()

    assertFalse(store.tracked)
    assertNull(store.attributionId)
    assertNull(store.offer)
    assertNotEquals(original, store.installId())
  }
}
