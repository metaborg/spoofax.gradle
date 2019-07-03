package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.registerSpoofaxBuildTask
import mb.spoofax.gradle.task.registerSpoofaxCleanTask
import mb.spoofax.gradle.util.*
import org.apache.commons.vfs2.FileObject
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.*
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.metaborg.core.MetaborgException
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion
import org.metaborg.core.project.ISimpleProjectService
import org.metaborg.core.resource.ResourceChangeKind
import org.metaborg.core.resource.ResourceUtils
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.SpoofaxConstants
import org.metaborg.spoofax.core.resource.SpoofaxIgnoresSelector
import org.metaborg.spoofax.meta.core.SpoofaxExtensionModule
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.build.SpoofaxLangSpecCommonPaths
import org.metaborg.spoofax.meta.core.config.StrategoFormat
import org.metaborg.spt.core.SPTModule
import org.metaborg.spt.core.SPTRunner
import java.io.File

@Suppress("unused")
class SpoofaxLangSpecPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(BasePlugin::class)
    project.pluginManager.apply(SpoofaxBasePlugin::class)
    // Apply Java library plugin before afterEvaluate to make configurations available to extension.
    project.pluginManager.apply(JavaLibraryPlugin::class)

    val extension = SpoofaxExtension(project)
    project.extensions.add("spoofax", extension)

    // Use a null module plugin loader for Spoofax, as service loading does not work well in a Gradle environment.
    val spoofaxModulePluginLoader = NullModulePluginLoader()
    val spoofax = Spoofax(spoofaxModulePluginLoader, SpoofaxGradleModule(), SpoofaxExtensionModule())
    spoofax.configureAsHeadlessApplication()
    val spoofaxMeta = SpoofaxMeta(spoofax, spoofaxModulePluginLoader, SpoofaxGradleMetaModule())
    project.afterEvaluate { configure(this, extension, spoofax, spoofaxMeta) }
    project.gradle.buildFinished {
      spoofaxMeta.close()
      spoofax.close()
    }
  }

  private fun configure(project: Project, extension: SpoofaxExtension, spoofax: Spoofax, spoofaxMeta: SpoofaxMeta) {
    val compileLanguageConfig = project.compileLanguageConfig
    val sourceLanguageConfig = project.sourceLanguageConfig
    val languageConfig = project.languageConfig
    val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

    // Get project projectDir.
    val resourceSrv = spoofax.resourceService
    val projectDir = project.projectDir
    val projectLoc = resourceSrv.resolve(projectDir)

    // Override Spoofax configuration from Gradle build script.
    val configOverride = run {
      val groupId = project.group.toString()
      val id = project.name
      val version = LanguageVersion.parse(project.version.toString())
      val metaborgVersion = extension.metaborgVersion
      val compileDeps = compileLanguageConfig.dependencies.map { it.toSpoofaxDependency() }
      val sourceDeps = sourceLanguageConfig.dependencies.map { it.toSpoofaxDependency() }
      val javaDeps = javaApiConfig.allDependencies.map { it.toSpoofaxDependency() }
      ConfigOverride(groupId, id, version, metaborgVersion, compileDeps, sourceDeps, javaDeps)
    }
    spoofaxMeta.injector.getInstance(SpoofaxGradleProjectConfigService::class.java).addOverride(projectLoc, configOverride)
    spoofaxMeta.injector.getInstance(SpoofaxGradleLanguageComponentConfigService::class.java).addOverride(projectLoc, configOverride)
    spoofaxMeta.injector.getInstance(SpoofaxGradleLanguageSpecConfigService::class.java).addOverride(projectLoc, configOverride)

    // Create Spoofax language specification project.
    val projectService = spoofax.projectService as ISimpleProjectService
    val langSpecProject = spoofaxMeta.languageSpecService.get(projectService.create(projectLoc))
      ?: throw GradleException("Project at $projectDir is not a Spoofax language specification project")
    val langSpecPaths = SpoofaxLangSpecCommonPaths(projectLoc)

    // Read Spoofax language specification configuration.
    val config = langSpecProject.config()
    project.group = config.identifier().groupId
    if(project.name != config.identifier().id) {
      throw GradleException("Project name ${project.name} is not equal to language ID ${config.identifier().id} from metaborg.yaml")
    }
    // Set project version only if it it has not been set yet.
    if(project.version == Project.DEFAULT_VERSION) {
      project.version = config.identifier().version.toString()
    }
    // Add dependencies to corresponding dependency configurations when they are empty.
    if(compileLanguageConfig.dependencies.isEmpty()) {
      for(langId in config.compileDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(compileLanguageConfig.name, dependency) {
          configureSpoofaxLanguageArtifact(dependency)
        }
      }
    }
    if(sourceLanguageConfig.dependencies.isEmpty()) {
      for(langId in config.sourceDeps()) {
        val dependency = langId.toGradleDependency(project)
        project.dependencies.add(sourceLanguageConfig.name, dependency) {
          configureSpoofaxLanguageArtifact(dependency)
        }
      }
    }
    if(javaApiConfig.allDependencies.isEmpty()) {
      for(id in config.javaDeps()) {
        val dependency = id.toGradleDependency(project, configuration = null)
        javaApiConfig.dependencies.add(dependency)
      }
      javaApiConfig.dependencies.add(project.dependencies.create("org.metaborg", "org.metaborg.spoofax.core", extension.metaborgVersion))
      extension.addSpoofaxRepos()
    }


    // Configure Java source and output directories.
    project.configure<SourceSetContainer> {
      val mainSourceSet = getByName(SourceSet.MAIN_SOURCE_SET_NAME)
      mainSourceSet.java {
        // Spoofax build uses the following additional source directories.
        srcDir("src/main/strategies")
        srcDir("src-gen/stratego-java")
        srcDir("src-gen/ds-java")
        // Spoofax build expects compiled Java classes in (Maven-style) 'target/classes' directory.
        @Suppress("UnstableApiUsage")
        outputDir = File(project.projectDir, "target/classes")
      }
    }


    // Build tasks.
    val srcGenDir = projectDir.resolve("src-gen")
    val targetDir = projectDir.resolve("target")
    val targetMetaborgDir = targetDir.resolve("metaborg")
    val buildTask = project.tasks.registerSpoofaxBuildTask(spoofax, langSpecProject)
    buildTask.configure {
      // Task dependencies:
      // 1. Language dependencies configuration, which influences which languages are loaded.
      dependsOn(languageConfig)
      if(extension.approximateDependencies) {
        // Inputs:
        // * `metaborg.yaml` config file
        inputs.file(projectDir.resolve("metaborg.yaml"))
        // * meta-language files
        inputs.files(project.fileTree(".") {
          include("**/*.esv", "**/*.sdf", "**/*.def", "**/*.sdf3", "**/*.nabl", "**/*.ts", "**/*.nabl2", "**/*.statix", "**/*.ds")
          exclude("/src-gen", "/target", "/build", "/.gradle", "/.git")
        })
        // TODO: included files that are not in the project directory.
        // Outputs:
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
        // * TODO: Statix
        // * TODO: dynsem
      } else {
        // Inputs: any file in the project directory.
        inputs.dir(projectDir)
        // Outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      langSpecProject.config().pardonedLanguages().forEach {
        addPardonedLanguage(it)
      }

      doFirst {
        // Requires languages and dialects to be loaded.
        lazyLoadLanguages(project.languageConfig, project, spoofax)
        lazyLoadDialects(projectLoc, project, spoofax)
      }
    }

    val metaBuilder = spoofaxMeta.metaBuilder
    val metaBuilderInput = LanguageSpecBuildInput(langSpecProject)
    val metaborgComponentYaml = srcGenDir.resolve("metaborg.component.yaml")
    val langSpecGenSourcesTask = project.tasks.register("spoofaxLangSpecGenerateSources") {
      // Task dependencies:
      // 1. Language dependencies configuration, which can influence dependencies in configuration, which in turn influences the src-gen/metaborg.component.yaml file.
      dependsOn(languageConfig)
      // 2. Must run after build task, because there may be custom generateSources build steps which require files to be built.
      mustRunAfter(buildTask)
      // Inputs:
      // * Files from artifacts from all dependencies in the language configuration.
      inputs.files(languageConfig)
      if(extension.approximateDependencies) {
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
        // * Any file in the project directory.
        inputs.dir(projectDir)
        // Outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      doLast {
        metaBuilder.initialize(metaBuilderInput)
        metaBuilder.generateSources(metaBuilderInput, null)
      }
    }
    val langSpecCompileTask = project.tasks.register("spoofaxLangSpecCompile") {
      // Task dependencies:
      // 1. Language dependencies configuration, which influences which languages are loaded.
      dependsOn(languageConfig)
      // 2. Spoofax build task, which provides sources which are compiled by this task.
      dependsOn(buildTask)
      // 3. Generate sources task, which provides sources which are compiled by this task.
      dependsOn(langSpecGenSourcesTask)
      if(extension.approximateDependencies) {
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
        when(config.strFormat()!!) {
          StrategoFormat.jar -> outputs.dir(srcGenDir.resolve("stratego-java"))
          StrategoFormat.ctree -> outputs.file(targetMetaborgDir.resolve("stratego.ctree"))
        }
        outputs.file(targetMetaborgDir.resolve("typesmart.context"))
        // TODO: Stratego include files and paths that are not in the project directory.
      } else {
        // Inputs: any file in the project directory.
        inputs.dir(projectDir)
        // Outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      doFirst {
        // Requires languages and dialects to be loaded.
        lazyLoadLanguages(project.languageConfig, project, spoofax)
        lazyLoadDialects(projectLoc, project, spoofax)
      }

      doLast {
        metaBuilder.compile(metaBuilderInput)
      }
    }
    // Since langSpecCompileTask will generate .java files, the compileJava task from the java plugin depends on it.
    val compileJavaTask = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
    compileJavaTask.dependsOn(langSpecCompileTask)
    val langSpecPackageTask = project.tasks.register("spoofaxLangSpecPackage") {
      // Task dependencies:
      // 1. Language dependencies configuration, which influences which languages are loaded.
      dependsOn(languageConfig)
      // 2. Compile task, which provides files that are packaged with this task.
      dependsOn(langSpecCompileTask)
      if(extension.approximateDependencies) {
        // Input: Stratego and Stratego Java strategies compiled class files.
        inputs.files(targetDir.resolve("classes"))
        // Stratego JAR
        if(config.strFormat() == StrategoFormat.jar) {
          // Input: pp.af and .tbl files are included into the JAR file
          inputs.files(project.fileTree(".") {
            include("**/*.pp.af", "**/*.tbl")
            exclude("/target", "/build", "/.gradle", "/.git")
          })
          // Output: Stratego JAR file.
          outputs.file(targetMetaborgDir.resolve("stratego.jar"))
        }
        // Output: Stratego Java strategies JAR file. Optional because it may not exist if there are no Java strategies.
        outputs.file(targetMetaborgDir.resolve("stratego-javastrat.jar")).optional()
      } else {
        // Inputs: any file in the project directory.
        inputs.dir(projectDir)
        // Outputs: any file in the project directory.
        outputs.dir(projectDir)
      }

      doFirst {
        // Requires languages and dialects to be loaded.
        lazyLoadLanguages(project.languageConfig, project, spoofax)
        lazyLoadDialects(projectLoc, project, spoofax)
      }

      doLast {
        metaBuilder.pkg(metaBuilderInput)
      }
    }
    val archiveLoc = langSpecPaths.spxArchiveFile(config.identifier().toFileString())
    val archiveFile = resourceSrv.localPath(archiveLoc)!!
    val langSpecArchiveTask = project.tasks.register("spoofaxLangSpecArchive") {
      // Task dependencies:
      // 1. Package task, which provides files that are archived with this task.
      dependsOn(langSpecPackageTask)
      if(extension.approximateDependencies) {
        val iconsDir = projectDir.resolve("icons")
        if(iconsDir.exists()) {
          inputs.dir(iconsDir)
        }
        inputs.dir(targetMetaborgDir)
        inputs.file(metaborgComponentYaml)
        // TODO: exported files.
      } else {
        // Inputs: any file in the project directory.
        inputs.dir(projectDir)
      }
      // Outputs: the spoofax-language archive file.
      outputs.file(archiveFile)

      doLast {
        metaBuilder.archive(metaBuilderInput)
        lazyLoadCompiledLanguage(archiveLoc, project, spoofax) // Test language archive by loading it.
      }
    }
    val assembleTask = project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
    assembleTask.dependsOn(langSpecArchiveTask)


    // Clean tasks.
    val spoofaxCleanTask = project.tasks.registerSpoofaxCleanTask(spoofax, langSpecProject)
    spoofaxCleanTask.configure {
      // No other inputs/outputs known: always execute.

      doFirst {
        // Requires languages and dialects to be loaded.
        lazyLoadLanguages(project.languageConfig, project, spoofax)
        lazyLoadDialects(projectLoc, project, spoofax)
      }
    }
    val langSpecCleanTask = project.tasks.register("spoofaxLangSpecClean") {
      // Task dependencies: should run after regular clean.
      shouldRunAfter(spoofaxCleanTask)
      // Inputs/outputs: none, always execute.

      doLast {
        metaBuilder.clean(metaBuilderInput)
      }
    }
    val cleanTask = project.tasks.getByName(LifecycleBasePlugin.CLEAN_TASK_NAME)
    cleanTask.dependsOn(langSpecCleanTask)


    // Build examples tasks.
    val buildExamplesTask = project.tasks.registerSpoofaxBuildTask(spoofax, langSpecProject, "spoofaxBuildExamples")
    buildExamplesTask.configure {
      // Task dependencies:
      // 1. Archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(langSpecArchiveTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir.resolve(extension.examplesDir))
      // Outputs: up to date when creating the archive was up to date (skipped).
      outputs.upToDateWhen {
        langSpecArchiveTask.get().state.upToDate
      }

      // Only build files of compiled language.
      addLanguage(langSpecProject.config().identifier())
      // Only execute task if buildExamples is set to true.
      onlyIf { extension.buildExamples }

      doFirst {
        // Requires compiled language to be loaded.
        lazyLoadCompiledLanguage(archiveLoc, project, spoofax)
      }
    }
    val checkTask = project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
    checkTask.dependsOn(buildExamplesTask)


    // SPT testing tasks.
    val sptLanguageConfig = project.configurations.create("sptLanguage") {
      isVisible = false
      isTransitive = false
      isCanBeConsumed = false
      isCanBeResolved = true
    }
    val sptId = LanguageIdentifier(extension.metaborgGroup, SpoofaxConstants.LANG_SPT_ID, LanguageVersion.parse(extension.metaborgVersion))
    val sptDependency = sptId.toGradleDependency(project)
    project.dependencies.add(sptLanguageConfig.name, sptDependency) {
      configureSpoofaxLanguageArtifact(sptDependency)
    }
    val spoofaxTestTask = project.tasks.register("spoofaxTest") {
      // Only execute task if runTests is set to true.
      onlyIf { extension.runTests }

      // Task dependencies:
      // 1. Archive task, which provides the archive of the compiled language specification which we are loading in this task.
      dependsOn(langSpecArchiveTask)
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
        lazyLoadCompiledLanguage(archiveLoc, project, spoofax)
        lazyLoadLanguages(sptLanguageConfig, project, spoofax)
      }

      doLast {
        val sptLangImpl = spoofax.languageService.getImpl(sptId)
          ?: throw GradleException("Failed to get SPT language implementation ($sptId)")
        val sptInjector = spoofaxMeta.injector.createChildInjector(SPTModule())
        val sptRunner = sptInjector.getInstance(SPTRunner::class.java)
        val langUnderTest = spoofax.languageService.getImpl(config.identifier())
        try {
          sptRunner.test(langSpecProject, sptLangImpl, langUnderTest)
          sptResultFile.writeText("success")
        } catch(e: MetaborgException) {
          sptResultFile.writeText("failed")
          throw GradleException("SPT tests failed", e)
        }
      }
    }
    checkTask.dependsOn(spoofaxTestTask)


    // Add the archive file as an artifact.
    val artifact = project.artifacts.add(Dependency.DEFAULT_CONFIGURATION, archiveFile) {
      this.name = project.name
      this.extension = SpoofaxBasePlugin.spoofaxLanguageExtension
      this.type = SpoofaxBasePlugin.spoofaxLanguageExtension
      builtBy(langSpecArchiveTask)
    }
    if(extension.createPublication) {
      // Add artifact as main publication.
      project.pluginManager.withPlugin("maven-publish") {
        project.extensions.configure<PublishingExtension> {
          publications.create<MavenPublication>("SpoofaxLanguage") {
            artifact(artifact) {
              this.extension = SpoofaxBasePlugin.spoofaxLanguageExtension
            }
            pom {
              packaging = SpoofaxBasePlugin.spoofaxLanguageExtension
              withXml {
                val root = asElement()
                val doc = root.ownerDocument
                val dependenciesNode = doc.createElement("dependencies")
                for(dependency in languageConfig.allDependencies) {
                  val dependencyNode = doc.createElement("dependency")

                  val groupIdNode = doc.createElement("groupId")
                  groupIdNode.appendChild(doc.createTextNode(dependency.group))
                  dependencyNode.appendChild(groupIdNode)

                  val artifactIdNode = doc.createElement("artifactId")
                  artifactIdNode.appendChild(doc.createTextNode(dependency.name))
                  dependencyNode.appendChild(artifactIdNode)

                  val versionNode = doc.createElement("version")
                  versionNode.appendChild(doc.createTextNode(dependency.version))
                  dependencyNode.appendChild(versionNode)

                  val scopeNode = doc.createElement("type")
                  scopeNode.appendChild(doc.createTextNode(SpoofaxBasePlugin.spoofaxLanguageExtension))
                  dependencyNode.appendChild(scopeNode)

                  dependenciesNode.appendChild(dependencyNode)
                }
                // TODO: add Java dependencies
                root.appendChild(dependenciesNode)
              }
            }
          }
        }
      }
    }
  }

  private fun lazyLoadLanguages(languageConfig: Configuration, project: Project, spoofax: Spoofax) =
    lazilyDo(project, "loadedLanguagesFrom-" + languageConfig.name) {
      languageConfig.forEach { spoofaxLanguageFile ->
        val spoofaxLanguageLoc = spoofax.resourceService.resolve(spoofaxLanguageFile)
        try {
          spoofax.languageDiscoveryService.languageFromArchive(spoofaxLanguageLoc)
        } catch(e: MetaborgException) {
          throw GradleException("Failed to load language from $spoofaxLanguageFile", e)
        }
      }
    }

  private fun lazyLoadDialects(projectLoc: FileObject, project: Project, spoofax: Spoofax) =
    lazilyDo(project, "loadedDialects") {
      val resources = ResourceUtils.find(projectLoc, SpoofaxIgnoresSelector())
      val creations = ResourceUtils.toChanges(resources, ResourceChangeKind.Create)
      spoofax.dialectProcessor.update(projectLoc, creations)
    }

  private fun lazyLoadCompiledLanguage(archiveLoc: FileObject, project: Project, spoofax: Spoofax) =
    lazilyDo(project, "loadedCompiledLanguage") {
      spoofax.languageDiscoveryService.languageFromArchive(archiveLoc)
    }

  private inline fun lazilyDo(project: Project, id: String, func: () -> Unit) {
    if(project.extra.has(id)) return
    func()
    project.extra.set(id, true)
  }
}

