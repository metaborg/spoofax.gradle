import org.metaborg.core.language.*

plugins {
}

spoofaxLanguageSpecification {
    languageContributions.add(LanguageContributionIdentifier(LanguageIdentifier(group.toString() + ".test", name + "_test", LanguageVersion.parse(version.toString() + "-test")), "lca"))
}
