import org.metaborg.core.language.*

plugins {
    id("dev.spoofax.spoofax2.gradle.langspec")
}

spoofaxLanguageSpecification {
    println("Adding language contribution for $group:$name:$version")
    languageContributions.add(LanguageContributionIdentifier(LanguageIdentifier("$group.test", name + "_test", LanguageVersion.parse("$version-test")), "lca"))
}

dependencies {
    api(platform(libs.spoofax3.bom))
}