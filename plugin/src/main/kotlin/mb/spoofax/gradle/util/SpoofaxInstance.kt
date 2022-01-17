package mb.spoofax.gradle.util

import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Singleton
import org.gradle.api.invocation.Gradle
import org.metaborg.core.build.dependency.IDependencyService
import org.metaborg.core.build.paths.ILanguagePathService
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
import org.metaborg.core.language.ILanguageService
import org.metaborg.core.plugin.IModulePluginLoader
import org.metaborg.core.resource.IResourceService
import org.metaborg.core.source.ISourceTextService
import org.metaborg.mbt.core.SpoofaxExtensionModule
import org.metaborg.meta.core.config.ILanguageSpecConfigBuilder
import org.metaborg.meta.core.config.ILanguageSpecConfigService
import org.metaborg.meta.core.config.ILanguageSpecConfigWriter
import org.metaborg.meta.core.config.LanguageSpecConfigService
import org.metaborg.spoofax.core.Spoofax
import org.metaborg.spoofax.core.SpoofaxModule
import org.metaborg.spoofax.core.build.ISpoofaxBuilder
import org.metaborg.spoofax.core.config.ISpoofaxProjectConfigBuilder
import org.metaborg.spoofax.core.config.ISpoofaxProjectConfigService
import org.metaborg.spoofax.core.config.ISpoofaxProjectConfigWriter
import org.metaborg.spoofax.core.config.SpoofaxProjectConfigBuilder
import org.metaborg.spoofax.meta.core.SpoofaxMeta
import org.metaborg.spoofax.meta.core.SpoofaxMetaModule
import org.metaborg.spoofax.meta.core.build.LanguageSpecBuilder
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigBuilder
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigService
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfigWriter
import org.metaborg.spoofax.meta.core.config.SpoofaxLanguageSpecConfigBuilder
import org.metaborg.spt.core.SPTModule
import org.metaborg.spt.core.SPTRunner
import java.util.concurrent.atomic.AtomicReference

open class SpoofaxInstance : AutoCloseable {
  companion object {
    private val internal = AtomicReference<SpoofaxInstance>()

    /**
     * Gets a SpoofaxInstance that is shared for this build. This instance should only be used for simple things like
     * reading non-overridable configuration of projects or language specs.
     */
    fun getShared(gradle: Gradle): SpoofaxInstance {
      if(internal.compareAndSet(null, SpoofaxInstance())) {
        gradle.buildFinished {
          internal.getAndUpdate { null }?.close()
        }
      }
      return internal.get()
    }
  }

  val spoofax: Spoofax get() = spoofaxInternal.updateAndGet { v -> v ?: createSpoofax() }
  val spoofaxMeta: SpoofaxMeta get() = spoofaxMetaInternal.updateAndGet { v -> v ?: createSpoofaxMeta() }
  val sptInjector: Injector get() = sptInjectorInternal.updateAndGet { v -> v ?: createSptInjector() }

  val resourceService: IResourceService get() = spoofax.resourceService
  val languageService: ILanguageService get() = spoofax.languageService
  val sourceTextService: ISourceTextService get() = spoofax.sourceTextService
  val dependencyService: IDependencyService get() = spoofax.dependencyService
  val languagePathService: ILanguagePathService get() = spoofax.languagePathService
  val builder: ISpoofaxBuilder get() = spoofax.builder

  val languageSpecBuilder: LanguageSpecBuilder get() = spoofaxMeta.metaBuilder
  internal val configOverrides: SpoofaxGradleConfigOverrides get() = spoofaxMeta.injector.getInstance(SpoofaxGradleConfigOverrides::class.java)

  val sptRunner: SPTRunner get() = sptInjector.getInstance(SPTRunner::class.java)

  override fun close() {
    spoofaxInternal.getAndUpdate { null }?.close()
    spoofaxMetaInternal.getAndUpdate { null }?.close()
    sptInjectorInternal.getAndUpdate { null }
  }

  private val spoofaxInternal: AtomicReference<Spoofax> = AtomicReference(null)
  private val spoofaxMetaInternal: AtomicReference<SpoofaxMeta> = AtomicReference(null)
  private val sptInjectorInternal: AtomicReference<Injector> = AtomicReference(null)

  private fun createSpoofax() = Spoofax(NullModulePluginLoader(), SpoofaxGradleModule(), SpoofaxExtensionModule(), org.metaborg.spoofax.meta.core.SpoofaxExtensionModule(), org.metaborg.spt.core.SpoofaxExtensionModule())
  private fun createSpoofaxMeta() = SpoofaxMeta(spoofax, NullModulePluginLoader(), SpoofaxGradleMetaModule())
  private fun createSptInjector() = spoofaxMeta.injector.createChildInjector(SPTModule())
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

    bind(SpoofaxGradleConfigOverrides::class.java).`in`(Singleton::class.java)

    bind(SpoofaxGradleLanguageSpecConfigService::class.java).`in`(Singleton::class.java)
    bind(ILanguageSpecConfigService::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)
    bind(ISpoofaxLanguageSpecConfigService::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)
    bind(ISpoofaxLanguageSpecConfigWriter::class.java).to(SpoofaxGradleLanguageSpecConfigService::class.java)

    bind(SpoofaxLanguageSpecConfigBuilder::class.java)
    bind(ILanguageSpecConfigBuilder::class.java).to(SpoofaxLanguageSpecConfigBuilder::class.java)
    bind(ISpoofaxLanguageSpecConfigBuilder::class.java).to(SpoofaxLanguageSpecConfigBuilder::class.java)
  }
}

