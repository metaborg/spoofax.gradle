import org.metaborg.convention.Person

// Workaround for issue: https://youtrack.jetbrains.com/issue/KTIJ-19369
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    `java-library`
    id("org.metaborg.convention.java")
    id("org.metaborg.convention.maven-publish")
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.gitonium)
}

// Configure Gitonium before setting the version
gitonium {
    mainBranch.set("master")
    tagPrefix.set("devenv-release/")
}

version = gitonium.version
group = "org.metaborg.devenv"

dependencies {
    api(platform(libs.metaborg.platform))

    api(libs.spoofax.meta.core)
    api(libs.spt.core)

    /*
    org.metaborg.spoofax.meta.core depends on older version of several artifacts. Due to an issue in Gradle, the first
    version of those artifacts that are loaded will be used by code in plugins that react to certain Gradle events, such
    as Project#afterEvaluate. Since the old versions do not have certain APIs, this will fail. Therefore, we force the
    versions to the latest ones.
    */
    api(libs.metaborg.resource.api)
    api(libs.metaborg.common)
    api(libs.metaborg.pie.runtime)
}

gradlePlugin {
    plugins {
        create("spoofax-base") {
            id = "org.metaborg.devenv.spoofax.gradle.base"
            implementationClass = "mb.spoofax.gradle.plugin.SpoofaxBasePlugin"
        }
        create("spoofax-language-specification") {
            id = "org.metaborg.devenv.spoofax.gradle.langspec"
            implementationClass = "mb.spoofax.gradle.plugin.SpoofaxLanguageSpecificationPlugin"
        }
        create("spoofax-project") {
            id = "org.metaborg.devenv.spoofax.gradle.project"
            implementationClass = "mb.spoofax.gradle.plugin.SpoofaxProjectPlugin"
        }
        create("spoofax-test") {
            id = "org.metaborg.devenv.spoofax.gradle.test"
            implementationClass = "mb.spoofax.gradle.plugin.SpoofaxTestPlugin"
        }
    }
}

// Add generated resources directory as a resource source directory.
val generatedResourcesDir = project.buildDir.resolve("generated/resources")
sourceSets {
    main {
        resources {
            srcDir(generatedResourcesDir)
        }
    }
}

//// Task that writes properties to a config.properties file, which is used in the plugin.
//val propertiesFile = generatedResourcesDir.resolve("config.properties")
//val generatePropertiesTask = tasks.register("generateConfigProperties") {
//    inputs.property("spoofax2Version", spoofax2Version)
//    inputs.property("spoofax2CoreDependency", spoofax2CoreDependency)
//    outputs.file(propertiesFile)
//    doLast {
//        val properties = NonShittyProperties()
//        properties.setProperty("spoofax2Version", spoofax2Version)
//        properties.setProperty("spoofax2CoreDependency", spoofax2CoreDependency)
//        propertiesFile.parentFile.run { if (!exists()) mkdirs() }
//        propertiesFile.bufferedWriter().use {
//            properties.storeWithoutDate(it)
//        }
//    }
//}
//tasks.compileJava.configure { dependsOn(generatePropertiesTask) }
//tasks.processResources.configure { dependsOn(generatePropertiesTask) }
//// Custom properties class that does not write the current date, fixing incrementality.
//class NonShittyProperties : java.util.Properties() {
//    fun storeWithoutDate(writer: java.io.BufferedWriter) {
//        val e: java.util.Enumeration<*> = keys()
//        while (e.hasMoreElements()) {
//            val key = e.nextElement()
//            val value = get(key)
//            writer.write("$key=$value")
//            writer.newLine()
//        }
//        writer.flush()
//    }
//}


mavenPublishConvention {
    repoOwner.set("metaborg")
    repoName.set("spoofax.gradle")

    metadata {
        inceptionYear.set("2019")
        developers.set(listOf(
            Person("Gohla", "Gabriel Konat", "gabrielkonat@gmail.com"),
            Person("Virtlink", "Daniel A. A. Pelsmaeker", "developer@pelsmaeker.net"),
        ))
    }
}
