import xyz.wagyourtail.jvmdg.gradle.flags.DowngradeFlags
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.withType<DowngradeJar>().configureEach { logLevel.set("FATAL") }
tasks.withType<DowngradeFiles>().configureEach { logLevel.set("FATAL") }
val lwjglDebug = false
val gpuHud = false

minecraft   {
    extraRunJvmArguments.add("-Dangelica.enableTestBlocks=true")
    extraRunJvmArguments.add("-Dangelica.tracy=true")
    extraRunJvmArguments.add("-Dangelica.useSDLGPU=true")
    extraRunJvmArguments.add("-Dangelica.failOnUnmappedGL=true")
    extraRunJvmArguments.add("-Dorg.lwjgl.util.Debug=$lwjglDebug")

    extraRunJvmArguments.add("-Dsun.net.client.defaultConnectTimeout=5000")
    extraRunJvmArguments.add("-Dsun.net.client.defaultReadTimeout=5000")
    // TODO: Remove once GTNHGradle includes shaderc/spvc in the default lwjgl3Bindings list
    lwjgl3Bindings.addAll("shaderc", "spvc")
    lwjgl3Version = "3.4.2-20260602.093430-9"
//    extraRunJvmArguments.addAll("-Dlegacy.debugClassLoadingSave=true")
//    extraRunJvmArguments.addAll("-Drfb.dumpLoadedClasses=true", "-Drfb.dumpLoadedClassesPerTransformer=true")
    //extraRunJvmArguments.add("-Dangelica.redirectorLogspam=true")

}

if (gpuHud) {
    tasks.withType<JavaExec>().matching { it.name.startsWith("runClient") }.configureEach {
        if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
            environment("MTL_HUD_ENABLED", "1")
        } else {
            environment("MANGOHUD", "1")
            environment("LD_PRELOAD", "/usr/\$LIB/mangohud/libMangoHud_shim.so")
        }
    }
}


configurations.all {
    exclude(group = "com.github.GTNewHorizons", module = "Angelica")
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.github.GTNewHorizons:lwjgl3ify"))
            .using(module("com.github.GTNewHorizons:lwjgl3ify:3.0.25"))
    }
}

// lwjgl3ify pulls a pinned lwjgl 3.4.2 snapshot transitively from here.
repositories {
    maven {
        name = "Maven Central Snapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        content { includeGroup("org.lwjgl") }
    }
}

val osName = System.getProperty("os.name").lowercase()
val isMacOs = org.gradle.internal.os.OperatingSystem.current().isMacOsX
val isMacOsArm64 = isMacOs && System.getProperty("os.arch") == "aarch64"

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runClient")) {
        if (isMacOs) {
            // SDL3 / Cocoa / Metal must initialize on the JVM main thread.
            jvmArgs("-XstartOnFirstThread")
            jvmArgs("-Dangelica.sdlgpu.encoderAssertions=true")
            if (lwjglDebug) {
                environment("METAL_DEVICE_WRAPPER_TYPE", "1")
                environment("METAL_DEBUG_ERROR_MODE", "0")
                environment("MTL_SHADER_VALIDATION", "1")
                environment("MTL_SHADER_VALIDATION_REPORT_TO_STDERR", "1")
                environment("MTL_DEBUG_LAYER", "1")
                jvmArgs("-Dangelica.sdlgpu.encoderAssertionsFatal=true")
                environment("MallocScribble", "1")
                environment("MallocPreScribble", "1")
                environment("MallocGuardEdges", "1")
                environment("MallocStackLogging", "1")
                environment("MallocStackLoggingNoCompact", "1")
            }
        }
    }
}

// Linux runClient: RenderDoc frame capture (default, F12) OR RADV RGP/SQTT capture (-Prgp). Mutually exclusive
tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runClient") && !isMacOs && !osName.contains("windows")) {
        environment("WAYLAND_DISPLAY", "")
        environment("XDG_SESSION_TYPE", "x11")
        val rgpFrame = project.findProperty("rgpFrame") as String?
        val rgpCapture = project.hasProperty("rgp") || rgpFrame != null
        if (rgpCapture) {
            environment("MESA_VK_TRACE", "rgp")
            if (rgpFrame != null) {
                environment("MESA_VK_TRACE_FRAME", rgpFrame)
                doFirst { logger.lifecycle("RGP: capturing frame $rgpFrame -> /tmp/java_*.rgp") }
            } else {
                environment("MESA_VK_TRACE_TRIGGER", "/tmp/angelica_rgp.trigger")
                doFirst { logger.lifecycle("RGP: touch /tmp/angelica_rgp.trigger (or press F1) to capture -> /tmp/java_*.rgp") }
            }
        } else {
            val renderdoc = file(project.findProperty("renderdocLib") as String? ?: "${System.getProperty("user.home")}/Downloads/renderdoc_1.44/lib/librenderdoc.so")
            if (renderdoc.isFile) {
                environment("LD_PRELOAD", renderdoc.absolutePath)
                doFirst { logger.lifecycle("RENDERDOC: LD_PRELOAD set, press F12 to capture") }
            }
        }
    }
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching("META-INF/rfb-plugin/*") {
        expand("version" to projectVersion)
    }
}

