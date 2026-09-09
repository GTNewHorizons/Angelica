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
    compileOnly(libs.retrofuturabootstrap) { isTransitive = false }
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
    testImplementation(project(":glsm")) {
        capabilities { requireCapability("${project.group}:glsm-stubs") }
    }
    testImplementation(libs.celeritas.common) { isTransitive = false }
    testImplementation(libs.lwjgl3.opengl)
    // GL.<clinit> loads liblwjgl_opengl.so. Test scope only: a root-level runtimeOnly collides with lwjgl3Bindings.
    testRuntimeOnly(libs.lwjgl3.opengl) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.core)
    testRuntimeOnly(libs.fastutil)
    testRuntimeOnly(libs.joml)
    testRuntimeOnly(libs.commons.lang3)
    testRuntimeOnly(libs.guava)
    testImplementation(libs.asm.tree.test)
}

val glsmSdlTag = "glsm-sdl"

val glsmSdlStubs = sourceSets.create("glsmSdlStubs") {
    java.srcDir("src/glsmSdlStubs/java")
}

val glsmSdlAgent = sourceSets.create("glsmSdlAgent") {
    java.srcDir("src/glsmSdlAgent/java")
}

dependencies {
    "glsmSdlAgentCompileOnly"(project(":glsm"))
    "glsmSdlAgentCompileOnly"(libs.asm.tree.test)
    testImplementation(glsmSdlAgent.output)
}

tasks.test {
    useJUnitPlatform {
        excludeTags(glsmSdlTag)
    }
    if (System.getProperty("os.name").startsWith("Mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

val glsmSdlRedirectAgentJar by tasks.registering(Jar::class) {
    archiveClassifier = "glsm-sdl-redirect-agent"
    from(glsmSdlAgent.output)
    manifest {
        attributes("Premain-Class" to "com.gtnewhorizons.angelica.sdlgpu.glsm.GlsmSdlRedirectAgent")
    }
}

val glsmSdlTest by tasks.registering(Test::class) {
    description = "Runs the headless GLSM-on-SDL FFP rig in its own JVM, with the SDL GPU gate engaged."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = glsmSdlStubs.output.classesDirs.plus(
        sourceSets["test"].runtimeClasspath.filter { file ->
            !file.name.startsWith("lwjgl-2.") && !file.name.startsWith("lwjgl_util-") && !file.name.startsWith("lwjgl-platform-")
        }
    )
    useJUnitPlatform {
        includeTags(glsmSdlTag)
    }
    dependsOn(tasks.named("glsmSdlStubsClasses"), glsmSdlRedirectAgentJar)
    val agentJarPath = glsmSdlRedirectAgentJar.flatMap { it.archiveFile }.map { it.asFile.path }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${agentJarPath.get()}") })
    jvmArgs(
        "-Dangelica.sdlgpu.enable=true",
        "-Dangelica.sdlgpu.disablePresenterThread=true",
        "-Dceleritas.lwjglService=com.gtnewhorizons.angelica.sdlgpu.SDLGPULWJGLService",
    )
    if (System.getProperty("os.name").startsWith("Mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    mustRunAfter(tasks.test)
}

tasks.check { dependsOn(glsmSdlTest) }

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
