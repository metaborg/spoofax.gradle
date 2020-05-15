package mb.spoofax.gradle.util

import com.google.inject.Injector
import mb.spoofax.gradle.plugin.SpoofaxExtensionBase
import org.apache.commons.vfs2.FileObject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.kotlin.dsl.*
import org.metaborg.core.MetaborgException
import org.metaborg.core.resource.ResourceChangeKind
import org.metaborg.core.resource.ResourceUtils
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector

fun lazyOverrideDependenciesInConfig(
  project: Project,
  extension: SpoofaxExtensionBase,
  spoofax: Spoofax,
  injector: Injector
) = lazilyDo(project, "overrodeConfiguration") {
  project.overrideConfig(extension, injector, true)
  spoofax.recreateProject(project) // Recreate project to force configuration to be updated.
}

fun lazyLoadLanguages(languageConfig: Configuration, project: Project, spoofax: Spoofax) =
  lazilyDo(project, "loadedLanguagesFrom-" + languageConfig.name) {
    languageConfig.forEach { spoofaxLanguageFile ->
      val spoofaxLanguageLoc = spoofax.resourceService.resolve(spoofaxLanguageFile)
      try {
        spoofax.languageDiscoveryService.languageFromArchive(spoofaxLanguageLoc)
      } catch(e: MetaborgException) {
        throw GradleException("Failed to load language from $spoofaxLanguageFile", e)
      }
    }
  }

fun lazyLoadDialects(projectLoc: FileObject, project: Project, spoofax: Spoofax) =
  lazilyDo(project, "loadedDialects") {
    val resources = ResourceUtils.find(projectLoc, SpoofaxIgnoresSelector())
    val creations = ResourceUtils.toChanges(resources, ResourceChangeKind.Create)
    spoofax.dialectProcessor.update(projectLoc, creations)
  }

fun lazyLoadCompiledLanguage(archiveLoc: FileObject, project: Project, spoofax: Spoofax) =
  lazilyDo(project, "loadedCompiledLanguage") {
    spoofax.languageDiscoveryService.languageFromArchive(archiveLoc)
  }

inline fun lazilyDo(project: Project, id: String, func: () -> Unit) {
  if(project.extra.has(id)) return
  func()
  project.extra.set(id, true)
}
