package mb.spoofax.gradle.task

import mb.spoofax.gradle.plugin.languageFiles
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getLanguageSpecification
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadCompiledLanguage
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideConfig
import org.gradle.api.GradleException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.pluto.SpoofaxContext
import java.io.File

fun TaskContainer.registerSpoofaxArchiveLanguageSpecTask(name: String = "spoofaxArchiveLanguageSpec"): TaskProvider<SpoofaxArchiveLanguageSpecTask> =
  register(name, SpoofaxArchiveLanguageSpecTask::class)

abstract class SpoofaxArchiveLanguageSpecTask : SpoofaxTask() {
  @get:Input
  abstract val languageIdentifier: Property<LanguageIdentifier>

  @get:OutputFile
  abstract val archiveFile: Property<File>

  init {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageFiles = project.languageFiles

    // Task dependencies:
    // - Language files, which in turn influences the src-gen/metaborg.component.yaml file and which languages are loaded.
    dependsOn(languageFiles.allDependencies)
    inputs.files({ languageFiles }) // Also depend on file contents. Closure to defer to task execution time.
    // - Spoofax build langauge spec task, which provides files that are packaged with this task.
    dependsOn("spoofaxBuildLanguageSpec")
    // - Java compile task, which provides class files that are packaged with this task.
    dependsOn(project.tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME))
    // - Extension properties
    inputs.property("approximateSpoofaxBuildDependencies", extension.spoofaxBuildApproximateDependencies)
    inputs.property("otherApproximateDependencies", extension.otherApproximateDependencies)
    inputs.property("strategoFormat", extension.strategoFormat).optional(true)
    // - Gradle group/name/version influences the `metaborg.component.yaml` file.
    inputs.property("group", project.group.toString())
    inputs.property("name", project.name)
    inputs.property("version", project.version.toString())

    // General inputs and outputs:
    if(extension.otherApproximateDependencies.get()) {
      // Approximate inputs/outputs:
      // Inputs:
      // - Stratego and Stratego Java strategies compiled class files.
      inputs.files(project.fileTree("target/classes") {
        exclude("**/*.pp.af", "**/*.tbl") // Exclude pp.af and .tbl files as inputs, because this task copies them here.
      }).optional() // Optional: not all language specs have a classes directory.
      // - pp.af and .tbl files (not in src-gen, build, or target) are included into the JAR file
      inputs.files(project.fileTree(".") {
        include("**/*.pp.af", "**/*.tbl")
        exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
      }).optional() // Optional: not all language specs have pp.af or .tbl files.
      // - icons
      val iconsDir = projectDir.resolve("icons")
      if(iconsDir.exists()) { // HACK: .optional() does not work if directory does not exist, do this instead.
        inputs.dir(iconsDir)
      }
      // - src-gen directory
      inputs.dir(srcGenDir)
      // - target/metaborg directory
      inputs.files(project.fileTree(targetMetaborgDir) {
        exclude("stratego.jar") // Exclude stratego.jar, as this task creates it.
      })
      // TODO: exported files.

      // Outputs:
      // - Stratego JAR
      outputs.file(targetMetaborgDir.resolve("stratego.jar")).optional() // Optional: not all language specs have a Stratego JAR.
    } else {
      // Conservative inputs: any file in the project directory (not matching include exclude patterns).
      inputs.files(project.fileTree(".") {
        exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
      })
      // Conservative outputs: any file in the project directory (not matching output exclude patterns).
      outputs.files(project.fileTree(".") {
        exclude(*extension.defaultOutputExcludePatterns.get().toTypedArray())
      })
    }

    // Task that assembles the project depends on this task, as this task generates artifacts to be assembled.
    project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(this)
  }

  @TaskAction
  private fun execute() {
    val spoofaxBuildService = spoofaxBuildService.finalizeAndGet()
    val archiveFile = archiveFile.finalizeAndGet()
    // Run package and archive part of the meta-build.
    spoofaxBuildService.run {
      // Fist override configuration, and load languages and dialects
      lazyOverrideConfig(extension, configOverrides, spoofax)
      lazyLoadLanguages(project.languageFiles, project, spoofax)
      lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)

      // Get language spec, with overridden configuration.
      val languageSpec = spoofaxMeta.getLanguageSpecification(project)
      val languageSpecBuildInput = LanguageSpecBuildInput(languageSpec)

      try {
        languageSpecBuilder.pkg(languageSpecBuildInput)
        languageSpecBuilder.archive(languageSpecBuildInput)
      } catch(e: MetaborgException) {
        throw GradleException("Packaging or archiving language specification failed", e)
      } finally {
        SpoofaxContext.deinit() // Deinit Spoofax-Pluto context so it can be reused by other builds on the same thread.
      }

      // Test language archive by loading it.
      lazyLoadCompiledLanguage(archiveFile, project, spoofax)
    }
  }
}
