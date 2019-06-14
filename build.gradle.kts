plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.8"
  id("org.metaborg.gradle.config.kotlin-gradle-plugin") version "0.3.8"
  id("org.metaborg.gitonium") version "0.1.2"
  kotlin("jvm") version "1.3.21"
  `kotlin-dsl`
}

metaborg {
  kotlinApiVersion = "1.2"
  kotlinLanguageVersion = "1.2"
}

gitonium {
  // Disable snapshot dependency checks for releases, until we depend on a stable version of Spoofax Core.
  checkSnapshotDependenciesInRelease = false
}

gradlePlugin {
  plugins {
    create("spoofax-langspec") {
      id = "org.metaborg.spoofax.gradle.langspec"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLangSpecPlugin"
    }
  }
}

// Embed Spoofax Core dependencies into the plugin so that users do not receive the transitive dependency tree.
val embedded: Configuration = configurations.create("embedded")
configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(embedded)
val spoofaxVersion = "2.6.0-SNAPSHOT"
dependencies {
  embedded("org.metaborg:org.metaborg.spoofax.meta.core:$spoofaxVersion")
  embedded("org.metaborg:org.metaborg.spt.core:$spoofaxVersion")
}
tasks {
  jar {
    // Closure inside from to defer evaluation of configuration until task execution time.
    from({ embedded.filter { it.exists() }.map { if(it.isDirectory) it else zipTree(it) } }) {
      // Exclude signature files from dependencies, otherwise the JVM will refuse to load the created JAR file.
      exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    // Enable zip64 to support ZIP files with more than 2^16 entries, which we need.
    isZip64 = true
  }
}

// Repositories required for transitive dependencies of 'spoofax.meta.core'.
repositories {
  maven("https://pluto-build.github.io/mvnrepository/")
  maven("https://sugar-lang.github.io/mvnrepository/")
  maven("http://nexus.usethesource.io/content/repositories/public/")
}
