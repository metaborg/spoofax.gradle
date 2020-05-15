package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
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
import java.io.OutputStream
import javax.inject.Inject

fun TaskContainer.registerSpoofaxBuildTask(
  spoofax: Spoofax,
  spoofaxProjectSupplier: () -> IProject,
  name: String = "spoofaxBuild"
) = register(
  name,
  SpoofaxBuildTask::class.java,
  spoofax.languageService,
  spoofax.sourceTextService,
  spoofax.dependencyService,
  spoofax.languagePathService,
  spoofax.builder,
  spoofaxProjectSupplier
)

open class SpoofaxBuildTask @Inject constructor(
  private val languageService: ILanguageService,
  private val sourceTextService: ISourceTextService,
  private val dependencyService: IDependencyService,
  private val languagePathService: ILanguagePathService,
  private val builder: ISpoofaxBuilder,
  private val spoofaxProjectSupplier: () -> IProject
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
  fun getLanguageIds(): List<LanguageIdentifier> {
    return languageIds
  }

  @Input
  fun getPardonedLanguages(): List<String> {
    return pardonedLanguages
  }

  @TaskAction
  private fun execute() {
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
      withMessagePrinter(StreamMessagePrinter(sourceTextService, true, true, NullOutputStream(), NullOutputStream(), System.out))
      withThrowOnErrors(true)
      if(pardonedLanguages.isNotEmpty()) {
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

class NullOutputStream : OutputStream() {
  override fun write(b: Int) {}
}
