package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.SpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.util.createSpoofax
import mb.spoofax.gradle.util.createSpoofaxMeta
import mb.spoofax.gradle.util.getLanguageSpecification
import mb.spoofax.gradle.util.getProjectLocation
import mb.spoofax.gradle.util.lazyLoadCompiledLanguage
import mb.spoofax.gradle.util.lazyLoadDialects
import mb.spoofax.gradle.util.lazyLoadLanguages
import mb.spoofax.gradle.util.lazyOverrideDependenciesInConfig
import mb.spoofax.gradle.util.overrideConfig
import mb.spoofax.gradle.util.recreateProject
import mb.spoofax.gradle.util.toGradleDependency
import org.apache.commons.vfs2.FileObject
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.SpoofaxConstants
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.build.SpoofaxLangSpecCommonPaths
import org.metaborg.spoofax.meta.core.config.StrategoFormat
import org.metaborg.spt.core.SPTModule
import org.metaborg.spt.core.SPTRunner
import java.io.File

open class SpoofaxLangSpecExtension(project: Project) : SpoofaxExtensionBase(project) {
  var addSpoofaxCoreDependency: Boolean = true
  var addSpoofaxRepository: Boolean = true

  var createPublication: Boolean = true

  var buildExamples: Boolean = false
  var examplesDir: String = "example"

  var runTests: Boolean = true

  var approximateDependencies: Boolean = true
}

