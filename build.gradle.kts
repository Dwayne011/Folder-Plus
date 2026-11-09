// Top-level build file. Plugin versions live in gradle/libs.versions.toml and
// each module applies its own plugins. Nothing is declared here on purpose:
// putting the Kotlin Gradle Plugin on the root classpath would clash with
// :app requesting `org.jetbrains.kotlin.android` with an explicit version, and
// declaring the Android Gradle Plugin here would make the build fail on
// machines without an Android SDK (see settings.gradle.kts).
