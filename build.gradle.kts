plugins {
  id("com.android.library") version "8.7.3"
  id("org.jetbrains.kotlin.android") version "2.0.21"
  id("maven-publish")
}

group = "com.tapapplink"
version = "0.1.0"

android {
  namespace = "com.tapapplink.sdk"
  compileSdk = 35
  defaultConfig {
    minSdk = 24
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }
}

afterEvaluate {
  publishing {
    publications {
      create<MavenPublication>("release") {
        from(components["release"])
        groupId = "com.tapapplink"
        artifactId = "sdk"
        version = "0.1.0"
        pom {
          name.set("Tap App Link Android SDK")
          description.set("Tap App Link attribution SDK for Android")
          url.set("https://tapapplink.com")
          licenses {
            license {
              name.set("MIT License")
              url.set("https://opensource.org/licenses/MIT")
            }
          }
          developers {
            developer {
              id.set("tapapplink")
              name.set("Tap App Link")
            }
          }
          scm {
            url.set("https://github.com/tapapplink/tapapplink")
            connection.set("scm:git:git://github.com/tapapplink/tapapplink.git")
            developerConnection.set("scm:git:ssh://github.com/tapapplink/tapapplink.git")
          }
        }
      }
    }
    repositories {
      mavenLocal()
    }
  }
}
