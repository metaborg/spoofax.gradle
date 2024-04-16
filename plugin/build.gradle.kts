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
      classpath("org.metaborg:gradle.config:0.4.8")
      classpath("org.metaborg:gitonium:0.1.5")
    }
  }
}

plugins {
  `java-gradle-plugin`
  `kotlin-dsl`
  `maven-publish`
}

var spoofax2Version: String
var spoofax2CoreDependency: String
val standaloneBuild = gradle.parent?.rootProject?.name == "spoofax.gradle.root"
if(standaloneBuild) { // If standalone build, apply additional plugins and set different dependencies.
  apply(plugin = "org.metaborg.gradle.config.root-project")
  apply(plugin = "org.metaborg.gitonium")
  spoofax2Version = System.getProperty("spoofax2Version")
  spoofax2CoreDependency = "org.metaborg:org.metaborg.spoofax.core:$spoofax2Version"
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
      // Allow duplicates, as Spoofax 2 has several duplicate things on the classpath.
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
  }
} else {
  spoofax2Version = ext["spoofax2Version"]!!.toString()
  spoofax2CoreDependency = "$group:org.metaborg.spoofax.core:$version"
  dependencies {
    api("$group:org.metaborg.spoofax.meta.core:$version")
    api("$group:org.metaborg.spt.core:$version")
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
  org.metaborg.spoofax.meta.core depends on older version of several artifacts. Due to an issue in Gradle, the first
  version of those artifacts that are loaded will be used by code in plugins that react to certain Gradle events, such
  as Project#afterEvaluate. Since the old versions do not have certain APIs, this will fail. Therefore, we force the
  versions to the latest ones.
  */
  api("org.metaborg:resource:0.14.1")
  api("org.metaborg:common:0.11.0")
  api("org.metaborg:pie.runtime:0.21.0")
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

// Add generated resources directory as a resource source directory.
val generatedResourcesDir = project.buildDir.resolve("generated/resources")
sourceSets {
  main {
    resources {
      srcDir(generatedResourcesDir)
    }
  }
}
// Task that writes properties to a config.properties file, which is used in the plugin.
val propertiesFile = generatedResourcesDir.resolve("config.properties")
val generatePropertiesTask = tasks.register("generateConfigProperties") {
  inputs.property("spoofax2Version", spoofax2Version)
  inputs.property("spoofax2CoreDependency", spoofax2CoreDependency)
  outputs.file(propertiesFile)
  doLast {
    val properties = NonShittyProperties()
    properties.setProperty("spoofax2Version", spoofax2Version)
    properties.setProperty("spoofax2CoreDependency", spoofax2CoreDependency)
    propertiesFile.parentFile.run { if(!exists()) mkdirs() }
    propertiesFile.bufferedWriter().use {
      properties.storeWithoutDate(it)
    }
  }
}
tasks.compileJava.configure { dependsOn(generatePropertiesTask) }
tasks.processResources.configure { dependsOn(generatePropertiesTask) }
// Custom properties class that does not write the current date, fixing incrementality.
class NonShittyProperties : java.util.Properties() {
  fun storeWithoutDate(writer: java.io.BufferedWriter) {
    val e: java.util.Enumeration<*> = keys()
    while(e.hasMoreElements()) {
      val key = e.nextElement()
      val value = get(key)
      writer.write("$key=$value")
      writer.newLine()
    }
    writer.flush()
  }
}
