package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.util.configureSpoofaxLanguageArtifact
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.*

open class SpoofaxExtensionBase(private val project: Project) {
  var metaborgGroup: String = SpoofaxBasePlugin.defaultMetaborgGroup
  var metaborgVersion: String = SpoofaxBasePlugin.defaultMetaborgVersion


  private val compileLanguageConfig = project.compileLanguageConfig

  fun addCompileLanguageDep(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(compileLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }

  fun addCompileLanguageProjectDep(path: String): Dependency {
    val dependency = project.dependencies.project(path, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(compileLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }


  private val sourceLanguageConfig = project.sourceLanguageConfig

  fun addSourceLanguageDep(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(sourceLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }

  fun addSourceLanguageProjectDep(path: String): Dependency {
    val dependency = project.dependencies.project(path, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(sourceLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }


  private val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

  fun addSpoofaxCoreDep(): Dependency {
    val dependency = project.dependencies.create(metaborgGroup, "org.metaborg.spoofax.core", metaborgVersion)
    javaApiConfig.dependencies.add(dependency)
    return dependency
  }


  fun addSpoofaxRepos() {
    project.repositories {
      maven("https://artifacts.metaborg.org/content/groups/public/")
    }
  }
}
