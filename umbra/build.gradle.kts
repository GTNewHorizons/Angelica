plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    extraRunJvmArguments.add("-Dumbra.dumpClass=true")
}


tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("version", projectVersion)
    filesMatching("META-INF/rfb-plugin/*") {
        expand("version" to projectVersion)
    }
}

val embedOnly: Configuration by configurations
val shadowImplementation: Configuration by configurations

tasks.shadowJar {
    dependsOn(embedOnly)
    from(embedOnly.map(::zipTree))

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()

    minimize {
        exclude(project(":glsm"))
        exclude(project(":lwjgl3-backend"))
    }

    relocate("com.mitchej123", "com.mitchej123")
    relocate("org.embeddedt", "org.embeddedt")
    relocate("com.gtnewhorizons.angelica.glsm", "com.gtnewhorizons.angelica.glsm")
    relocate("com.gtnewhorizons.angelica.lwjgl3", "com.gtnewhorizons.angelica.lwjgl3")
}
