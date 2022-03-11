package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.configureSafely
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideConfig
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.language.LanguageIdentifier

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

    val extension = SpoofaxProjectExtension(project)
    project.extensions.add("spoofaxProject", extension)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension)
    }
  }

  private fun configureAfterEvaluate(project: Project, extension: SpoofaxProjectExtension) {
    configureProjectAfterEvaluate(project, extension)
    configureBuildTask(project, extension)
    project.tasks.registerSpoofaxCleanTask().configureSafely {
      this.extension.set(extension)
    }
    configureTestTask(project, extension)
  }

  private fun configureProjectAfterEvaluate(project: Project, extension: SpoofaxProjectExtension) {
    // Add a dependency to Spoofax core.
    extension.addSpoofaxCoreDependency()
    // Add the Spoofax repository.
    extension.addSpoofaxRepository()
    // Add dependencies to corresponding configurations. // HACK: use shared Spoofax instance.
    extension.addDependenciesToProject(SpoofaxInstance.getShared(project.gradle).spoofax.getProject(project).config())
  }

  private fun configureBuildTask(project: Project, extension: SpoofaxProjectExtension) {
    val task = project.tasks.registerSpoofaxBuildTask()
    task.configureSafely {
      // Task dependencies:
      // - Language files, which influences which languages are loaded.
      val languageFiles = project.languageFiles
      dependsOn(languageFiles.allDependencies)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // Inputs:
      // - Any file in the project directory, with matching include and exclude patterns.
      inputs.files(project.fileTree(".") {
        include(*extension.inputIncludePatterns.get().toTypedArray())
        exclude(*extension.inputExcludePatterns.get().toTypedArray())
      })
      // Outputs:
      // - Any file in the project directory, with matching include and exclude patterns.
      outputs.files(project.fileTree(".") {
        include(*extension.outputIncludePatterns.get().toTypedArray())
        exclude(*extension.outputExcludePatterns.get().toTypedArray())
      })

      doFirst {
        // Fist override configuration, and load languages and dialects
        spoofaxBuildService.finalizeAndGet().run {
          lazyOverrideConfig(extension, configOverrides, spoofax)
          lazyLoadLanguages(project.languageFiles, project, spoofax)
          lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
        }
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.BUILD_TASK_NAME).dependsOn(task)
    project.plugins.withId("java") {
      // Some languages generate Java code, so make sure to run the Spoofax build before compiling Java code.
      project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME).dependsOn(task)
    }
  }

  private fun configureTestTask(
    project: Project,
    extension: SpoofaxProjectExtension
  ) {
    project.tasks.registerSpoofaxTestTask(extension).configureSafely {
      // Task dependencies:
      // - Language files, which influences which languages are loaded.
      val languageFiles = project.languageFiles
      dependsOn(languageFiles.allDependencies)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories

      // Test the specified language under test.
      languageUnderTest.set(extension.languageUnderTest)

      doFirst {
        // Fist override configuration, and load languages and dialects
        spoofaxBuildService.finalizeAndGet().run {
          lazyOverrideConfig(extension, configOverrides, spoofax)
          lazyLoadLanguages(project.languageFiles, project, spoofax)
          lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
        }
      }
    }
  }
}
