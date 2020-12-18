package mb.spoofax.gradle.util

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

fun lazyOverrideDependenciesInConfig(extension: SpoofaxExtensionBase) =
  lazilyDo(extension.project, "overrodeDependenciesInConfig") {
    extension.overrideDependencies()
    extension.instance.spoofax.recreateProject(extension.project) // Recreate project to force configuration to be updated.
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

fun lazilyDo(project: Project, id: String, func: () -> Unit) {
  if(project.extra.has(id)) return
  func()
  project.extra.set(id, true)
}
