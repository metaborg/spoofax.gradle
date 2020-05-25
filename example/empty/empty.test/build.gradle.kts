import org.metaborg.core.language.LanguageIdentifier

plugins {
  id("org.metaborg.spoofax.gradle.test")
}

spoofaxTest {
  languageUnderTest.set(LanguageIdentifier.parse("$group:empty:$version"))
}
