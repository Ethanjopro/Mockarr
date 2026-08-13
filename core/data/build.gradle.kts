plugins {
    id("mockarr.android.library")
}

android {
    namespace = "dev.mockarr.core.data"
}

dependencies {
    api(project(":core:model"))
}
