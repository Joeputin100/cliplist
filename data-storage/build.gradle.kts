import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    // NOTE: The kotlin-android plugin is intentionally NOT applied. AGP 9.x ships
    // built-in Kotlin support and treats applying org.jetbrains.kotlin.android as a
    // hard error ("no longer required since AGP 9.0"). See:
    // https://developer.android.com/build/migrate-to-built-in-kotlin
}

android {
    namespace = "com.cliplist.storage"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// AGP 9 built-in Kotlin: kotlinOptions{} inside android{} is gone; configure the
// Kotlin JVM target via the top-level kotlin{} extension instead. (It would default
// to compileOptions.targetCompatibility, but we set it explicitly for clarity.)
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":core-scan"))
}
