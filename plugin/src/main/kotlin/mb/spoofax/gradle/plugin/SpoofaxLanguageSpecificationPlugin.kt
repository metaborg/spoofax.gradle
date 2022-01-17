package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.SpoofaxArchiveLanguageSpecTask
import mb.spoofax.gradle.task.registerSpoofaxArchiveLanguageSpecTask
import mb.spoofax.gradle.task.registerSpoofaxBuildLanguageSpecTask
import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanLanguageSpecTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.configureSafely
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getLanguageSpecification
import mb.spoofax.gradle.util.lazyLoadCompiledLanguage
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion
import org.metaborg.spoofax.meta.core.config.StrategoFormat
import java.io.File

@Suppress("UnstableApiUsage")
open class SpoofaxLangSpecExtension(project: Project) : SpoofaxExtensionBase(project) {
  val strategoFormat: Property<StrategoFormat> = project.objects.property()
  val createPublication: Property<Boolean> = project.objects.property()
  val buildExamples: Property<Boolean> = project.objects.property()
  val examplesDir: Property<String> = project.objects.property()
  val runTests: Property<Boolean> = project.objects.property()

  val defaultInputExcludePatterns: SetProperty<String> = project.objects.setProperty()
  val defaultOutputExcludePatterns: SetProperty<String> = project.objects.setProperty()

  val spoofaxBuildApproximateDependencies: Property<Boolean> = project.objects.property()
  val spoofaxBuildConservativeInputIncludePatterns: SetProperty<String> = project.objects.setProperty()
  val spoofaxBuildConservativeInputExcludePatterns: SetProperty<String> = project.objects.setProperty()
  val spoofaxBuildConservativeOutputIncludePatterns: SetProperty<String> = project.objects.setProperty()
  val spoofaxBuildConservativeOutputExcludePatterns: SetProperty<String> = project.objects.setProperty()

  val otherApproximateDependencies: Property<Boolean> = project.objects.property()

  init {
    createPublication.convention(true)
    buildExamples.convention(false)
    examplesDir.convention("example")
    runTests.convention(true)

    val sharedExcludes = setOf("/out", "/bin", "/.gradle", "/.git")
    val sharedInputExcludes = setOf("/src-gen", "/target", "/build") + sharedExcludes
    val sharedOutputExcludes = sharedExcludes

    defaultInputExcludePatterns.convention(sharedInputExcludes)
    defaultOutputExcludePatterns.convention(sharedOutputExcludes)

    spoofaxBuildApproximateDependencies.convention(true)
    spoofaxBuildConservativeInputIncludePatterns.convention(setOf())
    spoofaxBuildConservativeInputExcludePatterns.convention(sharedInputExcludes)
    spoofaxBuildConservativeOutputIncludePatterns.convention(setOf())
    spoofaxBuildConservativeOutputExcludePatterns.convention(sharedOutputExcludes)

    otherApproximateDependencies.convention(true)
  }
}

