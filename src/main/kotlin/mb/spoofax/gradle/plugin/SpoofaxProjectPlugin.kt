package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.SpoofaxInstanceCache
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideDependenciesInConfig
import mb.spoofax.gradle.util.overrideConfig
import mb.spoofax.gradle.util.recreateProject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.spoofax.core.Spoofax

@Suppress("unused", "UnstableApiUsage")
class SpoofaxProjectPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)

    val extension = SpoofaxProjectExtension(project)
    project.extensions.add("spoofaxProject", extension)

    val instance = SpoofaxInstanceCache[project]
    instance.reset()
    instance.spoofax.recreateProject(project)

    project.afterEvaluate {
      configureAfterEvaluate(project, extension, instance)
    }

    project.gradle.buildFinished {
      instance.reset()
    }
  }

  private fun configureAfterEvaluate(project: Project, extension: SpoofaxProjectExtension, spoofaxInstance: SpoofaxInstance) {
    spoofaxInstance.run {
      configureProjectAfterEvaluate(project, extension, spoofax)
      configureBuildTask(project, extension, spoofax)
      configureCleanTask(project, extension, spoofax)
    }
  }

  private fun configureProjectAfterEvaluate(project: Project, extension: SpoofaxProjectExtension, spoofax: Spoofax) {
    // Override the language identifier and metaborgVersion in the configuration with values from the Gradle project.
    project.overrideConfig(extension, spoofax.injector, false)

    // Add dependencies to corresponding configurations.
    val spoofaxProject = spoofax.getProject(project)
    extension.addDependenciesToProject(spoofaxProject.config())
  }

  private fun configureBuildTask(project: Project, extension: SpoofaxProjectExtension, spoofax: Spoofax) {
    val languageFiles = project.languageFiles
    val task = project.tasks.registerSpoofaxBuildTask(spoofax, { spoofax.getProject(project) }, "spoofaxBuild")
    task.configure {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // Inputs:
      // * Any file in the project directory.
      inputs.dir(project.projectDir)
      // Outputs: none? TODO:

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofax.injector)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME).dependsOn(task)
    project.plugins.withId("java") {
      // Some languages generate Java code, so make sure to run the Spoofax build before compiling Java code.
      project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME).dependsOn(task)
    }
  }

  private fun configureCleanTask(project: Project, extension: SpoofaxProjectExtension, spoofax: Spoofax) {
    val spoofaxProject = spoofax.getProject(project)
    val languageFiles = project.languageFiles
    val task = project.tasks.registerSpoofaxCleanTask(spoofax, spoofaxProject)
    task.configure {
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofax.injector)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofaxProject.location(), project, spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME).dependsOn(task)
  }
}

@Suppress("unused")
open class SpoofaxProjectExtension(project: Project) : SpoofaxExtensionBase(project)
