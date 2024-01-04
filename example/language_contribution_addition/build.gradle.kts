import org.metaborg.core.language.*

plugins {
    id("dev.spoofax.spoofax2.gradle.langspec")
}

spoofaxLanguageSpecification {
    val id = LanguageIdentifier("$group.test", name + "_test", LanguageVersion.parse("$version-test"))
    println("Adding language contribution for: $id")
    languageContributions.add(LanguageContributionIdentifier(id, "lca"))
}

dependencies {
    api(platform(libs.spoofax3.bom))
}