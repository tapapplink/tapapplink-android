# Changelog

## 0.3.1

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
