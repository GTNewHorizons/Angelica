plugins {
    `java-library`
    `maven-publish`
}

version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
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
        name = "Mojang"
        url = uri("https://libraries.minecraft.net")
    }
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
    maven {
        name = "Forge"
        url = uri("https://maven.minecraftforge.net/")
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

    compileOnly("com.github.GTNewHorizons:lwjgl3ify:3.0.25:dev") { isTransitive = false }
    compileOnly("org.embeddedt.celeritas:celeritas-common:2.5.9-GTNH") { isTransitive = false }

    // GL enum constants (compileOnly -- no runtime GL dependency)
    compileOnly("org.lwjgl:lwjgl-opengl:${lwjglVersion}")

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

    // LWJGL3 core
    implementation("org.lwjgl:lwjgl:${lwjglVersion}")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")

    // SDL3 GPU
    implementation("org.lwjgl:lwjgl-sdl:${lwjglVersion}")
    runtimeOnly("org.lwjgl:lwjgl-sdl:$lwjglVersion:$lwjglNatives")

    // SPIRV-Cross (shader cross-compilation + reflection)
    implementation("org.lwjgl:lwjgl-spvc:${lwjglVersion}")
    runtimeOnly("org.lwjgl:lwjgl-spvc:$lwjglVersion:$lwjglNatives")

    // shaderc (GLSL -> SPIR-V)
    implementation("org.lwjgl:lwjgl-shaderc:${lwjglVersion}")
    runtimeOnly("org.lwjgl:lwjgl-shaderc:$lwjglVersion:$lwjglNatives")

    compileOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
    compileOnly("org.jetbrains:annotations:26.0.2")

    testImplementation(platform("org.junit:junit-bom:5.14.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(testFixtures(project(":glsm")))
    testImplementation("org.lwjgl:lwjgl-opengl:${lwjglVersion}")
    // GL.<clinit> loads liblwjgl_opengl.so. Test scope only: a root-level runtimeOnly collides with lwjgl3Bindings.
    testRuntimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.0-beta9")
    testRuntimeOnly("it.unimi.dsi:fastutil:8.5.18")
}

tasks.test {
    useJUnitPlatform()
    if (System.getProperty("os.name").startsWith("Mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Lwjgl3ify-Aware" to "true"
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.gtnewhorizons.angelica"
            artifactId = "sdl-gpu"
            version = rootProject.version.toString()
            from(components["java"])
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
