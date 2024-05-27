import org.metaborg.core.language.*

plugins {
    id("org.metaborg.spoofax.gradle.langspec")
}

spoofaxLanguageSpecification {
    addLanguageContributionsFromMetaborgYaml.set(false)
    languageContributions.add(LanguageContributionIdentifier(LanguageIdentifier(group.toString(), name, LanguageVersion.parse(version.toString())), "lco"))
}
