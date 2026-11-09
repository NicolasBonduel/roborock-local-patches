import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
}

group = "local.roborock"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    compileOnly(files(rootProject.file("tools/morphe-desktop.jar")))
}

kotlin.compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

val compileLoginUi = tasks.register<Exec>("compileLoginUi") {
    workingDir(rootProject.projectDir)
    commandLine("npm", "run", "build:js")
    inputs.files(
        rootProject.file("package-lock.json"),
        rootProject.file("package.json"),
        rootProject.file("scripts/build-login-ui.cjs"),
        file("src/main/resources/local-server.js"),
        file("src/main/resources/artwork/local-server-arrow.png"),
    )
    outputs.file(layout.buildDirectory.file("generated-resources/local-server.runtime.js"))
}

tasks.processResources {
    dependsOn(compileLoginUi)
    exclude("local-server.js", "artwork/**")
    from(layout.buildDirectory.dir("generated-resources"))
}

tasks.jar {
    manifest.attributes(
        "Name" to "Roborock Local Server",
        "Description" to "Direct local-server login for Roborock 4.76.08 arm64",
        "Version" to project.version,
        "Patcher-Version" to "2.3.0",
        "License" to "0BSD",
    )
}

val dexDirectory = layout.buildDirectory.dir("dex")
val androidSdk = providers.environmentVariable("ANDROID_HOME").orElse("${System.getProperty("user.home")}/Library/Android/sdk")

val compileDex = tasks.register<Exec>("compileDex") {
    dependsOn(tasks.jar)
    val jar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(jar)
    outputs.dir(dexDirectory)
    doFirst {
        dexDirectory.get().asFile.mkdirs()
        commandLine(
            "${androidSdk.get()}/build-tools/35.0.0/d8", "--release", "--min-api", "26",
            "--classpath", rootProject.file("tools/morphe-desktop.jar"),
            "--output", dexDirectory.get().asFile, jar.get().asFile,
        )
    }
}

tasks.register<Zip>("buildAndroid") {
    dependsOn(compileDex)
    archiveFileName.set("roborock-local-server-${project.version}.mpp")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(tasks.jar.map { zipTree(it.archiveFile.get().asFile) })
    from(dexDirectory)
}
