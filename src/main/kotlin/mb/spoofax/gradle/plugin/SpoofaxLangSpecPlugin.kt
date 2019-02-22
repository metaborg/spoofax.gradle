package mb.spoofax.gradle.plugin

import mb.spoofax.gradle.task.*
import mb.spoofax.gradle.util.*
import org.gradle.api.*
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
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.SpoofaxConstants
import org.metaborg.spoofax.meta.core.SpoofaxExtensionModule
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuildInput
import org.metaborg.spoofax.meta.core.build.SpoofaxLangSpecCommonPaths
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

    Spoofax(SpoofaxGradleModule(), SpoofaxExtensionModule()).use { spoofax ->
      spoofax.configureAsHeadlessApplication()
      SpoofaxMeta(spoofax, SpoofaxGradleMetaModule()).use { spoofaxMeta ->
        project.afterEvaluate { configure(this, extension, spoofax, spoofaxMeta) }
      }
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
        outputDir = File(project.projectDir, "target/classes")
      }
    }


    // Dynamic loading tasks.
    val loadLanguagesTask = project.tasks.registerLoadLanguagesTask(spoofax, languageConfig)
    val loadDialectsTask = project.tasks.registerLoadDialectsTask(spoofax, projectLoc)
    loadDialectsTask.configure {
      // Task dependencies: loading languages.
      dependsOn(loadLanguagesTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: none, always execute.
    }


    // Build tasks.
    val buildTask = project.tasks.registerSpoofaxBuildTask(spoofax, langSpecProject)
    buildTask.configure {
      // Task dependencies: loading languages and dialects.
      dependsOn(loadLanguagesTask, loadDialectsTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: any file in the project directory.
      outputs.dir(projectDir)

      langSpecProject.config().pardonedLanguages().forEach {
        addPardonedLanguage(it)
      }
    }

    val metaBuilder = spoofaxMeta.metaBuilder
    val metaBuilderInput = LanguageSpecBuildInput(langSpecProject)
    val langSpecGenSourcesTask = project.tasks.register("spoofaxLangSpecGenerateSources") {
      // Task dependencies:
      // 1. Any task that contributes to the language configuration (languageConfig can influence dependencies in configuration, which in turn influences the src-gen/metaborg.component.yaml file.)
      dependsOn(languageConfig)
      // 2. Must run after build task, because there may be custom generateSources build steps which require files to be built.
      mustRunAfter(buildTask)
      // Inputs:
      // 1. Files from artifacts from all dependencies in the language configuration.
      inputs.files(languageConfig)
      // 2. Any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: any file in the project directory.
      outputs.dir(projectDir)

      doLast {
        metaBuilder.initialize(metaBuilderInput)
        metaBuilder.generateSources(metaBuilderInput, null)
      }
    }
    val langSpecCompileTask = project.tasks.register("spoofaxLangSpecCompile") {
      // Task dependencies: loading languages and dialects, build, and generate-sources.
      dependsOn(loadLanguagesTask, loadDialectsTask, buildTask, langSpecGenSourcesTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: any file in the project directory.
      outputs.dir(projectDir)

      doLast {
        metaBuilder.compile(metaBuilderInput)
      }
    }
    // Since langSpecCompileTask will generate .java files, the compileJava task from the java plugin depends on it.
    val compileJavaTask = project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME)
    compileJavaTask.dependsOn(langSpecCompileTask)
    val langSpecPackageTask = project.tasks.register("spoofaxLangSpecPackage") {
      // Task dependencies: compile.
      dependsOn(langSpecCompileTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: any file in the project directory.
      outputs.dir(projectDir)

      doLast {
        metaBuilder.pkg(metaBuilderInput)
      }
    }
    val archiveLoc = langSpecPaths.spxArchiveFile(config.identifier().toFileString())
    val archiveFile = resourceSrv.localPath(archiveLoc)!!
    val langSpecArchiveTask = project.tasks.register("spoofaxLangSpecArchive") {
      // Task dependencies: package.
      dependsOn(langSpecPackageTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: the spoofax-language archive file.
      outputs.file(archiveFile)

      doLast {
        metaBuilder.archive(metaBuilderInput)
      }
    }
    val assembleTask = project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)
    assembleTask.dependsOn(langSpecArchiveTask)

    val loadCompiledLanguageTask = project.tasks.register("spoofaxLoadCompiledLanguage") {
      // Task dependencies:
      // 1. Task that creates the spoofax-language archive file.
      dependsOn(langSpecArchiveTask)
      // 2. Must run after loadLanguagesTask (if it runs at all), so that it reloads the language from the newly build archive if it was loaded before.
      mustRunAfter(loadLanguagesTask)
      // Inputs: spoofax-language archive file.
      inputs.file(archiveFile)
      // Outputs: up to date when there is a language component loaded from the archive location.
      outputs.upToDateWhen {
        spoofax.languageService.getComponent(archiveLoc.name) != null
      }

      doLast {
        spoofax.languageDiscoveryService.languageFromArchive(archiveLoc)
      }
    }
    val checkTask = project.tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME)
    checkTask.dependsOn(loadCompiledLanguageTask)


    // Clean tasks.
    val spoofaxCleanTask = project.tasks.registerSpoofaxCleanTask(spoofax, langSpecProject)
    spoofaxCleanTask.configure {
      // Depends on loaded languages.
      dependsOn(loadLanguagesTask) // TODO: only depends on compile languages
      // No other inputs/outputs known: always execute.
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
      // Task dependencies: loading the compiled language.
      dependsOn(loadCompiledLanguageTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: none, always execute.

      // Only build files of compiled language.
      addLanguage(langSpecProject.config().identifier())
      // Only execute task if buildExamples is set to true.
      onlyIf { extension.buildExamples }
    }
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
    val loadSptLanguageTask = project.tasks.registerLoadLanguagesTask(spoofax, sptLanguageConfig, "spoofaxLoadSptLanguage")
    loadSptLanguageTask.configure {
      // Only execute task if runTests is set to true.
      onlyIf { extension.runTests }
    }
    val spoofaxTestTask = project.tasks.register("spoofaxTest") {
      // Only execute task if runTests is set to true.
      onlyIf { extension.runTests }

      // Task dependencies: loading the SPT language, and the compiled language.
      dependsOn(loadSptLanguageTask, loadCompiledLanguageTask)
      // Inputs: any file in the project directory.
      inputs.dir(projectDir)
      // Outputs: none, always execute.

      doLast {
        val sptLangImpl = spoofax.languageService.getImpl(sptId)
          ?: throw GradleException("Failed to get SPT language implementation ($sptId)")
        val sptInjector = spoofaxMeta.injector.createChildInjector(SPTModule())
        val sptRunner = sptInjector.getInstance(SPTRunner::class.java)
        val langUnderTest = spoofax.languageService.getImpl(config.identifier())
        try {
          sptRunner.test(langSpecProject, sptLangImpl, langUnderTest)
        } catch(e: MetaborgException) {
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
}

@Suppress("unused")
open class SpoofaxExtension(private val project: Project) {
  var metaborgGroup: String = SpoofaxBasePlugin.defaultMetaborgGroup
  var metaborgVersion: String = SpoofaxBasePlugin.defaultMetaborgVersion
  var createPublication: Boolean = true
  var buildExamples: Boolean = false
  var runTests: Boolean = true


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
