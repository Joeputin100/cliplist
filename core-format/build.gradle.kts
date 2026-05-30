plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.cliplist"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
