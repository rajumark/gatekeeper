// Gatekeeper: on-device text safety (toxic message detection). Pure Kotlin inference, model bundled in the AAR,
// zero runtime dependencies.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.rajumark.hoverfly.gatekeeper"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    // -PtestBuildType=release runs the device tests non-debuggable, for realistic latency numbers.
    testBuildType = providers.gradleProperty("testBuildType").getOrElse("debug")
    testOptions { unitTests.all { it.testLogging { events("passed", "failed"); showStandardStreams = true } } }
}

// The on-device parity test reads the same Python reference vectors as the JVM test.
androidComponents {
    onVariants { v -> v.androidTest?.sources?.assets?.addStaticSourceDirectory("src/test/resources") }
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

// Coordinates and POM come from gradle.properties; credentials and the signing key from
// ~/.gradle/gradle.properties (see PUBLISHING.md). The release variant is published with a
// sources jar and a javadoc jar.
mavenPublishing {
    publishToMavenCentral()
    // Maven Central needs signed artifacts; Maven Local and JitPack do not, so sign only
    // when a key is configured.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}
