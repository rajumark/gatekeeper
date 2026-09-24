// Demo app. By default it uses the library source (:gatekeeper). To check the *published* artifact
// instead, publish to Maven Local and build with -PuseMavenLocal:
//   ./gradlew :gatekeeper:publishToMavenLocal :sample:assembleDebug -PuseMavenLocal
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.rajumark.hoverfly.gatekeeper.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rajumark.hoverfly.gatekeeper.sample"
        minSdk = 23 // Compose; the library itself supports 21
        targetSdk = 36
        versionCode = 1
        versionName = providers.gradleProperty("VERSION_NAME").get()
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug") // demo only
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    if (providers.gradleProperty("useMavenLocal").isPresent) {
        val group = providers.gradleProperty("GROUP").get()
        val artifact = providers.gradleProperty("POM_ARTIFACT_ID").get()
        val version = providers.gradleProperty("VERSION_NAME").get()
        implementation("$group:$artifact:$version")
    } else {
        implementation(project(":gatekeeper"))
    }
}
