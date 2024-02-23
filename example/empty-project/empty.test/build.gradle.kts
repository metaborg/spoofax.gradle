import org.metaborg.core.language.LanguageIdentifier

plugins {
    id("dev.spoofax.spoofax2.gradle.test")
}

dependencies {
    // FIXME: Move these platform definitions to a common spot
    sourceLanguage(platform(libs.spoofax3.bom))
    compileLanguage(platform(libs.spoofax3.bom))

    compileLanguage(project(":empty-project:empty"))
    compileLanguage(libs.spoofax.lang.spt)
    compileLanguage(libs.spoofax.lang.sdf3)

    sourceLanguage(libs.spoofax2.meta.lib.spoofax)
}

spoofaxTest {
    // spoofax.gradle.example.empty-project:empty:unspecified
    languageUnderTest.set(LanguageIdentifier.parse("$group:empty:$version"))

    // TODO: Make this the default?
    // Only use the dependencies specified above (should match metaborg.yaml though)
    addCompileDependenciesFromMetaborgYaml.set(false)
    addSourceDependenciesFromMetaborgYaml.set(false)
    addJavaDependenciesFromMetaborgYaml.set(false)
}