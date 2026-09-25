plugins {
    id("mockarr.android.library")
}

android {
    namespace = "dev.mockarr.core.mocklocation"
}

dependencies {
    api(project(":core:model"))
    // ADR 0005: Play services' fused location is a separate engine; only its mock mode controls it.
    implementation(libs.play.services.location)
}
