import java.time.Duration

plugins {
    `java-library`
    `maven-publish`
}

version = rootProject.version

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

// RFG variant attributes: the root Angelica project uses RetroFuturaGradle which expects
// these attributes on dependencies. Without them, Gradle can't resolve the subproject variant.
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
    compileOnly(libs.retrofuturabootstrap) { isTransitive = false }
    compileOnly(libs.celeritas.common) { isTransitive = false }

    // LWJGL3
    implementation(libs.lwjgl3)
    implementation(libs.lwjgl3.opengl)
    compileOnly(libs.lwjgl3.sdl)
    runtimeOnly(libs.lwjgl3) { artifact { classifier = lwjglNatives } }
    runtimeOnly(libs.lwjgl3.opengl) { artifact { classifier = lwjglNatives } }

    compileOnly(libs.log4j.api)
    compileOnly(libs.annotations)
}

val glesTag = "gles"

val glsmRedirectAgent: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val glsmLwjgl2Stubs: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    glsmRedirectAgent(project(":glsm", "redirectAgent"))
    glsmLwjgl2Stubs(project(":glsm", "lwjgl2Stubs"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(testFixtures(project(":glsm")))
    testImplementation(project(":glsm")) {
        capabilities { requireCapability("${project.group}:glsm-stubs") }
    }
    listOf(libs.lwjgl3.sdl, libs.lwjgl3.shaderc, libs.lwjgl3.spvc).forEach {
        testImplementation(it)
        testRuntimeOnly(it) { artifact { classifier = lwjglNatives } }
    }
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.core)
    testRuntimeOnly(libs.fastutil)
    testRuntimeOnly(libs.joml)
    testRuntimeOnly(libs.commons.lang3)
    testRuntimeOnly(libs.guava)
    testRuntimeOnly(libs.celeritas.common) { isTransitive = false }
    testRuntimeOnly(libs.asm.tree.test)
}

tasks.test {
    useJUnitPlatform {
        excludeTags(glesTag)
    }
}

val unpackLwjgl3Natives = registerLwjgl3NativesUnpack()

val glesTest by tasks.registering(Test::class) {
    description = "Boots GLSM on the LWJGL3 GL backend in a real OpenGL ES 3.2 context."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = glsmLwjgl2Stubs.plus(
        sourceSets["test"].runtimeClasspath.filter { file ->
            !file.name.startsWith("lwjgl-2.") && !file.name.startsWith("lwjgl_util-") && !file.name.startsWith("lwjgl-platform-")
        }
    )
    useJUnitPlatform {
        includeTags(glesTag)
    }
    systemProperty("glsm.stubs.capabilities", "lwjgl3")
    javaAgent(glsmRedirectAgent)
    loadLwjgl3NativesFrom(unpackLwjgl3Natives)
    outputs.cacheIf { false }
    timeout = Duration.ofMinutes(5)
    systemProperty("junit.jupiter.execution.timeout.default", "60s")
    systemProperty("junit.jupiter.execution.timeout.threaddump.enabled", "true")
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    mustRunAfter(tasks.test)
}

if (!providers.environmentVariable("CI").isPresent) {
    tasks.check { dependsOn(glesTest) }
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
            artifactId = "lwjgl3-backend"
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
