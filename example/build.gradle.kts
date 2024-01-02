plugins {
//  id("org.metaborg.gradle.config.root-project") version "0.4.8"
//  id("org.metaborg.gitonium") version "0.1.5"

  // Set versions for plugins to use, only applying them in subprojects (apply false here).
//  id("dev.spoofax.spoofax2.gradle.langspec") apply false // No version: use the plugin from the included composite build
//  id("dev.spoofax.spoofax2.gradle.project") apply false
//  id("dev.spoofax.spoofax2.gradle.test") apply false
}

// TODO:
//subprojects {
//  metaborg {
//    configureSubProject()
//  }
//}

// Normally when you go to a multi-project buil d and you execute `test`, you will execute

// all `:test` tasks in all projects. In contrast, when you execute `:test`, you only
// execute the `:test` task in the root project.
// Now, we would like to create a task in this composite build `testAll`
// that executes basically the equivalent of `test` in each of the included multi-project
// builds, which would execute `:test` in each of the projects. However, this seems to be
// impossible to write down. Instead, we call the root `:test` task in the included build,
// and in each included build's multi-project root project we'll extend the `test` task
// to depend on the `:test` tasks of the subprojects.

// Build tasks
tasks.register("assemble") {
    group = "Build Tasks"
    description = "Assembles the outputs of the included builds."
    dependsOn(gradle.includedBuilds.map { it.task(":assemble") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("assemble") })
}
tasks.register("build") {
    group = "Build Tasks"
    description = "Assembles and tests the included builds."
    dependsOn(gradle.includedBuilds.map { it.task(":build") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("build") })
}
tasks.register("clean") {
    group = "Build Tasks"
    description = "Cleans the outputs of the included builds."
    dependsOn(gradle.includedBuilds.map { it.task(":clean") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("clean") })
}

// Help tasks
tasks.register("allTasks") {
    group = "Help Tasks"
    description = "Displays all tasks of the included builds."
    dependsOn(gradle.includedBuilds.map { it.task(":tasks") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("tasks") })
}

// Publishing tasks
tasks.register("publish") {
    group = "Publishing Tasks"
    description = "Publishes all included builds to the remote Maven repository."
    dependsOn(gradle.includedBuilds.map { it.task(":publish") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("publish") })
}
tasks.register("publishToMavenLocal") {
    group = "Publishing Tasks"
    description = "Publishes all included builds to the local Maven repository."
    dependsOn(gradle.includedBuilds.map { it.task(":publishToMavenLocal") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("publishToMavenLocal") })
}

// Verification tasks
tasks.register("check") {
    group = "Verification Tasks"
    description = "Runs all checks on the included builds."
    dependsOn(gradle.includedBuilds.map { it.task(":check") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("check") })
}
tasks.register("test") {
    group = "Verification Tasks"
    description = "Runs all unit tests on the included builds."
    dependsOn(gradle.includedBuilds.map { it.task(":test") })
    dependsOn(project.subprojects.mapNotNull { it.tasks.findByName("test") })
}