import org.metaborg.core.language.*

plugins {
    `java-library`
    id("org.metaborg.convention.java")
    id("org.metaborg.devenv.spoofax.gradle.langspec")
}

spoofaxLanguageSpecification {
    languageContributions.add(LanguageContributionIdentifier(
        LanguageIdentifier("$group.test", "${name}_test", LanguageVersion.parse("$version-test")), "lca"))
    // Fixes: Could not resolve all dependencies for configuration '<project>:compileClasspath'.
    //  The project declares repositories, effectively ignoring the repositories you have declared in the settings.
    addSpoofaxRepository.set(false)
}
