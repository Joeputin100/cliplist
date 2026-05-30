plugins {
    alias(libs.plugins.kotlin.jvm)          apply false
    alias(libs.plugins.android.library)     apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler)    apply false
    // kotlin-android intentionally absent: AGP 9.0+ has built-in Kotlin support.
}
