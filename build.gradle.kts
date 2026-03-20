import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    extraRunJvmArguments.add("-Dangelica.enableTestBlocks=true")
    extraRunJvmArguments.add("-Dangelica.dumpClass=true")
//    extraRunJvmArguments.add("-Dorg.lwjgl.util.Debug=true")
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
    val isMacOsArm64 = System.getProperty("os.name").lowercase().contains("mac") && System.getProperty("os.arch") == "aarch64"
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

val embedOnly: Configuration by configurations

val stripModuleInfoFromEmbeds by tasks.registering(Jar::class) {
    dependsOn(embedOnly)
    from(embedOnly.map(::zipTree))
    exclude("module-info.class")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier = "embeds-stripped"
}

// Downgrade embedOnly jars for the tests.
val downgradeEmbedOnlyForTest by tasks.registering(DowngradeFiles::class) {
    inputCollection = files(stripModuleInfoFromEmbeds.map { it.archiveFile })
    outputs.dir(temporaryDir)
}

tasks.test {
    dependsOn(downgradeEmbedOnlyForTest)
    classpath = classpath.plus(files({ fileTree(downgradeEmbedOnlyForTest.get().temporaryDir) }))
}

tasks.shadowJar {
    dependsOn(embedOnly)
    from(embedOnly.map(::zipTree))
    minimize {
        exclude(project(":glsm"))
    }
    relocate("com.mitchej123", "com.mitchej123")
    relocate("org.embeddedt", "org.embeddedt")
}

