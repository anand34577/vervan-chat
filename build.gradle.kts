plugins {
    id("com.android.application") version "9.5.0" apply false
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the kotlin-android plugin is gone;
    // only the Compose compiler plugin is still applied explicitly.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.13" apply false
}
