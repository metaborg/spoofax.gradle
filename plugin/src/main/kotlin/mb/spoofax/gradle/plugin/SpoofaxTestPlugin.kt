package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.SpoofaxInstanceCache
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideDependenciesInConfig
import mb.spoofax.gradle.util.overrideIdentifiers
import mb.spoofax.gradle.util.overrideMetaborgVersion
import mb.spoofax.gradle.util.recreateProject
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

    val instance = SpoofaxInstanceCache[project]
    instance.reset()
    instance.spoofax.recreateProject(project)

    val extension = SpoofaxTestExtension(project)
    project.extensions.add("spoofaxTest", extension)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension, instance)
    }

    project.gradle.buildFinished {
      instance.reset()
    }
  }

  private fun configureAfterEvaluate(project: Project, extension: SpoofaxTestExtension, spoofaxInstance: SpoofaxInstance) {
    configureProjectAfterEvaluate(project, extension, spoofaxInstance)
    configureTestTask(project, extension, spoofaxInstance)
  }

  private fun configureProjectAfterEvaluate(project: Project, extension: SpoofaxTestExtension, spoofaxInstance: SpoofaxInstance) {
    // Check if languageUnderTest property was set
    extension.languageUnderTest.finalizeValue()
    if(!extension.languageUnderTest.isPresent) {
      throw GradleException("spoofaxTest.languageUnderTest property was not set on $project")
    }

    // Override the metaborgVersion and language identifier in the configuration, with values from the extension.
    extension.spoofax2Version.finalizeValue()
    extension.overrideMetaborgVersion(extension.spoofax2Version.get())
    extension.overrideIdentifiers()

    // Add dependencies from metaborg.yaml to the corresponding Gradle configurations.
    val spoofaxProject = spoofaxInstance.spoofax.getProject(project)
    extension.addDependenciesToProject(spoofaxProject.config())
  }

  private fun configureTestTask(
    project: Project,
    extension: SpoofaxTestExtension,
    spoofaxInstance: SpoofaxInstance
  ) {
    val spoofaxProject = spoofaxInstance.spoofax.getProject(project)
    val languageFiles = project.languageFiles
    val task = project.tasks.registerSpoofaxTestTask(spoofaxInstance.spoofax, spoofaxInstance.sptInjector, { spoofaxInstance.spoofax.getProject(project) })
    task.configure {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.

      // Test the specified language under test.
      languageUnderTest.set(extension.languageUnderTest)

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofaxInstance.spoofax)
        lazyLoadDialects(spoofaxProject.location(), project, spoofaxInstance.spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }
}
