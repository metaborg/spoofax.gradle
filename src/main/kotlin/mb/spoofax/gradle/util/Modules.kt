package mb.spoofax.gradle.util

import com.google.inject.Singleton
import org.metaborg.core.config.*
import org.metaborg.core.editor.IEditorRegistry
import org.metaborg.core.editor.NullEditorRegistry
import org.metaborg.meta.core.config.*
import org.metaborg.spoofax.core.SpoofaxModule
import org.metaborg.spoofax.core.config.*
import org.metaborg.spoofax.meta.core.SpoofaxMetaModule
import org.metaborg.spoofax.meta.core.config.*

class SpoofaxGradleModule : SpoofaxModule() {
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


class SpoofaxGradleMetaModule : SpoofaxMetaModule() {
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