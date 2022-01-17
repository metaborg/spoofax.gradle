package mb.spoofax.gradle.task

import mb.spoofax.gradle.plugin.languageFiles
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getLanguageSpecification
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideConfig
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput

fun TaskContainer.registerSpoofaxCleanLanguageSpecTask(name: String = "spoofaxCleanLanguageSpec"): TaskProvider<SpoofaxCleanLanguageSpecTask> =
  register(name, SpoofaxCleanLanguageSpecTask::class)

abstract class SpoofaxCleanLanguageSpecTask : SpoofaxTask() {
  @get:Input
  abstract val spoofaxCleanTask: Property<TaskProvider<*>>

  init {
    mustRunAfter(spoofaxCleanTask)
    project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME).dependsOn(this)
  }

  @TaskAction
  fun execute() {
    val spoofaxBuildService = spoofaxBuildService.finalizeAndGet()
    spoofaxBuildService.run {
      // Fist override configuration, and load languages and dialects
      lazyOverrideConfig(extension, configOverrides, spoofax)
      lazyLoadLanguages(project.languageFiles, project, spoofax)
      lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)

      // Get language spec, with overridden configuration.
      val languageSpec = spoofaxMeta.getLanguageSpecification(project)
      val languageSpecBuildInput = LanguageSpecBuildInput(languageSpec)

      languageSpecBuilder.clean(languageSpecBuildInput)
    }
  }
}
