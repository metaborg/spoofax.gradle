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

open class SpoofaxExtensionBase internal constructor(internal val project: Project) {
  var addCompileDependenciesFromMetaborgYaml: Property<Boolean> = project.objects.property()
  var addSourceDependenciesFromMetaborgYaml: Property<Boolean> = project.objects.property()
  var addJavaDependenciesFromMetaborgYaml: Property<Boolean> = project.objects.property()
  val addSpoofaxCoreDependency: Property<Boolean> = project.objects.property()
  val addSpoofaxRepository: Property<Boolean> = project.objects.property()

  init {
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
    addSpoofaxCoreDependency.finalizeValue()
    if(addSpoofaxCoreDependency.get() && project.plugins.hasPlugin(JavaPlugin::class.java)) {
      project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).dependencies
        .add(project.dependencies.create("org.metaborg", "org.metaborg.spoofax.core", MetaborgConstants.METABORG_VERSION))
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
}
