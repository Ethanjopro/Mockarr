plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
}

// Restructure rule (CLAUDE.md, ADR 0001): the pure-Kotlin core modules never
// touch the Android framework. Fails `check` the day a dependency leaks in.
val pureCoreModules = listOf("core/model", "core/simulation", "core/routing")

val checkCoreBoundary by tasks.registering {
    group = "verification"
    description = "Fails if core:model/simulation/routing import android.* or androidx.*"
    // Capture plain values here: the configuration cache cannot serialise
    // script-object references (fileTree/rootDir) used inside doLast.
    val sources = files(pureCoreModules.map { fileTree("$it/src") { include("**/*.kt") } })
    val rootPath = rootDir
    inputs.files(sources)
    doLast {
        val offenders = sources.files.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) ->
                    val trimmed = line.trimStart()
                    trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.")
                }
                .map { (index, line) -> "${file.relativeTo(rootPath)}:${index + 1}: ${line.trim()}" }
        }
        check(offenders.isEmpty()) {
            "core:* must stay Android-free (see CLAUDE.md → Restructure rules):\n" + offenders.joinToString("\n")
        }
    }
}

subprojects {
    if (path in pureCoreModules.map { ":" + it.replace('/', ':') }) {
        tasks.matching { it.name == "check" }.configureEach { dependsOn(checkCoreBoundary) }
    }
}
