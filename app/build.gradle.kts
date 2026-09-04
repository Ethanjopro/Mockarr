import java.util.Properties

plugins {
    id("mockarr.android.application")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.mockarr.app"

    defaultConfig {
        applicationId = "dev.mockarr.app"
        versionName = "0.2.0"
        versionCode = AndroidConfig.versionCode(versionName!!)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // ADR 0003: the managed backend key. Empty (clean clone, CI without the
    // secret) = public servers only; never commit secrets.properties.
    defaultConfig {
        buildConfigField("String", "GEOAPIFY_KEY", "\"${secret("GEOAPIFY_KEY")}\"")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:simulation"))
    implementation(project(":core:routing"))
    implementation(project(":core:mocklocation"))
    implementation(project(":core:data"))

    implementation(libs.maplibre)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    implementation(libs.compose.material.icons.core)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
}

/** A value from the git-ignored `secrets.properties` at the repo root, else the environment, else "". */
fun secret(name: String): String {
    val file = rootProject.file("secrets.properties")
    val props = Properties()
    if (file.exists()) file.inputStream().use(props::load)
    return props.getProperty(name) ?: providers.environmentVariable(name).orNull ?: ""
}
