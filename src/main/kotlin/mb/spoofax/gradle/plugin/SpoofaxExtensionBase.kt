package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.util.toGradleDependency
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.*
import org.metaborg.core.config.IProjectConfig

open class SpoofaxExtensionBase(private val project: Project) {
  var addCompileDependenciesFromMetaborgYaml: Boolean = true
  var addSourceDependenciesFromMetaborgYaml: Boolean = true
  var addJavaDependenciesFromMetaborgYaml: Boolean = true


  internal fun addDependenciesToProject(config: IProjectConfig) {
    if(addCompileDependenciesFromMetaborgYaml) {
      for(langId in config.compileDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(project.compileLanguage.name, dependency)
      }
    }
    if(addSourceDependenciesFromMetaborgYaml) {
      for(langId in config.sourceDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(project.sourceLanguage.name, dependency)
      }
    }
    if(addJavaDependenciesFromMetaborgYaml && project.plugins.hasPlugin(JavaPlugin::class.java)) {
      for(id in config.javaDeps()) {
        val dependency = id.toGradleDependency(project)
        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).dependencies.add(dependency)
      }
    }
  }

  internal fun addSpoofaxRepo() {
    project.repositories {
      maven("https://artifacts.metaborg.org/content/groups/public/")
    }
  }
}
