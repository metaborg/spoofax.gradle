package mb.spoofax.gradle.util

import mb.spoofax.gradle.plugin.SpoofaxExtensionBase
import mb.spoofax.gradle.plugin.SpoofaxLangSpecExtension
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
import java.io.File

internal fun lazyOverrideConfig(extension: SpoofaxExtensionBase, configOverrides: SpoofaxGradleConfigOverrides, spoofax: Spoofax) =
  lazilyDo(extension.project, "overrodeDependenciesInConfig") {
    // Override the metaborgVersion, language identifier, the Stratego format, and language contributions in the configuration, with values from the extension.
    extension.overrideMetaborgVersion(configOverrides)
    extension.overrideIdentifiers(configOverrides)
    if(extension is SpoofaxLangSpecExtension) { // HACK: cast. put this somewhere else
      extension.overrideStrategoFormat(configOverrides)
      extension.overrideLanguageContributions(configOverrides)
    }
    // Override dependencies
    extension.overrideDependencies(configOverrides)
    // Recreate project to force configuration to be updated.
    spoofax.recreateProject(extension.project)
  }

internal fun lazyLoadLanguages(languageConfig: Configuration, project: Project, spoofax: Spoofax) =
  lazilyDo(project, "loadedLanguagesFrom-" + languageConfig.name) {
    languageConfig.forEach { spoofaxLanguageFile ->
      val spoofaxLanguageLoc = spoofax.resourceService.resolve(spoofaxLanguageFile)
      try {
        spoofax.languageDiscoveryService.componentFromArchive(spoofaxLanguageLoc)
      } catch(e: MetaborgException) {
        throw GradleException("Failed to load language from $spoofaxLanguageFile", e)
      }
    }
  }

internal fun lazyLoadDialects(projectLoc: FileObject, project: Project, spoofax: Spoofax) =
  lazilyDo(project, "loadedDialects") {
    val resources = ResourceUtils.find(projectLoc, SpoofaxIgnoresSelector())
    val creations = ResourceUtils.toChanges(resources, ResourceChangeKind.Create)
    spoofax.dialectProcessor.update(projectLoc, creations)
  }

internal fun lazyLoadCompiledLanguage(archiveLoc: FileObject, project: Project, spoofax: Spoofax) =
  lazilyDo(project, "loadedCompiledLanguage") {
    spoofax.languageDiscoveryService.languagesFromArchive(archiveLoc)
  }

internal fun lazyLoadCompiledLanguage(archiveFile: File, project: Project, spoofax: Spoofax) =
  lazyLoadCompiledLanguage(spoofax.resolve(archiveFile), project, spoofax)

internal fun lazilyDo(project: Project, id: String, func: () -> Unit) {
  if(project.extra.has(id)) return
  func()
  project.extra.set(id, true)
}
