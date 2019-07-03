plugins {
  id("org.metaborg.gradle.config.root-project") version "0.3.6"
  id("org.metaborg.gitonium") version "0.1.1"
  id("org.metaborg.spoofax.gradle.langspec") // No version, use included build only.
  id("de.set.ecj") version "1.4.1" // Use ECJ to speed up compilation of Stratego's generated Java files.
}

spoofax {
  buildExamples = true

  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.esv", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.lang.template", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "org.metaborg.meta.nabl2.lang", metaborgVersion)
  addCompileLanguageDep("org.metaborg", "dynsem", metaborgVersion)

  addSourceLanguageDep("org.metaborg", "meta.lib.spoofax", metaborgVersion)
  addSourceLanguageDep("org.metaborg", "org.metaborg.meta.nabl2.shared", metaborgVersion)
  addSourceLanguageDep("org.metaborg", "org.metaborg.meta.nabl2.runtime", metaborgVersion)

  addSpoofaxCoreDep()
  addSpoofaxRepos()

  dependencies {
    api("org.metaborg:org.metaborg.meta.lang.dynsem.interpreter:$metaborgVersion")
  }
}