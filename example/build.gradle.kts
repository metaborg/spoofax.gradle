plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.21"
  id("org.metaborg.gitonium") version "0.1.3"

  // Set versions for plugins to use, only applying them in subprojects (apply false here).
  id("org.metaborg.spoofax.gradle.langspec") apply false // No version: use the plugin from the included composite build
  id("org.metaborg.spoofax.gradle.project") apply false
  id("org.metaborg.spoofax.gradle.test") apply false
  id("de.set.ecj") version "1.4.1" apply false
}

subprojects {
  metaborg {
    configureSubProject()
  }
}
