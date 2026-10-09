# Changelog

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
