plugins {
    `java-library`
    `maven-publish`
}

version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

val rfgObfAttr = Attribute.of("com.gtnewhorizons.retrofuturagradle.obfuscation", String::class.java)
val rfgTransformedAttr = Attribute.of("rfgDeobfuscatorTransformed", Boolean::class.javaObjectType)

configurations {
    listOf("apiElements", "runtimeElements").forEach { name ->
        named(name) {
            attributes {
                attribute(rfgObfAttr, "mcp")
                attribute(rfgTransformedAttr, true)
            }
        }
    }
}

val lwjglVersion = "3.4.2-SNAPSHOT"

repositories {
    mavenLocal()
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
    maven {
        name = "Minecraft Libraries"
        url = uri("https://libraries.minecraft.net")
    }
    maven {
        name = "Forge"
        url = uri("https://maven.minecraftforge.net/")
    }
    maven {
        name = "Forge artifact-only"
        url = uri("https://maven.minecraftforge.net/")
        metadataSources { artifact() }
        content { includeModule("net.minecraftforge", "forge") }
    }
    maven {
        name = "taumc"
        url = uri("https://maven.taumc.org/releases")
    }
    maven {
        name = "Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "Maven Central Snapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
    mavenCentral()
}

dependencies {
    api(project(":glsm"))

    compileOnly("com.github.GTNewHorizons:lwjgl3ify:3.0.15:dev") { isTransitive = false }
    compileOnly("net.minecraftforge:forge:1.7.10-10.13.4.1614-1.7.10:universal") { isTransitive = false }

    implementation("org.lwjgl:lwjgl:${lwjglVersion}")

    compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
    compileOnly("org.jetbrains:annotations:26.0.2")

    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val lwjglNatives = when {
        osName.contains("linux") && osArch.contains("aarch64") -> "natives-linux-arm64"
        osName.contains("linux") -> "natives-linux"
        osName.contains("windows") && osArch.contains("aarch64") -> "natives-windows-arm64"
        osName.contains("windows") -> "natives-windows"
        osName.contains("mac") && osArch.contains("aarch64") -> "natives-macos-arm64"
        osName.contains("mac") -> "natives-macos"
        else -> "natives-linux"
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    testImplementation("org.apache.logging.log4j:log4j-api:2.0-beta9")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.0-beta9")
}

val generateTracyTags = tasks.register("generateTracyTags") {
    val version = project.property("tracyVersion").toString()
    val outDir = layout.buildDirectory.dir("generated/sources/tracyTags")
    inputs.property("tracyVersion", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/gtnewhorizons/angelica/tracy/TracyTags.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.gtnewhorizons.angelica.tracy;

            // Generated from gradle.properties tracyVersion; do not edit
            public final class TracyTags {
                private TracyTags() {}
                public static final String TRACY_VERSION = "$version";
            }
            """.trimIndent() + "\n"
        )
    }
}
sourceSets["main"].java.srcDir(generateTracyTags)

tasks.test {
    useJUnitPlatform()
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Lwjgl3ify-Aware" to "true"
        )
    }
}

val distJar = tasks.register<Jar>("distJar") {
    archiveBaseName.set("angelica-tracy")
    archiveVersion.set(project.property("tracyVersion").toString())
    from(sourceSets["main"].output)
    manifest {
        attributes(
            "Lwjgl3ify-Aware" to "true",
            "FMLCorePlugin" to "com.gtnewhorizons.angelica.tracy.loading.TracyCorePlugin"
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.gtnewhorizons.angelica"
            artifactId = "angelica-tracy"
            version = project.property("tracyVersion").toString()
            artifact(distJar)
        }
    }
    repositories {
        if (System.getenv("MAVEN_USER") != null) {
            maven {
                name = "GTNHMaven"
                url = uri(rootProject.findProperty("mavenPublishUrl")?.toString() ?: "https://nexus.gtnewhorizons.com/repository/releases/")
                credentials {
                    username = System.getenv("MAVEN_USER")
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }
}
