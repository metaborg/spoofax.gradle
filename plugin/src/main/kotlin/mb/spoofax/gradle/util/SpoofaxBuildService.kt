@file:Suppress("UnstableApiUsage")

package mb.spoofax.gradle.util

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class SpoofaxBuildService : SpoofaxInstance(), BuildService<BuildServiceParameters.None>, AutoCloseable
