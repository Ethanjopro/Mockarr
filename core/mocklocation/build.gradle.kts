plugins {
    id("mockarr.android.library")
}

android {
    namespace = "dev.mockarr.core.mocklocation"
}

dependencies {
    api(project(":core:model"))
}
