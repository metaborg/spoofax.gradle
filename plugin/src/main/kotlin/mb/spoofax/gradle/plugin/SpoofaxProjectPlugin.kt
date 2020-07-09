package mb.spoofax.gradle.plugin

import com.google.inject.Injector
import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.SpoofaxInstanceCache
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideDependenciesInConfig
import mb.spoofax.gradle.util.overrideIdentifiers
import mb.spoofax.gradle.util.overrideMetaborgVersion
import mb.spoofax.gradle.util.recreateProject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.spoofax.core.Spoofax

@Suppress("unused")
open class SpoofaxProjectExtension(project: Project) : SpoofaxExtensionBase(project) {
  var languageUnderTest: Property<LanguageIdentifier> = project.objects.property()

  val inputIncludePatterns: SetProperty<String> = project.objects.setProperty()
  val inputExcludePatterns: SetProperty<String> = project.objects.setProperty()
  val outputIncludePatterns: SetProperty<String> = project.objects.setProperty()
  val outputExcludePatterns: SetProperty<String> = project.objects.setProperty()

  init {
    inputIncludePatterns.convention(setOf())
    inputExcludePatterns.convention(setOf("/target", "/build", "/out", "/bin", "/.gradle", "/.git"))
    outputIncludePatterns.convention(setOf())
    outputExcludePatterns.convention(setOf("/out", "/bin", "/.gradle", "/.git"))
  }
}

@Suppress("unused", "UnstableApiUsage")
class SpoofaxProjectPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)

    val instance = SpoofaxInstanceCache[project]
    instance.reset()
    instance.spoofax.recreateProject(project)

    val extension = SpoofaxProjectExtension(project)
    project.extensions.add("spoofaxProject", extension)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension, instance)
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
      configureTestTask(project, extension, spoofax, sptInjector)
    }
  }

  private fun configureProjectAfterEvaluate(project: Project, extension: SpoofaxProjectExtension, spoofax: Spoofax) {
    // Override the metaborgVersion and language identifier in the configuration, with values from the extension.
    extension.overrideMetaborgVersion()
    extension.overrideIdentifiers()

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
      // * Any file in the project directory, with matching include and exclude patterns.
      inputs.files(project.fileTree(".") {
        include(*extension.inputIncludePatterns.get().toTypedArray())
        exclude(*extension.inputExcludePatterns.get().toTypedArray())
      })
      // Outputs:
      // * Any file in the project directory, with matching include and exclude patterns.
      outputs.files(project.fileTree(".") {
        include(*extension.outputIncludePatterns.get().toTypedArray())
        exclude(*extension.outputExcludePatterns.get().toTypedArray())
      })

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
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
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofaxProject.location(), project, spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME).dependsOn(task)
  }

  private fun configureTestTask(
    project: Project,
    extension: SpoofaxProjectExtension,
    spoofax: Spoofax,
    sptInjector: Injector
  ) {
    val spoofaxProject = spoofax.getProject(project)
    val languageFiles = project.languageFiles
    val task = project.tasks.registerSpoofaxTestTask(spoofax, sptInjector, { spoofax.getProject(project) })
    task.configure {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.

      // Test the specified language under test.
      languageUnderTest.set(extension.languageUnderTest)

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofaxProject.location(), project, spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }
}
