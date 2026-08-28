# Tap App Link Android SDK

Kotlin library for creator install attribution. When the user arrives from Google Play, pass the Play Install Referrer into `trackInstall` for a deterministic match.

## Install

Add JitPack to your project `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
  repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
  }
}
```

Then in the app module:

```kotlin
implementation("com.github.tapapplink:tapapplink-android:0.1.0")
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
// Or pass Play Install Referrer when you collect it:
TapAppLink.trackInstall(context, installReferrer) { }

TapAppLink.setAppUserId(Purchases.sharedInstance.appUserID) { }
val offer = TapAppLink.getOffer()
TapAppLink.applyCode("SARAH10") { }
```

`trackInstall()` is safe on every launch — it only records once per install. Call `resetForTesting()` in debug builds before repeating a match test on the same install.

Purchases are attributed through billing webhooks. Leave out a client `trackPurchase` call.

## License

MIT
