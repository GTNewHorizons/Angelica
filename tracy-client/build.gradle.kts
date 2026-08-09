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

val lwjglNatives: String by extra
val tracyVersion = libs.versions.tracy.get()

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

    compileOnly(libs.lwjgl3ify) { artifact { classifier = "dev" }; isTransitive = false }
    compileOnly("net.minecraftforge:forge:1.7.10-10.13.4.1614-1.7.10:universal") { isTransitive = false }

    implementation(libs.lwjgl3)

    compileOnly(libs.log4j.api)
    compileOnly(libs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.lwjgl3) { artifact { classifier = lwjglNatives } }
    testImplementation(libs.log4j.api)
    testRuntimeOnly(libs.log4j.core)
}

val generateTracyTags = tasks.register("generateTracyTags") {
    val version = tracyVersion
    val outDir = layout.buildDirectory.dir("generated/sources/tracyTags")
    inputs.property("tracyVersion", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/gtnewhorizons/angelica/tracy/TracyTags.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package com.gtnewhorizons.angelica.tracy;

            // Generated from the version catalog; do not edit
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
    archiveVersion.set(tracyVersion)
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
            version = tracyVersion
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
