rootProject.name = "spoofax.gradle.example"

dependencyResolutionManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
        mavenCentral()
    }
}

pluginManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
        gradlePluginPortal()
    }
}

plugins {
    id("org.metaborg.convention.settings") version "latest.integration"
}

fun String.includeProject(id: String, path: String = "$this/$id") {
    include(id)
    project(":$id").projectDir = file(path)
}

"empty".run {
    includeProject("empty")
    includeProject("empty.example")
    includeProject("empty.test")
}

include("stratego_format_override")

include("language_contribution_override")
include("language_contribution_addition")
