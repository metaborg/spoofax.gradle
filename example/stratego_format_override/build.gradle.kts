plugins {
  id("org.metaborg.spoofax.gradle.langspec")
}

spoofaxLanguageSpecification {
  strategoFormat.set(org.metaborg.spoofax.meta.core.config.StrategoFormat.jar)
}
