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
implementation("com.github.tapapplink:tapapplink-android:0.2.0")
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

## CI and releases

GitHub Actions runs on every pull request and push to `main`: Gradle assemble, unit tests, ktlint, and Android Lint.

Pushing a semver tag (`0.2.0`, `v0.2.0`, or a prerelease suffix) runs the same checks, creates a GitHub Release, and requests a JitPack build for that tag. `jitpack.yml` pins OpenJDK 17 for JitPack.

### Local checks

```bash
./gradlew assembleRelease test ktlintCheck lintRelease
```

## Publishing

**JitPack (current):** consumers depend on `com.github.tapapplink:tapapplink-android:<tag>`. Tag a release; CI triggers JitPack. No extra secrets are required for that path.

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
