import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-library`
    `maven-publish`
}

group = "org.valkyrienskies.kelvin"
// Determine the version
val gitRevision = "git rev-parse HEAD".execute()
version = "1.0.0+" + gitRevision.substring(0, 10)

repositories {
    mavenCentral()
    maven {
        name = "VS Maven"
        url = uri(project.findProperty("vs_maven_url") ?: "https://maven.valkyrienskies.org/")

        val vsMavenUsername = project.findProperty("vs_maven_username") as String?
        val vsMavenPassword = project.findProperty("vs_maven_password") as String?

        if (vsMavenPassword != null && vsMavenUsername != null) {
            credentials {
                username = vsMavenUsername
                password = vsMavenPassword
            }
        }
    }
}

kapt {
    correctErrorTypes = true
}

publishing {
    repositories {
        val vsMavenUsername = project.findProperty("vs_maven_username") as String?
        val vsMavenPassword = project.findProperty("vs_maven_password") as String?
        val vsMavenUrl = project.findProperty("vs_maven_url") as String?
        if (vsMavenUrl != null && vsMavenPassword != null && vsMavenUsername != null) {
            println("Publishing to VS Maven ($version)")
            maven {
                url = uri(vsMavenUrl)
                credentials {
                    username = vsMavenUsername
                    password = vsMavenPassword
                }
            }
        }
    }
}

// required for reproducible builds
// https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<KaptGenerateStubsTask>() {
    kotlinOptions.jvmTarget = "1.8"
}
tasks.register("writeVersion") {
    val outputFile = file("build/version.txt")
    outputs.upToDateWhen { false }
    outputs.file(outputFile)
    doLast {
        outputFile.writeText(version.toString())
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs += listOf("-Xjvm-default=all")
        }

        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions {
            freeCompilerArgs += listOf("-Xjvm-default=all")
        }
        kotlinOptions.jvmTarget = "1.8"
    }
    compileJava {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    compileTestJava {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// region Automatically accept Gradle build scan TOS

if (hasProperty("buildScan")) {
    extensions.findByName("buildScan")?.withGroovyBuilder {
        setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
        setProperty("termsOfServiceAgree", "yes")
    }
}

// endregion

// region Util functions

fun String.execute(envp: Array<String>? = null, dir: File = projectDir): String {
    val process = Runtime.getRuntime().exec(this, envp, dir)
    return process.inputStream.reader().readText()
}

// endregion

