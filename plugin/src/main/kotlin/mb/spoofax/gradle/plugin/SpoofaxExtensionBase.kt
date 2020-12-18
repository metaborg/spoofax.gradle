package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.util.SpoofaxGradleConfigOverrides
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.SpoofaxInstanceCache
import mb.spoofax.gradle.util.toGradleDependency
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
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

  init {
    val configProperties = configProperties()
    spoofax2Version.convention(configProperties["spoofax2Version"]?.toString() ?: MetaborgConstants.METABORG_VERSION)
    spoofax2CoreDependency.convention(configProperties["spoofax2CoreDependency"]?.toString() ?: "org.metaborg:org.metaborg.spoofax.core:$spoofax2Version")
    addCompileDependenciesFromMetaborgYaml.convention(true)
    addSourceDependenciesFromMetaborgYaml.convention(true)
    addJavaDependenciesFromMetaborgYaml.convention(true)
    addSpoofaxCoreDependency.convention(true)
    addSpoofaxRepository.convention(true)
  }


  internal val instance: SpoofaxInstance get() = SpoofaxInstanceCache[project]

  internal val configOverrides: SpoofaxGradleConfigOverrides get() = instance.spoofaxMeta.injector.getInstance(SpoofaxGradleConfigOverrides::class.java)

  internal fun addDependenciesToProject(config: IProjectConfig) {
    addCompileDependenciesFromMetaborgYaml.finalizeValue()
    if(addCompileDependenciesFromMetaborgYaml.get()) {
      for(langId in config.compileDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(project.compileLanguage.name, dependency)
      }
    }
    addSourceDependenciesFromMetaborgYaml.finalizeValue()
    if(addSourceDependenciesFromMetaborgYaml.get()) {
      for(langId in config.sourceDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(project.sourceLanguage.name, dependency)
      }
    }
    addJavaDependenciesFromMetaborgYaml.finalizeValue()
    if(addJavaDependenciesFromMetaborgYaml.get() && project.plugins.hasPlugin(JavaPlugin::class.java)) {
      for(id in config.javaDeps()) {
        val dependency = id.toGradleDependency(project)
        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).dependencies.add(dependency)
      }
    }
  }

  internal fun addSpoofaxCoreDependency() {
    spoofax2CoreDependency.finalizeValue()
    if(addSpoofaxCoreDependency.get() && project.plugins.hasPlugin(JavaPlugin::class.java)) {
      project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).dependencies.add(project.dependencies.create(spoofax2CoreDependency.get()))
    }
  }

  internal fun addSpoofaxRepository() {
    addSpoofaxRepository.finalizeValue()
    if(addSpoofaxRepository.get()) {
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
