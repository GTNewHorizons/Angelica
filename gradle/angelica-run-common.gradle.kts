import com.gtnewhorizons.retrofuturagradle.MinecraftExtension
import org.gradle.accessors.dm.LibrariesForLibs
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

val libs = the<LibrariesForLibs>()

tasks.withType<DowngradeJar>().configureEach { logLevel.set("FATAL") }
tasks.withType<DowngradeFiles>().configureEach { logLevel.set("FATAL") }

configurations.all {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.github.GTNewHorizons:lwjgl3ify"))
            .using(module("com.github.GTNewHorizons:lwjgl3ify:${libs.versions.lwjgl3ify.get()}"))
    }
}

repositories {
    maven {
        name = "Maven Central Snapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        content { includeGroup("org.lwjgl") }
    }
}

tasks.withType<JavaExec>().matching { it.name.startsWith("runClient") }.configureEach {
    for ((key, value) in System.getProperties()) {
        val name = key.toString()
        if (name.startsWith("angelica.") || name.startsWith("quickPlay")) {
            jvmArgs("-D$name=$value")
        }
    }
}

val isMacOs = org.gradle.internal.os.OperatingSystem.current().isMacOsX
val lwjglDebug = project.extra.has("lwjglDebug") && project.extra["lwjglDebug"] as Boolean

tasks.withType<JavaExec>().configureEach {
    if (name.startsWith("runClient") && name != "runClient" && isMacOs) {
        jvmArgs("-XstartOnFirstThread")
        jvmArgs("-Dangelica.sdlgpu.encoderAssertions=" + if (lwjglDebug) "fatal" else "warn")
        if (lwjglDebug) {
            environment("METAL_DEVICE_WRAPPER_TYPE", "1")
            environment("METAL_DEBUG_ERROR_MODE", "0")
            environment("MTL_SHADER_VALIDATION", "1")
            environment("MTL_SHADER_VALIDATION_REPORT_TO_STDERR", "1")
            environment("MTL_DEBUG_LAYER", "1")
            environment("MallocScribble", "1")
            environment("MallocPreScribble", "1")
            environment("MallocGuardEdges", "1")
            environment("MallocStackLogging", "1")
            environment("MallocStackLoggingNoCompact", "1")
        }
    }
}

configure<MinecraftExtension> {
    lwjgl3Bindings.addAll("shaderc", "spvc")
    lwjgl3Version = libs.versions.lwjgl3.get()
}

tasks.named<Copy>("processResources") {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching("META-INF/rfb-plugin/*") {
        expand("version" to projectVersion)
    }
}
