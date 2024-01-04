package mb.spoofax.gradle.util

import org.metaborg.core.language.LanguageIdentifier
import org.metaborg.spoofax.meta.core.config.ISpoofaxLanguageSpecConfig
import org.metaborg.spoofax.meta.core.project.ISpoofaxLanguageSpec

class GradleSpoofaxLanguageSpecWrapper(
    /** The specification being wrapped. */
    val spec: ISpoofaxLanguageSpec,
    /** The Gradle identifier of the language. */
    identifier: LanguageIdentifier,
) : ISpoofaxLanguageSpec by spec {
    private val config = GradleSpoofaxLanguageSpecConfig(spec.config(), identifier)
    override fun config(): ISpoofaxLanguageSpecConfig = config
}

class GradleSpoofaxLanguageSpecConfig(
    /** The configuration being wrapped. */
    val config: ISpoofaxLanguageSpecConfig,
    /** The Gradle identifier of the language. */
    val identifier: LanguageIdentifier,
) : ISpoofaxLanguageSpecConfig by config {
    override fun identifier(): LanguageIdentifier = identifier
}