import java.util.Properties

plugins {
  id("org.metaborg.gradle.config.root-project")// version "0.3.21"
  id("org.metaborg.gradle.config.kotlin-gradle-plugin")// version "0.3.21"
  id("org.metaborg.gitonium")// version "0.1.3"
  kotlin("jvm")// version "1.3.41" // Use 1.3.41 to keep in sync with embedded Kotlin version of Gradle 5.6.4.
  id("org.gradle.kotlin.kotlin-dsl")
}

metaborg {
  kotlinApiVersion = "1.2"
  kotlinLanguageVersion = "1.2"
}

// Needs to be kept in sync with spoofax2Version of Spoofax 3 and the Spoofax 2 releng.
val spoofax2Version = try {
  project.ext["spoofax2Version"]!! // Set by Gradle project property (see gradle.properties).
} catch(e: ExtraPropertiesExtension.UnknownPropertyException) {
  // Get spoofax2Version explicitly via gradle.properties, as project properties are not passed to included builds.
  val propertiesFile = rootDir.resolve("../../../gradle.properties")
  if(propertiesFile.exists() && propertiesFile.isFile) {
    val properties = Properties()
    propertiesFile.inputStream().buffered().use { inputStream ->
      properties.load(inputStream)
    }
    properties.getProperty("spoofax2Version")!!
  } else {
    null!!
  }
}

dependencies {
  api("org.metaborg:org.metaborg.spoofax.meta.core:$spoofax2Version")
  api("org.metaborg:org.metaborg.spt.core:$spoofax2Version")
  /*
  org.metaborg.spoofax.meta.core depends on a version of PIE which depends on version 0.4.0 of org.metaborg:resource.
  Due to an issue in Gradle, the first version of resource that is loaded will be used by code in plugins that react to
  certain Gradle events, such as Project#afterEvaluate. Since version 0.4.0 does not have certain API, this will fail.
  Therefore, we force the version to 0.7.3.
  */
  api("org.metaborg:resource:0.7.3")
}

gradlePlugin {
  plugins {
    create("spoofax-base") {
      id = "org.metaborg.spoofax.gradle.base"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxBasePlugin"
    }
    create("spoofax-language-specification") {
      id = "org.metaborg.spoofax.gradle.langspec"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLanguageSpecificationPlugin"
    }
    create("spoofax-project") {
      id = "org.metaborg.spoofax.gradle.project"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxProjectPlugin"
    }
    create("spoofax-test") {
      id = "org.metaborg.spoofax.gradle.test"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxTestPlugin"
    }
  }
}
