plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    extraRunJvmArguments.add("-Dangelica.enableTestBlocks=true")
    extraRunJvmArguments.add("-Dangelica.dumpClass=true")
//    extraRunJvmArguments.addAll("-Dlegacy.debugClassLoadingSave=true")
//    extraRunJvmArguments.addAll("-Drfb.dumpLoadedClasses=true", "-Drfb.dumpLoadedClassesPerTransformer=true")
    //extraRunJvmArguments.add("-Dangelica.redirectorLogspam=true")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    // On macOS ARM64, use an ARM64-compatible JVM for tests
    val isMacOsArm64 = System.getProperty("os.name").lowercase().contains("mac") &&
        System.getProperty("os.arch") == "aarch64"
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8)
        if (isMacOsArm64) {
            vendor = JvmVendorSpec.AZUL
        }
    }
    dependsOn(tasks.extractNatives2)
    jvmArgs("-Djava.library.path=${tasks.extractNatives2.get().destinationFolder.asFile.get().path}")
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
