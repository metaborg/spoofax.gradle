plugins {
    `java-library`
    `maven-publish`
    id("dev.spoofax.spoofax2.gradle.langspec")
}

dependencies {
    // FIXME: Move these platform definitions to a common spot
    sourceLanguage(platform(libs.spoofax3.bom))
    compileLanguage(platform(libs.spoofax3.bom))
    api(platform(libs.spoofax3.bom))
    testImplementation(platform(libs.spoofax3.bom))
    annotationProcessor(platform(libs.spoofax3.bom))
    testAnnotationProcessor(platform(libs.spoofax3.bom))

    compileLanguage(libs.spoofax.lang.esv)
    compileLanguage(libs.spoofax.lang.sdf3)

    sourceLanguage(libs.spoofax2.meta.lib)
}

spoofaxLanguageSpecification {
    // TODO: Make this the default?
    // Only use the dependencies specified above (should match metaborg.yaml though)
    addCompileDependenciesFromMetaborgYaml.set(false)
    addSourceDependenciesFromMetaborgYaml.set(false)
    addJavaDependenciesFromMetaborgYaml.set(false)
}

project.task("getMavenCoordinates") {
    doLast {
        println("Maven Artifact: ${project.group}:${project.name}:${project.version}")
    }
}