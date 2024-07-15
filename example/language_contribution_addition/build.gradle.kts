import org.metaborg.core.language.*

plugins {
    id("org.metaborg.devenv.spoofax.gradle.langspec")
}

spoofaxLanguageSpecification {
    languageContributions.add(LanguageContributionIdentifier(
        LanguageIdentifier("$group.test", "${name}_test", LanguageVersion.parse("$version-test")), "lca"))
}
