package mb.spoofax.gradle.task

import mb.spoofax.gradle.plugin.SpoofaxLangSpecExtension
import mb.spoofax.gradle.plugin.languageFiles
import mb.spoofax.gradle.util.LoggingOutputStream
import mb.spoofax.gradle.util.finalizeAndGet
import mb.spoofax.gradle.util.getLanguageSpecification
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideConfig
import org.gradle.api.GradleException
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.MetaborgException
import org.metaborg.core.action.CompileGoal
import org.metaborg.core.build.BuildInputBuilder
import org.metaborg.core.messages.StreamMessagePrinter
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.config.StrategoFormat
import org.metaborg.spoofax.meta.core.pluto.SpoofaxContext
import java.io.IOException

fun TaskContainer.registerSpoofaxBuildLanguageSpecTask(name: String = "spoofaxBuildLanguageSpec"): TaskProvider<SpoofaxBuildLanguageSpecTask> =
  register(name, SpoofaxBuildLanguageSpecTask::class)

abstract class SpoofaxBuildLanguageSpecTask : SpoofaxTask() {
  @get:Internal
  protected val langSpecExtension
    get() = project.extensions.getByType<SpoofaxLangSpecExtension>()

  init {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetMetaborgDir = projectDir.resolve("target/metaborg")
    val languageFiles = project.languageFiles

    // Task dependencies:
    // 1. Language files, which in turn influences the src-gen/metaborg.component.yaml file and which languages are loaded.
    dependsOn(languageFiles.allDependencies)
    inputs.files({ languageFiles }) // Also depend on file contents. Closure to defer to task execution time.
    // 2. Java compile classpath, which in turn influences the src-gen/metaborg.component.yaml file.
    val compileClasspath = project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
    dependsOn(compileClasspath.allDependencies) // Do not depend on file contents, `compileJava` task depends on the contents already.
    // 3. Extension properties
    inputs.property("defaultInputExcludePatterns", langSpecExtension.defaultInputExcludePatterns)
    inputs.property("approximateSpoofaxBuildDependencies", langSpecExtension.spoofaxBuildApproximateDependencies)
    inputs.property("spoofaxBuildConservativeInputIncludePatterns", langSpecExtension.spoofaxBuildConservativeInputIncludePatterns)
    inputs.property("spoofaxBuildConservativeInputExcludePatterns", langSpecExtension.spoofaxBuildConservativeInputExcludePatterns)
    inputs.property("spoofaxBuildConservativeOutputIncludePatterns", langSpecExtension.spoofaxBuildConservativeOutputIncludePatterns)
    inputs.property("spoofaxBuildConservativeOutputExcludePatterns", langSpecExtension.spoofaxBuildConservativeOutputExcludePatterns)
    inputs.property("strategoFormat", langSpecExtension.strategoFormat).optional(true)
    inputs.property("otherApproximateDependencies", langSpecExtension.otherApproximateDependencies)
    inputs.property("addLanguageContributionsFromMetaborgYaml", langSpecExtension.addLanguageContributionsFromMetaborgYaml)
    inputs.property("languageContributions", langSpecExtension.languageContributions)
    // 4. Gradle group/name/version influences the `metaborg.component.yaml` file.
    inputs.property("group", project.group.toString())
    inputs.property("name", project.name)
    inputs.property("version", project.version.toString())

    // General inputs and outputs:
    if(langSpecExtension.otherApproximateDependencies.get()) {
      // Approximate inputs/outputs:
      // Inputs:
      // - `metaborg.yaml` config file
      inputs.file(projectDir.resolve("metaborg.yaml"))
      // - meta-language files
      inputs.files(project.fileTree(".") {
        include(
          "**/*.esv", "**/*.sdf", "**/*.def", "**/*.sdf3", "**/*.str", "**/*.str2", "**/*.nabl", "**/*.ts",
          "**/*.nabl2", "**/*.stx", "**/*.ds", "**/*.tbl", "**/*.pp.af"
        )
        exclude( // Ignore directories created by `languageSpecBuilder.initialize`.
          "src",
          "src/main",
          "src/main/ds",
          "src/main/strategies"
        )
        exclude(*langSpecExtension.defaultInputExcludePatterns.get().toTypedArray())
        exclude(*langSpecExtension.spoofaxBuildApproximateAdditionalInputExcludePatterns.get().toTypedArray())
      })
      // TODO: included files that are not in the project directory?

      // Outputs:
      // - src-gen
      outputs.dir(srcGenDir)
      // - target/metaborg
      outputs.files(project.fileTree(targetMetaborgDir) {
        exclude(*langSpecExtension.spoofaxBuildApproximateAdditionalOutputExcludePatterns.get().toTypedArray())
      })
    } else {
      // Conservative inputs: any file in the project directory (not matching include exclude patterns).
      inputs.files(project.fileTree(".") {
        include(*langSpecExtension.spoofaxBuildConservativeInputIncludePatterns.get().toTypedArray())
        exclude(*langSpecExtension.spoofaxBuildConservativeInputExcludePatterns.get().toTypedArray())
      })
      // Conservative outputs: any file in the project directory (not matching output exclude patterns).
      outputs.files(project.fileTree(".") {
        include(*langSpecExtension.spoofaxBuildConservativeOutputIncludePatterns.get().toTypedArray())
        exclude(*langSpecExtension.spoofaxBuildConservativeOutputExcludePatterns.get().toTypedArray())
      })
    }

    // Task that compiles Java sources depends on this task, as this task may generate Java source files.
    project.pluginManager.withPlugin("java") {
      this@SpoofaxBuildLanguageSpecTask.finalizedBy(project.tasks.withType(JavaCompile::class))
    }
  }

