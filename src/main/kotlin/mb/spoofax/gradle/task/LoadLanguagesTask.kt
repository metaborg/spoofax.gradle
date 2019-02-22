package mb.spoofax.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.ILanguageDiscoveryService
import org.metaborg.core.resource.IResourceService
import org.metaborg.spoofax.core.Spoofax
import javax.inject.Inject

fun TaskContainer.registerLoadLanguagesTask(spoofax: Spoofax, languageConfig: Configuration, name: String = "spoofaxLoadLanguages") =
  register(name, LoadLanguagesTask::class.java, spoofax.resourceService, spoofax.languageDiscoveryService, languageConfig)

open class LoadLanguagesTask @Inject constructor(
  private val resourceService: IResourceService,
  private val languageDiscoveryService: ILanguageDiscoveryService,
  private val languageConfig: Configuration
) : DefaultTask() {
  init {
    dependsOn(languageConfig)
  }

  @InputFiles
  fun languageConfigFiles(): FileCollection = languageConfig

  @TaskAction
  fun execute() {
    languageConfig.forEach { spoofaxLanguageFile ->
      val spoofaxLanguageLoc = resourceService.resolve(spoofaxLanguageFile)
      try {
        languageDiscoveryService.languageFromArchive(spoofaxLanguageLoc)
      } catch(e: MetaborgException) {
        throw GradleException("Failed to load language from $spoofaxLanguageFile", e)
      }
    }
  }
}
