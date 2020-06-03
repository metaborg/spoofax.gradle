package mb.spoofax.gradle.plugin

import com.google.inject.Injector
import mb.spoofax.gradle.task.SpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.task.registerSpoofaxTestTask
import mb.spoofax.gradle.util.SpoofaxInstance
import mb.spoofax.gradle.util.SpoofaxInstanceCache
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
import org.apache.commons.vfs2.FileObject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.build.SpoofaxLangSpecCommonPaths
import org.metaborg.spoofax.meta.core.config.StrategoFormat
import java.io.File
import java.io.IOException


open class SpoofaxLangSpecExtension(project: Project) : SpoofaxExtensionBase(project) {
  val strategoFormat: Property<StrategoFormat> = project.objects.property()
  val createPublication: Property<Boolean> = project.objects.property()
  val buildExamples: Property<Boolean> = project.objects.property()
  val examplesDir: Property<String> = project.objects.property()
  val runTests: Property<Boolean> = project.objects.property()
  val approximateDependencies: Property<Boolean> = project.objects.property()

  init {
    createPublication.convention(true)
    buildExamples.convention(false)
    examplesDir.convention("example")
    runTests.convention(true)
    approximateDependencies.convention(true)
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

    configureProject(project)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension, instance)
    }

    project.gradle.buildFinished {
      instance.reset()
    }
  }

  private fun configureProject(project: Project) {
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

    // HACK: disable several parts of the Java plugin that are not needed.
    project.tasks.getByName(JavaPlugin.JAR_TASK_NAME).enabled = false
    project.components.remove(project.components.getByName("java"))
    project.configurations.getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME).outgoing.artifacts.clear()
    @Suppress("DEPRECATION")
    project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).outgoing.artifacts.clear()
    project.configurations.getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME).outgoing.artifacts.clear()
  }

  private fun configureAfterEvaluate(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxInstance: SpoofaxInstance
  ) {
    spoofaxInstance.run {
      configureProjectAfterEvaluate(project, extension, spoofaxMeta)

      val buildTask = configureBuildTask(project, extension, spoofax, spoofaxMeta)
      val generateSourcesTask = configureGenerateSourcesTask(project, extension, spoofaxMeta, buildTask)
      val compileTask = configureCompileTask(project, extension, spoofax, spoofaxMeta, buildTask, generateSourcesTask)
      val packageTask = configurePackageTask(project, extension, spoofax, spoofaxMeta, compileTask)

      val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
      val languageSpecificationPaths = SpoofaxLangSpecCommonPaths(languageSpecification.location())
      val archiveLocation = languageSpecificationPaths.spxArchiveFile(languageSpecification.config().identifier().toFileString())
      val archiveTask = configureArchiveTask(project, extension, spoofax, spoofaxMeta, packageTask, archiveLocation)

      configureCleanTask(project, extension, spoofax, spoofaxMeta)
      configureBuildExamplesTask(project, extension, spoofax, spoofaxMeta, archiveTask, archiveLocation)
      configureTestTask(project, extension, spoofax, spoofaxMeta, sptInjector, archiveTask, archiveLocation)
    }
  }

  private fun configureProjectAfterEvaluate(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxMeta: SpoofaxMeta
  ) {
    // Override the metaborgVersion, language identifier, and the Stratego format in the configuration, with values from the extension.
    extension.overrideMetaborgVersion()
    extension.overrideIdentifiers()
    extension.strategoFormat.finalizeValue()
    if(extension.strategoFormat.isPresent) {
      extension.overrideStrategoFormat(extension.strategoFormat.get())
    }

    // Add dependencies to corresponding configurations.
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    extension.addDependenciesToProject(languageSpecification.config())

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

    // Finalize common properties
    extension.approximateDependencies.finalizeValue()
  }

  private fun configureBuildTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta
  ): TaskProvider<SpoofaxBuildTask> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageFiles = project.languageFiles
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    val task = project.tasks.registerSpoofaxBuildTask(spoofax, { spoofaxMeta.getLanguageSpecification(project) })
    task.configure {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // 2. Extension properties
      inputs.property("strategoFormat", extension.strategoFormat).optional(true)
      inputs.property("approximateDependencies", extension.approximateDependencies)
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // General inputs:
      if(extension.approximateDependencies.get()) {
        // Approximate inputs:
        // * `metaborg.yaml` config file
        inputs.file(projectDir.resolve("metaborg.yaml"))
        // * meta-language files
        inputs.files(project.fileTree(".") {
          include("**/*.esv", "**/*.sdf", "**/*.def", "**/*.sdf3", "**/*.nabl", "**/*.ts", "**/*.nabl2", "**/*.stx", "**/*.ds")
          exclude("/src-gen", "/target", "/build", "/.gradle", "/.git")
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
        // Conservative inputs: any file in the project directory.
        inputs.dir(projectDir)
        // Conservative outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      languageSpecification.config().pardonedLanguages().forEach {
        addPardonedLanguage(it)
      }

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }

      doLast {
        // Override Stratego provider in packed ESV file.
        overrideStrategoProvider(project, extension)
      }
    }
    return task
  }

  private fun configureGenerateSourcesTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxMeta: SpoofaxMeta,
    buildTask: TaskProvider<*>
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val metaborgComponentYaml = srcGenDir.resolve("metaborg.component.yaml")
    val languageFiles = project.languageFiles
    val compileClasspath = project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME)
    return project.tasks.register("spoofaxGenerateSources") {
      // Task dependencies:
      // 1. Language files, which in turn influences the src-gen/metaborg.component.yaml file.
      dependsOn(languageFiles)
      // 2. Java compile classpath, which in turn influences the src-gen/metaborg.component.yaml file.
      dependsOn(compileClasspath)
      // 3. Must run after build task, because there may be custom generateSources build steps which require files to be built.
      mustRunAfter(buildTask)
      // 4. Extension properties
      inputs.property("approximateDependencies", extension.approximateDependencies)
      // General inputs:
      if(extension.approximateDependencies.get()) {
        // Approximate inputs/outputs:
        // * `metaborg.yaml` config file
        inputs.file(projectDir.resolve("metaborg.yaml"))
        // Outputs:
        // * generated `metaborg.component.yaml file
        outputs.file(metaborgComponentYaml)
        // * generated completion file
        outputs.file(srcGenDir.resolve("completion/completion.str"))
        // * generated permissive normalized grammar
        outputs.file(srcGenDir.resolve("syntax/normalized/permissive-norm.aterm"))
      } else {
        // Conservative inputs: Any file in the project directory.
        inputs.dir(projectDir)
        // Conservative outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      doFirst {
        // Requires configuration override.
        lazyOverrideDependenciesInConfig(extension)
      }

      doLast {
        val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
        val metaBuilderInput = LanguageSpecBuildInput(languageSpecification)
        spoofaxMeta.metaBuilder.initialize(metaBuilderInput)
        spoofaxMeta.metaBuilder.generateSources(metaBuilderInput, null)
      }
    }
  }

  private fun configureCompileTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta,
    buildTask: TaskProvider<*>,
    generateSourcesTask: TaskProvider<*>
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    val languageFiles = project.languageFiles
    val task = project.tasks.register("spoofaxCompile") {
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
      inputs.property("approximateDependencies", extension.approximateDependencies)
      // General inputs:
      if(extension.approximateDependencies.get()) {
        // Approximate inputs/outputs:
        // * SDF
        // - old build and Stratego concrete syntax extensions
        inputs.files(project.fileTree(".") {
          include("**/*.sdf", "**/*.def")
          exclude("/target", "/build", "/.gradle", "/.git")
        })
        // - new build
        inputs.files(project.fileTree(".") {
          include("**/*-norm.aterm")
          exclude("/target", "/build", "/.gradle", "/.git")
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
          exclude("/target", "/build", "/.gradle", "/.git")
        })
        when(languageSpecification.config().strFormat()!!) {
          StrategoFormat.jar -> outputs.dir(srcGenDir.resolve("stratego-java"))
          StrategoFormat.ctree -> outputs.file(targetMetaborgDir.resolve("stratego.ctree"))
        }
        outputs.file(targetMetaborgDir.resolve("typesmart.context"))
        // TODO: Stratego include files and paths that are not in the project directory.
      } else {
        // Conservative inputs: any file in the project directory.
        inputs.dir(projectDir)
        // Conservative outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }

      doLast {
        spoofaxMeta.metaBuilder.compile(LanguageSpecBuildInput(languageSpecification))

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
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta,
    compileTask: TaskProvider<*>
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val languageFiles = project.languageFiles
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    return project.tasks.register("spoofaxLangSpecPackage") {
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
      inputs.property("approximateDependencies", extension.approximateDependencies)
      // General inputs:
      if(extension.approximateDependencies.get()) {
        // Approximate inputs/outputs:
        // * Stratego and Stratego Java strategies compiled class files.
        inputs.files(targetDir.resolve("classes"))
        // * Stratego JAR
        if(languageSpecification.config().strFormat() == StrategoFormat.jar) {
          // - pp.af and .tbl files are included into the JAR file
          inputs.files(project.fileTree(".") {
            include("**/*.pp.af", "**/*.tbl")
            exclude("/target", "/build", "/.gradle", "/.git")
          })
          // - Stratego JAR file.
          outputs.file(targetMetaborgDir.resolve("stratego.jar"))
        }
        // * Stratego Java strategies JAR file. Optional because it may not exist if there are no Java strategies.
        outputs.file(targetMetaborgDir.resolve("stratego-javastrat.jar")).optional()
      } else {
        // Conservative inputs: any file in the project directory.
        inputs.dir(projectDir)
        // Conservative outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }

      doLast {
        spoofaxMeta.metaBuilder.pkg(LanguageSpecBuildInput(languageSpecification))
      }
    }
  }

  private fun configureArchiveTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta,
    packageTask: TaskProvider<*>,
    archiveLocation: FileObject
  ): TaskProvider<*> {
    val projectDir = project.projectDir
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val metaborgComponentYaml = srcGenDir.resolve("metaborg.component.yaml")
    val languageFiles = project.languageFiles
    val archiveFile = spoofax.resourceService.localPath(archiveLocation)!!
    val task = project.tasks.register("spoofaxArchive") {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // 2. Package task, which provides files that are archived with this task.
      dependsOn(packageTask)
      // 3. Extension properties
      inputs.property("approximateDependencies", extension.approximateDependencies)
      // General inputs:
      if(extension.approximateDependencies.get()) {
        // Approximate inputs:
        // * icons
        val iconsDir = projectDir.resolve("icons")
        if(iconsDir.exists()) {
          inputs.dir(iconsDir)
        }
        // * target/metaborg directory
        inputs.dir(targetMetaborgDir)
        // * generated `metaborg.component.yaml file
        inputs.file(metaborgComponentYaml)
        // TODO: exported files.
      } else {
        // Conservative inputs: any file in the project directory.
        inputs.dir(projectDir)
      }
      // General outputs: the spoofax-language archive file.
      outputs.file(archiveFile)

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }

      doLast {
        val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
        spoofaxMeta.metaBuilder.archive(LanguageSpecBuildInput(languageSpecification))
        lazyLoadCompiledLanguage(archiveLocation, project, spoofax) // Test language archive by loading it.
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
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta
  ) {
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    val languageFiles = project.languageFiles
    val spoofaxCleanTask = project.tasks.registerSpoofaxCleanTask(spoofax, languageSpecification)
    spoofaxCleanTask.configure {
      // No other inputs/outputs known: always execute.

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(extension)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }
    }
    val task = project.tasks.register("spoofaxLangSpecClean") {
      // Task dependencies: should run after regular clean.
      shouldRunAfter(spoofaxCleanTask)
      // Inputs/outputs: none, always execute.

      doLast {
        spoofaxMeta.metaBuilder.clean(LanguageSpecBuildInput(languageSpecification))
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME).dependsOn(task)
  }


  private fun configureBuildExamplesTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta,
    archiveTask: TaskProvider<*>,
    archiveLocation: FileObject
  ) {
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    val task = project.tasks.registerSpoofaxBuildTask(spoofax, { languageSpecification }, "spoofaxBuildExamples")
    task.configure {
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
        lazyLoadCompiledLanguage(archiveLocation, project, spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }


  private fun configureTestTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta,
    sptInjector: Injector,
    archiveTask: TaskProvider<*>,
    archiveLocation: FileObject
  ) {
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    val task = project.tasks.registerSpoofaxTestTask(spoofax, sptInjector, { spoofax.getProject(project) })
    task.configure {
      // Only execute task if runTests is set to true.
      onlyIf { extension.runTests.finalizeValue(); extension.runTests.get() }

      // Task dependencies
      /// * Archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(archiveTask)

      // Test the compiled language
      languageUnderTest.set(languageSpecification.config().identifier())

      doFirst {
        // Requires compiled language and SPT language to be loaded.
        lazyLoadCompiledLanguage(archiveLocation, project, spoofax)
        lazyLoadLanguages(project.sptLanguageFiles, project, spoofax)
      }
    }
    project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)
  }
}
