plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.21"
  id("org.metaborg.gradle.config.kotlin-gradle-plugin") version "0.3.21"
  id("org.metaborg.gitonium") version "0.1.2"
  kotlin("jvm") version "1.3.41" // Use 1.3.41 to keep in sync with embedded Kotlin version of Gradle 5.6.4.
  `kotlin-dsl`
}

metaborg {
  kotlinApiVersion = "1.2"
  kotlinLanguageVersion = "1.2"
}

val spoofaxVersion = "2.6.0-SNAPSHOT"
dependencies {
  api("org.metaborg:org.metaborg.spoofax.meta.core:$spoofaxVersion")
  api("org.metaborg:org.metaborg.spt.core:$spoofaxVersion")
  /*
  org.metaborg.spoofax.meta.core depends on a version of PIE which depends on version 0.4.0 of org.metaborg:resource.
  Due to an issue in Gradle, the first version of resource that is loaded will be used by code in plugins that react to
  certain Gradle events, such as Project#afterEvaluate. Since version 0.4.0 does not have certain API, this will fail.
  Therefore, we force the version to 0.7.1.
  */
  api("org.metaborg:resource:0.7.1")
}

gradlePlugin {
  plugins {
    create("spoofax-langspec") {
      id = "org.metaborg.spoofax.gradle.langspec"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLangSpecPlugin"
    }
    create("spoofax-project") {
      id = "org.metaborg.spoofax.gradle.project"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxProjectPlugin"
    }
  }
}

gitonium {
  // Disable snapshot dependency checks for releases, until we depend on a stable version of Spoofax Core.
  checkSnapshotDependenciesInRelease = false
}
