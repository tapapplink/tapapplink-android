# Tap App Link Android SDK

Kotlin library for creator install attribution. On first install the SDK reads the Play Install Referrer and posts once to `/ingestInstall`. State is persisted locally so later launches reuse the attribution id and offer.

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

Then in the app module (0.3.2 or later):

```kotlin
implementation("com.github.tapapplink:tapapplink-android:0.3.2")
```

Upgrading from 0.3.0? 0.3.1 changed `applyCode` on Android. See the [changelog](CHANGELOG.md).

`./gradlew` needs JDK 17 or 21. JDK 25 is not supported by this Android Gradle Plugin.

## Usage

```kotlin
TapAppLink.configure(
  TapAppLinkConfig(
    publicKey = "etk_live_…",
    environment = TapAppLinkEnvironment.PRODUCTION,
    debugLogging = BuildConfig.DEBUG,
  )
)

// Call on every launch. Posts once per install; later launches return stored state.
TapAppLink.trackInstall(context) { result ->
  val offer = TapAppLink.getOffer()
}

// Optional: override the Play Install Referrer if you already collected it.
TapAppLink.trackInstall(context, installReferrer) { }

TapAppLink.setAppUserId(Purchases.sharedInstance.appUserID) { }
```

### Apply a discount code

Show success only on a real success result. Map each outcome to UI like this:

```kotlin
TapAppLink.applyCode(code) { result ->
  result.onSuccess { body ->
    val offer = TapAppLink.getOffer()
    val offerLine = offer?.let { "${it.creatorName}'s offer" }
    if (body.optBoolean("alreadyAttributed")) {
      // "You're all set" — hide the code; show offerLine if any.
      showStatus(title = "You're all set", codeVisible = false, hint = offerLine)
    } else {
      // "Code applied" — show the code; show offerLine if any.
      showStatus(title = "Code applied", codeVisible = true, code = code, hint = offerLine)
    }
  }.onFailure { error ->
    when (error) {
      is TapAppLinkRedeemException.UnknownCode -> {
        showStatus(
          title = "We don't recognise that code. Check it and try again.",
          hint = "Codes aren't case sensitive.",
        )
      }
      is TapAppLinkRedeemException.InactiveCode -> {
        showStatus(
          title = "This code is no longer active.",
          hint = "You can still subscribe at the regular price.",
        )
      }
      is TapAppLinkRedeemException.WrongEnvironment -> {
        // Same customer copy as unknownCode. Never say "environment" to customers.
        Log.w(TAG, TapAppLinkRedeemException.WrongEnvironment.DEVELOPER_WARNING)
        showStatus(
          title = "We don't recognise that code. Check it and try again.",
          hint = "Codes aren't case sensitive.",
        )
      }
      is TapAppLinkRedeemException.Network -> {
        showStatus(
          title = "We couldn't check your code. Check your connection and try again.",
        )
      }
      is TapAppLinkRedeemException.Other -> {
        Log.w(TAG, "applyCode failed status=${error.status} message=${error.message}")
        showStatus(
          title = "We couldn't check your code. Check your connection and try again.",
        )
      }
      else -> {
        Log.w(TAG, "applyCode failed", error)
        showStatus(
          title = "We couldn't check your code. Check your connection and try again.",
        )
      }
    }
  }
}
```

`TapAppLinkRedeemException.WrongEnvironment.DEVELOPER_WARNING` is exactly:
`This code belongs to the other environment (Sandbox or Production). Check your API key.`

`trackInstall()` is safe on every launch. It stores an `installId`, the tracked flag, attribution id and offer in SharedPreferences, and only posts `/ingestInstall` once per install. `setAppUserId` / identify send the stored `attributionId`.

Call `resetForTesting(context)` in debug builds before repeating a match test on the same device.

Purchases are attributed through billing webhooks. Leave out a client `trackPurchase` call.

## CI and releases

GitHub Actions runs on every pull request and push to `main`: Gradle assemble, unit tests, ktlint, and Android Lint.

Pushing a semver tag (`0.3.2`, `v0.3.2`, or a prerelease suffix) runs the same checks, creates a GitHub Release, and requests a JitPack build for that tag. `jitpack.yml` pins OpenJDK 17 for JitPack.

### Local checks

```bash
./gradlew assembleRelease test ktlintCheck lintRelease
```

## Publishing

**JitPack (current):** consumers depend on `com.github.tapapplink:tapapplink-android:0.3.2` (0.3.2 or later). Tag a release; CI triggers JitPack. No extra secrets are required for that path.

**Maven Central (optional, disabled):** the release workflow includes a `maven-central` job left behind `if: false` until a repository admin finishes the one-time setup below. Prefer JitPack until you need Central discovery or a non-`com.github` coordinate.

One-time setup (repository admin with write access to secrets and to the Sonatype / Central Portal account):

1. Create a [Maven Central Portal](https://central.sonatype.com/) account and claim the `com.tapapplink` namespace (or your chosen groupId).
2. Generate a Portal user token (username + password pair).
3. Create a GPG key for signing artefacts and export the private key (ASCII-armoured).
4. In this GitHub repo, add Actions secrets:
   - `MAVEN_CENTRAL_USERNAME` – Portal token username
   - `MAVEN_CENTRAL_PASSWORD` – Portal token password
   - `SIGNING_GPG_PRIVATE_KEY` – ASCII-armoured private key
   - `SIGNING_GPG_PASSPHRASE` – passphrase for that key
5. Wire Gradle publishing to the Central Portal (for example via the `com.vanniktech.maven.publish` plugin or the official `maven-publish` + signing setup), then set the `maven-central` job `if` to run when those secrets are present.

Until steps 1–5 are done, leave the job disabled so tagged releases only ship via GitHub Releases and JitPack.

## License

MIT
