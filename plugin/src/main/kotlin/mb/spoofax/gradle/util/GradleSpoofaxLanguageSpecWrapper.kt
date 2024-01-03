package mb.spoofax.gradle.util

import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfig
import org.metaborg.spoofax.meta.core.project.ISpoofaxLanguageSpec

class GradleSpoofaxLanguageSpecWrapper(
    /** The Gradle identifier of the language. */
    identifier: LanguageIdentifier,
    /** The specification being wrapped. */
    val spec: ISpoofaxLanguageSpec,
) : ISpoofaxLanguageSpec by spec {
    private val config = GradleSpoofaxLanguageSpecConfig(identifier, spec.config())
    override fun config(): ISpoofaxLanguageSpecConfig = config
}

class GradleSpoofaxLanguageSpecConfig(
    /** The Gradle identifier of the language. */
    val identifier: LanguageIdentifier,
    /** The configuration being wrapped. */
    val config: ISpoofaxLanguageSpecConfig,
) : ISpoofaxLanguageSpecConfig by config {
    override fun identifier(): LanguageIdentifier = identifier
}