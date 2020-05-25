package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.util.toGradleDependency
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.ReusableAction
import org.gradle.api.model.ObjectFactory
import org.metaborg.core.MetaborgConstants
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion
import org.metaborg.spoofax.core.SpoofaxConstants
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class SpoofaxBasePlugin @Inject constructor(
  private val objectFactory: ObjectFactory,
  private val softwareComponentFactory: SoftwareComponentFactory
) : Plugin<Project> {
  companion object {
    const val spoofaxLanguageUsage = "spoofax-language"


    const val compileLanguage = "compileLanguage"
    const val sourceLanguage = "sourceLanguage"

    const val languageArchive = "languageArchive"

    const val compileLanguageFiles = "compileLanguageFiles"
    const val sourceLanguageFiles = "sourceLanguageFiles"
    const val languageFiles = "languageFiles"
    const val sptLanguageFiles = "sptLanguageFiles"

    const val spoofaxLanguageComponent = "spoofax-language"


    const val spoofaxLanguageType = "spoofax-language"
    const val spoofaxLanguageExtension = "spoofax-language"


    val sptId = LanguageIdentifier(MetaborgConstants.METABORG_GROUP_ID, SpoofaxConstants.LANG_SPT_ID, LanguageVersion.parse(MetaborgConstants.METABORG_VERSION))
  }

  override fun apply(project: Project) {
    // Attributes
    val spoofaxLanguageUsage = objectFactory.named(Usage::class.java, spoofaxLanguageUsage)


    // User-facing configurations
    val compileLanguage = project.configurations.create(compileLanguage) {
      description = "Language dependencies for which the compiler is executed"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }
    val sourceLanguage = project.configurations.create(sourceLanguage) {
      description = "Language dependencies for which the sources are available"
      isCanBeConsumed = false
      isCanBeResolved = false
      isVisible = false
    }


    // Consumable configurations
    val languageArchive = project.configurations.create(languageArchive) {
      description = "Language archive"
      isCanBeConsumed = true
      isCanBeResolved = false
      isVisible = false
      isTransitive = false // Not transitive, as language dependencies are not transitive.
      attributes.attribute(Usage.USAGE_ATTRIBUTE, spoofaxLanguageUsage)
    }


    // Internal (resolvable) configurations
    project.configurations.create(compileLanguageFiles) {
      description = "Language files for which the compiler is executed"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Not transitive, as language dependencies are not transitive.
      extendsFrom(compileLanguage)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, spoofaxLanguageUsage)
    }
    project.configurations.create(sourceLanguageFiles) {
      description = "Language files for which sources are available"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Not transitive, as language dependencies are not transitive.
      extendsFrom(sourceLanguage)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, spoofaxLanguageUsage)
    }
    project.configurations.create(languageFiles) {
      description = "Language files"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Not transitive, as language dependencies are not transitive.
      extendsFrom(compileLanguage, sourceLanguage)
      attributes.attribute(Usage.USAGE_ATTRIBUTE, spoofaxLanguageUsage)
    }
    project.configurations.create(sptLanguageFiles) {
      description = "SPT language files"
      isCanBeConsumed = false
      isCanBeResolved = true
      isVisible = false
      isTransitive = false // Not transitive, as language dependencies are not transitive.
      attributes.attribute(Usage.USAGE_ATTRIBUTE, spoofaxLanguageUsage)
      defaultDependencies {
        this.add(sptId.toGradleDependency(project))
      }
    }


    /*
    Make Usage attribute "java-runtime" compatible with "spoofax-language", such that Spoofax languages published to
    a Maven repository by the Spoofax 2 Maven plugin, which have the Usage attribute "java-runtime", can be consumed
    by this plugin.
    */
    project.dependencies.attributesSchema {
      attribute(Usage.USAGE_ATTRIBUTE) {
        compatibilityRules.add(SpoofaxUsageCompatibilityRules::class.java)
      }
    }


    // Create a spoofax-language software component.
    val spoofaxLanguageComponent = softwareComponentFactory.adhoc(spoofaxLanguageComponent)
    spoofaxLanguageComponent.addVariantsFromConfiguration(languageArchive) {
      skip() // No transitive dependencies, so we can skip everything.
    }
    project.components.add(spoofaxLanguageComponent)
  }
}

val Project.compileLanguage: Configuration get() = this.configurations.getByName(SpoofaxBasePlugin.compileLanguage)
val Project.sourceLanguage: Configuration get() = this.configurations.getByName(SpoofaxBasePlugin.sourceLanguage)

internal val Project.languageArchive: Configuration get() = this.configurations.getByName(SpoofaxBasePlugin.languageArchive)

internal val Project.compileLanguageFiles: Configuration get() = this.configurations.getByName(SpoofaxBasePlugin.compileLanguageFiles)
internal val Project.sourceLanguageFiles: Configuration get() = this.configurations.getByName(SpoofaxBasePlugin.sourceLanguageFiles)
internal val Project.languageFiles: Configuration get() = this.configurations.getByName(SpoofaxBasePlugin.languageFiles)
internal val Project.sptLanguageFiles: Configuration get() = this.configurations.getByName(SpoofaxBasePlugin.sptLanguageFiles)

internal class SpoofaxUsageCompatibilityRules : AttributeCompatibilityRule<Usage>, ReusableAction {
  override fun execute(details: CompatibilityCheckDetails<Usage>) {
    val consumerValue = details.consumerValue!!.name
    val producerValue = details.producerValue!!.name
    if(consumerValue == SpoofaxBasePlugin.spoofaxLanguageComponent && producerValue == Usage.JAVA_RUNTIME) {
      details.compatible()
      return
    }
  }
}