  @TaskAction
  private fun execute() {
    val spoofaxBuildService = spoofaxBuildService.finalizeAndGet()
    spoofaxBuildService.run {
      // Fist override configuration, and load languages and dialects
      lazyOverrideConfig(langSpecExtension, configOverrides, spoofax)
      lazyLoadLanguages(project.languageFiles, project, spoofax)
      lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)

      // Get language spec, with overridden configuration.
      val languageSpec = spoofaxMeta.getLanguageSpecification(project)
      val languageSpecBuildInput = LanguageSpecBuildInput(languageSpec)

      // Run first part of meta-build.
      try {
        languageSpecBuilder.initialize(languageSpecBuildInput)
        languageSpecBuilder.generateSources(languageSpecBuildInput, null)
      } catch(e: MetaborgException) {
        throw GradleException("Initializing or generating sources for language specification failed", e)
      }

      // Run regular build TODO: make incremental?
      val inputBuilder = BuildInputBuilder(languageSpec).run {
        withSourcesFromDefaultSourceLocations(true)
        withSelector(SpoofaxIgnoresSelector())
        val printExceptions = when(project.gradle.startParameter.showStacktrace) {
          ShowStacktrace.INTERNAL_EXCEPTIONS -> false
          ShowStacktrace.ALWAYS -> true
          ShowStacktrace.ALWAYS_FULL -> true
        }
        withMessagePrinter(StreamMessagePrinter(
          sourceTextService,
          true,
          printExceptions,
          LoggingOutputStream(project.logger, LogLevel.INFO),
          LoggingOutputStream(project.logger, LogLevel.WARN),
          LoggingOutputStream(project.logger, LogLevel.ERROR)
        ))
        withPardonedLanguageStrings(languageSpec.config().pardonedLanguages())
        addTransformGoal(CompileGoal())
      }
      val buildInput = inputBuilder.build(dependencyService, languagePathService)
      val output = builder.build(buildInput)
      if(!output.success()) {
        throw GradleException("Spoofax build failed; errors encountered")
      }

      // Override Stratego provider in packed ESV file.
      overrideStrategoProvider(langSpecExtension)
    }

    // Run compile part of the meta-build.
    spoofaxBuildService.run {
      // Get language spec again, with overridden stratego provided, and recreate input.
      val languageSpec = spoofaxMeta.getLanguageSpecification(project)
      val languageSpecBuildInput = LanguageSpecBuildInput(languageSpec)
      try {
        languageSpecBuilder.compile(languageSpecBuildInput)
      } catch(e: MetaborgException) {
        throw GradleException("Compiling language specification failed", e)
      } finally {
        SpoofaxContext.deinit() // Deinit Spoofax-Pluto context so it can be reused by other builds on the same thread.
      }

      // Override Stratego provider again, as compile runs the ESV compiler again.
      overrideStrategoProvider(langSpecExtension)
    }
  }

  private fun overrideStrategoProvider(extension: SpoofaxLangSpecExtension) {
    // Override Stratego provider with the correct one based on the overridden stratego format.
    if(extension.strategoFormat.isPresent) {
      val esvAfFile = project.projectDir.resolve("target/metaborg/editor.esv.af")
      if(esvAfFile.exists()) {
        try {
          val strCtree = "target/metaborg/stratego.ctree"
          val strJar = "target/metaborg/stratego.jar"
          var content = esvAfFile.readText()
          content = when(extension.strategoFormat.get()) {
            StrategoFormat.ctree -> content.replace(strJar, strCtree)
            StrategoFormat.jar -> content.replace(strCtree, strJar)
            else -> content
          }
          esvAfFile.writeText(content)
        } catch(e: IOException) {
          throw GradleException("Cannot override Stratego format; cannot read file '$esvAfFile'", e)
        }
      }
    }
  }
}
