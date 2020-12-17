/**
 * The Spoofax Gradle plugin can be built standalone (i.e., when running `./gradlew buildAll` from the root of this
 * repository), or can be built as part of the devenv repository. When built standalone, we depend on Spoofax 2
 * artifacts directly. When built as part of devenv, we depend on the devenv (denoted by 'org.metaborg.devenv' group ID)
 * versions of Spoofax 2 artifacts, and also publish this plugin as a separate devenv artifact. To use this plugin as
 * part of devenv, you must insert '.devenv' after 'org.metaborg' in the artifact ID. For example:
 * `plugins { id("org.metaborg.devenv.spoofax.gradle.langspec") }`
 */

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
val spoofax2Version: String = if(standaloneBuild) {
  System.getProperty("spoofax2Version")
} else {
  ext["spoofax2Version"]!! as String
}
if(standaloneBuild) { // If standalone build, apply additional plugins and set different dependencies.
  apply(plugin = "org.metaborg.gradle.config.root-project")
  apply(plugin = "org.metaborg.gitonium")
  // Embed Spoofax Core dependencies into the plugin so that users do not receive the transitive dependency tree.
  val embedded: Configuration = configurations.create("embedded")
  configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(embedded)
  dependencies {
    embedded("org.metaborg:org.metaborg.spoofax.meta.core:$spoofax2Version")
    embedded("org.metaborg:org.metaborg.spt.core:$spoofax2Version")
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
} else {
  dependencies {
    api("$group:org.metaborg.spoofax.meta.core:$version")
    api("org.metaborg:org.metaborg.spt.core:$spoofax2Version")
  }
}

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
configure<mb.gradle.config.MetaborgExtension> {
  kotlinApiVersion = "1.2"
  kotlinLanguageVersion = "1.2"
}

dependencies {
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
    val pluginIdAffix = if(!standaloneBuild) ".devenv" else ""
    create("spoofax-base") {
      id = "org.metaborg$pluginIdAffix.spoofax.gradle.base"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxBasePlugin"
    }
    create("spoofax-language-specification") {
      id = "org.metaborg$pluginIdAffix.spoofax.gradle.langspec"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLanguageSpecificationPlugin"
    }
    create("spoofax-project") {
      id = "org.metaborg$pluginIdAffix.spoofax.gradle.project"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxProjectPlugin"
    }
    create("spoofax-test") {
      id = "org.metaborg$pluginIdAffix.spoofax.gradle.test"
      implementationClass = "mb.spoofax.gradle.plugin.SpoofaxTestPlugin"
    }
  }
}
