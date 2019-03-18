package mb.spoofax.gradle.util

import com.google.inject.Module
import org.metaborg.core.plugin.IModulePluginLoader

class NullModulePluginLoader : IModulePluginLoader {
  override fun modules() = mutableListOf<Module>()
}