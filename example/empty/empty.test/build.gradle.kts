plugins {
    id("org.metaborg.devenv.spoofax.gradle.test")
}

spoofaxTest {
    languageUnderTest.set(org.metaborg.core.language.LanguageIdentifier.parse("$group:empty:$version"))
}

dependencies {
    compileLanguage(project(":empty"))
}
