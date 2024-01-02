plugins {
    id("dev.spoofax.spoofax2.gradle.langspec")
}

spoofaxLanguageSpecification {
    strategoFormat.set(org.metaborg.spoofax.meta.core.config.StrategoFormat.jar)
}

dependencies {
    api(platform(libs.spoofax3.bom))
}