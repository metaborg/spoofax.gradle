buildscript {
  if(gradle.parent?.rootProject?.name == "spoofax.gradle.root") { // If standalone build, put additional plugins on the classpath.
    repositories {
      maven("https://artifacts.metaborg.org/content/groups/public/")
    }
    dependencies {
      classpath("org.metaborg:gradle.config:0.4.2")
      classpath("org.metaborg:gitonium:0.1.4")
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

val spoofax2Version = System.getProperty("spoofax2Version")

dependencies {
  api("org.metaborg:org.metaborg.spoofax.meta.core:$spoofax2Version")
  api("org.metaborg:org.metaborg.spt.core:$spoofax2Version")
  /*
  org.metaborg.spoofax.meta.core depends on a version of PIE which depends on an old version of org.metaborg:resource.
  Due to an issue in Gradle, the first version of resource that is loaded will be used by code in plugins that react to
  certain Gradle events, such as Project#afterEvaluate. Since the old version does not have certain API, this will fail.
  Therefore, we force the version to a recent one.
  */
  api("org.metaborg:resource:0.10.0")
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
