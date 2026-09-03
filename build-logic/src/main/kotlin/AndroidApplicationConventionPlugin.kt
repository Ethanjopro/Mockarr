import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                compileSdk = AndroidConfig.COMPILE_SDK
                defaultConfig {
                    minSdk = AndroidConfig.MIN_SDK
                    targetSdk = AndroidConfig.TARGET_SDK
                }
                compileOptions {
                    sourceCompatibility = AndroidConfig.JAVA_VERSION
                    targetCompatibility = AndroidConfig.JAVA_VERSION
                }
                configureRelease(target)
            }
            configureDetekt()
        }
    }

    /**
     * Release = R8 (minify + resource shrink) and, when an upload key is
     * available, signing with it. The key comes from `keystore.properties` at
     * the repo root (git-ignored; keys `storeFile`, `storePassword`, `keyAlias`,
     * `keyPassword`) or from `MOCKARR_UPLOAD_*` environment variables (CI).
     * With neither, the release build stays unsigned so a clean clone still
     * builds — Play App Signing holds the real app key either way
     * (`docs/release/play-launch.md`).
     */
    private fun ApplicationExtension.configureRelease(project: Project) {
        val upload = UploadKey.from(project)
        if (upload != null) {
            signingConfigs.create("upload") {
                storeFile = project.rootProject.file(upload.storeFile)
                storePassword = upload.storePassword
                keyAlias = upload.keyAlias
                keyPassword = upload.keyPassword
            }
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                if (upload != null) signingConfig = signingConfigs.getByName("upload")
            }
        }
    }
}

private data class UploadKey(
    val storeFile: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    companion object {
        private const val PROPERTIES_FILE = "keystore.properties"
        private const val ENV_PREFIX = "MOCKARR_UPLOAD_"

        fun from(project: Project): UploadKey? {
            val file = project.rootProject.file(PROPERTIES_FILE)
            val props = Properties()
            if (file.exists()) file.inputStream().use(props::load)
            fun value(key: String, env: String): String? =
                props.getProperty(key) ?: project.providers.environmentVariable(ENV_PREFIX + env).orNull
            val storeFile = value("storeFile", "STORE_FILE") ?: return null
            return UploadKey(
                storeFile = storeFile,
                storePassword = value("storePassword", "STORE_PASSWORD") ?: return null,
                keyAlias = value("keyAlias", "KEY_ALIAS") ?: return null,
                keyPassword = value("keyPassword", "KEY_PASSWORD") ?: return null,
            )
        }
    }
}
