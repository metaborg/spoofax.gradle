import org.metaborg.core.language.*

plugins {
    id("dev.spoofax.spoofax2.gradle.langspec")
}

spoofaxLanguageSpecification {
    addLanguageContributionsFromMetaborgYaml.set(false)
    languageContributions.add(LanguageContributionIdentifier(LanguageIdentifier(group.toString(), name, LanguageVersion.parse(version.toString())), "lco"))
}

dependencies {
    api(platform(libs.spoofax3.bom))

    testImplementation(libs.junit4)
}