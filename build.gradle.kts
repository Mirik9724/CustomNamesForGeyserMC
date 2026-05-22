import org.jetbrains.gradle.ext.settings
import org.jetbrains.gradle.ext.taskTriggers

plugins {
    kotlin("jvm") version "2.0.20-Beta1"
    kotlin("kapt") version "2.0.20-Beta1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.8"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

group = "net.Mirik9724"
version = "1.1.7"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven {
        url = uri("https://repo.opencollab.dev/main/")
    }
    maven { url = uri("https://jitpack.io") }
    maven {
        name = "elytrium-repo"
        url = uri("https://maven.elytrium.net/repo/")
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    kapt("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    compileOnly("org.geysermc.geyser:api:2.9.0-SNAPSHOT")
    compileOnly("net.elytrium.limboapi:api:1.1.26")

    implementation("net.bytebuddy:byte-buddy:1.14.12")
    implementation("net.bytebuddy:byte-buddy-agent:1.14.12")

    compileOnly("com.github.Mirik9724:MirikAPI:v0.1.5.10")
    compileOnly(files("libs/WLU.jar"))
}

tasks {
    runVelocity {
        velocityVersion("3.4.0-SNAPSHOT")
    }
}

//kotlin { jvmToolchain(17) }

val templateSource = file("src/main/templates")
val templateDest = layout.buildDirectory.dir("generated/sources/templates")
val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(templateSource)
    into(templateDest)
    expand(props)
}

sourceSets.main.configure { java.srcDir(generateTemplates.map { it.outputs }) }

project.idea.project.settings.taskTriggers.afterSync(generateTemplates)
//project.eclipse.synchronizationTasks(generateTemplates)

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations.runtimeClasspath.get())

    relocate(
        "org.bstats",
        "${project.group}.libs.bstats"
    )
}