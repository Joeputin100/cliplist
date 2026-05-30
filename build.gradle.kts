plugins {
    alias(libs.plugins.kotlin.jvm)      apply false
    alias(libs.plugins.android.library) apply false
    // kotlin-android is intentionally absent: AGP 9.0+ has built-in Kotlin support.
}
