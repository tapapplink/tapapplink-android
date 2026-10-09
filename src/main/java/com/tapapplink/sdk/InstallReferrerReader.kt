package com.tapapplink.sdk

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun interface ReferrerReader {
  /**
   * Returns the Play Install Referrer string, or null when unavailable / timed out.
   */
  fun read(context: Context, timeoutMs: Long): String?
}

internal class PlayInstallReferrerReader : ReferrerReader {
  override fun read(context: Context, timeoutMs: Long): String? {
    val result = AtomicReference<String?>(null)
    val latch = CountDownLatch(1)
    val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
    try {
      client.startConnection(
        object : InstallReferrerStateListener {
          override fun onInstallReferrerSetupFinished(responseCode: Int) {
            try {
              if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                val referrer = client.installReferrer?.installReferrer
                if (!referrer.isNullOrBlank()) {
                  result.set(referrer)
                }
              }
            } catch (_: Exception) {
              // Service may throw when Play Store is missing or restricted.
            } finally {
              endQuietly(client)
              latch.countDown()
            }
          }

          override fun onInstallReferrerServiceDisconnected() {
            latch.countDown()
          }
        },
      )
      if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        endQuietly(client)
      }
    } catch (_: Exception) {
      endQuietly(client)
    }
    return result.get()
  }

  private fun endQuietly(client: InstallReferrerClient) {
    try {
      client.endConnection()
    } catch (_: Exception) {
      // ignore
    }
  }

  companion object {
    const val DEFAULT_TIMEOUT_MS = 3_000L
  }
}

/**
 * Waits up to [timeoutMs] for [block] to produce a value. Used in tests to mirror
 * the production timeout behaviour without binding to Play services.
 */
internal fun <T> awaitWithTimeout(
  timeoutMs: Long,
  block: (onResult: (T?) -> Unit) -> Unit,
): T? {
  val result = AtomicReference<T?>(null)
  val latch = CountDownLatch(1)
  block { value ->
    result.set(value)
    latch.countDown()
  }
  latch.await(timeoutMs, TimeUnit.MILLISECONDS)
  return result.get()
}
