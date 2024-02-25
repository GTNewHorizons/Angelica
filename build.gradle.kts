plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

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
