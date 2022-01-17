package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideConfig
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.language.LanguageIdentifier

@Suppress("unused")
open class SpoofaxTestExtension(project: Project) : SpoofaxExtensionBase(project) {
  var languageUnderTest: Property<LanguageIdentifier> = project.objects.property()
}

@Suppress("unused", "UnstableApiUsage")
class SpoofaxTestPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)

    val extension = SpoofaxTestExtension(project)
    project.extensions.add("spoofaxTest", extension)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension)
    }
  }

  private fun configureAfterEvaluate(project: Project, extension: SpoofaxTestExtension) {
    configureProjectAfterEvaluate(project, extension)
    configureTestTask(project, extension)
  }

  private fun configureProjectAfterEvaluate(project: Project, extension: SpoofaxTestExtension) {
    // Check if languageUnderTest property was set
    extension.languageUnderTest.finalizeValue()
    if(!extension.languageUnderTest.isPresent) {
      throw GradleException("spoofaxTest.languageUnderTest property was not set on $project")
    }
    // Add a dependency to Spoofax core.
    extension.addSpoofaxCoreDependency()
    // Add the Spoofax repository.
    extension.addSpoofaxRepository()
    // Add dependencies to corresponding configurations. // HACK: use shared Spoofax instance.
    extension.addDependenciesToProject(SpoofaxInstance.getShared(project.gradle).spoofax.getProject(project).config())
  }

  private fun configureTestTask(
    project: Project,
    extension: SpoofaxTestExtension
  ) {
    project.tasks.registerSpoofaxTestTask().configure {
      // Task dependencies:
      // - Language files, which influences which languages are loaded.
      val languageFiles = project.languageFiles
      dependsOn(languageFiles.allDependencies)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories

      // Test the specified language under test.
      languageUnderTest.set(extension.languageUnderTest)

      doFirst {
        // Fist override configuration, and load languages and dialects.
        spoofaxBuildService.finalizeAndGet().run {
          lazyOverrideConfig(extension, configOverrides, spoofax)
          lazyLoadLanguages(project.languageFiles, project, spoofax)
          lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
        }
      }
    }
  }
}
