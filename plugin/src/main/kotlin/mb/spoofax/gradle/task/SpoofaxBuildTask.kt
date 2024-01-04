package mb.spoofax.gradle.task

import mb.spoofax.gradle.util.LoggingOutputStream
import mb.spoofax.gradle.util.SpoofaxBuildService
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getProject
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.metaborg.core.action.CompileGoal
import org.metaborg.core.build.BuildInputBuilder
import org.metaborg.core.build.dependency.MissingDependencyException
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.messages.StreamMessagePrinter
import org.metaborg.core.project.IProject
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector
import java.io.OutputStream

fun TaskContainer.registerSpoofaxBuildTask(name: String = "spoofaxBuild"): TaskProvider<SpoofaxBuildTask> =
  register(name, SpoofaxBuildTask::class)

abstract class SpoofaxBuildTask : SpoofaxTask() {
  @get:Internal
  abstract val spoofaxProjectSupplier: Property<SpoofaxBuildService.() -> IProject>

  @get:Input
  abstract val languageIds: ListProperty<LanguageIdentifier>

  @get:Input
  abstract val pardonedLanguages: ListProperty<String>


  init {
    spoofaxProjectSupplier.convention { spoofax.getProject(project) }
  }


  @TaskAction
  private fun execute() {
    val spoofaxBuildService = spoofaxBuildService.finalizeAndGet()
    val spoofaxProjectSupplier = spoofaxProjectSupplier.finalizeAndGet()
    val languageIds = languageIds.finalizeAndGet()
    val pardonedLanguages = pardonedLanguages.finalizeAndGet()
    spoofaxBuildService.run {
      val inputBuilder = BuildInputBuilder(spoofaxProjectSupplier()).run {
        if(languageIds.isNotEmpty()) {
          withCompileDependencyLanguages(false)
          val languageImpls = languageIds.map {
            languageService.getImpl(it)
              ?: throw GradleException("Spoofax build failed; language implementation with identifier $it could not be found")
          }
          withLanguages(languageImpls)
        } else {
          withCompileDependencyLanguages(true)
        }
        withDefaultIncludePaths(true)
        withSourcesFromDefaultSourceLocations(true)
        withSelector(SpoofaxIgnoresSelector())
        val printExceptions = when(project.gradle.startParameter.showStacktrace) {
          ShowStacktrace.INTERNAL_EXCEPTIONS -> false
          ShowStacktrace.ALWAYS -> true
          ShowStacktrace.ALWAYS_FULL -> true
        }
        withMessagePrinter(StreamMessagePrinter(
          sourceTextService,
          true,
          printExceptions,
          LoggingOutputStream(project.logger, LogLevel.INFO),
          LoggingOutputStream(project.logger, LogLevel.WARN),
          LoggingOutputStream(project.logger, LogLevel.ERROR)
        ))
        withThrowOnErrors(true)
        if(pardonedLanguages.isNotEmpty()) {
          withPardonedLanguageStrings(pardonedLanguages)
        }
        addTransformGoal(CompileGoal())
      }
      val buildInput = try {
        inputBuilder.build(dependencyService, languagePathService)
      } catch (ex: MissingDependencyException) {
        val s = languageService.allComponents.joinToString(", ") { it.id().toString() }
        throw MissingDependencyException("${ex.message}. Only these exist: $s", ex)
      }
      val output = builder.build(buildInput)
      if(!output.success()) {
        throw GradleException("Spoofax build failed; errors encountered")
      }
    }
  }
}

class NullOutputStream : OutputStream() {
  override fun write(b: Int) {}
}
