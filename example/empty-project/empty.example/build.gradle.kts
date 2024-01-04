plugins {
    id("dev.spoofax.spoofax2.gradle.project")
}

dependencies {
    // FIXME: Move these platform definitions to a common spot
    sourceLanguage(platform(libs.spoofax3.bom))
    compileLanguage(platform(libs.spoofax3.bom))

    compileLanguage(project(":empty-project:empty"))
}

spoofaxProject {
    // TODO: Make this the default?
    // Only use the dependencies specified above (should match metaborg.yaml though)
    addCompileDependenciesFromMetaborgYaml.set(false)
    addSourceDependenciesFromMetaborgYaml.set(false)
    addJavaDependenciesFromMetaborgYaml.set(false)
}