package mb.spoofax.gradle.util

import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Singleton
import org.gradle.api.Project
import org.metaborg.core.config.ILanguageComponentConfigBuilder
import org.metaborg.core.config.ILanguageComponentConfigService
import org.metaborg.core.config.ILanguageComponentConfigWriter
import org.metaborg.core.config.IProjectConfigBuilder
import org.metaborg.core.config.IProjectConfigService
import org.metaborg.core.config.IProjectConfigWriter
import org.metaborg.core.config.LanguageComponentConfigBuilder
import org.metaborg.core.config.ProjectConfigService
import org.metaborg.core.editor.IEditorRegistry
import org.metaborg.core.editor.NullEditorRegistry
import org.metaborg.core.plugin.IModulePluginLoader
import org.metaborg.meta.core.config.ILanguageSpecConfigBuilder
import org.metaborg.meta.core.config.ILanguageSpecConfigService
import org.metaborg.meta.core.config.ILanguageSpecConfigWriter
import org.metaborg.meta.core.config.LanguageSpecConfigService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.SpoofaxModule
import org.metaborg.spoofax.core.config.ISpoofaxProjectConfigBuilder
import org.metaborg.spoofax.core.config.ISpoofaxProjectConfigService
import org.metaborg.spoofax.core.config.ISpoofaxProjectConfigWriter
import org.metaborg.spoofax.core.config.SpoofaxProjectConfigBuilder
import org.metaborg.spoofax.meta.core.SpoofaxExtensionModule
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.SpoofaxMetaModule
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigBuilder
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigService
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigWriter
import org.metaborg.spoofax.meta.core.config.SpoofaxLanguageSpecConfigBuilder
import org.metaborg.spt.core.SPTModule
import java.util.*

internal class SpoofaxInstance {
  private var spoofaxInternal: Spoofax? = null
  private var spoofaxMetaInternal: SpoofaxMeta? = null
  private var sptInjectorInternal: Injector? = null

  val spoofax: Spoofax
    get() = if(spoofaxInternal != null) {
      spoofaxInternal!!
    } else {
      val spoofax = createSpoofax()
      spoofaxInternal = spoofax
      spoofax
    }

  val spoofaxMeta: SpoofaxMeta
    get() = if(spoofaxMetaInternal != null) {
      spoofaxMetaInternal!!
    } else {
      val spoofaxMeta = createSpoofaxMeta(spoofax)
      spoofaxMetaInternal = spoofaxMeta
      spoofaxMeta
    }

  val sptInjector: Injector
    get() = if(sptInjectorInternal != null) {
      sptInjectorInternal!!
    } else {
      val injector = createSptInjector(spoofaxMeta.injector)
      sptInjectorInternal = injector
      injector
    }

  fun reset() {
    sptInjectorInternal = null
    if(spoofaxMetaInternal != null) {
      spoofaxMetaInternal!!.close()
      spoofaxMetaInternal = null
    }
    if(spoofaxInternal != null) {
      spoofaxInternal!!.close()
      spoofaxInternal = null
    }
  }

  companion object {
    private fun createSpoofax(): Spoofax {
      val spoofax = Spoofax(NullModulePluginLoader(), SpoofaxGradleModule(), SpoofaxExtensionModule(), org.metaborg.mbt.core.SpoofaxExtensionModule(), org.metaborg.spt.core.SpoofaxExtensionModule())
      spoofax.configureAsHeadlessApplication()
      return spoofax
    }

    private fun createSpoofaxMeta(spoofax: Spoofax): SpoofaxMeta {
      return SpoofaxMeta(spoofax, NullModulePluginLoader(), SpoofaxGradleMetaModule())
    }

    private fun createSptInjector(injector: Injector): Injector {
      return injector.createChildInjector(SPTModule())
    }
  }
}

internal object SpoofaxInstanceCache {
  private val instances: WeakHashMap<Project, SpoofaxInstance> = WeakHashMap()

  operator fun get(project: Project): SpoofaxInstance = instances.getOrPut(project) { SpoofaxInstance() }
}

// Use a null module plugin loader for Spoofax & SpoofaxMeta, as service loading does not work well in a Gradle environment.
private class NullModulePluginLoader : IModulePluginLoader {
  override fun modules() = mutableListOf<Module>()
}

private class SpoofaxGradleModule : SpoofaxModule() {
  override fun bindEditor() {
    bind(IEditorRegistry::class.java).to(NullEditorRegistry::class.java).`in`(Singleton::class.java)
  }

  override fun bindProjectConfig() {
    bind(IProjectConfigWriter::class.java).to(ProjectConfigService::class.java).`in`(Singleton::class.java)

    bind(SpoofaxGradleProjectConfigService::class.java).`in`(Singleton::class.java)
    bind(IProjectConfigService::class.java).to(SpoofaxGradleProjectConfigService::class.java)
    bind(ISpoofaxProjectConfigService::class.java).to(SpoofaxGradleProjectConfigService::class.java)
    bind(ISpoofaxProjectConfigWriter::class.java).to(SpoofaxGradleProjectConfigService::class.java)

    bind(SpoofaxProjectConfigBuilder::class.java)
    bind(IProjectConfigBuilder::class.java).to(SpoofaxProjectConfigBuilder::class.java)
    bind(ISpoofaxProjectConfigBuilder::class.java).to(SpoofaxProjectConfigBuilder::class.java)
  }

  override fun bindLanguageComponentConfig() {
    bind(SpoofaxGradleLanguageComponentConfigService::class.java).`in`(Singleton::class.java)
    bind(ILanguageComponentConfigWriter::class.java).to(SpoofaxGradleLanguageComponentConfigService::class.java)
    bind(ILanguageComponentConfigService::class.java).to(SpoofaxGradleLanguageComponentConfigService::class.java)

    bind(LanguageComponentConfigBuilder::class.java)
    bind(ILanguageComponentConfigBuilder::class.java).to(LanguageComponentConfigBuilder::class.java)
  }
}

private class SpoofaxGradleMetaModule : SpoofaxMetaModule() {
  override fun bindLanguageSpecConfig() {
    bind(ILanguageSpecConfigWriter::class.java).to(LanguageSpecConfigService::class.java).`in`(Singleton::class.java)

    bind(SpoofaxGradleLanguageSpecConfigService::class.java).`in`(Singleton::class.java)
    bind(ILanguageSpecConfigService::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)
    bind(ISpoofaxLanguageSpecConfigService::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)
    bind(ISpoofaxLanguageSpecConfigWriter::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)

    bind(SpoofaxLanguageSpecConfigBuilder::class.java)
    bind(ILanguageSpecConfigBuilder::class.java).to(SpoofaxLanguageSpecConfigBuilder::class.java)
    bind(ISpoofaxLanguageSpecConfigBuilder::class.java).to(SpoofaxLanguageSpecConfigBuilder::class.java)
  }
}

