package com.tapapplink.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReferrerReaderTest {
  @Test
  fun awaitWithTimeoutReturnsValueWhenCallbackFires() {
    val value = awaitWithTimeout<String>(1_000) { onResult ->
      Thread {
        Thread.sleep(50)
        onResult("utm_source=tapapplink")
      }.start()
    }
    assertEquals("utm_source=tapapplink", value)
  }

  @Test
  fun awaitWithTimeoutReturnsNullWhenCallbackNeverFires() {
    val value = awaitWithTimeout<String>(100) { _ ->
      // Simulate Install Referrer service never responding.
    }
    assertNull(value)
  }

  @Test
  fun resolveReferrerPrefersCallerOverride() {
    assertEquals(
      "from_caller",
      resolveInstallReferrer(override = "from_caller", fromPlay = "from_play"),
    )
  }

  @Test
  fun resolveReferrerFallsBackToPlayWhenOverrideMissing() {
    assertEquals(
      "from_play",
      resolveInstallReferrer(override = null, fromPlay = "from_play"),
    )
  }

  @Test
  fun resolveReferrerIsNullWhenServiceUnavailable() {
    assertNull(resolveInstallReferrer(override = null, fromPlay = null))
  }

  @Test
  fun resolveReferrerIgnoresBlankOverride() {
    assertEquals(
      "from_play",
      resolveInstallReferrer(override = "  ", fromPlay = "from_play"),
    )
  }

  @Test
  fun timedOutReaderDoesNotBlockCallerIndefinitely() {
    val started = System.currentTimeMillis()
    val value = awaitWithTimeout<String>(150) { onResult ->
      // Async late callback: models a hung Play service binding.
      Thread {
        try {
          Thread.sleep(5_000)
        } catch (_: InterruptedException) {
          // ignore
        }
        onResult("late")
      }.start()
    }
    val elapsed = System.currentTimeMillis() - started
    assertNull(value)
    assertTrue("expected timeout well under 2s, was ${elapsed}ms", elapsed < 2_000)
  }

  private fun resolveInstallReferrer(override: String?, fromPlay: String?): String? = override?.takeIf { it.isNotBlank() } ?: fromPlay
}
