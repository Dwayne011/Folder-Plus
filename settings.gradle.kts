pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WidgetsPlus"

// :core is pure Kotlin/JVM and builds anywhere (used for unit-testable logic).
include(":core")

// :app needs the Android SDK. It is skipped automatically when no SDK is
// configured so that `gradle :core:test` still works on machines without one.
// Android Studio always has an SDK, so the app module is always included there.
val hasAndroidSdk = file("local.properties").exists() ||
    System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null
if (hasAndroidSdk || providers.gradleProperty("includeApp").isPresent) {
    include(":app")
}