@Suppress("unused")
open class SpoofaxExtension(private val project: Project) {
  var metaborgGroup: String = SpoofaxBasePlugin.defaultMetaborgGroup
  var metaborgVersion: String = SpoofaxBasePlugin.defaultMetaborgVersion
  var createPublication: Boolean = true
  var buildExamples: Boolean = false
  var examplesDir: String = "example"
  var runTests: Boolean = true
  var approximateDependencies: Boolean = true


  private val compileLanguageConfig = project.compileLanguageConfig

  fun addCompileLanguageDep(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(compileLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }

  fun addCompileLanguageProjectDep(path: String): Dependency {
    val dependency = project.dependencies.project(path, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(compileLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }


  private val sourceLanguageConfig = project.sourceLanguageConfig

  fun addSourceLanguageDep(group: String, name: String, version: String): Dependency {
    val dependency = project.dependencies.create(group, name, version, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(sourceLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }

  fun addSourceLanguageProjectDep(path: String): Dependency {
    val dependency = project.dependencies.project(path, Dependency.DEFAULT_CONFIGURATION)
    project.dependencies.add(sourceLanguageConfig.name, dependency) {
      configureSpoofaxLanguageArtifact(dependency)
    }
    return dependency
  }


  private val javaApiConfig = project.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME)

  fun addSpoofaxCoreDep(): Dependency {
    val dependency = project.dependencies.create(metaborgGroup, "org.metaborg.spoofax.core", metaborgVersion)
    javaApiConfig.dependencies.add(dependency)
    return dependency
  }


  fun addSpoofaxRepos() {
    project.repositories {
      maven("https://artifacts.metaborg.org/content/repositories/releases/")
      maven("https://artifacts.metaborg.org/content/repositories/snapshots/")
      maven("https://pluto-build.github.io/mvnrepository/")
      maven("https://sugar-lang.github.io/mvnrepository/")
      maven("http://nexus.usethesource.io/content/repositories/public/")
    }
  }
}
