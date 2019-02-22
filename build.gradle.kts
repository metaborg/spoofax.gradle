plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.3"
  id("org.metaborg.gradle.config.kotlin-gradle-plugin") version "0.3.3"
  id("org.metaborg.gradle.config.junit-testing") version "0.3.3"
  id("org.metaborg.gitonium") version "0.1.0"
  kotlin("jvm") version "1.3.20"
  `kotlin-dsl`
}

metaborg {
  kotlinApiVersion = "1.2"
  kotlinLanguageVersion = "1.2"
}

gitonium {
  // Disable snapshot dependency checks for releases, until we release a new stable version of Spoofax Core.
  checkSnapshotDependenciesInRelease = false
}

dependencies {
  implementation("org.metaborg:org.metaborg.spoofax.meta.core:2.6.0-SNAPSHOT")
  implementation("org.metaborg:org.metaborg.spt.core:2.6.0-SNAPSHOT")
}

gradlePlugin {
  plugins {
    create("spoofax-langspec") {
      id = "org.metaborg.spoofax.gradle.langspec"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLangSpecPlugin"
    }
  }
}

repositories {
  maven("https://pluto-build.github.io/mvnrepository/")
  maven("https://sugar-lang.github.io/mvnrepository/")
  maven("http://nexus.usethesource.io/content/repositories/public/")
}

tasks.withType<Test> {
  useJUnitPlatform {
    excludeTags.add("stackOverflowing")
  }
}
