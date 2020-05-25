package mb.spoofax.gradle.plugin

import com.google.inject.Injector
import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.SpoofaxInstanceCache
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideDependenciesInConfig
import mb.spoofax.gradle.util.overrideConfig
import mb.spoofax.gradle.util.recreateProject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxMeta

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

    val instance = SpoofaxInstanceCache[project]
    instance.reset()
    instance.spoofax.recreateProject(project)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension, instance)
    }

    project.gradle.buildFinished {
      instance.reset()
    }
  }

  private fun configureAfterEvaluate(project: Project, extension: SpoofaxTestExtension, spoofaxInstance: SpoofaxInstance) {
    spoofaxInstance.run {
      configureProjectAfterEvaluate(project, extension, spoofax, spoofaxMeta)
      configureTestTask(project, extension, spoofax, spoofaxMeta, sptInjector)
    }
  }

  private fun configureProjectAfterEvaluate(project: Project, extension: SpoofaxTestExtension, spoofax: Spoofax, spoofaxMeta: SpoofaxMeta) {
    // Check if languageUnderTest property was set
    extension.languageUnderTest.finalizeValue()
    if(!extension.languageUnderTest.isPresent) {
      throw GradleException("spoofaxTest.languageUnderTest property was not set on $project")
    }
    // Override the language identifier and metaborgVersion in the configuration with values from the Gradle project.
    project.overrideConfig(extension, spoofaxMeta.injector, false)

    // Add dependencies from metaborg.yaml to the corresponding Gradle configurations.
    val spoofaxProject = spoofax.getProject(project)
    extension.addDependenciesToProject(spoofaxProject.config())
  }

  private fun configureTestTask(
    project: Project,
    extension: SpoofaxTestExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta,
    sptInjector: Injector
  ) {
    val spoofaxProject = spoofax.getProject(project)
    val languageFiles = project.languageFiles
    val task = project.tasks.registerSpoofaxTestTask(spoofax, sptInjector, { spoofax.getProject(project) })
    task.configure {
      // Test the specified language under test.
      languageUnderTest.set(extension.languageUnderTest)

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofaxMeta.injector)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofaxProject.location(), project, spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }
}