@Suppress("unused", "UnstableApiUsage")
class SpoofaxLanguageSpecificationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(LifecycleBasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)
    project.pluginManager.apply(JavaPlugin::class)

    val extension = SpoofaxLangSpecExtension(project)
    project.extensions.add("spoofax", extension)

    val spoofax = createSpoofax(project.gradle)
    val spoofaxMeta = spoofax.createSpoofaxMeta(project.gradle)

    spoofax.recreateProject(project)

    project.afterEvaluate {
      configureAfterEvaluate(this, extension, spoofax, spoofaxMeta)
    }
  }

  private fun configureAfterEvaluate(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta
  ) {
    configureProject(project, extension, spoofaxMeta)

    val buildTask = configureBuildTask(project, extension, spoofax, spoofaxMeta)
    val generateSourcesTask = configureGenerateSourcesTask(project, extension, spoofax, spoofaxMeta, buildTask)
    val compileTask = configureCompileTask(project, extension, spoofax, spoofaxMeta, buildTask, generateSourcesTask)
    val packageTask = configurePackageTask(project, extension, spoofax, spoofaxMeta, compileTask)

    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    val languageSpecificationPaths = SpoofaxLangSpecCommonPaths(languageSpecification.location())
    val archiveLocation = languageSpecificationPaths.spxArchiveFile(languageSpecification.config().identifier().toFileString())
    val archiveTask = configureArchiveTask(project, extension, spoofax, spoofaxMeta, packageTask, archiveLocation)

    configureCleanTask(project, extension, spoofax, spoofaxMeta)
    configureBuildExamplesTask(project, extension, spoofax, spoofaxMeta, archiveTask, archiveLocation)
    configureTestTask(project, extension, spoofax, spoofaxMeta, archiveTask, archiveLocation)
  }

  private fun configureProject(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofaxMeta: SpoofaxMeta
  ) {
    // Override the language identifier and metaborgVersion in the configuration with values from the Gradle project.
    project.overrideConfig(extension, spoofaxMeta.injector, false)

    // Add dependencies to corresponding configurations.
    val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
    extension.addDependenciesToProject(languageSpecification.config())

    // Add a dependency to Spoofax core.
    if(extension.addSpoofaxCoreDependency) {
      project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).dependencies.add(project.dependencies.create("org.metaborg", "org.metaborg.spoofax.core", extension.metaborgVersion))
    }

    // Add the Spoofax repository.
    if(extension.addSpoofaxRepository) {
      extension.addSpoofaxRepo()
    }

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

    // Create publication from our component.
    if(extension.createPublication) {
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("SpoofaxLanguage") {
            from(project.components.getByName(SpoofaxBasePlugin.spoofaxLanguageComponent))
          }
        }
      }
    }
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
    val buildTask = project.tasks.registerSpoofaxBuildTask(spoofax, { spoofaxMeta.getLanguageSpecification(project) })
    buildTask.configure {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // General inputs:
      if(extension.approximateDependencies) {
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

      val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
      languageSpecification.config().pardonedLanguages().forEach {
        addPardonedLanguage(it)
      }

      doFirst {
        // Requires configuration override, languages, and dialects to be loaded.
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofaxMeta.injector)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }
    }
    return buildTask
  }

  private fun configureGenerateSourcesTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
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
      // General inputs:
      if(extension.approximateDependencies) {
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
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofaxMeta.injector)
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
    return project.tasks.register("spoofaxCompile") {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // 2. Spoofax build task, which provides sources which are compiled by this task.
      dependsOn(buildTask)
      // 3. Generate sources task, which provides sources which are compiled by this task.
      dependsOn(generateSourcesTask)
      // General inputs:
      if(extension.approximateDependencies) {
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
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofaxMeta.injector)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }

      doLast {
        spoofaxMeta.metaBuilder.compile(LanguageSpecBuildInput(languageSpecification))
      }

      project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME).dependsOn(this)
    }
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
      // 2. Compile task, which provides files that are packaged with this task.
      dependsOn(compileTask)
      // General inputs:
      if(extension.approximateDependencies) {
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
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofaxMeta.injector)
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
    val archiveTask = project.tasks.register("spoofaxArchive") {
      // Task dependencies:
      // 1. Language files, which influences which languages are loaded.
      dependsOn(languageFiles)
      inputs.files({ languageFiles }) // Closure to defer to task execution time.
      // TODO: Stratego dialects through *.tbl files in non-output directories
      // 2. Package task, which provides files that are archived with this task.
      dependsOn(packageTask)
      // General inputs:
      if(extension.approximateDependencies) {
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
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofaxMeta.injector)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }

      doLast {
        val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
        spoofaxMeta.metaBuilder.archive(LanguageSpecBuildInput(languageSpecification))
        lazyLoadCompiledLanguage(archiveLocation, project, spoofax) // Test language archive by loading it.
      }

      project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(this)
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
        lazyOverrideDependenciesInConfig(project, extension, spoofax, spoofaxMeta.injector)
        lazyLoadLanguages(languageFiles, project, spoofax)
        lazyLoadDialects(spoofax.getProjectLocation(project), project, spoofax)
      }
    }
    project.tasks.register("spoofaxLangSpecClean") {
      // Task dependencies: should run after regular clean.
      shouldRunAfter(spoofaxCleanTask)
      // Inputs/outputs: none, always execute.

      doLast {
        spoofaxMeta.metaBuilder.clean(LanguageSpecBuildInput(languageSpecification))
      }

      project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME).dependsOn(this)
    }
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
    val buildExamplesTask = project.tasks.registerSpoofaxBuildTask(spoofax, { languageSpecification }, "spoofaxBuildExamples")
    buildExamplesTask.configure {
      // Task dependencies:
      // 1. Archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(archiveTask)
      // Inputs: any file in the project directory.
      inputs.dir(project.projectDir.resolve(extension.examplesDir))
      // Outputs: up to date when creating the archive was up to date (skipped).
      outputs.upToDateWhen {
        archiveTask.get().state.upToDate
      }

      // Only build files of compiled language.
      addLanguage(languageSpecification.config().identifier())
      // Only execute task if buildExamples is set to true.
      onlyIf { extension.buildExamples }

      doFirst {
        // Requires compiled language to be loaded.
        lazyLoadCompiledLanguage(archiveLocation, project, spoofax)
      }

      project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(this)
    }
  }

  private fun configureTestTask(
    project: Project,
    extension: SpoofaxLangSpecExtension,
    spoofax: Spoofax,
    spoofaxMeta: SpoofaxMeta,
    archiveTask: TaskProvider<*>,
    archiveLocation: FileObject
  ) {
    // TODO: this needs to mimick the configurations from the base plugin to work.
    val sptLanguageConfig = project.configurations.create("sptLanguage") {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = true
    }
    val sptId = LanguageIdentifier(extension.metaborgGroup, SpoofaxConstants.LANG_SPT_ID, LanguageVersion.parse(extension.metaborgVersion))
    val sptDependency = sptId.toGradleDependency(project)
    project.dependencies.add(sptLanguageConfig.name, sptDependency)

    project.tasks.register("spoofaxTest") {
      // Only execute task if runTests is set to true.
      onlyIf { extension.runTests }

      // Task dependencies:
      // 1. Archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(archiveTask)
      // 2. SPT language dependency configuration, which influences which SPT language is loaded.
      dependsOn(sptLanguageConfig)
      // Inputs: SPT files.
      inputs.files(project.fileTree(".") {
        include("**/*.spt")
        exclude("/target", "/build", "/.gradle", "/.git")
      })
      // Outputs: SPT result file.
      val sptResultFile = project.buildDir.resolve("spt/result.txt")
      outputs.file(sptResultFile)

      doFirst {
        // Requires compiled language and SPT language to be loaded.
        lazyLoadCompiledLanguage(archiveLocation, project, spoofax)
        lazyLoadLanguages(sptLanguageConfig, project, spoofax)
      }

      doLast {
        val sptLangImpl = spoofax.languageService.getImpl(sptId)
          ?: throw GradleException("Failed to get SPT language implementation ($sptId)")
        val sptInjector = spoofaxMeta.injector.createChildInjector(SPTModule())
        val sptRunner = sptInjector.getInstance(SPTRunner::class.java)
        val languageSpecification = spoofaxMeta.getLanguageSpecification(project)
        val langUnderTest = spoofax.languageService.getImpl(languageSpecification.config().identifier())
        try {
          sptRunner.test(languageSpecification, sptLangImpl, langUnderTest)
          sptResultFile.writeText("success")
        } catch(e: MetaborgException) {
          sptResultFile.writeText("failed")
          throw GradleException("SPT tests failed", e)
        }
      }

      project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(this)
    }
  }
}