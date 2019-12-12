package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.util.*
import org.apache.commons.vfs2.FileObject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.LanguageVersion
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.core.resource.ResourceChangeKind
import org.metaborg.core.resource.ResourceUtils
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector
import org.metaborg.spoofax.meta.core.SpoofaxExtensionModule

@Suppress("unused")
class SpoofaxProjectPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(BasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)
    // Apply Java library plugin before afterEvaluate to make configurations available to extension.
    project.pluginManager.apply(JavaLibraryPlugin::class)

    val extension = SpoofaxProjectExtension(project)
    project.extensions.add("spoofax", extension)

    // Use a null module plugin loader for Spoofax, as service loading does not work well in a Gradle environment.
    val spoofaxModulePluginLoader = NullModulePluginLoader()
    val spoofax = Spoofax(spoofaxModulePluginLoader, SpoofaxGradleModule(), SpoofaxExtensionModule())
    spoofax.configureAsHeadlessApplication()
    project.afterEvaluate { configure(this, extension, spoofax) }
    project.gradle.buildFinished {
      spoofax.close()
    }
  }

  private fun configure(project: Project, extension: SpoofaxProjectExtension, spoofax: Spoofax) {
    val compileLanguageConfig = project.compileLanguageConfig
    val sourceLanguageConfig = project.sourceLanguageConfig
    val languageConfig = project.languageConfig
    val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

    // Get project projectDir.
    val resourceSrv = spoofax.resourceService
    val projectDir = project.projectDir
    val projectLoc = resourceSrv.resolve(projectDir)

    // Override Spoofax configuration from Gradle build script.
    project.createAndAddOverride(extension, resourceSrv, spoofax.injector)

    // Create Spoofax language specification project.
    val projectService = spoofax.projectService as ISimpleProjectService
    val spoofaxProject = projectService.create(projectLoc)
      ?: throw GradleException("Project at $projectDir is not a Spoofax project")

    // Read Spoofax language specification configuration.
    val config = spoofaxProject.config()
    // Add dependencies to corresponding dependency configurations when they are empty.
    if(compileLanguageConfig.dependencies.isEmpty()) {
      for(langId in config.compileDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(compileLanguageConfig.name, dependency) {
          configureSpoofaxLanguageArtifact(dependency)
        }
      }
    }
    if(sourceLanguageConfig.dependencies.isEmpty()) {
      for(langId in config.sourceDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(sourceLanguageConfig.name, dependency) {
          configureSpoofaxLanguageArtifact(dependency)
        }
      }
    }
    if(javaApiConfig.allDependencies.isEmpty()) {
      for(id in config.javaDeps()) {
        val dependency = id.toGradleDependency(project, configuration = null)
        javaApiConfig.dependencies.add(dependency)
      }
    }


    // Spoofax build task (compile all files of all loaded languages).
    val spoofaxBuildTask = project.tasks.registerSpoofaxBuildTask(spoofax, spoofaxProject, "spoofaxBuild")
    spoofaxBuildTask.configure {
      // Task dependencies:
      // 1. Language dependencies configuration, which influences which languages are loaded.
      dependsOn(languageConfig)
      // Inputs:
      // * Files from artifacts from all dependencies in the language configuration.
      inputs.files(languageConfig)
      // * Any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: none? TODO:

      doFirst {
        // Requires languages and dialects to be loaded.
        lazyLoadLanguages(project.languageConfig, project, spoofax)
        lazyLoadDialects(projectLoc, project, spoofax)
      }
    }
    val buildTask = project.tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME)
    buildTask.dependsOn(spoofaxBuildTask)
    // Some languages generate Java code, so make sure to run the Spoofax build before compiling Java code.
    val compileJavaTask = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
    compileJavaTask.dependsOn(spoofaxBuildTask)

    // Clean tasks.
    val spoofaxCleanTask = project.tasks.registerSpoofaxCleanTask(spoofax, spoofaxProject)
    spoofaxCleanTask.configure {
      // No other inputs/outputs known: always execute.

      doFirst {
        // Requires languages and dialects to be loaded.
        lazyLoadLanguages(project.languageConfig, project, spoofax)
        lazyLoadDialects(projectLoc, project, spoofax)
      }
    }
    val cleanTask = project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME)
    cleanTask.dependsOn(spoofaxCleanTask)
  }
}

@Suppress("unused")
open class SpoofaxProjectExtension(project: Project) : SpoofaxExtensionBase(project)
