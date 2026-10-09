# Changelog

## 0.3.2

- Restores the 0.3.0 `applyCode(code) { json -> }` signature as `@Deprecated`, because 0.3.1 changed it to `(Result<JSONObject>) -> Unit` and broke source compatibility for a patch. **Upgrade to 0.3.2.**
- The deprecated overload never returns an error body as success; on failure it throws `TapAppLinkRedeemException` on the SDK worker thread. Prefer the `Result` overload so you can handle errors in the callback.
- Migration:

  Before (0.3.0):

  ```kotlin
  TapAppLink.applyCode("SARAH10") { json ->
    // treated every callback as success, including error bodies in 0.3.0
  }
  ```

  After (0.3.2):

  ```kotlin
  TapAppLink.applyCode("SARAH10") { result ->
    result.onSuccess { json ->
      // real success only
    }.onFailure { error ->
      // TapAppLinkRedeemException: UnknownCode, InactiveCode, WrongEnvironment, Network, Other
    }
  }
  ```

## 0.3.1

- **Fixed:** Android and React Native 0.3.0 could return an error from `applyCode()` as if it were a normal result, so an app could show a code as applied when it wasn't. Android apps: **Upgrade to 0.3.2** (0.3.1 changed the `applyCode` callback shape; 0.3.2 restores the 0.3.0 signature as deprecated alongside `Result`). React Native: upgrade to 0.3.1 for typed errors. iOS and Flutter 0.3.0 threw a generic error, and 0.3.1 makes it typed.
- Send `X-TapAppLink-SDK-Version` on every request so the server can return distinct 410/400 statuses while remaining compatible with legacy 404 bodies.
- `applyCode` gained a `(Result<JSONObject>) -> Unit` overload; failures are `TapAppLinkRedeemException` (`UnknownCode`, `InactiveCode`, `WrongEnvironment`, `Network`, `Other`).

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
