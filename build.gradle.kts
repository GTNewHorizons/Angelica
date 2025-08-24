plugins {
    id("com.gtnewhorizons.gtnhconvention")
    id("com.osmerion.lwjgl3") version "0.5.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    testing {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }
}

val lwjglVersion = "3.3.3"

minecraft {
    javaCompatibilityVersion = 21
    mainLwjglVersion = 3
    lwjgl3Version = lwjglVersion

    extraRunJvmArguments.add("-Dangelica.enableTestBlocks=true")
    //extraRunJvmArguments.add("-Dangelica.dumpClass=true")
    //extraRunJvmArguments.add("-Dangelica.redirectorLogspam=true")
    //extraRunJvmArguments.add("-Dorg.lwjgl.util.Debug=true")
    //extraRunJvmArguments.addAll("-Dlegacy.debugClassLoading=true", "-Dlegacy.debugClassLoadingFiner=false", "-Dlegacy.debugClassLoadingSave=true")
}

repositories {
    mavenCentral()
}

// Configure LWJGL3 with the Osmerion plugin
lwjgl3 {
    targets.named("main") {
        // Set the LWJGL version to match the one in minecraft configuration
        version.set(lwjglVersion)

        modules.add("lwjgl")
        modules.add("lwjgl-glfw")
        modules.add("lwjgl-opengl")
    }
}


// Link the LWJGL configurations to the test configurations
configurations {
    val mainTarget = lwjgl3.targets.named("main").get()

    "testImplementation" {
        extendsFrom(mainTarget.libConfiguration.get())
    }

    "testRuntimeOnly" {
        extendsFrom(mainTarget.nativesConfiguration.get())
    }
}


for (jarTask in listOf(tasks.jar, tasks.shadowJar, tasks.sourcesJar)) {
    jarTask.configure {
        manifest {
            attributes("Lwjgl3ify-Aware" to true)
        }
    }
}

tasks.runClient { enabled = false }
tasks.runServer { enabled = false }
tasks.runClient17 { enabled = false }
tasks.runServer17 { enabled = false }

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })

    // Additional system properties for debugging LWJGL
    systemProperty("org.lwjgl.system.debug", "true")
    systemProperty("org.lwjgl.util.Debug", "true")
    systemProperty("org.lwjgl.util.DebugLoader", "true")
}

tasks.processResources {
    inputs.property("version", project.version.toString())
    filesMatching("META-INF/rfb-plugin/*") {
        expand("version" to project.version.toString())
    }
}

tasks.register<Copy>("copyDependencies") {
    group = "Angelica"
    description = "Collect dependencies into the testDependencies folder"
    from(configurations.default)
    into("testDependencies")
}



// Ensure LWJGL version consistency
configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.lwjgl") {
                useVersion(lwjglVersion)
            }
        }
    }
}
