package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.*
import org.metaborg.core.action.CompileGoal
import org.metaborg.core.build.BuildInputBuilder
import org.metaborg.core.build.dependency.IDependencyService
import org.metaborg.core.build.paths.ILanguagePathService
import org.metaborg.core.language.ILanguageService
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.messages.StreamMessagePrinter
import org.metaborg.core.project.IProject
import org.metaborg.core.source.ISourceTextService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.build.ISpoofaxBuilder
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector
import javax.inject.Inject

fun TaskContainer.registerSpoofaxBuildTask(
  spoofax: Spoofax,
  spoofaxProject: IProject,
  name: String = "spoofaxBuild"
) = register(
  name,
  SpoofaxBuildTask::class.java,
  spoofax.languageService,
  spoofax.sourceTextService,
  spoofax.dependencyService,
  spoofax.languagePathService,
  spoofax.builder,
  spoofaxProject
)

open class SpoofaxBuildTask @Inject constructor(
  private val languageService: ILanguageService,
  private val sourceTextService: ISourceTextService,
  private val dependencyService: IDependencyService,
  private val languagePathService: ILanguagePathService,
  private val builder: ISpoofaxBuilder,
  private val spoofaxProject: IProject
) : DefaultTask() {
  private val languageIds: MutableList<LanguageIdentifier> = mutableListOf()
  private val pardonedLanguages: MutableList<String> = mutableListOf()

  fun addLanguage(languageId: LanguageIdentifier) {
    languageIds.add(languageId)
  }

  fun addPardonedLanguage(languageName: String) {
    pardonedLanguages.add(languageName)
  }


  @Input
  fun getSpoofaxProject(): IProject {
    return spoofaxProject
  }

  @Input
  fun getLanguageIds(): List<LanguageIdentifier> {
    return languageIds
  }

  @Input
  fun getPardonedLanguages(): List<String> {
    return pardonedLanguages
  }

  @TaskAction
  private fun execute() {
    // TODO: can make this incremental based on changes in the project directory?
    val inputBuilder = BuildInputBuilder(spoofaxProject).run {
      if(!languageIds.isEmpty()) {
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
      withMessagePrinter(StreamMessagePrinter(sourceTextService, true, true, System.out, System.out, System.out))
      withThrowOnErrors(true)
      if(!pardonedLanguages.isEmpty()) {
        withPardonedLanguageStrings(pardonedLanguages)
      }
      addTransformGoal(CompileGoal())
    }
    val buildInput = inputBuilder.build(dependencyService, languagePathService)
    val output = builder.build(buildInput)
    if(!output.success()) {
      throw GradleException("Spoofax build failed; errors encountered")
    }
  }
}
