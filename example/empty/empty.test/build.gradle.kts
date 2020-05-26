plugins {
  id("org.metaborg.spoofax.gradle.test")
}

spoofaxTest {
  languageUnderTest.set(org.metaborg.core.language.LanguageIdentifier.parse("$group:empty:$version"))
}

dependencies {
  compileLanguage(project(":empty"))
}
