import java.util.Properties

buildscript {
  if(gradle.parent?.rootProject?.name == "spoofax.gradle.root") { // If standalone build, put additional plugins on the classpath.
    repositories {
      maven("https://artifacts.metaborg.org/content/groups/public/")
    }
    dependencies {
      classpath("org.metaborg:gradle.config:0.3.21")
      classpath("org.metaborg:gitonium:0.1.3")
    }
  }
}

plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
  `maven-publish`
}

val standaloneBuild = gradle.parent?.rootProject?.name == "spoofax.gradle.root"
if(standaloneBuild) { // If standalone build, apply additional plugins.
  apply(plugin = "org.metaborg.gradle.config.root-project")
  apply(plugin = "org.metaborg.gitonium")
}

configure<mb.gradle.config.MetaborgExtension> {
  kotlinApiVersion = "1.2"
  kotlinLanguageVersion = "1.2"
}

// Needs to be kept in sync with spoofax2Version of Spoofax 3 and the Spoofax 2 releng.
val spoofax2Version = try {
  project.ext["spoofax2Version"]!! // Set by Gradle project property (see gradle.properties).
} catch(e: ExtraPropertiesExtension.UnknownPropertyException) {
  // Get spoofax2Version explicitly via gradle.properties, as project properties are not passed to included builds.
  val path = if(standaloneBuild) "../gradle.properties" else "../../../gradle.properties"
  val file = rootDir.resolve(path)
  if(file.exists() && file.isFile) {
    val properties = Properties()
    file.inputStream().buffered().use { inputStream ->
      properties.load(inputStream)
    }
    properties.getProperty("spoofax2Version")
      ?: throw GradleException("Cannot determine Spoofax 2 version: Gradle project property 'spoofax2Version' was not set (in this project's gradle.properties file), and was also not found in '$path'")
  } else {
    throw GradleException("Cannot determine Spoofax 2 version: Gradle project property 'spoofax2Version' was not set (in this project's gradle.properties file), and Gradle properties file '$path' was also not found")
  }
}

dependencies {
  api("org.metaborg:org.metaborg.spoofax.meta.core:$spoofax2Version")
  api("org.metaborg:org.metaborg.spt.core:$spoofax2Version")
  /*
  org.metaborg.spoofax.meta.core depends on a version of PIE which depends on version 0.4.0 of org.metaborg:resource.
  Due to an issue in Gradle, the first version of resource that is loaded will be used by code in plugins that react to
  certain Gradle events, such as Project#afterEvaluate. Since version 0.4.0 does not have certain API, this will fail.
  Therefore, we force the version to 0.8.1.
  */
  api("org.metaborg:resource:0.8.1")
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
