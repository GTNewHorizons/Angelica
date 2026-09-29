import com.modrinth.minotaur.TaskModrinthUpload
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val lwjglDebug = false
val gpuHud = false
val renderdoc = false
val rgp = false
extra["lwjglDebug"] = lwjglDebug

minecraft   {
    extraRunJvmArguments.add("-Dangelica.debug.testBlocks=true")
    extraRunJvmArguments.add("-Dangelica.tracy=true")
    extraRunJvmArguments.add("-Dangelica.sdlgpu.enable=true")
    extraRunJvmArguments.add("-Dangelica.unmappedGL=fail")
    extraRunJvmArguments.add("-Dorg.lwjgl.util.Debug=$lwjglDebug")

    extraRunJvmArguments.add("-Dsun.net.client.defaultConnectTimeout=5000")
    extraRunJvmArguments.add("-Dsun.net.client.defaultReadTimeout=5000")
//    extraRunJvmArguments.addAll("-Dlegacy.debugClassLoadingSave=true")
//    extraRunJvmArguments.addAll("-Drfb.dumpLoadedClasses=true", "-Drfb.dumpLoadedClassesPerTransformer=true")
    //extraRunJvmArguments.add("-Dangelica.debug.redirectorLogspam=true")

}

apply(from = "gradle/angelica-run-common.gradle.kts")

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
}

val osName = System.getProperty("os.name").lowercase()
val isMacOs = org.gradle.internal.os.OperatingSystem.current().isMacOsX
val isMacOsArm64 = isMacOs && System.getProperty("os.arch") == "aarch64"

// Linux runClient: RenderDoc frame capture (F12) OR RADV RGP/SQTT capture. Mutually exclusive; RGP wins
tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runClient") && !isMacOs && !osName.contains("windows")) {
        environment("WAYLAND_DISPLAY", "")
        environment("XDG_SESSION_TYPE", "x11")
        val rgpFrame = project.findProperty("rgpFrame") as String?
        val rgpCapture = rgp || project.hasProperty("rgp") || rgpFrame != null
        if (rgpCapture) {
            environment("MESA_VK_TRACE", "rgp")
            if (rgpFrame != null) {
                environment("MESA_VK_TRACE_FRAME", rgpFrame)
                doFirst { logger.lifecycle("RGP: capturing frame $rgpFrame -> /tmp/java_*.rgp") }
            } else {
                environment("MESA_VK_TRACE_TRIGGER", "/tmp/angelica_rgp.trigger")
                doFirst { logger.lifecycle("RGP: touch /tmp/angelica_rgp.trigger (or press F1) to capture -> /tmp/java_*.rgp") }
            }
        } else if (renderdoc || project.hasProperty("renderdoc")) {
            val renderdocLib = file(project.findProperty("renderdocLib") as String? ?: "${System.getProperty("user.home")}/Downloads/renderdoc_1.44/lib/librenderdoc.so")
            if (renderdocLib.isFile) {
                environment("LD_PRELOAD", renderdocLib.absolutePath)
                doFirst { logger.lifecycle("RENDERDOC: LD_PRELOAD set, press F12 to capture") }
            } else {
                doFirst { logger.lifecycle("RENDERDOC: enabled but ${renderdocLib.absolutePath} not found; set -PrenderdocLib=<path>") }
            }
        }
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
    if (isMacOs) {
        jvmArgs("-Dapple.awt.UIElement=true")
    }
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
    jvmArgs("-Dceleritas.lwjglService=org.taumc.celeritas.lwjgl.HeadlessTestLWJGLService")
    dependsOn(":glsm:classes", ":lwjgl3-backend:classes", ":sdl-gpu:classes")
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

apply(from = "gradle/angelica-shadow-common.gradle.kts")

tasks.withType<TaskPublishCurseForge>().configureEach {
    uploadArtifacts.forEach { it.addGameVersion("Client") }
}

tasks.withType<TaskModrinthUpload>().configureEach {
    setDependsOn(dependsOn.filterNot { it == "build" } + "assemble")
}
