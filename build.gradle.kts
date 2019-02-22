plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.3"
  id("org.metaborg.gradle.config.kotlin-gradle-plugin") version "0.3.3"
  //id("org.metaborg.gradle.config.junit-testing") version "0.3.3"
  id("org.metaborg.gitonium") version "0.1.0"
  kotlin("jvm") version "1.3.20"
}

dependencies {
  implementation("org.metaborg:org.metaborg.spoofax.meta.core:2.6.0-SNAPSHOT")
  implementation("org.metaborg:org.metaborg.spt.core:2.6.0-SNAPSHOT")

  testImplementation("io.github.glytching:junit-extensions:2.3.0")
}

gradlePlugin {
  plugins {
    create("spoofax-langspec") {
      id = "org.metaborg.spoofax.gradle.langspec"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLangSpecPlugin"
    }
  }
}
