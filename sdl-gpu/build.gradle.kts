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

val lwjglNatives: String by extra

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

    compileOnly(libs.lwjgl3ify) { artifact { classifier = "dev" }; isTransitive = false }
    compileOnly(libs.celeritas.common) { isTransitive = false }

    // GL enum constants (compileOnly -- no runtime GL dependency)
    compileOnly(libs.lwjgl3.opengl)

    // LWJGL3 core + SDL3 GPU + SPIRV-Cross + shaderc
    implementation(libs.bundles.lwjgl3.sdlgpu)
    libs.bundles.lwjgl3.sdlgpu.get().forEach {
        runtimeOnly(it) { artifact { classifier = lwjglNatives } }
    }

    compileOnly(libs.log4j.api)
    compileOnly(libs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(testFixtures(project(":glsm")))
    testImplementation(libs.lwjgl3.opengl)
    // GL.<clinit> loads liblwjgl_opengl.so. Test scope only: a root-level runtimeOnly collides with lwjgl3Bindings.
    testRuntimeOnly(libs.lwjgl3.opengl) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.core)
    testRuntimeOnly(libs.fastutil)
}

tasks.test {
    useJUnitPlatform()
    if (System.getProperty("os.name").startsWith("Mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.named<JavaCompile>("compileJava") {
    val classesDir = destinationDirectory
    doLast { injectLwjgl3Aware(classesDir.get().asFile) }
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
