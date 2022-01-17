package mb.spoofax.gradle.task

import mb.spoofax.gradle.plugin.SpoofaxBasePlugin
import mb.spoofax.gradle.plugin.sptLanguageFiles
import mb.spoofax.gradle.util.SpoofaxBuildService
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.lazyLoadLanguages
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.project.IProject
import java.io.File

fun TaskContainer.registerSpoofaxTestTask(name: String = "spoofaxTest"): TaskProvider<SpoofaxTestTask> =
  register(name, SpoofaxTestTask::class)

abstract class SpoofaxTestTask : SpoofaxTask() {
  // Internals
  @get:Internal
  abstract val spoofaxProjectSupplier: Property<SpoofaxBuildService.() -> IProject>


  // Inputs:
  /// - Identifier of the language under test.
  @get:Input
  abstract val languageUnderTest: Property<LanguageIdentifier>

  /// - All SPT files.
  @get:InputFiles
  abstract val sptFiles: Property<ConfigurableFileTree>

  // Outputs:
  /// - SPT result file.
  @get:OutputFile
  abstract val sptResultFile: Property<File>


  init {
    spoofaxProjectSupplier.convention { spoofax.getProject(project) }

    // Only execute task if languageUnderTest is set.
    onlyIf { languageUnderTest.isPresent }

    // Task dependencies:
    /// - SPT language dependency configuration, which influences which SPT language is loaded.
    val sptLanguageFiles = project.sptLanguageFiles
    dependsOn(sptLanguageFiles.allDependencies)
    inputs.files({ sptLanguageFiles }) // Closure to defer to task execution time.

    sptFiles.convention(project.fileTree(".") {
      include("**/*.spt")
      exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
    })
    sptResultFile.convention(project.buildDir.resolve("spt/result.txt"))

    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(this)
  }


  @TaskAction
  fun execute() {
    val spoofaxBuildService = spoofaxBuildService.finalizeAndGet()
    val spoofaxProjectSupplier = spoofaxProjectSupplier.finalizeAndGet()
    val languageUnderTestId = languageUnderTest.finalizeAndGet()
    val sptResultFile = sptResultFile.finalizeAndGet()
    spoofaxBuildService.run {
      // Requires SPT language to be loaded.
      lazyLoadLanguages(project.sptLanguageFiles, project, spoofax)

      val sptLangImpl = languageService.getImpl(SpoofaxBasePlugin.sptId)
        ?: throw GradleException("Failed to get SPT language implementation (${SpoofaxBasePlugin.sptId})")
      val langUnderTest = languageService.getImpl(languageUnderTestId)
        ?: languageService.getComponent(languageUnderTestId)?.contributesTo()?.firstOrNull() // HACK: attempt to get language component instead, and get the first language it contributes to.
        ?: throw GradleException("Failed to get language implementation or component under test with ID '${languageUnderTestId}'; it does not exist")
      val spoofaxProject = spoofaxProjectSupplier()
      try {
        sptRunner.test(spoofaxProject, sptLangImpl, langUnderTest)
        sptResultFile.writeText("success")
      } catch(e: MetaborgException) {
        sptResultFile.writeText("failed")
        throw GradleException("SPT tests failed", e)
      }
    }
  }
}
