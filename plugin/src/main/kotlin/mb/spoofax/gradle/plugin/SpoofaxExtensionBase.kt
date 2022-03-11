package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.toGradleDependency
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.*
import org.metaborg.core.MetaborgConstants
import org.metaborg.core.config.IProjectConfig
import java.io.IOException
import java.io.InputStream
import java.util.*

@Suppress("UnstableApiUsage", "MemberVisibilityCanBePrivate")
open class SpoofaxExtensionBase internal constructor(internal val project: Project) {
  val spoofax2Version: Property<String> = project.objects.property()
  val spoofax2CoreDependency: Property<String> = project.objects.property()
  val addCompileDependenciesFromMetaborgYaml: Property<Boolean> = project.objects.property()
  val addSourceDependenciesFromMetaborgYaml: Property<Boolean> = project.objects.property()
  val addJavaDependenciesFromMetaborgYaml: Property<Boolean> = project.objects.property()
  val addSpoofaxCoreDependency: Property<Boolean> = project.objects.property()
  val addSpoofaxRepository: Property<Boolean> = project.objects.property()

  val sharedExcludes = setOf("/out", "/bin", "/.gradle", "/.git")
  val sharedInputExcludes = setOf("/src-gen", "/target", "/build") + sharedExcludes
  val sharedOutputExcludes = sharedExcludes

  val defaultInputExcludePatterns: SetProperty<String> = project.objects.setProperty()
  val defaultOutputExcludePatterns: SetProperty<String> = project.objects.setProperty()

  init {
    val configProperties = configProperties()
    spoofax2Version.convention(configProperties["spoofax2Version"]?.toString() ?: MetaborgConstants.METABORG_VERSION)
    spoofax2CoreDependency.convention(configProperties["spoofax2CoreDependency"]?.toString()
      ?: "org.metaborg:org.metaborg.spoofax.core:$spoofax2Version")
    addCompileDependenciesFromMetaborgYaml.convention(true)
    addSourceDependenciesFromMetaborgYaml.convention(true)
    addJavaDependenciesFromMetaborgYaml.convention(true)
    addSpoofaxCoreDependency.convention(true)
    addSpoofaxRepository.convention(true)

    defaultInputExcludePatterns.convention(sharedInputExcludes)
    defaultOutputExcludePatterns.convention(sharedOutputExcludes)
  }

  internal fun addDependenciesToProject(config: IProjectConfig) {
    if(addCompileDependenciesFromMetaborgYaml.finalizeAndGet()) {
      for(langId in config.compileDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(project.compileLanguage.name, dependency)
      }
    }
    if(addSourceDependenciesFromMetaborgYaml.finalizeAndGet()) {
      for(langId in config.sourceDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(project.sourceLanguage.name, dependency)
      }
    }
    if(addJavaDependenciesFromMetaborgYaml.finalizeAndGet() && project.plugins.hasPlugin(JavaPlugin::class.java)) {
      for(id in config.javaDeps()) {
        val dependency = id.toGradleDependency(project)
        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).dependencies.add(dependency)
      }
    }
  }

  internal fun addSpoofaxCoreDependency() {
    if(addSpoofaxCoreDependency.finalizeAndGet() && project.plugins.hasPlugin(JavaPlugin::class.java)) {
      project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).dependencies.add(project.dependencies.create(spoofax2CoreDependency.finalizeAndGet()))
    }
  }

  internal fun addSpoofaxRepository() {
    if(addSpoofaxRepository.finalizeAndGet()) {
      project.repositories {
        maven("https://artifacts.metaborg.org/content/groups/public/")
      }
    }
  }


  private fun configProperties(): Properties {
    val properties = Properties()
    val inputStream: InputStream? = javaClass.classLoader.getResourceAsStream("config.properties")
    if(inputStream != null) {
      try {
        inputStream.use { properties.load(it) }
      } catch(e: IOException) {
        // Ignore
      }
    }
    return properties
  }
}