@Suppress("unused", "UnstableApiUsage")
class SpoofaxLanguageSpecificationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)
    project.pluginManager.apply(JavaLibraryPlugin::class)

    val extension = SpoofaxLangSpecExtension(project)
    project.extensions.add("spoofaxLanguageSpecification", extension)

    configureJava(project)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension)
    }
  }

  private fun configureJava(project: Project) {
    // Configure Java source and output directories.
    project.configure<SourceSetContainer> {
      val mainSourceSet = getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      mainSourceSet.java {
        // Spoofax build uses the following additional source directories.
        srcDir("src/main/strategies")
        srcDir("src/main/ds")
        srcDir("src-gen/java")
        srcDir("src-gen/ds-java")
        // Spoofax build expects compiled Java classes in (Maven-style) 'target/classes' directory.
        @Suppress("UnstableApiUsage")
        outputDir = File(project.projectDir, "target/classes")
      }
    }
  }

  private fun configureAfterEvaluate(
    project: Project,
    extension: SpoofaxLangSpecExtension
  ) {
    configureProjectAfterEvaluate(project, extension)

    // HACK: determine languageIdentifier and archiveFile ourselves, hopefully in sync with Spoofax 2.
    val languageIdentifier = LanguageIdentifier(project.group.toString(), project.name, LanguageVersion.parse(project.version.toString()))
    val archiveFile = project.projectDir.resolve("target/${languageIdentifier.toFileString()}.spoofax-language")

    project.tasks.registerSpoofaxBuildLanguageSpecTask()
    val archiveTask = configureArchiveTask(project, languageIdentifier, archiveFile)

    val cleanTask = project.tasks.registerSpoofaxCleanTask()
    project.tasks.registerSpoofaxCleanLanguageSpecTask().configureSafely {
      spoofaxCleanTask.set(cleanTask)
    }

    configureBuildExamplesTask(project, extension, archiveTask, languageIdentifier, archiveFile)
    configureTestTask(project, extension, archiveTask, languageIdentifier, archiveFile)
  }

  private fun configureProjectAfterEvaluate(
    project: Project,
    extension: SpoofaxLangSpecExtension
  ) {
    // Add dependencies to corresponding configurations. // HACK: use shared Spoofax instance.
    extension.addDependenciesToProject(SpoofaxInstance.getShared(project.gradle).spoofaxMeta.getLanguageSpecification(project).config())
    // Add a dependency to Spoofax core.
    extension.addSpoofaxCoreDependency()
    // Add the Spoofax repository.
    extension.addSpoofaxRepository()
    // Create publication from our component.
    if(extension.createPublication.finalizeAndGet()) {
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("SpoofaxLanguage") {
            from(project.components.getByName(SpoofaxBasePlugin.spoofaxLanguageComponent))
          }
        }
      }
    }
  }

  private fun configureArchiveTask(
    project: Project,
    languageIdentifier: LanguageIdentifier,
    archiveFile: File
  ): TaskProvider<SpoofaxArchiveLanguageSpecTask> {
    val archiveTask = project.tasks.registerSpoofaxArchiveLanguageSpecTask()
    archiveTask.configureSafely {
      this.languageIdentifier.set(languageIdentifier)
      this.archiveFile.set(archiveFile)
    }

    // Add the archive file as an artifact, built by the archive task.
    project.languageArchive.outgoing.artifact(archiveFile) {
      this.name = project.name
      this.extension = SpoofaxBasePlugin.spoofaxLanguageExtension
      this.type = SpoofaxBasePlugin.spoofaxLanguageType
      builtBy(archiveTask)
    }

    return archiveTask
  }

  private fun configureBuildExamplesTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    archiveTask: TaskProvider<SpoofaxArchiveLanguageSpecTask>,
    languageIdentifier: LanguageIdentifier,
    archiveFile: File
  ) {
    val task = project.tasks.registerSpoofaxBuildTask(name = "spoofaxBuildExamples")
    task.configureSafely {
      // Only execute task if buildExamples is set to true.
      onlyIf { extension.buildExamples.finalizeValue(); extension.buildExamples.get() }

      // Task dependency: archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(archiveTask)
      // Inputs: any file in the project directory.
      inputs.dir(extension.examplesDir.map { project.projectDir.resolve(it) })
      // Outputs: up to date when creating the archive task was up-to-date (skipped).
      outputs.upToDateWhen {
        archiveTask.get().state.upToDate
      }

      spoofaxProjectSupplier.set { spoofaxMeta.getLanguageSpecification(project) }
      // Only build files of archived language.
      languageIds.add(languageIdentifier)

      doFirst {
        // Requires archived language to be loaded.
        spoofaxBuildService.finalizeAndGet().run {
          lazyLoadCompiledLanguage(archiveFile, project, spoofax)
        }
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }


  private fun configureTestTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    archiveTask: TaskProvider<SpoofaxArchiveLanguageSpecTask>,
    languageIdentifier: LanguageIdentifier,
    archiveFile: File
  ) {
    project.tasks.registerSpoofaxTestTask().configureSafely {
      // Only execute task if runTests is set to true.
      onlyIf { extension.runTests.finalizeValue(); extension.runTests.get() }

      // Task dependency: archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(archiveTask)

      spoofaxProjectSupplier.set { spoofaxMeta.getLanguageSpecification(project) }
      // Test the archived language.
      languageUnderTest.set(languageIdentifier)

      doFirst {
        // Requires archived language to be loaded.
        spoofaxBuildService.finalizeAndGet().run {
          lazyLoadCompiledLanguage(archiveFile, project, spoofax)
        }
      }
    }
  }
}
