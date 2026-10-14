# Changelog

## 0.3.3

- **Fixed:** `setAppUserId` posted `/ingestIdentify` on every call, so apps that link the billing user id from several places could fire it multiple times per launch. 0.3.3 skips when the id matches the last successfully identified value (persisted like `trackInstall`), and coalesces concurrent identical calls into one request. A different id still posts.
- Add optional `codeMatches` and `offer.creatorName` on the typed `TapAppLinkRedeemResult` from `applyCode` (matches server redeem response fields). Parse the success `JSONObject` with `TapAppLinkRedeemResult.from`.
- Send `X-TapAppLink-SDK-Version: 0.3.3` on all requests.

## 0.3.2

- **Fixed:** retrying `applyCode` after a timeout could count an install twice. 0.3.2 sends a request ID so the server recognises the retry. No code changes needed.

## 0.3.1

- **Breaking (Android only):** `applyCode` now returns `Result<JSONObject>`, so 0.3.0 call sites won't compile until they're updated. This is deliberate. The 0.3.0 call couldn't report an error, so any app using it may show an invalid code as applied. Update each call to handle the failure case (snippet below).

  Before (0.3.0):

  ```kotlin
  TapAppLink.applyCode(code) { json ->
    // treated every callback as success, including error bodies
  }
  ```

  After (0.3.1):

  ```kotlin
  TapAppLink.applyCode(code) { result ->
    result.onSuccess { json ->
      // real success only
    }.onFailure { error ->
      // TapAppLinkRedeemException: UnknownCode, InactiveCode, WrongEnvironment, Network, Other
    }
  }
  ```

- **Fixed:** Android and React Native 0.3.0 could return an error from `applyCode()` as if it were a normal result, so an app could show a code as applied when it wasn't. Upgrade to 0.3.1, which raises a typed error for unknown, inactive and wrong-environment codes. iOS and Flutter 0.3.0 threw a generic error, and 0.3.1 makes it typed.
- Send `X-TapAppLink-SDK-Version` on every request so the server can return distinct 410/400 statuses while remaining compatible with legacy 404 bodies.
- `applyCode` callback is now `(Result<JSONObject>) -> Unit` and failures are `TapAppLinkRedeemException` (`UnknownCode`, `InactiveCode`, `WrongEnvironment`, `Network`, `Other`).

## 0.3.0

- Persist install id, tracked flag, attribution id and offer in SharedPreferences so cold launches do not re-post `/ingestInstall`.
- Send a stable `installId` (UUID) on `/ingestInstall` for server-side dedupe.
- Read the Play Install Referrer internally via `InstallReferrerClient` on first install, with a timeout fallback. The optional `installReferrer` argument remains an override.
- Add opt-in `debugLogging` on `TapAppLinkConfig` (API key redacted in logs).

## 0.2.0

- Remove `discountBps` from `TapAppLinkOffer`. Present `billingOfferId` on the paywall.

## 0.1.1

- Remove unused `debugSessionId` from `configure()`. The sandbox debugger attaches via the QR link or code watch.

## 0.1.0

- First public Android library release (`com.tapapplink:sdk:0.1.0`).
