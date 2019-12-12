package mb.spoofax.gradle.util

import com.google.inject.Injector
import mb.spoofax.gradle.plugin.SpoofaxExtensionBase
import mb.spoofax.gradle.plugin.compileLanguageConfig
import mb.spoofax.gradle.plugin.sourceLanguageConfig
import org.apache.commons.configuration2.HierarchicalConfiguration
import org.apache.commons.configuration2.tree.ImmutableNode
import org.apache.commons.vfs2.FileObject
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.metaborg.core.config.*
import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.core.language.LanguageVersion
import org.metaborg.core.messages.MessageBuilder
import org.metaborg.core.resource.IResourceService
import org.metaborg.spoofax.core.config.*
import org.metaborg.spoofax.meta.core.config.*
import javax.inject.Inject

class SpoofaxGradleProjectConfigService @Inject constructor(
  configReaderWriter: AConfigurationReaderWriter,
  configBuilder: SpoofaxProjectConfigBuilder
) : SpoofaxProjectConfigService(configReaderWriter, configBuilder) {
  private val overrides = mutableMapOf<FileObject, ConfigOverride>()

  fun addOverride(projectLoc: FileObject, override: ConfigOverride) {
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
}

class SpoofaxGradleLanguageComponentConfigService @Inject constructor(
  configReaderWriter: AConfigurationReaderWriter,
  configBuilder: LanguageComponentConfigBuilder
) : LanguageComponentConfigService(configReaderWriter, configBuilder) {
  private val overrides = mutableMapOf<FileObject, ConfigOverride>()

  fun addOverride(projectLoc: FileObject, override: ConfigOverride) {
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
}

class SpoofaxGradleLanguageSpecConfigService @Inject constructor(
  configReaderWriter: AConfigurationReaderWriter,
  configBuilder: SpoofaxLanguageSpecConfigBuilder
) : SpoofaxLanguageSpecConfigService(configReaderWriter, configBuilder) {
  private val overrides = mutableMapOf<FileObject, ConfigOverride>()

  fun addOverride(projectLoc: FileObject, override: ConfigOverride) {
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

fun Project.createAndAddOverride(extension: SpoofaxExtensionBase, resourceSrv: IResourceService, injector: Injector) {
  // Override Spoofax configuration from Gradle build script.
  val configOverride = run {
    val groupId = project.group.toString()
    val id = project.name
    val version = LanguageVersion.parse(project.version.toString())
    val metaborgVersion = extension.metaborgVersion
    val compileDeps = this.compileLanguageConfig.dependencies.map { it.toSpoofaxDependency() }
    val sourceDeps = this.sourceLanguageConfig.dependencies.map { it.toSpoofaxDependency() }
    val javaDeps = this.configurations.getByName(JavaPlugin.API_CONFIGURATION_NAME).allDependencies.map { it.toSpoofaxDependency() }
    ConfigOverride(groupId, id, version, metaborgVersion, compileDeps, sourceDeps, javaDeps)
  }
  val projectLoc = resourceSrv.resolve(projectDir)
  injector.getInstance(SpoofaxGradleProjectConfigService::class.java).addOverride(projectLoc, configOverride)
  injector.getInstance(SpoofaxGradleLanguageComponentConfigService::class.java).addOverride(projectLoc, configOverride)
  injector.getInstance(SpoofaxGradleLanguageSpecConfigService::class.java).addOverride(projectLoc, configOverride)
}
