plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    testing {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}

minecraft {
    javaCompatibilityVersion = 21

    extraRunJvmArguments.add("-Dangelica.enableTestBlocks=true")
    //extraRunJvmArguments.add("-Dorg.lwjgl.util.Debug=true")
    //extraRunJvmArguments.addAll("-Dlegacy.debugClassLoading=true", "-Dlegacy.debugClassLoadingFiner=false", "-Dlegacy.debugClassLoadingSave=true")
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
    }
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8)
    }
    dependsOn(tasks.extractNatives2)
    jvmArgs("-Djava.library.path=${tasks.extractNatives2.get().destinationFolder.asFile.get().path}")
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

tasks.shadowJar {
    relocate("org.slf4j", "com.gtnewhorizons.angelica.shadow.org.slf4j")
}
