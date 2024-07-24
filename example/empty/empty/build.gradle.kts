plugins {
    `java-library`
    id("org.metaborg.convention.java")
    id("org.metaborg.devenv.spoofax.gradle.langspec")
}

spoofaxLanguageSpecification {
    addCompileDependenciesFromMetaborgYaml.set(false)
    addSourceDependenciesFromMetaborgYaml.set(false)

    // Fixes: Could not resolve all dependencies for configuration '<project>:compileClasspath'.
    //  The project declares repositories, effectively ignoring the repositories you have declared in the settings.
    addSpoofaxRepository.set(false)
    // Fixes: Could not find org.metaborg:org.metaborg.spoofax.core:property(java.lang.String)
    addSpoofaxCoreDependency.set(false)
}

dependencies {
    api(platform(libs.metaborg.platform))

    compileLanguage(libs.esv.lang)
    compileLanguage(libs.sdf3.lang)

    sourceLanguage(libs.meta.lib.spoofax)

    compileOnly(libs.spoofax.core)
}
