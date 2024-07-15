import org.metaborg.core.language.*

plugins {
    id("org.metaborg.devenv.spoofax.gradle.langspec")
}

spoofaxLanguageSpecification {
    addLanguageContributionsFromMetaborgYaml.set(false)
    languageContributions.add(LanguageContributionIdentifier(LanguageIdentifier(
        group.toString(), name, LanguageVersion.parse(version.toString())), "lco"))
}
