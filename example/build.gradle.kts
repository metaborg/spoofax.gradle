// Workaround for issue: https://youtrack.jetbrains.com/issue/KTIJ-19369
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("org.metaborg.convention.root-project")

    // Set versions for plugins to use, only applying them in subprojects (apply false here).
    id("org.metaborg.devenv.spoofax.gradle.langspec") apply false // No version: use the plugin from the included composite build
    id("org.metaborg.devenv.spoofax.gradle.project") apply false
    id("org.metaborg.devenv.spoofax.gradle.test") apply false
}

// Workaround for issue: https://github.com/gradle/gradle/issues/20131
println("")
