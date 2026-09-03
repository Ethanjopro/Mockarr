import org.gradle.api.JavaVersion

object AndroidConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 37
    val JAVA_VERSION = JavaVersion.VERSION_17

    private const val MAJOR_WEIGHT = 10_000
    private const val MINOR_WEIGHT = 100
    private const val SEMVER_PARTS = 3
    private const val MAX_PART = 99

    /**
     * Play's `versionCode` derived from a `MAJOR.MINOR.PATCH` name so the two
     * can never drift: 0.2.0 → 200, 1.4.12 → 10412. Each part is capped at 99.
     */
    fun versionCode(versionName: String): Int {
        val parts = versionName.split('.').map { it.toIntOrNull() }
        require(parts.size == SEMVER_PARTS && parts.all { it != null && it in 0..MAX_PART }) {
            "versionName must be MAJOR.MINOR.PATCH with parts 0..99, got '$versionName'"
        }
        val (major, minor, patch) = parts.map { it!! }
        return major * MAJOR_WEIGHT + minor * MINOR_WEIGHT + patch
    }
}
