import java.util.jar.JarFile

plugins {
    id("vs-core.convention")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.github.sgtsilvio.gradle.proguard") version "0.6.0"
}

// Create shade configuration
val shade: Configuration by configurations.creating {
    // Artifacts should be available while compiling, but should not show up in the POM file
    configurations.compileOnly.get().extendsFrom(this)
    // Artifacts should be available at runtime for testing only
    configurations.testImplementation.get().extendsFrom(this)
}

dependencies {
    api(project(":api"))

    // Kotlin
    api(kotlin("stdlib-jdk8"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    val jacksonVersion = "2.14.0"
    val nettyVersion = "4.1.25.Final"
    val kotestVersion = "5.4.1"

    // JOML for Math
    api("org.joml:joml:1.10.4")
    api("org.joml:joml-primitives:1.10.0")

    // Apache Commons Math for Linear Programming
    shade("org.apache.commons", "commons-math3", "3.6.1")

    // Guava
    implementation("com.google.guava", "guava", "31.0.1-jre")

    // Jackson Binary Dataformat for Object Serialization
    api("com.fasterxml.jackson.module", "jackson-module-kotlin", jacksonVersion)
    api("com.fasterxml.jackson.module", "jackson-module-parameter-names", jacksonVersion)
    api("com.fasterxml.jackson.dataformat", "jackson-dataformat-cbor", jacksonVersion)
    api("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml", jacksonVersion)
    api("com.github.Rubydesic:jackson-kotlin-dsl:1.2.0")

    api("com.networknt", "json-schema-validator", "1.0.71")
    shade("com.github.imifou", "jsonschema-module-addon", "1.2.1")
    shade("com.github.victools", "jsonschema-module-jackson", "4.25.0")
    shade("com.github.victools", "jsonschema-generator", "4.25.0")
    shade("com.flipkart.zjsonpatch", "zjsonpatch", "0.4.11")

    // FastUtil for Fast Primitive Collections
    implementation("it.unimi.dsi", "fastutil", "8.2.1")

    // Netty for networking (ByteBuf)
    api("io.netty", "netty-buffer", nettyVersion)

    // javax inject
    api("javax.inject", "javax.inject", "1")

    // Dagger for compile-time Dependency Injection
    val daggerVersion = "2.48"
    shade("com.google.dagger", "dagger", daggerVersion)
    annotationProcessor("com.google.dagger", "dagger-compiler", daggerVersion)
    testAnnotationProcessor("com.google.dagger", "dagger-compiler", daggerVersion)
    kapt("com.google.dagger", "dagger-compiler", daggerVersion)
    kaptTest("com.google.dagger", "dagger-compiler", daggerVersion)

    // MapStruct for DTO mapping (particularly ShipData)
    implementation("org.mapstruct:mapstruct:1.5.4.RubyDaggerFork-2")
    kapt("org.mapstruct:mapstruct-processor:1.5.4.RubyDaggerFork-2")

    // Junit 5 for Unit Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.0")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.mockk:mockk:1.12.5")

    // Log4j2 for Logging
    implementation("org.apache.logging.log4j:log4j-api:${properties["mc_log4j2_version"]}")
}

/*
 * Disable the default jar task and give it an unholy classifier to make it very visible if Gradle tries to publish it.
 */
tasks.jar {
    archiveClassifier.set("you-should-not-see-this-classifier-under-any-circumstance")
    enabled = false
}


tasks.shadowJar {
    // Only shade dependencies and transitive dependencies included using the shade configuration
    configurations = listOf(shade)

    // Don't shade dependencies that are already present at runtime in Minecraft
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
        exclude(dependency("javax.inject:javax.inject"))
        exclude(dependency("com.google.guava:guava"))
        exclude(dependency("com.fasterxml.jackson.core:.*"))
        exclude(dependency("com.fasterxml.jackson:.*"))
    }
}

/**
 * A task which returns a list of the paths of every class in every JAR in the given set of files
 */
abstract class GetClassesInClasspath : DefaultTask() {
    @get:InputFiles
    @get:CompileClasspath
    abstract val inputFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Internal
    val classes: List<String> get() = project.file(outputFile).readLines()

    @TaskAction
    fun writeClasses() {
        val classes = inputFiles
            .flatMap { jar ->
                JarFile(jar).entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && it != "module-info.class" }
            }
            .distinct()
            .joinToString("\n")

        outputFile.asFile.get().writeText(classes)
    }
}

