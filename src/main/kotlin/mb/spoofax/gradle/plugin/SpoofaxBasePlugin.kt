package mb.spoofax.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.metaborg.core.MetaborgConstants
import org.metaborg.spoofax.core.SpoofaxConstants

open class SpoofaxBasePlugin : Plugin<Project> {
  companion object {
    const val compileLanguageConfig = "spoofaxCompileLanguage"
    const val sourceLanguageConfig = "spoofaxSourceLanguage"
    const val languageConfig = "spoofaxLanguage"

    const val spoofaxLanguageExtension = "spoofax-language"

    const val defaultMetaborgGroup = MetaborgConstants.METABORG_GROUP_ID
    const val defaultMetaborgVersion = MetaborgConstants.METABORG_VERSION
  }

  override fun apply(project: Project) {
    val compileLanguageConfig = project.configurations.create(compileLanguageConfig) {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = false
    }
    val sourceLanguageConfig = project.configurations.create(sourceLanguageConfig) {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = false
    }
    project.configurations.create(languageConfig) {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = true
      isCanBeResolved = true
      extendsFrom(compileLanguageConfig, sourceLanguageConfig)
    }
  }
}

val Project.compileLanguageConfig get() = this.configurations.getByName(SpoofaxBasePlugin.compileLanguageConfig)
val Project.sourceLanguageConfig get() = this.configurations.getByName(SpoofaxBasePlugin.sourceLanguageConfig)
val Project.languageConfig get() = this.configurations.getByName(SpoofaxBasePlugin.languageConfig)
