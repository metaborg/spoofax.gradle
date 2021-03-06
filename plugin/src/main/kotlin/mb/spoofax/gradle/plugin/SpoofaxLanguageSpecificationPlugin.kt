package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.SpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.SpoofaxInstanceCache
import mb.spoofax.gradle.util.configureSafely
import mb.spoofax.gradle.util.getLanguageSpecification
import mb.spoofax.gradle.util.getProject
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadCompiledLanguage
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideDependenciesInConfig
import mb.spoofax.gradle.util.overrideIdentifiers
import mb.spoofax.gradle.util.overrideMetaborgVersion
import mb.spoofax.gradle.util.overrideStrategoFormat
import mb.spoofax.gradle.util.recreateProject
import mb.spoofax.gradle.util.registerSafely
import org.apache.commons.vfs2.FileObject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.build.SpoofaxLangSpecCommonPaths
import org.metaborg.spoofax.meta.core.config.StrategoFormat
import org.metaborg.spoofax.meta.core.pluto.SpoofaxContext
import java.io.File
import java.io.IOException

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
    val sharedInputExcludes = setOf("/target", "/build") + sharedExcludes
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

    val instance = SpoofaxInstanceCache[project]
    instance.reset()
    instance.spoofax.recreateProject(project)

    val extension = SpoofaxLangSpecExtension(project)
    project.extensions.add("spoofaxLanguageSpecification", extension)

    configureJava(project)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension, instance)
    }

    project.gradle.buildFinished {
      instance.reset()
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
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance
  ) {
    configureProjectAfterEvaluate(project, extension, spoofaxInstance)

    val generateSourcesTask = configureGenerateSourcesTask(project, extension, spoofaxInstance)
    val buildTask = configureBuildTask(project, extension, spoofaxInstance, generateSourcesTask)
    val compileTask = configureCompileTask(project, extension, spoofaxInstance, buildTask, generateSourcesTask)
    val packageTask = configurePackageTask(project, extension, spoofaxInstance, compileTask)

    val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
    val languageSpecificationPaths = SpoofaxLangSpecCommonPaths(languageSpecification.location())
    val archiveLocation = languageSpecificationPaths.spxArchiveFile(languageSpecification.config().identifier().toFileString())
    val archiveTask = configureArchiveTask(project, extension, spoofaxInstance, buildTask, generateSourcesTask, compileTask, packageTask, archiveLocation)

    configureCleanTask(project, extension, spoofaxInstance)
    configureBuildExamplesTask(project, extension, spoofaxInstance, archiveTask, archiveLocation)
    configureTestTask(project, extension, spoofaxInstance, archiveTask, archiveLocation)
  }

  private fun configureProjectAfterEvaluate(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance
  ) {
    // Override the metaborgVersion, language identifier, and the Stratego format in the configuration, with values from the extension.
    extension.spoofax2Version.finalizeValue()
    extension.overrideMetaborgVersion(extension.spoofax2Version.get())
    extension.overrideIdentifiers()
    extension.strategoFormat.finalizeValue()
    if(extension.strategoFormat.isPresent) {
      extension.overrideStrategoFormat(extension.strategoFormat.get())
    }

    // Add dependencies to corresponding configurations.
    extension.addDependenciesToProject(spoofaxInstance.spoofaxMeta.getLanguageSpecification(project).config())

    // Add a dependency to Spoofax core.
    extension.addSpoofaxCoreDependency()

    // Add the Spoofax repository.
    extension.addSpoofaxRepository()

    // Create publication from our component.
    extension.createPublication.finalizeValue()
    if(extension.createPublication.get()) {
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("SpoofaxLanguage") {
            from(project.components.getByName(SpoofaxBasePlugin.spoofaxLanguageComponent))
          }
        }
      }
    }
  }

  private fun configureGenerateSourcesTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val metaborgComponentYaml = srcGenDir.resolve("metaborg.component.yaml")
    val languageFiles = project.languageFiles
    val compileClasspath = project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
    return project.tasks.registerSafely("spoofaxGenerateSources") {
      // Task dependencies:
      // 1. Language files, which in turn influences the src-gen/metaborg.component.yaml file.
      dependsOn(languageFiles)
      // 2. Java compile classpath, which in turn influences the src-gen/metaborg.component.yaml file.
      dependsOn(compileClasspath)
      // 3. Extension properties
      inputs.property("otherApproximateDependencies", extension.otherApproximateDependencies)
      // 4. Gradle group/name/version influences the `metaborg.component.yaml` fike.
      inputs.property("group", project.group.toString())
      inputs.property("name", project.name)
      inputs.property("version", project.version.toString())
      // General inputs:
      if(extension.otherApproximateDependencies.get()) {
        // Approximate inputs/outputs:
        // * `metaborg.yaml` config file
        inputs.file(projectDir.resolve("metaborg.yaml"))
        // Outputs:
        // * generated `metaborg.component.yaml` file
        outputs.file(metaborgComponentYaml)
        // * generated completion file
        outputs.file(srcGenDir.resolve("completion/completion.str"))
        // * generated permissive normalized grammar
        outputs.file(srcGenDir.resolve("syntax/normalized/permissive-norm.aterm"))
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

      doFirst {
        // Requires configuration override.
        lazyOverrideDependenciesInConfig(extension)
      }

      doLast {
        val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
        val metaBuilderInput = LanguageSpecBuildInput(languageSpecification)
        spoofaxInstance.spoofaxMeta.metaBuilder.initialize(metaBuilderInput)
        spoofaxInstance.spoofaxMeta.metaBuilder.generateSources(metaBuilderInput, null)
      }
    }
  }

  private fun configureBuildTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance,
    generateSourcesTask: TaskProvider<*>
  ): TaskProvider<SpoofaxBuildTask> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageFiles = project.languageFiles
    val task = project.tasks.registerSpoofaxBuildTask(spoofaxInstance.spoofax, { spoofaxInstance.spoofaxMeta.getLanguageSpecification(project) })
    task.configureSafely {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // 2. Extension properties
      inputs.property("strategoFormat", extension.strategoFormat).optional(true)
      inputs.property("approximateSpoofaxBuildDependencies", extension.spoofaxBuildApproximateDependencies)
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // 3. Must run after generateSources task, because it may generate meta-language files.
      mustRunAfter(generateSourcesTask)
      // General inputs:
      if(extension.spoofaxBuildApproximateDependencies.get()) {
        // Approximate inputs:
        // * `metaborg.yaml` config file
        inputs.file(projectDir.resolve("metaborg.yaml"))
        // * meta-language files
        inputs.files(project.fileTree(".") {
          include("**/*.esv", "**/*.sdf", "**/*.def", "**/*.sdf3", "**/*.nabl", "**/*.ts", "**/*.nabl2", "**/*.stx", "**/*.ds")
          exclude("/src-gen")
          exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
        })
        // TODO: included files that are not in the project directory.
        // Approximate outputs:
        // * ESV
        outputs.file(targetMetaborgDir.resolve("editor.esv.af"))
        // * TODO: SDF2
        // * SDF3
        outputs.dir(srcGenDir.resolve("syntax"))
        outputs.dir(srcGenDir.resolve("pp"))
        outputs.dir(srcGenDir.resolve("signatures"))
        outputs.dir(srcGenDir.resolve("ds-signatures"))
        outputs.dir(srcGenDir.resolve("completion"))
        // * TODO: NaBL
        // * TODO: TS
        // * NaBL2
        outputs.dir(srcGenDir.resolve("nabl2"))
        // * Statix
        outputs.dir(srcGenDir.resolve("statix"))
        // * TODO: dynsem
      } else {
        // Conservative inputs: any file in the project directory, with matching include and exclude patterns.
        inputs.files(project.fileTree(".") {
          include(*extension.spoofaxBuildConservativeInputIncludePatterns.get().toTypedArray())
          exclude(*extension.spoofaxBuildConservativeInputExcludePatterns.get().toTypedArray())
        })
        // Conservative outputs: any file in the project directory, with matching include and exclude patterns.
        outputs.files(project.fileTree(".") {
          include(*extension.spoofaxBuildConservativeOutputIncludePatterns.get().toTypedArray())
          exclude(*extension.spoofaxBuildConservativeOutputExcludePatterns.get().toTypedArray())
        })
      }

      val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
      languageSpecification.config().pardonedLanguages().forEach {
        addPardonedLanguage(it)
      }

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofaxInstance.spoofax)
        lazyLoadDialects(spoofaxInstance.spoofax.getProjectLocation(project), project, spoofaxInstance.spoofax)
      }

      doLast {
        // Override Stratego provider in packed ESV file.
        overrideStrategoProvider(project, extension)
      }
    }
    return task
  }

  private fun configureCompileTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance,
    buildTask: TaskProvider<*>,
    generateSourcesTask: TaskProvider<*>
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageFiles = project.languageFiles
    val task = project.tasks.registerSafely("spoofaxCompile") {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // 2. Spoofax build task, which provides sources which are compiled by this task.
      dependsOn(buildTask)
      // 3. Generate sources task, which provides sources which are compiled by this task.
      dependsOn(generateSourcesTask)
      // 4. Extension properties
      inputs.property("strategoFormat", extension.strategoFormat).optional(true)
      inputs.property("otherApproximateDependencies", extension.otherApproximateDependencies)
      // General inputs:
      if(extension.otherApproximateDependencies.get()) {
        // Approximate inputs/outputs:
        // * SDF
        // - old build and Stratego concrete syntax extensions
        inputs.files(project.fileTree(".") {
          include("**/*.sdf", "**/*.def")
          exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
        })
        // - new build
        inputs.files(project.fileTree(".") {
          include("**/*-norm.aterm")
          exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
        })
        outputs.file(targetMetaborgDir.resolve("table.bin"))
        outputs.file(targetMetaborgDir.resolve("table-completions.bin"))
        // - both old and new build
        outputs.file(targetMetaborgDir.resolve("sdf.tbl"))
        outputs.file(targetMetaborgDir.resolve("sdf-completions.tbl"))
        // TODO: SDF include files and paths that are not in the project directory.

        // * Stratego
        inputs.files(project.fileTree(".") {
          include("**/*.str", "**/*.tbl", "**/*.pp.af")
          exclude("/src-gen/pp/*-parenthesize.str") // Ignore parenthesizer, as this task generates it.
          exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
        })
        val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
        when(languageSpecification.config().strFormat()!!) {
          StrategoFormat.jar -> outputs.dir(srcGenDir.resolve("java"))
          StrategoFormat.ctree -> outputs.file(targetMetaborgDir.resolve("stratego.ctree"))
        }
        // TODO: Stratego include files and paths that are not in the project directory.
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

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofaxInstance.spoofax)
        lazyLoadDialects(spoofaxInstance.spoofax.getProjectLocation(project), project, spoofaxInstance.spoofax)
      }

      doLast {
        val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
        try {
          spoofaxInstance.spoofaxMeta.metaBuilder.compile(LanguageSpecBuildInput(languageSpecification))
        } finally {
          SpoofaxContext.deinit() // Deinit Spoofax-Pluto context so it can be reused by other builds on the same thread.
        }

        // Override Stratego provider again, as compile runs the ESV compiler again.
        overrideStrategoProvider(project, extension)
      }
    }
    project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME).dependsOn(task)
    return task
  }

  private fun configurePackageTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance,
    compileTask: TaskProvider<*>
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageFiles = project.languageFiles
    return project.tasks.registerSafely("spoofaxLangSpecPackage") {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // 2. Spoofax compile task, which provides files that are packaged with this task.
      dependsOn(compileTask)
      // 3. Java compile task, which provides class files that are packaged with this task.
      dependsOn(project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME))
      // 4. Extension properties
      inputs.property("otherApproximateDependencies", extension.otherApproximateDependencies)
      // General inputs:
      if(extension.otherApproximateDependencies.get()) {
        // Approximate inputs/outputs:
        // * Stratego and Stratego Java strategies compiled class files.
        inputs.files(targetDir.resolve("classes"))
        // * Stratego JAR
        val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
        if(languageSpecification.config().strFormat() == StrategoFormat.jar) {
          // - pp.af and .tbl files are included into the JAR file
          inputs.files(project.fileTree(".") {
            include("**/*.pp.af", "**/*.tbl")
            exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
          })
        }
        outputs.file(targetMetaborgDir.resolve("stratego.jar"))
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

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofaxInstance.spoofax)
        lazyLoadDialects(spoofaxInstance.spoofax.getProjectLocation(project), project, spoofaxInstance.spoofax)
      }

      doLast {
        val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
        try {
          spoofaxInstance.spoofaxMeta.metaBuilder.pkg(LanguageSpecBuildInput(languageSpecification))
        } finally {
          SpoofaxContext.deinit() // Deinit Spoofax-Pluto context so it can be reused by other builds on the same thread.
        }
      }
    }
  }

  private fun configureArchiveTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance,
    buildTask: TaskProvider<*>,
    generateSourcesTask: TaskProvider<*>,
    compileTask: TaskProvider<*>,
    packageTask: TaskProvider<*>,
    archiveLocation: FileObject
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageFiles = project.languageFiles
    val archiveFile = spoofaxInstance.spoofax.resourceService.localPath(archiveLocation)!!
    val task = project.tasks.registerSafely("spoofaxArchive") {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // 2. Spoofax build/generate sources/compile/package tasks, which provide files that are archived with this task.
      dependsOn(buildTask, generateSourcesTask, compileTask, packageTask)
      // 3. Extension properties
      inputs.property("approximateDependencies", extension.otherApproximateDependencies)
      // General inputs:
      if(extension.otherApproximateDependencies.get()) {
        // Approximate inputs:
        // * icons
        val iconsDir = projectDir.resolve("icons")
        if(iconsDir.exists()) {
          inputs.dir(iconsDir)
        }
        // * src-gen directory
        inputs.dir(srcGenDir)
        // * target/metaborg directory
        inputs.dir(targetMetaborgDir)
        // TODO: exported files.
      } else {
        // Conservative inputs: any file in the project directory (not matching include exclude patterns).
        inputs.files(project.fileTree(".") {
          exclude(*extension.defaultInputExcludePatterns.get().toTypedArray())
        })
      }
      // General outputs: the spoofax-language archive file.
      outputs.file(archiveFile)

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofaxInstance.spoofax)
        lazyLoadDialects(spoofaxInstance.spoofax.getProjectLocation(project), project, spoofaxInstance.spoofax)
      }

      doLast {
        val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
        try {
          spoofaxInstance.spoofaxMeta.metaBuilder.archive(LanguageSpecBuildInput(languageSpecification))
        } finally {
          SpoofaxContext.deinit() // Deinit Spoofax-Pluto context so it can be reused by other builds on the same thread.
        }
        lazyLoadCompiledLanguage(archiveLocation, project, spoofaxInstance.spoofax) // Test language archive by loading it.
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(task)

    // Add the archive file as an artifact, built by the archive task.
    project.languageArchive.outgoing.artifact(archiveFile) {
      this.name = project.name
      this.extension = SpoofaxBasePlugin.spoofaxLanguageExtension
      this.type = SpoofaxBasePlugin.spoofaxLanguageType
      builtBy(task)
    }

    return task
  }

  private fun overrideStrategoProvider(project: Project, extension: SpoofaxLangSpecExtension) {
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


  private fun configureCleanTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance
  ) {
    val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
    val languageFiles = project.languageFiles
    val spoofaxCleanTask = project.tasks.registerSpoofaxCleanTask(spoofaxInstance.spoofax, languageSpecification)
    spoofaxCleanTask.configureSafely {
      // No other inputs/outputs known: always execute.

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofaxInstance.spoofax)
        lazyLoadDialects(spoofaxInstance.spoofax.getProjectLocation(project), project, spoofaxInstance.spoofax)
      }
    }
    val task = project.tasks.register("spoofaxLangSpecClean") {
      // Task dependencies: should run after regular clean.
      shouldRunAfter(spoofaxCleanTask)
      // Inputs/outputs: none, always execute.

      doLast {
        spoofaxInstance.spoofaxMeta.metaBuilder.clean(LanguageSpecBuildInput(languageSpecification))
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME).dependsOn(task)
  }


  private fun configureBuildExamplesTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance,
    archiveTask: TaskProvider<*>,
    archiveLocation: FileObject
  ) {
    val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
    val task = project.tasks.registerSpoofaxBuildTask(spoofaxInstance.spoofax, { languageSpecification }, "spoofaxBuildExamples")
    task.configureSafely {
      // Task dependencies:
      // 1. Archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(archiveTask)
      // Inputs: any file in the project directory.
      inputs.dir(extension.examplesDir.map { project.projectDir.resolve(it) })
      // Outputs: up to date when creating the archive was up to date (skipped).
      outputs.upToDateWhen {
        archiveTask.get().state.upToDate
      }

      // Only build files of compiled language.
      addLanguage(languageSpecification.config().identifier())
      // Only execute task if buildExamples is set to true.
      onlyIf { extension.buildExamples.finalizeValue(); extension.buildExamples.get() }

      doFirst {
        // Requires compiled language to be loaded.
        lazyLoadCompiledLanguage(archiveLocation, project, spoofaxInstance.spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }


  private fun configureTestTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance,
    archiveTask: TaskProvider<*>,
    archiveLocation: FileObject
  ) {
    val languageSpecification = spoofaxInstance.spoofaxMeta.getLanguageSpecification(project)
    val task = project.tasks.registerSpoofaxTestTask(spoofaxInstance.spoofax, spoofaxInstance.sptInjector, { spoofaxInstance.spoofax.getProject(project) })
    task.configureSafely {
      // Only execute task if runTests is set to true.
      onlyIf { extension.runTests.finalizeValue(); extension.runTests.get() }

      // Task dependencies
      /// * Archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(archiveTask)

      // Test the compiled language
      languageUnderTest.set(languageSpecification.config().identifier())

      doFirst {
        // Requires compiled language and SPT language to be loaded.
        lazyLoadCompiledLanguage(archiveLocation, project, spoofaxInstance.spoofax)
        lazyLoadLanguages(project.sptLanguageFiles, project, spoofaxInstance.spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }
}
