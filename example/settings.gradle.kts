rootProject.name = "spoofax.gradle.example"

pluginManagement {
    repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from("dev.spoofax:spoofax3-catalog:0.0.0-SNAPSHOT")
        }
    }
}

includeBuild("../plugin/")

include(":empty-project:empty")
include(":empty-project:empty.example")
include(":empty-project:empty.test")

include(":stratego_format_override")

include(":language_contribution_override")
include(":language_contribution_addition")
