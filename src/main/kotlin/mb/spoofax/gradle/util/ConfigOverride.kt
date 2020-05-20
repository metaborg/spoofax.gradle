package mb.spoofax.gradle.util

import com.google.inject.Injector
import mb.spoofax.gradle.plugin.SpoofaxExtensionBase
import mb.spoofax.gradle.plugin.compileLanguageFiles
import mb.spoofax.gradle.plugin.sourceLanguageFiles
import org.apache.commons.configuration2.HierarchicalConfiguration
import org.apache.commons.configuration2.tree.ImmutableNode
import org.apache.commons.vfs2.FileObject
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.metaborg.core.config.AConfigurationReaderWriter
import org.metaborg.core.config.ConfigRequest
import org.metaborg.core.config.ILanguageComponentConfig
import org.metaborg.core.config.LanguageComponentConfig
import org.metaborg.core.config.LanguageComponentConfigBuilder
import org.metaborg.core.config.LanguageComponentConfigService
import org.metaborg.core.config.ProjectConfig
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
import javax.inject.Inject

class SpoofaxGradleProjectConfigService @Inject constructor(
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

class SpoofaxGradleLanguageComponentConfigService @Inject constructor(
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

class SpoofaxGradleLanguageSpecConfigService @Inject constructor(
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

data class ConfigOverride(
  val groupId: String? = null,
  val id: String? = null,
  val version: LanguageVersion? = null,
  val metaborgVersion: String? = null,
  val compileDeps: Collection<LanguageIdentifier> = mutableListOf(),
  val sourceDeps: Collection<LanguageIdentifier> = mutableListOf(),
  val javaDeps: Collection<LanguageIdentifier> = mutableListOf()
) {
  fun applyToConfig(config: HierarchicalConfiguration<ImmutableNode>, languageComponentConfig: ILanguageComponentConfig?) {
    if(languageComponentConfig != null) {
      val identifier = run {
        val identifier = languageComponentConfig.identifier()
        LanguageIdentifier(groupId ?: identifier.groupId, id ?: identifier.id, version ?: identifier.version)
      }
      config.setProperty("id", identifier)
    }

    if(metaborgVersion != null) {
      config.setProperty("metaborgVersion", metaborgVersion)
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
  }

  override fun toString(): String {
    return "ConfigOverride(groupId=$groupId, id=$id, version=$version, metaborgVersion=$metaborgVersion, compileDeps=$compileDeps, sourceDeps=$sourceDeps, javaDeps=$javaDeps)"
  }
}

fun Project.overrideConfig(extension: SpoofaxExtensionBase, injector: Injector, overrideDependencies: Boolean) {
  val configOverride = run {
    val groupId = this.group.toString()
    val id = this.name
    val versionStr = this.version.toString()
    val version = if(versionStr != Project.DEFAULT_VERSION) LanguageVersion.parse(versionStr) else null
    val metaborgVersion = extension.metaborgVersion
    val compileDeps = if(overrideDependencies) {
      this.compileLanguageFiles.resolvedConfiguration.resolvedArtifacts.map {
        it.toSpoofaxDependency()
      }
    } else {
      mutableListOf()
    }
    val sourceDeps = if(overrideDependencies) {
      this.sourceLanguageFiles.resolvedConfiguration.resolvedArtifacts.map {
        it.toSpoofaxDependency()
      }
    } else {
      mutableListOf()
    }
    val javaDeps = if(overrideDependencies) {
      this.configurations.getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration.resolvedArtifacts.map {
        it.toSpoofaxDependency()
      }
    } else {
      mutableListOf()
    }
    ConfigOverride(groupId, id, version, metaborgVersion, compileDeps, sourceDeps, javaDeps)
  }
  val projectLoc = injector.getInstance(IResourceService::class.java).resolve(projectDir)
  injector.getInstance(SpoofaxGradleProjectConfigService::class.java).setOverride(projectLoc, configOverride)
  injector.getInstance(SpoofaxGradleLanguageComponentConfigService::class.java).setOverride(projectLoc, configOverride)
  injector.getInstance(SpoofaxGradleLanguageSpecConfigService::class.java).setOverride(projectLoc, configOverride)
}
