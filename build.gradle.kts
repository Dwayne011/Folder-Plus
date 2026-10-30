// Top-level build file. Plugin versions live in gradle/libs.versions.toml and
// each module applies its own plugins, so the Android Gradle Plugin is only
// resolved when the :app module is part of the build (see settings.gradle.kts).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}
