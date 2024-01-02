import org.metaborg.core.language.*

plugins {
}

spoofaxLanguageSpecification {
    addLanguageContributionsFromMetaborgYaml.set(false)
    languageContributions.add(LanguageContributionIdentifier(LanguageIdentifier(group.toString(), name, LanguageVersion.parse(version.toString())), "lco"))
}
