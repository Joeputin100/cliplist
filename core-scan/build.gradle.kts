plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.cliplist"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core-format"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // This module currently ships only the FakeVolume test helper (no @Test methods yet);
    // real tests arrive with PlaylistPlanner/PlaylistWriter in later Phase 2 tasks. Gradle 9
    // fails the test task when sources exist but no tests are discovered, so relax that here.
    failOnNoDiscoveredTests = false
}
