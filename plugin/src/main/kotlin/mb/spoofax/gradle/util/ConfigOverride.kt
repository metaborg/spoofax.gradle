package mb.spoofax.gradle.util

import mb.spoofax.gradle.plugin.SpoofaxExtensionBase
import mb.spoofax.gradle.plugin.compileLanguageFiles
import mb.spoofax.gradle.plugin.sourceLanguageFiles
import org.apache.commons.configuration2.HierarchicalConfiguration
import org.apache.commons.configuration2.tree.ImmutableNode
import org.apache.commons.vfs2.FileObject
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.metaborg.core.MetaborgConstants
import org.metaborg.core.config.AConfigurationReaderWriter
import org.metaborg.core.config.ConfigRequest
import org.metaborg.core.config.ILanguageComponentConfig
import org.metaborg.core.config.LanguageComponentConfig
import org.metaborg.core.config.LanguageComponentConfigBuilder
import org.metaborg.core.config.LanguageComponentConfigService
import org.metaborg.core.config.ProjectConfig
import org.metaborg.core.language.LanguageContributionIdentifier
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion
import org.metaborg.core.messages.MessageBuilder
import org.metaborg.core.resource.IResourceService
import org.metaborg.spoofax.core.config.ISpoofaxProjectConfig
import org.metaborg.spoofax.core.config.SpoofaxProjectConfig
import org.metaborg.spoofax.core.config.SpoofaxProjectConfigBuilder
import org.metaborg.spoofax.core.config.SpoofaxProjectConfigService
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfig
import org.metaborg.spoofax.meta.core.config.SpoofaxLanguageSpecConfig
import org.metaborg.spoofax.meta.core.config.SpoofaxLanguageSpecConfigBuilder
import org.metaborg.spoofax.meta.core.config.SpoofaxLanguageSpecConfigService
import org.metaborg.spoofax.meta.core.config.StrategoFormat
import javax.inject.Inject

data class ConfigOverride(
  var metaborgVersion: String? = null,
  var groupId: String? = null,
  var id: String? = null,
  var version: LanguageVersion? = null,
  var compileDeps: Collection<LanguageIdentifier> = mutableListOf(),
  var sourceDeps: Collection<LanguageIdentifier> = mutableListOf(),
  var javaDeps: Collection<LanguageIdentifier> = mutableListOf(),
  var strategoFormat: StrategoFormat? = null
) {
  fun applyToConfig(config: HierarchicalConfiguration<ImmutableNode>, languageComponentConfig: ILanguageComponentConfig?) {
    if(metaborgVersion != null) {
      config.setProperty("metaborgVersion", metaborgVersion)
    }
    if(languageComponentConfig != null) {
      val originalIdentifier = languageComponentConfig.identifier()
      val newIdentifier = LanguageIdentifier(groupId ?: originalIdentifier.groupId, id ?: originalIdentifier.id, version
        ?: originalIdentifier.version)
      config.setProperty("id", newIdentifier)

      config.setProperty("contributions", languageComponentConfig.langContribs().map {
        if(it.id == originalIdentifier) {
          LanguageContributionIdentifier(newIdentifier, it.name)
        } else {
          it
        }
      }.toMutableList())
    }
    if(!compileDeps.isEmpty()) {
      config.setProperty("dependencies.compile", compileDeps)
    }
    if(!sourceDeps.isEmpty()) {
      config.setProperty("dependencies.source", sourceDeps)
    }
    if(!javaDeps.isEmpty()) {
      config.setProperty("dependencies.java", javaDeps)
    }
    if(strategoFormat != null) {
      config.setProperty("language.stratego.format", strategoFormat)
    }
  }
}

internal fun SpoofaxExtensionBase.overrideMetaborgVersion(metaborgVersion: String?) {
  configOverrides.update(project) {
    this.metaborgVersion = metaborgVersion
  }
}

internal fun SpoofaxExtensionBase.overrideIdentifiers() {
  configOverrides.update(project) {
    groupId = project.group.toString()
    id = project.name
    val versionStr = project.version.toString()
    version = if(versionStr != Project.DEFAULT_VERSION) LanguageVersion.parse(versionStr) else null
  }
}

internal fun SpoofaxExtensionBase.overrideDependencies() {
  configOverrides.update(project) {
    compileDeps = project.compileLanguageFiles.resolvedConfiguration.firstLevelModuleDependencies.map {
      it.toSpoofaxDependency()
    }
    sourceDeps = project.sourceLanguageFiles.resolvedConfiguration.firstLevelModuleDependencies.map {
      it.toSpoofaxDependency()
    }
    if(project.plugins.hasPlugin(JavaPlugin::class.java)) {
      project.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration.firstLevelModuleDependencies.map {
        it.toSpoofaxDependency()
      }
    }
  }
}

internal fun SpoofaxExtensionBase.overrideStrategoFormat(strategoFormat: StrategoFormat) {
  configOverrides.update(project) {
    this.strategoFormat = strategoFormat
  }
}