tasks.register<Copy>("copyDependencies") {
    group = "Angelica"
    description = "Collect dependencies into the testDependencies folder"
    from(configurations.default)
    into("testDependencies")
}

val embedOnly: Configuration by configurations
val shadowImplementation: Configuration by configurations

val downgradeEmbedOnlyForTest by tasks.registering(DowngradeFiles::class) {
    inputCollection = embedOnly
    outputs.dir(temporaryDir)
}

val stripModuleInfoFromShadow by tasks.registering(Jar::class) {
    dependsOn(shadowImplementation)
    from(shadowImplementation.map(::zipTree))
    exclude("module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName = "shadow-stripped.jar"
}

val downgradeShadowImplForTest by tasks.registering(DowngradeFiles::class) {
    inputCollection = files(stripModuleInfoFromShadow.map { it.archiveFile })
    outputs.dir(temporaryDir)
}

val glsmTestFixtures: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
    }
}

val downgradeGlsmTestFixturesForTest by tasks.registering(DowngradeFiles::class) {
    inputCollection = glsmTestFixtures
    outputs.dir(temporaryDir)
}

fun downgraded(task: TaskProvider<DowngradeFiles>): FileCollection = files(task.map { it.outputMap.values })

fun Test.configureAngelicaJava8() {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8)
        if (isMacOsArm64) {
            vendor = JvmVendorSpec.AZUL
        }
    }
    dependsOn(tasks.extractNatives2)
    jvmArgs("-Djava.library.path=${tasks.extractNatives2.get().destinationFolder.asFile.get().path}")
    testLogging { events("passed", "skipped", "failed") }

    val swaps = mapOf(
        embedOnly to downgradeEmbedOnlyForTest,
        shadowImplementation to downgradeShadowImplForTest,
        glsmTestFixtures to downgradeGlsmTestFixturesForTest,
    )
    dependsOn(swaps.values)
    for ((original, downgrade) in swaps) {
        classpath = classpath.minus(original).plus(downgraded(downgrade))
    }
}

tasks.test {
    useJUnitPlatform { excludeTags = setOf("gl-core") }
    configureAngelicaJava8()
}

val downgradeRootMainClasses = tasks.named<DowngradeFiles>("downgradeMainClasses")
val downgradeRootTestClasses = tasks.named<DowngradeFiles>("downgradeTestClasses")

val glCoreTest by tasks.registering(Test::class) {
    description = "Runs the gl-core tests in their own JVM with a real GL context."
    group = "verification"
    useJUnitPlatform { includeTags = setOf("gl-core") }

    dependsOn(downgradeRootMainClasses, downgradeRootTestClasses)
    val rawMain = sourceSets["main"].output.classesDirs
    val rawTest = sourceSets["test"].output.classesDirs
    val downgradedTest = downgraded(downgradeRootTestClasses)

    setTestClassesDirs(downgradedTest)
    classpath = downgradedTest
        .plus(downgraded(downgradeRootMainClasses))
        .plus(sourceSets["test"].runtimeClasspath.minus(rawMain).minus(rawTest))

    configureAngelicaJava8()
    mustRunAfter(tasks.test)
}

tasks.test { finalizedBy(glCoreTest) }
tasks.check { dependsOn(glCoreTest) }

tasks.shadowJar {
    dependsOn(embedOnly)
    from(embedOnly.map(::zipTree))

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    minimize {
        exclude(project(rootProject.path))
        exclude(project(":glsm"))
        exclude(project(":lwjgl3-backend"))
        exclude(project(":sdl-gpu"))
        exclude(dependency("org.taumc:.*:.*"))
        exclude(dependency("org.antlr:.*:.*"))
    }

    relocate("com.mitchej123", "com.mitchej123")
    relocate("org.embeddedt", "org.embeddedt")
    relocate("com.gtnewhorizons.angelica.config", "com.gtnewhorizons.angelica.config")
    relocate("com.gtnewhorizons.angelica.glsm", "com.gtnewhorizons.angelica.glsm")
    relocate("com.gtnewhorizons.angelica.lwjgl3", "com.gtnewhorizons.angelica.lwjgl3")
    relocate("com.gtnewhorizons.angelica.sdlgpu", "com.gtnewhorizons.angelica.sdlgpu")
}

