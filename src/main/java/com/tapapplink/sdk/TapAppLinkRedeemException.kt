package com.tapapplink.sdk

/**
 * Typed failure from [TapAppLink.applyCode].
 *
 * Map server `error` values (`unknown_code`, `inactive_code`, `wrong_environment`)
 * and HTTP status fallbacks (404, 410, 400) into these cases.
 */
sealed class TapAppLinkRedeemException(
  message: String? = null,
  cause: Throwable? = null,
) : Exception(message, cause) {
  class UnknownCode : TapAppLinkRedeemException("unknown_code")

  class InactiveCode : TapAppLinkRedeemException("inactive_code")

  class WrongEnvironment : TapAppLinkRedeemException("wrong_environment") {
    companion object {
      /** Developer-only warning. Never show this (or the word "environment") to customers. */
      const val DEVELOPER_WARNING =
        "This code belongs to the other environment (Sandbox or Production). Check your API key."
    }
  }

  class Network(
    cause: Throwable? = null,
  ) : TapAppLinkRedeemException("network", cause)

  class Other(
    val status: Int,
    override val message: String?,
  ) : TapAppLinkRedeemException(message)
}