/**
 * Get the list of all the class files on the runtime classpath (aka, libraries that are *not* shaded)
 */
val getRuntimeClasses: TaskProvider<GetClassesInClasspath> = tasks.register<GetClassesInClasspath>("getRuntimeClasses") {
    inputFiles.from(configurations.runtimeClasspath)
    outputFile.set(layout.buildDirectory.file("tmp/runtime_classes.txt"))
}

val configureStripProguard: Task by tasks.register("configureStripProguard") {
    dependsOn(getRuntimeClasses)

    doLast {
        // Configure the stripProguard task to copy from the proguard jar and exclude the runtime classes: see below
        stripProguard.apply {
            from(zipTree(proguardJarFile)) {
                exclude(getRuntimeClasses.get().classes)
            }
        }
    }
}

/**
 * The file proguard will output to. This is NOT the final file (see: stripProguard task)
 */
val proguardJarFile = base.libsDirectory.file("${project.name}-${project.version}-proguard.jar")

/**
 * The proguardJar task invokes ProGuard on the generated shadowJar, obfuscating and optimizing.
 * We also use ProGuard to replace the 'relocate' functionality of shadowJar, by obfuscating libraries that would
 * otherwise be relocated.
 *
 * Check the 'proguard-config.pro' file to configure ProGuard.
 */
val proguardJar by tasks.registering(proguard.taskClass) {
    // [getRuntimeClasses] requires that the api jars are built first
    dependsOn(":api-game:jar", ":api:jar", getRuntimeClasses)

    jdkModules.add("java.base")
    val cfg = File(temporaryDir, "keepconfig.pro")

    doFirst {
        // region Generate Keep Filter
        // Note: we utilize an ugly hack here. Proguard has issues when Kotlin libraries are registered using "libraryjar"
        // see: https://github.com/Guardsquare/proguard/issues/94
        // In order to work around this, we just add all the libraries as application files, but use "-keep" to ensure that
        // proguard doesn't try to obfuscate them. Then, we remove all the library files in the 'stripProguard' task.
        // Since this is too long to go on the command line, generate a temporary config file
        val runtimeClassKeepFilter =
            getRuntimeClasses.get().classes.joinToString(",") { it.substring(0, it.length - 6).replace('/', '.') }

        cfg.writeText("-keep class ")
        cfg.appendText(runtimeClassKeepFilter)
        cfg.appendText(" { *; }")
        // endregion
    }

    rulesFiles.from(cfg, "proguard-config.pro")
    mappingFile.set(base.libsDirectory.file("${project.name}-${project.version}-mapping.txt"))

    // Add the shadowJar as an input to obfuscate
    addInput {
        classpath.from(tasks.shadowJar)
    }

    // Add all runtime dependencies as an input to obfuscate (this is the ugly hack part)
    addInput {
        classpath.from(configurations.runtimeClasspath.get())

        // Ignore the non-class files, since we're going to be deleting all of this anyway
        // If we include these other files, ProGuard gives us duplicate errors because of META-INF files and such
        filter.set("!**module-info.class,**.class")
    }

    // Set the output file
    addOutput {
        archiveFile.set(proguardJarFile)
    }
}

/**
 * Removes unnecessary library files from the ProGuard jar. See the comment in the proguardJar task for more info.
 */
val stripProguard by tasks.register<Jar>("stripProguard") {
    dependsOn(proguardJar, configureStripProguard)
    inputs.file(proguardJarFile)

    destinationDirectory.set(base.libsDirectory)
    archiveFileName.set("${project.name}-${project.version}.jar")
    // Partially configured in getRuntimeClasses: see above
}

// Add the final, proguard-obfuscated jar to the default outgoing configurations
// https://docs.gradle.org/current/userguide/cross_project_publications.html#sec:simple-sharing-artifacts-between-projects
artifacts {
    add("runtimeElements", stripProguard)
    add("apiElements", stripProguard)
}


configurations {
    listOf(runtimeElements.get(), apiElements.get()).forEach { config ->
        config.artifacts.removeIf { it.file.canonicalPath != stripProguard.archiveFile.get().asFile.canonicalPath }
    }
}


// Don't publish the "shadowRuntimeElements" configuration, which publishes the shadowJar
(components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(
    configurations.named("shadowRuntimeElements").get()
) {
    skip()
}


publishing {
    publications {
        create<MavenPublication>(project.name) {
            from(components["java"])
        }
    }
}

tasks.test {
    useJUnitPlatform {
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
    }
}



