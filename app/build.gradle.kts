plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP's built-in Kotlin picks up this KGP instead of its bundled 2.2.x.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
