# Tap App Link Android SDK

Kotlin library for Tap App Link attribution.

## Install

Until Maven Central is live, publish locally then depend on it:

```bash
./gradlew publishToMavenLocal
```

```kotlin
implementation("com.tapapplink:sdk:0.1.0")
```

`./gradlew` needs JDK 17 or 21. JDK 25 is not supported by this Android Gradle Plugin.

## Usage

```kotlin
TapAppLink.configure(
  TapAppLinkConfig(
    publicKey = "etk_live_…",
    environment = TapAppLinkEnvironment.PRODUCTION,
  )
)

TapAppLink.trackInstall(context) { /* install result */ }
TapAppLink.setAppUserId(Purchases.sharedInstance.appUserID) { /* identify result */ }
```

Pass Play Install Referrer into `trackInstall(context, installReferrer)` when you collect it.

## License

MIT