internal class SpoofaxGradleConfigOverrides @Inject constructor(
  private val resourceService: IResourceService,
  private val projectConfigService: SpoofaxGradleProjectConfigService,
  private val languageComponentConfigService: SpoofaxGradleLanguageComponentConfigService,
  private val languageSpecConfigService: SpoofaxGradleLanguageSpecConfigService
) {
  private val overrides = mutableMapOf<Project, ConfigOverride>()

  fun update(project: Project, fn: ConfigOverride.() -> Unit) {
    val override = overrides.getOrPut(project) { ConfigOverride() }
    override.fn()
    val projectLoc = resourceService.resolve(project.projectDir)
    projectConfigService.setOverride(projectLoc, override)
    languageComponentConfigService.setOverride(projectLoc, override)
    languageSpecConfigService.setOverride(projectLoc, override)
  }
}


internal class SpoofaxGradleProjectConfigService @Inject constructor(
  configReaderWriter: AConfigurationReaderWriter,
  private val configBuilder: SpoofaxProjectConfigBuilder
) : SpoofaxProjectConfigService(configReaderWriter, configBuilder) {
  private val overrides = mutableMapOf<FileObject, ConfigOverride>()

  fun setOverride(projectLoc: FileObject, override: ConfigOverride) {
    val configFile = getConfigFile(projectLoc)
    overrides[configFile] = override
  }

  override fun toConfig(config: HierarchicalConfiguration<ImmutableNode>, configFile: FileObject): ConfigRequest<ISpoofaxProjectConfig> {
    val projectConfig = SpoofaxProjectConfig(config)
    overrides[configFile]?.applyToConfig(config, null)
    val mb = MessageBuilder.create().asError().asInternal().withSource(configFile)
    val messages = projectConfig.validate(mb)
    return ConfigRequest(projectConfig, messages)
  }

  override fun getFromConfigFile(configFile: FileObject, rootFolder: FileObject): ConfigRequest<ISpoofaxProjectConfig> {
    val request = super.getFromConfigFile(configFile, rootFolder)
    if(request.config() == null) {
      // If the configuration is null, because there is no metaborg.yaml file, return a default config with override applied.
      return toConfig(fromConfig(configBuilder.reset().build(rootFolder)), configFile)
    }
    return request
  }
}

internal class SpoofaxGradleLanguageComponentConfigService @Inject constructor(
  configReaderWriter: AConfigurationReaderWriter,
  private val configBuilder: LanguageComponentConfigBuilder
) : LanguageComponentConfigService(configReaderWriter, configBuilder) {
  private val overrides = mutableMapOf<FileObject, ConfigOverride>()

  fun setOverride(projectLoc: FileObject, override: ConfigOverride) {
    val configFile = getConfigFile(projectLoc)
    overrides[configFile] = override
  }

  override fun toConfig(config: HierarchicalConfiguration<ImmutableNode>, configFile: FileObject): ConfigRequest<ILanguageComponentConfig> {
    val projectConfig = ProjectConfig(config)
    val languageComponentConfig = LanguageComponentConfig(config, projectConfig)
    overrides[configFile]?.applyToConfig(config, languageComponentConfig)
    val mb = MessageBuilder.create().asError().asInternal().withSource(configFile)
    val messages = languageComponentConfig.validate(mb)
    return ConfigRequest(languageComponentConfig, messages)
  }

  override fun getFromConfigFile(configFile: FileObject, rootFolder: FileObject): ConfigRequest<ILanguageComponentConfig> {
    val request = super.getFromConfigFile(configFile, rootFolder)
    if(request.config() == null) {
      // If the configuration is null, because there is no metaborg.yaml file, return a default config with override applied.
      return toConfig(fromConfig(configBuilder.reset().build(rootFolder)), configFile)
    }
    return request
  }
}

internal class SpoofaxGradleLanguageSpecConfigService @Inject constructor(
  configReaderWriter: AConfigurationReaderWriter,
  private val configBuilder: SpoofaxLanguageSpecConfigBuilder
) : SpoofaxLanguageSpecConfigService(configReaderWriter, configBuilder) {
  private val overrides = mutableMapOf<FileObject, ConfigOverride>()

  fun setOverride(projectLoc: FileObject, override: ConfigOverride) {
    val configFile = getConfigFile(projectLoc)
    overrides[configFile] = override
  }

  override fun toConfig(config: HierarchicalConfiguration<ImmutableNode>, configFile: FileObject): ConfigRequest<ISpoofaxLanguageSpecConfig> {
    val projectConfig = SpoofaxProjectConfig(config)
    val languageSpecConfig = SpoofaxLanguageSpecConfig(config, projectConfig)
    overrides[configFile]?.applyToConfig(config, languageSpecConfig)
    val mb = MessageBuilder.create().asError().asInternal().withSource(configFile)
    val messages = languageSpecConfig.validate(mb)
    return ConfigRequest(languageSpecConfig, messages)
  }

  override fun getFromConfigFile(configFile: FileObject, rootFolder: FileObject): ConfigRequest<ISpoofaxLanguageSpecConfig> {
    val request = super.getFromConfigFile(configFile, rootFolder)
    if(request.config() == null) {
      // If the configuration is null, because there is no metaborg.yaml file, return a default config with override applied.
      return toConfig(fromConfig(configBuilder.reset().build(rootFolder)), configFile)
    }
    return request
  }
}
