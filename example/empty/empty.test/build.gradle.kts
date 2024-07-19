plugins {
    `java-library`
    id("org.metaborg.convention.java")
    id("org.metaborg.devenv.spoofax.gradle.test")
}

spoofaxTest {
    languageUnderTest.set(org.metaborg.core.language.LanguageIdentifier.parse("$group:empty:$version"))
    // Fixes: Could not resolve all dependencies for configuration '<project>:compileClasspath'.
    //  The project declares repositories, effectively ignoring the repositories you have declared in the settings.
    addSpoofaxRepository.set(false)
    // Fixes: Could not find org.metaborg:org.metaborg.spoofax.core:property(java.lang.String)
    addSpoofaxCoreDependency.set(false)
}

dependencies {
    compileLanguage(project(":empty"))
    compileOnly(libs.spoofax.core)
}
