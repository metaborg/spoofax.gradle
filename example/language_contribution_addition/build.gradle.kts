import org.metaborg.core.language.*

plugins {
  id("org.metaborg.spoofax.gradle.langspec")
}

spoofaxLanguageSpecification {
  languageContributions.add(LanguageContributionIdentifier(LanguageIdentifier(group.toString() + ".test", name + "_test", LanguageVersion.parse(version.toString() + "-test")), "lca"))
}
