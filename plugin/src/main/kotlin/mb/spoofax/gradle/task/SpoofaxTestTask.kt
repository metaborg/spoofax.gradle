package mb.spoofax.gradle.task

import com.google.inject.Injector
import mb.spoofax.gradle.plugin.SpoofaxBasePlugin
import mb.spoofax.gradle.plugin.sptLanguageFiles
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.*
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.ILanguageService
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.project.IProject
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spt.core.SPTRunner
import javax.inject.Inject

fun TaskContainer.registerSpoofaxTestTask(
  spoofax: Spoofax,
  sptInjector: Injector,
  spoofaxProjectSupplier: () -> IProject,
  name: String = "spoofaxTest"
) = register(
  name,
  SpoofaxTestTask::class.java,
  spoofax.languageService,
  sptInjector.getInstance(SPTRunner::class.java),
  spoofaxProjectSupplier
)

open class SpoofaxTestTask @Inject constructor(
  private val languageService: ILanguageService,
  private val sptRunner: SPTRunner,
  private val spoofaxProjectSupplier: () -> IProject
) : DefaultTask() {
  init {
    // Only execute task if languageUnderTest is set.
    onlyIf { languageUnderTest.isPresent }

    // Task dependencies

    /// * SPT language dependency configuration, which influences which SPT language is loaded.
    val sptLanguageFiles = project.sptLanguageFiles
    dependsOn(sptLanguageFiles)
    inputs.files({ sptLanguageFiles }) // Closure to defer to task execution time.
  }


  // Inputs

  /// * Identifier of the language under test.
  @Input
  val languageUnderTest: Property<LanguageIdentifier> = project.objects.property()

  /// * All SPT files
  @InputFiles
  val sptFiles = project.fileTree(".") {
    include("**/*.spt")
    exclude("/target", "/build", "/.gradle", "/.git")
  }


  // Outputs

  /// * SPT result file.
  @OutputFile
  val sptResultFile = project.buildDir.resolve("spt/result.txt")


  @TaskAction
  fun execute() {
    val sptLangImpl = languageService.getImpl(SpoofaxBasePlugin.sptId)
      ?: throw GradleException("Failed to get SPT language implementation (${SpoofaxBasePlugin.sptId})")
    languageUnderTest.finalizeValue()
    val langUnderTest = languageService.getImpl(languageUnderTest.get())
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
