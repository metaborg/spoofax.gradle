package mb.spoofax.gradle.task

import mb.spoofax.gradle.plugin.languageFiles
import mb.spoofax.gradle.util.SpoofaxBuildService
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideConfig
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.build.CleanInput
import org.metaborg.core.project.IProject

fun TaskContainer.registerSpoofaxCleanTask(name: String = "spoofaxClean"): TaskProvider<SpoofaxCleanTask> =
  register(name, SpoofaxCleanTask::class)

abstract class SpoofaxCleanTask : SpoofaxTask() {
  // Internals
  @get:Internal
  abstract val spoofaxProjectSupplier: Property<SpoofaxBuildService.() -> IProject>


  init {
    spoofaxProjectSupplier.convention { spoofax.getProject(project) }

    // Task dependencies:
    // - Language files, which influences which languages are loaded.
    val languageFiles = project.languageFiles
    dependsOn(languageFiles.allDependencies)
    inputs.files({ languageFiles }) // Closure to defer to task execution time.
    // TODO: Stratego dialects through *.tbl files in non-output directories

    project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME).dependsOn(this)
  }


  @TaskAction
  fun execute() {
    val spoofaxBuildService = spoofaxBuildService.finalizeAndGet()
    val spoofaxProjectSupplier = spoofaxProjectSupplier.finalizeAndGet()
    spoofaxBuildService.run {
      // Fist override configuration, and load languages and dialects
      lazyOverrideConfig(extension, configOverrides, spoofax)
      lazyLoadLanguages(project.languageFiles, project, spoofax)
      lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)

      // Get project, with overridden configuration.
      val project = spoofaxProjectSupplier()

      builder.clean(CleanInput(project, languageService.allImpls, null))
    }
  }
}
